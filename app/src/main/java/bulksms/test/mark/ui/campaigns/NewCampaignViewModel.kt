package bulksms.test.mark.ui.campaigns

import android.app.Application
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import bulksms.test.mark.BulkSmsApp
import bulksms.test.mark.data.db.ContactGroupSummary
import bulksms.test.mark.data.entity.MessageTemplate
import bulksms.test.mark.data.preferences.AppPreferences
import bulksms.test.mark.data.repo.NewCampaignSpec
import bulksms.test.mark.data.sim.SimInfo
import bulksms.test.mark.data.sim.SimRepository
import bulksms.test.mark.data.sms.SegmentCounter
import bulksms.test.mark.service.SmsDispatchService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class NewCampaignState(
    val name: String = "",
    val message: String = "",
    val groups: List<ContactGroupSummary> = emptyList(),
    val templates: List<MessageTemplate> = emptyList(),
    val sims: List<SimInfo> = emptyList(),
    val selectedGroupId: Long? = null,
    val selectedTemplateId: Long? = null,
    val selectedSubId: Int? = null,
    val throttleMs: Long = 1_000L,
    val jitter: Boolean = false,
    val startRow: Int = 1,            // 1-based inclusive
    val endRow: Int? = null,          // 1-based inclusive; null = group end
    val anotherCampaignActive: Boolean = false,
    val activeCampaignId: Long? = null,
    val starting: Boolean = false,
    val errorText: String? = null
) {
    /** Number of recipients that will actually be sent given the current range. */
    val recipientsInRange: Int
        get() {
            val group = groups.firstOrNull { it.id == selectedGroupId } ?: return 0
            val total = group.contactCount
            if (total == 0) return 0
            val s = startRow.coerceIn(1, total)
            val e = (endRow ?: total).coerceIn(s, total)
            return e - s + 1
        }

    val rangeIsCustom: Boolean
        get() {
            val group = groups.firstOrNull { it.id == selectedGroupId } ?: return false
            return startRow != 1 || (endRow != null && endRow != group.contactCount)
        }

    val isReady: Boolean
        get() = name.isNotBlank() && message.isNotBlank() &&
            selectedGroupId != null && selectedSubId != null && !anotherCampaignActive &&
            recipientsInRange > 0
}

class NewCampaignViewModel(app: Application) : AndroidViewModel(app) {
    private val a = app as BulkSmsApp
    private val prefs = AppPreferences(app)
    private val simRepo = SimRepository(app)

