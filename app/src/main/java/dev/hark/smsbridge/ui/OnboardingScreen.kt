package dev.hark.smsbridge.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import dev.hark.smsbridge.data.Prefs

@Composable
fun OnboardingScreen(state: UiState, vm: AppViewModel) {
    val context = LocalContext.current
    var step by rememberSaveable { mutableIntStateOf(0) }
    var url by remember { mutableStateOf(state.url) }
    var secret by remember { mutableStateOf("") }
    var showSecret by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { vm.refreshPermissions() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(Modifier.height(24.dp))
        Text(
            "Hark SMS Bridge",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            "Step ${step + 1} of 3",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        when (step) {
            0 -> {
                Text("Codes are masked before they leave this phone.", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Hark reads incoming SMS, redacts OTPs and sensitive codes, and uploads the rest " +
                        "to a Google Sheet you control.",
                    style = MaterialTheme.typography.bodyMedium
                )
                SectionCard {
                    Bullet("Skip rules block the entire message. It is never stored or uploaded.")
                    Bullet("Mask rules replace only the captured span with [REDACTED].")
                    Bullet("A fail-closed sweep masks digit groups near OTP keywords.")
                    Bullet("Messages upload in aligned time windows, not one by one.")
                }
                InfoBanner(
                    "Messages are deleted from this phone after a successful upload. The Google Sheet is not.",
                    tone = Tone.NEUTRAL
                )
                InfoBanner(
                    "Do not share your Web App URL or secret. Anyone with both can read and write your sheet.",
                    tone = Tone.WARN,
                    icon = Icons.Default.Warning
                )
            }

            1 -> {
                Text("Permissions", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Hark needs SMS access to receive messages. Nothing is sent until you finish setup.",
                    style = MaterialTheme.typography.bodyMedium
                )
                SectionCard("SMS access") {
                    KeyValue("Receive SMS", if (state.hasReceiveSms) "Granted" else "Not granted")
                    KeyValue("Read SMS (backfill)", if (state.hasReadSms) "Granted" else "Optional")
                    Button(
                        onClick = {
                            permissionLauncher.launch(
                                arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS)
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (state.hasReceiveSms) "Review permissions" else "Grant SMS access")
                    }
                }
                SectionCard("Background") {
                    Text(
                        "Android may delay uploads unless Hark is exempt from battery optimization.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = { requestBatteryExemption(context) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (state.batteryOptimized) "Allow background running" else "Background allowed")
                    }
                }
                if (!state.hasReceiveSms) {
                    InfoBanner(
                        "Without RECEIVE_SMS, Hark cannot see new messages.",
                        tone = Tone.WARN,
                        icon = Icons.Default.Warning
                    )
                }
            }

            else -> {
                Text("Connect your sheet", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Deploy the Apps Script Web App, then paste its /exec URL and the same shared secret here.",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = {
                        url = it
                        error = null
                    },
                    label = { Text("Web App URL") },
                    placeholder = { Text("https://script.google.com/macros/s/.../exec") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = secret,
                    onValueChange = {
                        secret = it
                        error = null
                    },
                    label = { Text("Shared secret") },
                    supportingText = { Text("At least ${Prefs.MIN_SECRET_LENGTH} characters") },
                    singleLine = true,
                    visualTransformation = if (showSecret) VisualTransformation.None else PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Show secret", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.weight(1f))
                    Switch(checked = showSecret, onCheckedChange = { showSecret = it })
                }
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
                OutlinedButton(
                    onClick = {
                        val body = readAsset(context, "setup.md")
                        if (body.isNotBlank()) {
                            copyText(context, "Hark setup guide", body)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Copy setup guide")
                }
                InfoBanner(
                    "You can change or clear this later in Settings.",
                    tone = Tone.NEUTRAL
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (step > 0) {
                OutlinedButton(
                    onClick = {
                        error = null
                        step -= 1
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Back")
                }
            }
            Button(
                onClick = {
                    when (step) {
                        0 -> step = 1
                        1 -> step = 2
                        else -> {
                            val trimmedUrl = url.trim()
                            if (!Prefs.isValidUrl(trimmedUrl)) {
                                error = "URL must match https://script.google.com/macros/s/.../exec"
                            } else if (!Prefs.isValidSecret(secret)) {
                                error = "Secret must be at least ${Prefs.MIN_SECRET_LENGTH} characters"
                            } else {
                                vm.saveConnection(trimmedUrl, secret, true)
                                vm.finishOnboarding()
                            }
                        }
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Text(if (step < 2) "Continue" else "Finish setup")
            }
        }

        if (step == 2) {
            TextButton(
                onClick = { vm.finishOnboarding() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Skip for now")
            }
        }
    }
}

@Composable
private fun Bullet(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("•", style = MaterialTheme.typography.bodyMedium)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
