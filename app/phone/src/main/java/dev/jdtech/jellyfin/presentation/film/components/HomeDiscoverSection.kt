package dev.jdtech.jellyfin.presentation.film.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.film.R as FilmR
import dev.jdtech.jellyfin.film.presentation.home.HomeAction
import dev.jdtech.jellyfin.film.presentation.home.HomeDiscoverSection
import dev.jdtech.jellyfin.models.SeerrMediaStatus
import dev.jdtech.jellyfin.models.SeerrMediaType
import dev.jdtech.jellyfin.models.SeerrSearchItem
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings

/** A Seerr-backed discovery row (trending/popular) with tappable poster cards. */
@Composable
fun HomeDiscoverSection(
    section: HomeDiscoverSection,
    itemsPadding: PaddingValues,
    onAction: (HomeAction) -> Unit,
    modifier: Modifier = Modifier,
    titleModifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Box(modifier = Modifier.fillMaxWidth().height(42.dp).padding(itemsPadding)) {
            Row(
                modifier = Modifier.align(Alignment.CenterStart),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionServiceIcons(listOf(CoreR.drawable.ic_seerr))
                Text(
                    text = stringResource(section.titleRes),
                    modifier = titleModifier,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
        Spacer(modifier = Modifier.height(MaterialTheme.spacings.extraSmall))
        LazyRow(
            contentPadding = itemsPadding,
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.default),
        ) {
            // Trending mixes movies and shows, and a movie and a show can share a tmdb id,
            // so the key needs the media type to stay unique.
            items(section.items, key = { "${it.mediaType}-${it.tmdbId}" }) { item ->
                DiscoverCard(item = item, onClick = { onAction(HomeAction.OnSeerrItemClick(item)) })
            }
        }
    }
}

@Composable
private fun DiscoverCard(item: SeerrSearchItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier =
            modifier.width(150.dp).clip(MaterialTheme.shapes.small).clickable(onClick = onClick)
    ) {
        Box(
            modifier =
                Modifier.fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surfaceContainer)
        ) {
            if (item.posterUrl != null) {
                AsyncImage(
                    model = item.posterUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    painter = painterResource(CoreR.drawable.ic_film),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            if (item.status != SeerrMediaStatus.NOT_REQUESTED) {
                Box(
                    modifier =
                        Modifier.align(Alignment.TopEnd).padding(MaterialTheme.spacings.small)
                ) {
                    SeerrStatusChip(status = item.status)
                }
            }
        }
        Spacer(modifier = Modifier.height(MaterialTheme.spacings.extraSmall))
        Text(
            text = item.title,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        item.year?.let { year ->
            Text(
                text = year.toString(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(2.dp))
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeDiscoverSectionPreview() {
    FindroidTheme {
        HomeDiscoverSection(
            section =
                HomeDiscoverSection(
                    titleRes = FilmR.string.home_discover_trending,
                    items =
                        listOf(
                            SeerrSearchItem(
                                tmdbId = 1,
                                mediaType = SeerrMediaType.MOVIE,
                                title = "A Very Long Movie Title That Wraps Around",
                                year = 2024,
                                overview = null,
                                posterUrl = null,
                                status = SeerrMediaStatus.AVAILABLE,
                            ),
                            SeerrSearchItem(
                                tmdbId = 2,
                                mediaType = SeerrMediaType.TV,
                                title = "Some Show",
                                year = 2023,
                                overview = null,
                                posterUrl = null,
                                status = SeerrMediaStatus.NOT_REQUESTED,
                            ),
                        ),
                ),
            itemsPadding = PaddingValues(horizontal = 16.dp),
            onAction = {},
        )
    }
}
