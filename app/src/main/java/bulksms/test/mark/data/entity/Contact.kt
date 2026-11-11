package bulksms.test.mark.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "contacts",
    foreignKeys = [
        ForeignKey(
            entity = ContactGroup::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("groupId"),
        Index(value = ["groupId", "normalizedPhone"], unique = true)
    ]
)
data class Contact(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val groupId: Long,
    val rawPhone: String,
    val normalizedPhone: String,   // E.164 always (+2519... or +2517...)
    val carrier: String,           // "Ethio Telecom" | "Safaricom"
    val addedAt: Long
)
