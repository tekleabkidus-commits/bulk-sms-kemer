package bulksms.test.mark.service

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import bulksms.test.mark.BulkSmsApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SmsDeliveryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val recipientId = intent.getLongExtra(SmsResultReceiver.EXTRA_RECIPIENT_ID, -1L)
        val partIndex = intent.getIntExtra(SmsResultReceiver.EXTRA_PART_INDEX, 0)
        val totalParts = intent.getIntExtra(SmsResultReceiver.EXTRA_TOTAL_PARTS, 1)
        if (recipientId <= 0L || resultCode != Activity.RESULT_OK) return
        if(!SmsSegmentAckStore.delivered(context,recipientId,partIndex,totalParts,true)) return
        val app = context.applicationContext as BulkSmsApp
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                app.campaignRepo.markDelivered(recipientId)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_SMS_DELIVERED = "bulksms.test.mark.SMS_DELIVERED"
    }
}
