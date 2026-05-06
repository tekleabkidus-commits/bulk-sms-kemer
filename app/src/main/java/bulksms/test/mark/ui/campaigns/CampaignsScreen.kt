package bulksms.test.mark.ui.campaigns

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import bulksms.test.mark.data.db.CampaignRow
import bulksms.test.mark.data.entity.CampaignStatus
import bulksms.test.mark.ui.common.EmptyState
import bulksms.test.mark.util.Time

@Composable
fun CampaignsScreen(
    onNewCampaign: () -> Unit,
    onOpenCampaign: (Long) -> Unit,
    onResumeCampaign: (Long) -> Unit,
    vm: CampaignsViewModel = viewModel()
) {
    val rows by vm.rows.collectAsState(initial = emptyList())

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewCampaign,
                icon = { Icon(Icons.Outlined.Add, null) },
                text = { Text("New campaign") }
            )
        }
    ) { padding ->
        Box(
            Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            if (rows.isEmpty()) {
                EmptyState(
                    title = "No campaigns yet",
                    body = "Create a contact group, then start a new campaign.",
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(rows, key = { it.id }) { row ->
                        CampaignCard(
                            row,
                            onClick = {
                                if (row.status == CampaignStatus.RUNNING || row.status == CampaignStatus.PAUSED)
                                    onResumeCampaign(row.id)
                                else
                                    onOpenCampaign(row.id)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CampaignCard(row: CampaignRow, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(row.name, style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f))
                StatusPill(row.status)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "${row.groupName} · ${row.simDisplayLabel}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "${row.sentCount} of ${row.totalCount} sent · ${row.deliveredCount} delivered · ${row.failedCount} failed",
                style = MaterialTheme.typography.bodySmall
            )
            row.pauseReason?.let { reason ->
                Spacer(Modifier.height(4.dp))
                Text(
                    "Paused — ${reason.lowercase().replace('_', ' ')}" +
                        (row.pauseDetail?.let { " · $it" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
            row.completedAt?.let {
                Spacer(Modifier.height(4.dp))
                Text("Finished ${Time.pretty(it)}", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } ?: row.startedAt?.let {
                Spacer(Modifier.height(4.dp))
                Text("Started ${Time.pretty(it)}", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun StatusPill(status: CampaignStatus) {
    val (label, bg, fg) = when (status) {
        CampaignStatus.DRAFT     -> Triple("Draft",     MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
        CampaignStatus.RUNNING   -> Triple("Running",   MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
        CampaignStatus.PAUSED    -> Triple("Paused",    MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
        CampaignStatus.COMPLETED -> Triple("Completed", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
        CampaignStatus.CANCELLED -> Triple("Cancelled", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Surface(color = bg, shape = RoundedCornerShape(50)) {
        Text(label, color = fg,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp))
    }
}
