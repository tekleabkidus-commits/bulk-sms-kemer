package bulksms.test.mark.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import bulksms.test.mark.BulkSmsApp
import bulksms.test.mark.data.entity.CampaignStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in BOOT_ACTIONS) return
        val pending = goAsync()
        val app = context.applicationContext as BulkSmsApp
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val campaignId = app.campaignRepo.activeCampaignId() ?: return@launch
                val campaign = app.campaignRepo.getCampaign(campaignId) ?: return@launch
                if (campaign.status == CampaignStatus.RUNNING) {
                    val svc = Intent(context, SmsDispatchService::class.java).apply {
                        putExtra(SmsDispatchService.EXTRA_CAMPAIGN_ID, campaignId)
                        putExtra(SmsDispatchService.EXTRA_BOOT_RECOVERY, true)
                    }
                    ContextCompat.startForegroundService(context, svc)
                    DispatchNotifications.postAlert(
                        context,
                        "Campaign resumed after restart",
                        "Continuing \"${campaign.name}\" from where it stopped.",
                        campaignId
                    )
                }
                HeartbeatScheduler.schedule(context)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private val BOOT_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON"
        )
    }
}
