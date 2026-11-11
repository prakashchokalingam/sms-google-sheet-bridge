package dev.hark.smsbridge.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.hark.smsbridge.core.IstTime
import dev.hark.smsbridge.data.Prefs
import dev.hark.smsbridge.redact.SensitivityMode

@Composable
fun HomeScreen(state: UiState, vm: AppViewModel, onOpenRules: () -> Unit) {
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { vm.refreshPermissions() }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SectionCard("Connection") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Pill(
                        text = if (state.configured) "Connected" else "Not connected",
                        tone = if (state.configured) Tone.GOOD else Tone.BAD
                    )
                    Spacer(Modifier.weight(1f))
                    Text(state.deviceName, style = androidx.compose.material3.MaterialTheme.typography.labelMedium)
                }
                KeyValue(
                    "Last upload",
                    if (state.lastUploadAt == 0L) "Never" else IstTime.human(state.lastUploadAt)
                )
                KeyValue(
                    "Next batch",
                    IstTime.human(IstTime.nextWindowStart(System.currentTimeMillis(), state.windowMinutes))
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = vm::runNow, modifier = Modifier.weight(1f)) {
                        Text("Send now")
                    }
                    OutlinedButton(onClick = vm::testSavedConnection, modifier = Modifier.weight(1f)) {
                        Text("Test")
                    }
                }
                if (state.configured && Prefs.isValidUrl(state.url)) {
                    TextButton(onClick = { openUrl(context, state.url) }) {
                        Text("Open Web App URL")
                    }
                }
            }
        }

        item {
            SectionCard("Queue") {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile("Pending", state.counts.pendingMessages.toString(), Modifier.weight(1f))
                    StatTile("Batches", state.counts.pendingBatches.toString(), Modifier.weight(1f))
                    StatTile("Masked", state.counts.maskedMessages.toString(), Modifier.weight(1f))
                    StatTile("Skipped", state.counts.skippedTotal.toString(), Modifier.weight(1f))
                }
                Text(
                    "Messages are deleted from this phone after a successful upload.",
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            SectionCard("Privacy") {
                KeyValue(
                    "Mode",
                    if (state.mode == SensitivityMode.MASK) "Mask and send" else "Gate and skip"
                )
                KeyValue("Code sweep", if (state.sweepEnabled) "On" else "Off")
                val skipCount = state.rules.count { it.action.name == "SKIP" }
                val maskCount = state.rules.count { it.action.name == "MASK" }
                KeyValue("Rules", "$skipCount skip, $maskCount mask")
                OutlinedButton(onClick = onOpenRules, modifier = Modifier.fillMaxWidth()) {
                    Text("Edit rules")
                }
            }
        }

        if (state.ruleErrors.isNotEmpty()) {
            item {
                InfoBanner(
                    text = "${state.ruleErrors.size} rules have errors. Sending is paused until you fix them.",
                    tone = Tone.BAD,
                    icon = Icons.Default.Warning
                )
            }
            item {
                OutlinedButton(onClick = onOpenRules, modifier = Modifier.fillMaxWidth()) {
                    Text("Open rules")
                }
            }
        }

        if (state.batteryOptimized) {
            item {
                InfoBanner(
                    text = "Battery optimization can delay uploads. Allow Hark to run in the background.",
                    tone = Tone.WARN,
                    icon = Icons.Default.Warning
                )
            }
            item {
                OutlinedButton(
                    onClick = { requestBatteryExemption(context) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Allow background")
                }
            }
        }

        if (state.resolveContacts && !state.hasContacts) {
            item {
                InfoBanner(
                    text = "Contact names are on, but READ_CONTACTS is not granted.",
                    tone = Tone.WARN,
                    icon = Icons.Default.Warning
                )
            }
            item {
                OutlinedButton(
                    onClick = { permissionLauncher.launch(arrayOf(Manifest.permission.READ_CONTACTS)) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Grant contact permission")
                }
            }
        }

        if (!state.hasReadSms) {
            item {
                InfoBanner(
                    text = "READ_SMS is off. Live SMS still works. Backfill needs this permission.",
                    tone = Tone.NEUTRAL,
                    icon = Icons.Default.Info
                )
            }
            item {
                OutlinedButton(
                    onClick = { permissionLauncher.launch(arrayOf(Manifest.permission.READ_SMS)) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Grant read SMS for backfill")
                }
            }
        }

        val lastError = state.lastError
        if (!lastError.isNullOrBlank()) {
            item {
                SectionCard("Last error") {
                    Text(lastError)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = vm::clearError) {
                            Text("Clear")
                        }
                        OutlinedButton(onClick = vm::testSavedConnection) {
                            Text("Retest")
                        }
                    }
                }
            }
        }

        item {
            SectionCard("Recent batches") {
                if (state.batches.isEmpty()) {
                    Text(
                        "No batches yet.",
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    state.batches.take(8).forEachIndexed { index, batch ->
                        if (index > 0) HorizontalDivider()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "#${batch.seq}  ${IstTime.human(batch.windowStart)}",
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    "${batch.count} messages, ${batch.maskedCount} masked, ${batch.skipped} skipped",
                                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Pill(
                                text = batch.status,
                                tone = if (batch.status == "UPLOADED") Tone.GOOD else Tone.WARN
                            )
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(8.dp)) }
    }
}
