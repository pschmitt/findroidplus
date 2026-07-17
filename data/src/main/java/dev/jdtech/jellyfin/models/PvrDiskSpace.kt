package dev.jdtech.jellyfin.models

/**
 * PVR-side disk space, for the Downloads screen's storage summary. Sonarr and Radarr are commonly
 * hosted on the same machine/disk, in which case showing both would double-count (and just look
 * wrong) the same underlying space - rather than trying to detect that (matching root folder
 * paths across two independent services isn't reliable), this always assumes it's true and
 * surfaces a single number, preferring Sonarr's when both are configured. `null` when neither
 * service is configured or both fetches failed - nothing meaningful to show either way, so the UI
 * just omits the row for what's a nice-to-have summary, not a critical status.
 */
data class PvrDiskSpaceResult(val storage: PvrServiceDiskSpace? = null)

/**
 * [freeBytes]/[totalBytes] describe a single root folder - the largest one a service reports, not
 * a sum across all of them. Root folders commonly share the same underlying volume (e.g. movies/
 * and tv/ on one disk), which would double-count that shared space if summed.
 */
data class PvrServiceDiskSpace(val freeBytes: Long, val totalBytes: Long)
