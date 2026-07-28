package dev.pschmitt.jellyfin.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Schedules [RemoteConfigWorker] unconditionally (unlike [QueueStatusScheduler], this isn't gated
 * on any per-service toggle - it's core plumbing for FINDROID-44's cross-device rule push, not an
 * optional integration).
 */
object RemoteConfigScheduler {
    private const val UNIQUE_WORK_NAME = "remoteConfigSync"

    // WorkManager's own hard floor on periodic work.
    private const val INTERVAL_MINUTES = 15L

    fun schedule(context: Context) {
        val constraints =
            Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        val periodicRequest =
            PeriodicWorkRequestBuilder<RemoteConfigWorker>(INTERVAL_MINUTES, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()

        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(
                uniqueWorkName = UNIQUE_WORK_NAME,
                existingPeriodicWorkPolicy = ExistingPeriodicWorkPolicy.UPDATE,
                request = periodicRequest,
            )
    }
}
