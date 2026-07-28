package dev.pschmitt.jellyfin.film.presentation.downloads

import dev.pschmitt.jellyfin.models.PvrSource
import dev.pschmitt.jellyfin.repository.QueueStatusRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

/** One underlying queue entry to seed a [ManualImportController.open] call with. */
data class PendingImportRef(val source: PvrSource, val downloadId: String, val queueItemId: Int)

/**
 * Drives the "manage imports" sheet ([ManualImportSheetState]) for a queue entry, or a whole
 * cluster of duplicate entries at once (see `PvrQueueEntry.duplicateGroupKey`) - extracted out of
 * [DownloadsViewModel] so the same manual-import flow can be opened from other screens too (the
 * Movie/Episode/SeerrMedia detail views' own download widget), not just the bulk Downloads list.
 * Each owning ViewModel constructs one against its own [CoroutineScope] and exposes [state]
 * alongside its own screen state rather than folding it in - the sheet only ever needs this one
 * flow.
 */
class ManualImportController(
    private val queueStatusRepository: QueueStatusRepository,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<ManualImportSheetState?>(null)
    val state: StateFlow<ManualImportSheetState?> = _state.asStateFlow()

    fun open(title: String, refs: List<PendingImportRef>) {
        if (refs.isEmpty()) return
        _state.value =
            ManualImportSheetState(
                title = title,
                entries =
                    refs.map { ref ->
                        ManualImportEntry(
                            source = ref.source,
                            downloadId = ref.downloadId,
                            queueItemId = ref.queueItemId,
                        )
                    },
            )
        refs.forEachIndexed { index, ref ->
            scope.launch {
                queueStatusRepository
                    .getManualImportCandidates(ref.source, ref.downloadId)
                    .fold(
                        onSuccess = { candidates ->
                            updateEntry(index) {
                                it.copy(
                                    isLoading = false,
                                    candidates = candidates,
                                    selectedIds =
                                        candidates.filter { c -> c.canImport }.map { c -> c.id }.toSet(),
                                )
                            }
                        },
                        onFailure = { e ->
                            Timber.w(e, "Failed to load manual import candidates for ${ref.downloadId}")
                            updateEntry(index) { it.copy(isLoading = false, error = e.message) }
                        },
                    )
            }
        }
    }

    fun close() {
        _state.value = null
    }

    fun selectEntry(index: Int) {
        _state.update { current ->
            current?.let { if (index in it.entries.indices) it.copy(selectedEntryIndex = index) else it }
        }
    }

    fun toggleSelection(candidateId: Int) {
        val index = _state.value?.selectedEntryIndex ?: return
        updateEntry(index) { entry ->
            val selected = entry.selectedIds
            val newSelected = if (candidateId in selected) selected - candidateId else selected + candidateId
            entry.copy(selectedIds = newSelected)
        }
    }

    /**
     * Imports from the currently-selected entry only, then removes every other entry in the
     * cluster as a losing duplicate (best-effort - a cleanup failure is logged, not surfaced,
     * since the import itself already succeeded and is the part the user actually asked for).
     */
    fun confirm(onSuccess: () -> Unit = {}, onFailure: (String?) -> Unit = {}) {
        val current = _state.value ?: return
        val selected = current.entries.getOrNull(current.selectedEntryIndex) ?: return
        if (selected.selectedIds.isEmpty() || current.isImporting) return
        _state.update { it?.copy(isImporting = true) }
        scope.launch {
            queueStatusRepository
                .performManualImport(selected.source, selected.downloadId, selected.selectedIds)
                .fold(
                    onSuccess = {
                        _state.value = null
                        onSuccess()
                        removeLosingDuplicates(current.entries, current.selectedEntryIndex)
                    },
                    onFailure = { e ->
                        _state.update { it?.copy(isImporting = false, error = e.message) }
                        onFailure(e.message)
                    },
                )
        }
    }

    /** Rejects the whole cluster - every entry, not just the one currently selected. */
    fun reject(removeFromClient: Boolean, blocklist: Boolean, onSuccess: () -> Unit = {}, onFailure: (String?) -> Unit = {}) {
        val current = _state.value ?: return
        if (current.isRejecting) return
        _state.update { it?.copy(isRejecting = true) }
        scope.launch {
            val failed =
                queueStatusRepository.removeQueueItems(
                    current.entries.map { it.source to it.queueItemId },
                    removeFromClient,
                    blocklist,
                )
            _state.value = null
            if (failed.isEmpty()) {
                onSuccess()
            } else {
                onFailure(null)
            }
        }
    }

    private fun removeLosingDuplicates(entries: List<ManualImportEntry>, keptIndex: Int) {
        val losers = entries.filterIndexed { index, _ -> index != keptIndex }
        if (losers.isEmpty()) return
        scope.launch {
            losers.forEach { loser ->
                queueStatusRepository
                    .removeQueueItem(loser.source, loser.queueItemId, removeFromClient = true, blocklist = false)
                    .onFailure {
                        Timber.w(it, "Failed to remove losing duplicate queue item ${loser.queueItemId}")
                    }
            }
        }
    }

    private inline fun updateEntry(index: Int, transform: (ManualImportEntry) -> ManualImportEntry) {
        _state.update { current ->
            current?.let {
                val entries = it.entries.toMutableList()
                entries.getOrNull(index)?.let { entry -> entries[index] = transform(entry) }
                it.copy(entries = entries)
            }
        }
    }
}
