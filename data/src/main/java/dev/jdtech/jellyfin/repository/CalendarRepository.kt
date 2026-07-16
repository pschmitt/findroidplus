package dev.jdtech.jellyfin.repository

import dev.jdtech.jellyfin.models.CalendarEntry

/**
 * Exposes upcoming Sonarr/Radarr releases (future air dates/release dates), merged and sorted by
 * date. Unlike [QueueStatusRepository], this is a plain on-demand suspend fetch - release dates
 * don't change minute to minute, so there's no WorkManager worker or `StateFlow`/polling loop
 * here, just a fetch-and-match called whenever the Calendar screen (or a show screen) needs fresh
 * data.
 */
interface CalendarRepository {
    /**
     * Returns upcoming calendar entries from [daysBack] days ago through [daysForward] days from
     * now, sorted by date ascending. Entries Findroid couldn't match to a local Jellyfin item are
     * still included (with a `null` item id) rather than dropped.
     */
    suspend fun getUpcoming(daysBack: Int = 3, daysForward: Int = 30): List<CalendarEntry>
}
