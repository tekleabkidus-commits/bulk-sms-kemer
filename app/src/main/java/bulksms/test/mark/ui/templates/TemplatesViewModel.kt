package bulksms.test.mark.ui.templates

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import bulksms.test.mark.BulkSmsApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class TemplatesViewModel(app: Application) : AndroidViewModel(app) {
    private val a = app as BulkSmsApp

    private val _query = MutableStateFlow("")
    val query = _query.asStateFlow()

    val templates = combine(a.templateRepo.observeAll(), _query) { all, q ->
        if (q.isBlank()) all
        else all.filter { it.name.contains(q, ignoreCase = true) || it.content.contains(q, ignoreCase = true) }
    }

    fun setQuery(q: String) { _query.value = q }

    fun delete(id: Long) {
        viewModelScope.launch { a.templateRepo.delete(id) }
    }
}
