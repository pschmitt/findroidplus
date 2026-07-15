package dev.jdtech.jellyfin.core.presentation.downloader

import android.app.DownloadManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.FindroidSourceType
import dev.jdtech.jellyfin.models.isDownloading
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import dev.jdtech.jellyfin.utils.Downloader
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

@HiltViewModel
class DownloaderViewModel
@Inject
constructor(private val downloader: Downloader, private val appPreferences: AppPreferences) :
    ViewModel() {
    private val _state = MutableStateFlow(DownloaderState())
    val state = _state.asStateFlow()

    private val eventsChannel = Channel<DownloaderEvent>()
    val events = eventsChannel.receiveAsFlow()

    var downloadId: Long? = null

    val downloadLocationPreference: String
        get() = appPreferences.getValue(appPreferences.downloadLocation)

    private var progressJob: Job? = null

    fun update(item: FindroidItem) {
        viewModelScope.launch {
            if (item.isDownloading()) {
                val source =
                    item.sources.firstOrNull { it.type == FindroidSourceType.LOCAL }
                        ?: return@launch
                val downloadId = source.downloadId ?: return@launch
                this@DownloaderViewModel.downloadId = downloadId
                observeDownloadProgress(downloadId)
            }
        }
    }

    private fun download(item: FindroidItem, storageIndex: Int = 0) {
        viewModelScope.launch {
            _state.emit(DownloaderState(status = DownloadManager.STATUS_PENDING))
            val (downloadId, uiText) =
                downloader.downloadItem(
                    item = item,
                    sourceId = item.sources.first().id,
                    storageIndex = storageIndex,
                )
            if (downloadId != -1L) {
                this@DownloaderViewModel.downloadId = downloadId
                observeDownloadProgress(downloadId)
            } else {
                _state.emit(
                    DownloaderState(status = DownloadManager.STATUS_FAILED, errorText = uiText)
                )
            }
        }
    }

    private fun cancelDownload() {
        viewModelScope.launch {
            // Stop progress observation
            progressJob?.cancel()

            // Cancel the download
            downloadId?.let { downloader.cancelDownload(downloadId = it) }

            // Emit empty DownloadState
            _state.emit(DownloaderState())
        }
    }

    private fun deleteDownload(item: FindroidItem) {
        viewModelScope.launch {
            downloader.deleteItem(
                item = item,
                source = item.sources.first { it.type == FindroidSourceType.LOCAL },
            )
            eventsChannel.send(DownloaderEvent.Deleted)
        }
    }

    private fun observeDownloadProgress(downloadId: Long) {
        progressJob?.cancel()
        progressJob =
            viewModelScope.launch {
                downloader.getProgressFlow(downloadId).collectLatest { progress ->
                    _state.emit(
                        DownloaderState(
                            status = progress.status,
                            progress = progress.percent.coerceAtLeast(0) / 100f,
                            speedBytesPerSecond = progress.speedBytesPerSecond,
                            etaSeconds = progress.etaSeconds,
                        )
                    )
                    if (progress.status == DownloadManager.STATUS_SUCCESSFUL) {
                        eventsChannel.send(DownloaderEvent.Successful)
                    }
                }
            }
    }

    fun onAction(action: DownloaderAction) {
        when (action) {
            is DownloaderAction.Download -> download(action.item, action.storageIndex)
            is DownloaderAction.DeleteDownload -> deleteDownload(action.item)
            is DownloaderAction.CancelDownload -> cancelDownload()
            is DownloaderAction.ForceDownload -> forceDownload()
        }
    }

    private fun forceDownload() {
        viewModelScope.launch { downloadId?.let { downloader.forceDownload(it) } }
    }
}
