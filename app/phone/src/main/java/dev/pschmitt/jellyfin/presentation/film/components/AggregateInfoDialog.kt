package dev.pschmitt.jellyfin.presentation.film.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import dev.pschmitt.jellyfin.core.R as CoreR
import dev.pschmitt.jellyfin.presentation.theme.FindroidTheme
import dev.pschmitt.jellyfin.presentation.theme.spacings
import dev.pschmitt.jellyfin.utils.formatBinaryFileSize

/**
 * The Show/Season counterpart of [InfoDialog] - these are collections, not a single video file,
 * so there's no codec/resolution to show, just how many episodes and how much of them is
 * downloaded to this device.
 */
@Composable
fun AggregateInfoDialog(episodeCount: Int, downloadedSizeBytes: Long, onDismiss: () -> Unit) {
    AlertDialog(
        title = { Text(text = stringResource(CoreR.string.info)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small)) {
                Text(
                    text =
                        pluralStringResource(
                            CoreR.plurals.aggregate_info_episode_count,
                            episodeCount,
                            episodeCount,
                        ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text =
                        stringResource(
                            CoreR.string.aggregate_info_downloaded_size,
                            formatBinaryFileSize(downloadedSizeBytes),
                        ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(text = stringResource(CoreR.string.close)) }
        },
    )
}

@Composable
@Preview
private fun AggregateInfoDialogPreview() {
    FindroidTheme {
        AggregateInfoDialog(episodeCount = 12, downloadedSizeBytes = 4_500_000_000L, onDismiss = {})
    }
}
