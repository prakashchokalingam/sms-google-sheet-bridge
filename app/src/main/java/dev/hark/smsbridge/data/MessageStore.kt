package dev.hark.smsbridge.data

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase

class MessageStore(private val helper: HarkDb) {

    private val db: SQLiteDatabase
        get() = helper.writableDatabase

    fun insert(message: StoredMessage): Boolean {
        val values = ContentValues().apply {
            put("id", message.id)
            put("received_at", message.receivedAt)
            put("sender", message.sender)
            put("sender_name", message.senderName)
            put("sim", message.sim)
            put("body", message.body)
            if (message.batchSeq == null) putNull("batch_seq") else put("batch_seq", message.batchSeq)
            put("masked", if (message.masked) 1 else 0)
            put("redactions", message.redactions)
        }
        return db.insertWithOnConflict("sms", null, values, SQLiteDatabase.CONFLICT_IGNORE) != -1L
    }

    fun incrementSkipped(windowStart: Long) {
        db.beginTransaction()
        try {
            db.execSQL(
                "INSERT OR IGNORE INTO window_counter (window_start, masked_count, skipped_otp) VALUES (?, 0, 0)",
                arrayOf(windowStart)
            )
            db.execSQL(
                "UPDATE window_counter SET skipped_otp = skipped_otp + 1 WHERE window_start = ?",
                arrayOf(windowStart)
            )
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun createBatch(windowStart: Long, windowEnd: Long, now: Long): BatchRecord {
        db.beginTransaction()
        try {
            val existing = findBatch(windowStart, windowEnd)
            if (existing != null) {
                db.setTransactionSuccessful()
                return existing
            }

            val seq = db.rawQuery("SELECT COALESCE(MAX(seq), 0) + 1 FROM batch", null).use { cursor ->
                cursor.moveToFirst()
                cursor.getInt(0)
            }

            val assign = ContentValues().apply { put("batch_seq", seq) }
            val count = db.update(
                "sms",
                assign,
                "batch_seq IS NULL AND received_at < ?",
                arrayOf(windowEnd.toString())
            )

            val maskedCount = db.rawQuery(
                "SELECT COUNT(*) FROM sms WHERE batch_seq = ? AND masked = 1",
                arrayOf(seq.toString())
            ).use { cursor ->
                cursor.moveToFirst()
                cursor.getInt(0)
            }

            val skipped = db.rawQuery(
                "SELECT COALESCE(SUM(skipped_otp), 0) FROM window_counter WHERE window_start <= ?",
                arrayOf(windowEnd.toString())
            ).use { cursor ->
                cursor.moveToFirst()
                cursor.getInt(0)
            }

            db.delete("window_counter", "window_start <= ?", arrayOf(windowEnd.toString()))

            val values = ContentValues().apply {
                put("seq", seq)
                put("window_start", windowStart)
                put("window_end", windowEnd)
                put("count", count)
                put("masked_count", maskedCount)
                put("skipped_otp", skipped)
                put("status", STATUS_PENDING)
                put("created_at", now)
                put("attempts", 0)
            }
            db.insertOrThrow("batch", null, values)
            db.setTransactionSuccessful()
            return BatchRecord(
                seq = seq,
                windowStart = windowStart,
                windowEnd = windowEnd,
                count = count,
                maskedCount = maskedCount,
                skipped = skipped,
                status = STATUS_PENDING,
                createdAt = now,
                sentAt = null,
                attempts = 0,
                lastError = null
            )
        } finally {
            db.endTransaction()
        }
    }

    fun pendingBatches(limit: Int = 25): List<BatchRecord> {
        return db.rawQuery(
            "SELECT * FROM batch WHERE status = ? ORDER BY seq ASC LIMIT ?",
            arrayOf(STATUS_PENDING, limit.toString())
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(batchFrom(cursor))
            }
        }
    }

    fun messagesFor(seq: Int): List<StoredMessage> {
        return db.rawQuery(
            "SELECT * FROM sms WHERE batch_seq = ? ORDER BY received_at ASC",
            arrayOf(seq.toString())
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(messageFrom(cursor))
            }
        }
    }

