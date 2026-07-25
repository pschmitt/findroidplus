package dev.jdtech.jellyfin.film.presentation.home

import androidx.annotation.StringRes
import dev.jdtech.jellyfin.models.HomeItem
import dev.jdtech.jellyfin.models.PvrQueueEntry
import dev.jdtech.jellyfin.models.SeerrSearchItem
import dev.jdtech.jellyfin.models.Server

/** A Seerr-backed discovery row (trending/popular) shown below the library sections. */
data class HomeDiscoverSection(
    @param:StringRes val titleRes: Int,
    val items: List<SeerrSearchItem>,
)

data class HomeState(
    val server: Server? = null,
    val suggestionsSection: HomeItem.Suggestions? = null,
    val resumeSection: HomeItem.Section? = null,
    val nextUpSection: HomeItem.Section? = null,
    val favoritesSection: HomeItem.Section? = null,
    val views: List<HomeItem.ViewItem> = emptyList(),
    val discoverSections: List<HomeDiscoverSection> = emptyList(),
    val activeDownloads: List<PvrQueueEntry> = emptyList(),
    // The Sonarr/Radarr brand icons (drawable ids) for whichever of those two are actually
    // enabled - shown next to the "Pending downloads" title, same idea as the service icons in
    // "Customize home screen".
    val pvrServiceIcons: List<Int> = emptyList(),
    // Fully resolved render order for the sections above - see `HomeSectionKeys`/
    // `resolveHomeSectionOrder` (core/.../utils/HomeSectionOrder.kt). Recomputed whenever the
    // underlying sections change and whenever Home resumes (to pick up a reorder made from the
    // "Customize home screen" settings screen without a full data reload).
    val sectionOrder: List<String> = emptyList(),
    val isLoading: Boolean = false,
    val error: Exception? = null,
)
