package dev.jdtech.jellyfin.presentation.settings.remotedevices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jdtech.jellyfin.models.FindroidShow
import dev.jdtech.jellyfin.models.RemoteDeviceInfo
import dev.jdtech.jellyfin.repository.JellyfinRepository
import dev.jdtech.jellyfin.repository.RemoteConfigRepository
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

@HiltViewModel
class RemoteDevicesViewModel
@Inject
constructor(
    private val remoteConfigRepository: RemoteConfigRepository,
    private val jellyfinRepository: JellyfinRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(RemoteDevicesState())
    val state = _state.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _state.emit(
                _state.value.copy(
                    isLoading = true,
                    error = null,
                    remoteManagementEnabled = remoteConfigRepository.isRemoteManagementEnabled(),
                )
            )
            try {
                val (devices, pending, posters) = loadDevicesPendingAndPosters()
                _state.emit(
                    _state.value.copy(
                        isLoading = false,
                        devices = devices,
                        pendingCommands = pending,
                        showsBySeriesId = posters,
                    )
                )
            } catch (e: Exception) {
                _state.emit(_state.value.copy(isLoading = false, error = e))
            }
        }
    }

    // Pull-to-refresh: also drives an immediate RemoteConfigRepository.syncNow() first - a
    // device's own heartbeat/active-rules summary is otherwise only as fresh as its last periodic
    // RemoteConfigWorker cycle, same reasoning as AutoDownloadRulesViewModel.refresh().
    fun refresh() {
        viewModelScope.launch {
            _state.emit(_state.value.copy(isRefreshing = true, error = null))
            try {
                remoteConfigRepository.syncNow()
            } catch (e: Exception) {
                Timber.w(e, "Manual remote config sync failed")
            }
            try {
                val (devices, pending, posters) = loadDevicesPendingAndPosters()
                _state.emit(
                    _state.value.copy(
                        isRefreshing = false,
                        devices = devices,
                        pendingCommands = pending,
                        showsBySeriesId = posters,
                    )
                )
            } catch (e: Exception) {
                _state.emit(_state.value.copy(isRefreshing = false, error = e))
            }
        }
    }

    private suspend fun loadDevicesPendingAndPosters() = coroutineScope {
        val devices = remoteConfigRepository.listOtherDevices()
        val pending = remoteConfigRepository.listPendingCommandsFromThisDevice()
        val posters = resolveShowPosters(devices)
        Triple(devices, pending, posters)
    }

    /**
     * `RemoteActiveRuleSummary` only carries a seriesId + name, not enough to render a poster -
     * this device resolves the real [FindroidShow] itself via the same Jellyfin session every
     * instance shares. Best-effort and concurrent: a show that fails to resolve (deleted, network
     * hiccup) just renders without a poster rather than failing the whole screen.
     */
    private suspend fun resolveShowPosters(
        devices: List<RemoteDeviceInfo>
    ): Map<String, FindroidShow> = coroutineScope {
        val seriesIds = devices.flatMap { it.activeRules }.map { it.seriesId }.distinct()
        seriesIds
            .map { seriesId ->
                async {
                    try {
                        seriesId to jellyfinRepository.getShow(UUID.fromString(seriesId))
                    } catch (e: Exception) {
                        null
                    }
                }
            }
            .awaitAll()
            .filterNotNull()
            .toMap()
    }

    fun onAction(action: RemoteDevicesAction) {
        when (action) {
            is RemoteDevicesAction.RemoveActiveRule ->
                removeActiveRule(action.targetDeviceId, action.serverId, action.seriesId)
            is RemoteDevicesAction.CancelPendingCommand -> cancelPendingCommand(action.commandId)
            is RemoteDevicesAction.SetRemoteManagementEnabled ->
                setRemoteManagementEnabled(action.enabled)
            is RemoteDevicesAction.OnBackClick -> Unit
        }
    }

    private fun removeActiveRule(targetDeviceId: String, serverId: String, seriesId: String) {
        viewModelScope.launch {
            val userId = jellyfinRepository.getUserId()
            remoteConfigRepository.pushRemoveRule(
                targetDeviceId = targetDeviceId,
                serverId = serverId,
                userId = userId,
                seriesId = UUID.fromString(seriesId),
            )
            // The rule only actually disappears once the target device applies the clearing
            // command on its own next sync - reload now just to refresh the pending-commands list
            // (the new clearing command shows up there immediately), not because the removal is
            // already reflected in devices[].activeRules.
            load()
        }
    }

    private fun cancelPendingCommand(commandId: String) {
        viewModelScope.launch {
            remoteConfigRepository.cancelPendingCommand(commandId)
            load()
        }
    }

    private fun setRemoteManagementEnabled(enabled: Boolean) {
        viewModelScope.launch {
            remoteConfigRepository.setRemoteManagementEnabled(enabled)
            _state.emit(_state.value.copy(remoteManagementEnabled = enabled))
            // Disabling removes this device's own entry from the shared registry immediately -
            // doesn't affect the "other devices" list below, but reload anyway for hygiene.
            load()
        }
    }
}
