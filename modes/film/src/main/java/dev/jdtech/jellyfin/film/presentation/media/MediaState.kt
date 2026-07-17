package dev.jdtech.jellyfin.film.presentation.media

import dev.jdtech.jellyfin.models.FindroidCollection

data class MediaState(
    val libraries: List<FindroidCollection> = emptyList(),
    val isLoading: Boolean = false,
    val error: Exception? = null,
    // Whether Sonarr or Radarr is enabled *and* has a base URL configured - the Calendar tab has
    // nothing useful to show otherwise, so NavigationRoot uses this to hide it entirely rather
    // than showing an always-empty screen.
    val showCalendarTab: Boolean = false,
)
