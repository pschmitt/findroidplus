package dev.jdtech.jellyfin.api.pvr

/**
 * Shared [dev.jdtech.jellyfin.security.SecureCredentialStore] key names for the Sonarr/Radarr API
 * keys - kept here (in `data`, alongside the API clients that need them) rather than in
 * `core/security`, since `SecureCredentialStore` is intentionally generic and not
 * Sonarr/Radarr-specific, and `data` cannot depend on `core` (dependency direction is the other
 * way around).
 */
object PvrCredentialKeys {
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
}
