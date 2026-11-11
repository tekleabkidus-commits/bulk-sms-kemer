package bulksms.test.mark.ui.contacts

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import bulksms.test.mark.BulkSmsApp
import bulksms.test.mark.data.excel.ParseResult
import bulksms.test.mark.data.excel.PhoneFileParser
import bulksms.test.mark.ui.common.InfoBanner
import bulksms.test.mark.ui.common.StatRow
import bulksms.test.mark.ui.common.WarningBanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ImportContactsScreen(groupId: Long, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as BulkSmsApp
    val scope = rememberCoroutineScope()

    var pickedUri by remember { mutableStateOf<Uri?>(null) }
    var parsing by remember { mutableStateOf(false) }
    var parseError by remember { mutableStateOf<String?>(null) }
    var parsed by remember { mutableStateOf<ParseResult?>(null) }
    var insertedCount by remember { mutableStateOf<Int?>(null) }

    val pickFile = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        pickedUri = uri
        parsed = null
        insertedCount = null
        parseError = null
        if (uri != null) {
            scope.launch {
                parsing = true
                try {
                    val r = withContext(Dispatchers.IO) {
                        PhoneFileParser(ctx.contentResolver).parse(uri)
                    }
                    parsed = r
                } catch (t: Throwable) {
                    parseError = t.message ?: "Could not read this file."
                } finally {
                    parsing = false
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Import contacts") },
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
            InfoBanner(
                "Pick a .xlsx or .csv with phone numbers in the first column. " +
                    "We accept Ethio Telecom (+2519…) and Safaricom (+2517…) numbers. " +
                    "Headers are auto-detected. Duplicates are skipped silently."
            )

            Button(
                onClick = {
                    pickFile.launch(arrayOf(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        "application/vnd.ms-excel",
                        "text/csv",
                        "text/comma-separated-values",
                        "text/plain",
                        "application/octet-stream"
                    ))
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Outlined.UploadFile, null)
                Spacer(Modifier.width(8.dp))
                Text("Pick file")
            }

            if (parsing) {
                Text("Reading file…", style = MaterialTheme.typography.bodyMedium)
            }
            parseError?.let { WarningBanner(it) }

            parsed?.let { p ->
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Preview", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        StatRow(items = listOf(
                            "Rows" to p.totalRows.toString(),
                            "Valid" to p.validRaw.size.toString(),
                            "Invalid" to p.invalidCount.toString(),
                            "Duplicate" to p.duplicateCount.toString()
                        ))
                        Spacer(Modifier.height(8.dp))
                        if (p.invalidCount > 0) {
                            Text(
                                "${p.invalidCount} number(s) were not in +2519/+2517 format and were skipped.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                        if (p.duplicateCount > 0) {
                            Text(
                                "${p.duplicateCount} duplicate(s) within the file were collapsed.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            pickedUri = null; parsed = null; insertedCount = null
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Pick different file") }
                    Button(
                        enabled = p.validRaw.isNotEmpty(),
                        onClick = {
                            scope.launch {
                                val n = withContext(Dispatchers.IO) {
                                    app.contactRepo.addContactsToGroup(groupId, p)
                                }
                                insertedCount = n
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Add to group") }
                }
            }

            insertedCount?.let { n ->
                Spacer(Modifier.height(4.dp))
                InfoBanner("Added $n new contact(s) to the group.")
                Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Done") }
            }
        }
    }
}
