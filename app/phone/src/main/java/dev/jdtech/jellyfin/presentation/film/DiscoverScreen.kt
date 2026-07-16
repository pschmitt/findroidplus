package dev.jdtech.jellyfin.presentation.film

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.recalculateWindowInsets
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.film.presentation.discover.DiscoverEvent
import dev.jdtech.jellyfin.film.presentation.discover.DiscoverState
import dev.jdtech.jellyfin.film.presentation.discover.DiscoverViewModel
import dev.jdtech.jellyfin.models.SeerrMediaStatus
import dev.jdtech.jellyfin.models.SeerrMediaType
import dev.jdtech.jellyfin.models.SeerrSearchItem
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings
import dev.jdtech.jellyfin.utils.ObserveAsEvents

/**
 * Jellyseerr-backed "add new content" screen: search TMDB, file a request (Jellyseerr routes it
 * to Sonarr/Radarr server-side), and see recent requests with their availability. Only reachable
 * when Jellyseerr is configured (see `MediaState.showDiscoverTab`).
 */
@Composable
fun DiscoverScreen(viewModel: DiscoverViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    ObserveAsEvents(viewModel.events) { event ->
        val message =
            when (event) {
                is DiscoverEvent.Requested ->
                    context.getString(CoreR.string.discover_requested_toast, event.title)
                is DiscoverEvent.Failed ->
                    context.getString(
                        CoreR.string.discover_request_failed_toast,
                        event.message ?: context.getString(CoreR.string.unknown_error),
                    )
            }
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    DiscoverScreenLayout(
        state = state,
        onQueryChanged = viewModel::onQueryChanged,
        onRequest = viewModel::request,
    )
}

/** Media-type filter for search results - presentation-only, so it lives in the composable. */
private enum class DiscoverFilter {
    ALL,
    MOVIES,
    SHOWS,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DiscoverScreenLayout(
    state: DiscoverState,
    onQueryChanged: (String) -> Unit = {},
    onRequest: (SeerrSearchItem) -> Unit = {},
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    var filter by rememberSaveable { mutableStateOf(DiscoverFilter.ALL) }

    val filteredResults =
        when (filter) {
            DiscoverFilter.ALL -> state.results
            DiscoverFilter.MOVIES -> state.results.filter { it.mediaType == SeerrMediaType.MOVIE }
            DiscoverFilter.SHOWS -> state.results.filter { it.mediaType == SeerrMediaType.TV }
        }

    Scaffold(
        modifier =
            Modifier.fillMaxSize()
                .recalculateWindowInsets()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(CoreR.string.title_discover)) },
                windowInsets = WindowInsets.statusBars.union(WindowInsets.displayCutout),
                scrollBehavior = scrollBehavior,
            )
        },
        contentWindowInsets = WindowInsets.statusBars.union(WindowInsets.displayCutout),
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChanged,
                placeholder = { Text(text = stringResource(CoreR.string.discover_search_hint)) },
                leadingIcon = {
                    Icon(
                        painter = painterResource(CoreR.drawable.ic_search),
                        contentDescription = null,
                    )
                },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChanged("") }) {
                            Icon(
                                painter = painterResource(CoreR.drawable.ic_x),
                                contentDescription = stringResource(CoreR.string.discover_clear_search),
                            )
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier =
                    Modifier.fillMaxWidth()
                        .padding(
                            horizontal = MaterialTheme.spacings.default,
                            vertical = MaterialTheme.spacings.small,
                        ),
            )

            if (state.query.isNotBlank()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small),
                    modifier = Modifier.padding(horizontal = MaterialTheme.spacings.default),
                ) {
                    FilterChip(
                        selected = filter == DiscoverFilter.ALL,
                        onClick = { filter = DiscoverFilter.ALL },
                        label = { Text(stringResource(CoreR.string.discover_filter_all)) },
                    )
                    FilterChip(
                        selected = filter == DiscoverFilter.MOVIES,
                        onClick = { filter = DiscoverFilter.MOVIES },
                        label = { Text(stringResource(CoreR.string.discover_filter_movies)) },
                    )
                    FilterChip(
                        selected = filter == DiscoverFilter.SHOWS,
                        onClick = { filter = DiscoverFilter.SHOWS },
                        label = { Text(stringResource(CoreR.string.discover_filter_shows)) },
                    )
                }
            }

            // Reserve the height so results don't jump when the indicator appears/disappears.
            Box(modifier = Modifier.fillMaxWidth().height(MaterialTheme.spacings.small)) {
                if (state.isSearching) {
                    LinearProgressIndicator(
                        modifier =
                            Modifier.fillMaxWidth()
                                .padding(horizontal = MaterialTheme.spacings.default)
                                .align(Alignment.Center)
                    )
                }
            }

            state.error?.let { error ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                        Modifier.fillMaxWidth()
                            .padding(
                                horizontal = MaterialTheme.spacings.default,
                                vertical = MaterialTheme.spacings.small,
                            )
                            .clip(MaterialTheme.shapes.small)
                            .background(MaterialTheme.colorScheme.errorContainer)
                            .padding(MaterialTheme.spacings.small),
                ) {
                    Icon(
                        painter = painterResource(CoreR.drawable.ic_alert_circle),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }

            when {
                state.query.isNotBlank() -> {
                    if (filteredResults.isEmpty() && !state.isSearching && state.error == null) {
                        EmptyHint(text = stringResource(CoreR.string.discover_no_results))
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(
                                items = filteredResults,
                                key = { "result-${it.mediaType}-${it.tmdbId}" },
                            ) { item ->
                                DiscoverResultRow(
                                    item = item,
                                    requestedThisSession = item.tmdbId in state.requestedTmdbIds,
                                    onRequest = { onRequest(item) },
                                )
                            }
                        }
                    }
                }
                state.recentRequests.isNotEmpty() -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        item {
                            Text(
                                text = stringResource(CoreR.string.discover_recent_requests),
                                style = MaterialTheme.typography.titleMedium,
                                modifier =
                                    Modifier.padding(
                                        horizontal = MaterialTheme.spacings.default,
                                        vertical = MaterialTheme.spacings.small,
                                    ),
                            )
                        }
                        items(items = state.recentRequests, key = { "request-${it.id}" }) { request
                            ->
                            Row(
                                modifier =
                                    Modifier.fillMaxWidth()
                                        .padding(
                                            horizontal = MaterialTheme.spacings.default,
                                            vertical = MaterialTheme.spacings.small,
                                        ),
                                horizontalArrangement =
                                    Arrangement.spacedBy(MaterialTheme.spacings.default),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                SeerrPoster(posterUrl = request.posterUrl)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = request.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = mediaTypeLabel(request.mediaType),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                StatusChip(status = request.mediaStatus)
                            }
                        }
                    }
                }
                else -> {
                    if (state.error == null) {
                        EmptyHint(text = stringResource(CoreR.string.discover_empty_hint))
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyHint(text: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(MaterialTheme.spacings.large),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = painterResource(CoreR.drawable.ic_sparkles),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp),
        )
        Spacer(modifier = Modifier.height(MaterialTheme.spacings.medium))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DiscoverResultRow(
    item: SeerrSearchItem,
    requestedThisSession: Boolean,
    onRequest: () -> Unit,
) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .padding(
                    horizontal = MaterialTheme.spacings.default,
                    vertical = MaterialTheme.spacings.small,
                ),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.default),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SeerrPoster(posterUrl = item.posterUrl)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text =
                    listOfNotNull(item.year?.toString(), mediaTypeLabel(item.mediaType))
                        .joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            item.overview?.let { overview ->
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = overview,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        when {
            requestedThisSession -> StatusChip(status = null)
            item.status == SeerrMediaStatus.NOT_REQUESTED ->
                Button(onClick = onRequest) {
                    Text(text = stringResource(CoreR.string.discover_request))
                }
            else -> StatusChip(status = item.status)
        }
    }
}

