package dev.pschmitt.jellyfin.presentation.settings.remotedevices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.pschmitt.jellyfin.models.JollyfinShow
import dev.pschmitt.jellyfin.models.RemoteDeviceInfo
import dev.pschmitt.jellyfin.repository.JellyfinRepository
import dev.pschmitt.jellyfin.repository.RemoteConfigRepository
import dev.pschmitt.jellyfin.settings.domain.AppPreferences
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
    private val appPreferences: AppPreferences,
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
     * this device resolves the real [JollyfinShow] itself via its own live [jellyfinRepository]
     * session. That session (a process-wide singleton - see `JellyfinApi`'s kdoc) is only ever
     * pointed at *one* server at a time: whichever `AppPreferences.currentServer` is currently
     * active here. A rule belonging to a *different* server than the one currently active on this
     * device can't be resolved at all - looking it up would just throw (wrong server entirely, not
     * merely a missing item) - so those are skipped up front rather than attempted and silently
     * swallowed. Best-effort and concurrent otherwise: a show that fails to resolve for any other
     * reason (deleted, network hiccup) just renders without a poster, logged for diagnosis, rather
     * than failing the whole screen.
     */
    private suspend fun resolveShowPosters(
        devices: List<RemoteDeviceInfo>
    ): Map<String, JollyfinShow> = coroutineScope {
        val currentServerId = appPreferences.getValue(appPreferences.currentServer)
        val rules = devices.flatMap { it.activeRules }.distinctBy { it.seriesId }
        rules
            .map { rule ->
                async {
                    if (rule.serverId != currentServerId) {
                        Timber.w(
                            "Skipping poster for '%s': belongs to server %s, this device is on %s",
                            rule.showName,
                            rule.serverId,
                            currentServerId,
                        )
                        return@async null
                    }
                    try {
                        rule.seriesId to jellyfinRepository.getShow(UUID.fromString(rule.seriesId))
                    } catch (e: Exception) {
                        Timber.w(
                            e,
                            "Failed to resolve poster for '%s' (%s)",
                            rule.showName,
                            rule.seriesId,
                        )
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
                removeActiveRule(
                    action.targetDeviceId,
                    action.serverId,
                    action.seriesId,
                    action.alsoDeleteDownloads,
                )
            is RemoteDevicesAction.CancelPendingCommand -> cancelPendingCommand(action.commandId)
            is RemoteDevicesAction.SetRuleEnabled ->
                setRuleEnabled(
                    action.targetDeviceId,
                    action.serverId,
                    action.seriesId,
                    action.enabled,
                )
            is RemoteDevicesAction.SetRemoteManagementEnabled ->
                setRemoteManagementEnabled(action.enabled)
            is RemoteDevicesAction.ForgetDevice -> forgetDevice(action.deviceId)
            is RemoteDevicesAction.OnBackClick -> Unit
        }
    }

    private fun forgetDevice(deviceId: String) {
        viewModelScope.launch {
            remoteConfigRepository.removeDevice(deviceId)
            load()
        }
    }

    private fun removeActiveRule(
        targetDeviceId: String,
        serverId: String,
        seriesId: String,
        alsoDeleteDownloads: Boolean,
    ) {
        viewModelScope.launch {
            val userId = jellyfinRepository.getUserId()
            remoteConfigRepository.pushRemoveRule(
                targetDeviceId = targetDeviceId,
                serverId = serverId,
                userId = userId,
                seriesId = UUID.fromString(seriesId),
                alsoDeleteDownloads = alsoDeleteDownloads,
            )
            // The rule only actually disappears once the target device applies the clearing
            // command on its own next sync - reload now just to refresh the pending-commands list
            // (the new clearing command shows up there immediately), not because the removal is
            // already reflected in devices[].activeRules.
            load()
        }
    }

    private fun setRuleEnabled(
        targetDeviceId: String,
        serverId: String,
        seriesId: String,
        enabled: Boolean,
    ) {
        viewModelScope.launch {
            val userId = jellyfinRepository.getUserId()
            remoteConfigRepository.pushSetRuleEnabled(
                targetDeviceId = targetDeviceId,
                serverId = serverId,
                userId = userId,
                seriesId = UUID.fromString(seriesId),
                enabled = enabled,
            )
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
