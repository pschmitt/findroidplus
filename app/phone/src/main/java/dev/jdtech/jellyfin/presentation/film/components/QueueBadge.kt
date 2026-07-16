package dev.jdtech.jellyfin.presentation.film.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.models.PvrSource
import dev.jdtech.jellyfin.models.QueueItemStatus
import dev.jdtech.jellyfin.models.QueueStatus
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme

/**
 * Shown over a poster when Sonarr/Radarr have a matching item in their download queue, but it
 * isn't (yet) an in-progress local Jellyfin download. Callers should prefer [DownloadingBadge]/
 * [DownloadedBadge] over this badge whenever a real local download is in flight - see
 * `EpisodeCard`/`ItemCard` for the precedence rule.
 */
@Composable
fun QueueBadge(status: QueueStatus, modifier: Modifier = Modifier) {
    when (status.status) {
        QueueItemStatus.QUEUED ->
            BaseBadge(modifier = modifier) {
                Text(
                    text = stringResource(CoreR.string.queue_status_queued),
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                )
            }
        QueueItemStatus.DOWNLOADING ->
            BaseBadge(modifier = modifier) {
                if (status.percent >= 0) {
                    Text(
                        text = "${status.percent}%",
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                    )
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
            BaseBadge(modifier = modifier) {
                Text(
                    text = stringResource(CoreR.string.queue_status_importing),
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                )
            }
        QueueItemStatus.WARNING ->
            BaseBadge(
                modifier = modifier,
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
                modifier = modifier,
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
