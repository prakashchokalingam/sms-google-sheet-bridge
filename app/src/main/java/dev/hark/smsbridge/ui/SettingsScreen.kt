package dev.hark.smsbridge.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import dev.hark.smsbridge.BuildConfig
import dev.hark.smsbridge.redact.SensitivityMode

@Composable
fun SettingsScreen(state: UiState, vm: AppViewModel) {
    val context = LocalContext.current
    var url by remember { mutableStateOf(state.url) }
    var secret by remember { mutableStateOf("") }
    var showSecret by remember { mutableStateOf(false) }
    var device by remember { mutableStateOf(state.deviceName) }
    var backfillDays by remember { mutableStateOf(state.backfillDays.toString()) }
    var dialogTitle by remember { mutableStateOf<String?>(null) }
    var dialogBody by remember { mutableStateOf("") }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { vm.refreshPermissions() }

    val windowOptions = listOf(15, 30, 60, 180, 360, 720, 1440)

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SectionCard("Connection") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Pill(
                        text = if (state.configured) "Saved" else "Not configured",
                        tone = if (state.configured) Tone.GOOD else Tone.BAD
                    )
                    Spacer(Modifier.weight(1f))
                    if (state.busy) {
                        Text("Working...", style = MaterialTheme.typography.labelSmall)
                    }
                }
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Web App URL") },
                    placeholder = { Text("https://script.google.com/macros/s/.../exec") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = secret,
                    onValueChange = { secret = it },
                    label = { Text("Shared secret") },
                    placeholder = { Text(if (state.hasSecret) "Leave blank to keep current" else "At least 32 characters") },
                    singleLine = true,
                    visualTransformation = if (showSecret) VisualTransformation.None else PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Show secret", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.weight(1f))
                    Switch(checked = showSecret, onCheckedChange = { showSecret = it })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            vm.saveConnection(url, secret, false)
                            secret = ""
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Save")
                    }
                    OutlinedButton(
                        onClick = {
                            vm.saveConnection(url, secret, true)
                            secret = ""
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Save and test")
                    }
                }
            }
        }

        item {
            SectionCard("Device") {
                OutlinedTextField(
                    value = device,
                    onValueChange = { device = it },
                    label = { Text("Device name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(
                    onClick = { vm.saveDeviceName(device) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Save device name")
                }
            }
        }

        item {
            SectionCard("Delivery") {
                Text("Batch window", style = MaterialTheme.typography.titleSmall)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    windowOptions.forEach { minutes ->
                        FilterChip(
                            selected = state.windowMinutes == minutes,
                            onClick = { vm.setWindowMinutes(minutes) },
                            label = { Text(windowLabel(minutes)) }
                        )
                    }
                }
                Text(
                    "Windows align to midnight IST. The batch covers the previous full window.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text("Sensitivity", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.mode == SensitivityMode.MASK,
                        onClick = { vm.setMode(SensitivityMode.MASK) },
                        label = { Text("Mask and send") }
                    )
                    FilterChip(
                        selected = state.mode == SensitivityMode.GATE,
                        onClick = { vm.setMode(SensitivityMode.GATE) },
                        label = { Text("Gate and skip") }
                    )
                }
                Text(
                    if (state.mode == SensitivityMode.MASK) {
                        "Codes are replaced with [REDACTED]. The rest of the message is sent."
                    } else {
                        "Any message with a code is skipped entirely and never stored."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Code sweep", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Masks digit groups near OTP keywords even without a matching rule.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = state.sweepEnabled,
                        onCheckedChange = { vm.setSweepEnabled(it) }
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Contact names", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            if (state.hasContacts) "Names are resolved locally." else "Needs READ_CONTACTS.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = state.resolveContacts,
                        onCheckedChange = { enabled ->
                            if (enabled && !state.hasContacts) {
                                permissionLauncher.launch(arrayOf(Manifest.permission.READ_CONTACTS))
                            } else {
                                vm.setResolveContacts(enabled)
                            }
                        }
                    )
                }
            }
        }

        item {
            SectionCard("Backfill") {
                Text(
                    "Reads existing SMS from the default inbox and queues missing messages.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = backfillDays,
                        onValueChange = { backfillDays = it.filter(Char::isDigit).take(2) },
                        label = { Text("Days") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(110.dp)
                    )
                    OutlinedButton(
                        onClick = {
                            val days = backfillDays.toIntOrNull()?.coerceIn(1, 90) ?: state.backfillDays
                            vm.setBackfillDays(days)
                            if (state.hasReadSms) {
                                vm.backfill()
                            } else {
                                permissionLauncher.launch(arrayOf(Manifest.permission.READ_SMS))
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (state.hasReadSms) "Run backfill" else "Grant and backfill")
                    }
                }
            }
        }

        item {
            SectionCard("Permissions") {
                KeyValue("Receive SMS", if (state.hasReceiveSms) "Granted" else "Not granted")
                KeyValue("Read SMS", if (state.hasReadSms) "Granted" else "Not granted")
                KeyValue("Contacts", if (state.hasContacts) "Granted" else "Not granted")
                OutlinedButton(
                    onClick = {
                        permissionLauncher.launch(
                            arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS)
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Request SMS permissions")
                }
                OutlinedButton(
                    onClick = { requestBatteryExemption(context) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Allow background running")
                }
            }
        }

        item {
            SectionCard("Setup") {
                OutlinedButton(
                    onClick = {
                        dialogTitle = "Setup guide"
                        dialogBody = readAsset(context, "setup.md").ifBlank {
                            "Copy apps-script/Code.gs into a new Apps Script project, deploy it as a Web App, " +
                                "then paste the /exec URL and the shared secret here."
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Setup guide")
                }
                OutlinedButton(
                    onClick = {
                        dialogTitle = "Notices"
                        dialogBody = readAsset(context, "notices.txt").ifBlank {
                            "Hark SMS Bridge masks and forwards messages to a Google Sheet that you control. " +
                                "Google Sheets, Google Drive, and Apps Script are third-party services. " +
                                "Your carrier may charge for data."
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Notices")
                }
                OutlinedButton(
                    onClick = vm::reopenOnboarding,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Replay onboarding")
                }
                Text(
                    "Hark SMS Bridge ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item { Spacer(Modifier.width(1.dp)) }
    }

    val title = dialogTitle
    if (title != null) {
        AlertDialog(
            onDismissRequest = {
                dialogTitle = null
                dialogBody = ""
            },
            title = { Text(title) },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 460.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        dialogBody,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    dialogTitle = null
                    dialogBody = ""
                }) {
                    Text("Close")
                }
            }
        )
    }
}

private fun windowLabel(minutes: Int): String {
    return when {
        minutes < 60 -> "${minutes}m"
        minutes == 60 -> "1h"
        minutes == 1440 -> "24h"
        minutes % 60 == 0 -> "${minutes / 60}h"
        else -> "${minutes}m"
    }
}
