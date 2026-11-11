package bulksms.test.mark.ui.campaigns

import android.app.Application
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import bulksms.test.mark.BulkSmsApp
import bulksms.test.mark.data.entity.PauseReason
import bulksms.test.mark.data.entity.Recipient
import bulksms.test.mark.data.entity.RecipientStatus
import bulksms.test.mark.service.SmsDispatchService
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RunCampaignUiState(
    val name: String = "",
    val total: Int = 0,
    val pending: Int = 0,
    val sending: Int = 0,
    val sent: Int = 0,
    val delivered: Int = 0,
    val failed: Int = 0,
    val invalid: Int = 0,
    val skipped: Int = 0,
    val running: Boolean = false,
    val paused: Boolean = false,
    val pauseReason: String? = null,
    val pauseDetail: String? = null,
    val simLabel: String = "",
    val recent: List<Recipient> = emptyList(),
    val message: String = ""
)

class RunCampaignViewModel(app: Application) : AndroidViewModel(app) {
    private val a = app as BulkSmsApp

    fun stateFlow(campaignId: Long) = combine(
        a.campaignRepo.observeCampaign(campaignId),
        a.campaignRepo.observeStatusCounts(campaignId),
        a.campaignRepo.observeRecent(campaignId, 30)
    ) { campaign, counts, recent ->
        if (campaign == null) RunCampaignUiState()
        else {
            fun count(s: RecipientStatus) = counts.firstOrNull { it.status == s }?.count ?: 0
            RunCampaignUiState(
                name = campaign.name,
                total = campaign.totalCount,
                pending = count(RecipientStatus.PENDING),
                sending = count(RecipientStatus.SENDING),
                sent = count(RecipientStatus.SENT) + count(RecipientStatus.DELIVERED),
                delivered = count(RecipientStatus.DELIVERED),
                failed = count(RecipientStatus.FAILED),
                invalid = count(RecipientStatus.INVALID),
                skipped = count(RecipientStatus.SKIPPED),
                running = campaign.status == bulksms.test.mark.data.entity.CampaignStatus.RUNNING,
                paused = campaign.status == bulksms.test.mark.data.entity.CampaignStatus.PAUSED,
                pauseReason = campaign.pauseReason,
                pauseDetail = campaign.pauseDetail,
                simLabel = campaign.simDisplayLabel,
                recent = recent,
                message = campaign.message
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), RunCampaignUiState())

    fun pause(campaignId: Long) {
        viewModelScope.launch {
            a.campaignRepo.pause(campaignId, PauseReason.USER, null)
            SmsDispatchService.requestStop(getApplication())
        }
    }

    fun resume(campaignId: Long) {
        viewModelScope.launch {
            a.campaignRepo.startRunning(campaignId)
            val intent = android.content.Intent(getApplication(), SmsDispatchService::class.java)
                .putExtra(SmsDispatchService.EXTRA_CAMPAIGN_ID, campaignId)
            ContextCompat.startForegroundService(getApplication(), intent)
        }
    }

    fun cancel(campaignId: Long) {
        viewModelScope.launch {
            a.campaignRepo.cancel(campaignId)
            SmsDispatchService.requestStop(getApplication())
        }
    }

    fun retryAllFailed(campaignId: Long) {
        viewModelScope.launch {
            a.campaignRepo.retryAllFailed(campaignId)
            // If campaign already completed, move it back to RUNNING + start service.
            val campaign = a.campaignRepo.getCampaign(campaignId)
            if (campaign != null && campaign.status != bulksms.test.mark.data.entity.CampaignStatus.RUNNING) {
                a.campaignRepo.startRunning(campaignId)
                val intent = android.content.Intent(getApplication(), SmsDispatchService::class.java)
                    .putExtra(SmsDispatchService.EXTRA_CAMPAIGN_ID, campaignId)
                ContextCompat.startForegroundService(getApplication(), intent)
            }
        }
    }
}
