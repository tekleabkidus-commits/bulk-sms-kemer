package bulksms.test.mark.ui.templates

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import bulksms.test.mark.BulkSmsApp
import bulksms.test.mark.data.sms.SegmentCounter
import kotlinx.coroutines.launch

class TemplateEditorViewModel(app: Application) : AndroidViewModel(app) {
    private val a = app as BulkSmsApp
    suspend fun load(id: Long) = a.templateRepo.getById(id)
    suspend fun save(id: Long?, name: String, content: String): Long {
        return if (id == null) a.templateRepo.create(name, content)
        else { a.templateRepo.update(id, name, content); id }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun TemplateEditorScreen(
    templateId: Long?,
    onDone: () -> Unit,
    vm: TemplateEditorViewModel = viewModel()
) {
    var name by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    LaunchedEffect(templateId) {
        if (templateId != null) {
            val t = vm.load(templateId)
            if (t != null) { name = t.name; content = t.content }
        }
    }

    val seg = remember(content) { SegmentCounter.count(content) }
    val canSave = name.isNotBlank() && content.isNotBlank()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (templateId == null) "New template" else "Edit template") },
                navigationIcon = {
                    IconButton(onClick = onDone) { Icon(Icons.Outlined.ArrowBack, "Back") }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Name") },
                singleLine = true
            )
            OutlinedTextField(
                value = content, onValueChange = { content = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Message content") },
                minLines = 6,
                maxLines = 16
            )
            Text(
                "${seg.length} chars · ${seg.segments} segment(s) · " +
                    if (seg.encoding == SegmentCounter.Encoding.GSM_7) "GSM-7"
                    else "UCS-2 (Amharic / Unicode)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Button(
                enabled = canSave,
                onClick = {
                    scope.launch {
                        vm.save(templateId, name, content)
                        onDone()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (templateId == null) "Create" else "Save") }
        }
    }
}
