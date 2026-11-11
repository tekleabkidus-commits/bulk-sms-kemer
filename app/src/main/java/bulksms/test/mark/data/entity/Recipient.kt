package bulksms.test.mark.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "recipients",
    foreignKeys = [
        ForeignKey(
            entity = Campaign::class,
            parentColumns = ["id"],
            childColumns = ["campaignId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("campaignId"),
        Index(value = ["campaignId", "status"]),
        Index(value = ["campaignId", "status", "nextEligibleAt"]),
        Index("normalizedPhone"),
        Index("sentAt")
    ]
)
data class Recipient(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val campaignId: Long,
    val rowIndex: Int,
    val rawPhone: String,
    val normalizedPhone: String,
    val carrier: String,
    val status: RecipientStatus = RecipientStatus.PENDING,
    val attempts: Int = 0,
    val nextEligibleAt: Long? = null,        // for retry backoff scheduling
    val segmentCount: Int = 0,
    val lastError: String? = null,
    val smsResultCode: Int? = null,
    val sendingStartedAt: Long? = null,      // set when SENDING; used to detect stale
    val sentAt: Long? = null,
    val deliveredAt: Long? = null
)
