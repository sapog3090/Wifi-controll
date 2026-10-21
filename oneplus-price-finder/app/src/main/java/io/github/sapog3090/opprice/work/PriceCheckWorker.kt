package io.github.sapog3090.opprice.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.sapog3090.opprice.container
import java.util.concurrent.TimeUnit

class PriceCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val tracks = applicationContext.container.tracks
        for (id in tracks.trackedIds()) {
            val outcome = tracks.check(id) ?: continue
            if (outcome.isNews) Notifier.notifyDrop(applicationContext, outcome)
        }
        return Result.success()
    }
}

object PriceCheckScheduler {
    private const val WORK_NAME = "price-check"

    fun schedule(context: Context, hours: Int, replace: Boolean) {
        val request = PeriodicWorkRequestBuilder<PriceCheckWorker>(hours.toLong(), TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInitialDelay(hours.toLong(), TimeUnit.HOURS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            if (replace) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }
}
