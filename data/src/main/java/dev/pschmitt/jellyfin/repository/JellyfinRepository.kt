package dev.pschmitt.jellyfin.repository

import androidx.paging.PagingData
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
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.DisplayPreferencesDto
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.PublicSystemInfo
import org.jellyfin.sdk.model.api.UserConfiguration

interface JellyfinRepository {
    suspend fun getPublicSystemInfo(): PublicSystemInfo

    suspend fun getUserViews(): List<BaseItemDto>

    suspend fun getEpisode(itemId: UUID): FindroidEpisode

    suspend fun getMovie(itemId: UUID): FindroidMovie

    suspend fun getShow(itemId: UUID): FindroidShow

    suspend fun getSeason(itemId: UUID): FindroidSeason

    suspend fun getLibraries(): List<FindroidCollection>

    suspend fun getItem(itemId: UUID): FindroidItem?

    suspend fun getItems(
        parentId: UUID? = null,
        includeTypes: List<BaseItemKind>? = null,
        recursive: Boolean = false,
        sortBy: SortBy = SortBy.defaultValue,
        sortOrder: SortOrder = SortOrder.ASCENDING,
        startIndex: Int? = null,
        limit: Int? = null,
        searchTerm: String? = null,
    ): List<FindroidItem>

    suspend fun getItemsPaging(
        parentId: UUID? = null,
        includeTypes: List<BaseItemKind>? = null,
        recursive: Boolean = false,
        sortBy: SortBy = SortBy.defaultValue,
        sortOrder: SortOrder = SortOrder.ASCENDING,
        searchTerm: String? = null,
    ): Flow<PagingData<FindroidItem>>

    suspend fun getPerson(personId: UUID): FindroidPerson

    suspend fun getPersonItems(
        personIds: List<UUID>,
        includeTypes: List<BaseItemKind>? = null,
        recursive: Boolean = true,
    ): List<FindroidItem>

    suspend fun getFavoriteItems(): List<FindroidItem>

    suspend fun getSearchItems(query: String): List<FindroidItem>

    suspend fun getSuggestions(): List<FindroidItem>

    suspend fun getResumeItems(): List<FindroidItem>

    suspend fun getLatestMedia(parentId: UUID): List<FindroidItem>

    suspend fun getSeasons(seriesId: UUID, offline: Boolean = false): List<FindroidSeason>

    suspend fun getNextUp(seriesId: UUID? = null): List<FindroidEpisode>

    suspend fun getEpisodes(
        seriesId: UUID,
        seasonId: UUID,
        fields: List<ItemFields>? = null,
        startItemId: UUID? = null,
        limit: Int? = null,
        offline: Boolean = false,
    ): List<FindroidEpisode>

    suspend fun getMediaSources(itemId: UUID, includePath: Boolean = false): List<FindroidSource>

    suspend fun getStreamUrl(itemId: UUID, mediaSourceId: String): String

    suspend fun getSegments(itemId: UUID): List<FindroidSegment>

    suspend fun getTrickplayData(itemId: UUID, width: Int, index: Int): ByteArray?

    suspend fun postCapabilities()

    suspend fun postPlaybackStart(itemId: UUID)

    suspend fun postPlaybackStop(itemId: UUID, positionTicks: Long, playedPercentage: Int)

    suspend fun postPlaybackProgress(itemId: UUID, positionTicks: Long, isPaused: Boolean)

    suspend fun markAsFavorite(itemId: UUID)

    suspend fun unmarkAsFavorite(itemId: UUID)

    suspend fun markAsPlayed(itemId: UUID)

    suspend fun markAsUnplayed(itemId: UUID)

    // Unlike markAsFavorite/markAsPlayed, this is not offline-tolerant: a delete has no sensible
    // "retry later" semantics, so a failure here must propagate to the caller rather than being
    // swallowed into a background sync flag.
    suspend fun deleteItem(itemId: UUID)

    /**
     * Kicks off a full library scan (Jellyfin's own "Scan All Libraries" task) - used after a
     * Sonarr/Radarr manual import finishes so the newly-placed file shows up without waiting for
     * Jellyfin's own scheduled scan. Fire-and-forget from the server's point of view; this just
     * requests the scan, it doesn't wait for it to finish.
     */
    suspend fun refreshLibrary()

    /**
     * Whether the current Jellyfin user's server-side policy allows deleting media at all ("Allow
     * this user to delete media" in Jellyfin's admin UI) - gates whether the "Delete from Jellyfin"
     * action is even shown, rather than offering it and having [deleteItem] fail with a permissions
     * error.
     */
    suspend fun canDeleteMedia(): Boolean

    fun getBaseUrl(): String

    /**
     * The current user's raw Jellyfin access token - only needed for the local control API's
     * debug-proxy endpoint (`core/.../localcontrol/LocalControlRouter.kt`), which forwards ad hoc
     * requests using the app's own already-stored credentials rather than exposing them to the
     * caller.
     */
    fun getAccessToken(): String?

    suspend fun updateDeviceName(name: String)

    suspend fun getUserConfiguration(): UserConfiguration?

    suspend fun getDownloads(): List<FindroidItem>

    fun getUserId(): UUID

    /**
     * Reads the shared per-user [DisplayPreferencesDto] bucket identified by
     * [displayPreferencesId]/[client] - used as a zero-infrastructure transport for cross-device
     * remote config (see [dev.pschmitt.jellyfin.repository.RemoteConfigRepository]), since every
     * instance already talks to this same Jellyfin account continuously.
     */
    suspend fun getDisplayPreferences(
        displayPreferencesId: String,
        client: String,
    ): DisplayPreferencesDto

    /** Writes back a [DisplayPreferencesDto] previously obtained from [getDisplayPreferences]. */
    suspend fun updateDisplayPreferences(
        displayPreferencesId: String,
        client: String,
        data: DisplayPreferencesDto,
    )
}
