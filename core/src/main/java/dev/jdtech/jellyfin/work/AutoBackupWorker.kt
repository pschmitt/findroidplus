package dev.jdtech.jellyfin.work

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.jdtech.jellyfin.backup.BackupManager
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Writes an unencrypted backup to the user-chosen SAF folder. Auto-backups are always
 * unencrypted (see BackupCrypto's design note) - only the manual "Back up now" export supports a
 * password, typed fresh each time.
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
    override suspend fun doWork(): Result =
        withContext(Dispatchers.IO) {
            val folderUriString =
                appPreferences.getValue(appPreferences.autoBackupFolderUri)
                    ?: return@withContext Result.failure()
            val folder =
                DocumentFile.fromTreeUri(applicationContext, Uri.parse(folderUriString))
                    ?: return@withContext Result.failure()

            try {
                val fileName =
                    "findroid-backup-${SimpleDateFormat("yyyy-MM-dd-HHmmss", Locale.US).format(Date())}.frb"
                val file =
                    folder.createFile("application/octet-stream", fileName)
                        ?: return@withContext Result.failure()

                val envelope = backupManager.buildBackup()
                backupManager.writeBackup(envelope, file.uri, password = null)
                appPreferences.setValue(appPreferences.lastBackupTimestamp, System.currentTimeMillis())
                Result.success()
            } catch (e: Exception) {
                Timber.e(e, "Auto-backup failed")
                Result.retry()
            }
        }
}
