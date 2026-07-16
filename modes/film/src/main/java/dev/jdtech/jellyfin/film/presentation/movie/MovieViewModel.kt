package dev.jdtech.jellyfin.film.presentation.movie

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jdtech.jellyfin.api.pvr.PvrRelease
import dev.jdtech.jellyfin.core.presentation.search.ReleasePickerState
import dev.jdtech.jellyfin.core.presentation.search.SearchEvent
import dev.jdtech.jellyfin.film.domain.VideoMetadataParser
import dev.jdtech.jellyfin.models.FindroidItemPerson
import dev.jdtech.jellyfin.models.FindroidMovie
import dev.jdtech.jellyfin.repository.JellyfinRepository
import dev.jdtech.jellyfin.repository.QueueStatusRepository
import dev.jdtech.jellyfin.repository.RadarrSearchRepository
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.model.api.PersonKind

@HiltViewModel
class MovieViewModel
@Inject
constructor(
    private val repository: JellyfinRepository,
    private val videoMetadataParser: VideoMetadataParser,
    private val appPreferences: AppPreferences,
    private val radarrSearchRepository: RadarrSearchRepository,
    private val queueStatusRepository: QueueStatusRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(MovieState())
    val state = _state.asStateFlow()

    private val searchEventsChannel = Channel<SearchEvent>()
    val searchEvents = searchEventsChannel.receiveAsFlow()

    private var queueStatusJob: Job? = null

    lateinit var movieId: UUID

    fun loadMovie(movieId: UUID) {
        this.movieId = movieId
        observeQueueStatus(movieId)
        viewModelScope.launch {
            try {
                val movie = repository.getMovie(movieId)
                val videoMetadata = videoMetadataParser.parse(movie.sources.first())
                val actors = getActors(movie)
                val director = getDirector(movie)
                val writers = getWriters(movie)
                val dateFormat = appPreferences.getValue(appPreferences.dateFormat)
                _state.emit(
                    _state.value.copy(
                        movie = movie,
                        videoMetadata = videoMetadata,
                        actors = actors,
                        director = director,
                        writers = writers,
                        dateFormat = dateFormat,
                    )
                )
            } catch (e: Exception) {
                _state.emit(_state.value.copy(error = e))
            }
        }
    }

    private fun observeQueueStatus(movieId: UUID) {
        if (queueStatusJob != null) return
        queueStatusJob =
            viewModelScope.launch {
                queueStatusRepository.getQueueStatusFlow(movieId).collect { status ->
                    _state.value = _state.value.copy(queueStatus = status)
                }
            }
    }

    private suspend fun resolveTargetMovieId(): Int? {
        val tmdbId = _state.value.movie?.tmdbId ?: return null
        return radarrSearchRepository.resolveMovieId(tmdbId)
    }

    private fun searchMovieAutomatic() {
        viewModelScope.launch {
            val movieId = resolveTargetMovieId()
            val event =
                if (movieId == null) {
                    SearchEvent.Failed("Could not find this movie in Radarr")
                } else {
                    radarrSearchRepository
                        .searchMovie(movieId)
                        .fold({ SearchEvent.SearchTriggered }, { SearchEvent.Failed(it.message) })
                }
            searchEventsChannel.send(event)
        }
    }

    private fun openReleasePicker() {
        viewModelScope.launch {
            _state.value = _state.value.copy(releasePicker = ReleasePickerState())
            val movieId = resolveTargetMovieId()
            if (movieId == null) {
                _state.value = _state.value.copy(releasePicker = null)
                searchEventsChannel.send(SearchEvent.Failed("Could not find this movie in Radarr"))
                return@launch
            }
            val result = radarrSearchRepository.getReleases(movieId)
            _state.value =
                _state.value.copy(
                    releasePicker = result.getOrNull()?.let { ReleasePickerState(isLoading = false, releases = it) }
                )
            result.onFailure { searchEventsChannel.send(SearchEvent.Failed(it.message)) }
        }
    }

    private fun grabRelease(release: PvrRelease) {
        viewModelScope.launch {
            val result = radarrSearchRepository.grabRelease(release)
            _state.value = _state.value.copy(releasePicker = null)
            searchEventsChannel.send(
                result.fold({ SearchEvent.ReleaseGrabbed }, { SearchEvent.Failed(it.message) })
            )
        }
    }

    private suspend fun getActors(item: FindroidMovie): List<FindroidItemPerson> {
        return withContext(Dispatchers.Default) {
            item.people.filter { it.type == PersonKind.ACTOR }
        }
    }

    private suspend fun getDirector(item: FindroidMovie): FindroidItemPerson? {
        return withContext(Dispatchers.Default) {
            item.people.firstOrNull { it.type == PersonKind.DIRECTOR }
        }
    }

    private suspend fun getWriters(item: FindroidMovie): List<FindroidItemPerson> {
        return withContext(Dispatchers.Default) {
            item.people.filter { it.type == PersonKind.WRITER }
        }
    }

    fun onAction(action: MovieAction) {
        when (action) {
            is MovieAction.MarkAsPlayed -> {
                viewModelScope.launch {
                    repository.markAsPlayed(movieId)
                    loadMovie(movieId)
                }
            }
            is MovieAction.UnmarkAsPlayed -> {
                viewModelScope.launch {
                    repository.markAsUnplayed(movieId)
                    loadMovie(movieId)
                }
            }
            is MovieAction.MarkAsFavorite -> {
                viewModelScope.launch {
                    repository.markAsFavorite(movieId)
                    loadMovie(movieId)
                }
            }
            is MovieAction.UnmarkAsFavorite -> {
                viewModelScope.launch {
                    repository.unmarkAsFavorite(movieId)
                    loadMovie(movieId)
                }
            }
            is MovieAction.SearchMovieAutomatic -> searchMovieAutomatic()
            is MovieAction.OpenReleasePicker -> openReleasePicker()
            is MovieAction.GrabRelease -> grabRelease(action.release)
            is MovieAction.DismissReleasePicker ->
                _state.value = _state.value.copy(releasePicker = null)
            else -> Unit
        }
    }
}
