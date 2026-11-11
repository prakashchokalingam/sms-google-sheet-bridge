package dev.hark.smsbridge.ui

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.PowerManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.hark.smsbridge.HarkApp
import dev.hark.smsbridge.data.BatchRecord
import dev.hark.smsbridge.data.Counts
import dev.hark.smsbridge.data.Prefs
import dev.hark.smsbridge.data.RuleStore
import dev.hark.smsbridge.data.StoredMessage
import dev.hark.smsbridge.net.UploadResult
import dev.hark.smsbridge.redact.RedactionRule
import dev.hark.smsbridge.redact.SensitivityMode
import dev.hark.smsbridge.work.WorkScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

data class UiState(
    val loaded: Boolean = false,
    val configured: Boolean = false,
    val onboardingDone: Boolean = false,
    val hasReceiveSms: Boolean = false,
    val hasReadSms: Boolean = false,
    val hasContacts: Boolean = false,
    val batteryOptimized: Boolean = true,
    val url: String = "",
    val hasSecret: Boolean = false,
    val deviceName: String = "",
    val windowMinutes: Int = 60,
    val mode: SensitivityMode = SensitivityMode.MASK,
    val resolveContacts: Boolean = false,
    val sweepEnabled: Boolean = true,
    val backfillDays: Int = 7,
    val lastUploadAt: Long = 0L,
    val lastError: String? = null,
    val counts: Counts = Counts(0, 0, 0, 0, 0),
    val messages: List<StoredMessage> = emptyList(),
    val batches: List<BatchRecord> = emptyList(),
    val rules: List<RedactionRule> = emptyList(),
    val ruleErrors: Map<String, String> = emptyMap(),
    val busy: Boolean = false,
    val notice: String? = null,
    val noticeSeq: Int = 0
)

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val container = (app as HarkApp).container

    var state by mutableStateOf(UiState())
        private set

    fun refresh() {
        viewModelScope.launch {
            val snapshot = withContext(Dispatchers.IO) { snapshot() }
            state = snapshot.copy(
                notice = state.notice,
                noticeSeq = state.noticeSeq,
                busy = state.busy
            )
        }
    }

    fun refreshPermissions() {
        refresh()
    }

    fun consumeNotice() {
        state = state.copy(notice = null)
    }

    fun finishOnboarding() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { container.prefs.onboardingDone = true }
            WorkScheduler.schedule(getApplication())
            refresh()
        }
    }

    fun reopenOnboarding() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { container.prefs.onboardingDone = false }
            refresh()
        }
    }

    fun testRule(rule: RedactionRule, sample: String): String {
        val decision = container.engine.evaluate(
            body = sample,
            rules = listOf(rule.copy(enabled = true)),
            mode = SensitivityMode.MASK,
            sweep = false
        )
        return when (decision) {
            is dev.hark.smsbridge.redact.RedactionDecision.Allow ->
                "ALLOW\n${decision.body}"
            is dev.hark.smsbridge.redact.RedactionDecision.Mask ->
                "MASK (${decision.redactions} redaction)\n${decision.body}"
            is dev.hark.smsbridge.redact.RedactionDecision.Skip ->
                "SKIP (${decision.reason})"
        }
    }

    fun runNow() {
        if (!container.prefs.isConfigured) {
            notice("Add the Web App URL and secret first")
            return
        }
        WorkScheduler.runNow(getApplication())
        notice("Upload queued")
    }

    fun testSavedConnection() {
        val url = container.prefs.webAppUrl
        val secret = container.prefs.secret
        if (!container.prefs.isConfigured || secret == null) {
            notice("Add the Web App URL and secret first")
            return
        }
        viewModelScope.launch {
            state = state.copy(busy = true)
            val result = withContext(Dispatchers.IO) {
                container.uploader.testConnection(url, secret)
            }
            state = state.copy(busy = false)
            notice(describe(result))
        }
    }

    fun saveConnection(url: String, secret: String, test: Boolean) {
        viewModelScope.launch {
            val trimmed = url.trim()
            if (!Prefs.isValidUrl(trimmed)) {
                notice("URL must be https://script.google.com/macros/s/.../exec")
                return@launch
            }
            val effective = secret.ifBlank { container.prefs.secret.orEmpty() }
            if (!Prefs.isValidSecret(effective)) {
                notice("Secret must be at least ${Prefs.MIN_SECRET_LENGTH} characters")
                return@launch
            }

            state = state.copy(busy = true)
            withContext(Dispatchers.IO) { container.prefs.setConnection(trimmed, effective) }
            WorkScheduler.schedule(getApplication())

            if (test) {
                val result = withContext(Dispatchers.IO) {
                    container.uploader.testConnection(trimmed, effective)
                }
                notice(describe(result))
            } else {
                notice("Connection saved")
            }

            state = state.copy(busy = false)
            refresh()
        }
    }

    fun saveDeviceName(name: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { container.prefs.deviceName = name }
            notice("Device name saved")
            refresh()
        }
    }

    fun setWindowMinutes(minutes: Int) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { container.prefs.windowMinutes = minutes }
            WorkScheduler.scheduleNext(getApplication())
            notice("Window set to ${describeWindow(minutes)}")
            refresh()
        }
    }

    fun setMode(mode: SensitivityMode) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { container.prefs.sensitivityMode = mode }
            notice(if (mode == SensitivityMode.MASK) "Mask mode on" else "Gate mode on")
            refresh()
        }
    }

    fun setResolveContacts(enabled: Boolean) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { container.prefs.resolveContacts = enabled }
            refresh()
        }
    }

    fun setSweepEnabled(enabled: Boolean) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { container.prefs.sweepEnabled = enabled }
            notice(if (enabled) "Code sweep on" else "Code sweep off")
            refresh()
        }
    }

    fun setBackfillDays(days: Int) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { container.prefs.backfillDays = days }
            refresh()
        }
    }

    fun backfill() {
        val days = container.prefs.backfillDays
        viewModelScope.launch {
            state = state.copy(busy = true)
            val result = withContext(Dispatchers.IO) { container.backfill.run(days) }
            state = state.copy(busy = false)
            notice(
                result.error ?: "Scanned ${result.scanned}, stored ${result.stored}, " +
                    "skipped ${result.skipped}, duplicates ${result.duplicates}"
            )
            refresh()
        }
    }

    fun saveRule(rule: RedactionRule, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            val error = withContext(Dispatchers.IO) { container.engine.validate(rule) }
            if (error != null) {
                onResult(error)
                return@launch
            }
            withContext(Dispatchers.IO) { container.rules.save(rule) }
            WorkScheduler.scheduleNext(getApplication())
            onResult(null)
            notice("Rule saved")
            refresh()
        }
    }

    fun toggleRule(id: String, enabled: Boolean) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { container.rules.setEnabled(id, enabled) }
            WorkScheduler.scheduleNext(getApplication())
            refresh()
        }
    }

    fun deleteRule(id: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { container.rules.deleteCustom(id) }
            notice("Rule deleted")
            refresh()
        }
    }

    fun resetRules() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { container.rules.resetBuiltIns() }
            WorkScheduler.scheduleNext(getApplication())
            notice("Built-in rules restored")
            refresh()
        }
    }

    fun importRules(text: String, onResult: (RuleStore.ImportOutcome) -> Unit) {
        viewModelScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                container.rules.importJson(text) { container.engine.validate(it) }
            }
            WorkScheduler.scheduleNext(getApplication())
            onResult(outcome)
            notice("Imported ${outcome.imported} rules")
            refresh()
        }
    }

    fun exportRules(onResult: (String) -> Unit) {
        viewModelScope.launch {
            val json = withContext(Dispatchers.IO) { container.rules.exportJson() }
            onResult(json)
        }
    }

    fun clearError() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { container.prefs.clearError() }
            notice("Error cleared")
            refresh()
        }
    }

    fun newRule(): RedactionRule {
        return RedactionRule(
            id = "custom.${UUID.randomUUID()}",
            name = "Custom rule",
            pattern = "",
            action = dev.hark.smsbridge.redact.RuleAction.MASK,
            group = 1,
            enabled = true,
            priority = 300,
            builtIn = false
        )
    }

    private fun snapshot(): UiState {
        val c = container
        val application = getApplication<Application>()
        val rules = c.rules.list()
        val power = application.getSystemService(Context.POWER_SERVICE) as PowerManager
        return UiState(
            loaded = true,
            configured = c.prefs.isConfigured,
            onboardingDone = c.prefs.onboardingDone,
            hasReceiveSms = granted(Manifest.permission.RECEIVE_SMS),
            hasReadSms = granted(Manifest.permission.READ_SMS),
            hasContacts = granted(Manifest.permission.READ_CONTACTS),
            batteryOptimized = !power.isIgnoringBatteryOptimizations(application.packageName),
            url = c.prefs.webAppUrl,
            hasSecret = (c.prefs.secret?.length ?: 0) >= Prefs.MIN_SECRET_LENGTH,
            deviceName = c.prefs.deviceName,
            windowMinutes = c.prefs.windowMinutes,
            mode = c.prefs.sensitivityMode,
            resolveContacts = c.prefs.resolveContacts,
            sweepEnabled = c.prefs.sweepEnabled,
            backfillDays = c.prefs.backfillDays,
            lastUploadAt = c.prefs.lastUploadAt,
            lastError = c.prefs.lastError,
            counts = c.messages.counts(),
            messages = c.messages.recentMessages(200),
            batches = c.messages.recentBatches(30),
            rules = rules,
            ruleErrors = c.engine.validateAll(rules)
        )
    }

    private fun granted(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(getApplication(), permission) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun notice(message: String) {
        state = state.copy(notice = message, noticeSeq = state.noticeSeq + 1)
    }

    private fun describe(result: UploadResult): String {
        return when (result) {
            is UploadResult.Success -> "Connection OK"
            is UploadResult.Failure -> "Test failed: ${result.message}"
        }
    }

    private fun describeWindow(minutes: Int): String {
        return when {
            minutes < 60 -> "$minutes minutes"
            minutes == 60 -> "1 hour"
            minutes == 1440 -> "24 hours"
            minutes % 60 == 0 -> "${minutes / 60} hours"
            else -> "$minutes minutes"
        }
    }
}
