package bulksms.test.mark.ui.campaigns

import android.app.Application
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.compose.collectAsLazyPagingItems
import bulksms.test.mark.BulkSmsApp
import bulksms.test.mark.data.entity.CampaignStatus
import bulksms.test.mark.data.excel.ResultsExporter
import bulksms.test.mark.ui.common.StatRow
import bulksms.test.mark.ui.common.StatusChip
import bulksms.test.mark.util.Time
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CampaignDetailViewModel(app: Application) : AndroidViewModel(app) {
    private val a = app as BulkSmsApp
    fun row(id: Long) = a.campaignRepo.observeRow(id)
    fun pager(id: Long) = Pager(PagingConfig(pageSize = 50)) {
        a.campaignRepo.pageInCampaign(id)
    }.flow

    suspend fun listAll(id: Long) = a.campaignRepo.listAllInCampaign(id)
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun CampaignDetailScreen(
    campaignId: Long,
    onBack: () -> Unit,
    onResume: () -> Unit,
    vm: CampaignDetailViewModel = viewModel()
) {
    val ctx = LocalContext.current
    val row by vm.row(campaignId).collectAsState(initial = null)
    val items = vm.pager(campaignId).collectAsLazyPagingItems()
    val scope = rememberCoroutineScope()

    var exportType by remember { mutableStateOf("xlsx") }
    val createDoc = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(
            if (exportType == "xlsx")
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            else "text/csv"
        )
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                val list = vm.listAll(campaignId)
                if (exportType == "xlsx") ResultsExporter.toXlsx(ctx.contentResolver, uri, list)
                else ResultsExporter.toCsv(ctx.contentResolver, uri, list)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(row?.name ?: "Campaign") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, "Back") }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            row?.let { r ->
                StatRow(items = listOf(
                    "Total" to r.totalCount.toString(),
                    "Sent"  to r.sentCount.toString(),
                    "Delivered" to r.deliveredCount.toString(),
                    "Failed" to r.failedCount.toString()
                ))

                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Line("Group", r.groupName)
                        Line("SIM", r.simDisplayLabel)
                        Line("Status", r.status.name)
                        r.startedAt?.let { Line("Started", Time.pretty(it)) }
                        r.completedAt?.let { Line("Finished", Time.pretty(it)) }
                        Spacer(Modifier.height(8.dp))
                        Text("Message", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(r.message, style = MaterialTheme.typography.bodyMedium)
                    }
                }

                if (r.status == CampaignStatus.PAUSED) {
                    Button(onClick = onResume, modifier = Modifier.fillMaxWidth()) { Text("Resume") }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            exportType = "xlsx"
                            createDoc.launch("${sanitize(r.name)}.xlsx")
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Export xlsx") }
                    OutlinedButton(
                        onClick = {
                            exportType = "csv"
                            createDoc.launch("${sanitize(r.name)}.csv")
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Export csv") }
                }

                Text("Recipients", style = MaterialTheme.typography.titleSmall)
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(count = items.itemCount) { idx ->
                        val rec = items[idx] ?: return@items
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(rec.normalizedPhone, style = MaterialTheme.typography.bodyMedium)
                                    rec.lastError?.let {
                                        Text(it, style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.tertiary)
                                    }
                                }
                                rec.sentAt?.let {
                                    Text(Time.pretty(it), style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(end = 8.dp))
                                }
                                StatusChip(status = rec.status)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Line(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 12.dp))
    }
}

private fun sanitize(s: String): String =
    s.replace(Regex("[^A-Za-z0-9_-]+"), "_").trim('_').take(40).ifBlank { "campaign" }
