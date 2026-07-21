package dev.jdtech.jellyfin.models

import dev.jdtech.jellyfin.database.ServerDatabaseDao
import dev.jdtech.jellyfin.repository.JellyfinRepository
import java.io.File
import java.util.UUID
import org.jellyfin.sdk.model.api.MediaProtocol
import org.jellyfin.sdk.model.api.MediaSourceInfo

data class FindroidSource(
    val id: String,
    val name: String,
    val type: FindroidSourceType,
    val path: String,
    val size: Long,
    val mediaStreams: List<FindroidMediaStream>,
    val downloadId: Long? = null,
    val checksum: String? = null,
    val excludeFromAutoDelete: Boolean = false,
)

suspend fun MediaSourceInfo.toFindroidSource(
    jellyfinRepository: JellyfinRepository,
    itemId: UUID,
    includePath: Boolean = false,
): FindroidSource {
    val path =
        when (protocol) {
            MediaProtocol.FILE -> {
                try {
                    if (includePath) jellyfinRepository.getStreamUrl(itemId, id.orEmpty()) else ""
                } catch (e: Exception) {
                    ""
                }
            }
            MediaProtocol.HTTP -> this.path.orEmpty()
            else -> ""
        }
    return FindroidSource(
        id = id.orEmpty(),
        name = name.orEmpty(),
        type = FindroidSourceType.REMOTE,
        path = path,
        size = size ?: 0,
        mediaStreams =
            mediaStreams?.map { it.toFindroidMediaStream(jellyfinRepository) } ?: emptyList(),
    )
}

fun FindroidSourceDto.toFindroidSource(serverDatabaseDao: ServerDatabaseDao): FindroidSource {
    return toFindroidSource(serverDatabaseDao.getMediaStreamsBySourceId(id))
}

/**
 * Same mapping as [toFindroidSource], but takes an already-fetched media-stream list instead of
 * doing its own DB query - lets batch callers (see `toFindroidMovies`/`toFindroidEpisodes`) fetch
 * media streams for every source in one query instead of one query per source.
 */
fun FindroidSourceDto.toFindroidSource(mediaStreams: List<FindroidMediaStreamDto>): FindroidSource {
    return FindroidSource(
        id = id,
        name = name,
        type = type,
        path = path,
        size = File(path).length(),
        mediaStreams = mediaStreams.map { it.toFindroidMediaStream() },
        downloadId = downloadId,
        checksum = checksum,
        excludeFromAutoDelete = excludeFromAutoDelete,
    )
}

enum class FindroidSourceType {
    REMOTE,
    LOCAL,
}
