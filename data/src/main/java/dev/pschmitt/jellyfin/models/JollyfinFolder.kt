package dev.pschmitt.jellyfin.models

import dev.pschmitt.jellyfin.repository.JellyfinRepository
import java.util.UUID
import org.jellyfin.sdk.model.DateTime
import org.jellyfin.sdk.model.api.BaseItemDto

data class JollyfinFolder(
    override val id: UUID,
    override val name: String,
    override val originalTitle: String? = null,
    override val overview: String = "",
    override val played: Boolean,
    override val favorite: Boolean,
    override val canPlay: Boolean = false,
    override val canDownload: Boolean = false,
    override val sources: List<JollyfinSource> = emptyList(),
    override val runtimeTicks: Long = 0L,
    override val playbackPositionTicks: Long = 0L,
    override val unplayedItemCount: Int?,
    override val images: JollyfinImages,
    override val chapters: List<JollyfinChapter> = emptyList(),
    override val dateCreated: DateTime? = null,
) : JollyfinItem

fun BaseItemDto.toJollyfinFolder(jellyfinRepository: JellyfinRepository): JollyfinFolder {
    return JollyfinFolder(
        id = id,
        name = name.orEmpty(),
        played = userData?.played == true,
        favorite = userData?.isFavorite == true,
        unplayedItemCount = userData?.unplayedItemCount,
        images = toJollyfinImages(jellyfinRepository),
    )
}
