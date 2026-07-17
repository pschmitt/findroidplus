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
)
