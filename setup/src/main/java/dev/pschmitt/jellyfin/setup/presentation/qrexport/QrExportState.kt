package dev.pschmitt.jellyfin.setup.presentation.qrexport

import dev.pschmitt.jellyfin.models.ProfileWithUserAndServer
import dev.pschmitt.jellyfin.models.ServerWithAddressesAndUsers
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
    val availableProfiles: List<ProfileWithUserAndServer> = emptyList(),
    val selectedProfileId: UUID? = null,
    // Only relevant/shown when there's more than one server, or the selected server has more
    // than one user - single-server/single-user setups just use whichever one there is.
    val availableServers: List<ServerWithAddressesAndUsers> = emptyList(),
    val selectedServerId: String? = null,
    val selectedUserId: UUID? = null,
    val advancedExpanded: Boolean = false,
    // Pre-filled from the selected user/current storage, editable - a blank jellyfinPassword
    // means "keep the existing session" (no re-auth); a non-blank one triggers a live login
    // against the selected server to embed a fresh token instead. Sonarr/Radarr/Seerr apiKey
    // fields follow the same blank-means-"keep the stored one" pattern - only baseUrl is
    // pre-filled from the selected profile; apiKey starts blank and QrConfigManager falls back to
    // the selected profile's resolved SecureCredentialStore value when it's left that way.
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
