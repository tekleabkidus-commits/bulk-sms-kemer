package bulksms.test.mark.data.repo

import androidx.room.withTransaction
import bulksms.test.mark.data.db.AppDatabase
import bulksms.test.mark.data.db.CampaignDao
import bulksms.test.mark.data.db.ContactDao
import bulksms.test.mark.data.db.RecipientDao
import bulksms.test.mark.data.entity.Campaign
import bulksms.test.mark.data.entity.CampaignStatus
import bulksms.test.mark.data.entity.PauseReason
import bulksms.test.mark.data.entity.Recipient
import bulksms.test.mark.data.entity.RecipientStatus

data class NewCampaignSpec(
    val name: String,
    val message: String,
    val groupId: Long,
    val groupName: String,
    val templateId: Long?,
    val simSubscriptionId: Int,
    val simDisplayLabel: String,
    val throttleMillis: Long,
    val jitterEnabled: Boolean,
    val segmentsPerMessage: Int,
    /** 1-based inclusive starting row in the contact group. Default 1 = beginning. */
    val startRow: Int = 1,
    /** 1-based inclusive ending row. null = send to last row. */
    val endRow: Int? = null
)

class CampaignRepository(
    private val campaignDao: CampaignDao,
    private val recipientDao: RecipientDao,
    private val contactDao: ContactDao
) {
    /** Full database reference is needed for atomic creation (transaction across multiple DAOs). */
    @Volatile private var dbRef: AppDatabase? = null
    fun attachDb(db: AppDatabase) { dbRef = db }

    // ---------- Lifecycle ----------

    suspend fun createCampaign(spec: NewCampaignSpec): Long {
        val now = System.currentTimeMillis()
        val allContacts = contactDao.listAllInGroup(spec.groupId)
        if (allContacts.isEmpty()) error("Group is empty")

        // Apply 1-based inclusive range. Coerce to safe bounds.
        val startIdx = (spec.startRow.coerceAtLeast(1) - 1).coerceAtMost(allContacts.size - 1)
        val endIdx = (spec.endRow ?: allContacts.size).coerceIn(startIdx + 1, allContacts.size)
        val contacts = allContacts.subList(startIdx, endIdx)
        if (contacts.isEmpty()) error("No recipients in selected range")

        val rangeNote = if (spec.startRow > 1 || spec.endRow != null) {
            " (rows ${startIdx + 1}–$endIdx of ${allContacts.size})"
        } else ""

        val campaign = Campaign(
            name = spec.name.trim(),
            message = spec.message,
            groupId = spec.groupId,
            groupName = spec.groupName + rangeNote,
            templateId = spec.templateId,
            simSubscriptionId = spec.simSubscriptionId,
            simDisplayLabel = spec.simDisplayLabel,
            throttleMillis = spec.throttleMillis,
            jitterEnabled = spec.jitterEnabled,
            totalCount = contacts.size,
            segmentsPerMessage = spec.segmentsPerMessage,
            status = CampaignStatus.DRAFT,
            createdAt = now
        )

        val db = dbRef
        return if (db != null) {
            db.withTransaction {
                val id = campaignDao.insert(campaign)
                val recipients = contacts.mapIndexed { localIdx, c ->
                    Recipient(
                        campaignId = id,
                        // Preserve original 0-based row position so reports show the true
                        // location in the source list, not the local slice index.
                        rowIndex = startIdx + localIdx,
                        rawPhone = c.rawPhone,
                        normalizedPhone = c.normalizedPhone,
                        carrier = c.carrier
                    )
                }
                recipientDao.insertAll(recipients)
                id
            }
        } else {
            // Fallback (dbRef should always be attached, but keep behavior safe)
            val id = campaignDao.insert(campaign)
            val recipients = contacts.mapIndexed { localIdx, c ->
                Recipient(
                    campaignId = id, rowIndex = startIdx + localIdx,
                    rawPhone = c.rawPhone, normalizedPhone = c.normalizedPhone, carrier = c.carrier
                )
            }
            recipientDao.insertAll(recipients)
            id
        }
    }

    suspend fun startRunning(id: Long) =
        campaignDao.setRunning(id, System.currentTimeMillis())

    suspend fun pause(id: Long, reason: PauseReason, detail: String? = null) =
        campaignDao.setPaused(id, reason.name, detail)

    suspend fun complete(id: Long) =
        campaignDao.setCompleted(id, System.currentTimeMillis())

    suspend fun cancel(id: Long) =
        campaignDao.setCancelled(id, System.currentTimeMillis())

    suspend fun resetSendingOnRestart(id: Long) {
        // 5-minute stale window — anything in SENDING older than that we assume actually went out
        // (avoid duplicates). Anything fresher we rewind to PENDING for clean retry.
        val now = System.currentTimeMillis()
        recipientDao.recoverStaleSending(now, now - STALE_SENDING_MS)
        recipientDao.resetSending(id)
    }

    suspend fun activeCampaignId(): Long? = campaignDao.activeCampaignId()
    suspend fun getCampaign(id: Long): Campaign? = campaignDao.getById(id)
    fun observeCampaign(id: Long) = campaignDao.observeById(id)
    fun observeRow(id: Long) = campaignDao.observeRow(id)
    fun observeAllRows() = campaignDao.observeAllRows()

    // ---------- Dispatch helpers (used by the service) ----------

    suspend fun nextEligible(campaignId: Long): Recipient? =
        recipientDao.nextEligible(campaignId, System.currentTimeMillis())

    suspend fun nextRetryTime(campaignId: Long): Long? =
        recipientDao.nextRetryTime(campaignId)

    suspend fun pendingCount(campaignId: Long): Int =
        recipientDao.pendingCount(campaignId)

    suspend fun markSending(id: Long, segments: Int) =
        recipientDao.markSending(id, System.currentTimeMillis(), segments)

    suspend fun markSent(id: Long, code: Int) =
        recipientDao.markSent(id, System.currentTimeMillis(), code)

    suspend fun markDelivered(id: Long) =
        recipientDao.markDelivered(id, System.currentTimeMillis())

    suspend fun markUnknown(id: Long, reason: String) =
        recipientDao.markUnknown(id, reason)

    suspend fun markFailed(id: Long, err: String, code: Int?) =
        recipientDao.markFailed(id, err, code)

    suspend fun markRetryLater(id: Long, attemptNumber: Int, err: String, code: Int?) {
        // Backoff schedule (locked): 30s, 1min, 2min, 5min — across 4 attempts then FAILED.
        // attemptNumber here is the attempt that just FAILED (1-based).
        val backoff = when (attemptNumber) {
            1 -> 30_000L
            2 -> 60_000L
            3 -> 120_000L
            else -> 300_000L
        }
        val nextAt = System.currentTimeMillis() + backoff
        recipientDao.markRetryLater(id, nextAt, err, code)
    }

    /**
     * Salvage a recipient whose broadcast arrived during a known network interrupt
     * window. Resets to PENDING with attempts decremented (so this attempt doesn't count
     * toward the retry budget) and schedules retry in ~10 seconds. Called by the receiver
     * when SmsDispatchService.recentlyInterrupted() returns true.
     */
    suspend fun salvageRecipient(id: Long) {
        val nextAt = System.currentTimeMillis() + 10_000L
        recipientDao.salvageRecipient(id, nextAt)
    }

    // ---------- Recipient queries ----------

    fun observeStatusCounts(id: Long) = recipientDao.observeStatusCounts(id)
    fun observeRecent(id: Long, limit: Int = 30) = recipientDao.observeRecent(id, limit)
    fun pageInCampaign(id: Long) = recipientDao.pageInCampaign(id)
    fun pageInCampaignByStatus(id: Long, statuses: List<RecipientStatus>) =
        recipientDao.pageInCampaignByStatus(id, statuses)

    suspend fun listAllInCampaign(id: Long): List<bulksms.test.mark.data.entity.Recipient> =
        recipientDao.listAllInCampaign(id)

    suspend fun retryAllFailed(id: Long): Int = recipientDao.retryAllFailed(id)
    suspend fun retryOne(recipientId: Long) = recipientDao.retryOne(recipientId)

    // ---------- Sent Messages tab ----------

    fun pageSentMessages(
        campaignId: Long?,
        fromTs: Long?,
        toTs: Long?,
        query: String,
        filterStatus: RecipientStatus?
    ) = recipientDao.pageSentMessages(campaignId, fromTs, toTs, query, filterStatus)

    fun observeSentStatusCounts(
        campaignId: Long?,
        fromTs: Long?,
        toTs: Long?,
        query: String
    ) = recipientDao.observeSentStatusCounts(campaignId, fromTs, toTs, query)

    companion object {
        const val MAX_ATTEMPTS = 4
        const val STALE_SENDING_MS: Long = 5 * 60 * 1000L
    }
}
