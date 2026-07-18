package dev.jdtech.jellyfin.repository

import dev.jdtech.jellyfin.models.ManualImportCandidate
import dev.jdtech.jellyfin.models.PvrQueueSnapshot
import dev.jdtech.jellyfin.models.PvrSource
import dev.jdtech.jellyfin.models.QueueStatus
import java.util.UUID
import kotlinx.coroutines.flow.Flow

/**
 * Exposes the current Sonarr/Radarr download queue. [getQueueSnapshotFlow] carries the full queue
 * - including entries that couldn't be matched to a Jellyfin library item (e.g. a torrent added
 * manually on the PVR side) and per-service fetch errors - see `matchSonarr`/`matchRadarr` in
 * `QueueStatusMatching.kt`. The map-shaped flows only cover matched entries, keyed by the
 * Jellyfin item id; items that aren't in either queue right now are simply absent.
 */
interface QueueStatusRepository {
    /** Every current queue entry (matched or not, Sonarr-then-Radarr order) plus fetch errors. */
    fun getQueueSnapshotFlow(): Flow<PvrQueueSnapshot>

    fun getQueueStatusFlow(): Flow<Map<UUID, QueueStatus>>

    fun getQueueStatusFlow(itemId: UUID): Flow<QueueStatus?>

    /** Radarr queue status keyed by TMDB id, including movies not imported into Jellyfin yet. */
    fun getRadarrQueueStatusFlow(): Flow<Map<Int, QueueStatus>>

    /** Sonarr queue status keyed by Sonarr episode id, including episodes not in Jellyfin yet. */
    fun getSonarrQueueStatusFlow(): Flow<Map<Int, QueueStatus>>

    /** Forces an immediate fetch+match cycle, independent of the in-process/background polling. */
    suspend fun refreshNow()

    /**
     * Removes a queue entry from Sonarr/Radarr (there is no API-side "pause" - that lives in the
     * download client). [removeFromClient] also deletes the download in the download client;
     * [blocklist] prevents the same release from being grabbed again. Refreshes the snapshot on
     * success, so the flows above update immediately.
     */
    suspend fun removeQueueItem(
        source: PvrSource,
        queueItemId: Int,
        removeFromClient: Boolean,
        blocklist: Boolean,
    ): Result<Unit>

    /**
     * Bulk version of [removeQueueItem] - e.g. "clear all pending downloads" from the Downloads
     * screen. Each removal is attempted independently (Sonarr/Radarr's v3 API has no bulk
     * queue-delete endpoint), and a failure on one entry doesn't stop the rest. Returns the
     * (source, queueItemId) pairs that failed, so the caller can report how many of the requested
     * removals actually succeeded.
     */
    suspend fun removeQueueItems(
        items: List<Pair<PvrSource, Int>>,
        removeFromClient: Boolean,
        blocklist: Boolean,
    ): List<Pair<PvrSource, Int>>

    /**
     * Lists the individual files inside a download Sonarr/Radarr couldn't fully auto-import (see
     * [ManualImportCandidate]), for the "manage imports" review UI.
     */
    suspend fun getManualImportCandidates(
        source: PvrSource,
        downloadId: String,
    ): Result<List<ManualImportCandidate>>

    /**
     * Imports the files in [selectedIds] (as returned by [getManualImportCandidates]), using
     * Sonarr/Radarr's own guessed mapping for each - re-fetches the candidates internally to
     * rebuild the full request rather than trusting a caller-held copy that may be stale.
     */
    suspend fun performManualImport(
        source: PvrSource,
        downloadId: String,
        selectedIds: Set<Int>,
    ): Result<Unit>
}
