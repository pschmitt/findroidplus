package dev.pschmitt.jellyfin.repository

import android.content.Context
import androidx.paging.PagingData
import dev.pschmitt.jellyfin.api.JellyfinApi
import dev.pschmitt.jellyfin.database.ServerDatabaseDao
import dev.pschmitt.jellyfin.models.FindroidCollection
import dev.pschmitt.jellyfin.models.FindroidEpisode
import dev.pschmitt.jellyfin.models.FindroidItem
import dev.pschmitt.jellyfin.models.FindroidMovie
import dev.pschmitt.jellyfin.models.FindroidPerson
import dev.pschmitt.jellyfin.models.FindroidSeason
import dev.pschmitt.jellyfin.models.FindroidSegment
import dev.pschmitt.jellyfin.models.FindroidShow
import dev.pschmitt.jellyfin.models.FindroidSource
import dev.pschmitt.jellyfin.models.SortBy
import dev.pschmitt.jellyfin.models.SortOrder
import dev.pschmitt.jellyfin.models.toFindroidEpisode
import dev.pschmitt.jellyfin.models.toFindroidMovie
import dev.pschmitt.jellyfin.models.toFindroidSeason
import dev.pschmitt.jellyfin.models.toFindroidSegment
import dev.pschmitt.jellyfin.models.toFindroidShow
import dev.pschmitt.jellyfin.models.toFindroidSource
import dev.pschmitt.jellyfin.settings.domain.AppPreferences
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.model.DateTime
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.DisplayPreferencesDto
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.PublicSystemInfo
import org.jellyfin.sdk.model.api.UserConfiguration

