package dev.jdtech.jellyfin.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "sources")
data class FindroidSourceDto(
    @PrimaryKey val id: String,
    val itemId: UUID,
    val name: String,
    val type: FindroidSourceType,
    val path: String,
    val downloadId: Long? = null,
    // SHA-256 hex digest computed once the download finishes, used to detect corruption after a
    // storage move (see DownloaderImpl.moveFile). Null for remote sources and for local sources
    // downloaded before this field existed.
    val checksum: String? = null,
    // True while this download was cancelled by BatterySaverReceiver rather than by the user, so
    // it's the one resumed once battery saver turns back off - see
    // DownloaderImpl.pauseAllForBatterySaver/resumeBatterySaverPausedDownloads.
    @ColumnInfo(defaultValue = "0") val pausedByBatterySaver: Boolean = false,
    // User-set "keep" pin - excludes this download from AutoDeleteWatchedWorker even once it's
    // watched and past the configured threshold. See FindroidEpisode.isMarkedForAutoDeletion.
    @ColumnInfo(defaultValue = "0") val excludeFromAutoDelete: Boolean = false,
)

fun FindroidSource.toFindroidSourceDto(itemId: UUID, path: String): FindroidSourceDto {
    return FindroidSourceDto(
        id = id,
        itemId = itemId,
        name = name,
        type = FindroidSourceType.LOCAL,
        path = path,
    )
}
