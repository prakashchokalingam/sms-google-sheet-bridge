package dev.hark.smsbridge.net

import dev.hark.smsbridge.core.hmacSha256Hex
import dev.hark.smsbridge.data.Prefs
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

sealed interface UploadResult {
    data class Success(
        val duplicate: Boolean = false,
        val uploadedAt: Long? = null
    ) : UploadResult

    data class Failure(
        val message: String,
        val retryable: Boolean
    ) : UploadResult
}

class SheetUploader(private val prefs: Prefs) {

    suspend fun testConnection(url: String, secret: String): UploadResult {
        return send(url, secret, PayloadBuilder.testBody())
    }

    suspend fun upload(payload: String, url: String, secret: String): UploadResult {
        return send(url, secret, payload)
    }

    private fun send(url: String, secret: String, payload: String): UploadResult {
        val trimmedUrl = url.trim()
        if (!Prefs.isValidUrl(trimmedUrl)) {
            return UploadResult.Failure("Invalid Web App URL", false)
        }
        if (!Prefs.isValidSecret(secret)) {
            return UploadResult.Failure("Secret is shorter than 32 characters", false)
        }

        val timestamp = (System.currentTimeMillis() / 1000L).toString()
        val signature = hmacSha256Hex(secret, "$timestamp.$payload")
        val envelope = JSONObject()
            .put("ts", timestamp)
            .put("sig", signature)
            .put("body", payload)
            .toString()

        return try {
            val response = post(trimmedUrl, envelope)
            parse(response)
        } catch (e: Exception) {
            UploadResult.Failure(e.message ?: "Network error", true)
        }
    }

    private fun post(url: String, body: String): HttpResponse {
        var current = url
        var method = "POST"
        var payload: String? = body
        var hops = 0

        while (true) {
            val connection = (URL(current).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = false
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "HarkSmsBridge/0.1")
            }

            try {
                if (method == "POST") {
                    connection.doOutput = true
                    connection.outputStream.use { it.write((payload ?: "").toByteArray(Charsets.UTF_8)) }
                }
                val code = connection.responseCode
                if (code in REDIRECT_CODES && hops < 3) {
                    val location = connection.getHeaderField("Location")
                        ?: return HttpResponse(code, connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty())
                    if (!isAllowedRedirect(location)) {
                        return HttpResponse(code, "Blocked redirect")
                    }
                    current = location
                    method = "GET"
                    payload = null
                    hops++
                    continue
                }
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                return HttpResponse(code, text)
            } finally {
                connection.disconnect()
            }
        }
    }

    private fun parse(response: HttpResponse): UploadResult {
        if (response.code !in 200..299) {
            val retryable = response.code >= 500 || response.code == 429
            return UploadResult.Failure(
                "HTTP ${response.code}: ${response.body.take(200)}",
                retryable
            )
        }

        val json = runCatching { JSONObject(response.body) }.getOrNull()
            ?: return UploadResult.Failure(
                "Unexpected response from Web App. Check that access is set to Anyone.",
                false
            )

        if (json.optBoolean("ok", false)) {
            val uploadedAt = json.optString("uploaded_at").takeIf { it.isNotBlank() }?.let { value ->
                runCatching {
                    OffsetDateTime.parse(value, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant().toEpochMilli()
                }.getOrNull()
            }
            return UploadResult.Success(
                duplicate = json.optBoolean("duplicate", false),
                uploadedAt = uploadedAt
            )
        }

        val error = json.optString("error", "Upload rejected")
        val retryable = !error.contains("signature", ignoreCase = true) &&
            !error.contains("configured", ignoreCase = true)
        return UploadResult.Failure(error, retryable)
    }

    private fun isAllowedRedirect(location: String): Boolean {
        val parsed = runCatching { URL(location) }.getOrNull() ?: return false
        if (parsed.protocol != "https") return false
        val host = parsed.host
        return host == "script.google.com" || host.endsWith(".googleusercontent.com")
    }

    private data class HttpResponse(val code: Int, val body: String)

    companion object {
        private val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
    }
}
