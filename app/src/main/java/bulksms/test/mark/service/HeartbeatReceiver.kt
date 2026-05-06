package bulksms.test.mark.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import bulksms.test.mark.BulkSmsApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class HeartbeatReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val app = context.applicationContext as BulkSmsApp
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val campaignId = app.campaignRepo.activeCampaignId() ?: run {
                    HeartbeatScheduler.cancel(context)  // nothing to revive
                    return@launch
                }
                val campaign = app.campaignRepo.getCampaign(campaignId)
                if (campaign != null && campaign.status.name == "RUNNING") {
                    if (!SmsDispatchService.isRunning) {
                        val svc = Intent(context, SmsDispatchService::class.java).apply {
                            putExtra(SmsDispatchService.EXTRA_CAMPAIGN_ID, campaignId)
                        }
                        ContextCompat.startForegroundService(context, svc)
                    }
                }
                // Re-arm
                HeartbeatScheduler.schedule(context)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_HEARTBEAT = "bulksms.test.mark.HEARTBEAT"
    }
}
