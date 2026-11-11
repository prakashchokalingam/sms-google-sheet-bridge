package io.github.prakashchokalingam.smsgooglesheetbridge.net

import io.github.prakashchokalingam.smsgooglesheetbridge.core.IstTime
import io.github.prakashchokalingam.smsgooglesheetbridge.data.BatchRecord
import io.github.prakashchokalingam.smsgooglesheetbridge.data.StoredMessage
import org.json.JSONArray
import org.json.JSONObject

object PayloadBuilder {

    const val VERSION = 1

    fun build(batch: BatchRecord, messages: List<StoredMessage>, device: String): String {
        val root = JSONObject()
        root.put("v", VERSION)
        root.put("seq", batch.seq)
        root.put("kind", "batch")
        root.put("window_start", IstTime.iso(batch.windowStart))
        root.put("window_end", IstTime.iso(batch.windowEnd))
        root.put("device", device)
        root.put("count", batch.count)
        root.put("masked", batch.maskedCount)
        root.put("skipped_otp", batch.skipped)

        val array = JSONArray()
        for (message in messages) {
            val item = JSONObject()
            item.put("msg_id", message.id)
            item.put("received_at", IstTime.iso(message.receivedAt))
            item.put("sender", message.sender)
            item.put("sim", message.sim)
            item.put("body", message.body)
            item.put("redacted", if (message.masked) message.redactions else 0)
            array.put(item)
        }
        root.put("messages", array)
        return root.toString()
    }

    fun testBody(): String = JSONObject().put("test", true).toString()
}
