package dev.pschmitt.jellyfin.work

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.pschmitt.jellyfin.backup.BackupFileNaming
import dev.pschmitt.jellyfin.backup.BackupManager
import dev.pschmitt.jellyfin.settings.domain.AppPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Writes a backup to the user-chosen SAF folder, encrypted with the configured auto-backup password
 * if one is set (see AppPreferences.autoBackupPassword), same as an unencrypted export when left
 * blank.
 */
@HiltWorker
class AutoBackupWorker
@AssistedInject
constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val backupManager: BackupManager,
    private val appPreferences: AppPreferences,
) : CoroutineWorker(context, params) {
    /**
     * Records [reason] to [AppPreferences.autoBackupLastError] so it survives process death and can
     * be surfaced in the Backup & Restore settings screen - a scheduled auto-backup that fails has
     * no other way to reach the user, unlike the manual "Back up now" flow's snackbar.
     */
    private fun recordFailure(reason: String) {
        appPreferences.setValue(appPreferences.autoBackupLastError, reason)
    }

    override suspend fun doWork(): Result =
        withContext(Dispatchers.IO) {
            val folderUriString =
                appPreferences.getValue(appPreferences.autoBackupFolderUri)
                    ?: run {
                        Timber.w("Auto-backup failed: no backup folder configured")
                        recordFailure("No backup folder configured")
                        return@withContext Result.failure()
                    }
            val folder =
                DocumentFile.fromTreeUri(applicationContext, Uri.parse(folderUriString))
                    ?: run {
                        Timber.w(
                            "Auto-backup failed: could not resolve backup folder URI %s",
                            folderUriString,
                        )
                        recordFailure("Backup folder is invalid or inaccessible")
                        return@withContext Result.failure()
                    }

            try {
                val fileName = BackupFileNaming.fileName()
                val file =
                    folder.createFile("application/octet-stream", fileName)
                        ?: run {
                            Timber.w(
                                "Auto-backup failed: could not create backup file in %s " +
                                    "(SAF folder grant may have been revoked or the folder moved/deleted)",
                                folderUriString,
                            )
                            recordFailure(
                                "Could not create backup file - check the backup folder is still accessible"
                            )
                            return@withContext Result.failure()
                        }

                val password = appPreferences.getValue(appPreferences.autoBackupPassword)
                val envelope = backupManager.buildBackup()
                backupManager.writeBackup(envelope, file.uri, password = password)
                appPreferences.setValue(
                    appPreferences.lastBackupTimestamp,
                    System.currentTimeMillis(),
                )
                appPreferences.setValue(appPreferences.autoBackupLastError, null)
                Result.success()
            } catch (e: Exception) {
                Timber.e(e, "Auto-backup failed with an exception, will retry")
                recordFailure(e.message ?: e::class.simpleName ?: "Unknown error")
                Result.retry()
            }
        }
}
