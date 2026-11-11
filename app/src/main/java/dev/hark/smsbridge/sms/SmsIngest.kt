package dev.hark.smsbridge.sms

import dev.hark.smsbridge.core.IstTime
import dev.hark.smsbridge.core.sha256Hex
import dev.hark.smsbridge.data.MessageStore
import dev.hark.smsbridge.data.Prefs
import dev.hark.smsbridge.data.RuleStore
import dev.hark.smsbridge.data.StoredMessage
import dev.hark.smsbridge.redact.RedactionDecision
import dev.hark.smsbridge.redact.RedactionEngine

sealed interface IngestResult {
    data class Stored(val masked: Boolean, val duplicate: Boolean) : IngestResult
    data class Skipped(val reason: String) : IngestResult
    data object Ignored : IngestResult
}

class SmsIngest(
    private val messages: MessageStore,
    private val rules: RuleStore,
    private val prefs: Prefs,
    private val contacts: ContactResolver,
    private val engine: RedactionEngine
) {

    fun ingest(sender: String?, body: String, receivedAt: Long, sim: Int): IngestResult {
        if (body.isBlank()) return IngestResult.Ignored

        val cleanSender = sender?.trim().takeUnless { it.isNullOrBlank() } ?: "unknown"
        val decision = engine.evaluate(
            body = body,
            rules = rules.list(),
            mode = prefs.sensitivityMode,
            sweep = prefs.sweepEnabled
        )

        return when (decision) {
            is RedactionDecision.Skip -> {
                messages.incrementSkipped(IstTime.floorWindow(receivedAt, prefs.windowMinutes))
                IngestResult.Skipped(decision.reason)
            }

            is RedactionDecision.Mask -> {
                val stored = StoredMessage(
                    id = messageId(cleanSender, receivedAt, body),
                    receivedAt = receivedAt,
                    sender = cleanSender,
                    senderName = senderName(cleanSender),
                    sim = sim,
                    body = decision.body,
                    batchSeq = null,
                    masked = true,
                    redactions = decision.redactions
                )
                IngestResult.Stored(masked = true, duplicate = !messages.insert(stored))
            }

            is RedactionDecision.Allow -> {
                val stored = StoredMessage(
                    id = messageId(cleanSender, receivedAt, body),
                    receivedAt = receivedAt,
                    sender = cleanSender,
                    senderName = senderName(cleanSender),
                    sim = sim,
                    body = decision.body,
                    batchSeq = null,
                    masked = false,
                    redactions = 0
                )
                IngestResult.Stored(masked = false, duplicate = !messages.insert(stored))
            }
        }
    }

    private fun senderName(sender: String): String {
        return if (prefs.resolveContacts) contacts.displayName(sender) else ""
    }

    private fun messageId(sender: String, receivedAt: Long, body: String): String {
        return sha256Hex("$sender\u0000$receivedAt\u0000$body")
    }
}
