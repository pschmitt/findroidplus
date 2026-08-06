package dev.pschmitt.jellyfin.presentation.film.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.pschmitt.jellyfin.core.R as CoreR
import dev.pschmitt.jellyfin.presentation.theme.JollyfinTheme
import dev.pschmitt.jellyfin.presentation.theme.spacings

/**
 * Shown instead of [PvrErrorBanner] while a configured Sonarr/Radarr is still within its tolerated
 * failure window (see `QueueStatusRepositoryImpl.FAILURE_THRESHOLD`) and has never yet had a
 * successful poll this app session - i.e. genuinely still checking, not yet known to be broken.
 * Without this, that window renders identically to "nothing queued" until the threshold trips and
 * the error banner appears with no transition.
 */
@Composable
fun PvrQueueLoadingPlaceholder(modifier: Modifier = Modifier) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(MaterialTheme.spacings.small),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(CoreR.string.pvr_queue_loading),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
@Preview
private fun PvrQueueLoadingPlaceholderPreview() {
    JollyfinTheme { PvrQueueLoadingPlaceholder() }
}
