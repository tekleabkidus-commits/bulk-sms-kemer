package bulksms.test.mark.ui.reliability

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import bulksms.test.mark.ui.common.Bullet
import bulksms.test.mark.util.PermissionUtil
import bulksms.test.mark.data.preferences.AppPreferences

@Composable
fun ReliabilityScreen(onDone: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = remember { AppPreferences(ctx) }
    var checked by remember { mutableStateOf(prefs.reliabilityComplete) }

    val tips = remember { OemHints.tipsFor(Build.MANUFACTURER) }

    Column(
        Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Reliability setup", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Long campaigns can be killed by aggressive battery management. Complete these " +
                "steps in your phone's Settings, then tick the box. Without these, sending may " +
                "stop after a few hours.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(12.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("For ${tips.label}:", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                tips.steps.forEach { Bullet(it) }
            }
        }

        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { PermissionUtil.openAppDetails(ctx) },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Open app settings") }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = checked,
                onCheckedChange = {
                    checked = it
                    prefs.reliabilityComplete = it
                }
            )
            Text("I've completed all steps for my phone.",
                style = MaterialTheme.typography.bodyMedium)
        }
        Button(
            onClick = onDone,
            enabled = checked,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Continue") }
    }
}
