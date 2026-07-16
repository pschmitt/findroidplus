package dev.jdtech.jellyfin.repository

import dev.jdtech.jellyfin.api.pvr.RadarrMovie
import dev.jdtech.jellyfin.api.pvr.RadarrQueueItem
import dev.jdtech.jellyfin.api.pvr.SonarrQueueItem
import dev.jdtech.jellyfin.api.pvr.SonarrSeries
import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.FindroidMovie
import dev.jdtech.jellyfin.models.FindroidShow
import dev.jdtech.jellyfin.models.PvrSource
import dev.jdtech.jellyfin.models.QueueItemStatus
import dev.jdtech.jellyfin.models.QueueStatus
import java.util.UUID

/**
 * Pure functions matching Sonarr/Radarr queue entries to Jellyfin item ids - no suspend, no I/O,
 * so they're directly unit-testable without Room/Hilt/Android in the loop. Any lookup that fails
 * along the way (unknown provider id, orphaned queue reference, episode not yet synced into
 * Jellyfin's library, ...) just skips that queue entry - this must never throw, since a single bad
 * PVR-side reference shouldn't take down the whole match.
 *
 * If two queue entries resolve to the same Jellyfin item (e.g. a retried download that shows up
 * as two queue rows before Sonarr/Radarr cleans up the old one), the later entry in [queue] wins -
 * both loops below iterate in list order and overwrite the map entry.
 */

/** Sonarr's `series.tvdbId`/`movie.tmdbId` default to 0 when the field is absent from the DTO. */
private const val UNSET_PROVIDER_ID = 0

fun matchSonarr(
    series: List<SonarrSeries>,
    queue: List<SonarrQueueItem>,
    jellyfinShows: List<FindroidShow>,
    episodesByShowId: Map<UUID, List<FindroidEpisode>>,
): Map<UUID, QueueStatus> {
    val showByTvdbId: Map<String, FindroidShow> =
        jellyfinShows.mapNotNull { show -> show.tvdbId?.let { it to show } }.toMap()
    val tvdbIdBySeriesId: Map<Int, Int> =
        series.filter { it.tvdbId != UNSET_PROVIDER_ID }.associate { it.id to it.tvdbId }

    val result = mutableMapOf<UUID, QueueStatus>()
    for (item in queue) {
        val tvdbId = tvdbIdBySeriesId[item.seriesId] ?: continue
        val show = showByTvdbId[tvdbId.toString()] ?: continue
        val episodeNumber = item.episode?.episodeNumber?.takeIf { it != UNSET_PROVIDER_ID } ?: continue
        val episodes = episodesByShowId[show.id] ?: continue
        val episode =
            episodes.firstOrNull {
                it.parentIndexNumber == item.seasonNumber && it.indexNumber == episodeNumber
            } ?: continue
        result[episode.id] = item.toQueueStatus()
    }
    return result
}

fun matchRadarr(
    movies: List<RadarrMovie>,
    queue: List<RadarrQueueItem>,
    jellyfinMovies: List<FindroidMovie>,
): Map<UUID, QueueStatus> {
    val movieByTmdbId: Map<String, FindroidMovie> =
        jellyfinMovies.mapNotNull { movie -> movie.tmdbId?.let { it to movie } }.toMap()
    val tmdbIdByMovieId: Map<Int, Int> =
        movies.filter { it.tmdbId != UNSET_PROVIDER_ID }.associate { it.id to it.tmdbId }

    val result = mutableMapOf<UUID, QueueStatus>()
    for (item in queue) {
        val tmdbId = tmdbIdByMovieId[item.movieId] ?: continue
        val movie = movieByTmdbId[tmdbId.toString()] ?: continue
        result[movie.id] = item.toQueueStatus()
    }
    return result
}

private fun SonarrQueueItem.toQueueStatus(): QueueStatus =
    buildQueueStatus(
        source = PvrSource.SONARR,
        status = status,
        trackedDownloadStatus = trackedDownloadStatus,
        trackedDownloadState = trackedDownloadState,
        size = size,
        sizeleft = sizeleft,
        timeleft = timeleft,
        errorMessage = errorMessage,
    )

