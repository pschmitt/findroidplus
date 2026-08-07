package dev.pschmitt.jellyfin.presentation.profiles

import dev.pschmitt.jellyfin.api.pvr.PvrService

/**
 * TV mirror of app/phone's
 * `dev.pschmitt.jellyfin.presentation.settings.profiles.ProfileDetailAction`
 * - see [ProfileDetailState] for why this is duplicated rather than imported.
 */
sealed interface ProfileDetailAction {
    data object OnBackClick : ProfileDetailAction

    data class OnRenameConfirmed(val name: String) : ProfileDetailAction

    data object OnSetAsMainClick : ProfileDetailAction

    data object OnDeleteClick : ProfileDetailAction

    data class OnToggleInherit(val service: PvrService, val inherit: Boolean) : ProfileDetailAction

    data class OnEnabledChanged(val service: PvrService, val enabled: Boolean) : ProfileDetailAction

    data class OnBaseUrlChanged(val service: PvrService, val baseUrl: String) : ProfileDetailAction

    data class OnApiKeyChanged(val service: PvrService, val apiKey: String) : ProfileDetailAction

    data class OnAdvancedSettingsChanged(
        val service: PvrService,
        val headers: String,
        val basicAuthUsername: String,
        val basicAuthPassword: String,
    ) : ProfileDetailAction

    data class OnTestConnectionClick(val service: PvrService) : ProfileDetailAction
}
