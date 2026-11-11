package bulksms.test.mark.ui.permissions

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import bulksms.test.mark.util.PermissionUtil

@Composable
fun PermissionScreen(onContinue: () -> Unit) {
    val ctx = LocalContext.current

    var hasSendSms by remember { mutableStateOf(PermissionUtil.hasSendSms(ctx)) }
    var hasReadPhone by remember { mutableStateOf(PermissionUtil.hasReadPhoneState(ctx)) }
    var hasNotif by remember { mutableStateOf(PermissionUtil.hasNotifications(ctx)) }
    var hasBattery by remember { mutableStateOf(PermissionUtil.isIgnoringBatteryOptimization(ctx)) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        hasSendSms = PermissionUtil.hasSendSms(ctx)
        hasReadPhone = PermissionUtil.hasReadPhoneState(ctx)
        hasNotif = PermissionUtil.hasNotifications(ctx)
    }

    val allRuntimeOk = hasSendSms && hasReadPhone && hasNotif
    val allOk = allRuntimeOk && hasBattery

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Permissions required",
            style = MaterialTheme.typography.headlineSmall)
        Text(
            "Bulk SMS Kemer needs the following permissions to send messages reliably " +
                "and survive long campaigns.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        PermissionItem(
            label = "Send SMS",
            desc = "Required to dispatch messages through your SIM.",
            granted = hasSendSms,
            onGrant = {
                launcher.launch(arrayOf(Manifest.permission.SEND_SMS))
            }
        )
        PermissionItem(
            label = "Phone state",
            desc = "Required to detect SIMs and pause during calls.",
            granted = hasReadPhone,
            onGrant = {
                launcher.launch(arrayOf(Manifest.permission.READ_PHONE_STATE))
            }
        )
        PermissionItem(
            label = "Notifications",
            desc = "Required so the foreground service can keep running.",
            granted = hasNotif,
            onGrant = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    launcher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                } else {
                    PermissionUtil.openAppDetails(ctx)
                }
            }
        )
        PermissionItem(
            label = "Ignore battery optimization",
            desc = "Required so the system does not kill the sender during long campaigns.",
            granted = hasBattery,
            onGrant = {
                PermissionUtil.openBatteryOptimizationSettings(ctx)
                hasBattery = PermissionUtil.isIgnoringBatteryOptimization(ctx)
            }
        )

        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                hasBattery = PermissionUtil.isIgnoringBatteryOptimization(ctx)
                if (allOk) onContinue()
            },
            enabled = allOk,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Continue") }
        if (!allOk) {
            Text(
                "Tap each Grant button above. Battery optimization is in system settings.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PermissionItem(label: String, desc: String, granted: Boolean, onGrant: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.titleMedium)
                Text(desc, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (granted) {
                Box(
                    Modifier
                        .padding(start = 12.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(50)
                    ) {
                        Text("Granted",
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelMedium)
                    }
                }
            } else {
                OutlinedButton(onClick = onGrant) { Text("Grant") }
            }
        }
    }
}
