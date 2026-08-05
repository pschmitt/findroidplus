package dev.pschmitt.jellyfin.presentation.film.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.pschmitt.jellyfin.core.R as CoreR
import dev.pschmitt.jellyfin.film.R as FilmR
import dev.pschmitt.jellyfin.film.presentation.search.SearchAction
import dev.pschmitt.jellyfin.film.presentation.search.SearchState
import dev.pschmitt.jellyfin.models.SeerrMediaType
import dev.pschmitt.jellyfin.presentation.theme.spacings
import dev.pschmitt.jellyfin.presentation.utils.GridCellsAdaptiveWithMinColumns
import dev.pschmitt.jellyfin.presentation.utils.rememberSafePadding
import kotlinx.coroutines.delay

/**
 * Search as a plain in-place screen - a back button + text field row, same page background as
 * everywhere else, directly followed by the results grid - rather than Material3's `SearchBar`
 * component, whose "expanded" state is a floating, shadowed, near-fullscreen surface with its own
 * scrim. That read as a dialog/popup rather than just another screen state.
 */
@Composable
fun FilmSearchScreen(
    state: SearchState,
    onAction: (SearchAction) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    val safePadding = rememberSafePadding()

    var query by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    LaunchedEffect(query) {
        if (query.isNotBlank()) {
            // Debounce scales with input length. Max debounce of 300ms.
            delay(minOf(50L + (query.count() * 50L), 300L))
        }
        onAction(SearchAction.Search(query))
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .padding(
                        start = safePadding.start + MaterialTheme.spacings.small,
                        top = safePadding.top + MaterialTheme.spacings.small,
                        end = safePadding.end + MaterialTheme.spacings.small,
                    ),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBackClick) {
                Icon(
                    painter = painterResource(CoreR.drawable.ic_arrow_left),
                    contentDescription = null,
                )
            }
            TextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.weight(1f).focusRequester(focusRequester),
                placeholder = { Text(text = stringResource(FilmR.string.search_placeholder)) },
                singleLine = true,
                trailingIcon = {
                    if (state.loading) {
                        Box(modifier = Modifier.size(32.dp)) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        }
                    } else if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(
                                painter = painterResource(CoreR.drawable.ic_x),
                                contentDescription = null,
                            )
                        }
                    }
                },
                colors =
                    TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
            )
        }
        LazyVerticalGrid(
            columns = GridCellsAdaptiveWithMinColumns(minSize = 160.dp, minColumns = 2),
            modifier = Modifier.fillMaxSize(),
            contentPadding =
                PaddingValues(
                    start = safePadding.start + MaterialTheme.spacings.default,
                    top = MaterialTheme.spacings.small,
                    end = safePadding.end + MaterialTheme.spacings.default,
                    bottom = safePadding.bottom + MaterialTheme.spacings.default,
                ),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.default),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.default),
        ) {
            items(items = state.items, key = { it.id }) { item ->
                ItemCard(
                    item = item,
                    direction = Direction.VERTICAL,
                    onClick = { onAction(SearchAction.OnItemClick(item)) },
                    modifier = Modifier.animateItem(),
                )
            }
            if (state.seerrResults.isNotEmpty()) {
                item(key = "seerr-search-header", span = { GridItemSpan(maxLineSpan) }) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SectionServiceIcons(listOf(CoreR.drawable.ic_seerr))
                        Text(
                            text = stringResource(CoreR.string.media_seerr_section),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
                items(
                    items = state.seerrResults,
                    key = { "seerr-${it.mediaType}-${it.tmdbId}" },
                    span = { GridItemSpan(maxLineSpan) },
                ) { item ->
                    SeerrResultRow(
                        item = item,
                        requestedThisSession = false,
                        queueStatus =
                            if (item.mediaType == SeerrMediaType.MOVIE) {
                                state.radarrQueueStatus[item.tmdbId]
                            } else {
                                null
                            },
                        onClick = { onAction(SearchAction.OnSeerrItemClick(item)) },
                    )
                }
            }
        }
    }
}
