package dev.jdtech.jellyfin.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.models.UpcomingSeason
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings

/**
 * A Sonarr-known season not yet in the Jellyfin library - the show-level equivalent of
 * [UpcomingEpisodeCard]. Non-interactive for the same reason as that card: TV has no Seerr detail
 * screen wired up yet, so there's nothing productive a tap could do here.
 */
@Composable
fun UpcomingSeasonCard(season: UpcomingSeason) {
    Surface(
        onClick = {},
        enabled = false,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(10.dp)),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
            ),
        modifier = Modifier.width(150.dp).alpha(0.5f),
    ) {
        Column {
            Box(
                modifier =
                    Modifier.fillMaxWidth()
                        .aspectRatio(0.66f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painter = painterResource(CoreR.drawable.ic_calendar), contentDescription = null)
            }
            Spacer(modifier = Modifier.height(MaterialTheme.spacings.small))
            Text(
                text = stringResource(CoreR.string.season_number, season.seasonNumber),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(CoreR.string.upcoming_season_episode_count, season.episodeCount),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(CoreR.string.season_upcoming_episode_badge),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Preview(device = "id:tv_1080p")
@Composable
private fun UpcomingSeasonCardPreview() {
    FindroidTheme {
        UpcomingSeasonCard(
            season = UpcomingSeason(seasonNumber = 4, episodeCount = 10, monitored = true)
        )
    }
}
