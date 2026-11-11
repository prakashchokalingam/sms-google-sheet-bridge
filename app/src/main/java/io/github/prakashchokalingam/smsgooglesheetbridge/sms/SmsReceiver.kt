package io.github.prakashchokalingam.smsgooglesheetbridge.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsMessage
import android.telephony.SubscriptionManager
import io.github.prakashchokalingam.smsgooglesheetbridge.SmsBridgeApp
import io.github.prakashchokalingam.smsgooglesheetbridge.work.WorkScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (parts.isEmpty()) return

        val app = context.applicationContext as? SmsBridgeApp ?: return
        val pending = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        scope.launch {
            try {
                val grouped = LinkedHashMap<String, MutableList<SmsMessage>>()
                for (part in parts) {
                    val sender = part.originatingAddress
                        ?: part.displayOriginatingAddress
                        ?: "unknown"
                    grouped.getOrPut(sender) { mutableListOf() }.add(part)
                }

                val sim = runCatching {
                    SubscriptionManager.getDefaultSmsSubscriptionId()
                }.getOrDefault(-1)

                for ((sender, group) in grouped) {
                    val body = group.joinToString(separator = "") { it.messageBody.orEmpty() }
                    val receivedAt = group.minOfOrNull { it.timestampMillis } ?: System.currentTimeMillis()
                    app.container.ingest.ingest(sender, body, receivedAt, sim)
                }

                WorkScheduler.scheduleNext(context)
            } catch (e: Exception) {
                app.container.prefs.recordError("Receive failed: ${e.message}")
            } finally {
                pending.finish()
            }
        }
    }
}
