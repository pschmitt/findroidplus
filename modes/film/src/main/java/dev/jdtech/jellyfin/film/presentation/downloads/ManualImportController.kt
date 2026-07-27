package dev.jdtech.jellyfin.film.presentation.downloads

import dev.jdtech.jellyfin.models.PvrSource
import dev.jdtech.jellyfin.repository.QueueStatusRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Drives the "manage imports" sheet ([ManualImportSheetState]) for a single queue entry -
 * extracted out of [DownloadsViewModel] so the same manual-import flow can be opened from other
 * screens too (the Movie/Episode detail views' own download widget), not just the bulk Downloads
 * list. Each owning ViewModel constructs one against its own [CoroutineScope] and exposes [state]
 * alongside its own screen state rather than folding it in - the sheet only ever needs this one
 * flow.
 */
class ManualImportController(
    private val queueStatusRepository: QueueStatusRepository,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<ManualImportSheetState?>(null)
    val state: StateFlow<ManualImportSheetState?> = _state.asStateFlow()

    fun open(source: PvrSource, downloadId: String, queueItemId: Int, title: String) {
        _state.value =
            ManualImportSheetState(
                source = source,
                downloadId = downloadId,
                queueItemId = queueItemId,
                title = title,
            )
        scope.launch {
            queueStatusRepository
                .getManualImportCandidates(source, downloadId)
                .fold(
                    onSuccess = { candidates ->
                        _state.update {
                            it?.copy(
                                isLoading = false,
                                candidates = candidates,
                                selectedIds =
                                    candidates.filter { c -> c.canImport }.map { c -> c.id }.toSet(),
                            )
                        }
                    },
                    onFailure = { e -> _state.update { it?.copy(isLoading = false, error = e.message) } },
                )
        }
    }

    fun close() {
        _state.value = null
    }

    fun toggleSelection(candidateId: Int) {
        _state.update { current ->
            current?.let {
                val selected = it.selectedIds
                val newSelected =
                    if (candidateId in selected) selected - candidateId else selected + candidateId
                it.copy(selectedIds = newSelected)
            }
        }
    }

    fun confirm(onSuccess: () -> Unit = {}, onFailure: (String?) -> Unit = {}) {
        val current = _state.value ?: return
        if (current.selectedIds.isEmpty() || current.isImporting) return
        _state.update { it?.copy(isImporting = true) }
        scope.launch {
            queueStatusRepository
                .performManualImport(current.source, current.downloadId, current.selectedIds)
                .fold(
                    onSuccess = {
                        _state.value = null
                        onSuccess()
                    },
                    onFailure = { e ->
                        _state.update { it?.copy(isImporting = false, error = e.message) }
                        onFailure(e.message)
                    },
                )
        }
    }

    fun reject(removeFromClient: Boolean, blocklist: Boolean, onSuccess: () -> Unit = {}, onFailure: (String?) -> Unit = {}) {
        val current = _state.value ?: return
        if (current.isRejecting) return
        _state.update { it?.copy(isRejecting = true) }
        scope.launch {
            queueStatusRepository
                .removeQueueItem(current.source, current.queueItemId, removeFromClient, blocklist)
                .fold(
                    onSuccess = {
                        _state.value = null
                        onSuccess()
                    },
                    onFailure = { e ->
                        _state.update { it?.copy(isRejecting = false, error = e.message) }
                        onFailure(e.message)
                    },
                )
        }
    }
}
