package dev.pschmitt.jellyfin.models

import androidx.room.Entity
import java.util.UUID
import org.jellyfin.sdk.model.DateTime

@Entity(tableName = "userdata", primaryKeys = ["userId", "itemId"])
data class JollyfinUserDataDto(
    val userId: UUID,
    val itemId: UUID,
    val played: Boolean,
    val favorite: Boolean,
    val playbackPositionTicks: Long,
    val toBeSynced: Boolean = false,
    // Mirrors the server's userData.lastPlayedDate locally, set/cleared alongside every setPlayed
    // call (see JellyfinRepositoryImpl/JellyfinRepositoryOfflineImpl) - lets
    // AutoDeleteWatchedWorker and the "marked for deletion" UI badge compute eligibility from the
    // local DB alone, without a network round trip per item.
    val lastPlayedDate: DateTime? = null,
)

fun JollyfinItem.toJollyfinUserDataDto(userId: UUID): JollyfinUserDataDto {
    return JollyfinUserDataDto(
        userId = userId,
        itemId = id,
        played = played,
        favorite = favorite,
        playbackPositionTicks = playbackPositionTicks,
    )
}
