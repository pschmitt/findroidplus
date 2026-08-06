package dev.pschmitt.jellyfin.models

import dev.pschmitt.jellyfin.repository.JellyfinRepository
import java.util.UUID
import org.jellyfin.sdk.model.api.BaseItemDto

data class JollyfinPerson(
    val id: UUID,
    val name: String,
    val overview: String,
    val images: JollyfinImages,
)

fun BaseItemDto.toJollyfinPerson(repository: JellyfinRepository): JollyfinPerson {
    return JollyfinPerson(
        id = id,
        name = name.orEmpty(),
        overview = overview.orEmpty(),
        images = toJollyfinImages(repository),
    )
}
