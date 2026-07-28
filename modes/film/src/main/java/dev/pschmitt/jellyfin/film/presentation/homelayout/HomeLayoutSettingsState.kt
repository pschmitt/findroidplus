package dev.pschmitt.jellyfin.film.presentation.homelayout

import dev.pschmitt.jellyfin.models.UiText

/**
 * [serviceIcons] are the PVR/Seerr brand logo drawable resource ids this row's content actually
 * depends on - e.g. Sonarr/Radarr for "Pending downloads", Seerr for the Discover rows - shown
 * next to the label so it's clear at a glance which integration a section needs.
 */
data class HomeLayoutRow(val key: String, val label: UiText, val serviceIcons: List<Int> = emptyList())

data class HomeLayoutSettingsState(
    val rows: List<HomeLayoutRow> = emptyList(),
    val hiddenRows: List<HomeLayoutRow> = emptyList(),
    val isLoading: Boolean = false,
)
