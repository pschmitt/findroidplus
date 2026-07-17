package dev.jdtech.jellyfin.api.pvr

/** Services which share the configurable HTTP transport. */
enum class PvrService {
    SONARR,
    RADARR,
    SEERR,
}

/** Optional reverse-proxy authentication and headers for a PVR service. */
data class PvrAdvancedConfig(
    val headers: List<Pair<String, String>> = emptyList(),
    val basicAuthUsername: String? = null,
    val basicAuthPassword: String? = null,
) {
    companion object {
        fun parseHeaders(value: String?): List<Pair<String, String>> =
            value
                .orEmpty()
                .lines()
                .mapNotNull { line ->
                    val separator = line.indexOf(':')
                    if (separator <= 0) return@mapNotNull null
                    val name = line.take(separator).trim()
                    if (name.isEmpty()) null else name to line.drop(separator + 1).trim()
                }
    }
}

/**
 * Supplies credentials from the app module without making the data module depend on Android
 * encrypted preferences. The provider is installed once by BaseApplication and read when a PVR
 * client is constructed, so settings changes apply to the next request immediately.
 */
object PvrAdvancedSettings {
    @Volatile var provider: (PvrService) -> PvrAdvancedConfig = { PvrAdvancedConfig() }
}
