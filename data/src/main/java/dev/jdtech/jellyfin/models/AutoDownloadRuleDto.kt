package dev.jdtech.jellyfin.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.jdtech.jellyfin.backup.UUIDSerializer
import java.util.UUID
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "autoDownloadRules",
    foreignKeys =
        [
            ForeignKey(
                entity = Server::class,
                parentColumns = arrayOf("id"),
                childColumns = arrayOf("serverId"),
                onDelete = ForeignKey.CASCADE,
            ),
            ForeignKey(
                entity = User::class,
                parentColumns = arrayOf("id"),
                childColumns = arrayOf("userId"),
                onDelete = ForeignKey.CASCADE,
            ),
        ],
    indices =
        [
            Index("serverId"),
            Index("userId"),
            Index(value = ["serverId", "userId", "seriesId", "seasonId"], unique = true),
        ],
)
data class AutoDownloadRuleDto(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val serverId: String,
    @Serializable(with = UUIDSerializer::class) val userId: UUID,
    @Serializable(with = UUIDSerializer::class) val seriesId: UUID,
    // null = show-level rule, applies to all current and future seasons.
    // non-null = season-level rule, applies only to that season.
    @Serializable(with = UUIDSerializer::class) val seasonId: UUID?,
    val enabled: Boolean = true,
    val createdAt: Long,
    // false (default) = backfill all currently-missing episodes immediately, then follow new
    // ones. true = never backfill; only queue episodes whose premiere date is after createdAt.
    @ColumnInfo(defaultValue = "0") val onlyNewEpisodes: Boolean = false,
    // When true, evaluation skips episodes that are already marked played.
    @ColumnInfo(defaultValue = "0") val onlyUnwatched: Boolean = false,
)
