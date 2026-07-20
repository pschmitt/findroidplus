package dev.jdtech.jellyfin.presentation.film

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.recalculateWindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import dev.jdtech.jellyfin.PlayerActivity
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.film.presentation.calendar.CalendarState
import dev.jdtech.jellyfin.film.presentation.calendar.CalendarViewModel
import dev.jdtech.jellyfin.models.CalendarEntry
import dev.jdtech.jellyfin.models.PvrSource
import dev.jdtech.jellyfin.presentation.components.TopBarTitle
import dev.jdtech.jellyfin.presentation.film.components.PvrErrorBanner
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings
import dev.jdtech.jellyfin.utils.formatCalendarTime
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.UUID
import org.jellyfin.sdk.model.api.BaseItemKind

@Composable
fun CalendarScreen(
    onSeasonClick: (UUID) -> Unit = {},
    onEpisodeClick: (UUID) -> Unit = {},
    onMovieClick: (UUID) -> Unit = {},
    onSeerrClick: (CalendarEntry) -> Unit = {},
    onSettingsClick: () -> Unit = {},
    viewModel: CalendarViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(true) { viewModel.refresh() }

    CalendarScreenLayout(
        state = state,
        onEntryClick = { entry ->
            // entry.itemId is the season for Sonarr entries (see matchSonarrCalendar) - the
            // episode itself lives there, not on the show's overview page.
            val itemId = entry.itemId
            val tmdbId = entry.tmdbId
            val episodeItemId = entry.episodeItemId
            when {
                entry.hasFile && episodeItemId != null -> onEpisodeClick(episodeItemId)
                entry.source == PvrSource.SONARR && tmdbId != null -> onSeerrClick(entry)
                itemId != null ->
                    when (entry.source) {
                        PvrSource.SONARR -> onSeasonClick(itemId)
                        PvrSource.RADARR -> onMovieClick(itemId)
                    }
                // Not in the library yet - fall back to the Seerr detail view, where the item
                // can be inspected and requested.
                tmdbId != null -> onSeerrClick(entry)
            }
        },
        onRefresh = viewModel::refresh,
        onSettingsClick = onSettingsClick,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalendarScreenLayout(
    state: CalendarState,
    onEntryClick: (CalendarEntry) -> Unit = {},
    onRefresh: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier =
            Modifier.fillMaxSize()
                .recalculateWindowInsets()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    TopBarTitle(
                        text = stringResource(CoreR.string.title_calendar),
                        iconRes = CoreR.drawable.ic_calendar,
                    )
                },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            painter = painterResource(CoreR.drawable.ic_settings),
                            contentDescription = stringResource(CoreR.string.title_settings),
                        )
                    }
                },
                windowInsets = WindowInsets.statusBars.union(WindowInsets.displayCutout),
                scrollBehavior = scrollBehavior,
            )
        },
        contentWindowInsets = WindowInsets.statusBars.union(WindowInsets.displayCutout),
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (state.isEmpty && !state.isLoading) {
                Text(
                    text = stringResource(CoreR.string.calendar_empty),
                    modifier = Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            PullToRefreshBox(isRefreshing = state.isLoading, onRefresh = onRefresh) {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                if (state.serviceErrors.isNotEmpty()) {
                    item {
                        PvrErrorBanner(
                            errors = state.serviceErrors,
                            modifier =
                                Modifier.padding(
                                    horizontal = MaterialTheme.spacings.default,
                                    vertical = MaterialTheme.spacings.small,
                                ),
                        )
                    }
                }
                state.groupedEntries.forEach { (date, entries) ->
                    stickyHeader { CalendarDateHeader(date = date) }
                    items(items = entries) { entry ->
                        CalendarEntryRow(
                            entry = entry,
                            clickable =
                                entry.itemId != null || entry.tmdbId != null,
                            onClick = { onEntryClick(entry) },
                        )
                    }
                }
                }
            }
        }
    }
}

