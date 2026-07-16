package dev.jdtech.jellyfin.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.jdtech.jellyfin.repository.QueueStatusRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Backstop for [QueueStatusRepository]'s in-process poll loop, which only runs while the app
 * process is alive - this keeps the Sonarr/Radarr queue status reasonably fresh while the app is
 * backgrounded or the process has been killed, mirroring [AutoBackupWorker]'s shape.
 */
@HiltWorker
class QueueStatusWorker
@AssistedInject
constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val queueStatusRepository: QueueStatusRepository,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result =
        withContext(Dispatchers.IO) {
            try {
                queueStatusRepository.refreshNow()
                Result.success()
            } catch (e: Exception) {
                Timber.e(e, "Failed to refresh Sonarr/Radarr queue status")
                Result.retry()
            }
        }
}
