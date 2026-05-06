package bulksms.test.mark.data.db

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import bulksms.test.mark.data.entity.Recipient
import bulksms.test.mark.data.entity.RecipientStatus
import kotlinx.coroutines.flow.Flow

data class RecipientStatusCount(val status: RecipientStatus, val count: Int)

data class SentMessageRow(
    val id: Long,
    val campaignId: Long,
    val campaignName: String,
    val groupName: String,
    val simLabel: String,
    val rawPhone: String,
    val normalizedPhone: String,
    val carrier: String,
    val status: RecipientStatus,
    val attempts: Int,
    val segmentCount: Int,
    val lastError: String?,
    val sentAt: Long?,
    val deliveredAt: Long?,
    val message: String
)

@Dao
interface RecipientDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(recipients: List<Recipient>)

    @Query("SELECT * FROM recipients WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): Recipient?

    @Query("""
        SELECT * FROM recipients
        WHERE campaignId = :campaignId
          AND status = 'PENDING'
          AND (nextEligibleAt IS NULL OR nextEligibleAt <= :now)
        ORDER BY nextEligibleAt ASC, id ASC
        LIMIT 1
    """)
    suspend fun nextEligible(campaignId: Long, now: Long): Recipient?

    /** When does the next retry become eligible? Used to wake up the dispatcher precisely. */
    @Query("""
        SELECT MIN(nextEligibleAt) FROM recipients
        WHERE campaignId = :campaignId AND status = 'PENDING' AND nextEligibleAt IS NOT NULL
    """)
    suspend fun nextRetryTime(campaignId: Long): Long?

    @Query("""
        UPDATE recipients
        SET status = 'SENDING',
            attempts = attempts + 1,
            sendingStartedAt = :now,
            segmentCount = :segments
        WHERE id = :id
    """)
    suspend fun markSending(id: Long, now: Long, segments: Int)

    @Query("""
        UPDATE recipients
        SET status = 'SENT', sentAt = :now, smsResultCode = :code, lastError = NULL
        WHERE id = :id
    """)
    suspend fun markSent(id: Long, now: Long, code: Int)

    @Query("UPDATE recipients SET status = 'DELIVERED', deliveredAt = :now WHERE id = :id")
    suspend fun markDelivered(id: Long, now: Long)

    @Query("""
        UPDATE recipients
        SET status = 'FAILED', lastError = :err, smsResultCode = :code
        WHERE id = :id
    """)
    suspend fun markFailed(id: Long, err: String, code: Int?)

    @Query("""
        UPDATE recipients
        SET status = 'PENDING', nextEligibleAt = :nextAt, lastError = :err, smsResultCode = :code
        WHERE id = :id
    """)
    suspend fun markRetryLater(id: Long, nextAt: Long, err: String, code: Int?)

    /**
     * Recover stale SENDING entries: if the SENT broadcast never arrived and the row has been
     * "SENDING" for more than [staleMs], assume it was actually sent (we don't want duplicates).
     */
    @Query("""
        UPDATE recipients
        SET status = 'SENT', sentAt = COALESCE(sentAt, :now), lastError = 'Stale-recovered'
        WHERE status = 'SENDING' AND sendingStartedAt IS NOT NULL AND sendingStartedAt < :cutoff
    """)
    suspend fun recoverStaleSending(now: Long, cutoff: Long): Int

    @Query("SELECT status AS status, COUNT(*) AS count FROM recipients WHERE campaignId = :id GROUP BY status")
    fun observeStatusCounts(id: Long): Flow<List<RecipientStatusCount>>

    @Query("""
        SELECT * FROM recipients WHERE campaignId = :id
        ORDER BY
          CASE WHEN sentAt IS NULL THEN 1 ELSE 0 END ASC,
          sentAt DESC, id DESC
        LIMIT :limit
    """)
    fun observeRecent(id: Long, limit: Int = 30): Flow<List<Recipient>>

    @Query("""
        SELECT * FROM recipients WHERE campaignId = :id
        ORDER BY
          CASE WHEN sentAt IS NULL THEN 1 ELSE 0 END ASC,
          sentAt DESC, id DESC
    """)
    fun pageInCampaign(id: Long): PagingSource<Int, Recipient>

    @Query("""
        SELECT * FROM recipients
        WHERE campaignId = :id AND status IN (:statuses)
        ORDER BY
          CASE WHEN sentAt IS NULL THEN 1 ELSE 0 END ASC,
          sentAt DESC, id DESC
    """)
    fun pageInCampaignByStatus(id: Long, statuses: List<RecipientStatus>): PagingSource<Int, Recipient>

    @Query("SELECT * FROM recipients WHERE campaignId = :id ORDER BY id ASC")
    suspend fun listAllInCampaign(id: Long): List<Recipient>

    @Query("SELECT COUNT(*) FROM recipients WHERE campaignId = :id AND status = 'PENDING'")
    suspend fun pendingCount(id: Long): Int

    /** Reset any SENDING rows back to PENDING for clean dispatch resume (called rarely). */
    @Query("UPDATE recipients SET status = 'PENDING' WHERE campaignId = :id AND status = 'SENDING'")
    suspend fun resetSending(id: Long)

    /** Re-queue all FAILED rows in a campaign for manual retry. */
    @Query("UPDATE recipients SET status = 'PENDING', nextEligibleAt = NULL, lastError = NULL, attempts = 0 WHERE campaignId = :id AND status = 'FAILED'")
    suspend fun retryAllFailed(id: Long): Int

    /** Re-queue a single FAILED row. */
    @Query("UPDATE recipients SET status = 'PENDING', nextEligibleAt = NULL, lastError = NULL, attempts = 0 WHERE id = :id")
    suspend fun retryOne(id: Long)

    // ---------- Sent Messages tab (cross-campaign) ----------

    @Query("""
        SELECT r.id AS id, r.campaignId AS campaignId, c.name AS campaignName,
               c.groupName AS groupName, c.simDisplayLabel AS simLabel,
               r.rawPhone AS rawPhone, r.normalizedPhone AS normalizedPhone, r.carrier AS carrier,
               r.status AS status, r.attempts AS attempts, r.segmentCount AS segmentCount,
               r.lastError AS lastError, r.sentAt AS sentAt, r.deliveredAt AS deliveredAt,
               c.message AS message
        FROM recipients r INNER JOIN campaigns c ON r.campaignId = c.id
        WHERE (:campaignId IS NULL OR r.campaignId = :campaignId)
          AND (:fromTs IS NULL OR (r.sentAt IS NOT NULL AND r.sentAt >= :fromTs))
          AND (:toTs IS NULL OR (r.sentAt IS NOT NULL AND r.sentAt <= :toTs))
          AND (:query = '' OR r.normalizedPhone LIKE '%' || :query || '%')
          AND (:filterStatus IS NULL OR r.status = :filterStatus)
        ORDER BY
          CASE WHEN r.sentAt IS NULL THEN 1 ELSE 0 END ASC,
          r.sentAt DESC, r.id DESC
    """)
    fun pageSentMessages(
        campaignId: Long?,
        fromTs: Long?,
        toTs: Long?,
        query: String,
        filterStatus: RecipientStatus?
    ): PagingSource<Int, SentMessageRow>

    @Query("""
        SELECT r.status AS status, COUNT(*) AS count
        FROM recipients r INNER JOIN campaigns c ON r.campaignId = c.id
        WHERE (:campaignId IS NULL OR r.campaignId = :campaignId)
          AND (:fromTs IS NULL OR (r.sentAt IS NOT NULL AND r.sentAt >= :fromTs))
          AND (:toTs IS NULL OR (r.sentAt IS NOT NULL AND r.sentAt <= :toTs))
          AND (:query = '' OR r.normalizedPhone LIKE '%' || :query || '%')
        GROUP BY r.status
    """)
    fun observeSentStatusCounts(
        campaignId: Long?,
        fromTs: Long?,
        toTs: Long?,
        query: String
    ): Flow<List<RecipientStatusCount>>
}
