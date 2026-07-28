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

    // Root cause of FINDROID-47 ("auto-backup never runs"): enabling the toggle before picking a
    // folder looked "configured" in Settings (enabled + an interval chosen) but this gate silently
    // cancelled/never enqueued any work - autoBackupLastError was only ever written from inside
    // AutoBackupWorker.doWork(), which never got a chance to run, so nothing ever explained why no
    // backup showed up.
    private const val NO_FOLDER_ERROR = "Auto-backup is enabled but no backup folder is set"

    fun schedule(context: Context, appPreferences: AppPreferences) {
        val workManager = WorkManager.getInstance(context)

        val enabled = appPreferences.getValue(appPreferences.autoBackupEnabled)
        val folderConfigured =
            !appPreferences.getValue(appPreferences.autoBackupFolderUri).isNullOrEmpty()

        // Only keep this scheduled while enabled and a destination folder has actually been
        // chosen - matches scheduleAutoDeleteWatched's gate-and-cancel pattern.
        if (!enabled || !folderConfigured) {
            workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
            if (enabled && !folderConfigured) {
                appPreferences.setValue(appPreferences.autoBackupLastError, NO_FOLDER_ERROR)
            }
            return
        }

        // Conditions are satisfied again - clear a stale NO_FOLDER_ERROR left over from a previous
        // bail-out above, so the Settings screen's error banner doesn't linger after the user
        // picks a folder. Any other error (a real doWork() failure) is left alone; it's still
        // accurate until the next run resolves it.
        if (appPreferences.getValue(appPreferences.autoBackupLastError) == NO_FOLDER_ERROR) {
            appPreferences.setValue(appPreferences.autoBackupLastError, null)
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
