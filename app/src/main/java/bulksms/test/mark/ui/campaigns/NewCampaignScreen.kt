package bulksms.test.mark.ui.campaigns

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import bulksms.test.mark.data.sms.SegmentCounter
import bulksms.test.mark.ui.common.InfoBanner
import bulksms.test.mark.ui.common.WarningBanner
import bulksms.test.mark.util.Time
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewCampaignScreen(
    onCancel: () -> Unit,
    onStarted: (Long) -> Unit,
    vm: NewCampaignViewModel = viewModel()
) {
    val state by vm.state.collectAsState()
    var showConfirm by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New campaign") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Outlined.Close, "Cancel")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (state.anotherCampaignActive) {
                WarningBanner(
                    "Another campaign is currently running or paused. " +
                        "Finish or cancel it before starting a new one."
                )
            }

            Text("Contact group", style = MaterialTheme.typography.titleMedium)
            if (state.groups.isEmpty()) {
                InfoBanner("Create a contact group from the Contacts tab first.")
            } else {
                GroupPicker(state, vm)
            }

            // Range picker — only useful once a group is chosen.
            if (state.selectedGroupId != null) {
                RangePicker(state, vm)
            }

            Text("Message", style = MaterialTheme.typography.titleMedium)
            TemplatePickerRow(state, vm)
            OutlinedTextField(
                value = state.message,
                onValueChange = vm::setMessage,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Type or pick a template") },
                minLines = 4,
                maxLines = 12
            )
            EncodingRow(vm.encoding())

            Text("Sender SIM", style = MaterialTheme.typography.titleMedium)
            if (state.sims.isEmpty()) {
                WarningBanner("No active SIMs detected. Insert a SIM and re-open this screen.")
            } else {
                SimPicker(state, vm)
            }

            Text("Send rate", style = MaterialTheme.typography.titleMedium)
            ThrottleControls(state, vm)

            Text("Campaign name", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = state.name,
                onValueChange = vm::setName,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Name") }
            )

            state.errorText?.let { WarningBanner(it) }

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { showConfirm = true },
                enabled = state.isReady && !state.starting,
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (state.starting) "Starting…" else "Review and start") }
        }
    }

    if (showConfirm) {
        val groupTotal = vm.selectedGroup()?.contactCount ?: 0
        ConfirmCampaignDialog(
            campaignName = state.name,
            groupName = vm.selectedGroup()?.name ?: "",
            recipients = state.recipientsInRange,
            rangeText = if (state.rangeIsCustom)
                "rows ${state.startRow}–${state.endRow ?: groupTotal} of $groupTotal"
            else null,
            simLabel = vm.selectedSim()?.label ?: "",
            throttleMs = state.throttleMs,
            jitter = state.jitter,
            segments = vm.segmentsPerMessage(),
            messagePreview = state.message,
            onCancel = { showConfirm = false },
            onConfirm = {
                showConfirm = false
                scope.launch {
                    val id = vm.startCampaign()
                    if (id != null) onStarted(id)
                }
            }
        )
    }
}

@Composable
private fun RangePicker(state: NewCampaignState, vm: NewCampaignViewModel) {
    val total = state.groups.firstOrNull { it.id == state.selectedGroupId }?.contactCount ?: 0
    if (total <= 0) return

    var startText by remember(state.startRow) { mutableStateOf(state.startRow.toString()) }
    var endText by remember(state.endRow, state.selectedGroupId) {
        mutableStateOf((state.endRow ?: total).toString())
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Recipient range", style = MaterialTheme.typography.titleSmall)
            Text(
                "Send only to a slice of the group. Useful when resuming a previous run " +
                    "or skipping numbers you've already messaged.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = startText,
                    onValueChange = { v ->
                        val digits = v.filter { it.isDigit() }.take(7)
                        startText = digits
                        digits.toIntOrNull()?.let { vm.setStartRow(it) }
                    },
                    label = { Text("From row") },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                    ),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = endText,
                    onValueChange = { v ->
                        val digits = v.filter { it.isDigit() }.take(7)
                        endText = digits
                        val parsed = digits.toIntOrNull()
                        vm.setEndRow(parsed)
                    },
                    label = { Text("To row") },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                    ),
                    modifier = Modifier.weight(1f)
                )
            }
            val recipientCount = state.recipientsInRange
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (recipientCount > 0) "Will send to $recipientCount of $total"
                    else "Invalid range",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (recipientCount > 0) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.tertiary
                )
                if (state.rangeIsCustom) {
                    androidx.compose.material3.TextButton(onClick = {
                        vm.resetRange()
                        startText = "1"
                        endText = total.toString()
                    }) { Text("Reset") }
                }
            }
        }
    }
}

