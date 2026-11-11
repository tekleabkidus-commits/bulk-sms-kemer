package bulksms.test.mark.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import bulksms.test.mark.BulkSmsApp
import bulksms.test.mark.data.entity.CampaignStatus
import bulksms.test.mark.data.entity.PauseReason
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ServiceCommandReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val app = context.applicationContext as BulkSmsApp
        val campaignId = intent.getLongExtra(EXTRA_CAMPAIGN_ID, -1L)
        if (campaignId <= 0) { pending.finish(); return }
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ACTION_PAUSE -> {
                        app.campaignRepo.pause(campaignId, PauseReason.USER, null)
                        SmsDispatchService.requestStop(context)
                    }
                    ACTION_RESUME -> {
                        val c = app.campaignRepo.getCampaign(campaignId) ?: return@launch
                        if (c.status == CampaignStatus.PAUSED || c.status == CampaignStatus.DRAFT) {
                            app.campaignRepo.startRunning(campaignId)
                            val svc = Intent(context, SmsDispatchService::class.java)
                                .putExtra(SmsDispatchService.EXTRA_CAMPAIGN_ID, campaignId)
                            ContextCompat.startForegroundService(context, svc)
                        }
                    }
                    ACTION_CANCEL -> {
                        app.campaignRepo.cancel(campaignId)
                        SmsDispatchService.requestStop(context)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_PAUSE = "bulksms.test.mark.PAUSE"
        const val ACTION_RESUME = "bulksms.test.mark.RESUME"
        const val ACTION_CANCEL = "bulksms.test.mark.CANCEL"
        const val EXTRA_CAMPAIGN_ID = "campaign_id"
    }
}
