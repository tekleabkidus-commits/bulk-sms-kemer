package bulksms.test.mark.ui.sent

import android.app.Application
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.compose.collectAsLazyPagingItems
import bulksms.test.mark.BulkSmsApp
import bulksms.test.mark.data.db.RecipientStatusCount
import bulksms.test.mark.data.db.SentMessageRow
import bulksms.test.mark.data.entity.RecipientStatus
import bulksms.test.mark.ui.common.StatRow
import bulksms.test.mark.ui.common.StatusChip
import bulksms.test.mark.util.Time
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest

data class SentFilter(
    val campaignId: Long? = null,
    val fromTs: Long? = null,
    val toTs: Long? = null,
    val query: String = "",
    val status: RecipientStatus? = null
)

class SentMessagesViewModel(app: Application) : AndroidViewModel(app) {
    private val a = app as BulkSmsApp

    private val _filter = MutableStateFlow(SentFilter())
    val filter = _filter.asStateFlow()

    val campaigns = a.campaignRepo.observeAllRows()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun pager() = _filter.flatMapLatest { f ->
        Pager(PagingConfig(pageSize = 50)) {
            a.campaignRepo.pageSentMessages(
                campaignId = f.campaignId,
                fromTs = f.fromTs,
                toTs = f.toTs,
                query = f.query,
                filterStatus = f.status
            )
        }.flow
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun counts(): Flow<List<RecipientStatusCount>> = _filter.flatMapLatest { f ->
        a.campaignRepo.observeSentStatusCounts(
            campaignId = f.campaignId,
            fromTs = f.fromTs,
            toTs = f.toTs,
            query = f.query
        )
    }

    fun setQuery(q: String) { _filter.value = _filter.value.copy(query = q) }
    fun setCampaign(id: Long?) { _filter.value = _filter.value.copy(campaignId = id) }
    fun setStatus(s: RecipientStatus?) { _filter.value = _filter.value.copy(status = s) }
    fun setLast24h() {
        val now = System.currentTimeMillis()
        _filter.value = _filter.value.copy(fromTs = now - 24L*3600_000, toTs = now)
    }
    fun setLast7d() {
        val now = System.currentTimeMillis()
        _filter.value = _filter.value.copy(fromTs = now - 7L*24*3600_000, toTs = now)
    }
    fun setToday() {
        _filter.value = _filter.value.copy(fromTs = Time.startOfTodayLocal(), toTs = System.currentTimeMillis())
    }
    fun clearDate() {
        _filter.value = _filter.value.copy(fromTs = null, toTs = null)
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SentMessagesScreen(vm: SentMessagesViewModel = viewModel()) {
    val filter by vm.filter.collectAsState()
    val counts by vm.counts().collectAsState(initial = emptyList())
    val pager = remember { vm.pager() }
    val items = pager.collectAsLazyPagingItems()
    val campaigns by vm.campaigns.collectAsState(initial = emptyList())
    var campaignMenu by remember { mutableStateOf(false) }

    fun count(status: RecipientStatus): Int =
        counts.firstOrNull { it.status == status }?.count ?: 0

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Sent messages") })
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = filter.query, onValueChange = vm::setQuery,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Search by phone number") },
                singleLine = true
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AssistChip(onClick = { vm.setToday() }, label = { Text("Today") })
                AssistChip(onClick = { vm.setLast24h() }, label = { Text("24h") })
                AssistChip(onClick = { vm.setLast7d() }, label = { Text("7d") })
                AssistChip(onClick = { vm.clearDate() }, label = { Text("All") })
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StatusFilterChip("Sent", filter.status == RecipientStatus.SENT) {
                    vm.setStatus(if (filter.status == RecipientStatus.SENT) null else RecipientStatus.SENT)
                }
                StatusFilterChip("Delivered", filter.status == RecipientStatus.DELIVERED) {
                    vm.setStatus(if (filter.status == RecipientStatus.DELIVERED) null else RecipientStatus.DELIVERED)
                }
                StatusFilterChip("Failed", filter.status == RecipientStatus.FAILED) {
                    vm.setStatus(if (filter.status == RecipientStatus.FAILED) null else RecipientStatus.FAILED)
                }
                StatusFilterChip("Unknown", filter.status == RecipientStatus.UNKNOWN) {
                    vm.setStatus(if (filter.status == RecipientStatus.UNKNOWN) null else RecipientStatus.UNKNOWN)
                }
            }
            Spacer(Modifier.height(8.dp))

            // Campaign filter (compact dropdown)
            val selectedCampaign = remember(filter.campaignId, campaigns) {
                campaigns.firstOrNull { it.id == filter.campaignId }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                AssistChip(
                    onClick = { campaignMenu = true },
                    leadingIcon = { Icon(Icons.Outlined.FilterList, null,
                        modifier = Modifier.height(18.dp)) },
                    label = { Text(selectedCampaign?.name ?: "All campaigns") }
                )
                DropdownMenu(
                    expanded = campaignMenu,
                    onDismissRequest = { campaignMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("All campaigns") },
                        onClick = { vm.setCampaign(null); campaignMenu = false }
                    )
                    campaigns.forEach { c ->
                        DropdownMenuItem(
                            text = { Text(c.name) },
                            onClick = { vm.setCampaign(c.id); campaignMenu = false }
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))

            StatRow(items = listOf(
                "Sent" to (count(RecipientStatus.SENT) + count(RecipientStatus.DELIVERED)).toString(),
                "Delivered" to count(RecipientStatus.DELIVERED).toString(),
                "Failed" to count(RecipientStatus.FAILED).toString(),
                "Unknown" to count(RecipientStatus.UNKNOWN).toString()
            ))
            Spacer(Modifier.height(8.dp))

            LazyColumn(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(count = items.itemCount) { idx ->
                    val r = items[idx] ?: return@items
                    SentRowCard(r)
                }
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun StatusFilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) }
    )
}

@Composable
private fun SentRowCard(r: SentMessageRow) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(r.normalizedPhone, style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f))
                StatusChip(r.status)
            }
            Spacer(Modifier.height(2.dp))
            Text(
                "${r.campaignName} · ${r.simLabel} · ${r.carrier}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            r.sentAt?.let {
                Text(Time.pretty(it), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            r.lastError?.let {
                Spacer(Modifier.height(2.dp))
                Text(it, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary)
            }
        }
    }
}
