package dev.jdtech.jellyfin.api.pvr

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// DTOs for the Seerr/Seerr API (/api/v1) - the media request manager sitting in front of
// Sonarr/Radarr. Only the fields Findroid's Discover screen needs are modeled; `ignoreUnknownKeys`
// drops the (large) rest. Search results are TMDB resources, so `id` is a TMDB id everywhere.

// region GET /api/v1/search?query=X&page=N
// Mixed movie/tv/person results; `mediaType` discriminates. Movies carry `title`/`releaseDate`,
// series carry `name`/`firstAirDate`. `mediaInfo` is only present when Seerr already tracks
// the item (requested, processing, or available) - see SeerrMediaInfo.

@Serializable
data class SeerrSearchResponse(
    val page: Int = 1,
    val totalPages: Int = 1,
    val totalResults: Int = 0,
    val results: List<SeerrSearchResult> = emptyList(),
)

@Serializable
data class SeerrSearchResult(
    val id: Int,
    val mediaType: String = "",
    val title: String? = null,
    val name: String? = null,
    val releaseDate: String? = null,
    val firstAirDate: String? = null,
    val posterPath: String? = null,
    val overview: String? = null,
    val mediaInfo: SeerrMediaInfo? = null,
)

/**
 * Seerr's media availability: 1=UNKNOWN, 2=PENDING, 3=PROCESSING, 4=PARTIALLY_AVAILABLE,
 * 5=AVAILABLE (see `SeerrMediaStatus.fromCode`). The detail endpoints additionally embed the
 * open requests for the media - needed to cancel ("unrequest") from the detail view, since
 * DELETE /request/{id} wants request ids, not TMDB ids. `seasons` is TV-only: Jellyseerr tracks
 * each season's own availability separately from the show-level aggregate `status`.
 */
@Serializable
data class SeerrMediaInfo(
    val status: Int = 0,
    val requests: List<SeerrMediaRequestRef> = emptyList(),
    val seasons: List<SeerrSeasonMediaInfo> = emptyList(),
)

/** `status` is the request workflow state: 1=PENDING approval, 2=APPROVED, 3=DECLINED. */
@Serializable data class SeerrMediaRequestRef(val id: Int, val status: Int = 0)

/** Per-season entry of `SeerrMediaInfo.seasons` - `status` uses the same codes as media-level. */
@Serializable data class SeerrSeasonMediaInfo(val seasonNumber: Int = 0, val status: Int = 0)

// endregion

// region POST /api/v1/request
// `seasons` is polymorphic on the Seerr side (a number array or the string "all") and only
// meaningful for tv requests - hence the raw JsonElement.

@Serializable
data class SeerrCreateRequestBody(
    val mediaType: String,
    val mediaId: Int,
    val seasons: JsonElement? = null,
)

// endregion

// region GET /api/v1/request?take=N&sort=added
// The embedded `media` only carries provider ids and status - the human-readable title needs a
// separate detail lookup (GET /api/v1/movie/{tmdbId} / /api/v1/tv/{tmdbId}).

@Serializable
data class SeerrRequestsResponse(
    val pageInfo: SeerrPageInfo = SeerrPageInfo(),
    val results: List<SeerrRequestDto> = emptyList(),
)

@Serializable data class SeerrPageInfo(val results: Int = 0)

/** `status` is the request workflow state: 1=PENDING approval, 2=APPROVED, 3=DECLINED. */
@Serializable
data class SeerrRequestDto(
    val id: Int,
    val status: Int = 0,
    val type: String = "",
    val createdAt: String? = null,
    val media: SeerrRequestMedia = SeerrRequestMedia(),
)

@Serializable
data class SeerrRequestMedia(
    val tmdbId: Int = 0,
    val mediaType: String = "",
    val status: Int = 0,
)

// endregion

// region GET /api/v1/movie/{tmdbId}, GET /api/v1/tv/{tmdbId}
// Rich TMDB detail payloads, used both to resolve titles/posters for the requests list and to
// back the dedicated Seerr media detail view.

/**
 * One TMDB-sourced video (trailer/teaser/clip/featurette/...) - `site` is almost always
 * "YouTube", and `url` is already a full playable link (`https://www.youtube.com/watch?v={key}`),
 * not just the bare video id.
 */
@Serializable
data class SeerrRelatedVideo(
    val url: String? = null,
    val site: String? = null,
    val type: String? = null,
)

@Serializable
data class SeerrMovieDetails(
    val id: Int,
    val title: String = "",
    val posterPath: String? = null,
    val backdropPath: String? = null,
    val overview: String? = null,
    val releaseDate: String? = null,
    val runtime: Int? = null,
    val genres: List<SeerrGenre> = emptyList(),
    val mediaInfo: SeerrMediaInfo? = null,
    val relatedVideos: List<SeerrRelatedVideo> = emptyList(),
)

@Serializable
data class SeerrTvDetails(
    val id: Int,
    val name: String = "",
    val posterPath: String? = null,
    val backdropPath: String? = null,
    val overview: String? = null,
    val firstAirDate: String? = null,
    val numberOfSeasons: Int? = null,
    val genres: List<SeerrGenre> = emptyList(),
    val mediaInfo: SeerrMediaInfo? = null,
    val relatedVideos: List<SeerrRelatedVideo> = emptyList(),
)

@Serializable
data class SeerrTvSeasonDetails(
    val id: Int,
    val name: String = "",
    val seasonNumber: Int = 0,
    val overview: String? = null,
    val posterPath: String? = null,
    val episodes: List<SeerrTvEpisode> = emptyList(),
)

@Serializable
data class SeerrTvEpisode(
    val id: Int,
    val name: String = "",
    val seasonNumber: Int = 0,
    val episodeNumber: Int = 0,
    val airDate: String? = null,
    val overview: String? = null,
    val stillPath: String? = null,
)

@Serializable data class SeerrGenre(val id: Int = 0, val name: String = "")

// endregion

// region GET /api/v1/auth/me
// Used as the "Test connection" probe - answers 200 with the key's user only when the base URL
// points at a Seerr instance and the API key is valid.

@Serializable data class SeerrUser(val id: Int, val displayName: String? = null)

// endregion
