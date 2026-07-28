package dev.pschmitt.jellyfin.settings.domain

object Constants {
    // Player - Media Segments
    object PlayerMediaSegmentsAutoSkip {
        const val ALWAYS = "always"
        const val PIP = "pip"
    }

    // Cache
    const val DEFAULT_IMAGE_CACHE_SIZE_MB = 50

    // Network
    const val NETWORK_DEFAULT_REQUEST_TIMEOUT = 30_000L
    const val NETWORK_DEFAULT_CONNECT_TIMEOUT = 6_000L
    const val NETWORK_DEFAULT_SOCKET_TIMEOUT = 10_000L
    const val NETWORK_DEFAULT_PVR_SEARCH_TIMEOUT = 180_000L
}