class JellyfinRepositoryOfflineImpl(
    private val context: Context,
    private val jellyfinApi: JellyfinApi,
    private val database: ServerDatabaseDao,
    private val appPreferences: AppPreferences,
) : JellyfinRepository {

    override suspend fun getPublicSystemInfo(): PublicSystemInfo {
        throw Exception("System info not available in offline mode")
    }

    override suspend fun getUserViews(): List<BaseItemDto> {
        return emptyList()
    }

    override suspend fun getMovie(itemId: UUID): FindroidMovie =
        withContext(Dispatchers.IO) {
            database.getMovie(itemId).toFindroidMovie(database, jellyfinApi.userId!!)
        }

    override suspend fun getShow(itemId: UUID): FindroidShow =
        withContext(Dispatchers.IO) {
            database.getShow(itemId).toFindroidShow(database, jellyfinApi.userId!!)
        }

    override suspend fun getSeason(itemId: UUID): FindroidSeason =
        withContext(Dispatchers.IO) {
            database.getSeason(itemId).toFindroidSeason(database, jellyfinApi.userId!!)
        }

    override suspend fun getEpisode(itemId: UUID): FindroidEpisode =
        withContext(Dispatchers.IO) {
            database.getEpisode(itemId).toFindroidEpisode(database, jellyfinApi.userId!!)
        }

    override suspend fun getLibraries(): List<FindroidCollection> {
        return emptyList()
    }

    override suspend fun getItem(itemId: UUID): FindroidItem? {
        return null
    }

    override suspend fun getItems(
        parentId: UUID?,
        includeTypes: List<BaseItemKind>?,
        recursive: Boolean,
        sortBy: SortBy,
        sortOrder: SortOrder,
        startIndex: Int?,
        limit: Int?,
        searchTerm: String?,
    ): List<FindroidItem> {
        return emptyList()
    }

    override suspend fun getItemsPaging(
        parentId: UUID?,
        includeTypes: List<BaseItemKind>?,
        recursive: Boolean,
        sortBy: SortBy,
        sortOrder: SortOrder,
        searchTerm: String?,
    ): Flow<PagingData<FindroidItem>> {
        TODO("Not yet implemented")
    }

    override suspend fun getPerson(personId: UUID): FindroidPerson {
        TODO("Not yet implemented")
    }

    override suspend fun getPersonItems(
        personIds: List<UUID>,
        includeTypes: List<BaseItemKind>?,
        recursive: Boolean,
    ): List<FindroidItem> {
        TODO("Not yet implemented")
    }

    override suspend fun getFavoriteItems(): List<FindroidItem> {
        TODO("Not yet implemented")
    }

    override suspend fun getSearchItems(query: String): List<FindroidItem> {
        return withContext(Dispatchers.IO) {
            val movies =
                database
                    .searchMovies(appPreferences.getValue(appPreferences.currentServer)!!, query)
                    .map { it.toFindroidMovie(database, jellyfinApi.userId!!) }
            val shows =
                database
                    .searchShows(appPreferences.getValue(appPreferences.currentServer)!!, query)
                    .map { it.toFindroidShow(database, jellyfinApi.userId!!) }
            val episodes =
                database
                    .searchEpisodes(appPreferences.getValue(appPreferences.currentServer)!!, query)
                    .map { it.toFindroidEpisode(database, jellyfinApi.userId!!) }
            movies + shows + episodes
        }
    }

    override suspend fun getSuggestions(): List<FindroidItem> {
        return emptyList()
    }

    override suspend fun getResumeItems(): List<FindroidItem> {
        return withContext(Dispatchers.IO) {
            val movies =
                database
                    .getMoviesByServerId(appPreferences.getValue(appPreferences.currentServer)!!)
                    .map { it.toFindroidMovie(database, jellyfinApi.userId!!) }
                    .filter { it.playbackPositionTicks > 0 }
            val episodes =
                database
                    .getEpisodesByServerId(appPreferences.getValue(appPreferences.currentServer)!!)
                    .map { it.toFindroidEpisode(database, jellyfinApi.userId!!) }
                    .filter { it.playbackPositionTicks > 0 }
            movies + episodes
        }
    }

    override suspend fun getLatestMedia(parentId: UUID): List<FindroidItem> {
        return emptyList()
    }

    override suspend fun getSeasons(seriesId: UUID, offline: Boolean): List<FindroidSeason> =
        withContext(Dispatchers.IO) {
            database.getSeasonsByShowId(seriesId).map {
                it.toFindroidSeason(database, jellyfinApi.userId!!)
            }
        }

    override suspend fun getNextUp(seriesId: UUID?): List<FindroidEpisode> {
        return withContext(Dispatchers.IO) {
            val result = mutableListOf<FindroidEpisode>()
            val shows =
                database
                    .getShowsByServerId(appPreferences.getValue(appPreferences.currentServer)!!)
                    .filter { if (seriesId != null) it.id == seriesId else true }
            for (show in shows) {
                val episodes =
                    database.getEpisodesByShowId(show.id).map {
                        it.toFindroidEpisode(database, jellyfinApi.userId!!)
                    }
                val indexOfLastPlayed = episodes.indexOfLast { it.played }
                if (indexOfLastPlayed == -1) {
                    result.add(episodes.first())
                } else {
                    episodes.getOrNull(indexOfLastPlayed + 1)?.let { result.add(it) }
                }
            }
            result.filter { it.playbackPositionTicks == 0L }
        }
    }

    override suspend fun getEpisodes(
        seriesId: UUID,
        seasonId: UUID,
        fields: List<ItemFields>?,
        startItemId: UUID?,
        limit: Int?,
        offline: Boolean,
    ): List<FindroidEpisode> =
        withContext(Dispatchers.IO) {
            val items =
                database.getEpisodesBySeasonId(seasonId).map {
                    it.toFindroidEpisode(database, jellyfinApi.userId!!)
                }
            if (startItemId != null) return@withContext items.dropWhile { it.id != startItemId }
            items
        }

    override suspend fun getMediaSources(itemId: UUID, includePath: Boolean): List<FindroidSource> =
        withContext(Dispatchers.IO) {
            database.getSources(itemId).map { it.toFindroidSource(database) }
        }

    override suspend fun getStreamUrl(itemId: UUID, mediaSourceId: String): String {
        TODO("Not yet implemented")
    }

    override suspend fun getSegments(itemId: UUID): List<FindroidSegment> =
        withContext(Dispatchers.IO) { database.getSegments(itemId).map { it.toFindroidSegment() } }

    override suspend fun getTrickplayData(itemId: UUID, width: Int, index: Int): ByteArray? =
        withContext(Dispatchers.IO) {
            try {
                val sources =
                    File(context.filesDir, "trickplay/$itemId").listFiles()
                        ?: return@withContext null
                File(sources.first(), index.toString()).readBytes()
            } catch (_: Exception) {
                null
            }
        }

    override suspend fun postCapabilities() {}

    override suspend fun postPlaybackStart(itemId: UUID) {}

    override suspend fun postPlaybackStop(
        itemId: UUID,
        positionTicks: Long,
        playedPercentage: Int,
    ) {
        withContext(Dispatchers.IO) {
            when {
                playedPercentage < 10 -> {
                    database.setPlaybackPositionTicks(itemId, jellyfinApi.userId!!, 0)
                    database.setPlayed(jellyfinApi.userId!!, itemId, false)
                    database.setLastPlayedDate(jellyfinApi.userId!!, itemId, null)
                }
                playedPercentage > 90 -> {
                    database.setPlaybackPositionTicks(itemId, jellyfinApi.userId!!, 0)
                    database.setPlayed(jellyfinApi.userId!!, itemId, true)
                    database.setLastPlayedDate(jellyfinApi.userId!!, itemId, DateTime.now())
                }
                else -> {
                    database.setPlaybackPositionTicks(itemId, jellyfinApi.userId!!, positionTicks)
                    database.setPlayed(jellyfinApi.userId!!, itemId, false)
                    database.setLastPlayedDate(jellyfinApi.userId!!, itemId, null)
                }
            }
            database.setUserDataToBeSynced(jellyfinApi.userId!!, itemId, true)
        }
    }

    override suspend fun postPlaybackProgress(
        itemId: UUID,
        positionTicks: Long,
        isPaused: Boolean,
    ) {
        withContext(Dispatchers.IO) {
            database.setPlaybackPositionTicks(itemId, jellyfinApi.userId!!, positionTicks)
            database.setUserDataToBeSynced(jellyfinApi.userId!!, itemId, true)
        }
    }

    override suspend fun markAsFavorite(itemId: UUID) {
        withContext(Dispatchers.IO) {
            database.setFavorite(jellyfinApi.userId!!, itemId, true)
            database.setUserDataToBeSynced(jellyfinApi.userId!!, itemId, true)
        }
    }

    override suspend fun unmarkAsFavorite(itemId: UUID) {
        withContext(Dispatchers.IO) {
            database.setFavorite(jellyfinApi.userId!!, itemId, false)
            database.setUserDataToBeSynced(jellyfinApi.userId!!, itemId, true)
        }
    }

    override suspend fun markAsPlayed(itemId: UUID) {
        withContext(Dispatchers.IO) {
            database.setPlayed(jellyfinApi.userId!!, itemId, true)
            database.setLastPlayedDate(jellyfinApi.userId!!, itemId, DateTime.now())
            database.setPlaybackPositionTicks(itemId, jellyfinApi.userId!!, 0)
            database.setUserDataToBeSynced(jellyfinApi.userId!!, itemId, true)
        }
    }

    override suspend fun markAsUnplayed(itemId: UUID) {
        withContext(Dispatchers.IO) {
            database.setPlayed(jellyfinApi.userId!!, itemId, false)
            database.setLastPlayedDate(jellyfinApi.userId!!, itemId, null)
            database.setUserDataToBeSynced(jellyfinApi.userId!!, itemId, true)
        }
    }

    // Unlike markAsFavorite/markAsPlayed above, there's no "set a local flag and sync later"
    // equivalent for a delete - it must actually reach the server.
    override suspend fun deleteItem(itemId: UUID) {
        throw Exception("Deleting an item is not available in offline mode")
    }

    // Nothing to scan without a server - best-effort/fire-and-forget by design, so a no-op here
    // rather than throwing.
    override suspend fun refreshLibrary() {}

    override suspend fun canDeleteMedia(): Boolean = false

    override fun getBaseUrl(): String {
        return ""
    }

    override fun getAccessToken(): String? = null

    override suspend fun updateDeviceName(name: String) {
        TODO("Not yet implemented")
    }

    override suspend fun getUserConfiguration(): UserConfiguration? {
        return null
    }

    override suspend fun getDownloads(): List<FindroidItem> =
        withContext(Dispatchers.IO) {
            val items = mutableListOf<FindroidItem>()
            items.addAll(
                database
                    .getMoviesByServerId(appPreferences.getValue(appPreferences.currentServer)!!)
                    .map { it.toFindroidMovie(database, jellyfinApi.userId!!) }
            )
            items.addAll(
                database
                    .getShowsByServerId(appPreferences.getValue(appPreferences.currentServer)!!)
                    .map { it.toFindroidShow(database, jellyfinApi.userId!!) }
            )
            items
        }

    override fun getUserId(): UUID {
        return jellyfinApi.userId!!
    }

    override suspend fun getDisplayPreferences(
        displayPreferencesId: String,
        client: String,
    ): DisplayPreferencesDto {
        throw Exception("Remote config is not available in offline mode")
    }

    override suspend fun updateDisplayPreferences(
        displayPreferencesId: String,
        client: String,
        data: DisplayPreferencesDto,
    ) {
        throw Exception("Remote config is not available in offline mode")
    }
}
