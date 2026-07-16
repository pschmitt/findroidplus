package dev.jdtech.jellyfin.presentation.settings.integrations

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

    data class OnPollIntervalChanged(val minutes: Int) : IntegrationsSettingsAction
}
