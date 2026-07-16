package dev.jdtech.jellyfin.utils

import dev.jdtech.jellyfin.models.FindroidShow
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val calendarDateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())

/**
 * Formats a [LocalDate] for display in calendar-style UI (e.g. the Calendar tab or the ShowScreen
 * "next episode airs" banner), e.g. "Jul 24".
 */
fun formatCalendarDate(date: LocalDate): String = date.format(calendarDateFormatter)

fun getShowDateString(item: FindroidShow): String {
    val dateRange: MutableList<String> = mutableListOf()
    item.productionYear?.let { dateRange.add(it.toString()) }
    when (item.status) {
        "Continuing" -> {
            dateRange.add("Present")
        }

        "Ended" -> {
            item.endDate?.let { dateRange.add(it.year.toString()) }
        }
    }
    if (dateRange.count() > 1 && dateRange[0] == dateRange[1]) return dateRange[0]
    return dateRange.joinToString(separator = " - ")
}
