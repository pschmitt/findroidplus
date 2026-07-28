package dev.pschmitt.jellyfin.presentation.film.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.pschmitt.jellyfin.core.R as CoreR
import dev.pschmitt.jellyfin.models.VideoMetadata
import dev.pschmitt.jellyfin.presentation.theme.spacings
import dev.pschmitt.jellyfin.utils.formatBinaryFileSize

@Composable
fun ExtraInfoText(videoMetadata: VideoMetadata) {
    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small)) {
        Text(
            text =
                "${stringResource(CoreR.string.size)}: ${formatBinaryFileSize(videoMetadata.size)}",
            style = MaterialTheme.typography.bodyMedium,
        )
        if (videoMetadata.videoTracks.isNotEmpty()) {
            Text(
                text =
                    "${stringResource(CoreR.string.video)}: ${videoMetadata.videoTracks.joinToString { it }}",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (videoMetadata.audioTracks.isNotEmpty()) {
            Text(
                text =
                    "${stringResource(CoreR.string.audio)}: ${videoMetadata.audioTracks.joinToString { it }}",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (videoMetadata.subtitleTracks.isNotEmpty()) {
            Text(
                text =
                    "${stringResource(CoreR.string.subtitle)}: ${videoMetadata.subtitleTracks.joinToString { it }}",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
