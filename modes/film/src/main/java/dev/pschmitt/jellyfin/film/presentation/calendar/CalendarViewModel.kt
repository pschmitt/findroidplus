package dev.pschmitt.jellyfin.film.presentation.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.pschmitt.jellyfin.models.CalendarEntry
import dev.pschmitt.jellyfin.models.CalendarResult
import dev.pschmitt.jellyfin.repository.CalendarCache
import dev.pschmitt.jellyfin.repository.CalendarRepository
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class CalendarViewModel
@Inject
constructor(
    private val calendarRepository: CalendarRepository,
    private val calendarCache: CalendarCache,
) : ViewModel() {
    private val _state = MutableStateFlow(CalendarState())
    val state = _state.asStateFlow()

    init {
        // Show the last-known result instantly (no spinner) when reopening the tab - see
        // CalendarCache's kdoc for why this is process-scoped rather than ViewModel-scoped.
        calendarCache.result?.let(::applyResult)
        load(force = false)
    }

    fun refresh() {
        load(force = true)
    }

    /**
     * Only blocks with a spinner when there's genuinely nothing to show yet (first load this
     * process, or a previous load never succeeded) - otherwise this is a silent background refresh
     * on top of whatever [CalendarCache]/[init] already put on screen, so reopening the tab never
     * re-blocks on a fresh Sonarr/Radarr/Jellyfin fetch the way it used to.
     *
     * [force] skips the cache's 12h TTL check - an explicit pull-to-refresh should always hit the
     * network, but the tab-reopen path in [init] shouldn't, since `PreloadCalendarWorker` (or a
     * previous tab visit) may well have fetched within the last few minutes already.
     */
    private fun load(force: Boolean) {
        if (!force && calendarCache.isFresh()) return
        viewModelScope.launch {
            if (_state.value.isEmpty) {
                _state.value = _state.value.copy(isLoading = true, error = null)
            }
            try {
                val result = calendarRepository.getUpcoming()
                calendarCache.update(result)
                applyResult(result)
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e)
            }
        }
    }

    private fun applyResult(result: CalendarResult) {
        _state.value =
            _state.value.copy(
                isLoading = false,
                error = null,
                groupedEntries = groupByDate(result.entries),
                serviceErrors = result.errors,
            )
    }
}

/**
 * Groups an already date-sorted (ascending) list of entries into consecutive same-date runs,
 * preserving order - a free function so it's directly unit-testable without a ViewModel/Hilt in the
 * loop, same convention as `buildPvrQueueGroups` in `DownloadsViewModel.kt`.
 */
internal fun groupByDate(entries: List<CalendarEntry>): List<Pair<LocalDate, List<CalendarEntry>>> =
    entries.groupBy { it.date }.map { (date, entriesForDate) -> date to entriesForDate }
