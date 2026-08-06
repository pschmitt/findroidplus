package dev.pschmitt.jellyfin.models

import android.net.Uri
import dev.pschmitt.jellyfin.repository.JellyfinRepository
import java.util.UUID
import org.jellyfin.sdk.model.api.BaseItemPerson
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.PersonKind

data class JollyfinItemPersonImage(val uri: Uri?, val blurHash: String?)

fun BaseItemPerson.toJollyfinImage(repository: JellyfinRepository): JollyfinItemPersonImage {
    val baseUrl = Uri.parse(repository.getBaseUrl())
    return JollyfinItemPersonImage(
        uri =
            primaryImageTag?.let { tag ->
                baseUrl
                    .buildUpon()
                    .appendEncodedPath("items/$id/Images/${ImageType.PRIMARY}")
                    .appendQueryParameter("tag", tag)
                    .build()
            },
        blurHash = imageBlurHashes?.get(ImageType.PRIMARY)?.get(primaryImageTag),
    )
}

data class JollyfinItemPerson(
    val id: UUID,
    val name: String,
    val type: PersonKind,
    val role: String,
    val image: JollyfinItemPersonImage,
)

fun BaseItemPerson.toJollyfinPerson(repository: JellyfinRepository): JollyfinItemPerson {
    return JollyfinItemPerson(
        id = id,
        name = name.orEmpty(),
        type = type,
        role = role.orEmpty(),
        image = toJollyfinImage(repository),
    )
}
