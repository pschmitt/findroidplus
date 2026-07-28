package dev.pschmitt.jellyfin.film.presentation.season

import dev.pschmitt.jellyfin.api.pvr.PvrRelease
import dev.pschmitt.jellyfin.core.presentation.downloader.DownloadSelection
import dev.pschmitt.jellyfin.models.FindroidItem
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

sealed interface SeasonAction {
    data class Play(val startFromBeginning: Boolean = false) : SeasonAction

    data object MarkAsPlayed : SeasonAction

    data object UnmarkAsPlayed : SeasonAction

    data object MarkAsFavorite : SeasonAction

    data object UnmarkAsFavorite : SeasonAction

    data class DownloadWithScope(
        val selection: DownloadSelection,
        val alsoFollowNew: Boolean,
        val onlyUnwatched: Boolean,
        // null = this device (applies locally as today); non-null = push to that device instead.
        val targetDeviceId: String? = null,
    ) : SeasonAction

    data class DeleteSeasonDownloads(val alsoRemoveRules: Boolean) : SeasonAction

    data object OnBackClick : SeasonAction

    data object OnHomeClick : SeasonAction

    data object OnSettingsClick : SeasonAction

    data class NavigateToItem(val item: FindroidItem) : SeasonAction

    data class NavigateToSeries(val seriesId: UUID) : SeasonAction

    data class NavigateToSeerr(
        val tmdbId: Int,
        val seasonNumber: Int,
        val episodeNumber: Int,
        val sonarrEpisodeId: Int,
        // Sonarr-derived, already timezone-localized air date/time (from UpcomingEpisode), so the
        // Seerr detail screen shows the same value this row just did instead of re-deriving one
        // from TMDB's plain, unlocalized air_date - see SeerrMediaScreen's seerrMetaLine.
        val airDate: LocalDate? = null,
        val airTime: LocalTime? = null,
    ) : SeasonAction

    /** [knownEpisodeId] is Sonarr's numeric episode id when already known (upcoming episode rows),
     * `null` for real episodes - resolved from [SeasonState.seriesTvdbId] instead. */
    data class SearchEpisodeAutomatic(val episodeNumber: Int, val knownEpisodeId: Int?) : SeasonAction

    data class OpenReleasePicker(val episodeNumber: Int, val knownEpisodeId: Int?) : SeasonAction

    data class GrabRelease(val release: PvrRelease) : SeasonAction

    data object DismissReleasePicker : SeasonAction

    /** Toggles a "download this episode once it's available" request for an upcoming-episode
     * placeholder row - queues it if not already queued, cancels it otherwise. [sonarrEpisodeId]
     * is stashed on the request row for convenience, same as [UpcomingEpisode.episodeId]. */
    data class ToggleEpisodeQueued(val episodeNumber: Int, val sonarrEpisodeId: Int) : SeasonAction
}
