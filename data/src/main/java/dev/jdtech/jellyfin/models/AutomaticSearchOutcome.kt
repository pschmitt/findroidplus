package dev.jdtech.jellyfin.models

/**
 * Result of waiting for a Sonarr/Radarr automatic search (triggered via
 * [dev.jdtech.jellyfin.repository.SonarrSearchRepository.searchEpisode] /
 * [dev.jdtech.jellyfin.repository.RadarrSearchRepository.searchMovie]) to finish, so the app can
 * notify the user instead of leaving them to guess whether/when it completed. [title] is already
 * human-readable ("Show S01E05" / "Movie title") - built by the repository since only it knows the
 * service-specific metadata to look up.
 */
data class AutomaticSearchOutcome(
    val succeeded: Boolean,
    val title: String,
)
