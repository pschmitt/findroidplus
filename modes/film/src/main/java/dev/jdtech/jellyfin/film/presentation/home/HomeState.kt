package dev.jdtech.jellyfin.film.presentation.home

import androidx.annotation.StringRes
import dev.jdtech.jellyfin.models.HomeItem
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
    val views: List<HomeItem.ViewItem> = emptyList(),
    val discoverSections: List<HomeDiscoverSection> = emptyList(),
    val isLoading: Boolean = false,
    val error: Exception? = null,
)
