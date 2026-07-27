package dev.jdtech.jellyfin.setup.presentation.qrexport

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jdtech.jellyfin.pvr.PvrConfiguration
import dev.jdtech.jellyfin.qrsetup.QrConfigCodec
import dev.jdtech.jellyfin.qrsetup.QrConfigManager
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class QrExportViewModel
@Inject
constructor(
    private val appPreferences: AppPreferences,
    private val pvrConfiguration: PvrConfiguration,
    private val qrConfigManager: QrConfigManager,
) : ViewModel() {
    private val _state = MutableStateFlow(QrExportState())
    val state = _state.asStateFlow()

    fun onAction(action: QrExportAction) {
        when (action) {
            is QrExportAction.OnLoad -> load()
            is QrExportAction.OnIncludeJellyfinChanged ->
                _state.value = _state.value.copy(includeJellyfin = action.include, payload = null)
            is QrExportAction.OnIncludeSonarrChanged ->
                _state.value = _state.value.copy(includeSonarr = action.include, payload = null)
            is QrExportAction.OnIncludeRadarrChanged ->
                _state.value = _state.value.copy(includeRadarr = action.include, payload = null)
            is QrExportAction.OnIncludeSeerrChanged ->
                _state.value = _state.value.copy(includeSeerr = action.include, payload = null)
            is QrExportAction.OnServerSelected -> {
                val server = _state.value.availableServers.find { it.server.id == action.serverId }
                val defaultUser =
                    server?.users?.find { it.id == server.server.currentUserId }
                        ?: server?.users?.firstOrNull()
                _state.value =
                    _state.value.copy(
                        selectedServerId = action.serverId,
                        selectedUserId = defaultUser?.id,
                        payload = null,
                    )
            }
            is QrExportAction.OnUserSelected ->
                _state.value = _state.value.copy(selectedUserId = action.userId, payload = null)
            is QrExportAction.OnAdvancedToggle ->
                _state.value = _state.value.copy(advancedExpanded = !_state.value.advancedExpanded)
            is QrExportAction.OnPasswordChanged ->
                _state.value = _state.value.copy(password = action.password, payload = null)
            is QrExportAction.OnGenerateClick -> generate()
            is QrExportAction.OnBackClick -> Unit
        }
    }

    private fun load() {
        viewModelScope.launch {
            val availableServers = qrConfigManager.getAvailableServers()
            val currentServerId = appPreferences.getValue(appPreferences.currentServer)
            val currentServer =
                availableServers.find { it.server.id == currentServerId }
                    ?: availableServers.firstOrNull()
            val currentUser =
                currentServer?.users?.find { it.id == currentServer.server.currentUserId }
                    ?: currentServer?.users?.firstOrNull()

            _state.value =
                _state.value.copy(
                    jellyfinAvailable = currentServer != null,
                    sonarrAvailable = pvrConfiguration.isSonarrConfigured(),
                    radarrAvailable = pvrConfiguration.isRadarrConfigured(),
                    seerrAvailable = pvrConfiguration.isSeerrConfigured(),
                    availableServers = availableServers,
                    selectedServerId = currentServer?.server?.id,
                    selectedUserId = currentUser?.id,
                )
        }
    }

    private fun generate() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isGenerating = true, error = null)
            try {
                val current = _state.value
                val envelope =
                    qrConfigManager.buildEnvelope(
                        includeJellyfin = current.includeJellyfin && current.jellyfinAvailable,
                        jellyfinServerId = current.selectedServerId,
                        jellyfinUserId = current.selectedUserId,
                        includeSonarr = current.includeSonarr && current.sonarrAvailable,
                        includeRadarr = current.includeRadarr && current.radarrAvailable,
                        includeSeerr = current.includeSeerr && current.seerrAvailable,
                    )
                val payload =
                    QrConfigCodec.encodePayload(envelope, current.password.ifBlank { null })
                _state.value = _state.value.copy(payload = payload, isGenerating = false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message, isGenerating = false)
            }
        }
    }
}
