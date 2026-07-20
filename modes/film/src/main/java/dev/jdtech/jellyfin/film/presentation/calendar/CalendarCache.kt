package dev.jdtech.jellyfin.film.presentation.calendar

import dev.jdtech.jellyfin.models.CalendarResult
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [CalendarViewModel] is recreated every time the Calendar tab is navigated to (it's a plain
 * `hiltViewModel()` scoped to the Navigation-Compose backstack entry, same as `LibraryViewModel`),
 * so without a wider-scoped cache every reopen re-hit Sonarr/Radarr/Jellyfin from scratch even
 * when the last fetch was only seconds old - reported as "overkill" by the user. `@Singleton`
 * (rather than `LibraryItemsCache`'s `@ApplicationScope`-launched approach) since there's no Flow
 * to keep alive here, just the last successful [CalendarResult] snapshot to hand back instantly
 * while [CalendarViewModel] kicks off a background refresh. Deliberately in-memory only, not
 * persisted to disk - a fresh process still fetches once, same as before this cache existed.
 */
@Singleton
class CalendarCache @Inject constructor() {
    @Volatile var result: CalendarResult? = null
}
