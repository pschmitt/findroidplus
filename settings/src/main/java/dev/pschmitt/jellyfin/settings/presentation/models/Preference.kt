package dev.pschmitt.jellyfin.settings.presentation.models

import dev.pschmitt.jellyfin.settings.domain.models.Preference as PreferenceBackend
import dev.pschmitt.jellyfin.settings.presentation.enums.DeviceType

interface Preference {
    val nameStringResource: Int
    val descriptionStringRes: Int?
    val iconDrawableId: Int?
    val enabled: Boolean
    val dependencies: List<PreferenceBackend<Boolean>>
    val supportedDeviceTypes: List<DeviceType>
}
