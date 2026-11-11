package io.github.prakashchokalingam.smsgooglesheetbridge.sms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Telephony
import androidx.core.content.ContextCompat

data class BackfillResult(
    val scanned: Int,
    val stored: Int,
    val skipped: Int,
    val duplicates: Int,
    val error: String? = null
)

class Backfill(
    private val context: Context,
    private val ingest: SmsIngest
) {

    fun run(days: Int): BackfillResult {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return BackfillResult(0, 0, 0, 0, "READ_SMS permission is not granted")
        }

        val since = System.currentTimeMillis() - days.coerceIn(1, 90) * 86_400_000L
        var scanned = 0
        var stored = 0
        var skipped = 0
        var duplicates = 0

        return try {
            context.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
                "${Telephony.Sms.DATE} >= ?",
                arrayOf(since.toString()),
                "${Telephony.Sms.DATE} ASC"
            )?.use { cursor ->
                val addressIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val bodyIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val dateIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)

                while (cursor.moveToNext()) {
                    scanned++
                    val address = if (cursor.isNull(addressIndex)) null else cursor.getString(addressIndex)
                    val body = if (cursor.isNull(bodyIndex)) "" else cursor.getString(bodyIndex)
                    val date = cursor.getLong(dateIndex)
                    when (val result = ingest.ingest(address, body, date, -1)) {
                        is IngestResult.Stored -> {
                            if (result.duplicate) duplicates++ else stored++
                        }
                        is IngestResult.Skipped -> skipped++
                        IngestResult.Ignored -> Unit
                    }
                }
            }

            BackfillResult(scanned, stored, skipped, duplicates)
        } catch (e: Exception) {
            BackfillResult(scanned, stored, skipped, duplicates, e.message ?: "Import failed")
        }
    }
}
