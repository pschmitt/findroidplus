package dev.jdtech.jellyfin.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import java.util.concurrent.TimeUnit

/**
 * Shared between BaseApplication's startup scheduling and BackupSettingsViewModel, so toggling
 * auto-backup on/off, changing the interval, or picking a folder can reschedule immediately
 * without duplicating the gate-and-cancel logic.
 */
object AutoBackupScheduler {
    private const val UNIQUE_WORK_NAME = "autoBackup"

    fun schedule(context: Context, appPreferences: AppPreferences) {
        val workManager = WorkManager.getInstance(context)

        // Only keep this scheduled while enabled and a destination folder has actually been
        // chosen - matches scheduleAutoDeleteWatched's gate-and-cancel pattern.
        if (
            !appPreferences.getValue(appPreferences.autoBackupEnabled) ||
                appPreferences.getValue(appPreferences.autoBackupFolderUri).isNullOrEmpty()
        ) {
            workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
            return
        }

        val intervalMinutes =
            appPreferences
                .getValue(appPreferences.autoBackupIntervalMinutes)
                .coerceIn(15, 30 * 24 * 60)

        val constraints = Constraints.Builder().setRequiresBatteryNotLow(true).build()

        val periodicRequest =
            PeriodicWorkRequestBuilder<AutoBackupWorker>(intervalMinutes.toLong(), TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()

        workManager.enqueueUniquePeriodicWork(
            uniqueWorkName = UNIQUE_WORK_NAME,
            existingPeriodicWorkPolicy = ExistingPeriodicWorkPolicy.UPDATE,
            request = periodicRequest,
        )
    }
}
