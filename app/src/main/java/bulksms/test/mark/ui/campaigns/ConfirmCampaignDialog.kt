package bulksms.test.mark.ui.campaigns

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Checkbox
import bulksms.test.mark.util.Time

@Composable
fun ConfirmCampaignDialog(
    campaignName: String,
    groupName: String,
    recipients: Int,
    simLabel: String,
    throttleMs: Long,
    jitter: Boolean,
    segments: Int,
    messagePreview: String,
    onCancel: () -> Unit,
    onConfirm: () -> Unit
) {
    val totalSegments = recipients * segments
    val estDurationMs = recipients.toLong() * throttleMs
    var consent by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onCancel,
        confirmButton = {
            Button(onClick = onConfirm, enabled = consent) {
                Text("Confirm and start")
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text("Cancel") }
        },
        title = { Text("Review and confirm") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Pair("Campaign", campaignName).let { Line(it.first, it.second) }
                Line("Group", "$groupName · $recipients recipients")
                Line("SIM", simLabel)
                Line("Send rate",
                    "${"%.1f".format(throttleMs / 1000.0)} sec / SMS" + if (jitter) " (±20% jitter)" else "")
                Line("Per message", "$segments segment(s)")
                Line("Total to send", "$totalSegments SMS units")
                Line("Estimated time", Time.durationShort(estDurationMs))
                Spacer(Modifier.height(4.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Message preview", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        Text(messagePreview, style = MaterialTheme.typography.bodyMedium,
                            maxLines = 6)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = consent, onCheckedChange = { consent = it })
                    Text(
                        "Recipients consented to receive these messages and I am responsible " +
                            "for compliance with local regulations.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    )
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
