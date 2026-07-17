package dev.jdtech.jellyfin.film.presentation.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jdtech.jellyfin.models.CalendarEntry
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
) : ViewModel() {
    private val _state = MutableStateFlow(CalendarState())
    val state = _state.asStateFlow()

    init {
        load()
    }

    fun refresh() {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val result = calendarRepository.getUpcoming()
                _state.value =
                    _state.value.copy(
                        isLoading = false,
                        error = null,
                        groupedEntries = groupByDate(result.entries),
                        serviceErrors = result.errors,
                    )
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e)
            }
        }
    }
}

/**
 * Groups an already date-sorted (ascending) list of entries into consecutive same-date runs,
 * preserving order - a free function so it's directly unit-testable without a ViewModel/Hilt in
 * the loop, same convention as `buildPvrQueueGroups` in `DownloadsViewModel.kt`.
 */
internal fun groupByDate(entries: List<CalendarEntry>): List<Pair<LocalDate, List<CalendarEntry>>> =
    entries.groupBy { it.date }.map { (date, entriesForDate) -> date to entriesForDate }
