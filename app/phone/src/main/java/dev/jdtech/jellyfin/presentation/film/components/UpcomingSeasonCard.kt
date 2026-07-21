package dev.jdtech.jellyfin.presentation.film.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.models.UpcomingSeason
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings

/**
 * A Sonarr-known season not yet in the Jellyfin library - the show-level equivalent of
 * [UpcomingEpisodeCard], shown alongside real [ItemCard] entries in the Show screen's seasons
 * row. Dimmed with no poster (there isn't one yet), same visual language as
 * [UpcomingEpisodeCard]. It can still open the Seerr detail view for the season, matching that
 * card's behavior.
 */
@Composable
fun UpcomingSeasonCard(
    season: UpcomingSeason,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Column(
        modifier =
            modifier.width(150.dp).alpha(0.5f).clip(MaterialTheme.shapes.small).let {
                if (onClick != null) it.clickable(onClick = onClick) else it
            }
    ) {
        Surface(shape = MaterialTheme.shapes.small) {
            Box(
                modifier =
                    Modifier.fillMaxWidth()
                        .aspectRatio(0.66f)
                        .background(MaterialTheme.colorScheme.surfaceContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(CoreR.drawable.ic_calendar),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(modifier = Modifier.height(MaterialTheme.spacings.extraSmall))
        Text(
            text = stringResource(CoreR.string.season_number, season.seasonNumber),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = stringResource(CoreR.string.upcoming_season_episode_count, season.episodeCount),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = stringResource(CoreR.string.season_upcoming_episode_badge),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun UpcomingSeasonCardPreview() {
    FindroidTheme {
        UpcomingSeasonCard(
            season = UpcomingSeason(seasonNumber = 4, episodeCount = 10, monitored = true)
        )
    }
}
