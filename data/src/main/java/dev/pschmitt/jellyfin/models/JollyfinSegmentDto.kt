package dev.pschmitt.jellyfin.models

import androidx.room.Entity
import java.util.UUID

@Entity(tableName = "segments", primaryKeys = ["itemId", "type"])
data class JollyfinSegmentDto(
    val itemId: UUID,
    val type: JollyfinSegmentType,
    val startTicks: Long,
    val endTicks: Long,
)

fun JollyfinSegment.toJollyfinSegmentsDto(itemId: UUID): JollyfinSegmentDto {
    return JollyfinSegmentDto(
        itemId = itemId,
        type = type,
        startTicks = startTicks,
        endTicks = endTicks,
    )
}
