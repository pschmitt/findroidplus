package dev.jdtech.jellyfin.film.presentation.episode

import dev.jdtech.jellyfin.api.pvr.PvrRelease
import dev.jdtech.jellyfin.core.presentation.downloader.DownloadSelection
import java.util.UUID

sealed interface EpisodeAction {
    data class Play(val startFromBeginning: Boolean = false) : EpisodeAction

    data object MarkAsPlayed : EpisodeAction

    data object UnmarkAsPlayed : EpisodeAction

    data object MarkAsFavorite : EpisodeAction

    data object UnmarkAsFavorite : EpisodeAction

    data class DownloadWithScope(
        val selection: DownloadSelection,
        val alsoFollowNew: Boolean,
        val onlyUnwatched: Boolean,
    ) : EpisodeAction

    data object OnBackClick : EpisodeAction

    data object OnHomeClick : EpisodeAction

    data object OnSettingsClick : EpisodeAction

    data class NavigateToPerson(val personId: UUID) : EpisodeAction

    data class NavigateToSeason(val seasonId: UUID) : EpisodeAction

    data class NavigateToShow(val showId: UUID) : EpisodeAction

    data object SearchEpisodeAutomatic : EpisodeAction

    data object OpenReleasePicker : EpisodeAction

    data class GrabRelease(val release: PvrRelease) : EpisodeAction

    data object DismissReleasePicker : EpisodeAction

    data object ToggleExcludeFromAutoDelete : EpisodeAction
}
