package bulksms.test.mark.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "campaigns")
data class Campaign(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val message: String,                     // the actual rendered message body — same for all recipients
    val groupId: Long?,                      // source group (nullable — group could be deleted later)
    val groupName: String,                   // snapshot at campaign creation
    val templateId: Long?,                   // source template if any
    val simSubscriptionId: Int,
    val simDisplayLabel: String,
    val throttleMillis: Long,                // 500..10000
    val jitterEnabled: Boolean = false,
    val totalCount: Int,
    val segmentsPerMessage: Int,             // computed at creation, used for cost estimate
    val status: CampaignStatus,
    val pauseReason: String? = null,         // PauseReason.name
    val pauseDetail: String? = null,
    val createdAt: Long,
    val startedAt: Long? = null,
    val completedAt: Long? = null
)
