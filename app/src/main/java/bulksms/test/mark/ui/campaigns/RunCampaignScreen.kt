package bulksms.test.mark.ui.campaigns

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.lifecycle.viewmodel.compose.viewModel
import bulksms.test.mark.ui.common.StatRow
import bulksms.test.mark.ui.common.StatusChip
import bulksms.test.mark.ui.common.WarningBanner

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun RunCampaignScreen(
    campaignId: Long,
    onBack: () -> Unit,
    vm: RunCampaignViewModel = viewModel()
) {
    val state by vm.stateFlow(campaignId).collectAsState()
    var showCancelConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.name.ifBlank { "Campaign" }, maxLines = 1) },
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
            ProgressRing(state)
            Text("via ${state.simLabel}", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)

            if (state.paused) {
                WarningBanner(
                    "Paused — ${(state.pauseReason ?: "").lowercase().replace('_', ' ')}" +
                        (state.pauseDetail?.let { " · $it" } ?: "")
                )
            }

            StatRow(items = listOf(
                "Pending"  to state.pending.toString(),
                "Sending"  to state.sending.toString(),
                "Sent"     to state.sent.toString(),
                "Delivered" to state.delivered.toString(),
            ))
            StatRow(items = listOf(
                "Failed"  to state.failed.toString(),
                "Invalid" to state.invalid.toString(),
                "Skipped" to state.skipped.toString(),
                "Total"   to state.total.toString(),
            ))

            ControlsRow(
                running = state.running, paused = state.paused,
                onPause = { vm.pause(campaignId) },
                onResume = { vm.resume(campaignId) },
                onCancel = { showCancelConfirm = true }
            )

            if (state.failed > 0) {
                OutlinedButton(
                    onClick = { vm.retryAllFailed(campaignId) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Retry all failed (${state.failed})") }
            }

            Spacer(Modifier.height(4.dp))
            Text("Recent activity", style = MaterialTheme.typography.titleSmall)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(state.recent, key = { it.id }) { r ->
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(r.normalizedPhone,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f))
                            r.lastError?.let {
                                Text(it, style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.padding(end = 8.dp))
                            }
                            StatusChip(status = r.status)
                        }
                    }
                }
            }
        }
    }

    if (showCancelConfirm) {
        AlertDialog(
            onDismissRequest = { showCancelConfirm = false },
            title = { Text("Cancel campaign?") },
            text = { Text("This will stop sending. Already-sent messages cannot be recalled.") },
            confirmButton = {
                Button(onClick = {
                    showCancelConfirm = false
                    vm.cancel(campaignId)
                    onBack()
                }) { Text("Cancel campaign") }
            },
            dismissButton = {
                TextButton(onClick = { showCancelConfirm = false }) { Text("Keep running") }
            }
        )
    }
}

@Composable
private fun ProgressRing(state: RunCampaignUiState) {
    val ratio = if (state.total > 0) state.sent.toFloat() / state.total else 0f
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(Modifier.size(110.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { ratio },
                modifier = Modifier.size(110.dp),
                strokeWidth = 8.dp
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${(ratio * 100).toInt()}%", style = MaterialTheme.typography.titleLarge)
                Text("sent", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Column {
            Text("${state.sent} of ${state.total}",
                style = MaterialTheme.typography.headlineSmall)
            if (state.total > 0)
                Text("${state.pending + state.sending} remaining",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ControlsRow(
    running: Boolean,
    paused: Boolean,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (running) {
            Button(onClick = onPause, modifier = Modifier.weight(1f)) { Text("Pause") }
        } else if (paused) {
            Button(onClick = onResume, modifier = Modifier.weight(1f)) { Text("Resume") }
        } else {
            Button(onClick = onResume, modifier = Modifier.weight(1f), enabled = false) { Text("Done") }
        }
        OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancel") }
    }
}
