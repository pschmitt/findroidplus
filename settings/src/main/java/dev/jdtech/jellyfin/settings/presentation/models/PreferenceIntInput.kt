package dev.jdtech.jellyfin.settings.presentation.models

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import dev.jdtech.jellyfin.settings.domain.models.Preference as PreferenceBackend
import dev.jdtech.jellyfin.settings.presentation.enums.DeviceType

data class PreferenceIntInput(
    @param:StringRes override val nameStringResource: Int,
    @param:StringRes override val descriptionStringRes: Int? = null,
    @param:DrawableRes override val iconDrawableId: Int? = null,
    override val enabled: Boolean = true,
    override val dependencies: List<PreferenceBackend<Boolean>> = emptyList(),
    override val supportedDeviceTypes: List<DeviceType> = listOf(DeviceType.PHONE, DeviceType.TV),
    val onClick: (Preference) -> Unit = {},
    val backendPreference: PreferenceBackend<Int>,
    @param:StringRes val prefixRes: Int? = null,
    @param:StringRes val suffixRes: Int? = null,
    val value: Int = -1,
    // When set, renders as a presets-plus-custom interval picker instead of a bare number field.
    val presetsMinutes: List<Int>? = null,
    val validRange: IntRange = 1..Int.MAX_VALUE,
) : Preference
