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

class PackageReplacedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pending = goAsync()
        val app = context.applicationContext as BulkSmsApp
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val id = app.campaignRepo.activeCampaignId() ?: return@launch
                val c = app.campaignRepo.getCampaign(id) ?: return@launch
                if (c.status == CampaignStatus.RUNNING) {
                    val svc = Intent(context, SmsDispatchService::class.java)
                        .putExtra(SmsDispatchService.EXTRA_CAMPAIGN_ID, id)
                    ContextCompat.startForegroundService(context, svc)
                }
                HeartbeatScheduler.schedule(context)
            } finally {
                pending.finish()
            }
        }
    }
}
