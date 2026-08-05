package dev.pschmitt.jellyfin.repository

import dev.pschmitt.jellyfin.database.ServerDatabaseDao
import dev.pschmitt.jellyfin.models.PendingDownloadRequestDto
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PendingDownloadRequestRepositoryImpl(private val database: ServerDatabaseDao) :
    PendingDownloadRequestRepository {

    override suspend fun queue(
        serverId: String,
        userId: UUID,
        seriesId: UUID,
        seasonNumber: Int,
        episodeNumber: Int?,
        sonarrEpisodeId: Int?,
    ): PendingDownloadRequestDto =
        withContext(Dispatchers.IO) {
            database.getPendingDownloadRequest(
                serverId,
                userId,
                seriesId,
                seasonNumber,
                episodeNumber,
            )
                ?: run {
                    val request =
                        PendingDownloadRequestDto(
                            serverId = serverId,
                            userId = userId,
                            seriesId = seriesId,
                            seasonNumber = seasonNumber,
                            episodeNumber = episodeNumber,
                            sonarrEpisodeId = sonarrEpisodeId,
                            requestedAt = System.currentTimeMillis(),
                        )
                    val id = database.insertPendingDownloadRequest(request)
                    request.copy(id = id)
                }
        }

    override suspend fun cancel(
        serverId: String,
        userId: UUID,
        seriesId: UUID,
        seasonNumber: Int,
        episodeNumber: Int?,
    ) =
        withContext(Dispatchers.IO) {
            database
                .getPendingDownloadRequest(serverId, userId, seriesId, seasonNumber, episodeNumber)
                ?.let { database.deletePendingDownloadRequest(it.id) }
            Unit
        }

    override suspend fun isQueued(
        serverId: String,
        userId: UUID,
        seriesId: UUID,
        seasonNumber: Int,
        episodeNumber: Int?,
    ): Boolean =
        withContext(Dispatchers.IO) {
            database.getPendingDownloadRequest(
                serverId,
                userId,
                seriesId,
                seasonNumber,
                episodeNumber,
            ) != null
        }

    override suspend fun getQueuedForSeries(
        serverId: String,
        userId: UUID,
        seriesId: UUID,
    ): List<PendingDownloadRequestDto> =
        withContext(Dispatchers.IO) {
            database.getPendingDownloadRequestsForSeries(serverId, userId, seriesId)
        }

    override suspend fun getAll(serverId: String, userId: UUID): List<PendingDownloadRequestDto> =
        withContext(Dispatchers.IO) { database.getPendingDownloadRequests(serverId, userId) }

    override suspend fun deleteById(id: Long) =
        withContext(Dispatchers.IO) { database.deletePendingDownloadRequest(id) }
}
