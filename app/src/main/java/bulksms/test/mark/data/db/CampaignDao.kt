package bulksms.test.mark.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import bulksms.test.mark.data.entity.Campaign
import bulksms.test.mark.data.entity.CampaignStatus
import kotlinx.coroutines.flow.Flow

data class CampaignRow(
    val id: Long,
    val name: String,
    val message: String,
    val groupName: String,
    val simDisplayLabel: String,
    val totalCount: Int,
    val sentCount: Int,
    val deliveredCount: Int,
    val failedCount: Int,
    val status: CampaignStatus,
    val pauseReason: String?,
    val pauseDetail: String?,
    val createdAt: Long,
    val startedAt: Long?,
    val completedAt: Long?
)

@Dao
interface CampaignDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(campaign: Campaign): Long

    @Update
    suspend fun update(campaign: Campaign)

    @Query("SELECT * FROM campaigns WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): Campaign?

    @Query("SELECT * FROM campaigns WHERE id = :id LIMIT 1")
    fun observeById(id: Long): Flow<Campaign?>

    @Query("SELECT * FROM campaigns WHERE status = :status ORDER BY id DESC")
    suspend fun listByStatus(status: CampaignStatus): List<Campaign>

    @Query("SELECT id FROM campaigns WHERE status IN ('RUNNING', 'PAUSED') LIMIT 1")
    suspend fun activeCampaignId(): Long?

    @Query("UPDATE campaigns SET status = :status WHERE id = :id")
    suspend fun setStatus(id: Long, status: CampaignStatus)

    @Query("UPDATE campaigns SET status = 'PAUSED', pauseReason = :reason, pauseDetail = :detail WHERE id = :id")
    suspend fun setPaused(id: Long, reason: String, detail: String?)

    @Query("UPDATE campaigns SET status = 'RUNNING', pauseReason = NULL, pauseDetail = NULL, startedAt = COALESCE(startedAt, :now) WHERE id = :id")
    suspend fun setRunning(id: Long, now: Long)

    @Query("UPDATE campaigns SET status = 'COMPLETED', completedAt = :now WHERE id = :id")
    suspend fun setCompleted(id: Long, now: Long)

    @Query("UPDATE campaigns SET status = 'CANCELLED', completedAt = :now WHERE id = :id")
    suspend fun setCancelled(id: Long, now: Long)

    @Query("""
        SELECT
          c.id, c.name, c.message, c.groupName, c.simDisplayLabel, c.totalCount,
          (SELECT COUNT(*) FROM recipients r WHERE r.campaignId = c.id AND r.status IN ('SENT','DELIVERED')) AS sentCount,
          (SELECT COUNT(*) FROM recipients r WHERE r.campaignId = c.id AND r.status = 'DELIVERED') AS deliveredCount,
          (SELECT COUNT(*) FROM recipients r WHERE r.campaignId = c.id AND r.status = 'FAILED') AS failedCount,
          c.status, c.pauseReason, c.pauseDetail, c.createdAt, c.startedAt, c.completedAt
        FROM campaigns c
        ORDER BY c.id DESC
    """)
    fun observeAllRows(): Flow<List<CampaignRow>>

    @Query("""
        SELECT
          c.id, c.name, c.message, c.groupName, c.simDisplayLabel, c.totalCount,
          (SELECT COUNT(*) FROM recipients r WHERE r.campaignId = c.id AND r.status IN ('SENT','DELIVERED')) AS sentCount,
          (SELECT COUNT(*) FROM recipients r WHERE r.campaignId = c.id AND r.status = 'DELIVERED') AS deliveredCount,
          (SELECT COUNT(*) FROM recipients r WHERE r.campaignId = c.id AND r.status = 'FAILED') AS failedCount,
          c.status, c.pauseReason, c.pauseDetail, c.createdAt, c.startedAt, c.completedAt
        FROM campaigns c WHERE c.id = :id
    """)
    fun observeRow(id: Long): Flow<CampaignRow?>
}
