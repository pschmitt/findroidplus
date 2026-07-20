package dev.jdtech.jellyfin.film.presentation.homelayout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.film.R as FilmR
import dev.jdtech.jellyfin.models.CollectionType
import dev.jdtech.jellyfin.models.UiText
import dev.jdtech.jellyfin.pvr.PvrConfiguration
import dev.jdtech.jellyfin.repository.JellyfinRepository
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import dev.jdtech.jellyfin.utils.HomeSectionKeys
import dev.jdtech.jellyfin.utils.homeSectionOrderFromString
import dev.jdtech.jellyfin.utils.homeSectionOrderToString
import dev.jdtech.jellyfin.utils.resolveHomeSectionOrder
import javax.inject.Inject
import org.jellyfin.sdk.model.api.BaseItemDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

@HiltViewModel
class HomeLayoutSettingsViewModel
@Inject
constructor(
    private val repository: JellyfinRepository,
    private val appPreferences: AppPreferences,
    private val pvrConfiguration: PvrConfiguration,
) : ViewModel() {
    private val _state = MutableStateFlow(HomeLayoutSettingsState())
    val state = _state.asStateFlow()

    /** A label plus the PVR/Seerr brand icons that row's content actually depends on. */
    private data class SectionInfo(val label: UiText, val serviceIcons: List<Int> = emptyList())

    /**
     * Every known (key, info) pair regardless of hidden status - populated once by [load] (it's
     * the only part of this screen that needs a repository round trip, for library names), then
     * reused by [hide]/[restore] to recompute the visible/hidden split without refetching.
     */
    private var cachedLabels: LinkedHashMap<String, SectionInfo> = LinkedHashMap()

    /**
     * Builds [cachedLabels] in the same default order as
     * [dev.jdtech.jellyfin.film.presentation.home.HomeViewModel.recomputeSectionOrder]: Pending
     * downloads, Latest Shows, Next Up, Continue Watching, Latest Movies, Suggestions, Trending,
     * Popular Shows, Popular Movies - so a freshly-installed layout matches between this screen
     * and Home before the user ever touches an order/hide control.
     */
    fun load() {
        viewModelScope.launch {
            _state.emit(_state.value.copy(isLoading = true))

            val labels = LinkedHashMap<String, SectionInfo>()
            val jellyfinIcons = listOf(CoreR.drawable.ic_logo)

            // The PVR queue mixes Sonarr and Radarr entries directly (not through Seerr), so it
            // only wears the icon(s) for whichever of those two are actually configured.
            val pvrIcons =
                buildList {
                    if (appPreferences.getValue(appPreferences.sonarrEnabled)) add(CoreR.drawable.ic_sonarr)
                    if (appPreferences.getValue(appPreferences.radarrEnabled)) add(CoreR.drawable.ic_radarr)
                }
            labels[HomeSectionKeys.ACTIVE_DOWNLOADS] =
                SectionInfo(UiText.StringResource(CoreR.string.pvr_queue_section_title), pvrIcons)

            var showViews = emptyList<BaseItemDto>()
            var movieViews = emptyList<BaseItemDto>()
            var otherViews = emptyList<BaseItemDto>()
            if (appPreferences.getValue(appPreferences.homeLatest)) {
                try {
                    val views =
                        repository.getUserViews().filter { view ->
                            CollectionType.fromString(view.collectionType?.serialName) in
                                CollectionType.supported
                        }
                    showViews =
                        views.filter {
                            CollectionType.fromString(it.collectionType?.serialName) ==
                                CollectionType.TvShows
                        }
                    movieViews =
                        views.filter {
                            CollectionType.fromString(it.collectionType?.serialName) ==
                                CollectionType.Movies
                        }
                    otherViews = views.filterNot { it in showViews || it in movieViews }
                } catch (e: Exception) {
                    Timber.e(e, "Failed to load library views for home layout settings")
                }
            }

            fun addView(view: BaseItemDto) {
                labels[HomeSectionKeys.view(view.id)] =
                    SectionInfo(
                        UiText.StringResource(FilmR.string.latest_library, view.name.orEmpty()),
                        jellyfinIcons,
                    )
            }
            showViews.forEach(::addView)

            if (appPreferences.getValue(appPreferences.homeNextUp)) {
                labels[HomeSectionKeys.NEXT_UP] =
                    SectionInfo(UiText.StringResource(FilmR.string.next_up), jellyfinIcons)
            }
            if (appPreferences.getValue(appPreferences.homeContinueWatching)) {
                labels[HomeSectionKeys.CONTINUE_WATCHING] =
                    SectionInfo(UiText.StringResource(FilmR.string.continue_watching), jellyfinIcons)
            }

            movieViews.forEach(::addView)
            otherViews.forEach(::addView)

            if (appPreferences.getValue(appPreferences.homeSuggestions)) {
                labels[HomeSectionKeys.SUGGESTIONS] =
                    SectionInfo(
                        UiText.StringResource(FilmR.string.home_section_suggestions),
                        jellyfinIcons,
                    )
            }

            if (
                appPreferences.getValue(appPreferences.homeDiscover) &&
                    pvrConfiguration.isSeerrConfigured()
            ) {
                // The Discover rows are always Seerr-backed content, regardless of media type -
                // Radarr/Sonarr only come into it once a specific title is actually requested.
                val seerrIcons = listOf(CoreR.drawable.ic_seerr)
                labels[HomeSectionKeys.discover(FilmR.string.home_discover_trending)] =
                    SectionInfo(UiText.StringResource(FilmR.string.home_discover_trending), seerrIcons)
                labels[HomeSectionKeys.discover(FilmR.string.home_discover_popular_shows)] =
                    SectionInfo(
                        UiText.StringResource(FilmR.string.home_discover_popular_shows),
                        seerrIcons,
                    )
                labels[HomeSectionKeys.discover(FilmR.string.home_discover_popular_movies)] =
                    SectionInfo(
                        UiText.StringResource(FilmR.string.home_discover_popular_movies),
                        seerrIcons,
                    )
            }

            cachedLabels = labels
            recomputeRows()
            _state.value = _state.value.copy(isLoading = false)
        }
    }

    fun onAction(action: HomeLayoutSettingsAction) {
        when (action) {
            is HomeLayoutSettingsAction.OnMoveUp -> move(action.index, action.index - 1)
            is HomeLayoutSettingsAction.OnMoveDown -> move(action.index, action.index + 1)
            is HomeLayoutSettingsAction.OnHide -> hide(action.key)
            is HomeLayoutSettingsAction.OnRestore -> restore(action.key)
            is HomeLayoutSettingsAction.OnResetLayout -> resetLayout()
        }
    }

    private fun move(from: Int, to: Int) {
        val rows = _state.value.rows
        if (from !in rows.indices || to !in rows.indices) return

        val reordered = rows.toMutableList()
        val item = reordered.removeAt(from)
        reordered.add(to, item)

        _state.value = _state.value.copy(rows = reordered)
        appPreferences.setValue(
            appPreferences.homeSectionOrder,
            homeSectionOrderToString(reordered.map { it.key }),
        )
    }

    private fun hide(key: String) {
        val hidden = currentHiddenKeys().toMutableList()
        if (key !in hidden) hidden.add(key)
        appPreferences.setValue(appPreferences.homeHiddenSections, homeSectionOrderToString(hidden))
        recomputeRows()
    }

    private fun restore(key: String) {
        val hidden = currentHiddenKeys().toMutableList()
        hidden.remove(key)
        appPreferences.setValue(appPreferences.homeHiddenSections, homeSectionOrderToString(hidden))
        recomputeRows()
    }

    private fun currentHiddenKeys(): List<String> =
        homeSectionOrderFromString(appPreferences.getValue(appPreferences.homeHiddenSections))

    private fun recomputeRows() {
        val hidden = currentHiddenKeys().toSet()
        val natural = cachedLabels.keys.toList()

        val visibleNatural = natural.filterNot { it in hidden }
        val persisted =
            homeSectionOrderFromString(appPreferences.getValue(appPreferences.homeSectionOrder))
        val order = resolveHomeSectionOrder(visibleNatural, persisted)
        val rows =
            order.mapNotNull { key ->
                cachedLabels[key]?.let { HomeLayoutRow(key, it.label, it.serviceIcons) }
            }

        val hiddenRows =
            natural.filter { it in hidden }.mapNotNull { key ->
                cachedLabels[key]?.let { HomeLayoutRow(key, it.label, it.serviceIcons) }
            }

        _state.value = _state.value.copy(rows = rows, hiddenRows = hiddenRows)
    }

    /** Clears both the persisted order and the hidden set, back to the default layout. */
    private fun resetLayout() {
        appPreferences.setValue(appPreferences.homeSectionOrder, null)
        appPreferences.setValue(appPreferences.homeHiddenSections, null)
        recomputeRows()
    }
}
