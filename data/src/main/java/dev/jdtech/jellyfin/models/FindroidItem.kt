package dev.jdtech.jellyfin.models

import dev.jdtech.jellyfin.database.ServerDatabaseDao
import dev.jdtech.jellyfin.repository.JellyfinRepository
import java.util.UUID
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind

interface FindroidItem {
    val id: UUID
    val name: String
    val originalTitle: String?
    val overview: String
    val played: Boolean
    val favorite: Boolean
    val canPlay: Boolean
    val canDownload: Boolean
    val sources: List<FindroidSource>
    val runtimeTicks: Long
    val playbackPositionTicks: Long
    val unplayedItemCount: Int?
    val images: FindroidImages
    val chapters: List<FindroidChapter>
}

suspend fun BaseItemDto.toFindroidItem(
    jellyfinRepository: JellyfinRepository,
    serverDatabase: ServerDatabaseDao? = null,
): FindroidItem? {
    return when (type) {
        BaseItemKind.MOVIE -> toFindroidMovie(jellyfinRepository, serverDatabase)
        BaseItemKind.EPISODE -> toFindroidEpisode(jellyfinRepository)
        BaseItemKind.SEASON -> toFindroidSeason(jellyfinRepository)
        BaseItemKind.SERIES -> toFindroidShow(jellyfinRepository)
        BaseItemKind.BOX_SET -> toFindroidBoxSet(jellyfinRepository)
        BaseItemKind.FOLDER -> toFindroidFolder(jellyfinRepository)
        else -> null
    }
}

fun FindroidItem.isDownloading(): Boolean {
    return sources
        .filter { it.type == FindroidSourceType.LOCAL }
        .any { it.path.endsWith(".download") }
}

fun FindroidItem.isDownloaded(): Boolean {
    return sources
        .filter { it.type == FindroidSourceType.LOCAL }
        .any { !it.path.endsWith(".download") }
}

/**
 * A completed local download whose file is actually missing or empty on disk right now - e.g.
 * the storage volume it lived on got reformatted or unmounted. [FindroidSource.size] is a live
 * `File(path).length()` read (see `FindroidSourceDto.toFindroidSource`), which silently returns 0
 * for a vanished file rather than throwing - a completed (non-`.download`) source is never
 * legitimately 0 bytes, so this is an unambiguous signal to stop offering Play and surface a
 * re-download/delete choice instead.
 */
fun FindroidItem.isDownloadBroken(): Boolean {
    return sources
        .filter { it.type == FindroidSourceType.LOCAL && !it.path.endsWith(".download") }
        .any { it.size <= 0L }
}

/**
 * TMDB id, when known - only [FindroidMovie]/[FindroidShow] carry one (Jellyfin's own
 * `ProviderIds["Tmdb"]`, a nullable String there since it's Jellyfin-sourced metadata, not
 * guaranteed present). Used to match a Jellyfin library item against a Seerr search result, whose
 * `tmdbId` is a non-nullable `Int` (TMDB is the ground truth Seerr is built on).
 */
fun FindroidItem.tmdbIdOrNull(): Int? =
    when (this) {
        is FindroidMovie -> tmdbId
        is FindroidShow -> tmdbId
        else -> null
    }?.toIntOrNull()
