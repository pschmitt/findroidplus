package dev.pschmitt.jellyfin.models

import dev.pschmitt.jellyfin.database.ServerDatabaseDao
import dev.pschmitt.jellyfin.repository.JellyfinRepository
import java.util.UUID
import org.jellyfin.sdk.model.DateTime
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.PlayAccess

data class JollyfinSeason(
    override val id: UUID,
    override val name: String,
    val seriesId: UUID,
    val seriesName: String,
    override val originalTitle: String?,
    override val overview: String,
    override val sources: List<JollyfinSource>,
    val indexNumber: Int,
    val episodes: Collection<JollyfinEpisode>,
    override val played: Boolean,
    override val favorite: Boolean,
    override val canPlay: Boolean,
    override val canDownload: Boolean,
    override val runtimeTicks: Long = 0L,
    override val playbackPositionTicks: Long = 0L,
    override val unplayedItemCount: Int?,
    override val images: JollyfinImages,
    override val chapters: List<JollyfinChapter> = emptyList(),
    override val dateCreated: DateTime? = null,
) : JollyfinItem

fun BaseItemDto.toJollyfinSeason(jellyfinRepository: JellyfinRepository): JollyfinSeason {
    return JollyfinSeason(
        id = id,
        name = name.orEmpty(),
        originalTitle = originalTitle,
        overview = overview.orEmpty(),
        played = userData?.played == true,
        favorite = userData?.isFavorite == true,
        canPlay = playAccess != PlayAccess.NONE,
        canDownload = canDownload == true,
        unplayedItemCount = userData?.unplayedItemCount,
        indexNumber = indexNumber ?: 0,
        sources = emptyList(),
        episodes = emptyList(),
        seriesId = seriesId!!,
        seriesName = seriesName.orEmpty(),
        images = toJollyfinImages(jellyfinRepository),
    )
}

fun JollyfinSeasonDto.toJollyfinSeason(database: ServerDatabaseDao, userId: UUID): JollyfinSeason {
    val userData = database.getUserDataOrCreateNew(id, userId)
    return JollyfinSeason(
        id = id,
        name = name,
        originalTitle = null,
        overview = overview,
        played = userData.played,
        favorite = userData.favorite,
        canPlay = true,
        canDownload = false,
        unplayedItemCount = null,
        indexNumber = indexNumber,
        sources = emptyList(),
        episodes = emptyList(),
        seriesId = seriesId,
        seriesName = seriesName,
        images = toLocalJollyfinImages(itemId = id),
    )
}
