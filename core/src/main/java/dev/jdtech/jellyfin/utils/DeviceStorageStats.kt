package dev.jdtech.jellyfin.utils

/** Total/available bytes on the device's primary download storage location, see [Downloader.getStorageStats]. */
data class DeviceStorageStats(val totalBytes: Long, val availableBytes: Long)
