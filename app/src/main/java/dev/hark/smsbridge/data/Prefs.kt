package dev.hark.smsbridge.data

import android.content.Context
import android.os.Build
import dev.hark.smsbridge.redact.SensitivityMode

class Prefs(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("hark_settings", Context.MODE_PRIVATE)
    private val secrets = SecretStore(appContext)

    var webAppUrl: String
        get() = prefs.getString(KEY_URL, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_URL, value.trim()).apply()

    var secret: String?
        get() = secrets.get()
        set(value) {
            if (value.isNullOrBlank()) secrets.clear() else secrets.put(value)
        }

    var deviceName: String
        get() = prefs.getString(KEY_DEVICE, null) ?: defaultDeviceName()
        set(value) = prefs.edit().putString(KEY_DEVICE, value.trim()).apply()

    var windowMinutes: Int
        get() = prefs.getInt(KEY_WINDOW, 60).coerceIn(15, 1440)
        set(value) = prefs.edit().putInt(KEY_WINDOW, value.coerceIn(15, 1440)).apply()

    var sensitivityMode: SensitivityMode
        get() = runCatching {
            SensitivityMode.valueOf(prefs.getString(KEY_MODE, SensitivityMode.MASK.name).orEmpty())
        }.getOrDefault(SensitivityMode.MASK)
        set(value) = prefs.edit().putString(KEY_MODE, value.name).apply()

    var resolveContacts: Boolean
        get() = prefs.getBoolean(KEY_CONTACTS, false)
        set(value) = prefs.edit().putBoolean(KEY_CONTACTS, value).apply()

    var sweepEnabled: Boolean
        get() = prefs.getBoolean(KEY_SWEEP, true)
        set(value) = prefs.edit().putBoolean(KEY_SWEEP, value).apply()

    var backfillDays: Int
        get() = prefs.getInt(KEY_BACKFILL_DAYS, 7).coerceIn(1, 90)
        set(value) = prefs.edit().putInt(KEY_BACKFILL_DAYS, value.coerceIn(1, 90)).apply()

    var onboardingDone: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDING, value).apply()

    var lastUploadAt: Long
        get() = prefs.getLong(KEY_LAST_UPLOAD, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_UPLOAD, value).apply()

    var lastError: String?
        get() = prefs.getString(KEY_LAST_ERROR, null)
        set(value) = prefs.edit().putString(KEY_LAST_ERROR, value).apply()

    var lastErrorAt: Long
        get() = prefs.getLong(KEY_LAST_ERROR_AT, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_ERROR_AT, value).apply()

    val isConfigured: Boolean
        get() = isValidUrl(webAppUrl) && (secret?.length ?: 0) >= MIN_SECRET_LENGTH

    fun setConnection(url: String, secretValue: String) {
        webAppUrl = url
        secret = secretValue
    }

    fun clearConnection() {
        webAppUrl = ""
        secret = null
    }

    fun recordError(message: String) {
        lastError = message.take(1000)
        lastErrorAt = System.currentTimeMillis()
    }

    fun clearError() {
        lastError = null
        lastErrorAt = 0L
    }

    private fun defaultDeviceName(): String {
        val manufacturer = Build.MANUFACTURER.orEmpty().trim()
        val model = Build.MODEL.orEmpty().trim()
        return listOf(manufacturer, model).filter { it.isNotBlank() }.joinToString(" ").ifBlank { "Android" }
    }

    companion object {
        const val MIN_SECRET_LENGTH = 32

        private const val KEY_URL = "web_app_url"
        private const val KEY_DEVICE = "device_name"
        private const val KEY_ONBOARDING = "onboarding_done"
        private const val KEY_WINDOW = "window_minutes"
        private const val KEY_MODE = "sensitivity_mode"
        private const val KEY_CONTACTS = "resolve_contacts"
        private const val KEY_SWEEP = "sweep_enabled"
        private const val KEY_BACKFILL_DAYS = "backfill_days"
        private const val KEY_LAST_UPLOAD = "last_upload_at"
        private const val KEY_LAST_ERROR = "last_error"
        private const val KEY_LAST_ERROR_AT = "last_error_at"

        private val URL_PATTERN = Regex("^https://script\\.google\\.com/macros/s/[A-Za-z0-9_-]+/exec$")

        fun isValidUrl(url: String): Boolean = URL_PATTERN.matches(url.trim())

        fun isValidSecret(value: String): Boolean = value.length >= MIN_SECRET_LENGTH
    }
}
