package dev.jdtech.jellyfin.film.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jdtech.jellyfin.backup.BackupDownloadedItemKind
import dev.jdtech.jellyfin.backup.decodePendingRestoreDownloads
import dev.jdtech.jellyfin.backup.encodePendingRestoreDownloads
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.database.ServerDatabaseDao
import dev.jdtech.jellyfin.film.R as FilmR
import dev.jdtech.jellyfin.models.CollectionType
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.HomeItem
import dev.jdtech.jellyfin.models.HomeSection
import dev.jdtech.jellyfin.models.UiText
import dev.jdtech.jellyfin.pvr.PvrConfiguration
import dev.jdtech.jellyfin.repository.JellyfinRepository
import dev.jdtech.jellyfin.repository.SeerrRepository
import dev.jdtech.jellyfin.repository.QueueStatusRepository
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import dev.jdtech.jellyfin.utils.Downloader
import dev.jdtech.jellyfin.utils.HomeSectionKeys
import dev.jdtech.jellyfin.utils.homeSectionOrderFromString
import dev.jdtech.jellyfin.utils.homeSectionOrderToString
import dev.jdtech.jellyfin.utils.resolveHomeSectionOrder
import dev.jdtech.jellyfin.utils.toView
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

@HiltViewModel
class HomeViewModel
@Inject
constructor(
    val repository: JellyfinRepository,
    val appPreferences: AppPreferences,
    val database: ServerDatabaseDao,
    val downloader: Downloader,
    private val seerrRepository: SeerrRepository,
    private val pvrConfiguration: PvrConfiguration,
    private val queueStatusRepository: QueueStatusRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(HomeState())
    val state = _state.asStateFlow()

    private val uuidSuggestions = UUID.fromString("31e47044-9b79-4bb0-99d0-0e477ed65420")
    private val uuidContinueWatching =
        UUID(4937169328197226115, -4704919157662094443) // 44845958-8326-4e83-beb4-c4f42e9eeb95
    private val uuidNextUp =
        UUID(1783371395749072194, -6164625418200444295) // 18bfced5-f237-4d42-aa72-d9d7fed19279

    private val uiTextContinueWatching = UiText.StringResource(FilmR.string.continue_watching)
    private val uiTextNextUp = UiText.StringResource(FilmR.string.next_up)

    init {
        viewModelScope.launch {
            queueStatusRepository.getQueueSnapshotFlow().collectLatest { snapshot ->
                _state.value =
                    _state.value.copy(
                        activeDownloads =
                            snapshot.entries.filter {
                                it.status.status == dev.jdtech.jellyfin.models.QueueItemStatus.DOWNLOADING
                            }
                    )
            }
        }
    }

    fun loadData() {
        Timber.i("Loading data")
        viewModelScope.launch(Dispatchers.Default) {
            _state.emit(_state.value.copy(isLoading = true, error = null))
            try {
                appPreferences.getValue(appPreferences.currentServer)?.let { serverId ->
                    loadServerName(serverId)
                    processPendingRestoreDownloads(serverId)
                }

                loadSuggestions()
                loadResumeItems()
                loadNextUpItems()
                loadViews()
                loadDiscover()
                loadPvrServiceIcons()
                recomputeSectionOrder()
            } catch (e: Exception) {
                _state.emit(_state.value.copy(error = e))
            }
            _state.emit(_state.value.copy(isLoading = false))
        }
    }

    fun refresh() = loadData()

    /**
     * Cheap, network-free re-read of the persisted section order (see
     * `core/.../utils/HomeSectionOrder.kt`) against whatever's already loaded - called when Home
     * resumes so a reorder made from the "Customize home screen" settings screen takes effect
     * immediately on returning, without a full [loadData] round trip.
     */
    fun refreshSectionOrder() {
        recomputeSectionOrder()
    }

    /**
     * Default order: Pending downloads, Latest Shows, Next Up, Continue Watching, Latest Movies,
     * Suggestions, Trending, Popular Shows, Popular Movies - views are split by library type
     * (TV/movie) so each lands next to its own "Latest ..." slot rather than grouped together.
     * Only used until the user actually reorders anything - from then on
     * [resolveHomeSectionOrder] keeps whatever they set and just appends genuinely new keys here.
     */
    private fun recomputeSectionOrder() {
        val current = _state.value
        val hidden =
            homeSectionOrderFromString(appPreferences.getValue(appPreferences.homeHiddenSections))
                .toSet()

        val showViews = current.views.filter { it.view.type == CollectionType.TvShows }
        val movieViews = current.views.filter { it.view.type == CollectionType.Movies }
        val otherViews =
            current.views.filterNot { it.view.type == CollectionType.TvShows || it.view.type == CollectionType.Movies }
        val discoverKeyOrder =
            listOf(
                FilmR.string.home_discover_trending,
                FilmR.string.home_discover_popular_shows,
                FilmR.string.home_discover_popular_movies,
            )
        val discoverByKey = current.discoverSections.associateBy { HomeSectionKeys.discover(it.titleRes) }

        val natural =
            buildList {
                    add(HomeSectionKeys.ACTIVE_DOWNLOADS)
                    addAll(showViews.map { HomeSectionKeys.view(it.view.id) })
                    if (current.nextUpSection != null) add(HomeSectionKeys.NEXT_UP)
                    if (current.resumeSection != null) add(HomeSectionKeys.CONTINUE_WATCHING)
                    addAll(movieViews.map { HomeSectionKeys.view(it.view.id) })
                    addAll(otherViews.map { HomeSectionKeys.view(it.view.id) })
                    if (current.suggestionsSection != null) add(HomeSectionKeys.SUGGESTIONS)
                    addAll(discoverKeyOrder.map { HomeSectionKeys.discover(it) }.filter { it in discoverByKey })
                }
                .filterNot { it in hidden }
        val persisted = homeSectionOrderFromString(appPreferences.getValue(appPreferences.homeSectionOrder))
        _state.value = current.copy(sectionOrder = resolveHomeSectionOrder(natural, persisted))
    }

    private suspend fun loadServerName(serverId: String) {
        val server = database.get(serverId)
        if (server != null) {
            _state.emit(_state.value.copy(server = server))
        }
    }

    // Restoring downloads requires an active, authenticated session against the right server,
    // which may not exist right after restore - so re-queuing is deferred to the next Home load
    // for the server the restored items actually belong to, rather than done immediately in
    // RestoreBackupScreen. Items belonging to other (not-yet-selected) servers are left pending.
    private suspend fun processPendingRestoreDownloads(serverId: String) {
        val json = appPreferences.getValue(appPreferences.pendingRestoreDownloads) ?: return
        val items =
            try {
                decodePendingRestoreDownloads(json)
            } catch (e: Exception) {
                Timber.e(e, "Failed to parse pending restore downloads")
                emptyList()
            }

        val (matching, remaining) = items.partition { it.serverId == serverId }

        for (item in matching) {
            try {
                val itemId = UUID.fromString(item.itemId)
                val findroidItem: FindroidItem? =
                    when (item.itemKind) {
                        BackupDownloadedItemKind.MOVIE -> repository.getMovie(itemId)
                        BackupDownloadedItemKind.EPISODE -> repository.getEpisode(itemId)
                        else -> null
                    }
                val sourceId = findroidItem?.sources?.firstOrNull()?.id ?: continue
                downloader.downloadItem(findroidItem, sourceId)
            } catch (e: Exception) {
                Timber.e(e, "Failed to queue restored download for item ${item.itemId}")
            }
        }

        appPreferences.setValue(
            appPreferences.pendingRestoreDownloads,
            if (remaining.isEmpty()) null else encodePendingRestoreDownloads(remaining),
        )
    }

    private suspend fun loadSuggestions() {
        Timber.i("Loading suggestions")
        if (!appPreferences.getValue(appPreferences.homeSuggestions)) {
            _state.emit(_state.value.copy(suggestionsSection = null))
            return
        }

        val items = repository.getSuggestions()

        val section =
            if (items.isEmpty()) {
                null
            } else {
                HomeItem.Suggestions(id = uuidSuggestions, items = items)
            }

        _state.emit(_state.value.copy(suggestionsSection = section))
    }

    private suspend fun loadResumeItems() {
        Timber.i("Loading resume items")
        if (!appPreferences.getValue(appPreferences.homeContinueWatching)) {
            _state.emit(_state.value.copy(resumeSection = null))
            return
        }

        val resumeItems = repository.getResumeItems()

        val section =
            if (resumeItems.isEmpty()) {
                null
            } else {
                HomeItem.Section(
                    HomeSection(uuidContinueWatching, uiTextContinueWatching, resumeItems)
                )
            }

        _state.emit(_state.value.copy(resumeSection = section))
    }

    private suspend fun loadNextUpItems() {
        Timber.i("Loading next up items")
        if (!appPreferences.getValue(appPreferences.homeNextUp)) {
            _state.emit(_state.value.copy(nextUpSection = null))
            return
        }

        val nextUpItems = repository.getNextUp()

        val section =
            if (nextUpItems.isEmpty()) {
                null
            } else {
                HomeItem.Section(HomeSection(uuidNextUp, uiTextNextUp, nextUpItems))
            }

        _state.emit(_state.value.copy(nextUpSection = section))
    }

    private suspend fun loadViews() {
        Timber.i("Loading views")
        val items =
            if (appPreferences.getValue(appPreferences.homeLatest)) {
                repository
                    .getUserViews()
                    .filter { view ->
                        CollectionType.fromString(view.collectionType?.serialName) in
                            CollectionType.supported
                    }
                    .map { view -> view to repository.getLatestMedia(view.id) }
                    .filter { (_, latest) -> latest.isNotEmpty() }
                    .map { (view, latest) -> view.toView(latest) }
                    .map { HomeItem.ViewItem(it) }
            } else {
                emptyList()
            }

        _state.emit(_state.value.copy(views = items))
    }

    /**
     * Seerr-backed discovery rows at the bottom of Home. Failures are silently dropped
     * (discovery is bonus content - a broken Seerr instance must not take down Home), and
     * sections that fail or come back empty simply don't appear.
     */
    private suspend fun loadDiscover() {
        if (
            !appPreferences.getValue(appPreferences.homeDiscover) ||
                !pvrConfiguration.isSeerrConfigured()
        ) {
            _state.emit(_state.value.copy(discoverSections = emptyList()))
            return
        }

        Timber.i("Loading Seerr discovery sections")
        val sections = coroutineScope {
            val trending =
                async { FilmR.string.home_discover_trending to seerrRepository.getTrending() }
            val movies =
                async {
                    FilmR.string.home_discover_popular_movies to
                        seerrRepository.getPopularMovies()
                }
            val shows =
                async {
                    FilmR.string.home_discover_popular_shows to seerrRepository.getPopularShows()
                }
            listOf(trending.await(), movies.await(), shows.await()).mapNotNull { (titleRes, result) ->
                result
                    .getOrNull()
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { HomeDiscoverSection(titleRes = titleRes, items = it) }
            }
        }

        _state.emit(_state.value.copy(discoverSections = sections))
    }

    private suspend fun loadPvrServiceIcons() {
        val icons =
            buildList {
                if (appPreferences.getValue(appPreferences.sonarrEnabled)) add(CoreR.drawable.ic_sonarr)
                if (appPreferences.getValue(appPreferences.radarrEnabled)) add(CoreR.drawable.ic_radarr)
            }
        _state.emit(_state.value.copy(pvrServiceIcons = icons))
    }

    fun onAction(action: HomeAction) {
        when (action) {
            is HomeAction.OnRetryClick -> {
                loadData()
            }
            is HomeAction.OnEnableOfflineMode -> {
                appPreferences.setValue(appPreferences.offlineMode, true)
            }
            is HomeAction.OnReorderSections -> {
                reorderSections(action.fromIndex, action.toIndex)
            }
            else -> Unit
        }
    }

    /**
     * Applies a long-press drag move made directly on the Home screen: mutates the already-
     * resolved [HomeState.sectionOrder] in place and persists it immediately, same as a move made
     * from the "Customize home screen" settings screen - both write the same preference, so
     * either surface stays in sync with the other.
     */
    private fun reorderSections(fromIndex: Int, toIndex: Int) {
        val order = _state.value.sectionOrder
        if (fromIndex !in order.indices || toIndex !in order.indices) return

        val reordered = order.toMutableList()
        val key = reordered.removeAt(fromIndex)
        reordered.add(toIndex, key)

        _state.value = _state.value.copy(sectionOrder = reordered)
        appPreferences.setValue(appPreferences.homeSectionOrder, homeSectionOrderToString(reordered))
    }
}
