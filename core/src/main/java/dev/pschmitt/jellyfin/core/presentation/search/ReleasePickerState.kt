package dev.pschmitt.jellyfin.core.presentation.search

import dev.pschmitt.jellyfin.api.pvr.PvrRelease

/** Null in the owning screen's state when the release picker sheet isn't open. */
data class ReleasePickerState(
    val isLoading: Boolean = true,
    val releases: List<PvrRelease> = emptyList(),
)
