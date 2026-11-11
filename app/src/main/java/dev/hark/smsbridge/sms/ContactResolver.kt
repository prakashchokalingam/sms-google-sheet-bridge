package dev.hark.smsbridge.sms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.util.LruCache
import androidx.core.content.ContextCompat

class ContactResolver(private val context: Context) {

    private val cache = LruCache<String, String>(128)

    fun displayName(number: String?): String {
        if (number.isNullOrBlank()) return ""
        val cached = cache.get(number)
        if (cached != null) return cached

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return ""
        }

        val name = runCatching {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(number)
            )
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0).orEmpty() else ""
            }.orEmpty()
        }.getOrDefault("")

        cache.put(number, name)
        return name
    }
}
