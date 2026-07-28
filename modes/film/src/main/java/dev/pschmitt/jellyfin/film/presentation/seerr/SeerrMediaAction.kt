package dev.pschmitt.jellyfin.film.presentation.seerr

import dev.pschmitt.jellyfin.api.pvr.PvrRelease

sealed interface SeerrMediaAction {
    data object OnRequest : SeerrMediaAction

    /** Cancels all of the media's open requests - the "unrequest" action. */
    data object OnCancelRequest : SeerrMediaAction

    data object OnAutomaticSearchInPvr : SeerrMediaAction

    data object OnOpenReleasePicker : SeerrMediaAction

    data class GrabRelease(val release: PvrRelease) : SeerrMediaAction

    data object DismissReleasePicker : SeerrMediaAction

    data object OnRetryClick : SeerrMediaAction

    data object OnBackClick : SeerrMediaAction
}
