package dev.pschmitt.jellyfin.presentation.film.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.pschmitt.jellyfin.core.R as CoreR
import dev.pschmitt.jellyfin.models.PvrSource
import dev.pschmitt.jellyfin.models.QueueItemStatus
import dev.pschmitt.jellyfin.models.QueueStatus
import dev.pschmitt.jellyfin.presentation.theme.FindroidTheme
import dev.pschmitt.jellyfin.presentation.theme.spacings
import dev.pschmitt.jellyfin.utils.formatBinaryFileSize
import dev.pschmitt.jellyfin.utils.formatDownloadSpeed
import dev.pschmitt.jellyfin.utils.formatEta

/**
 * Shown over a poster when Sonarr/Radarr have a matching item in their download queue, but it
 * isn't (yet) an in-progress local Jellyfin download. Callers should prefer [DownloadingBadge]/
 * [DownloadedBadge] over this badge whenever a real local download is in flight - see
 * `EpisodeCard`/`ItemCard` for the precedence rule.
 *
 * Tappable regardless of status to show [QueueStatusDetailDialog] with size/ETA/speed - a
 * DOWNLOADING badge alone only has room for a percentage or a bare icon.
 */
@Composable
fun QueueBadge(status: QueueStatus, modifier: Modifier = Modifier) {
    var detailDialogOpen by remember { mutableStateOf(false) }

    when (status.status) {
        QueueItemStatus.QUEUED ->
            BaseBadge(modifier = modifier.clickable { detailDialogOpen = true }) {
                Text(
                    text = stringResource(CoreR.string.queue_status_queued),
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                )
            }
        QueueItemStatus.DOWNLOADING ->
            BaseBadge(modifier = modifier.clickable { detailDialogOpen = true }) {
                if (status.percent >= 0) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(4.dp)) {
                        CircularProgressIndicator(
                            progress = { status.percent / 100f },
                            modifier = Modifier.size(28.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            trackColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.3f),
                            strokeWidth = 2.dp,
                        )
                        Text(
                            text = "${status.percent}%",
                            color = MaterialTheme.colorScheme.onPrimary,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                } else {
                    Icon(
                        painter = painterResource(CoreR.drawable.ic_download),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(4.dp),
                    )
                }
            }
        QueueItemStatus.IMPORTING ->
            BaseBadge(modifier = modifier.clickable { detailDialogOpen = true }) {
                Text(
                    text = stringResource(CoreR.string.queue_status_importing),
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                )
            }
        QueueItemStatus.WARNING ->
            BaseBadge(
                modifier = modifier.clickable { detailDialogOpen = true },
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            ) {
                Icon(
                    painter = painterResource(CoreR.drawable.ic_alert_circle),
                    contentDescription = stringResource(CoreR.string.queue_status_warning),
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.padding(4.dp).size(16.dp),
                )
            }
        QueueItemStatus.FAILED ->
            BaseBadge(
                modifier = modifier.clickable { detailDialogOpen = true },
                containerColor = MaterialTheme.colorScheme.errorContainer,
            ) {
                Icon(
                    painter = painterResource(CoreR.drawable.ic_alert_circle),
                    contentDescription = stringResource(CoreR.string.queue_status_failed),
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(4.dp).size(16.dp),
                )
            }
    }

    if (detailDialogOpen) {
        QueueStatusDetailDialog(status = status, onDismiss = { detailDialogOpen = false })
    }
}

@Composable
private fun QueueStatusDetailDialog(status: QueueStatus, onDismiss: () -> Unit) {
    AlertDialog(
        title = { Text(text = stringResource(CoreR.string.queue_status_detail_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small)) {
                if (status.sizeBytes > 0) {
                    Text(
                        text =
                            stringResource(
                                CoreR.string.queue_status_detail_size,
                                formatBinaryFileSize(status.sizeBytes - status.remainingBytes),
                                formatBinaryFileSize(status.sizeBytes),
                            ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (status.etaSeconds >= 0) {
                    Text(
                        text =
                            stringResource(
                                CoreR.string.queue_status_detail_eta,
                                formatEta(status.etaSeconds),
                            ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (status.speedBytesPerSecond > 0) {
                    Text(
                        text =
                            stringResource(
                                CoreR.string.queue_status_detail_speed,
                                formatDownloadSpeed(status.speedBytesPerSecond),
                            ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                status.errorMessage?.let { errorMessage ->
                    Text(text = errorMessage, style = MaterialTheme.typography.bodyMedium)
                }
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
private fun QueueBadgeQueuedPreview() {
    FindroidTheme {
        QueueBadge(status = QueueStatus(source = PvrSource.SONARR, status = QueueItemStatus.QUEUED))
    }
}

@Composable
@Preview
private fun QueueBadgeDownloadingPercentPreview() {
    FindroidTheme {
        QueueBadge(
            status =
                QueueStatus(
                    source = PvrSource.RADARR,
                    status = QueueItemStatus.DOWNLOADING,
                    percent = 63,
                )
        )
    }
}

@Composable
@Preview
private fun QueueBadgeDownloadingIndeterminatePreview() {
    FindroidTheme {
        QueueBadge(
            status =
                QueueStatus(
                    source = PvrSource.SONARR,
                    status = QueueItemStatus.DOWNLOADING,
                    percent = -1,
                )
        )
    }
}

@Composable
@Preview
private fun QueueBadgeImportingPreview() {
    FindroidTheme {
        QueueBadge(
            status = QueueStatus(source = PvrSource.SONARR, status = QueueItemStatus.IMPORTING)
        )
    }
}

@Composable
@Preview
private fun QueueBadgeWarningPreview() {
    FindroidTheme {
        QueueBadge(
            status =
                QueueStatus(
                    source = PvrSource.RADARR,
                    status = QueueItemStatus.WARNING,
                    errorMessage = "Stalled",
                )
        )
    }
}

@Composable
@Preview
private fun QueueBadgeFailedPreview() {
    FindroidTheme {
        QueueBadge(
            status =
                QueueStatus(
                    source = PvrSource.RADARR,
                    status = QueueItemStatus.FAILED,
                    errorMessage = "No files found are eligible for import",
                )
        )
    }
}
