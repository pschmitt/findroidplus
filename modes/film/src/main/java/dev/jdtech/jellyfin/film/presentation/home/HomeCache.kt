package dev.jdtech.jellyfin.film.presentation.home

import dev.jdtech.jellyfin.models.HomeItem
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Process-scoped snapshot of everything [HomeViewModel.loadData] fetches, applied instantly on
 * `init` so a cold app start paints the last-known Home content on the very first frame instead
 * of a blank screen while the real network round trip is still in flight. [HomeViewModel.loadData]
 * always still runs immediately after and overwrites both the live state and this snapshot - this
 * is purely about first-paint latency, not a staleness/TTL cache like `CalendarCache`: Home content
 * (continue watching, next up) changes far more often than the calendar does, so there's no
 * "still fresh enough to skip the refresh" window here, only "instant vs. blank while it loads".
 */
@Singleton
class HomeCache @Inject constructor() {
    @Volatile var snapshot: HomeSnapshot? = null
}

data class HomeSnapshot(
    val suggestionsSection: HomeItem.Suggestions?,
    val resumeSection: HomeItem.Section?,
    val nextUpSection: HomeItem.Section?,
    val favoritesSection: HomeItem.Section?,
    val views: List<HomeItem.ViewItem>,
    val discoverSections: List<HomeDiscoverSection>,
    val pvrServiceIcons: List<Int>,
)
