package dev.jdtech.jellyfin.settings.presentation.settings

import androidx.annotation.DrawableRes
import dev.jdtech.jellyfin.settings.presentation.models.PreferenceGroup

data class SettingsState(
    val isLoading: Boolean = false,
    val preferenceGroups: List<PreferenceGroup> = emptyList(),
    @param:DrawableRes val titleIconDrawableId: Int? = null,
)
