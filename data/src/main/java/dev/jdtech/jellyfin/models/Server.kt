package dev.jdtech.jellyfin.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import dev.jdtech.jellyfin.backup.UUIDSerializer
import java.util.UUID
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "servers")
data class Server(
    @PrimaryKey val id: String,
    val name: String,
    @Serializable(with = UUIDSerializer::class) var currentServerAddressId: UUID?,
    @Serializable(with = UUIDSerializer::class) var currentUserId: UUID?,
)
