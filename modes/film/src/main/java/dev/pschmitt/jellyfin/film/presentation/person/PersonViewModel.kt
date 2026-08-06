package dev.pschmitt.jellyfin.film.presentation.person

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.pschmitt.jellyfin.models.JollyfinMovie
import dev.pschmitt.jellyfin.models.JollyfinShow
import dev.pschmitt.jellyfin.repository.JellyfinRepository
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemKind

@HiltViewModel
class PersonViewModel @Inject internal constructor(private val repository: JellyfinRepository) :
    ViewModel() {
    private val _state = MutableStateFlow(PersonState())
    val state = _state.asStateFlow()

    fun loadPerson(personId: UUID) {
        viewModelScope.launch {
            _state.emit(_state.value.copy(isRefreshing = true))
            try {
                val person = repository.getPerson(personId)

                val items =
                    repository.getPersonItems(
                        personIds = listOf(personId),
                        includeTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
                        recursive = true,
                    )

                val movies = items.filterIsInstance<JollyfinMovie>()
                val shows = items.filterIsInstance<JollyfinShow>()

                _state.emit(
                    _state.value.copy(
                        person = person,
                        starredInMovies = movies,
                        starredInShows = shows,
                        isRefreshing = false,
                    )
                )
            } catch (e: Exception) {
                _state.emit(_state.value.copy(error = e, isRefreshing = false))
            }
        }
    }
}
