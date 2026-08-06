package dev.pschmitt.jellyfin.repository

import dev.pschmitt.jellyfin.models.PvrDiskSpaceResult

/**
 * Free/total space on the Sonarr/Radarr root folders that hold the user's media - not Jellyfin's
 * own storage (the Jellyfin server API exposes no such endpoint) and not Jollyfin's on-device
 * downloads (computed locally from [dev.pschmitt.jellyfin.models.JollyfinSource.size] instead). A
 * plain on-demand suspend fetch, same rationale as [CalendarRepository] - disk space doesn't change
 * minute to minute.
 */
interface PvrDiskSpaceRepository {
    suspend fun getDiskSpace(): PvrDiskSpaceResult
}
