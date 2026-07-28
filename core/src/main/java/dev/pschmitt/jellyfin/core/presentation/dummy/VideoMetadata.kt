package dev.pschmitt.jellyfin.core.presentation.dummy

import dev.pschmitt.jellyfin.models.AudioChannel
import dev.pschmitt.jellyfin.models.AudioCodec
import dev.pschmitt.jellyfin.models.DisplayProfile
import dev.pschmitt.jellyfin.models.Resolution
import dev.pschmitt.jellyfin.models.VideoCodec
import dev.pschmitt.jellyfin.models.VideoMetadata

val dummyVideoMetadata =
    VideoMetadata(
        size = 1000000000,
        videoTracks = emptyList(),
        audioTracks = emptyList(),
        subtitleTracks = emptyList(),
        resolution = listOf(Resolution.HD),
        videoCodecs = listOf(VideoCodec.AV1),
        displayProfiles = listOf(DisplayProfile.HDR10),
        audioChannels = listOf(AudioChannel.CH_5_1),
        audioCodecs = listOf(AudioCodec.OPUS),
        isAtmos = listOf(false),
    )
