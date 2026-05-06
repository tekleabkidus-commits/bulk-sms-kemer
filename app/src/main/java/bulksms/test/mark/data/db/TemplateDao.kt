package bulksms.test.mark.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import bulksms.test.mark.data.entity.MessageTemplate
import kotlinx.coroutines.flow.Flow

@Dao
interface TemplateDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(t: MessageTemplate): Long

    @Update
    suspend fun update(t: MessageTemplate)

    @Delete
    suspend fun delete(t: MessageTemplate)

    @Query("SELECT * FROM templates WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): MessageTemplate?

    @Query("SELECT * FROM templates ORDER BY (lastUsedAt IS NULL), lastUsedAt DESC, updatedAt DESC")
    fun observeAll(): Flow<List<MessageTemplate>>

    @Query("UPDATE templates SET lastUsedAt = :now, useCount = useCount + 1 WHERE id = :id")
    suspend fun touchUsed(id: Long, now: Long)

    @Query("SELECT COUNT(*) FROM templates")
    fun observeCount(): Flow<Int>
}
