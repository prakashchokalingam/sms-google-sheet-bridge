package io.github.prakashchokalingam.smsgooglesheetbridge.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.github.prakashchokalingam.smsgooglesheetbridge.SmsBridgeApp
import io.github.prakashchokalingam.smsgooglesheetbridge.core.IstTime
import io.github.prakashchokalingam.smsgooglesheetbridge.net.PayloadBuilder
import io.github.prakashchokalingam.smsgooglesheetbridge.net.UploadResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BatchWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val app = applicationContext as? SmsBridgeApp ?: return@withContext Result.failure()
        val container = app.container
        val prefs = container.prefs

        if (!prefs.isConfigured) {
            return@withContext Result.success()
        }

        try {
            val now = System.currentTimeMillis()
            createDueBatches(app, now)
            val retry = uploadPending(app)
            WorkScheduler.scheduleNext(applicationContext)
            if (retry) Result.retry() else Result.success()
        } catch (e: Exception) {
            prefs.recordError("Batch failed: ${e.message}")
            WorkScheduler.scheduleNext(applicationContext)
            Result.retry()
        }
    }

    private fun createDueBatches(app: SmsBridgeApp, now: Long): Int {
        val container = app.container
        val minutes = container.prefs.windowMinutes
        val step = minutes * 60_000L
        val windowEnd = IstTime.floorWindow(now, minutes)
        var cursor = container.messages.lastBatchEnd() ?: (windowEnd - step)

        if (cursor > windowEnd) {
            cursor = windowEnd - step
        }
        val earliest = windowEnd - MAX_CATCHUP * step
        if (cursor < earliest) {
            cursor = earliest
        }

        var created = 0
        while (cursor + step <= windowEnd && created < MAX_CATCHUP) {
            container.messages.createBatch(cursor, cursor + step, now)
            cursor += step
            created++
        }
        return created
    }

    private suspend fun uploadPending(app: SmsBridgeApp): Boolean {
        val container = app.container
        val prefs = container.prefs
        val secret = prefs.secret ?: return false
        val pending = container.messages.pendingBatches(50)
        if (pending.isEmpty()) return false

        for (batch in pending) {
            val messages = container.messages.messagesFor(batch.seq)
            val payload = PayloadBuilder.build(batch, messages, prefs.deviceName)
            when (val result = container.uploader.upload(payload, prefs.webAppUrl, secret)) {
                is UploadResult.Success -> {
                    val uploadedAt = result.uploadedAt ?: System.currentTimeMillis()
                    container.messages.markUploaded(batch.seq, uploadedAt)
                    prefs.lastUploadAt = uploadedAt
                    prefs.clearError()
                }

                is UploadResult.Failure -> {
                    container.messages.markFailed(batch.seq, result.message)
                    prefs.recordError("Upload failed for batch ${batch.seq}: ${result.message}")
                    return result.retryable
                }
            }
        }
        return false
    }

    companion object {
        private const val MAX_CATCHUP = 168
    }
}
