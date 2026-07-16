package dev.jdtech.jellyfin.repository

import dev.jdtech.jellyfin.models.QueueStatus
import java.util.UUID
import kotlinx.coroutines.flow.Flow

/**
 * Exposes the current Sonarr/Radarr download queue, keyed by the Jellyfin item id it was matched
 * to (see `matchSonarr`/`matchRadarr` in `QueueStatusMatching.kt`). Items Findroid couldn't match
 * to a provider id, or that aren't in either queue right now, are simply absent from the map -
 * never an error entry.
 */
interface QueueStatusRepository {
    fun getQueueStatusFlow(): Flow<Map<UUID, QueueStatus>>

    fun getQueueStatusFlow(itemId: UUID): Flow<QueueStatus?>

    /** Forces an immediate fetch+match cycle, independent of the in-process/background polling. */
    suspend fun refreshNow()
}
