package dev.hark.smsbridge.data

data class StoredMessage(
    val id: String,
    val receivedAt: Long,
    val sender: String,
    val senderName: String,
    val sim: Int,
    val body: String,
    val batchSeq: Int?,
    val masked: Boolean,
    val redactions: Int
)

data class BatchRecord(
    val seq: Int,
    val windowStart: Long,
    val windowEnd: Long,
    val count: Int,
    val maskedCount: Int,
    val skipped: Int,
    val status: String,
    val createdAt: Long,
    val sentAt: Long?,
    val attempts: Int,
    val lastError: String?
)

data class Counts(
    val pendingMessages: Int,
    val pendingBatches: Int,
    val maskedMessages: Int,
    val skippedTotal: Int,
    val uploadedBatches: Int
)

data class SyncTotals(
    val messages: Int,
    val masked: Int,
    val skipped: Int,
    val events: Int
)
