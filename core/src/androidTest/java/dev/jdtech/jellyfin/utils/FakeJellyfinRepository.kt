package dev.jdtech.jellyfin.utils

import androidx.paging.PagingData
import dev.jdtech.jellyfin.models.FindroidCollection
import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.FindroidMovie
import dev.jdtech.jellyfin.models.FindroidPerson
import dev.jdtech.jellyfin.models.FindroidSeason
import dev.jdtech.jellyfin.models.FindroidSegment
import dev.jdtech.jellyfin.models.FindroidShow
import dev.jdtech.jellyfin.models.FindroidSource
import dev.jdtech.jellyfin.models.SortBy
import dev.jdtech.jellyfin.models.SortOrder
import dev.jdtech.jellyfin.repository.JellyfinRepository
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.PublicSystemInfo
import org.jellyfin.sdk.model.api.UserConfiguration

/** Minimal test double covering only what [AutoDownloadRuleEvaluator] exercises. */
class FakeJellyfinRepository(
    private val seasons: List<FindroidSeason> = emptyList(),
    private val episodesBySeasonId: Map<UUID, List<FindroidEpisode>> = emptyMap(),
    private val userId: UUID = UUID.randomUUID(),
) : JellyfinRepository {
    override suspend fun getSeasons(seriesId: UUID, offline: Boolean): List<FindroidSeason> = seasons

    override suspend fun getSeason(itemId: UUID): FindroidSeason =
        seasons.first { it.id == itemId }

    override suspend fun getEpisodes(
        seriesId: UUID,
        seasonId: UUID,
        fields: List<ItemFields>?,
        startItemId: UUID?,
        limit: Int?,
        offline: Boolean,
    ): List<FindroidEpisode> = episodesBySeasonId[seasonId].orEmpty()

    override fun getUserId(): UUID = userId

    override suspend fun getPublicSystemInfo(): PublicSystemInfo = error("not used in test")

    override suspend fun getUserViews(): List<BaseItemDto> = error("not used in test")

    override suspend fun getEpisode(itemId: UUID): FindroidEpisode = error("not used in test")

    override suspend fun getMovie(itemId: UUID): FindroidMovie = error("not used in test")

    override suspend fun getShow(itemId: UUID): FindroidShow = error("not used in test")

    override suspend fun getLibraries(): List<FindroidCollection> = error("not used in test")

    override suspend fun getItem(itemId: UUID): FindroidItem? = error("not used in test")

    override suspend fun getItems(
        parentId: UUID?,
        includeTypes: List<BaseItemKind>?,
        recursive: Boolean,
        sortBy: SortBy,
        sortOrder: SortOrder,
        startIndex: Int?,
        limit: Int?,
    ): List<FindroidItem> = error("not used in test")

    override suspend fun getItemsPaging(
        parentId: UUID?,
        includeTypes: List<BaseItemKind>?,
        recursive: Boolean,
        sortBy: SortBy,
        sortOrder: SortOrder,
    ): Flow<PagingData<FindroidItem>> = error("not used in test")

    override suspend fun getPerson(personId: UUID): FindroidPerson = error("not used in test")

    override suspend fun getPersonItems(
        personIds: List<UUID>,
        includeTypes: List<BaseItemKind>?,
        recursive: Boolean,
    ): List<FindroidItem> = error("not used in test")

    override suspend fun getFavoriteItems(): List<FindroidItem> = error("not used in test")

    override suspend fun getSearchItems(query: String): List<FindroidItem> = error("not used in test")

    override suspend fun getSuggestions(): List<FindroidItem> = error("not used in test")

    override suspend fun getResumeItems(): List<FindroidItem> = error("not used in test")

    override suspend fun getLatestMedia(parentId: UUID): List<FindroidItem> = error("not used in test")

    override suspend fun getNextUp(seriesId: UUID?): List<FindroidEpisode> = error("not used in test")

    override suspend fun getMediaSources(
        itemId: UUID,
        includePath: Boolean,
    ): List<FindroidSource> = error("not used in test")

    override suspend fun getStreamUrl(itemId: UUID, mediaSourceId: String): String =
        error("not used in test")

    override suspend fun getSegments(itemId: UUID): List<FindroidSegment> = error("not used in test")

    override suspend fun getTrickplayData(itemId: UUID, width: Int, index: Int): ByteArray? =
        error("not used in test")

    override suspend fun postCapabilities() = error("not used in test")

    override suspend fun postPlaybackStart(itemId: UUID) = error("not used in test")

    override suspend fun postPlaybackStop(itemId: UUID, positionTicks: Long, playedPercentage: Int) =
        error("not used in test")

    override suspend fun postPlaybackProgress(itemId: UUID, positionTicks: Long, isPaused: Boolean) =
        error("not used in test")

    override suspend fun markAsFavorite(itemId: UUID) = error("not used in test")

    override suspend fun unmarkAsFavorite(itemId: UUID) = error("not used in test")

    override suspend fun markAsPlayed(itemId: UUID) = error("not used in test")

    override suspend fun markAsUnplayed(itemId: UUID) = error("not used in test")

    override fun getBaseUrl(): String = error("not used in test")

    override suspend fun updateDeviceName(name: String) = error("not used in test")

    override suspend fun getUserConfiguration(): UserConfiguration? = error("not used in test")

    override suspend fun getDownloads(): List<FindroidItem> = error("not used in test")
}
