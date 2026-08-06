package dev.pschmitt.jellyfin.models

import org.jellyfin.sdk.model.api.MediaSegmentDto
import org.jellyfin.sdk.model.api.MediaSegmentType

enum class JollyfinSegmentType {
    INTRO,
    OUTRO,
    RECAP,
    PREVIEW,
    COMMERCIAL,
    UNKNOWN,
}

private fun MediaSegmentType.toJollyfinSegmentType(): JollyfinSegmentType =
    when (this) {
        MediaSegmentType.UNKNOWN -> JollyfinSegmentType.UNKNOWN
        MediaSegmentType.INTRO -> JollyfinSegmentType.INTRO
        MediaSegmentType.OUTRO -> JollyfinSegmentType.OUTRO
        MediaSegmentType.RECAP -> JollyfinSegmentType.RECAP
        MediaSegmentType.PREVIEW -> JollyfinSegmentType.PREVIEW
        MediaSegmentType.COMMERCIAL -> JollyfinSegmentType.COMMERCIAL
    }

data class JollyfinSegment(val type: JollyfinSegmentType, val startTicks: Long, val endTicks: Long)

fun JollyfinSegmentDto.toJollyfinSegment(): JollyfinSegment {
    return JollyfinSegment(type = type, startTicks = startTicks, endTicks = endTicks)
}

fun MediaSegmentDto.toJollyfinSegment(): JollyfinSegment {
    return JollyfinSegment(
        type = type.toJollyfinSegmentType(),
        startTicks = startTicks / 10000,
        endTicks = endTicks / 10000,
    )
}
