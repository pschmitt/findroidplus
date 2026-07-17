package dev.jdtech.jellyfin.film.presentation.season

import dev.jdtech.jellyfin.core.presentation.search.ReleasePickerState
import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.FindroidSeason
import dev.jdtech.jellyfin.models.QueueStatus
import dev.jdtech.jellyfin.models.UpcomingEpisode
import dev.jdtech.jellyfin.repository.ExistingAutoDownloadScope
import dev.jdtech.jellyfin.utils.DownloadProgress
import java.util.UUID

data class SeasonState(
    val season: FindroidSeason? = null,
    val episodes: List<FindroidEpisode> = emptyList(),
    // Sonarr-known episodes of this season not yet in the Jellyfin library - always empty unless
    // Sonarr is configured and the show is matched (see SeasonEpisodesRepository). Rendered as
    // greyed-out placeholder rows after the real episodes, see SeasonScreen.
    val upcomingEpisodes: List<UpcomingEpisode> = emptyList(),
    val autoDownloadEnabled: Boolean = false,
    val existingScope: ExistingAutoDownloadScope = ExistingAutoDownloadScope(),
    val hasDownloads: Boolean = false,
    val downloadsSizeBytes: Long = 0L,
    val downloadProgress: Map<UUID, DownloadProgress> = emptyMap(),
    val queueStatus: Map<UUID, QueueStatus> = emptyMap(),
    // The show's tvdb id, fetched once per loadSeason() - reused both for upcomingEpisodes and to
    // resolve a Sonarr episode id on demand when the user triggers a search on a real episode row.
    val seriesTvdbId: String? = null,
    val seriesTmdbId: Int? = null,
    // Gates the per-episode search buttons - no point offering a Sonarr search that can only
    // fail with a toast when Sonarr isn't (fully) configured.
    val sonarrConfigured: Boolean = false,
    val releasePicker: ReleasePickerState? = null,
    val error: Exception? = null,
)
