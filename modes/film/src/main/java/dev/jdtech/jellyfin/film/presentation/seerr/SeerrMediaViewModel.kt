package dev.jdtech.jellyfin.film.presentation.seerr

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jdtech.jellyfin.models.SeerrMediaType
import dev.jdtech.jellyfin.repository.SeerrRepository
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** One-shot feedback for the request/cancel actions, shown as a toast. */
sealed interface SeerrMediaEvent {
    data class Requested(val title: String) : SeerrMediaEvent

    data class RequestCancelled(val title: String) : SeerrMediaEvent

    data class ActionFailed(val message: String?) : SeerrMediaEvent
}

@HiltViewModel
class SeerrMediaViewModel
@Inject
constructor(private val seerrRepository: SeerrRepository) : ViewModel() {
    private val _state = MutableStateFlow(SeerrMediaState())
    val state = _state.asStateFlow()

    private val eventsChannel = Channel<SeerrMediaEvent>()
    val events = eventsChannel.receiveAsFlow()

    private var tmdbId: Int = 0
    private lateinit var mediaType: SeerrMediaType

    fun loadDetail(tmdbId: Int, mediaType: SeerrMediaType) {
        this.tmdbId = tmdbId
        this.mediaType = mediaType
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            seerrRepository
                .getDetails(tmdbId, mediaType)
                .fold(
                    onSuccess = { detail ->
                        _state.value = _state.value.copy(isLoading = false, detail = detail)
                    },
                    onFailure = { e ->
                        _state.value =
                            _state.value.copy(isLoading = false, error = e as? Exception ?: Exception(e))
                    },
                )
        }
    }

    fun onAction(action: SeerrMediaAction) {
        when (action) {
            is SeerrMediaAction.OnRequest -> request()
            is SeerrMediaAction.OnCancelRequest -> cancelRequests()
            is SeerrMediaAction.OnRetryClick -> loadDetail(tmdbId, mediaType)
            else -> Unit
        }
    }

    private fun request() {
        val detail = _state.value.detail ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(isSubmitting = true)
            seerrRepository
                .request(detail.tmdbId, detail.mediaType)
                .fold(
                    onSuccess = {
                        eventsChannel.send(SeerrMediaEvent.Requested(detail.title))
                        // Reload rather than patching the state locally - the new request's id is
                        // needed for a subsequent unrequest, and only the server has it.
                        loadDetail(detail.tmdbId, detail.mediaType)
                    },
                    onFailure = { e ->
                        eventsChannel.send(SeerrMediaEvent.ActionFailed(e.message))
                    },
                )
            _state.value = _state.value.copy(isSubmitting = false)
        }
    }

    private fun cancelRequests() {
        val detail = _state.value.detail ?: return
        if (detail.cancellableRequestIds.isEmpty()) return
        viewModelScope.launch {
            _state.value = _state.value.copy(isSubmitting = true)
            // A media can accumulate several open requests (e.g. re-requested seasons);
            // "unrequest" means clearing them all. Stop at the first failure and surface it.
            var failure: Throwable? = null
            for (requestId in detail.cancellableRequestIds) {
                val result = seerrRepository.cancelRequest(requestId)
                if (result.isFailure) {
                    failure = result.exceptionOrNull()
                    break
                }
            }
            if (failure == null) {
                eventsChannel.send(SeerrMediaEvent.RequestCancelled(detail.title))
            } else {
                eventsChannel.send(SeerrMediaEvent.ActionFailed(failure.message))
            }
            loadDetail(detail.tmdbId, detail.mediaType)
            _state.value = _state.value.copy(isSubmitting = false)
        }
    }
}
