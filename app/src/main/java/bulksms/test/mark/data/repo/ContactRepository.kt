package bulksms.test.mark.data.repo

import bulksms.test.mark.data.db.ContactDao
import bulksms.test.mark.data.db.ContactGroupDao
import bulksms.test.mark.data.db.ContactGroupSummary
import bulksms.test.mark.data.entity.Contact
import bulksms.test.mark.data.entity.ContactGroup
import bulksms.test.mark.data.excel.ParseResult
import bulksms.test.mark.data.sms.PhoneNormalizer

class ContactRepository(
    private val groupDao: ContactGroupDao,
    private val contactDao: ContactDao
) {
    fun observeGroupSummaries() = groupDao.observeSummaries()
    suspend fun getGroupSummary(id: Long): ContactGroupSummary? = groupDao.getSummary(id)
    suspend fun getGroup(id: Long): ContactGroup? = groupDao.getById(id)

    suspend fun createGroup(name: String, notes: String? = null): Long {
        val now = System.currentTimeMillis()
        return groupDao.insert(ContactGroup(name = name.trim(), createdAt = now, updatedAt = now, notes = notes))
    }

    suspend fun renameGroup(id: Long, newName: String) {
        val g = groupDao.getById(id) ?: return
        groupDao.update(g.copy(name = newName.trim(), updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteGroup(id: Long) {
        val g = groupDao.getById(id) ?: return
        groupDao.delete(g)  // CASCADE drops contacts.
    }

    /**
     * Bulk-insert contacts from a parsed file. Silent dedup is enforced by the unique index
     * (groupId, normalizedPhone) and IGNORE conflict strategy.
     */
    suspend fun addContactsToGroup(groupId: Long, parsed: ParseResult): Int {
        val now = System.currentTimeMillis()
        val rows = parsed.rawValidPairs.map { (raw, n) ->
            Contact(
                groupId = groupId,
                rawPhone = raw,
                normalizedPhone = n,
                carrier = PhoneNormalizer.carrierOf(n),
                addedAt = now
            )
        }
        if (rows.isEmpty()) return 0
        val ids = contactDao.insertAll(rows)
        val inserted = ids.count { it != -1L }
        if (inserted > 0) {
            val g = groupDao.getById(groupId)
            if (g != null) groupDao.update(g.copy(updatedAt = now))
        }
        return inserted
    }

    suspend fun addSingleNumber(groupId: Long, raw: String): Boolean {
        val n = PhoneNormalizer.normalizeEthiopian(raw) ?: return false
        val now = System.currentTimeMillis()
        val ids = contactDao.insertAll(listOf(
            Contact(groupId = groupId, rawPhone = raw, normalizedPhone = n,
                carrier = PhoneNormalizer.carrierOf(n), addedAt = now)
        ))
        val ok = ids.firstOrNull() != -1L
        if (ok) {
            val g = groupDao.getById(groupId)
            if (g != null) groupDao.update(g.copy(updatedAt = now))
        }
        return ok
    }

    suspend fun removeContact(contactId: Long) = contactDao.deleteById(contactId)
    suspend fun listAllInGroup(groupId: Long) = contactDao.listAllInGroup(groupId)
    fun pageInGroup(groupId: Long) = contactDao.pageInGroup(groupId)
    fun searchInGroup(groupId: Long, query: String) = contactDao.searchInGroup(groupId, query)
    fun observeCount(groupId: Long) = contactDao.observeCountInGroup(groupId)

    suspend fun touchUsed(groupId: Long) =
        groupDao.touchLastUsed(groupId, System.currentTimeMillis())
}
