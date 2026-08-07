package dev.pschmitt.jellyfin.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import dev.pschmitt.jellyfin.backup.UUIDSerializer
import java.util.UUID
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "profiles",
    foreignKeys =
        [
            ForeignKey(
                entity = User::class,
                parentColumns = arrayOf("id"),
                childColumns = arrayOf("userId"),
                onDelete = ForeignKey.CASCADE,
            ),
            ForeignKey(
                entity = PvrServiceConfig::class,
                parentColumns = arrayOf("id"),
                childColumns = arrayOf("sonarrConfigId"),
                onDelete = ForeignKey.SET_NULL,
            ),
            ForeignKey(
                entity = PvrServiceConfig::class,
                parentColumns = arrayOf("id"),
                childColumns = arrayOf("radarrConfigId"),
                onDelete = ForeignKey.SET_NULL,
            ),
            ForeignKey(
                entity = PvrServiceConfig::class,
                parentColumns = arrayOf("id"),
                childColumns = arrayOf("seerrConfigId"),
                onDelete = ForeignKey.SET_NULL,
            ),
        ],
)
data class Profile(
    @PrimaryKey @Serializable(with = UUIDSerializer::class) val id: UUID,
    var name: String,
    @ColumnInfo(index = true) @Serializable(with = UUIDSerializer::class) var userId: UUID,
    var isMain: Boolean = false,
    @ColumnInfo(index = true)
    @Serializable(with = UUIDSerializer::class)
    var sonarrConfigId: UUID? = null,
    @ColumnInfo(index = true)
    @Serializable(with = UUIDSerializer::class)
    var radarrConfigId: UUID? = null,
    @ColumnInfo(index = true)
    @Serializable(with = UUIDSerializer::class)
    var seerrConfigId: UUID? = null,
)
