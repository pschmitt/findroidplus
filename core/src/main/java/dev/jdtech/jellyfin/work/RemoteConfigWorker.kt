package dev.jdtech.jellyfin.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.jdtech.jellyfin.repository.RemoteConfigRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Periodic poll for cross-device auto-download rule pushes (FINDROID-44), mirroring
 * [QueueStatusWorker]'s "backstop poll" shape.
 */
@HiltWorker
class RemoteConfigWorker
@AssistedInject
constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val remoteConfigRepository: RemoteConfigRepository,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result =
        withContext(Dispatchers.IO) {
            try {
                remoteConfigRepository.syncNow()
                Result.success()
            } catch (e: Exception) {
                Timber.e(e, "Failed to sync remote auto-download commands")
                Result.retry()
            }
        }
}
