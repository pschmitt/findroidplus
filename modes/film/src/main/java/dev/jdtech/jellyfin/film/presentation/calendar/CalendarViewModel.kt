package dev.jdtech.jellyfin.film.presentation.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jdtech.jellyfin.models.CalendarEntry
import dev.jdtech.jellyfin.models.CalendarResult
import dev.jdtech.jellyfin.repository.CalendarRepository
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
        load()
    }

    fun refresh() {
        load()
    }

    /**
     * Only blocks with a spinner when there's genuinely nothing to show yet (first load this
     * process, or a previous load never succeeded) - otherwise this is a silent background
     * refresh on top of whatever [CalendarCache]/[init] already put on screen, so reopening the
     * tab never re-blocks on a fresh Sonarr/Radarr/Jellyfin fetch the way it used to.
     */
    private fun load() {
        viewModelScope.launch {
            if (_state.value.isEmpty) {
                _state.value = _state.value.copy(isLoading = true, error = null)
            }
            try {
                val result = calendarRepository.getUpcoming()
                calendarCache.result = result
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
 * preserving order - a free function so it's directly unit-testable without a ViewModel/Hilt in
 * the loop, same convention as `buildPvrQueueGroups` in `DownloadsViewModel.kt`.
 */
internal fun groupByDate(entries: List<CalendarEntry>): List<Pair<LocalDate, List<CalendarEntry>>> =
    entries.groupBy { it.date }.map { (date, entriesForDate) -> date to entriesForDate }
