package dev.jdtech.jellyfin.api.pvr

import kotlinx.serialization.Serializable

/**
 * Response DTOs for the Sonarr/Radarr v3 REST APIs. Only the fields Findroid actually needs are
 * modeled - both APIs return many more fields per record, so every DTO here relies on the caller
 * decoding with `ignoreUnknownKeys = true` (see [PvrJson]).
 */

// region Sonarr - GET /api/v3/series

@Serializable
data class SonarrSeries(
    val id: Int,
    val tvdbId: Int = 0,
    val tmdbId: Int = 0,
    val title: String = "",
    val images: List<PvrImage> = emptyList(),
)

// endregion

// region Radarr - GET /api/v3/movie

@Serializable
data class RadarrMovie(
    val id: Int,
    val tmdbId: Int = 0,
    val title: String = "",
    val images: List<PvrImage> = emptyList(),
)

// endregion

// region Sonarr - GET /api/v3/queue
// Sonarr's queue entries are per-episode: seriesId + episodeId + seasonNumber identify what's
// being grabbed. The episode's number within its season is only available via the nested
// `episode` object, which Sonarr only embeds when the request passes `includeEpisode=true` (see
// SonarrApi.getQueue()) - needed to resolve the specific Jellyfin episode by season/episode
// number, since `episodeId` is Sonarr's own internal id and has no meaning to Jellyfin.

@Serializable data class SonarrEpisode(val episodeNumber: Int = 0)

@Serializable
data class SonarrQueueItem(
    val id: Int,
    val seriesId: Int = 0,
    val episodeId: Int = 0,
    val seasonNumber: Int = 0,
    val episode: SonarrEpisode? = null,
    val title: String? = null,
    val status: String? = null,
    val trackedDownloadStatus: String? = null,
    val trackedDownloadState: String? = null,
    val size: Long = 0L,
    val sizeleft: Long = 0L,
    val timeleft: String? = null,
    val estimatedCompletionTime: String? = null,
    val errorMessage: String? = null,
)

@Serializable
data class SonarrQueueResponse(
    val page: Int = 1,
    val pageSize: Int = 0,
    val totalRecords: Int = 0,
    val records: List<SonarrQueueItem> = emptyList(),
)

// endregion

// region Radarr - GET /api/v3/queue
// Radarr's queue entries are per-movie: movieId identifies what's being grabbed (no
// season/episode concept).

@Serializable
data class RadarrQueueItem(
    val id: Int,
    val movieId: Int = 0,
    val title: String? = null,
    val status: String? = null,
    val trackedDownloadStatus: String? = null,
    val trackedDownloadState: String? = null,
    val size: Long = 0L,
    val sizeleft: Long = 0L,
    val timeleft: String? = null,
    val estimatedCompletionTime: String? = null,
    val errorMessage: String? = null,
)

@Serializable
data class RadarrQueueResponse(
    val page: Int = 1,
    val pageSize: Int = 0,
    val totalRecords: Int = 0,
    val records: List<RadarrQueueItem> = emptyList(),
)

// endregion

// region Sonarr - GET /api/v3/calendar
// Unlike /queue, /calendar accepts includeSeries=true, which embeds the series object (with
// tvdbId) directly on each entry - no separate getSeries() call/join needed to resolve tvdbId.
// The endpoint returns a flat JSON array, not a paginated {records: [...]} wrapper.

@Serializable
data class PvrImage(
    val coverType: String? = null,
    val remoteUrl: String? = null,
    val url: String? = null,
)

@Serializable
data class SonarrCalendarSeries(
    val tvdbId: Int = 0,
    // Sonarr v4 series resources also carry the TMDB id - used to open the Seerr detail view
    // for entries that aren't in the Jellyfin library yet.
    val tmdbId: Int = 0,
    val title: String = "",
    val images: List<PvrImage> = emptyList(),
)

@Serializable
data class SonarrCalendarEntry(
    val id: Int,
    val seriesId: Int = 0,
    val seasonNumber: Int = 0,
    val episodeNumber: Int = 0,
    val title: String? = null,
    val airDateUtc: String? = null,
    val hasFile: Boolean = false,
    val monitored: Boolean = false,
    val series: SonarrCalendarSeries? = null,
)

// endregion