@Composable
private fun GroupPicker(state: NewCampaignState, vm: NewCampaignViewModel) {
    var expanded by remember { mutableStateOf(false) }
    val selected = state.groups.firstOrNull { it.id == state.selectedGroupId }
    Box(Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                selected?.let { "${it.name} · ${it.contactCount} contacts" } ?: "Pick a group",
                modifier = Modifier.weight(1f)
            )
            Icon(Icons.Outlined.ArrowDropDown, null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            state.groups.forEach { g ->
                DropdownMenuItem(
                    text = { Text("${g.name} · ${g.contactCount} contacts") },
                    onClick = {
                        vm.setGroup(g.id)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun TemplatePickerRow(state: NewCampaignState, vm: NewCampaignViewModel) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        state.templates.take(6).forEach { t ->
            AssistChip(
                onClick = { vm.setTemplate(t) },
                label = { Text(t.name, maxLines = 1) }
            )
        }
        if (state.selectedTemplateId != null) {
            AssistChip(
                onClick = { vm.clearTemplate() },
                label = { Text("Clear") }
            )
        }
    }
}

@Composable
private fun EncodingRow(seg: SegmentCounter.Result) {
    Text(
        "Length: ${seg.length} · Segments: ${seg.segments} · Encoding: " +
            if (seg.encoding == SegmentCounter.Encoding.GSM_7) "GSM-7" else "UCS-2 (Amharic / Unicode)",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun SimPicker(state: NewCampaignState, vm: NewCampaignViewModel) {
    var expanded by remember { mutableStateOf(false) }
    val selected = state.sims.firstOrNull { it.subscriptionId == state.selectedSubId }
    Box(Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                selected?.label ?: "Pick a SIM",
                modifier = Modifier.weight(1f)
            )
            Icon(Icons.Outlined.ArrowDropDown, null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            state.sims.forEach { sim ->
                DropdownMenuItem(
                    text = { Text(sim.label) },
                    onClick = {
                        vm.setSim(sim.subscriptionId)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun ThrottleControls(state: NewCampaignState, vm: NewCampaignViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            "${"%.1f".format(state.throttleMs / 1000.0)} sec between messages",
            style = MaterialTheme.typography.titleMedium
        )
        Slider(
            value = state.throttleMs.toFloat(),
            onValueChange = { vm.setThrottle(it.toLong()) },
            valueRange = 500f..10_000f,
            steps = 94 // 0.5 → 10 by 0.1 = 95 stops, steps = stops - 1
        )
        Text(
            "Range: 0.5 – 10 seconds. Lower = faster, but more risk of carrier blocking.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (state.throttleMs <= 1_000L) {
            WarningBanner(
                "⚠️ At ${"%.1f".format(state.throttleMs / 1000.0)} sec/SMS the carrier may rate-limit " +
                    "or block your SIM. Recommended for sustained sending: 3 seconds or more."
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = state.jitter, onCheckedChange = vm::setJitter)
            Spacer(Modifier.padding(horizontal = 8.dp))
            Text("Add ±20% random jitter (looks more human)")
        }
    }
}
