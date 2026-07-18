package dev.jdtech.jellyfin.models

/** UI-facing models for the Seerr/Seerr integration - see `SeerrRepository`. */

enum class SeerrMediaType {
    MOVIE,
    TV,
}

/** Seerr's media availability lifecycle. */
enum class SeerrMediaStatus {
    /** Not tracked by Seerr at all - requestable. */
    NOT_REQUESTED,
    /** Requested, waiting for approval. */
    PENDING,
    /** Approved and handed to Sonarr/Radarr, not yet downloaded. */
    PROCESSING,
    PARTIALLY_AVAILABLE,
    AVAILABLE;

    companion object {
        /** Seerr's numeric codes: 1=unknown, 2=pending, 3=processing, 4=partial, 5=available. */
        fun fromCode(code: Int?): SeerrMediaStatus =
            when (code) {
                2 -> PENDING
                3 -> PROCESSING
                4 -> PARTIALLY_AVAILABLE
                5 -> AVAILABLE
                else -> NOT_REQUESTED
            }
    }
}

data class SeerrSearchItem(
    val tmdbId: Int,
    val mediaType: SeerrMediaType,
    val title: String,
    val year: Int?,
    val overview: String?,
    val posterUrl: String?,
    val status: SeerrMediaStatus,
)

data class SeerrRequestItem(
    val id: Int,
    val tmdbId: Int,
    val mediaType: SeerrMediaType,
    val title: String,
    val posterUrl: String?,
    val mediaStatus: SeerrMediaStatus,
)

/** Full detail payload backing the dedicated Seerr media view. */
data class SeerrMediaDetail(
    val tmdbId: Int,
    val mediaType: SeerrMediaType,
    val title: String,
    val year: Int?,
    val overview: String?,
    val posterUrl: String?,
    val backdropUrl: String?,
    val genres: List<String>,
    /** Movies only. */
    val runtimeMinutes: Int?,
    /** Series only. */
    val numberOfSeasons: Int?,
    val status: SeerrMediaStatus,
    /**
     * Ids of the media's open (not-declined) requests - non-empty means the item can be
     * "unrequested" by deleting these.
     */
    val cancellableRequestIds: List<Int>,
    /** Present when the view was opened for a TV season or an episode in it. */
    val season: SeerrSeasonDetail? = null,
    /** Present when the view was opened from a specific upcoming TV episode. */
    val episode: SeerrEpisodeDetail? = null,
    /**
     * Series only. Per-season status, as tracked by Seerr - empty when the show has never been
     * requested at all (a normal state, not an error). Seasons absent from this list simply have
     * no status yet (implicitly not-requested).
     */
    val seasons: List<SeerrSeasonInfo> = emptyList(),
)

/** A single season's own request/availability status, independent of the show-level aggregate. */
data class SeerrSeasonInfo(val seasonNumber: Int, val status: SeerrMediaStatus)

data class SeerrSeasonDetail(
    val title: String,
    val seasonNumber: Int,
    val overview: String?,
    val posterUrl: String?,
)

data class SeerrEpisodeDetail(
    val title: String,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val airDate: String?,
    val overview: String?,
    val stillUrl: String?,
)
