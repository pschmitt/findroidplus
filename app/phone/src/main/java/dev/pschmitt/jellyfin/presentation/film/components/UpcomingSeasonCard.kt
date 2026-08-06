package dev.pschmitt.jellyfin.presentation.film.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.pschmitt.jellyfin.core.R as CoreR
import dev.pschmitt.jellyfin.models.UpcomingSeason
import dev.pschmitt.jellyfin.presentation.theme.JollyfinTheme
import dev.pschmitt.jellyfin.presentation.theme.spacings

/**
 * A Sonarr-known season not yet in the Jellyfin library - the show-level equivalent of
 * [UpcomingEpisodeCard], shown alongside real [ItemCard] entries in the Show screen's seasons row.
 * Dimmed, same visual language as [UpcomingEpisodeCard]. Shows the real TMDB season poster (see
 * [UpcomingSeason.posterUrl]) once it's resolved, falling back to a calendar icon placeholder while
 * it's loading, unavailable, or Seerr isn't configured. It can still open the Seerr detail view for
 * the season, matching that card's behavior.
 */
@Composable
fun UpcomingSeasonCard(
    season: UpcomingSeason,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    queued: Boolean = false,
    onToggleQueued: (() -> Unit)? = null,
) {
    Column(
        modifier =
            modifier.width(150.dp).alpha(0.5f).clip(MaterialTheme.shapes.small).let {
                if (onClick != null) it.clickable(onClick = onClick) else it
            }
    ) {
        Surface(shape = MaterialTheme.shapes.small) {
            Box {
                Box(
                    modifier =
                        Modifier.fillMaxWidth()
                            .aspectRatio(0.66f)
                            .background(MaterialTheme.colorScheme.surfaceContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    if (season.posterUrl != null) {
                        AsyncImage(
                            model = season.posterUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxWidth().aspectRatio(0.66f),
                            placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceContainer),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Icon(
                            painter = painterResource(CoreR.drawable.ic_calendar),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (onToggleQueued != null) {
                    IconButton(
                        onClick = onToggleQueued,
                        modifier =
                            Modifier.align(Alignment.TopEnd)
                                .padding(MaterialTheme.spacings.extraSmall)
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)),
                    ) {
                        Icon(
                            painter =
                                painterResource(
                                    if (queued) CoreR.drawable.ic_check
                                    else CoreR.drawable.ic_download
                                ),
                            contentDescription =
                                stringResource(
                                    if (queued) CoreR.string.pending_download_queued_action
                                    else CoreR.string.pending_download_queue_action
                                ),
                            tint =
                                if (queued) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
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
    JollyfinTheme {
        UpcomingSeasonCard(
            season = UpcomingSeason(seasonNumber = 4, episodeCount = 10, monitored = true)
        )
    }
}
