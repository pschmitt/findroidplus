package dev.jdtech.jellyfin.setup.presentation.qrexport

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jdtech.jellyfin.pvr.PvrConfiguration
import dev.jdtech.jellyfin.qrsetup.QrConfigCodec
import dev.jdtech.jellyfin.qrsetup.QrConfigManager
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import java.security.SecureRandom
import javax.inject.Inject
import kotlinx.coroutines.Job
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

    // Cancelled/replaced on every regenerate so rapid checkbox toggling can't let an earlier
    // (now stale) generate() call finish after a later one and clobber its result.
    private var generateJob: Job? = null

    fun onAction(action: QrExportAction) {
        when (action) {
            is QrExportAction.OnLoad -> load()
            is QrExportAction.OnIncludeJellyfinChanged ->
                updateAndRegenerate { it.copy(includeJellyfin = action.include) }
            is QrExportAction.OnIncludeSonarrChanged ->
                updateAndRegenerate { it.copy(includeSonarr = action.include) }
            is QrExportAction.OnIncludeRadarrChanged ->
                updateAndRegenerate { it.copy(includeRadarr = action.include) }
            is QrExportAction.OnIncludeSeerrChanged ->
                updateAndRegenerate { it.copy(includeSeerr = action.include) }
            is QrExportAction.OnServerSelected -> {
                val server = _state.value.availableServers.find { it.server.id == action.serverId }
                val defaultUser =
                    server?.users?.find { it.id == server.server.currentUserId }
                        ?: server?.users?.firstOrNull()
                updateAndRegenerate {
                    it.copy(selectedServerId = action.serverId, selectedUserId = defaultUser?.id)
                }
            }
            is QrExportAction.OnUserSelected ->
                updateAndRegenerate { it.copy(selectedUserId = action.userId) }
            is QrExportAction.OnAdvancedToggle ->
                _state.value = _state.value.copy(advancedExpanded = !_state.value.advancedExpanded)
            is QrExportAction.OnRegeneratePassword ->
                updateAndRegenerate { it.copy(password = generatePassword()) }
            is QrExportAction.OnTogglePasswordVisibility ->
                _state.value = _state.value.copy(passwordVisible = !_state.value.passwordVisible)
            is QrExportAction.OnBackClick -> Unit
        }
    }

    private fun updateAndRegenerate(transform: (QrExportState) -> QrExportState) {
        _state.value = transform(_state.value)
        generate()
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
                    password = generatePassword(),
                )
            generate()
        }
    }

    private fun generate() {
        generateJob?.cancel()
        val current = _state.value
        val anySelected =
            (current.includeJellyfin && current.jellyfinAvailable) ||
                (current.includeSonarr && current.sonarrAvailable) ||
                (current.includeRadarr && current.radarrAvailable) ||
                (current.includeSeerr && current.seerrAvailable)
        if (!anySelected) {
            _state.value = _state.value.copy(payload = null, error = null, isGenerating = false)
            return
        }
        generateJob = viewModelScope.launch {
            _state.value = _state.value.copy(isGenerating = true, error = null)
            try {
                val envelope =
                    qrConfigManager.buildEnvelope(
                        includeJellyfin = current.includeJellyfin && current.jellyfinAvailable,
                        jellyfinServerId = current.selectedServerId,
                        jellyfinUserId = current.selectedUserId,
                        includeSonarr = current.includeSonarr && current.sonarrAvailable,
                        includeRadarr = current.includeRadarr && current.radarrAvailable,
                        includeSeerr = current.includeSeerr && current.seerrAvailable,
                    )
                val payload = QrConfigCodec.encodePayload(envelope, current.password)
                _state.value = _state.value.copy(payload = payload, isGenerating = false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message, isGenerating = false)
            }
        }
    }

    /**
     * Legible-alphabet (no `0/O/1/I/L`) random passphrase, ~60 bits of entropy - short enough to
     * read off one screen and type into another by hand, since unlike the rest of the payload, the
     * passphrase itself never travels through the QR code.
     */
    private fun generatePassword(): String {
        val alphabet = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"
        val random = SecureRandom()
        return (1..12)
            .map { alphabet[random.nextInt(alphabet.length)] }
            .chunked(4)
            .joinToString("-") { it.joinToString("") }
    }
}
