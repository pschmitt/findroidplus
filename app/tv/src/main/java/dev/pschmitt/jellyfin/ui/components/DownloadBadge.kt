package dev.pschmitt.jellyfin.ui.components

import android.app.DownloadManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import dev.pschmitt.jellyfin.core.R as CoreR
import dev.pschmitt.jellyfin.presentation.theme.JollyfinTheme
import dev.pschmitt.jellyfin.utils.DownloadProgress

/** Static overlay shown on ItemCard/EpisodeCard posters for downloaded/downloading items. */
@Composable
fun DownloadedBadge(modifier: Modifier = Modifier) {
    BaseBadge(modifier = modifier) {
        Icon(
            painter = painterResource(CoreR.drawable.ic_download),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(16.dp).padding(4.dp),
        )
    }
}

@Composable
fun DownloadingBadge(progress: DownloadProgress, modifier: Modifier = Modifier) {
    BaseBadge(modifier = modifier) {
        when (progress.status) {
            DownloadManager.STATUS_PENDING ->
                Text(
                    text = stringResource(CoreR.string.download_queued),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                )
            DownloadManager.STATUS_PAUSED ->
                Text(
                    text = stringResource(CoreR.string.download_paused),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                )
            else ->
                if (progress.percent >= 0) {
                    Text(
                        text = "${progress.percent}%",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                    )
                } else {
                    Icon(
                        painter = painterResource(CoreR.drawable.ic_download),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp).padding(4.dp),
                    )
                }
        }
    }
}

/**
 * Static overlay shown on ItemCard/EpisodeCard posters for episodes the auto-delete-watched worker
 * will remove soon - see JollyfinEpisode.isMarkedForAutoDeletion.
 */
@Composable
fun MarkedForDeletionBadge(modifier: Modifier = Modifier) {
    BaseBadge(modifier = modifier) {
        Icon(
            painter = painterResource(CoreR.drawable.ic_trash),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(16.dp).padding(4.dp),
        )
    }
}

@Composable
private fun BaseBadge(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier = modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.6f))) {
        content()
    }
}

@Composable
@Preview
private fun DownloadedBadgePreview() {
    JollyfinTheme { DownloadedBadge() }
}

@Composable
@Preview
private fun DownloadingBadgePreview() {
    JollyfinTheme {
        DownloadingBadge(
            progress = DownloadProgress(status = DownloadManager.STATUS_RUNNING, percent = 42)
        )
    }
}

@Composable
@Preview
private fun MarkedForDeletionBadgePreview() {
    JollyfinTheme { MarkedForDeletionBadge() }
}
