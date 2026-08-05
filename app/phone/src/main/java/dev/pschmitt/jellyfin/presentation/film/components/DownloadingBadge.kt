package dev.pschmitt.jellyfin.presentation.film.components

import android.app.DownloadManager
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.pschmitt.jellyfin.core.R as CoreR
import dev.pschmitt.jellyfin.presentation.theme.FindroidTheme
import dev.pschmitt.jellyfin.utils.DownloadProgress

/** Shown over an episode's poster while it's queued, downloading, or paused. */
@Composable
fun DownloadingBadge(progress: DownloadProgress, modifier: Modifier = Modifier) {
    BaseBadge(modifier = modifier) {
        when (progress.status) {
            DownloadManager.STATUS_PENDING ->
                Text(
                    text = stringResource(CoreR.string.download_queued),
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                )
            DownloadManager.STATUS_PAUSED ->
                Text(
                    text = stringResource(CoreR.string.download_paused),
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                )
            else ->
                if (progress.percent >= 0) {
                    Text(
                        text = "${progress.percent}%",
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
    }
}

@Composable
@Preview
private fun DownloadingBadgeQueuedPreview() {
    FindroidTheme {
        DownloadingBadge(progress = DownloadProgress(status = DownloadManager.STATUS_PENDING))
    }
}

@Composable
@Preview
private fun DownloadingBadgeProgressPreview() {
    FindroidTheme {
        DownloadingBadge(
            progress = DownloadProgress(status = DownloadManager.STATUS_RUNNING, percent = 42)
        )
    }
}
