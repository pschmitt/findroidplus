package dev.pschmitt.jellyfin.utils

/**
 * Total/available bytes on one app-storage volume, see [Downloader.getAllStorageStats]. [path] is
 * that volume's root (the same `getExternalFilesDirs()` entry `JollyfinSource.path` for a LOCAL
 * source would start with, when the download actually lives on this volume) - lets callers
 * attribute local download usage to the *correct* volume instead of always assuming index 0, which
 * is wrong on devices where the configured download location is the external/removable volume (an
 * SD card or USB storage) rather than internal storage. [isRemovable] distinguishes that
 * external/removable volume from the internal one for display purposes.
 */
data class DeviceStorageStats(
    val path: String,
    val totalBytes: Long,
    val availableBytes: Long,
    val isRemovable: Boolean,
)
