package bulksms.test.mark.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import bulksms.test.mark.data.entity.ContactGroup
import kotlinx.coroutines.flow.Flow

data class ContactGroupSummary(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val lastUsedAt: Long?,
    val notes: String?,
    val contactCount: Int
)

@Dao
interface ContactGroupDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(group: ContactGroup): Long

    @Update
    suspend fun update(group: ContactGroup)

    @Delete
    suspend fun delete(group: ContactGroup)

    @Query("SELECT * FROM contact_groups WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ContactGroup?

    @Query("""
        SELECT g.id, g.name, g.createdAt, g.updatedAt, g.lastUsedAt, g.notes,
               (SELECT COUNT(*) FROM contacts c WHERE c.groupId = g.id) AS contactCount
        FROM contact_groups g
        ORDER BY g.updatedAt DESC
    """)
    fun observeSummaries(): Flow<List<ContactGroupSummary>>

    @Query("""
        SELECT g.id, g.name, g.createdAt, g.updatedAt, g.lastUsedAt, g.notes,
               (SELECT COUNT(*) FROM contacts c WHERE c.groupId = g.id) AS contactCount
        FROM contact_groups g
        WHERE g.id = :id
    """)
    suspend fun getSummary(id: Long): ContactGroupSummary?

    @Query("UPDATE contact_groups SET lastUsedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun touchLastUsed(id: Long, now: Long)
}
