package dev.hark.smsbridge.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hark.smsbridge.data.RuleStore
import dev.hark.smsbridge.redact.RedactionRule
import dev.hark.smsbridge.redact.RuleAction

@Composable
fun RulesScreen(state: UiState, vm: AppViewModel) {
    val context = LocalContext.current
    var editing by remember { mutableStateOf<RedactionRule?>(null) }
    var creating by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }
    var exportText by remember { mutableStateOf<String?>(null) }
    var importErrors by remember { mutableStateOf<List<String>>(emptyList()) }

    val skipRules = state.rules.filter { it.action == RuleAction.SKIP }
    val maskRules = state.rules.filter { it.action == RuleAction.MASK }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            InfoBanner(
                text = "Skip rules block the whole message. Mask rules replace only the captured span. Skip rules run first."
            )
        }

        if (state.ruleErrors.isNotEmpty()) {
            item {
                InfoBanner(
                    text = "${state.ruleErrors.size} rules have errors. Fix them, or disable them, to resume sending.",
                    tone = Tone.BAD,
                    icon = Icons.Default.Warning
                )
            }
            items(state.ruleErrors.entries.toList()) { entry ->
                Text(
                    text = "${entry.key}: ${entry.value}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        creating = true
                        editing = vm.newRule()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Add rule")
                }
                OutlinedButton(
                    onClick = vm::resetRules,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Restore defaults")
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        importErrors = emptyList()
                        showImport = true
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Import JSON")
                }
                OutlinedButton(
                    onClick = { vm.exportRules { exportText = it } },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Export JSON")
                }
            }
        }

        item {
            Text(
                "Skip rules (${skipRules.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        items(skipRules, key = { it.id }) { rule ->
            RuleRow(rule, state.ruleErrors[rule.id], onClick = {
                creating = false
                editing = rule
            }, onToggle = { vm.toggleRule(rule.id, it) })
        }

        item {
            Text(
                "Mask rules (${maskRules.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
        items(maskRules, key = { it.id }) { rule ->
            RuleRow(rule, state.ruleErrors[rule.id], onClick = {
                creating = false
                editing = rule
            }, onToggle = { vm.toggleRule(rule.id, it) })
        }
    }

    val rule = editing
    if (rule != null) {
        RuleEditorDialog(
            initial = rule,
            isNew = creating,
            vm = vm,
            onDismiss = {
                editing = null
                creating = false
            }
        )
    }

    if (showImport) {
        ImportDialog(
            errors = importErrors,
            onImport = { text ->
                vm.importRules(text) { outcome: RuleStore.ImportOutcome ->
                    importErrors = outcome.errors
                    if (outcome.errors.isEmpty()) showImport = false
                }
            },
            onDismiss = { showImport = false }
        )
    }

    val exported = exportText
    if (exported != null) {
        ExportDialog(
            text = exported,
            onCopy = { copyText(context, "Hark rules", exported) },
            onDismiss = { exportText = null }
        )
    }
}

@Composable
private fun RuleRow(
    rule: RedactionRule,
    error: String?,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit
) {
    SectionCard(modifier = Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        rule.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.width(6.dp))
                    if (rule.builtIn) {
                        Pill("built-in", Tone.NEUTRAL)
                    }
                }
                Text(
                    rule.pattern,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(8.dp))
            Pill(
                text = rule.action.name,
                tone = if (rule.action == RuleAction.SKIP) Tone.BAD else Tone.GOOD
            )
            Spacer(Modifier.width(8.dp))
            Switch(checked = rule.enabled, onCheckedChange = onToggle)
        }
        if (error != null) {
            Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun RuleEditorDialog(
    initial: RedactionRule,
    isNew: Boolean,
    vm: AppViewModel,
    onDismiss: () -> Unit
) {
    var name by remember(initial.id) { mutableStateOf(initial.name) }
    var pattern by remember(initial.id) { mutableStateOf(initial.pattern) }
    var action by remember(initial.id) { mutableStateOf(initial.action) }
    var group by remember(initial.id) { mutableStateOf(initial.group.toString()) }
    var replacement by remember(initial.id) { mutableStateOf(initial.replacement) }
    var enabled by remember(initial.id) { mutableStateOf(initial.enabled) }
    var sample by remember(initial.id) { mutableStateOf("Your OTP is 123456") }
    var testResult by remember(initial.id) { mutableStateOf<String?>(null) }
    var error by remember(initial.id) { mutableStateOf<String?>(null) }

    val current = initial.copy(
        name = name,
        pattern = pattern,
        action = action,
        group = group.toIntOrNull() ?: 0,
        replacement = replacement,
        enabled = enabled
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "New rule" else "Edit rule") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 500.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = action == RuleAction.MASK,
                        onClick = { action = RuleAction.MASK },
                        label = { Text("Mask") }
                    )
                    FilterChip(
                        selected = action == RuleAction.SKIP,
                        onClick = { action = RuleAction.SKIP },
                        label = { Text("Skip") }
                    )
                }

                OutlinedTextField(
                    value = pattern,
                    onValueChange = {
                        pattern = it
                        testResult = null
                        error = null
                    },
                    label = { Text("Regex") },
                    minLines = 3,
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = group,
                    onValueChange = { group = it.filter(Char::isDigit) },
                    label = { Text("Capture group") },
                    supportingText = { Text("0 masks the whole match. 1 masks the first ( ) group.") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (action == RuleAction.MASK) {
                    OutlinedTextField(
                        value = replacement,
                        onValueChange = { replacement = it },
                        label = { Text("Replacement") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Enabled")
                    Spacer(Modifier.weight(1f))
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }

                HorizontalDivider()

                Text("Test sample", style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(
                    value = sample,
                    onValueChange = {
                        sample = it
                        testResult = null
                    },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(onClick = { testResult = vm.testRule(current, sample) }) {
                    Text("Test rule")
                }
                testResult?.let {
                    InfoBanner(text = it, tone = Tone.NEUTRAL)
                }
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                vm.saveRule(current) { saveError ->
                    if (saveError == null) onDismiss() else error = saveError
                }
            }) {
                Text("Save")
            }
        },
        dismissButton = {
            Row {
                if (!isNew && !initial.builtIn) {
                    TextButton(
                        onClick = {
                            vm.deleteRule(initial.id)
                            onDismiss()
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Delete")
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}

@Composable
private fun ImportDialog(
    errors: List<String>,
    onImport: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import rules") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Paste exported JSON. Imported rules are validated first.")
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    minLines = 6,
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.fillMaxWidth()
                )
                errors.forEach {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onImport(text) }) {
                Text("Import")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun ExportDialog(
    text: String,
    onCopy: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export rules") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SelectionContainer {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        )
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onCopy) {
                Text("Copy")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
