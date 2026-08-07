package dev.pschmitt.jellyfin.api.pvr

import java.util.UUID

/**
 * Shared [dev.pschmitt.jellyfin.security.SecureCredentialStore] key names for the Sonarr/Radarr API
 * keys - kept here (in `data`, alongside the API clients that need them) rather than in
 * `core/security`, since `SecureCredentialStore` is intentionally generic and not
 * Sonarr/Radarr-specific, and `data` cannot depend on `core` (dependency direction is the other way
 * around).
 */
object PvrCredentialKeys {
    // Legacy global (pre-Profiles) keys. Only read by ProfileMigrationRunner during the one-time
    // upgrade backfill into namespaced per-PvrServiceConfig keys below - nothing else should read
    // these once Profiles has shipped.
    const val SONARR_API_KEY = "sonarr_api_key"
    const val RADARR_API_KEY = "radarr_api_key"
    // Key string keeps the pre-rebrand "jellyseerr" name - it's already persisted in encrypted
    // prefs on devices and inside backups, so changing it would silently drop the stored key.
    const val SEERR_API_KEY = "jellyseerr_api_key"
    const val SONARR_HTTP_HEADERS = "sonarr_http_headers"
    const val SONARR_BASIC_AUTH_USERNAME = "sonarr_basic_auth_username"
    const val SONARR_BASIC_AUTH_PASSWORD = "sonarr_basic_auth_password"
    const val RADARR_HTTP_HEADERS = "radarr_http_headers"
    const val RADARR_BASIC_AUTH_USERNAME = "radarr_basic_auth_username"
    const val RADARR_BASIC_AUTH_PASSWORD = "radarr_basic_auth_password"
    const val SEERR_HTTP_HEADERS = "seerr_http_headers"
    const val SEERR_BASIC_AUTH_USERNAME = "seerr_basic_auth_username"
    const val SEERR_BASIC_AUTH_PASSWORD = "seerr_basic_auth_password"

    private fun prefix(service: PvrService): String =
        when (service) {
            PvrService.SONARR -> "sonarr"
            PvrService.RADARR -> "radarr"
            PvrService.SEERR -> "seerr"
        }

    /**
     * Namespaced key for a specific
     * [PvrServiceConfig][dev.pschmitt.jellyfin.models.PvrServiceConfig] row's API key.
     */
    fun apiKey(service: PvrService, configId: UUID): String = "${prefix(service)}_api_key_$configId"

    fun httpHeaders(service: PvrService, configId: UUID): String =
        "${prefix(service)}_http_headers_$configId"

    fun basicAuthUsername(service: PvrService, configId: UUID): String =
        "${prefix(service)}_basic_auth_username_$configId"

    fun basicAuthPassword(service: PvrService, configId: UUID): String =
        "${prefix(service)}_basic_auth_password_$configId"

    /** Legacy flat key for [service], used only by [ProfileMigrationRunner] during backfill. */
    fun legacyApiKey(service: PvrService): String =
        when (service) {
            PvrService.SONARR -> SONARR_API_KEY
            PvrService.RADARR -> RADARR_API_KEY
            PvrService.SEERR -> SEERR_API_KEY
        }

    fun legacyHttpHeaders(service: PvrService): String =
        when (service) {
            PvrService.SONARR -> SONARR_HTTP_HEADERS
            PvrService.RADARR -> RADARR_HTTP_HEADERS
            PvrService.SEERR -> SEERR_HTTP_HEADERS
        }

    fun legacyBasicAuthUsername(service: PvrService): String =
        when (service) {
            PvrService.SONARR -> SONARR_BASIC_AUTH_USERNAME
            PvrService.RADARR -> RADARR_BASIC_AUTH_USERNAME
            PvrService.SEERR -> SEERR_BASIC_AUTH_USERNAME
        }

    fun legacyBasicAuthPassword(service: PvrService): String =
        when (service) {
            PvrService.SONARR -> SONARR_BASIC_AUTH_PASSWORD
            PvrService.RADARR -> RADARR_BASIC_AUTH_PASSWORD
            PvrService.SEERR -> SEERR_BASIC_AUTH_PASSWORD
        }
}
