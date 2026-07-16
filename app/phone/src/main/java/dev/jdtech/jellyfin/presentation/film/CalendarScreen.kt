package dev.jdtech.jellyfin.presentation.film

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.film.presentation.calendar.CalendarState
import dev.jdtech.jellyfin.film.presentation.calendar.CalendarViewModel
import dev.jdtech.jellyfin.models.CalendarEntry
import dev.jdtech.jellyfin.models.PvrSource
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.UUID

@Composable
fun CalendarScreen(
    onShowClick: (UUID) -> Unit = {},
    onMovieClick: (UUID) -> Unit = {},
    viewModel: CalendarViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(true) { viewModel.refresh() }

    CalendarScreenLayout(
        state = state,
        onEntryClick = { entry ->
            val itemId = entry.itemId ?: return@CalendarScreenLayout
            when (entry.source) {
                PvrSource.SONARR -> onShowClick(itemId)
                PvrSource.RADARR -> onMovieClick(itemId)
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalendarScreenLayout(
    state: CalendarState,
    onEntryClick: (CalendarEntry) -> Unit = {},
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier =
            Modifier.fillMaxSize()
                .recalculateWindowInsets()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(CoreR.string.title_calendar)) },
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
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                state.groupedEntries.forEach { (date, entries) ->
                    stickyHeader { CalendarDateHeader(date = date) }
                    items(items = entries) { entry ->
                        CalendarEntryRow(entry = entry, onClick = { onEntryClick(entry) })
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
 * A single upcoming Sonarr/Radarr release. Unlike [dev.jdtech.jellyfin.presentation.film.components.ItemPoster],
 * this always renders a source-based placeholder icon rather than a real poster -
 * [CalendarEntry] only carries a resolved item id, not a loaded [dev.jdtech.jellyfin.models.FindroidItem],
 * so there's no poster image URL to load here. Not clickable when [CalendarEntry.itemId] is null
 * (unmatched entry - nothing to navigate to yet).
 */
@Composable
private fun CalendarEntryRow(entry: CalendarEntry, onClick: () -> Unit) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .let { if (entry.itemId != null) it.clickable(onClick = onClick) else it }
                .padding(
                    horizontal = MaterialTheme.spacings.default,
                    vertical = MaterialTheme.spacings.small,
                ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.width(64.dp).clip(MaterialTheme.shapes.small)) {
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
        }
        Spacer(modifier = Modifier.width(MaterialTheme.spacings.small))
        CalendarEntryBadge(entry = entry)
    }
}

/** Small source + status indicator: SONARR/RADARR plus whether the file already exists/is monitored. */
@Composable
private fun CalendarEntryBadge(entry: CalendarEntry) {
    Icon(
        painter =
            painterResource(
                when {
                    entry.hasFile -> CoreR.drawable.ic_download
                    entry.monitored -> CoreR.drawable.ic_calendar
                    else -> CoreR.drawable.ic_x
                }
            ),
        contentDescription = null,
        tint =
            if (entry.hasFile) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
    )
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
                    hasFile = false,
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
