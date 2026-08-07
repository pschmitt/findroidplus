package dev.pschmitt.jellyfin.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import dev.pschmitt.jellyfin.api.pvr.PvrService
import dev.pschmitt.jellyfin.backup.UUIDSerializer
import java.util.UUID
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "pvrServiceConfigs")
data class PvrServiceConfig(
    @PrimaryKey @Serializable(with = UUIDSerializer::class) val id: UUID,
    val service: PvrService,
    val enabled: Boolean = false,
    val baseUrl: String? = null,
)
