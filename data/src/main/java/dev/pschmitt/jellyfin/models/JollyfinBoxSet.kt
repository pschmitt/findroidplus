package dev.pschmitt.jellyfin.models

import dev.pschmitt.jellyfin.repository.JellyfinRepository
import java.util.UUID
import org.jellyfin.sdk.model.DateTime
import org.jellyfin.sdk.model.api.BaseItemDto

data class JollyfinBoxSet(
    override val id: UUID,
    override val name: String,
    override val originalTitle: String? = null,
    override val overview: String = "",
    override val played: Boolean = false,
    override val favorite: Boolean = false,
    override val canPlay: Boolean = false,
    override val canDownload: Boolean = false,
    override val sources: List<JollyfinSource> = emptyList(),
    override val runtimeTicks: Long = 0L,
    override val playbackPositionTicks: Long = 0L,
    override val unplayedItemCount: Int? = null,
    override val images: JollyfinImages,
    override val chapters: List<JollyfinChapter> = emptyList(),
    override val dateCreated: DateTime? = null,
) : JollyfinItem

fun BaseItemDto.toJollyfinBoxSet(jellyfinRepository: JellyfinRepository): JollyfinBoxSet {
    return JollyfinBoxSet(
        id = id,
        name = name.orEmpty(),
        images = toJollyfinImages(jellyfinRepository),
    )
}
