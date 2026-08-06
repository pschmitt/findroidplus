package dev.pschmitt.jellyfin.presentation.settings.remotedevices

import dev.pschmitt.jellyfin.models.JollyfinShow
import dev.pschmitt.jellyfin.models.RemoteConfigCommand
import dev.pschmitt.jellyfin.models.RemoteDeviceInfo

data class RemoteDevicesState(
    val isLoading: Boolean = false,
    // Pull-to-refresh spinner, distinct from isLoading - see AutoDownloadRulesState's identical
    // split for why.
    val isRefreshing: Boolean = false,
    // Per-device opt-out (AppPreferences.remoteManagementEnabled) - whether *this* device allows
    // others to manage it. Independent of the devices list below, which is about other devices.
    val remoteManagementEnabled: Boolean = true,
    val devices: List<RemoteDeviceInfo> = emptyList(),
    // Commands this device has enqueued for others that haven't been applied by their target yet.
    val pendingCommands: List<RemoteConfigCommand> = emptyList(),
    // Resolved show objects for active rules' posters, keyed by seriesId string - a
    // RemoteActiveRuleSummary only carries an id + name, not enough to render a poster, so this
    // device resolves the real show via its own Jellyfin session (best-effort; a seriesId missing
    // here just means its row renders without a poster rather than failing to load at all).
    val showsBySeriesId: Map<String, JollyfinShow> = emptyMap(),
    val error: Exception? = null,
)

sealed interface RemoteDevicesAction {
    data object OnBackClick : RemoteDevicesAction

    data class RemoveActiveRule(
        val targetDeviceId: String,
        val serverId: String,
        val seriesId: String,
        val alsoDeleteDownloads: Boolean = false,
    ) : RemoteDevicesAction

    data class CancelPendingCommand(val commandId: String) : RemoteDevicesAction

    data class SetRemoteManagementEnabled(val enabled: Boolean) : RemoteDevicesAction
}
