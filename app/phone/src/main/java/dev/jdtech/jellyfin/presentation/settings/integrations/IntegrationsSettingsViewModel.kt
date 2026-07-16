package dev.jdtech.jellyfin.presentation.settings.integrations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jdtech.jellyfin.api.pvr.PvrCredentialKeys
import dev.jdtech.jellyfin.api.pvr.RadarrApi
import dev.jdtech.jellyfin.api.pvr.SonarrApi
import dev.jdtech.jellyfin.security.SecureCredentialStore
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class IntegrationsSettingsViewModel
@Inject
constructor(
    private val appPreferences: AppPreferences,
    private val secureCredentialStore: SecureCredentialStore,
) : ViewModel() {
    private val _state = MutableStateFlow(IntegrationsSettingsState())
    val state = _state.asStateFlow()

    fun load() {
        _state.value =
            IntegrationsSettingsState(
                sonarrEnabled = appPreferences.getValue(appPreferences.sonarrEnabled),
                sonarrBaseUrl = appPreferences.getValue(appPreferences.sonarrBaseUrl).orEmpty(),
                sonarrApiKey = secureCredentialStore.getString(PvrCredentialKeys.SONARR_API_KEY).orEmpty(),
                radarrEnabled = appPreferences.getValue(appPreferences.radarrEnabled),
                radarrBaseUrl = appPreferences.getValue(appPreferences.radarrBaseUrl).orEmpty(),
                radarrApiKey = secureCredentialStore.getString(PvrCredentialKeys.RADARR_API_KEY).orEmpty(),
                pvrPollIntervalMinutes =
                    appPreferences.getValue(appPreferences.pvrPollIntervalMinutes),
            )
    }

    fun onAction(action: IntegrationsSettingsAction) {
        when (action) {
            is IntegrationsSettingsAction.OnBackClick -> Unit
            is IntegrationsSettingsAction.OnSonarrEnabledChanged -> {
                appPreferences.setValue(appPreferences.sonarrEnabled, action.enabled)
                _state.value = _state.value.copy(sonarrEnabled = action.enabled)
            }
            is IntegrationsSettingsAction.OnSonarrBaseUrlChanged -> {
                appPreferences.setValue(
                    appPreferences.sonarrBaseUrl,
                    action.baseUrl.ifBlank { null },
                )
                _state.value =
                    _state.value.copy(
                        sonarrBaseUrl = action.baseUrl,
                        sonarrTestState = PvrTestState.Idle,
                    )
            }
            is IntegrationsSettingsAction.OnSonarrApiKeyChanged -> {
                secureCredentialStore.putString(PvrCredentialKeys.SONARR_API_KEY, action.apiKey.ifBlank { null })
                _state.value =
                    _state.value.copy(
                        sonarrApiKey = action.apiKey,
                        sonarrTestState = PvrTestState.Idle,
                    )
            }
            is IntegrationsSettingsAction.OnTestSonarrConnection -> testSonarrConnection()
            is IntegrationsSettingsAction.OnRadarrEnabledChanged -> {
                appPreferences.setValue(appPreferences.radarrEnabled, action.enabled)
                _state.value = _state.value.copy(radarrEnabled = action.enabled)
            }
            is IntegrationsSettingsAction.OnRadarrBaseUrlChanged -> {
                appPreferences.setValue(
                    appPreferences.radarrBaseUrl,
                    action.baseUrl.ifBlank { null },
                )
                _state.value =
                    _state.value.copy(
                        radarrBaseUrl = action.baseUrl,
                        radarrTestState = PvrTestState.Idle,
                    )
            }
            is IntegrationsSettingsAction.OnRadarrApiKeyChanged -> {
                secureCredentialStore.putString(PvrCredentialKeys.RADARR_API_KEY, action.apiKey.ifBlank { null })
                _state.value =
                    _state.value.copy(
                        radarrApiKey = action.apiKey,
                        radarrTestState = PvrTestState.Idle,
                    )
            }
            is IntegrationsSettingsAction.OnTestRadarrConnection -> testRadarrConnection()
            is IntegrationsSettingsAction.OnPollIntervalChanged -> {
                appPreferences.setValue(appPreferences.pvrPollIntervalMinutes, action.minutes)
                _state.value = _state.value.copy(pvrPollIntervalMinutes = action.minutes)
            }
        }
    }

    private fun testSonarrConnection() {
        val baseUrl = _state.value.sonarrBaseUrl
        val apiKey = _state.value.sonarrApiKey
        _state.value = _state.value.copy(sonarrTestState = PvrTestState.Testing)
        viewModelScope.launch {
            val result =
                try {
                    val series = SonarrApi(baseUrl, apiKey).getSeries()
                    PvrTestState.Success(series.size)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    PvrTestState.Error(e.message ?: e.toString())
                }
            _state.value = _state.value.copy(sonarrTestState = result)
        }
    }

    private fun testRadarrConnection() {
        val baseUrl = _state.value.radarrBaseUrl
        val apiKey = _state.value.radarrApiKey
        _state.value = _state.value.copy(radarrTestState = PvrTestState.Testing)
        viewModelScope.launch {
            val result =
                try {
                    val movies = RadarrApi(baseUrl, apiKey).getMovie()
                    PvrTestState.Success(movies.size)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    PvrTestState.Error(e.message ?: e.toString())
                }
            _state.value = _state.value.copy(radarrTestState = result)
        }
    }
}
