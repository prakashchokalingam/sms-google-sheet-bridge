package io.github.prakashchokalingam.smsgooglesheetbridge.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.prakashchokalingam.smsgooglesheetbridge.core.IstTime
import io.github.prakashchokalingam.smsgooglesheetbridge.data.BatchRecord
import io.github.prakashchokalingam.smsgooglesheetbridge.data.Prefs
import io.github.prakashchokalingam.smsgooglesheetbridge.redact.SensitivityMode
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    state: UiState,
    vm: AppViewModel,
    onOpenRules: () -> Unit,
    onOpenMessages: () -> Unit
) {
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { vm.refreshPermissions() }

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        vm.refresh()
        while (true) {
            delay(30_000)
            now = System.currentTimeMillis()
            vm.refresh()
        }
    }

    var detail by remember { mutableStateOf<BatchRecord?>(null) }

    val lastUploaded = state.batches.firstOrNull { it.status == "UPLOADED" }
    val nextSync = IstTime.nextWindowStart(now, state.windowMinutes)

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Sync", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.width(8.dp))
                    Pill(
                        text = if (state.configured) "Connected" else "Not connected",
                        tone = if (state.configured) Tone.GOOD else Tone.BAD
                    )
                }
                Text(
                    "${state.deviceName} · every ${windowLabel(state.windowMinutes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                KeyValue("Last", lastSyncLabel(lastUploaded))
                KeyValue("Next", nextSyncLabel(nextSync, now))
                KeyValue(
                    "Queued",
                    if (state.counts.pendingMessages == 0) "None" else messageLabel(state.counts.pendingMessages)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = vm::runNow, modifier = Modifier.weight(1f)) {
                        Text("Sync now")
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
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Activity", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.weight(1f))
                    Text(
                        todayLabel(state),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (state.batches.isEmpty()) {
                    Text(
                        "Nothing synced yet. Send a test SMS or tap Sync now.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    state.batches.take(8).forEachIndexed { index, batch ->
                        if (index > 0) HorizontalDivider()
                        BatchRow(batch = batch, onClick = { detail = batch })
                    }
                    if (state.batches.size > 8) {
                        Text(
                            "Showing latest 8 events",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
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
                    text = "Battery optimization can delay uploads. Allow SMS Google Sheet Bridge to run in the background.",
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

        item { Spacer(Modifier.height(8.dp)) }
    }

    val batch = detail
    if (batch != null) {
        BatchDetailDialog(
            batch = batch,
            device = state.deviceName,
            onViewMessages = {
                detail = null
                onOpenMessages()
            },
            onDismiss = { detail = null }
        )
    }
}

@Composable
private fun BatchRow(batch: BatchRecord, onClick: () -> Unit) {
    val failed = batch.status != "UPLOADED" && !batch.lastError.isNullOrBlank()
    val heartbeat = batch.count == 0
    val dotColor = when {
        batch.status == "UPLOADED" && heartbeat ->
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
        batch.status == "UPLOADED" -> MaterialTheme.colorScheme.primary
        failed -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.tertiary
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            IstTime.clock(batch.windowEnd),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(44.dp)
        )
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(dotColor, CircleShape)
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                batchSummary(batch),
                style = MaterialTheme.typography.bodyMedium,
                color = if (heartbeat) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
            Text(
                if (batch.status == "UPLOADED") {
                    "synced ${IstTime.clock(batch.sentAt ?: batch.windowEnd)}"
                } else {
                    "waiting to upload"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(8.dp))
        Pill(
            text = when {
                batch.status == "UPLOADED" -> "Synced"
                failed -> "Failed"
                else -> "Queued"
            },
            tone = when {
                batch.status == "UPLOADED" -> Tone.GOOD
                failed -> Tone.BAD
                else -> Tone.WARN
            }
        )
    }
}

@Composable
private fun BatchDetailDialog(
    batch: BatchRecord,
    device: String,
    onViewMessages: () -> Unit,
    onDismiss: () -> Unit
) {
    val failed = batch.status != "UPLOADED" && !batch.lastError.isNullOrBlank()
    val status = when {
        batch.status == "UPLOADED" -> "Synced"
        failed -> "Failed"
        else -> "Queued"
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sync event #${batch.seq}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Window",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text("${IstTime.human(batch.windowStart)} - ${IstTime.clock(batch.windowEnd)} IST")
                KeyValue("Messages", batch.count.toString())
                KeyValue("Masked", batch.maskedCount.toString())
                KeyValue("Skipped", batch.skipped.toString())
                KeyValue("Device", device)
                KeyValue("Status", status)
                KeyValue("Uploaded", batch.sentAt?.let { IstTime.human(it) } ?: "Not yet")
                if (batch.attempts > 1 && batch.status != "UPLOADED") {
                    KeyValue("Attempts", batch.attempts.toString())
                }
                batch.lastError?.takeIf { it.isNotBlank() }?.let {
                    InfoBanner(it, Tone.BAD)
                }
            }
        },
        confirmButton = {
            if (batch.count > 0) {
                TextButton(onClick = onViewMessages) {
                    Text("View messages")
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        },
        dismissButton = {
            if (batch.count > 0) {
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        }
    )
}

private fun lastSyncLabel(batch: BatchRecord?): String {
    if (batch == null) return "No sync yet"
    val time = IstTime.clock(batch.sentAt ?: batch.windowEnd)
    if (batch.count == 0) return "$time · heartbeat"
    return buildString {
        append(time)
        append(" · ")
        append(messageLabel(batch.count))
        if (batch.maskedCount > 0) append(" · ${batch.maskedCount} masked")
        if (batch.skipped > 0) append(" · ${batch.skipped} skipped")
    }
}

private fun todayLabel(state: UiState): String {
    val today = state.today
    if (today.events == 0) return "Today · no syncs"
    return buildString {
        append("Today · ")
        append(messageLabel(today.messages))
        if (today.masked > 0) append(" · ${today.masked} masked")
        if (today.skipped > 0) append(" · ${today.skipped} skipped")
    }
}

private fun batchSummary(batch: BatchRecord): String {
    if (batch.count == 0) return "0 msgs · heartbeat"
    return buildString {
        append(messageLabel(batch.count))
        if (batch.maskedCount > 0) append(" · ${batch.maskedCount} masked")
        if (batch.skipped > 0) append(" · ${batch.skipped} skipped")
    }
}

private fun messageLabel(count: Int): String {
    return if (count == 1) "1 msg" else "$count msgs"
}

private fun nextSyncLabel(nextSync: Long, now: Long): String {
    val remaining = (nextSync - now).coerceAtLeast(0)
    val minutes = (remaining + 59_999L) / 60_000L
    val time = IstTime.clock(nextSync)
    return when {
        minutes <= 0L -> "due now ($time)"
        minutes < 60L -> "in $minutes min ($time)"
        else -> "in ${minutes / 60L} h ${minutes % 60L} min ($time)"
    }
}

private fun windowLabel(minutes: Int): String {
    return when {
        minutes < 60 -> "${minutes} min"
        minutes == 60 -> "1 h"
        minutes == 1440 -> "24 h"
        minutes % 60 == 0 -> "${minutes / 60} h"
        else -> "${minutes} min"
    }
}