    private val _state = MutableStateFlow(NewCampaignState())
    val state = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            val groups = a.contactRepo.observeGroupSummaries().first()
            val templates = a.templateRepo.observeAll().first()
            val sims = simRepo.listActive()
            val activeId = a.campaignRepo.activeCampaignId()
            _state.value = _state.value.copy(
                groups = groups,
                templates = templates,
                sims = sims,
                anotherCampaignActive = activeId != null,
                activeCampaignId = activeId,
                selectedSubId = _state.value.selectedSubId
                    ?: prefs.lastSelectedSubId.takeIf { id -> sims.any { it.subscriptionId == id } }
                    ?: sims.firstOrNull()?.subscriptionId,
                throttleMs = if (_state.value.throttleMs == 1_000L) prefs.lastThrottleMs.coerceIn(500L, 10_000L) else _state.value.throttleMs,
                jitter = if (_state.value.message.isEmpty()) prefs.lastJitter else _state.value.jitter
            )
        }
    }

    fun setName(s: String) { _state.value = _state.value.copy(name = s) }
    fun setMessage(s: String) { _state.value = _state.value.copy(message = s) }
    fun setGroup(id: Long) {
        val g = _state.value.groups.firstOrNull { it.id == id }
        _state.value = _state.value.copy(
            selectedGroupId = id,
            // Reset range to full when switching groups
            startRow = 1,
            endRow = null,
            name = if (_state.value.name.isBlank() && g != null) defaultCampaignName(g.name) else _state.value.name
        )
    }
    fun setStartRow(value: Int) {
        val total = _state.value.groups.firstOrNull { it.id == _state.value.selectedGroupId }?.contactCount ?: 0
        val clamped = if (total == 0) 1 else value.coerceIn(1, total)
        _state.value = _state.value.copy(startRow = clamped)
    }
    fun setEndRow(value: Int?) {
        if (value == null) {
            _state.value = _state.value.copy(endRow = null)
            return
        }
        val total = _state.value.groups.firstOrNull { it.id == _state.value.selectedGroupId }?.contactCount ?: 0
        val s = _state.value.startRow
        val clamped = if (total == 0) s else value.coerceIn(s, total)
        _state.value = _state.value.copy(endRow = clamped)
    }
    fun resetRange() {
        _state.value = _state.value.copy(startRow = 1, endRow = null)
    }
    fun setTemplate(t: MessageTemplate) {
        _state.value = _state.value.copy(selectedTemplateId = t.id, message = t.content)
    }
    fun clearTemplate() {
        _state.value = _state.value.copy(selectedTemplateId = null)
    }
    fun setSim(subId: Int) {
        prefs.lastSelectedSubId = subId
        _state.value = _state.value.copy(selectedSubId = subId)
    }
    fun setThrottle(ms: Long) {
        val clamped = ms.coerceIn(500L, 10_000L)
        prefs.lastThrottleMs = clamped
        _state.value = _state.value.copy(throttleMs = clamped)
    }
    fun setJitter(on: Boolean) {
        prefs.lastJitter = on
        _state.value = _state.value.copy(jitter = on)
    }

    fun segmentsPerMessage(): Int = SegmentCounter.count(_state.value.message).segments
    fun encoding(): SegmentCounter.Result = SegmentCounter.count(_state.value.message)

    fun selectedGroup(): ContactGroupSummary? =
        _state.value.groups.firstOrNull { it.id == _state.value.selectedGroupId }

    fun selectedSim(): SimInfo? =
        _state.value.sims.firstOrNull { it.subscriptionId == _state.value.selectedSubId }

    /**
     * Atomically create the campaign + recipients and start the dispatch service.
     * Returns the new campaign id, or null on error.
     */
    suspend fun startCampaign(): Long? {
        val s = _state.value
        if (!s.isReady) return null
        _state.value = s.copy(starting = true, errorText = null)

        return try {
            val template = s.selectedTemplateId?.let { a.templateRepo.getById(it) }
            val group = selectedGroup() ?: error("Group not selected")
            val sim = selectedSim() ?: error("SIM not selected")

            val spec = NewCampaignSpec(
                name = s.name.trim(),
                message = s.message,
                groupId = group.id,
                groupName = group.name,
                templateId = template?.id,
                simSubscriptionId = sim.subscriptionId,
                simDisplayLabel = sim.label,
                throttleMillis = s.throttleMs,
                jitterEnabled = s.jitter,
                segmentsPerMessage = segmentsPerMessage(),
                startRow = s.startRow,
                endRow = s.endRow
            )

            val id = a.campaignRepo.createCampaign(spec)
            template?.let { a.templateRepo.touchUsed(it.id) }
            a.contactRepo.touchUsed(group.id)
            a.campaignRepo.startRunning(id)

            val intent = android.content.Intent(getApplication(), SmsDispatchService::class.java)
                .putExtra(SmsDispatchService.EXTRA_CAMPAIGN_ID, id)
            ContextCompat.startForegroundService(getApplication(), intent)

            id
        } catch (t: Throwable) {
            _state.value = _state.value.copy(starting = false, errorText = t.message ?: "Failed")
            null
        }
    }

    private fun defaultCampaignName(groupName: String): String =
        "$groupName · ${java.text.SimpleDateFormat("MMM d HH:mm", java.util.Locale.US)
            .format(java.util.Date())}"
}
