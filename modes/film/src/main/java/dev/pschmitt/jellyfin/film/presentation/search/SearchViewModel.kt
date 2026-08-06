package dev.pschmitt.jellyfin.film.presentation.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.pschmitt.jellyfin.film.presentation.home.HomeCache
import dev.pschmitt.jellyfin.models.JollyfinItem
import dev.pschmitt.jellyfin.models.tmdbIdOrNull
import dev.pschmitt.jellyfin.pvr.PvrConfiguration
import dev.pschmitt.jellyfin.repository.JellyfinRepository
import dev.pschmitt.jellyfin.repository.QueueStatusRepository
import dev.pschmitt.jellyfin.repository.SeerrRepository
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

@HiltViewModel
class SearchViewModel
@Inject
constructor(
    private val repository: JellyfinRepository,
    private val seerrRepository: SeerrRepository,
    private val pvrConfiguration: PvrConfiguration,
    private val queueStatusRepository: QueueStatusRepository,
    private val homeCache: HomeCache,
) : ViewModel() {
    private val _state = MutableStateFlow(SearchState())
    val state = _state.asStateFlow()

    var currentJob: Job? = null

    init {
        viewModelScope.launch {
            queueStatusRepository.getRadarrQueueStatusFlow().collect { statuses ->
                _state.value = _state.value.copy(radarrQueueStatus = statuses)
            }
        }
    }

    private fun search(query: String) {
        currentJob?.cancel()
        currentJob = viewModelScope.launch {
            try {
                if (query.isBlank()) {
                    _state.emit(
                        SearchState(
                            items = suggestedItems(),
                            radarrQueueStatus = _state.value.radarrQueueStatus,
                        )
                    )
                    return@launch
                }

                _state.emit(
                    SearchState(
                        loading = true,
                        seerrSearching = pvrConfiguration.isSeerrConfigured(),
                        radarrQueueStatus = _state.value.radarrQueueStatus,
                    )
                )
                val items = repository.getSearchItems(query)
                // Hide Seerr results already in the Jellyfin library right above them - a
                // Seerr result is only useful here as "not on your server yet, want to
                // request it?", so one that's already a library hit is a plain duplicate.
                val libraryTmdbIds = items.mapNotNull { it.tmdbIdOrNull() }.toSet()
                val seerrResults =
                    if (pvrConfiguration.isSeerrConfigured()) {
                        seerrRepository.search(query).getOrDefault(emptyList()).filter {
                            it.tmdbId !in libraryTmdbIds
                        }
                    } else {
                        emptyList()
                    }

                _state.emit(
                    SearchState(
                        items = items,
                        seerrResults = seerrResults,
                        loading = false,
                        radarrQueueStatus = _state.value.radarrQueueStatus,
                    )
                )
            } catch (_: CancellationException) {} catch (e: Exception) {
                Timber.e(e)
                _state.emit(_state.value.copy(loading = false))
            }
        }
    }

    // Populates the otherwise-blank pre-search screen with Home's already-loaded Continue
    // Watching/Favorites/latest-library rows, reusing HomeCache's snapshot instead of issuing
    // fresh repository calls - this is just a "less empty" placeholder, not search results, so
    // slightly-stale content is fine.
    private fun suggestedItems(): List<JollyfinItem> {
        val snapshot = homeCache.snapshot ?: return emptyList()
        val seenIds = mutableSetOf<UUID>()
        val suggestions = mutableListOf<JollyfinItem>()
        fun addAll(items: List<JollyfinItem>) {
            items.forEach { if (seenIds.add(it.id)) suggestions.add(it) }
        }
        addAll(snapshot.resumeSection?.homeSection?.items.orEmpty())
        addAll(snapshot.favoritesSection?.homeSection?.items.orEmpty())
        snapshot.views.forEach { addAll(it.view.items) }
        return suggestions
    }

    fun onAction(action: SearchAction) {
        when (action) {
            is SearchAction.Search -> {
                search(query = action.query)
            }
            else -> Unit
        }
    }
}
