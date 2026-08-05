package dev.pschmitt.jellyfin.presentation.film.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.pschmitt.jellyfin.core.R as CoreR
import dev.pschmitt.jellyfin.core.presentation.dummy.dummyEpisode
import dev.pschmitt.jellyfin.core.presentation.dummy.dummyMovie
import dev.pschmitt.jellyfin.models.FindroidItem
import dev.pschmitt.jellyfin.presentation.theme.FindroidTheme

/**
 * A Netflix-style play affordance overlaid on the item's header image, replacing the old dedicated
 * Play button in ItemButtonsBar. Always resumes (or starts) playback - restarting from the
 * beginning is a separate action reachable from ItemButtonsBar's overflow menu.
 */
@Composable
fun PlayOverlayButton(
    item: FindroidItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    // The downloaded file backing this item is actively being deleted - its bytes may already be
    // gone from disk before the DB (and therefore item.isDownloaded()) catches up, so playback
    // needs blocking here rather than trusting that flag alone.
    isDeleting: Boolean = false,
) {
    val runtimeMinutesLeft by
        remember(item.playbackPositionTicks) {
            mutableLongStateOf((item.runtimeTicks - item.playbackPositionTicks) / 600000000)
        }

    // The "X min left" caption is positioned with a fixed offset from the button rather than
    // stacked in a centered Column with it - a Column would center the button+caption pair as a
    // group, pushing the button itself off the true center whenever the caption is shown.
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        FilledIconButton(
            onClick = onClick,
            enabled = enabled && !isDeleting,
            modifier = Modifier.size(64.dp),
            colors =
                IconButtonDefaults.filledIconButtonColors(
                    containerColor = Color.Black.copy(alpha = 0.5f),
                    contentColor = Color.White,
                ),
        ) {
            if (isDeleting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = Color.White,
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(
                    painter = painterResource(CoreR.drawable.ic_play),
                    contentDescription = stringResource(CoreR.string.play),
                    modifier = Modifier.size(32.dp),
                )
            }
        }
        if (item.playbackPositionTicks > 0) {
            Text(
                text = stringResource(CoreR.string.runtime_minutes_left, runtimeMinutesLeft),
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.align(Alignment.Center).offset(y = 40.dp),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PlayOverlayButtonMoviePreview() {
    FindroidTheme { PlayOverlayButton(item = dummyMovie, onClick = {}) }
}

@Preview(showBackground = true)
@Composable
private fun PlayOverlayButtonEpisodePreview() {
    FindroidTheme { PlayOverlayButton(item = dummyEpisode, onClick = {}) }
}
