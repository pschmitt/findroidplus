package dev.jdtech.jellyfin.presentation.settings.integrations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jdtech.jellyfin.api.pvr.SeerrApi
import dev.jdtech.jellyfin.api.pvr.PvrCredentialKeys
import dev.jdtech.jellyfin.api.pvr.PvrService
import dev.jdtech.jellyfin.api.pvr.RadarrApi
import dev.jdtech.jellyfin.api.pvr.SonarrApi
import dev.jdtech.jellyfin.security.SecureCredentialStore
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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

    // API keys are debounced instead of persisted per keystroke - every putString on
    // EncryptedSharedPreferences runs keystore crypto, which is pointless churn while the user is
    // still typing/pasting. Dirty flags let onCleared flush a pending write when the screen is
    // closed before the debounce fires.
    private val apiKeyPersistJobs = mutableMapOf<String, Job>()
    private val dirtyApiKeys = mutableSetOf<String>()

    fun load() {
        _state.value =
            IntegrationsSettingsState(
                sonarrEnabled = appPreferences.getValue(appPreferences.sonarrEnabled),
                sonarrBaseUrl = appPreferences.getValue(appPreferences.sonarrBaseUrl).orEmpty(),
                sonarrApiKey = secureCredentialStore.getString(PvrCredentialKeys.SONARR_API_KEY).orEmpty(),
                sonarrHttpHeaders = secureCredentialStore.getString(PvrCredentialKeys.SONARR_HTTP_HEADERS).orEmpty(),
                sonarrBasicAuthUsername = secureCredentialStore.getString(PvrCredentialKeys.SONARR_BASIC_AUTH_USERNAME).orEmpty(),
                sonarrBasicAuthPassword = secureCredentialStore.getString(PvrCredentialKeys.SONARR_BASIC_AUTH_PASSWORD).orEmpty(),
                radarrEnabled = appPreferences.getValue(appPreferences.radarrEnabled),
                radarrBaseUrl = appPreferences.getValue(appPreferences.radarrBaseUrl).orEmpty(),
                radarrApiKey = secureCredentialStore.getString(PvrCredentialKeys.RADARR_API_KEY).orEmpty(),
                radarrHttpHeaders = secureCredentialStore.getString(PvrCredentialKeys.RADARR_HTTP_HEADERS).orEmpty(),
                radarrBasicAuthUsername = secureCredentialStore.getString(PvrCredentialKeys.RADARR_BASIC_AUTH_USERNAME).orEmpty(),
                radarrBasicAuthPassword = secureCredentialStore.getString(PvrCredentialKeys.RADARR_BASIC_AUTH_PASSWORD).orEmpty(),
                seerrEnabled = appPreferences.getValue(appPreferences.seerrEnabled),
                seerrBaseUrl = appPreferences.getValue(appPreferences.seerrBaseUrl).orEmpty(),
                seerrApiKey =
                    secureCredentialStore.getString(PvrCredentialKeys.SEERR_API_KEY).orEmpty(),
                seerrHttpHeaders = secureCredentialStore.getString(PvrCredentialKeys.SEERR_HTTP_HEADERS).orEmpty(),
                seerrBasicAuthUsername = secureCredentialStore.getString(PvrCredentialKeys.SEERR_BASIC_AUTH_USERNAME).orEmpty(),
                seerrBasicAuthPassword = secureCredentialStore.getString(PvrCredentialKeys.SEERR_BASIC_AUTH_PASSWORD).orEmpty(),
                pvrPollIntervalMinutes =
                    appPreferences.getValue(appPreferences.pvrPollIntervalMinutes),
                pvrReleaseCacheMinutes =
                    appPreferences.getValue(appPreferences.pvrReleaseCacheMinutes),
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
                persistApiKeyDebounced(PvrCredentialKeys.SONARR_API_KEY, action.apiKey)
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
                persistApiKeyDebounced(PvrCredentialKeys.RADARR_API_KEY, action.apiKey)
                _state.value =
                    _state.value.copy(
                        radarrApiKey = action.apiKey,
                        radarrTestState = PvrTestState.Idle,
                    )
            }
            is IntegrationsSettingsAction.OnTestRadarrConnection -> testRadarrConnection()
            is IntegrationsSettingsAction.OnSeerrEnabledChanged -> {
                appPreferences.setValue(appPreferences.seerrEnabled, action.enabled)
                _state.value = _state.value.copy(seerrEnabled = action.enabled)
            }
            is IntegrationsSettingsAction.OnSeerrBaseUrlChanged -> {
                appPreferences.setValue(
                    appPreferences.seerrBaseUrl,
                    action.baseUrl.ifBlank { null },
                )
                _state.value =
                    _state.value.copy(
                        seerrBaseUrl = action.baseUrl,
                        seerrTestState = PvrTestState.Idle,
                    )
            }
            is IntegrationsSettingsAction.OnSeerrApiKeyChanged -> {
                persistApiKeyDebounced(PvrCredentialKeys.SEERR_API_KEY, action.apiKey)
                _state.value =
                    _state.value.copy(
                        seerrApiKey = action.apiKey,
                        seerrTestState = PvrTestState.Idle,
                    )
            }
            is IntegrationsSettingsAction.OnAdvancedSettingsChanged -> updateAdvancedSettings(action)
            is IntegrationsSettingsAction.OnTestSeerrConnection -> testSeerrConnection()
            is IntegrationsSettingsAction.OnPollIntervalChanged -> {
                appPreferences.setValue(appPreferences.pvrPollIntervalMinutes, action.minutes)
                _state.value = _state.value.copy(pvrPollIntervalMinutes = action.minutes)
            }
            is IntegrationsSettingsAction.OnReleaseCacheChanged -> {
                appPreferences.setValue(appPreferences.pvrReleaseCacheMinutes, action.minutes)
                _state.value = _state.value.copy(pvrReleaseCacheMinutes = action.minutes)
            }
        }
    }

    private fun updateAdvancedSettings(action: IntegrationsSettingsAction.OnAdvancedSettingsChanged) {
        val (headersKey, usernameKey, passwordKey) =
            when (action.service) {
                PvrService.SONARR -> Triple(
                    PvrCredentialKeys.SONARR_HTTP_HEADERS,
                    PvrCredentialKeys.SONARR_BASIC_AUTH_USERNAME,
                    PvrCredentialKeys.SONARR_BASIC_AUTH_PASSWORD,
                )
                PvrService.RADARR -> Triple(
                    PvrCredentialKeys.RADARR_HTTP_HEADERS,
                    PvrCredentialKeys.RADARR_BASIC_AUTH_USERNAME,
                    PvrCredentialKeys.RADARR_BASIC_AUTH_PASSWORD,
                )
                PvrService.SEERR -> Triple(
                    PvrCredentialKeys.SEERR_HTTP_HEADERS,
                    PvrCredentialKeys.SEERR_BASIC_AUTH_USERNAME,
                    PvrCredentialKeys.SEERR_BASIC_AUTH_PASSWORD,
                )
            }
        secureCredentialStore.putString(headersKey, action.headers.ifBlank { null })
        secureCredentialStore.putString(usernameKey, action.basicAuthUsername.ifBlank { null })
        secureCredentialStore.putString(passwordKey, action.basicAuthPassword.ifBlank { null })
        _state.value =
            when (action.service) {
                PvrService.SONARR -> _state.value.copy(
                    sonarrHttpHeaders = action.headers,
                    sonarrBasicAuthUsername = action.basicAuthUsername,
                    sonarrBasicAuthPassword = action.basicAuthPassword,
                )
                PvrService.RADARR -> _state.value.copy(
                    radarrHttpHeaders = action.headers,
                    radarrBasicAuthUsername = action.basicAuthUsername,
                    radarrBasicAuthPassword = action.basicAuthPassword,
                )
                PvrService.SEERR -> _state.value.copy(
                    seerrHttpHeaders = action.headers,
                    seerrBasicAuthUsername = action.basicAuthUsername,
                    seerrBasicAuthPassword = action.basicAuthPassword,
                )
            }
    }

    private fun persistApiKeyDebounced(credentialKey: String, value: String) {
        dirtyApiKeys.add(credentialKey)
        apiKeyPersistJobs[credentialKey]?.cancel()
        apiKeyPersistJobs[credentialKey] =
            viewModelScope.launch {
                delay(API_KEY_PERSIST_DEBOUNCE_MS)
                secureCredentialStore.putString(credentialKey, value.ifBlank { null })
                dirtyApiKeys.remove(credentialKey)
            }
    }

    override fun onCleared() {
        super.onCleared()
        apiKeyPersistJobs.values.forEach { it.cancel() }
        // Flush anything still pending so closing the screen right after typing doesn't lose it.
        if (PvrCredentialKeys.SONARR_API_KEY in dirtyApiKeys) {
            secureCredentialStore.putString(
                PvrCredentialKeys.SONARR_API_KEY,
                _state.value.sonarrApiKey.ifBlank { null },
            )
        }
        if (PvrCredentialKeys.RADARR_API_KEY in dirtyApiKeys) {
            secureCredentialStore.putString(
                PvrCredentialKeys.RADARR_API_KEY,
                _state.value.radarrApiKey.ifBlank { null },
            )
        }
        if (PvrCredentialKeys.SEERR_API_KEY in dirtyApiKeys) {
            secureCredentialStore.putString(
                PvrCredentialKeys.SEERR_API_KEY,
                _state.value.seerrApiKey.ifBlank { null },
            )
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

    private fun testSeerrConnection() {
        val baseUrl = _state.value.seerrBaseUrl
        val apiKey = _state.value.seerrApiKey
        _state.value = _state.value.copy(seerrTestState = PvrTestState.Testing)
        viewModelScope.launch {
            val result =
                try {
                    val api = SeerrApi(baseUrl, apiKey)
                    // auth/me validates the key; the request count doubles as the "N items" the
                    // shared success message expects.
                    api.getCurrentUser()
                    PvrTestState.Success(api.getRequests(take = 1).pageInfo.results)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    PvrTestState.Error(e.message ?: e.toString())
                }
            _state.value = _state.value.copy(seerrTestState = result)
        }
    }

    private companion object {
        const val API_KEY_PERSIST_DEBOUNCE_MS = 750L
    }
}
