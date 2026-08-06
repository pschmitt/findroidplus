package dev.pschmitt.jellyfin.models

import org.jellyfin.sdk.model.api.TrickplayInfoDto

data class JollyfinTrickplayInfo(
    val width: Int,
    val height: Int,
    val tileWidth: Int,
    val tileHeight: Int,
    val thumbnailCount: Int,
    val interval: Int,
    val bandwidth: Int,
)

fun TrickplayInfoDto.toJollyfinTrickplayInfo(): JollyfinTrickplayInfo {
    return JollyfinTrickplayInfo(
        width = width,
        height = height,
        tileWidth = tileWidth,
        tileHeight = tileHeight,
        thumbnailCount = thumbnailCount,
        interval = interval,
        bandwidth = bandwidth,
    )
}

fun JollyfinTrickplayInfoDto.toJollyfinTrickplayInfo(): JollyfinTrickplayInfo {
    return JollyfinTrickplayInfo(
        width = width,
        height = height,
        tileWidth = tileWidth,
        tileHeight = tileHeight,
        thumbnailCount = thumbnailCount,
        interval = interval,
        bandwidth = bandwidth,
    )
}
