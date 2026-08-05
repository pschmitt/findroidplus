package dev.pschmitt.jellyfin.presentation.film.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.pschmitt.jellyfin.core.R as CoreR
import dev.pschmitt.jellyfin.models.PvrFetchError
import dev.pschmitt.jellyfin.models.PvrSource
import dev.pschmitt.jellyfin.presentation.components.MessageDetailsDialog
import dev.pschmitt.jellyfin.presentation.theme.FindroidTheme
import dev.pschmitt.jellyfin.presentation.theme.spacings

/**
 * Inline per-service fetch-failure banner for PVR-backed surfaces (Calendar, the Downloads screen's
 * queue section) - an unreachable Sonarr/Radarr should say so instead of masquerading as an empty
 * list. The messages already name the failing service (see `mapPvrSearchError`). Tapping a message
 * opens the full text in a copyable dialog - some of these (an HTTP error body) run longer than
 * fits on one or two lines here.
 */
@Composable
fun PvrErrorBanner(errors: List<PvrFetchError>, modifier: Modifier = Modifier) {
    if (errors.isEmpty()) return
    var expandedMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.extraSmall),
    ) {
        errors.forEach { error ->
            Row(
                modifier =
                    Modifier.fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .clickable { expandedMessage = error.message }
                        .padding(MaterialTheme.spacings.small),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(CoreR.drawable.ic_alert_circle),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = error.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }

    expandedMessage?.let { message ->
        MessageDetailsDialog(
            title = stringResource(CoreR.string.error_details_title),
            message = message,
            onDismissRequest = { expandedMessage = null },
        )
    }
}

@Composable
@Preview
private fun PvrErrorBannerPreview() {
    FindroidTheme {
        PvrErrorBanner(
            errors =
                listOf(
                    PvrFetchError(
                        source = PvrSource.SONARR,
                        message = "Could not reach Sonarr - check the server URL",
                    )
                )
        )
    }
}
