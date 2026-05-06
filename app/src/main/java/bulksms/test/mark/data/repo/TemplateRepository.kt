package bulksms.test.mark.data.repo

import bulksms.test.mark.data.db.TemplateDao
import bulksms.test.mark.data.entity.MessageTemplate

class TemplateRepository(private val dao: TemplateDao) {

    fun observeAll() = dao.observeAll()
    fun observeCount() = dao.observeCount()
    suspend fun getById(id: Long): MessageTemplate? = dao.getById(id)

    suspend fun create(name: String, content: String): Long {
        val now = System.currentTimeMillis()
        return dao.insert(MessageTemplate(name = name.trim(), content = content,
            createdAt = now, updatedAt = now))
    }

    suspend fun update(id: Long, name: String, content: String) {
        val t = dao.getById(id) ?: return
        dao.update(t.copy(name = name.trim(), content = content, updatedAt = System.currentTimeMillis()))
    }

    suspend fun delete(id: Long) {
        val t = dao.getById(id) ?: return
        dao.delete(t)
    }

    suspend fun touchUsed(id: Long) =
        dao.touchUsed(id, System.currentTimeMillis())
}
