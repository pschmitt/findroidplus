package dev.jdtech.jellyfin.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import dev.jdtech.jellyfin.backup.UUIDSerializer
import java.util.UUID
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "serverAddresses",
    foreignKeys =
        [
            ForeignKey(
                entity = Server::class,
                parentColumns = arrayOf("id"),
                childColumns = arrayOf("serverId"),
                onDelete = ForeignKey.CASCADE,
            )
        ],
)
data class ServerAddress(
    @PrimaryKey @Serializable(with = UUIDSerializer::class) val id: UUID,
    @ColumnInfo(index = true) val serverId: String,
    val address: String,
)
