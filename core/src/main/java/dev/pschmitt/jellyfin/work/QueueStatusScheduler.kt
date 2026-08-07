package dev.pschmitt.jellyfin.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dev.pschmitt.jellyfin.api.pvr.PvrService
import dev.pschmitt.jellyfin.pvr.PvrConfigResolver
import dev.pschmitt.jellyfin.settings.domain.AppPreferences
import java.util.concurrent.TimeUnit

/**
 * Schedules [QueueStatusWorker] as a backstop for
 * [dev.pschmitt.jellyfin.repository. QueueStatusRepository]'s in-process poll loop, mirroring
 * [AutoBackupScheduler]'s gate-and-cancel shape. Only kept scheduled while at least one of
 * Sonarr/Radarr is enabled for the active profile.
 */
object QueueStatusScheduler {
    private const val UNIQUE_WORK_NAME = "queueStatusPoll"

    // WorkManager enforces a hard 15-minute floor on periodic work regardless of what's requested
    // here, so this backstop is necessarily coarser than the in-process poll loop's own
    // (1-minute) floor - it only matters while the app isn't in the foreground.
    private const val MIN_INTERVAL_MINUTES = 15
    private const val MAX_INTERVAL_MINUTES = 24 * 60

    fun schedule(
        context: Context,
        appPreferences: AppPreferences,
        pvrConfigResolver: PvrConfigResolver,
    ) {
        val workManager = WorkManager.getInstance(context)

        // Reads the active profile's resolved config, not the pre-Profiles global toggle - see
        // PvrConfigResolver's kdoc. Only called once at process startup, so a profile switch that
        // changes Sonarr/Radarr's enabled state won't re-evaluate this until the next cold start.
        val sonarrEnabled = pvrConfigResolver.resolveConfig(PvrService.SONARR)?.enabled == true
        val radarrEnabled = pvrConfigResolver.resolveConfig(PvrService.RADARR)?.enabled == true
        if (!sonarrEnabled && !radarrEnabled) {
            workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
            return
        }

        val intervalMinutes =
            appPreferences
                .getValue(appPreferences.pvrPollIntervalMinutes)
                .coerceIn(MIN_INTERVAL_MINUTES, MAX_INTERVAL_MINUTES)

        val constraints =
            Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        val periodicRequest =
            PeriodicWorkRequestBuilder<QueueStatusWorker>(
                    intervalMinutes.toLong(),
                    TimeUnit.MINUTES,
                )
                .setConstraints(constraints)
                .build()

        workManager.enqueueUniquePeriodicWork(
            uniqueWorkName = UNIQUE_WORK_NAME,
            existingPeriodicWorkPolicy = ExistingPeriodicWorkPolicy.UPDATE,
            request = periodicRequest,
        )
    }
}
