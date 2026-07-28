package dev.pschmitt.jellyfin.settings.presentation.settings

import androidx.annotation.DrawableRes
import dev.pschmitt.jellyfin.settings.presentation.models.PreferenceGroup

data class SettingsState(
    val isLoading: Boolean = false,
    val preferenceGroups: List<PreferenceGroup> = emptyList(),
    @param:DrawableRes val titleIconDrawableId: Int? = null,
)
