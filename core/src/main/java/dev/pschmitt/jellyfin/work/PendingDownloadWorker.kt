package dev.pschmitt.jellyfin.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.pschmitt.jellyfin.database.ServerDatabaseDao
import dev.pschmitt.jellyfin.repository.JellyfinRepository
import dev.pschmitt.jellyfin.repository.PendingDownloadRequestRepository
import dev.pschmitt.jellyfin.settings.domain.AppPreferences
import dev.pschmitt.jellyfin.utils.Downloader
import dev.pschmitt.jellyfin.utils.PendingDownloadFulfiller
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Periodically checks every pending "download when available" request (see
 * `dev.pschmitt.jellyfin.models.PendingDownloadRequestDto`) against the current Jellyfin library
 * and fulfills any whose season/episode has appeared - see [PendingDownloadFulfiller]. Scoped to
 * the currently active server/user only, same rationale as [AutoDownloadWorker]: [Downloader] and
 * [JellyfinRepository] are both Hilt singletons scoped to the "current" server, so evaluating any
 * other server here would mis-tag persisted rows. Requests for inactive servers stay correctly
 * persisted and get evaluated whenever the user switches back to that server.
 */
@HiltWorker
class PendingDownloadWorker
@AssistedInject
constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val database: ServerDatabaseDao,
    private val jellyfinRepository: JellyfinRepository,
    private val downloader: Downloader,
    private val appPreferences: AppPreferences,
    private val requestRepository: PendingDownloadRequestRepository,
    private val notifier: PendingDownloadFulfilledNotifier,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return withContext(Dispatchers.IO) {
            val serverId =
                appPreferences.getValue(appPreferences.currentServer)
                    ?: return@withContext Result.success()
            val userId = jellyfinRepository.getUserId()

            val fulfiller = PendingDownloadFulfiller()
            for (request in requestRepository.getAll(serverId, userId)) {
                val fulfilled =
                    fulfiller.fulfill(
                        request,
                        database,
                        jellyfinRepository,
                        downloader,
                        appPreferences,
                    ) { title ->
                        notifier.notifyFulfilled(title)
                    }
                if (fulfilled) {
                    requestRepository.deleteById(request.id)
                }
            }

            Result.success()
        }
    }
}
