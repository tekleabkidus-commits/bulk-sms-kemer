package bulksms.test.mark.data.db

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import bulksms.test.mark.data.entity.Contact
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {

    /** IGNORE strategy = silent dedup within the group (unique index on groupId+normalizedPhone). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(contacts: List<Contact>): List<Long>

    @Query("DELETE FROM contacts WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM contacts WHERE groupId = :groupId")
    suspend fun deleteAllInGroup(groupId: Long)

    @Query("SELECT COUNT(*) FROM contacts WHERE groupId = :groupId")
    suspend fun countInGroup(groupId: Long): Int

    @Query("SELECT COUNT(*) FROM contacts WHERE groupId = :groupId")
    fun observeCountInGroup(groupId: Long): Flow<Int>

    @Query("SELECT * FROM contacts WHERE groupId = :groupId ORDER BY id ASC")
    suspend fun listAllInGroup(groupId: Long): List<Contact>

    @Query("SELECT * FROM contacts WHERE groupId = :groupId ORDER BY addedAt DESC")
    fun pageInGroup(groupId: Long): PagingSource<Int, Contact>

    @Query("SELECT * FROM contacts WHERE groupId = :groupId AND normalizedPhone LIKE '%' || :query || '%' ORDER BY addedAt DESC")
    fun searchInGroup(groupId: Long, query: String): PagingSource<Int, Contact>
}
