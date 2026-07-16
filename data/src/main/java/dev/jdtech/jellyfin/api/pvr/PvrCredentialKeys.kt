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
}
