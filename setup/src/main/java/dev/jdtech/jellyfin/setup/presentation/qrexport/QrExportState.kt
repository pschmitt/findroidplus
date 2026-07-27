package dev.jdtech.jellyfin.setup.presentation.qrexport

import dev.jdtech.jellyfin.models.ServerWithAddressesAndUsers
import java.util.UUID

data class QrExportState(
    val jellyfinAvailable: Boolean = false,
    val sonarrAvailable: Boolean = false,
    val radarrAvailable: Boolean = false,
    val seerrAvailable: Boolean = false,
    val includeJellyfin: Boolean = true,
    val includeSonarr: Boolean = true,
    val includeRadarr: Boolean = true,
    val includeSeerr: Boolean = true,
    // Only relevant/shown when there's more than one server, or the selected server has more
    // than one user - single-server/single-user setups just use whichever one there is.
    val availableServers: List<ServerWithAddressesAndUsers> = emptyList(),
    val selectedServerId: String? = null,
    val selectedUserId: UUID? = null,
    val advancedExpanded: Boolean = false,
    // Pre-filled from the selected user/current storage, editable - a blank jellyfinPassword
    // means "keep the existing session" (no re-auth); a non-blank one triggers a live login
    // against the selected server to embed a fresh token instead. Sonarr/Radarr/Seerr fields are
    // plain overrides (no re-auth involved), pre-filled from AppPreferences/SecureCredentialStore.
    val jellyfinUsername: String = "",
    val jellyfinPassword: String = "",
    val jellyfinPasswordVisible: Boolean = false,
    val isVerifyingJellyfinLogin: Boolean = false,
    val jellyfinLoginError: String? = null,
    val sonarrBaseUrl: String = "",
    val sonarrApiKey: String = "",
    val radarrBaseUrl: String = "",
    val radarrApiKey: String = "",
    val seerrBaseUrl: String = "",
    val seerrApiKey: String = "",
    // Auto-generated on load (see QrExportViewModel.generatePassword) - the export is encrypted
    // by default, not opt-in; the user reads/re-generates it here rather than typing one in.
    val password: String = "",
    val passwordVisible: Boolean = false,
    val isGenerating: Boolean = false,
    val payload: String? = null,
    val error: String? = null,
)
