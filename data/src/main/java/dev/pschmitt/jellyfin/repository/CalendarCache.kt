package dev.pschmitt.jellyfin.repository

import dev.pschmitt.jellyfin.models.CalendarResult
import java.util.concurrent.TimeUnit

/**
 * `CalendarViewModel` is recreated every time the Calendar tab is navigated to (it's a plain
 * `hiltViewModel()` scoped to the Navigation-Compose backstack entry, same as `LibraryViewModel`),
 * so without a wider-scoped cache every reopen re-hit Sonarr/Radarr/Jellyfin from scratch even
 * when the last fetch was only seconds old - reported as "overkill" by the user. `@Singleton`
 * (rather than `LibraryItemsCache`'s `@ApplicationScope`-launched approach) since there's no Flow
 * to keep alive here, just the last successful [CalendarResult] snapshot to hand back instantly
 * while `CalendarViewModel` kicks off a background refresh. Deliberately in-memory only, not
 * persisted to disk - a fresh process still fetches once, same as before this cache existed.
 *
 * Lives in `data` (rather than `modes:film`, where it originally lived) so `core`'s
 * `PreloadCalendarWorker` can share the exact same cache/TTL as `CalendarViewModel` - `core`
 * depends on `data` but not on `modes:film`. `data` has no Hilt plugin (same rationale as
 * `CalendarRepositoryImpl`), so this is a plain class provided as a `@Singleton` via
 * `dev.pschmitt.jellyfin.di.CalendarModule` in `core` rather than an `@Inject constructor`.
 *
 * [isFresh] backs the 12h TTL: a background preload at app startup skips the network round trip
 * entirely if the last successful fetch is still within that window, and `CalendarViewModel`'s own
 * background refresh on tab-reopen does the same - only an explicit pull-to-refresh forces a fetch
 * regardless of freshness.
 */
class CalendarCache {
    @Volatile
    var result: CalendarResult? = null
        private set

    @Volatile private var lastFetchedAtMillis: Long? = null

    fun update(result: CalendarResult) {
        this.result = result
        this.lastFetchedAtMillis = System.currentTimeMillis()
    }

    fun isFresh(ttlMillis: Long = DEFAULT_TTL_MILLIS): Boolean {
        val fetchedAt = lastFetchedAtMillis ?: return false
        return System.currentTimeMillis() - fetchedAt < ttlMillis
    }

    companion object {
        val DEFAULT_TTL_MILLIS: Long = TimeUnit.HOURS.toMillis(12)
    }
}
