package dev.pschmitt.jellyfin.repository

import dev.pschmitt.jellyfin.models.PendingDownloadRequestDto
import java.util.UUID

/**
 * Persists "download this once it's available" requests for Sonarr-known seasons/episodes that
 * aren't in the Jellyfin library yet (see [PendingDownloadRequestDto]). [episodeNumber] is null for
 * a whole-season request throughout this API, non-null for a single episode - callers pass
 * whichever scope the user tapped on [dev.pschmitt.jellyfin.utils.PendingDownloadFulfiller] handles
 * both the same way.
 */
interface PendingDownloadRequestRepository {
    /** No-op (returns the existing row) if this exact season/episode is already queued. */
    suspend fun queue(
        serverId: String,
        userId: UUID,
        seriesId: UUID,
        seasonNumber: Int,
        episodeNumber: Int?,
        sonarrEpisodeId: Int?,
    ): PendingDownloadRequestDto

    suspend fun cancel(
        serverId: String,
        userId: UUID,
        seriesId: UUID,
        seasonNumber: Int,
        episodeNumber: Int?,
    )

    suspend fun isQueued(
        serverId: String,
        userId: UUID,
        seriesId: UUID,
        seasonNumber: Int,
        episodeNumber: Int?,
    ): Boolean

    /**
     * Every pending request for one show - used by the Show/Season screens to derive which
     * season/episode rows should render as "queued".
     */
    suspend fun getQueuedForSeries(
        serverId: String,
        userId: UUID,
        seriesId: UUID,
    ): List<PendingDownloadRequestDto>

    /**
     * Every pending request for the current server/user - used by
     * [dev.pschmitt.jellyfin.work.PendingDownloadWorker] to evaluate all of them each cycle.
     */
    suspend fun getAll(serverId: String, userId: UUID): List<PendingDownloadRequestDto>

    suspend fun deleteById(id: Long)
}
