package dev.pschmitt.jellyfin.models

import kotlinx.serialization.Serializable
import org.jellyfin.sdk.model.api.BaseItemDto

@Serializable
data class JollyfinChapter(
    /** The start position. */
    val startPosition: Long,
    /** The name. */
    val name: String? = null,
)

fun BaseItemDto.toJollyfinChapters(): List<JollyfinChapter> {
    return chapters?.map { chapter ->
        JollyfinChapter(startPosition = chapter.startPositionTicks / 10000, name = chapter.name)
    } ?: emptyList()
}
