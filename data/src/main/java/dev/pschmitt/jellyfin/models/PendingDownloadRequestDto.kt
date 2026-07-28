package dev.pschmitt.jellyfin.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.pschmitt.jellyfin.backup.UUIDSerializer
import java.util.UUID
import kotlinx.serialization.Serializable

/**
 * A user-queued "download this once it's actually in the library" request for a Sonarr-known
 * season/episode that doesn't exist in Jellyfin yet (see [UpcomingSeason]/[UpcomingEpisode]) -
 * keyed by the parent show's Jellyfin [seriesId] plus Sonarr-style season/episode numbers rather
 * than a Jellyfin item id, since there isn't one until Sonarr grabs it, Jellyfin scans it in, and
 * the season/episode actually appears. Periodically resolved against the live Jellyfin library by
 * [dev.pschmitt.jellyfin.work.PendingDownloadWorker] (via
 * [dev.pschmitt.jellyfin.utils.PendingDownloadFulfiller]) - once the target appears, its download is
 * enqueued, a notification is posted, and this row is deleted. A one-off "download whichever
 * episodes exist once this appears" request, not a persistent "keep following new episodes" rule -
 * see [dev.pschmitt.jellyfin.repository.AutoDownloadRuleRepository] for that.
 */
@Serializable
@Entity(
    tableName = "pending_download_requests",
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
            Index(value = ["serverId", "userId", "seriesId"]),
        ],
)
data class PendingDownloadRequestDto(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val serverId: String,
    @Serializable(with = UUIDSerializer::class) val userId: UUID,
    @Serializable(with = UUIDSerializer::class) val seriesId: UUID,
    val seasonNumber: Int,
    // null = whole-season request (download whatever episodes of this season exist once the
    // season itself appears in the library), non-null = a single specific episode.
    @ColumnInfo(defaultValue = "NULL") val episodeNumber: Int? = null,
    // Sonarr's own numeric episode id, for convenience - not currently used for matching (matching
    // is done by season/episode number against Jellyfin, see PendingDownloadFulfiller) but kept so
    // a future UI (e.g. a "pending downloads" list) can trigger a manual search without a second
    // SonarrSearchRepository.resolveEpisodeId round trip.
    @ColumnInfo(defaultValue = "NULL") val sonarrEpisodeId: Int? = null,
    val requestedAt: Long,
)