    fun markUploaded(seq: Int, uploadedAt: Long) {
        db.beginTransaction()
        try {
            db.execSQL(
                "UPDATE batch SET status = ?, sent_at = ?, attempts = attempts + 1, last_error = NULL WHERE seq = ?",
                arrayOf<Any>(STATUS_UPLOADED, uploadedAt, seq)
            )
            db.delete("sms", "batch_seq = ?", arrayOf(seq.toString()))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun markFailed(seq: Int, error: String) {
        db.execSQL(
            "UPDATE batch SET attempts = attempts + 1, last_error = ? WHERE seq = ?",
            arrayOf<Any>(error.take(500), seq)
        )
    }

    fun counts(): Counts {
        val pendingMessages = scalar("SELECT COUNT(*) FROM sms")
        val maskedMessages = scalar("SELECT COUNT(*) FROM sms WHERE masked = 1")
        val pendingBatches = scalar("SELECT COUNT(*) FROM batch WHERE status = '$STATUS_PENDING'")
        val uploadedBatches = scalar("SELECT COUNT(*) FROM batch WHERE status = '$STATUS_UPLOADED'")
        val skipped = scalar("SELECT COALESCE(SUM(skipped_otp), 0) FROM batch") +
            scalar("SELECT COALESCE(SUM(skipped_otp), 0) FROM window_counter")
        return Counts(
            pendingMessages = pendingMessages,
            pendingBatches = pendingBatches,
            maskedMessages = maskedMessages,
            skippedTotal = skipped,
            uploadedBatches = uploadedBatches
        )
    }

    fun recentMessages(limit: Int = 200): List<StoredMessage> {
        return db.rawQuery(
            "SELECT * FROM sms ORDER BY received_at DESC LIMIT ?",
            arrayOf(limit.toString())
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(messageFrom(cursor))
            }
        }
    }

    fun recentBatches(limit: Int = 30): List<BatchRecord> {
        return db.rawQuery(
            "SELECT * FROM batch ORDER BY seq DESC LIMIT ?",
            arrayOf(limit.toString())
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(batchFrom(cursor))
            }
        }
    }

    fun lastBatchEnd(): Long? {
        return db.rawQuery("SELECT MAX(window_end) FROM batch", null).use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
        }
    }

    fun firstBatchStart(): Long? {
        return db.rawQuery("SELECT MIN(window_start) FROM batch", null).use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
        }
    }

    private fun findBatch(windowStart: Long, windowEnd: Long): BatchRecord? {
        return db.rawQuery(
            "SELECT * FROM batch WHERE window_start = ? AND window_end = ?",
            arrayOf(windowStart.toString(), windowEnd.toString())
        ).use { cursor ->
            if (cursor.moveToFirst()) batchFrom(cursor) else null
        }
    }

    private fun scalar(sql: String): Int {
        return db.rawQuery(sql, null).use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }
    }

    private fun messageFrom(cursor: Cursor): StoredMessage {
        val batchIndex = cursor.getColumnIndexOrThrow("batch_seq")
        return StoredMessage(
            id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
            receivedAt = cursor.getLong(cursor.getColumnIndexOrThrow("received_at")),
            sender = cursor.getString(cursor.getColumnIndexOrThrow("sender")),
            senderName = cursor.getString(cursor.getColumnIndexOrThrow("sender_name")),
            sim = cursor.getInt(cursor.getColumnIndexOrThrow("sim")),
            body = cursor.getString(cursor.getColumnIndexOrThrow("body")),
            batchSeq = if (cursor.isNull(batchIndex)) null else cursor.getInt(batchIndex),
            masked = cursor.getInt(cursor.getColumnIndexOrThrow("masked")) == 1,
            redactions = cursor.getInt(cursor.getColumnIndexOrThrow("redactions"))
        )
    }

    private fun batchFrom(cursor: Cursor): BatchRecord {
        val sentIndex = cursor.getColumnIndexOrThrow("sent_at")
        val errorIndex = cursor.getColumnIndexOrThrow("last_error")
        return BatchRecord(
            seq = cursor.getInt(cursor.getColumnIndexOrThrow("seq")),
            windowStart = cursor.getLong(cursor.getColumnIndexOrThrow("window_start")),
            windowEnd = cursor.getLong(cursor.getColumnIndexOrThrow("window_end")),
            count = cursor.getInt(cursor.getColumnIndexOrThrow("count")),
            maskedCount = cursor.getInt(cursor.getColumnIndexOrThrow("masked_count")),
            skipped = cursor.getInt(cursor.getColumnIndexOrThrow("skipped_otp")),
            status = cursor.getString(cursor.getColumnIndexOrThrow("status")),
            createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("created_at")),
            sentAt = if (cursor.isNull(sentIndex)) null else cursor.getLong(sentIndex),
            attempts = cursor.getInt(cursor.getColumnIndexOrThrow("attempts")),
            lastError = if (cursor.isNull(errorIndex)) null else cursor.getString(errorIndex)
        )
    }

    companion object {
        const val STATUS_PENDING = "PENDING"
        const val STATUS_UPLOADED = "UPLOADED"
    }
}
