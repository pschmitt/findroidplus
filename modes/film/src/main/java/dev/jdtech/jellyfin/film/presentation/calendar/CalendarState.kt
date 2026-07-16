package dev.jdtech.jellyfin.film.presentation.calendar

import dev.jdtech.jellyfin.models.CalendarEntry
import java.time.LocalDate

/**
 * [groupedEntries] is [CalendarRepository][dev.jdtech.jellyfin.repository.CalendarRepository]'s
 * flat, date-sorted list re-grouped by date for the sticky-header UI - already sorted ascending by
 * date (the repository's contract), so no re-sorting is needed here, just grouping consecutive
 * same-date runs together.
 */
data class CalendarState(
    val isLoading: Boolean = false,
    val error: Exception? = null,
    val groupedEntries: List<Pair<LocalDate, List<CalendarEntry>>> = emptyList(),
) {
    val isEmpty: Boolean
        get() = groupedEntries.isEmpty()
}