// region Sonarr - GET /api/v3/episode?seriesId=X
// Unlike /calendar (a global date-range view across all shows), this returns every episode of one
// specific series - what SeasonUpcomingEpisodesRepository needs to find episodes Sonarr knows
// about that aren't in the Jellyfin library yet, regardless of whether they've aired. A flat JSON
// array, same as /calendar and /series.

@Serializable
data class SonarrEpisodeDto(
    val id: Int,
    val seasonNumber: Int = 0,
    val episodeNumber: Int = 0,
    val title: String? = null,
    val airDateUtc: String? = null,
    val hasFile: Boolean = false,
    val monitored: Boolean = false,
)

// endregion

// region Sonarr/Radarr - POST /api/v3/command, GET /api/v3/command/{id}
// POST triggers an automatic search: the PVR service picks and grabs the best matching release
// itself, with no release list surfaced to the caller (compare GET/POST /api/v3/release below,
// which is the interactive/manual counterpart). The POST is answered as soon as the command is
// *queued*, not once it finishes - GET /api/v3/command/{id} is polled afterwards to find out
// when/how it ended, so the app can notify the user once the search actually completes. The
// request payloads differ per service ("EpisodeSearch" + episodeIds vs "MoviesSearch" + movieIds),
// the response shape is identical.

@Serializable data class SonarrCommandRequest(val name: String, val episodeIds: List<Int>)

@Serializable data class SonarrSeriesCommandRequest(val name: String, val seriesId: Int)

@Serializable data class RadarrCommandRequest(val name: String, val movieIds: List<Int>)

@Serializable data class PvrCommandResponse(val id: Int, val status: String? = null)

// endregion

// region Sonarr - GET /api/v3/episode/{id}
// Single-episode lookup, used only to build a human-readable notification once an automatic search
// (triggered above) finishes - the command response/status carries no episode-identifying text.

@Serializable data class SonarrEpisodeSeriesRef(val title: String? = null)

@Serializable
data class SonarrEpisodeDetail(
    val seasonNumber: Int = 0,
    val episodeNumber: Int = 0,
    val title: String? = null,
    val series: SonarrEpisodeSeriesRef? = null,
)

// endregion

// region Sonarr/Radarr - GET /api/v3/release?episodeId=X (Sonarr) / ?movieId=X (Radarr),
// POST /api/v3/release
// GET lists candidate releases (interactive/manual search) without grabbing anything; POSTing a
// release's guid+indexerId back grabs that specific one. Sonarr and Radarr use the same release
// resource shape, so one set of DTOs serves both. Only the fields Findroid's release picker needs
// are modeled - the release resource has many more.

@Serializable data class PvrGrabReleaseRequest(val guid: String, val indexerId: Int)

@Serializable data class PvrQualityName(val name: String? = null)

@Serializable data class PvrReleaseQuality(val quality: PvrQualityName? = null)

@Serializable
data class PvrRelease(
    val guid: String,
    val indexerId: Int = 0,
    val indexer: String? = null,
    val title: String? = null,
    val size: Long = 0L,
    val seeders: Int? = null,
    val ageHours: Double? = null,
    val quality: PvrReleaseQuality? = null,
    val rejected: Boolean = false,
    val rejections: List<String> = emptyList(),
)

// endregion

// region Sonarr/Radarr - GET /api/v3/rootfolder
// One entry per *configured* TV-shows/movies storage location - unlike /diskspace (which also
// reports every other mount point the service's host can see, not just the ones actually used
// for the library), this only lists folders the user explicitly set up as a root folder, complete
// with free/total space already embedded. Both APIs return the same shape; a flat JSON array.

@Serializable
data class PvrRootFolderDto(
    val path: String = "",
    val freeSpace: Long = 0L,
    val totalSpace: Long = 0L,
)

// endregion

// region Radarr - GET /api/v3/calendar
// Radarr's calendar entries are full movie objects (same shape as /api/v3/movie), so tmdbId is
// already present per entry - no separate getMovie() call/join needed. Also a flat JSON array,
// same as Sonarr's calendar endpoint.

@Serializable
data class RadarrCalendarEntry(
    val id: Int,
    val tmdbId: Int = 0,
    val title: String = "",
    val hasFile: Boolean = false,
    val monitored: Boolean = false,
    val images: List<PvrImage> = emptyList(),
    val inCinemas: String? = null,
    val digitalRelease: String? = null,
    val physicalRelease: String? = null,
)

// endregion
