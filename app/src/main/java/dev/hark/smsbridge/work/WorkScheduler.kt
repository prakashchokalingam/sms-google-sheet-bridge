package dev.hark.smsbridge.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dev.hark.smsbridge.HarkApp
import dev.hark.smsbridge.core.IstTime
import java.util.concurrent.TimeUnit

object WorkScheduler {

    private const val PERIODIC = "hark-sms-batch-periodic"
    private const val NEXT = "hark-sms-batch-next"
    private const val NOW = "hark-sms-batch-now"

    private val constraints: Constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    fun schedule(context: Context) {
        val periodic = PeriodicWorkRequestBuilder<BatchWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC,
            ExistingPeriodicWorkPolicy.KEEP,
            periodic
        )
        scheduleNext(context)
    }

    fun scheduleNext(context: Context) {
        val app = context.applicationContext as? HarkApp ?: return
        val prefs = app.container.prefs
        if (!prefs.isConfigured) {
            WorkManager.getInstance(context).cancelUniqueWork(NEXT)
            return
        }

        val now = System.currentTimeMillis()
        val next = IstTime.nextWindowStart(now, prefs.windowMinutes) + 60_000L
        val delay = (next - now).coerceAtLeast(60_000L)
        val request = OneTimeWorkRequestBuilder<BatchWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(NEXT, ExistingWorkPolicy.REPLACE, request)
    }

    fun runNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<BatchWorker>()
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, request)
    }
}
