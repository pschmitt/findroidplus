package dev.jdtech.jellyfin.film.presentation.seerr

import dev.jdtech.jellyfin.models.SeerrMediaDetail
import dev.jdtech.jellyfin.models.QueueStatus
import dev.jdtech.jellyfin.core.presentation.search.ReleasePickerState
import java.util.UUID

data class SeerrMediaState(
    val detail: SeerrMediaDetail? = null,
    val isLoading: Boolean = false,
    // A request/cancel round-trip is in flight - disables the action button meanwhile.
    val isSubmitting: Boolean = false,
    val pvrSearchConfigured: Boolean = false,
    val manualPvrSearchAvailable: Boolean = false,
    val queueStatus: QueueStatus? = null,
    val jellyfinShowId: UUID? = null,
    val jellyfinSeasonId: UUID? = null,
    // Set only when detail.episode is non-null and this exact episode (not just its season) is
    // matched in the library - the one genuine binary "is THIS episode available" signal, since
    // Seerr only tracks request/availability status at the season/show level.
    val jellyfinEpisodeId: UUID? = null,
    val releasePicker: ReleasePickerState? = null,
    val error: Exception? = null,
)
