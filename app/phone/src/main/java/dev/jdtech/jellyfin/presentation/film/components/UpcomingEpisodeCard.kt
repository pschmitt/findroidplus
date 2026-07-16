package dev.jdtech.jellyfin.presentation.film.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import dev.jdtech.jellyfin.models.UpcomingEpisode
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings
import dev.jdtech.jellyfin.utils.formatCalendarDate
import java.time.LocalDate

/**
 * A Sonarr-known episode not yet in the Jellyfin library - visually distinct from a real
 * [EpisodeCard] on purpose: dimmed, no poster (there isn't one yet), no click target, and an
 * explicit "Not yet available" label so it doesn't read as a broken/loading real episode.
 */
@Composable
fun UpcomingEpisodeCard(
    episode: UpcomingEpisode,
    modifier: Modifier = Modifier,
    onSearchAutomatic: (() -> Unit)? = null,
    onSearchManual: (() -> Unit)? = null,
) {
    Row(modifier = modifier.height(84.dp).fillMaxWidth()) {
        Row(modifier = Modifier.weight(1f).fillMaxHeight().alpha(0.5f)) {
            Box(
                modifier =
                    Modifier.fillMaxHeight()
                        .aspectRatio(1.77f)
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.surfaceContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(CoreR.drawable.ic_calendar),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(MaterialTheme.spacings.default / 2))
            Column(
                modifier = Modifier.fillMaxHeight(),
                verticalArrangement = Arrangement.Center,
            ) {
                val title = episode.title ?: stringResource(CoreR.string.episode_number, episode.episodeNumber)
                Text(
                    text = stringResource(CoreR.string.episode_name, episode.episodeNumber, title),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text =
                        episode.airDate?.let {
                            stringResource(CoreR.string.season_upcoming_episode_air_date, formatCalendarDate(it))
                        } ?: stringResource(CoreR.string.season_upcoming_episode_tba),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = stringResource(CoreR.string.season_upcoming_episode_badge),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        if (onSearchAutomatic != null && onSearchManual != null) {
            PvrSearchButton(
                onAutomaticSearch = onSearchAutomatic,
                onManualSearch = onSearchManual,
                modifier = Modifier.align(Alignment.CenterVertically),
            )
        }
    }
}

@Preview(showBackground = true)
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
                    hasFile = false,
                    monitored = true,
                    episodeId = 123,
                )
        )
    }
}
