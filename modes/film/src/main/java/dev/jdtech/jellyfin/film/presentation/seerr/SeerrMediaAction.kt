package dev.jdtech.jellyfin.film.presentation.seerr

sealed interface SeerrMediaAction {
    data object OnRequest : SeerrMediaAction

    /** Cancels all of the media's open requests - the "unrequest" action. */
    data object OnCancelRequest : SeerrMediaAction

    data object OnRetryClick : SeerrMediaAction

    data object OnBackClick : SeerrMediaAction
}
