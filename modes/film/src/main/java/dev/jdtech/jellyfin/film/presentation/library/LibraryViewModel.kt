package dev.jdtech.jellyfin.film.presentation.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.cachedIn
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jdtech.jellyfin.models.CollectionType
import dev.jdtech.jellyfin.models.SortBy
import dev.jdtech.jellyfin.models.SortOrder
import dev.jdtech.jellyfin.repository.JellyfinRepository
import dev.jdtech.jellyfin.repository.QueueStatusRepository
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
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
) : ViewModel() {
    private val _state = MutableStateFlow(LibraryState())
    val state = _state.asStateFlow()

    lateinit var parentId: UUID
    lateinit var libraryType: CollectionType

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

    fun setup(parentId: UUID, libraryType: CollectionType) {
        this.parentId = parentId
        this.libraryType = libraryType
    }

    fun loadItems() {
        val itemType =
            when (libraryType) {
                CollectionType.Movies -> listOf(BaseItemKind.MOVIE)
                CollectionType.TvShows -> listOf(BaseItemKind.SERIES)
                CollectionType.BoxSets -> listOf(BaseItemKind.BOX_SET)
                CollectionType.Mixed,
                CollectionType.Folders ->
                    listOf(BaseItemKind.FOLDER, BaseItemKind.MOVIE, BaseItemKind.SERIES)
                else -> null
            }

        val recursive = itemType == null || !itemType.contains(BaseItemKind.FOLDER)

        viewModelScope.launch {
            _state.emit(_state.value.copy(isLoading = true, error = null))

            initSorting()

            // Jellyfin uses a different enum for sorting series by date played.
            val effectiveSortBy =
                if (libraryType == CollectionType.TvShows && sortBy == SortBy.DATE_PLAYED) {
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
                        libraryItemsCache.get("$parentId:$effectiveSortBy:$sortOrder") {
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
                searchJob =
                    viewModelScope.launch {
                        delay(SEARCH_DEBOUNCE_MS)
                        loadItems()
                    }
            }
            else -> Unit
        }
    }

    companion object {
        private const val SEARCH_DEBOUNCE_MS = 300L
    }
}
