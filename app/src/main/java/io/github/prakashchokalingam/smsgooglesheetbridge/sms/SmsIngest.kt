package io.github.prakashchokalingam.smsgooglesheetbridge.sms

import io.github.prakashchokalingam.smsgooglesheetbridge.core.IstTime
import io.github.prakashchokalingam.smsgooglesheetbridge.core.sha256Hex
import io.github.prakashchokalingam.smsgooglesheetbridge.data.MessageStore
import io.github.prakashchokalingam.smsgooglesheetbridge.data.Prefs
import io.github.prakashchokalingam.smsgooglesheetbridge.data.RuleStore
import io.github.prakashchokalingam.smsgooglesheetbridge.data.StoredMessage
import io.github.prakashchokalingam.smsgooglesheetbridge.redact.RedactionDecision
import io.github.prakashchokalingam.smsgooglesheetbridge.redact.RedactionEngine

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
