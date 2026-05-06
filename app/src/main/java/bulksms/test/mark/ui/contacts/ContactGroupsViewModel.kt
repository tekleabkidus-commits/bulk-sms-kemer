package bulksms.test.mark.ui.contacts

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import bulksms.test.mark.BulkSmsApp
import kotlinx.coroutines.launch

class ContactGroupsViewModel(app: Application) : AndroidViewModel(app) {
    private val a = app as BulkSmsApp
    val groups = a.contactRepo.observeGroupSummaries()

    fun create(name: String, onResult: (Long) -> Unit) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val id = a.contactRepo.createGroup(name)
            onResult(id)
        }
    }

    fun rename(id: Long, newName: String) {
        if (newName.isBlank()) return
        viewModelScope.launch { a.contactRepo.renameGroup(id, newName) }
    }

    fun delete(id: Long) {
        viewModelScope.launch { a.contactRepo.deleteGroup(id) }
    }
}
