package dev.jdtech.jellyfin.film.presentation.movie

import dev.jdtech.jellyfin.api.pvr.PvrRelease
import java.util.UUID

sealed interface MovieAction {
    data object SearchMovieAutomatic : MovieAction

    data object OpenReleasePicker : MovieAction

    data class GrabRelease(val release: PvrRelease) : MovieAction

    data object DismissReleasePicker : MovieAction

    data class Play(val startFromBeginning: Boolean = false) : MovieAction

    data class PlayTrailer(val trailer: String) : MovieAction

    data object MarkAsPlayed : MovieAction

    data object UnmarkAsPlayed : MovieAction

    data object MarkAsFavorite : MovieAction

    data object UnmarkAsFavorite : MovieAction

    data object OnBackClick : MovieAction

    data object OnHomeClick : MovieAction

    data object OnSettingsClick : MovieAction

    data class NavigateToPerson(val personId: UUID) : MovieAction
}
