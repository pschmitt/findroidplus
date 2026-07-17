package dev.jdtech.jellyfin.film.presentation.seerr

import dev.jdtech.jellyfin.models.SeerrMediaDetail

data class SeerrMediaState(
    val detail: SeerrMediaDetail? = null,
    val isLoading: Boolean = false,
    // A request/cancel round-trip is in flight - disables the action button meanwhile.
    val isSubmitting: Boolean = false,
    val error: Exception? = null,
)
