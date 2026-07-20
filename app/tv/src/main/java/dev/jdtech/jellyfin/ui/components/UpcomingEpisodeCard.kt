package dev.jdtech.jellyfin.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import dev.jdtech.jellyfin.models.UpcomingEpisode
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings
import dev.jdtech.jellyfin.utils.formatCalendarDate
import dev.jdtech.jellyfin.utils.formatCalendarTime
import java.time.LocalDate

/**
 * A Sonarr-known episode not yet in the Jellyfin library - dimmed, and `enabled = false` so D-pad
 * focus traversal skips right over it rather than landing on a dead/no-op row (same mechanism
 * disabled settings cards use, see SettingsSwitchCard). No poster (there isn't one yet) and an
 * explicit "Not yet available" label so it doesn't read as a broken real episode. Mirrors the
 * phone app's `dev.jdtech.jellyfin.presentation.film.components.UpcomingEpisodeCard`.
 */
@Composable
fun UpcomingEpisodeCard(episode: UpcomingEpisode) {
    Surface(
        onClick = {},
        enabled = false,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(10.dp)),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
            ),
        modifier = Modifier.fillMaxWidth().alpha(0.5f),
    ) {
        Row(modifier = Modifier.padding(MaterialTheme.spacings.small)) {
            Box(
                modifier =
                    Modifier.width(160.dp)
                        .height(90.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painter = painterResource(CoreR.drawable.ic_calendar), contentDescription = null)
            }
            Spacer(modifier = Modifier.width(MaterialTheme.spacings.medium))
            Column {
                val title =
                    episode.title ?: stringResource(CoreR.string.episode_number, episode.episodeNumber)
                Text(
                    text = stringResource(CoreR.string.episode_name, episode.episodeNumber, title),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(MaterialTheme.spacings.extraSmall))
                Text(
                    text =
                        episode.airDate?.let { airDate ->
                            episode.airTime?.let { airTime ->
                                stringResource(
                                    CoreR.string.season_upcoming_episode_air_date_time,
                                    formatCalendarDate(airDate),
                                    formatCalendarTime(airTime),
                                )
                            } ?: stringResource(CoreR.string.season_upcoming_episode_air_date, formatCalendarDate(airDate))
                        } ?: stringResource(CoreR.string.season_upcoming_episode_tba),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(CoreR.string.season_upcoming_episode_badge),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Preview(device = "id:tv_1080p")
@Composable
private fun UpcomingEpisodeCardPreview() {
    FindroidTheme {
        UpcomingEpisodeCard(
            episode =
                UpcomingEpisode(
                    seasonNumber = 1,
                    episodeNumber = 5,
                    title = "The One Where It Airs",
                    airDate = LocalDate.now().plusDays(7),
                    airTime = java.time.LocalTime.of(21, 0),
                    hasFile = false,
                    monitored = true,
                    episodeId = 123,
                )
        )
    }
}
