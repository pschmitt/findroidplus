package dev.jdtech.jellyfin.presentation.settings.integrations

import dev.jdtech.jellyfin.api.pvr.PvrService

sealed interface IntegrationsSettingsAction {
    data object OnBackClick : IntegrationsSettingsAction

    data class OnSonarrEnabledChanged(val enabled: Boolean) : IntegrationsSettingsAction

    data class OnSonarrBaseUrlChanged(val baseUrl: String) : IntegrationsSettingsAction

    data class OnSonarrApiKeyChanged(val apiKey: String) : IntegrationsSettingsAction

    data object OnTestSonarrConnection : IntegrationsSettingsAction

    data class OnRadarrEnabledChanged(val enabled: Boolean) : IntegrationsSettingsAction

    data class OnRadarrBaseUrlChanged(val baseUrl: String) : IntegrationsSettingsAction

    data class OnRadarrApiKeyChanged(val apiKey: String) : IntegrationsSettingsAction

    data object OnTestRadarrConnection : IntegrationsSettingsAction

    data class OnSeerrEnabledChanged(val enabled: Boolean) : IntegrationsSettingsAction

    data class OnSeerrBaseUrlChanged(val baseUrl: String) : IntegrationsSettingsAction

    data class OnSeerrApiKeyChanged(val apiKey: String) : IntegrationsSettingsAction

    data class OnAdvancedSettingsChanged(
        val service: PvrService,
        val headers: String,
        val basicAuthUsername: String,
        val basicAuthPassword: String,
    ) : IntegrationsSettingsAction

    data object OnTestSeerrConnection : IntegrationsSettingsAction

    data class OnPollIntervalChanged(val minutes: Int) : IntegrationsSettingsAction

    data class OnReleaseCacheChanged(val minutes: Int) : IntegrationsSettingsAction
}
