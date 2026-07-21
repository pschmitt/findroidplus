package dev.jdtech.jellyfin.models

/**
 * A Sonarr-known season of a show that isn't in the Jellyfin library yet - the show-level
 * equivalent of [UpcomingEpisode], shown as a placeholder card alongside real seasons on the Show
 * screen. Sonarr's v3 API has no "season" resource of its own - this is derived by grouping
 * `GET /api/v3/episode` entries by `seasonNumber` (see
 * [dev.jdtech.jellyfin.repository.matchMissingSeasons]), so [episodeCount] counts every episode
 * Sonarr knows about for that season (aired or not) and [monitored] is true when at least one of
 * them is monitored. [posterUrl] is fetched separately (TMDB via Seerr, see
 * [dev.jdtech.jellyfin.repository.SeerrRepository.getSeasonPosterUrls]) once the season numbers
 * are known - null while that fetch hasn't resolved yet, Seerr isn't configured, or TMDB has no
 * poster for this season.
 */
data class UpcomingSeason(
    val seasonNumber: Int,
    val episodeCount: Int,
    val monitored: Boolean,
    val posterUrl: String? = null,
)
