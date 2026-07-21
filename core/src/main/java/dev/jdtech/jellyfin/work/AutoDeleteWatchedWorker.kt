package dev.jdtech.jellyfin.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.jdtech.jellyfin.database.ServerDatabaseDao
import dev.jdtech.jellyfin.models.FindroidSourceType
import dev.jdtech.jellyfin.models.isMarkedForAutoDeletion
import dev.jdtech.jellyfin.models.toFindroidEpisodes
import dev.jdtech.jellyfin.repository.JellyfinRepository
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import dev.jdtech.jellyfin.utils.Downloader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Deletes downloaded episodes that have been watched for longer than the configured threshold.
 * Scoped to the currently active server/user only, same rationale as [AutoDownloadWorker].
 *
 * Eligibility is computed entirely from the local DB (see [isMarkedForAutoDeletion]) rather than
 * re-fetching each candidate from the server - this used to call [JellyfinRepository.getEpisode]
 * once per downloaded episode, which was both a needless network round trip per item and a
 * different eligibility check than the one the "marked for deletion" UI badge uses (the badge
 * reads local DB state), so the two could disagree. [FindroidUserDataDto.lastPlayedDate][dev.jdtech.jellyfin.models.FindroidUserDataDto]
 * is kept in sync locally by every `setPlayed` call site (see `JellyfinRepositoryImpl`/
 * `JellyfinRepositoryOfflineImpl`), so the local copy is authoritative.
 */
@HiltWorker
class AutoDeleteWatchedWorker
@AssistedInject
constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val database: ServerDatabaseDao,
    private val jellyfinRepository: JellyfinRepository,
    private val downloader: Downloader,
    private val appPreferences: AppPreferences,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result =
        withContext(Dispatchers.IO) {
            if (!appPreferences.getValue(appPreferences.autoDeleteWatched)) {
                return@withContext Result.success()
            }
            val serverId =
                appPreferences.getValue(appPreferences.currentServer)
                    ?: return@withContext Result.success()
            val hours = appPreferences.getValue(appPreferences.autoDeleteWatchedHours)
            val userId = jellyfinRepository.getUserId()

            val episodes = database.getEpisodesByServerId(serverId).toFindroidEpisodes(database, userId)

            for (episode in episodes) {
                if (!episode.isMarkedForAutoDeletion(hours)) continue
                try {
                    val source =
                        episode.sources.firstOrNull { it.type == FindroidSourceType.LOCAL }
                            ?: continue
                    downloader.deleteItem(episode, source)
                } catch (e: Exception) {
                    Timber.e(e, "Failed to auto-delete watched episode ${episode.id}")
                }
            }

            Result.success()
        }
}
