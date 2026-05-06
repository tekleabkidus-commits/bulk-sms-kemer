package bulksms.test.mark.ui.contacts

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.compose.collectAsLazyPagingItems
import bulksms.test.mark.BulkSmsApp
import bulksms.test.mark.data.db.ContactGroupSummary
import bulksms.test.mark.ui.common.WarningBanner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ContactGroupDetailViewModel(app: Application) : AndroidViewModel(app) {
    private val a = app as BulkSmsApp

    private val _summary = MutableStateFlow<ContactGroupSummary?>(null)
    val summary = _summary.asStateFlow()

    fun loadSummary(id: Long) {
        viewModelScope.launch {
            _summary.value = a.contactRepo.getGroupSummary(id)
        }
    }

    fun pager(id: Long, query: String) = Pager(PagingConfig(pageSize = 50)) {
        if (query.isBlank()) a.contactRepo.pageInGroup(id)
        else a.contactRepo.searchInGroup(id, query)
    }.flow

    suspend fun addNumber(id: Long, raw: String): Boolean {
        val ok = a.contactRepo.addSingleNumber(id, raw)
        if (ok) loadSummary(id)
        return ok
    }

    fun removeContact(id: Long, contactId: Long) {
        viewModelScope.launch {
            a.contactRepo.removeContact(contactId)
            loadSummary(id)
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ContactGroupDetailScreen(
    groupId: Long,
    onBack: () -> Unit,
    onImport: () -> Unit,
    vm: ContactGroupDetailViewModel = viewModel()
) {
    val summary by vm.summary.collectAsState()
    var query by remember { mutableStateOf("") }
    val items = vm.pager(groupId, query).collectAsLazyPagingItems()
    var addDialog by remember { mutableStateOf(false) }
    var addError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(groupId) { vm.loadSummary(groupId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(summary?.name ?: "Group") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, "Back") }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onImport,
                icon = { Icon(Icons.Outlined.UploadFile, null) },
                text = { Text("Import") }
            )
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("${summary?.contactCount ?: 0} contacts",
                style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Search by number") },
                singleLine = true
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { addDialog = true }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Add one number")
                }
            }
            addError?.let { WarningBanner(it) }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(count = items.itemCount) { idx ->
                    val c = items[idx] ?: return@items
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(c.normalizedPhone, style = MaterialTheme.typography.bodyMedium)
                                Text(c.carrier, style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { vm.removeContact(groupId, c.id) }) {
                                Icon(Icons.Outlined.Delete, "Remove",
                                    tint = MaterialTheme.colorScheme.tertiary)
                            }
                        }
                    }
                }
            }
        }
    }

    if (addDialog) {
        var raw by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { addDialog = false; addError = null },
            title = { Text("Add a phone number") },
            text = {
                Column {
                    OutlinedTextField(
                        value = raw, onValueChange = { raw = it; addError = null },
                        singleLine = true,
                        label = { Text("e.g. +251911234567 or 0911234567") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Only Ethio Telecom (+2519…) and Safaricom Ethiopia (+2517…) numbers are accepted.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val attempt = raw
                    scope.launch {
                        val ok = vm.addNumber(groupId, attempt)
                        if (ok) addDialog = false
                        else addError = "Could not add — invalid format or already in this group."
                    }
                }) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { addDialog = false; addError = null }) { Text("Cancel") }
            }
        )
    }
}