@Composable
private fun CalendarDateHeader(date: LocalDate) {
    Card {
        Text(
            text = formatCalendarDate(date),
            modifier =
                Modifier.fillMaxWidth()
                    .padding(
                        horizontal = MaterialTheme.spacings.medium,
                        vertical = MaterialTheme.spacings.medium,
                    ),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

/**
 * "Today"/"Tomorrow" for the two nearest days (the cases a user checking "what's coming up" cares
 * about most), a localized medium-length date otherwise (e.g. "Jul 24, 2026").
 */
private fun formatCalendarDate(date: LocalDate): String {
    val today = LocalDate.now()
    return when (date) {
        today -> CALENDAR_TODAY_PLACEHOLDER
        today.plusDays(1) -> CALENDAR_TOMORROW_PLACEHOLDER
        else -> date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    }
}

// Replaced with the localized string resource by the composable below - kept as a plain function
// above so date-grouping/formatting logic can be unit tested without a Compose runtime. See
// CalendarDateHeader for the string-resource-aware formatting actually rendered on screen.
private const val CALENDAR_TODAY_PLACEHOLDER = "Today"
private const val CALENDAR_TOMORROW_PLACEHOLDER = "Tomorrow"

/**
 * A single upcoming Sonarr/Radarr release. Renders the matched item's real poster
 * ([CalendarEntry.images], fetched by [dev.jdtech.jellyfin.repository.CalendarRepositoryImpl]
 * once [CalendarEntry.itemId] resolves) when available, falling back to a source-based
 * placeholder icon for unmatched entries or if the poster fetch failed. [clickable] is decided
 * by the caller: matched entries navigate into the library, unmatched ones with a TMDB id open
 * the Seerr detail view.
 */
@Composable
private fun CalendarEntryRow(
    entry: CalendarEntry,
    clickable: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .let { if (clickable) it.clickable(onClick = onClick) else it }
                .padding(
                    horizontal = MaterialTheme.spacings.default,
                    vertical = MaterialTheme.spacings.small,
                ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.width(64.dp).clip(MaterialTheme.shapes.small)) {
            val poster = entry.images?.primary ?: entry.posterUrl
            if (poster != null) {
                AsyncImage(
                    model = poster,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier =
                        Modifier.fillMaxWidth()
                            .aspectRatio(0.66f)
                            .background(MaterialTheme.colorScheme.surfaceContainer),
                )
            } else {
                Box(
                    modifier =
                        Modifier.fillMaxWidth()
                            .aspectRatio(0.66f)
                            .background(MaterialTheme.colorScheme.surfaceContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter =
                            painterResource(
                                if (entry.source == PvrSource.SONARR) CoreR.drawable.ic_tv
                                else CoreR.drawable.ic_film
                            ),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.width(MaterialTheme.spacings.default))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            entry.subtitle?.let { subtitle ->
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // Exact air time in the device's time zone - only known for Sonarr entries, whose
            // airDateUtc is a full instant (Radarr release dates are date-only).
            entry.airTime?.let { airTime ->
                Text(
                    text = stringResource(CoreR.string.calendar_air_time, formatCalendarTime(airTime)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (entry.hasFile) {
            entry.playbackTarget?.let { (itemId, itemKind) ->
                Spacer(modifier = Modifier.width(MaterialTheme.spacings.small))
                CalendarPlayButton(itemId = itemId, itemKind = itemKind)
            }
        }
    }
}

/**
 * The playable Jellyfin item for an entry Sonarr/Radarr already has a file for - null if it
 * hasn't been matched into the library yet ([CalendarEntry.episodeItemId]/[CalendarEntry.itemId]
 * unresolved), in which case there's nothing to play.
 */
private val CalendarEntry.playbackTarget: Pair<UUID, BaseItemKind>?
    get() =
        when (source) {
            PvrSource.SONARR -> episodeItemId?.let { it to BaseItemKind.EPISODE }
            PvrSource.RADARR -> itemId?.let { it to BaseItemKind.MOVIE }
        }

/**
 * Launches playback directly (bypassing the detail screen), the same Intent-to-PlayerActivity
 * call MovieScreen/EpisodeScreen use for their own Play actions - a plain checkmark or download
 * icon here would either clash with "watched" elsewhere in the app or read as an on-device
 * download action, so this is an actual shortcut instead of a status glyph.
 */
@Composable
private fun CalendarPlayButton(itemId: UUID, itemKind: BaseItemKind) {
    val context = LocalContext.current
    IconButton(
        onClick = {
            val intent = Intent(context, PlayerActivity::class.java)
            intent.putExtra("itemId", itemId.toString())
            intent.putExtra("itemKind", itemKind.serialName)
            context.startActivity(intent)
        }
    ) {
        Icon(painter = painterResource(CoreR.drawable.ic_play), contentDescription = stringResource(CoreR.string.play))
    }
}

private val dummyCalendarEntries: List<Pair<LocalDate, List<CalendarEntry>>> =
    listOf(
        LocalDate.now() to
            listOf(
                CalendarEntry(
                    date = LocalDate.now(),
                    source = PvrSource.SONARR,
                    title = "House of the Dragon",
                    subtitle = "S03E05 - The Red Dragon and the Gold",
                    itemId = UUID.randomUUID(),
                    episodeItemId = UUID.randomUUID(),
                    hasFile = true,
                    monitored = true,
                )
            ),
        LocalDate.now().plusDays(1) to
            listOf(
                CalendarEntry(
                    date = LocalDate.now().plusDays(1),
                    source = PvrSource.RADARR,
                    title = "Some Upcoming Movie",
                    subtitle = null,
                    itemId = UUID.randomUUID(),
                    hasFile = false,
                    monitored = true,
                )
            ),
        LocalDate.now().plusDays(4) to
            listOf(
                CalendarEntry(
                    date = LocalDate.now().plusDays(4),
                    source = PvrSource.SONARR,
                    title = "Some Unsynced Show",
                    subtitle = "S01E02 - Pilot",
                    itemId = null,
                    hasFile = false,
                    monitored = true,
                )
            ),
    )

@PreviewScreenSizes
@Composable
private fun CalendarScreenLayoutPreview() {
    FindroidTheme { CalendarScreenLayout(state = CalendarState(groupedEntries = dummyCalendarEntries)) }
}

@PreviewScreenSizes
@Composable
private fun CalendarScreenLayoutEmptyPreview() {
    FindroidTheme { CalendarScreenLayout(state = CalendarState()) }
}
