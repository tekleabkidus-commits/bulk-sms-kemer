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
 * We only finalize the recipient on the LAST part to avoid premature SENT / FAILED transitions.
 */
class SmsResultReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val recipientId = intent.getLongExtra(EXTRA_RECIPIENT_ID, -1L)
        val attemptNumber = intent.getIntExtra(EXTRA_ATTEMPT, 1)
        val partIndex = intent.getIntExtra(EXTRA_PART_INDEX, 0)
        val totalParts = intent.getIntExtra(EXTRA_TOTAL_PARTS, 1)
        val isLastPart = partIndex == totalParts - 1
        val code = resultCode

        if (recipientId <= 0L) return

        val app = context.applicationContext as BulkSmsApp
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                handle(app, recipientId, attemptNumber, code, isLastPart)
                // Wake the dispatcher in case it was idle waiting for this result.
                SmsDispatchService.signalResult()
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun handle(
        app: BulkSmsApp,
        recipientId: Long,
        attemptNumber: Int,
        code: Int,
        isLastPart: Boolean
    ) {
        // For non-OK results we can finalize early (any failed segment fails the whole message).
        if (code != Activity.RESULT_OK) {
            finalizeFailure(app.campaignRepo, recipientId, attemptNumber, code)
            return
        }
        // RESULT_OK: only finalize on last part. Earlier parts are no-op.
        if (isLastPart) {
            app.campaignRepo.markSent(recipientId, code)
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
