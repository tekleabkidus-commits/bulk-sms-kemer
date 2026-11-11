package bulksms.test.mark.service

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager
import bulksms.test.mark.BulkSmsApp
import bulksms.test.mark.data.repo.CampaignRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Registered programmatically by SmsDispatchService. Fires once per multipart segment.
 * Aggregate ALL sent callbacks before finalizing; any partial multipart failure is UNKNOWN.
 */
class SmsResultReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val recipientId = intent.getLongExtra(EXTRA_RECIPIENT_ID, -1L)
        val attemptNumber = intent.getIntExtra(EXTRA_ATTEMPT, 1)
        val partIndex = intent.getIntExtra(EXTRA_PART_INDEX, 0)
        val totalParts = intent.getIntExtra(EXTRA_TOTAL_PARTS, 1)
        val code = resultCode

        if (recipientId <= 0L) return

        val app = context.applicationContext as BulkSmsApp
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                handle(context, app, recipientId, attemptNumber, code, partIndex, totalParts)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun handle(
        context: Context,
        app: BulkSmsApp,
        recipientId: Long,
        attemptNumber: Int,
        code: Int,
        partIndex: Int,
        totalParts: Int
    ) {
        // Idempotency: only act if this recipient is still in SENDING state. If a
        // previous broadcast (e.g., an earlier part that failed) already finalized this
        // recipient, ignore subsequent broadcasts so we don't double-count.
        val current = app.database.recipientDao().getById(recipientId) ?: return
        if (current.status != bulksms.test.mark.data.entity.RecipientStatus.SENDING) return
        if (current.attempts != attemptNumber) return // stale callback from prior attempt

        // Distrust broadcasts that arrive during the 10-second window following a network
        // interrupt (incoming call, SIM dropping briefly). The Android SmsManager API
        // commonly broadcasts RESULT_OK during these windows even when the modem never
        // actually transmitted the message — the system's own messaging app then shows
        // the SMS as failed. We salvage these recipients back to PENDING so they get
        // re-sent fresh once the interrupt clears.
        if (SmsDispatchService.recentlyInterrupted()) {
            app.campaignRepo.markUnknown(recipientId, "Network interrupt near SMS submission")
            return
        }

        when (SmsSegmentAckStore.sent(context,recipientId,attemptNumber,partIndex,totalParts,code==Activity.RESULT_OK)) {
            SmsSegmentAckStore.Outcome.WAIT -> return
            SmsSegmentAckStore.Outcome.AMBIGUOUS -> {
                app.campaignRepo.markUnknown(recipientId, "Multipart SMS partially sent or failed; manual review")
                SmsDispatchService.recordFailure(isGeneric=code==SmsManager.RESULT_ERROR_GENERIC_FAILURE)
            }
            SmsSegmentAckStore.Outcome.FAILURE -> {
                finalizeFailure(app.campaignRepo,recipientId,attemptNumber,code)
                SmsDispatchService.recordFailure(isGeneric=code==SmsManager.RESULT_ERROR_GENERIC_FAILURE)
            }
            SmsSegmentAckStore.Outcome.COMPLETE -> {
                app.campaignRepo.markSent(recipientId,code)
                if(SmsSegmentAckStore.deliveryComplete(context,recipientId,totalParts))
                    app.campaignRepo.markDelivered(recipientId)
                SmsDispatchService.recordSuccess()
            }
        }
    }

    private suspend fun finalizeFailure(
        repo: CampaignRepository,
        recipientId: Long,
        attemptNumber: Int,
        code: Int
    ) {
        val errText = errorText(code)
        if (attemptNumber >= CampaignRepository.MAX_ATTEMPTS) {
            repo.markFailed(recipientId, errText, code)
        } else {
            repo.markRetryLater(recipientId, attemptNumber, errText, code)
        }
    }

    private fun errorText(code: Int): String = when (code) {
        SmsManager.RESULT_ERROR_GENERIC_FAILURE -> "Generic failure"
        SmsManager.RESULT_ERROR_RADIO_OFF -> "Radio off"
        SmsManager.RESULT_ERROR_NULL_PDU -> "Null PDU"
        SmsManager.RESULT_ERROR_NO_SERVICE -> "No service"
        SmsManager.RESULT_ERROR_LIMIT_EXCEEDED -> "Carrier limit"
        SmsManager.RESULT_ERROR_FDN_CHECK_FAILURE -> "FDN check failure"
        SmsManager.RESULT_ERROR_SHORT_CODE_NOT_ALLOWED -> "Short code blocked"
        SmsManager.RESULT_ERROR_SHORT_CODE_NEVER_ALLOWED -> "Short code blocked"
        else -> "Error $code"
    }

    companion object {
        const val ACTION_SMS_SENT = "bulksms.test.mark.SMS_SENT"
        const val EXTRA_RECIPIENT_ID = "rid"
        const val EXTRA_ATTEMPT = "attempt"
        const val EXTRA_PART_INDEX = "pidx"
        const val EXTRA_TOTAL_PARTS = "ptot"
    }
}