private fun RadarrQueueItem.toQueueStatus(): QueueStatus =
    buildQueueStatus(
        source = PvrSource.RADARR,
        status = status,
        trackedDownloadStatus = trackedDownloadStatus,
        trackedDownloadState = trackedDownloadState,
        size = size,
        sizeleft = sizeleft,
        timeleft = timeleft,
        errorMessage = errorMessage,
    )

private fun buildQueueStatus(
    source: PvrSource,
    status: String?,
    trackedDownloadStatus: String?,
    trackedDownloadState: String?,
    size: Long,
    sizeleft: Long,
    timeleft: String?,
    errorMessage: String?,
): QueueStatus {
    val etaSeconds = parseTimeleftSeconds(timeleft)
    val percent = if (size > 0) (((size - sizeleft) * 100) / size).toInt().coerceIn(0, 100) else -1
    val speedBytesPerSecond = if (etaSeconds > 0 && sizeleft > 0) sizeleft / etaSeconds else 0L
    return QueueStatus(
        source = source,
        status = mapQueueItemStatus(status, trackedDownloadStatus, trackedDownloadState),
        percent = percent,
        sizeBytes = size,
        remainingBytes = sizeleft,
        speedBytesPerSecond = speedBytesPerSecond,
        etaSeconds = etaSeconds,
        errorMessage = errorMessage,
    )
}

/**
 * Sonarr and Radarr share the same queue item status vocabulary:
 * - `status`: "queued" / "delay" / "paused" / "downloading" / "completed" / "failed" / "warning"
 * - `trackedDownloadStatus`: "ok" / "warning" / "error" - an overlay on top of `status` describing
 *   whether the *tracked* download (post-grab import tracking) is healthy.
 * - `trackedDownloadState`: "downloading" / "importPending" / "importing" / "imported" /
 *   "failedPending" / "failed"
 *
 * `trackedDownloadStatus` signals problems regardless of the coarser `status` field, so it's
 * checked first; otherwise `status`/`trackedDownloadState` together decide between
 * queued/downloading/importing.
 */
internal fun mapQueueItemStatus(
    status: String?,
    trackedDownloadStatus: String?,
    trackedDownloadState: String?,
): QueueItemStatus {
    val normalizedStatus = status?.lowercase()
    val normalizedTrackedStatus = trackedDownloadStatus?.lowercase()
    val normalizedTrackedState = trackedDownloadState?.lowercase()

    if (normalizedTrackedStatus == "error") return QueueItemStatus.FAILED
    if (normalizedTrackedStatus == "warning") return QueueItemStatus.WARNING

    return when {
        normalizedStatus == "failed" || normalizedTrackedState in FAILED_STATES ->
            QueueItemStatus.FAILED
        normalizedStatus == "warning" -> QueueItemStatus.WARNING
        normalizedStatus == "completed" || normalizedTrackedState in IMPORTING_STATES ->
            QueueItemStatus.IMPORTING
        normalizedStatus == "downloading" -> QueueItemStatus.DOWNLOADING
        else -> QueueItemStatus.QUEUED
    }
}

private val FAILED_STATES = setOf("failed", "failedpending")
private val IMPORTING_STATES = setOf("importpending", "importing", "imported")

/** Parses Sonarr/Radarr's `timeleft` duration string ("HH:MM:SS", sometimes "D.HH:MM:SS"). */
internal fun parseTimeleftSeconds(timeleft: String?): Long {
    if (timeleft.isNullOrBlank()) return -1L
    val dayAndRest = timeleft.split(".", limit = 2)
    val (days, clock) = if (dayAndRest.size == 2) dayAndRest[0] to dayAndRest[1] else "0" to dayAndRest[0]
    val parts = clock.split(":").mapNotNull { it.toLongOrNull() }
    if (parts.size != 3) return -1L
    val daysLong = days.toLongOrNull() ?: 0L
    val (hours, minutes, seconds) = parts
    return daysLong * 86_400L + hours * 3_600L + minutes * 60L + seconds
}
