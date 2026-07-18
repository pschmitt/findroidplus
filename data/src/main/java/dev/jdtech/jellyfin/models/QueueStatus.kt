package dev.jdtech.jellyfin.models

/**
 * One Sonarr/Radarr queue entry as surfaced by
 * [dev.jdtech.jellyfin.repository.QueueStatusRepository]. [item] is the Jellyfin library item the
 * entry was matched to (see `matchSonarr`/`matchRadarr` in `QueueStatusMatching.kt`) - null when
 * the download couldn't be resolved to anything in the library, e.g. a torrent added manually on
 * the Sonarr/Radarr side for a series/movie Jellyfin hasn't imported yet. Unmatched entries still
 * carry a human-readable [title] built from the PVR side's own metadata, so a queue view can list
 * every download rather than silently dropping the unmatched ones.
 */
data class PvrQueueEntry(
    val item: FindroidItem?,
    val title: String,
    val status: QueueStatus,
    // Provider ids keep progress visible for Seerr-only media before Jellyfin imports the file.
    val tmdbId: Int? = null,
    val sonarrEpisodeId: Int? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val posterUrl: String? = null,
    // The PVR service's own id for this queue row - stable across polls, so snapshots can be
    // diffed to detect a download leaving the queue (= finished importing, in the common case).
    val queueItemId: Int = 0,
)

/**
 * One full poll of both services' queues. [errors] carries per-service fetch failures instead of
 * silently collapsing them into an empty queue - an unreachable Sonarr should read as "Sonarr is
 * unreachable", not "nothing is downloading". [fetchedSources] lists the services that were
 * enabled *and* answered this poll: only their entries' disappearance since the previous snapshot
 * means anything (see `QueueStatusRepositoryImpl.notifyFinishedDownloads`).
 */
data class PvrQueueSnapshot(
    val entries: List<PvrQueueEntry> = emptyList(),
    val errors: List<PvrFetchError> = emptyList(),
    val fetchedSources: Set<PvrSource> = emptySet(),
)

/** A user-presentable per-service fetch failure - [message] already names the service. */
data class PvrFetchError(val source: PvrSource, val message: String)

/**
 * The download-progress payload of a single Sonarr/Radarr queue entry (see [PvrQueueEntry] for
 * the item association).
 */
data class QueueStatus(
    val source: PvrSource,
    val status: QueueItemStatus,
    val percent: Int = -1,
    val sizeBytes: Long = 0L,
    val remainingBytes: Long = 0L,
    val speedBytesPerSecond: Long = 0L,
    val etaSeconds: Long = -1L,
    val errorMessage: String? = null,
    // The underlying download-client transfer's id (distinct from the queue row's own id) - what
    // GET/POST /api/v3/manualimport filters/targets by. Null when the PVR service didn't report
    // one (should not happen in practice, but the field is optional on the wire).
    val downloadId: String? = null,
)

enum class PvrSource { SONARR, RADARR }

enum class QueueItemStatus { QUEUED, DOWNLOADING, IMPORTING, WARNING, FAILED }
