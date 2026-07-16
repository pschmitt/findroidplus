package dev.jdtech.jellyfin.film.presentation.season

import dev.jdtech.jellyfin.api.pvr.PvrRelease
import dev.jdtech.jellyfin.core.presentation.downloader.DownloadSelection
import dev.jdtech.jellyfin.models.FindroidItem
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
    ) : SeasonAction

    data class DeleteSeasonDownloads(val alsoRemoveRules: Boolean) : SeasonAction

    data object OnBackClick : SeasonAction

    data object OnHomeClick : SeasonAction

    data object OnSettingsClick : SeasonAction

    data class NavigateToItem(val item: FindroidItem) : SeasonAction

    data class NavigateToSeries(val seriesId: UUID) : SeasonAction

    /** [knownEpisodeId] is Sonarr's numeric episode id when already known (upcoming episode rows),
     * `null` for real episodes - resolved from [SeasonState.seriesTvdbId] instead. */
    data class SearchEpisodeAutomatic(val episodeNumber: Int, val knownEpisodeId: Int?) : SeasonAction

    data class OpenReleasePicker(val episodeNumber: Int, val knownEpisodeId: Int?) : SeasonAction

    data class GrabRelease(val release: PvrRelease) : SeasonAction

    data object DismissReleasePicker : SeasonAction
}
