package dev.jdtech.jellyfin.film.presentation.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.cachedIn
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jdtech.jellyfin.models.CollectionType
import dev.jdtech.jellyfin.models.SeerrRequestItem
import dev.jdtech.jellyfin.models.SeerrSearchItem
import dev.jdtech.jellyfin.models.SortBy
import dev.jdtech.jellyfin.models.SortOrder
import dev.jdtech.jellyfin.pvr.PvrConfiguration
import dev.jdtech.jellyfin.repository.JellyfinRepository
import dev.jdtech.jellyfin.repository.QueueStatusRepository
import dev.jdtech.jellyfin.repository.SeerrRepository
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemKind

@HiltViewModel
class LibraryViewModel
@Inject
constructor(
    private val jellyfinRepository: JellyfinRepository,
    private val appPreferences: AppPreferences,
    private val libraryItemsCache: LibraryItemsCache,
    private val queueStatusRepository: QueueStatusRepository,
    private val seerrRepository: SeerrRepository,
    private val pvrConfiguration: PvrConfiguration,
) : ViewModel() {
    private val _state = MutableStateFlow(LibraryState())
    val state = _state.asStateFlow()

    private val eventsChannel = Channel<LibraryEvent>()
    val events = eventsChannel.receiveAsFlow()

    // Null means the merged "Media" view: all movies and shows across libraries.
    var parentId: UUID? = null
    lateinit var libraryType: CollectionType

    private val isMergedMedia: Boolean
        get() = parentId == null

    lateinit var sortBy: SortBy
    lateinit var sortOrder: SortOrder

    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            queueStatusRepository.getQueueStatusFlow().collect { queueStatusByItemId ->
                _state.value = _state.value.copy(queueStatus = queueStatusByItemId)
            }
        }
    }

    fun setup(parentId: UUID?, libraryType: CollectionType) {
        this.parentId = parentId
        this.libraryType = libraryType

        if (isMergedMedia) {
            val seerrConfigured = pvrConfiguration.isSeerrConfigured()
            _state.value = _state.value.copy(seerrConfigured = seerrConfigured)
            if (seerrConfigured) {
                loadRecentRequests()
            }
        }
    }

    fun loadItems() {
        val itemType =
            when {
                isMergedMedia ->
                    when (_state.value.filter) {
                        MediaFilter.ALL -> listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES)
                        MediaFilter.MOVIES -> listOf(BaseItemKind.MOVIE)
                        MediaFilter.SHOWS -> listOf(BaseItemKind.SERIES)
                    }
                else ->
                    when (libraryType) {
                        CollectionType.Movies -> listOf(BaseItemKind.MOVIE)
                        CollectionType.TvShows -> listOf(BaseItemKind.SERIES)
                        CollectionType.BoxSets -> listOf(BaseItemKind.BOX_SET)
                        CollectionType.Mixed,
                        CollectionType.Folders ->
                            listOf(BaseItemKind.FOLDER, BaseItemKind.MOVIE, BaseItemKind.SERIES)
                        else -> null
                    }
            }

        val recursive = itemType == null || !itemType.contains(BaseItemKind.FOLDER)

        viewModelScope.launch {
            _state.emit(_state.value.copy(isLoading = true, error = null))

            initSorting()

            // Jellyfin uses a different enum for sorting series by date played. In the merged
            // Media view that enum only applies when the view is narrowed to shows - a mixed
            // movie+series query can only use one of the two.
            val seriesOnly =
                libraryType == CollectionType.TvShows ||
                    (isMergedMedia && _state.value.filter == MediaFilter.SHOWS)
            val effectiveSortBy =
                if (seriesOnly && sortBy == SortBy.DATE_PLAYED) {
                    SortBy.SERIES_DATE_PLAYED
                } else {
                    sortBy
                }
            val searchTerm = _state.value.searchQuery.trim().ifBlank { null }

            try {
                val items =
                    if (searchTerm != null) {
                        // Search results are transient - never worth caching across ViewModel
                        // instances the way the plain browse view is below.
                        jellyfinRepository
                            .getItemsPaging(
                                parentId = parentId,
                                includeTypes = itemType,
                                recursive = recursive,
                                sortBy = effectiveSortBy,
                                sortOrder = sortOrder,
                                searchTerm = searchTerm,
                            )
                            .cachedIn(viewModelScope)
                    } else {
                        libraryItemsCache.get(
                            "$parentId:${_state.value.filter}:$effectiveSortBy:$sortOrder"
                        ) {
                            jellyfinRepository.getItemsPaging(
                                parentId = parentId,
                                includeTypes = itemType,
                                recursive = recursive,
                                sortBy = effectiveSortBy,
                                sortOrder = sortOrder,
                                searchTerm = null,
                            )
                        }
                    }
                _state.emit(_state.value.copy(items = items))
            } catch (e: Exception) {
                _state.emit(_state.value.copy(error = e))
            }
        }
    }

    private suspend fun initSorting() {
        if (!::sortBy.isInitialized || !::sortOrder.isInitialized) {
            sortBy = SortBy.fromString(appPreferences.getValue(appPreferences.sortBy))
            sortOrder = SortOrder.fromString(appPreferences.getValue(appPreferences.sortOrder))
            _state.emit(_state.value.copy(sortBy = sortBy, sortOrder = sortOrder))
        }
    }

    private fun setSorting(sortBy: SortBy, sortOrder: SortOrder) {
        this.sortBy = sortBy
        this.sortOrder = sortOrder
        viewModelScope.launch {
            _state.emit(_state.value.copy(sortBy = sortBy, sortOrder = sortOrder))
            appPreferences.setValue(appPreferences.sortBy, sortBy.toString())
            appPreferences.setValue(appPreferences.sortOrder, sortOrder.toString())
        }
    }

    fun onAction(action: LibraryAction) {
        when (action) {
            is LibraryAction.ChangeSorting -> {
                if (action.sortBy != this.sortBy || action.sortOrder != this.sortOrder) {
                    setSorting(sortBy = action.sortBy, sortOrder = action.sortOrder)
                    loadItems()
                }
            }
            is LibraryAction.OnSearchQueryChange -> {
                _state.value = _state.value.copy(searchQuery = action.query)
                searchJob?.cancel()
                if (action.query.isBlank()) {
                    _state.value =
                        _state.value.copy(
                            seerrResults = emptyList(),
                            seerrSearching = false,
                            seerrError = null,
                        )
                }
                searchJob =
                    viewModelScope.launch {
                        delay(SEARCH_DEBOUNCE_MS)
                        loadItems()
                        searchSeerr(action.query)
                    }
            }
            is LibraryAction.ChangeFilter -> {
                if (action.filter != _state.value.filter) {
                    _state.value = _state.value.copy(filter = action.filter)
                    loadItems()
                }
            }
            is LibraryAction.OnSeerrRequest -> requestSeerr(action.item)
            is LibraryAction.OnSeerrCancelRequest -> cancelSeerrRequest(action.request)
            else -> Unit
        }
    }

    /**
     * Merged-Media only: mirror the library search against Seerr so content that isn't on disk
     * shows up as requestable right below the library results.
     */
    private suspend fun searchSeerr(query: String) {
        if (!isMergedMedia || !_state.value.seerrConfigured || query.isBlank()) return

        _state.value = _state.value.copy(seerrSearching = true, seerrError = null)
        seerrRepository
            .search(query)
            .fold(
                onSuccess = { items ->
                    _state.value = _state.value.copy(seerrSearching = false, seerrResults = items)
                },
                onFailure = { e ->
                    _state.value =
                        _state.value.copy(
                            seerrSearching = false,
                            seerrResults = emptyList(),
                            seerrError = e.message,
                        )
                },
            )
    }

    private fun requestSeerr(item: SeerrSearchItem) {
        viewModelScope.launch {
            seerrRepository
                .request(item.tmdbId, item.mediaType)
                .fold(
                    onSuccess = {
                        _state.value =
                            _state.value.copy(
                                requestedTmdbIds = _state.value.requestedTmdbIds + item.tmdbId
                            )
                        eventsChannel.send(LibraryEvent.SeerrRequested(item.title))
                        loadRecentRequests()
                    },
                    onFailure = { e ->
                        eventsChannel.send(LibraryEvent.SeerrRequestFailed(e.message))
                    },
                )
        }
    }

    private fun cancelSeerrRequest(request: SeerrRequestItem) {
        viewModelScope.launch {
            seerrRepository
                .cancelRequest(request.id)
                .fold(
                    onSuccess = {
                        eventsChannel.send(LibraryEvent.SeerrRequestCancelled(request.title))
                        loadRecentRequests()
                    },
                    onFailure = { e ->
                        eventsChannel.send(LibraryEvent.SeerrCancelFailed(e.message))
                    },
                )
        }
    }

    private fun loadRecentRequests() {
        viewModelScope.launch {
            seerrRepository
                .getRecentRequests()
                .fold(
                    onSuccess = { requests ->
                        _state.value = _state.value.copy(recentRequests = requests)
                    },
                    // Non-fatal: the requests list is a bonus section, search errors are
                    // surfaced separately via seerrError.
                    onFailure = {},
                )
        }
    }

    companion object {
        private const val SEARCH_DEBOUNCE_MS = 300L
    }
}
