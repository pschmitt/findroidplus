package dev.pschmitt.jellyfin.utils

import android.content.Context
import android.os.Environment

/**
 * Resolves the [Context.getExternalFilesDirs] index matching the "internal"/"external"
 * download-location preference. "internal" means the non-removable app-private external storage
 * (the built-in flash storage), "external" means removable storage (SD card/USB) - neither is
 * [Context.getFilesDir]. Returns -1 for "ask" or when no matching volume is mounted.
 */
fun resolveDownloadStorageIndex(context: Context, locationPreference: String): Int {
    val storageLocations = context.getExternalFilesDirs(null)
    return when (locationPreference) {
        "internal" ->
            storageLocations.indexOfFirst {
                it != null && !Environment.isExternalStorageRemovable(it)
            }
        "external" ->
            storageLocations.indexOfFirst {
                it != null && Environment.isExternalStorageRemovable(it)
            }
        else -> -1
    }
}

/**
 * The inverse lookup: which storage volume a downloaded file's [path] actually lives on, as
 * removable (SD card/USB) or not. Returns null if [path] doesn't match any currently mounted
 * app-storage volume (e.g. its volume isn't mounted right now).
 */
fun isPathOnRemovableStorage(context: Context, path: String): Boolean? {
    val storageLocation =
        context.getExternalFilesDirs(null).firstOrNull { it != null && path.startsWith(it.path) }
            ?: return null
    return Environment.isExternalStorageRemovable(storageLocation)
}
