package dev.pschmitt.jellyfin.utils

import dev.pschmitt.jellyfin.core.Constants
import dev.pschmitt.jellyfin.database.ServerDatabaseDao
import dev.pschmitt.jellyfin.models.PendingDownloadRequestDto
import dev.pschmitt.jellyfin.repository.JellyfinRepository
import dev.pschmitt.jellyfin.settings.domain.AppPreferences
import org.jellyfin.sdk.model.api.ItemFields
import timber.log.Timber

/**
 * Resolves one [PendingDownloadRequestDto] against the current Jellyfin library and, if the
 * requested season/episode has appeared, enqueues its download. Mirrors
 * [AutoDownloadRuleEvaluator]'s shape/dedup logic, but a pending request is a one-off "download
 * whichever episodes exist the moment this season/episode shows up" rather than a persistent
 * "keep following new episodes" rule (see [dev.pschmitt.jellyfin.repository.AutoDownloadRuleRepository]
 * for that) - so once resolved, the caller ([dev.pschmitt.jellyfin.work.PendingDownloadWorker])
 * deletes the row regardless of whether anything actually needed downloading.
 */
class PendingDownloadFulfiller {
    /**
     * Returns true when [request]'s target season/episode was found in the library (whether or
     * not a new download was actually needed - it may already be downloaded/queued by other
     * means) and the row should be deleted; false when it still isn't there yet and the request
     * should be left in place for the next cycle. [onFulfilled] is invoked with a display title
     * only when a new download was actually enqueued, so the caller can post a notification.
     */
    suspend fun fulfill(
        request: PendingDownloadRequestDto,
        database: ServerDatabaseDao,
        repository: JellyfinRepository,
        downloader: Downloader,
        appPreferences: AppPreferences,
        onFulfilled: suspend (title: String) -> Unit,
    ): Boolean {
        val season =
            try {
                repository.getSeasons(request.seriesId).firstOrNull {
                    it.indexNumber == request.seasonNumber
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to check pending download request ${request.id}")
                return false
            } ?: return false

        val episodes =
            try {
                repository.getEpisodes(
                    seriesId = request.seriesId,
                    seasonId = season.id,
                    fields = listOf(ItemFields.MEDIA_SOURCES),
                )
            } catch (e: Exception) {
                Timber.e(e, "Failed to fetch episodes for pending download request ${request.id}")
                return false
            }

        val requestedEpisodeNumber = request.episodeNumber
        val targetEpisodes =
            if (requestedEpisodeNumber != null) {
                val episode = episodes.firstOrNull { it.indexNumber == requestedEpisodeNumber } ?: return false
                listOf(episode)
            } else {
                // Whole-season request: only fulfilled once the season actually has episodes to
                // download - a bare season shell with none yet means "not really there", so leave
                // it pending rather than deleting the request for nothing.
                if (episodes.isEmpty()) return false
                episodes
            }

        var queuedAny = false
        // Set once an episode was ready to download but skipped for being over the size cap - in
        // that case the request must stay pending (return false) rather than being deleted as
        // fulfilled, so it's retried once the cap allows it again instead of being silently
        // dropped forever.
        var capReached = false
        val storageIndex = downloader.resolvePreferredStorageIndex()
        val maxSizeEnabled = appPreferences.getValue(appPreferences.maxDownloadSizeEnabled)
        val maxSizeBytes =
            appPreferences.getValue(appPreferences.maxDownloadSizeGb) * Constants.BYTES_PER_GIB
        var totalDownloadedBytes = if (maxSizeEnabled) downloader.getTotalDownloadedBytes() else 0L
        for (episode in targetEpisodes) {
            try {
                // A sources row already exists the moment a download is enqueued (before it
                // finishes), so its mere presence covers already downloaded/queued/running alike.
                if (database.getSources(episode.id).isNotEmpty()) continue

                if (maxSizeEnabled && totalDownloadedBytes >= maxSizeBytes) {
                    capReached = true
                    continue
                }

                val source = episode.sources.firstOrNull() ?: continue
                downloader.downloadItem(episode, source.id, storageIndex = storageIndex)
                totalDownloadedBytes += source.size
                queuedAny = true
            } catch (e: Exception) {
                Timber.e(e, "Failed to queue pending download for episode ${episode.id}")
            }
        }

        if (queuedAny) {
            val title = if (requestedEpisodeNumber != null) targetEpisodes.first().name else season.name
            onFulfilled(title)
        }
        return !capReached
    }
}
