package dev.jdtech.jellyfin.presentation.film

import android.widget.Toast
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
import dev.jdtech.jellyfin.api.pvr.PvrRelease
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.core.presentation.search.SearchEvent
import dev.jdtech.jellyfin.film.presentation.calendar.CalendarState
import dev.jdtech.jellyfin.film.presentation.calendar.CalendarViewModel
import dev.jdtech.jellyfin.models.CalendarEntry
import dev.jdtech.jellyfin.models.PvrSource
import dev.jdtech.jellyfin.presentation.film.components.PvrSearchButton
import dev.jdtech.jellyfin.presentation.film.components.ReleasePickerSheet
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings
import dev.jdtech.jellyfin.utils.ObserveAsEvents
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.UUID

@Composable
fun CalendarScreen(
    onSeasonClick: (UUID) -> Unit = {},
    onMovieClick: (UUID) -> Unit = {},
    viewModel: CalendarViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(true) { viewModel.refresh() }

    ObserveAsEvents(viewModel.searchEvents) { event ->
        val message =
            when (event) {
                is SearchEvent.SearchTriggered -> context.getString(CoreR.string.search_triggered_toast)
                is SearchEvent.ReleaseGrabbed -> context.getString(CoreR.string.release_grabbed_toast)
                is SearchEvent.Failed ->
                    context.getString(
                        CoreR.string.search_failed_toast,
                        event.message ?: context.getString(CoreR.string.unknown_error),
                    )
            }
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    CalendarScreenLayout(
        state = state,
        onEntryClick = { entry ->
            // entry.itemId is the season for Sonarr entries (see matchSonarrCalendar) - the
            // episode itself lives there, not on the show's overview page.
            val itemId = entry.itemId ?: return@CalendarScreenLayout
            when (entry.source) {
                PvrSource.SONARR -> onSeasonClick(itemId)
                PvrSource.RADARR -> onMovieClick(itemId)
            }
        },
        onSearchAutomatic = viewModel::searchAutomatic,
        onSearchManual = viewModel::openReleasePicker,
        onGrabRelease = viewModel::grabRelease,
        onDismissReleasePicker = viewModel::dismissReleasePicker,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalendarScreenLayout(
    state: CalendarState,
    onEntryClick: (CalendarEntry) -> Unit = {},
    onSearchAutomatic: (CalendarEntry) -> Unit = {},
    onSearchManual: (CalendarEntry) -> Unit = {},
    onGrabRelease: (PvrRelease) -> Unit = {},
    onDismissReleasePicker: () -> Unit = {},
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
                        CalendarEntryRow(
                            entry = entry,
                            onClick = { onEntryClick(entry) },
                            onSearchAutomatic =
                                if (entry.episodeId != null || entry.movieId != null) {
                                    { onSearchAutomatic(entry) }
                                } else {
                                    null
                                },
                            onSearchManual =
                                if (entry.episodeId != null || entry.movieId != null) {
                                    { onSearchManual(entry) }
                                } else {
                                    null
                                },
                        )
                    }
                }
            }
        }

        state.releasePicker?.let { releasePicker ->
            ReleasePickerSheet(
                state = releasePicker,
                onGrab = onGrabRelease,
                onDismissRequest = onDismissReleasePicker,
            )
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
 * placeholder icon for unmatched entries or if the poster fetch failed. Not clickable when
 * [CalendarEntry.itemId] is null (unmatched entry - nothing to navigate to yet).
 */
@Composable
private fun CalendarEntryRow(
    entry: CalendarEntry,
    onClick: () -> Unit,
    onSearchAutomatic: (() -> Unit)? = null,
    onSearchManual: (() -> Unit)? = null,
) {
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
            val posterUri = entry.images?.primary
            if (posterUri != null) {
                AsyncImage(
                    model = posterUri,
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
        }
        Spacer(modifier = Modifier.width(MaterialTheme.spacings.small))
        CalendarEntryBadge(entry = entry)
        if (onSearchAutomatic != null && onSearchManual != null) {
            PvrSearchButton(onAutomaticSearch = onSearchAutomatic, onManualSearch = onSearchManual)
        }
    }
}

/**
 * PVR status indicator - NOT related to Findroid's own on-device downloads (compare
 * [dev.jdtech.jellyfin.presentation.film.components.ItemButtonsBar]'s download button). This
 * reflects whether Sonarr/Radarr itself has already grabbed/imported the file on the *server*:
 * - [CalendarEntry.hasFile]: Sonarr/Radarr already has it - it should already be in Jellyfin.
 * - [CalendarEntry.monitored] (and no file yet): tracked, still upcoming.
 * - neither: Sonarr/Radarr isn't monitoring this release at all.
 */
@Composable
private fun CalendarEntryBadge(entry: CalendarEntry) {
    val (icon, description) =
        when {
            entry.hasFile -> CoreR.drawable.ic_download to CoreR.string.calendar_status_available
            entry.monitored -> CoreR.drawable.ic_calendar to CoreR.string.calendar_status_upcoming
            else -> CoreR.drawable.ic_x to CoreR.string.calendar_status_unmonitored
        }
    Icon(
        painter = painterResource(icon),
        contentDescription = stringResource(description),
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