@Composable
private fun SeerrPoster(posterUrl: String?) {
    Box(
        modifier =
            Modifier.width(64.dp)
                .aspectRatio(2f / 3f)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceContainer)
    ) {
        if (posterUrl != null) {
            AsyncImage(
                model = posterUrl,
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
    }
}

/**
 * Colored pill making the item's lifecycle scannable at a glance. `null` renders the
 * just-requested-in-this-session marker (the search payload's own status is stale then).
 */
@Composable
private fun StatusChip(status: SeerrMediaStatus?) {
    data class ChipStyle(val textRes: Int, val container: Color, val content: Color)

    val style =
        when (status) {
            SeerrMediaStatus.AVAILABLE ->
                ChipStyle(
                    CoreR.string.discover_status_available,
                    MaterialTheme.colorScheme.primaryContainer,
                    MaterialTheme.colorScheme.onPrimaryContainer,
                )
            SeerrMediaStatus.PARTIALLY_AVAILABLE ->
                ChipStyle(
                    CoreR.string.discover_status_partially_available,
                    MaterialTheme.colorScheme.tertiaryContainer,
                    MaterialTheme.colorScheme.onTertiaryContainer,
                )
            SeerrMediaStatus.PROCESSING ->
                ChipStyle(
                    CoreR.string.discover_status_processing,
                    MaterialTheme.colorScheme.secondaryContainer,
                    MaterialTheme.colorScheme.onSecondaryContainer,
                )
            SeerrMediaStatus.PENDING ->
                ChipStyle(
                    CoreR.string.discover_status_pending,
                    MaterialTheme.colorScheme.secondaryContainer,
                    MaterialTheme.colorScheme.onSecondaryContainer,
                )
            SeerrMediaStatus.NOT_REQUESTED,
            null ->
                ChipStyle(
                    CoreR.string.discover_status_requested,
                    MaterialTheme.colorScheme.secondaryContainer,
                    MaterialTheme.colorScheme.onSecondaryContainer,
                )
        }
    Box(
        modifier =
            Modifier.clip(CircleShape)
                .background(style.container)
                .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = stringResource(style.textRes),
            style = MaterialTheme.typography.labelSmall,
            color = style.content,
        )
    }
}

@Composable
private fun mediaTypeLabel(mediaType: SeerrMediaType): String =
    stringResource(
        when (mediaType) {
            SeerrMediaType.MOVIE -> CoreR.string.discover_type_movie
            SeerrMediaType.TV -> CoreR.string.discover_type_show
        }
    )

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Preview
private fun DiscoverScreenLayoutPreview() {
    FindroidTheme {
        DiscoverScreenLayout(
            state =
                DiscoverState(
                    query = "dune",
                    results =
                        listOf(
                            SeerrSearchItem(
                                tmdbId = 438631,
                                mediaType = SeerrMediaType.MOVIE,
                                title = "Dune",
                                year = 2021,
                                overview =
                                    "Paul Atreides, a brilliant and gifted young man born into a great destiny.",
                                posterUrl = null,
                                status = SeerrMediaStatus.AVAILABLE,
                            ),
                            SeerrSearchItem(
                                tmdbId = 693134,
                                mediaType = SeerrMediaType.MOVIE,
                                title = "Dune: Part Two",
                                year = 2024,
                                overview = null,
                                posterUrl = null,
                                status = SeerrMediaStatus.NOT_REQUESTED,
                            ),
                        ),
                )
        )
    }
}
