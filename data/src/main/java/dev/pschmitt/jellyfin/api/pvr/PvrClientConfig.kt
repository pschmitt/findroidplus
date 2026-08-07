package dev.pschmitt.jellyfin.api.pvr

/**
 * Resolved PVR service config as needed by the lambda-injection pattern used by every `data`-layer
 * PVR repository (`SonarrSearchRepositoryImpl`, `RadarrSearchRepositoryImpl`,
 * `SeerrRepositoryImpl`, `CalendarRepositoryImpl`, `PvrDiskSpaceRepositoryImpl`,
 * `QueueStatusRepositoryImpl`, `SeasonEpisodesRepositoryImpl`). `data` can't import `core`'s
 * `dev.pschmitt.jellyfin.pvr.PvrConfigResolver`/`ResolvedPvrConfig` directly (module dependency
 * direction is the other way around), so this is a small `data`-local mirror, built by each Hilt
 * `@Provides` module in `core` from the resolver's own result and passed in as a lambda.
 */
data class PvrClientConfig(val enabled: Boolean, val baseUrl: String?, val apiKey: String?)

/**
 * Richer variant additionally carrying httpHeaders/basicAuth - for `BackupManager`/
 * `QrConfigManager`'s export/dump path, which needs the full resolved config (not just what an API
 * client needs to connect) to round-trip everything a profile's PVR override can hold.
 */
data class PvrClientConfigFull(
    val enabled: Boolean,
    val baseUrl: String?,
    val apiKey: String?,
    val httpHeaders: String?,
    val basicAuthUsername: String?,
    val basicAuthPassword: String?,
)
