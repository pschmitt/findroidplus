package dev.jdtech.jellyfin.utils

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import android.text.format.Formatter
import androidx.core.net.toUri
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.database.ServerDatabaseDao
import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.FindroidMovie
import dev.jdtech.jellyfin.models.FindroidSource
import dev.jdtech.jellyfin.models.FindroidSources
import dev.jdtech.jellyfin.models.FindroidTrickplayInfo
import dev.jdtech.jellyfin.models.UiText
import dev.jdtech.jellyfin.models.toFindroidEpisode
import dev.jdtech.jellyfin.models.toFindroidEpisodeDto
import dev.jdtech.jellyfin.models.toFindroidMediaStreamDto
import dev.jdtech.jellyfin.models.toFindroidMovie
import dev.jdtech.jellyfin.models.toFindroidMovieDto
import dev.jdtech.jellyfin.models.toFindroidSeasonDto
import dev.jdtech.jellyfin.models.toFindroidSegmentsDto
import dev.jdtech.jellyfin.models.toFindroidShowDto
import dev.jdtech.jellyfin.models.toFindroidSource
import dev.jdtech.jellyfin.models.toFindroidSourceDto
import dev.jdtech.jellyfin.models.toFindroidTrickplayInfoDto
import dev.jdtech.jellyfin.models.toFindroidUserDataDto
import dev.jdtech.jellyfin.repository.JellyfinRepository
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import dev.jdtech.jellyfin.work.ImagesDownloaderWorker
import dev.jdtech.jellyfin.work.VideoDownloadWorker
import java.io.File
import java.util.UUID
import kotlin.Exception
import kotlin.math.ceil
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import timber.log.Timber

class DownloaderImpl(
    private val context: Context,
    private val database: ServerDatabaseDao,
    private val jellyfinRepository: JellyfinRepository,
    private val appPreferences: AppPreferences,
    private val workManager: WorkManager,
) : Downloader {
    private val downloadManager = context.getSystemService(DownloadManager::class.java)

    // TODO: We should probably move most (if not all) code to a worker.
    //  At this moment it is possible that some things are not downloaded due to the user leaving
    //  the current screen
    override suspend fun downloadItem(
        item: FindroidItem,
        sourceId: String,
        storageIndex: Int,
    ): Pair<Long, UiText?> = coroutineScope {
        try {
            val source =
                jellyfinRepository.getMediaSources(item.id, true).first { it.id == sourceId }
            val segments = jellyfinRepository.getSegments(item.id)
            val trickplayInfo =
                if (item is FindroidSources) {
                    item.trickplayInfo?.get(sourceId)
                } else {
                    null
                }
            val storageLocation = context.getExternalFilesDirs(null)[storageIndex]
            if (
                storageLocation == null ||
                    Environment.getExternalStorageState(storageLocation) !=
                        Environment.MEDIA_MOUNTED
            ) {
                return@coroutineScope Pair(
                    -1,
                    UiText.StringResource(CoreR.string.storage_unavailable),
                )
            }
            val path =
                Uri.fromFile(File(storageLocation, "downloads/${item.id}.${source.id}.download"))
            val stats = StatFs(storageLocation.path)
            if (stats.availableBytes < source.size) {
                return@coroutineScope Pair(
                    -1,
                    UiText.StringResource(
                        CoreR.string.not_enough_storage,
                        Formatter.formatFileSize(context, source.size),
                        Formatter.formatFileSize(context, stats.availableBytes),
                    ),
                )
            }
            // The primary source is streamed by our own VideoDownloadWorker rather than
            // DownloadManager - see the class doc on VideoDownloadWorker for why. downloadId is
            // now a synthetic, locally-unique 64-bit id used purely as a Room lookup key; it no
            // longer comes from DownloadManager.enqueue().
            val downloadId = UUID.randomUUID().mostSignificantBits
            val finalPath = path.path.orEmpty().replace(".download", "")

            when (item) {
                is FindroidMovie -> {
                    database.insertMovie(
                        item.toFindroidMovieDto(
                            appPreferences.getValue(appPreferences.currentServer)
                        )
                    )
                }
                is FindroidEpisode -> {
                    val show = jellyfinRepository.getShow(item.seriesId)
                    database.insertShow(
                        show.toFindroidShowDto(
                            appPreferences.getValue(appPreferences.currentServer)
                        )
                    )
                    val season = jellyfinRepository.getSeason(item.seasonId)
                    database.insertSeason(season.toFindroidSeasonDto())
                    database.insertEpisode(
                        item.toFindroidEpisodeDto(
                            appPreferences.getValue(appPreferences.currentServer)
                        )
                    )

                    startImagesDownloader(show)
                    startImagesDownloader(season)
                }
            }

            val sourceDto = source.toFindroidSourceDto(item.id, path.path.orEmpty())

            database.insertSource(sourceDto.copy(downloadId = downloadId))
            database.insertUserData(item.toFindroidUserDataDto(jellyfinRepository.getUserId()))

            // Enqueue only after the sources row exists - VideoDownloadWorker updates that row by
            // id on completion, so it must not race the insert above.
            enqueueVideoDownload(
                downloadId = downloadId,
                sourceId = source.id,
                sourceUrl = source.path,
                destinationPath = path.path.orEmpty(),
                finalPath = finalPath,
                expectedSize = source.size,
                itemName = downloadDisplayName(item),
            )

            downloadExternalMediaStreams(item, source, storageIndex)

            segments.forEach { database.insertSegment(it.toFindroidSegmentsDto(item.id)) }

            if (trickplayInfo != null) {
                downloadTrickplayData(item.id, sourceId, trickplayInfo)
            }

            startImagesDownloader(item)
            return@coroutineScope Pair(downloadId, null)
        } catch (e: Exception) {
            try {
                val source = jellyfinRepository.getMediaSources(item.id).first { it.id == sourceId }
                deleteItem(item, source)
            } catch (_: Exception) {}
            Timber.e(e)
            return@coroutineScope Pair(
                -1,
                if (e.message != null) UiText.DynamicString(e.message!!)
                else UiText.StringResource(CoreR.string.unknown_error),
            )
        }
    }

    override suspend fun cancelDownload(downloadId: Long) {
        val sourceDto = database.getSourceByDownloadId(downloadId) ?: return
        workManager.cancelUniqueWork(sourceDto.id)

        val item = findFindroidItem(sourceDto.itemId)
        if (item == null) {
            // The movie/episode row is already gone - fall back to just cleaning up the source
            // row and the partial file directly, since deleteItem() needs the item's type to
            // cascade into season/show cleanup.
            Timber.e("cancelDownload: no FindroidItem found for source ${sourceDto.id}, cleaning up source only")
            database.deleteSource(sourceDto.id)
            File(sourceDto.path).delete()
            return
        }
        deleteItem(item, sourceDto.toFindroidSource(database))
    }

    override suspend fun pauseDownload(downloadId: Long) {
        val sourceDto = database.getSourceByDownloadId(downloadId) ?: return
        workManager.cancelUniqueWork(sourceDto.id)
    }

    override suspend fun resumeDownload(downloadId: Long): UiText? {
        val sourceDto =
            database.getSourceByDownloadId(downloadId)
                ?: return UiText.StringResource(CoreR.string.unknown_error)
        return try {
            val remoteSource =
                jellyfinRepository.getMediaSources(sourceDto.itemId, true).firstOrNull {
                    it.id == sourceDto.id
                } ?: return UiText.StringResource(CoreR.string.unknown_error)

            val itemName =
                findFindroidItem(sourceDto.itemId)?.let { downloadDisplayName(it) } ?: sourceDto.name
            val finalPath = sourceDto.path.replace(".download", "")

            enqueueVideoDownload(
                downloadId = downloadId,
                sourceId = remoteSource.id,
                sourceUrl = remoteSource.path,
                destinationPath = sourceDto.path,
                finalPath = finalPath,
                expectedSize = remoteSource.size,
                itemName = itemName,
            )
            null
        } catch (e: Exception) {
            Timber.e(e)
            if (e.message != null) UiText.DynamicString(e.message!!)
            else UiText.StringResource(CoreR.string.unknown_error)
        }
    }

    // For episodes, the episode title alone doesn't say which show/season it's from - show that
    // instead so concurrent downloads are distinguishable in the notification and Downloads page.
    private fun downloadDisplayName(item: FindroidItem): String =
        when (item) {
            is FindroidEpisode -> "${item.seriesName} • S${item.parentIndexNumber}E${item.indexNumber}"
            else -> item.name
        }

    private fun findFindroidItem(itemId: UUID): FindroidItem? {
        val userId = jellyfinRepository.getUserId()
        return try {
            database.getMovie(itemId).toFindroidMovie(database, userId)
        } catch (_: Exception) {
            try {
                database.getEpisode(itemId).toFindroidEpisode(database, userId)
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun enqueueVideoDownload(
        downloadId: Long,
        sourceId: String,
        sourceUrl: String,
        destinationPath: String,
        finalPath: String,
        expectedSize: Long,
        itemName: String,
    ) {
        val downloadRequest =
            OneTimeWorkRequestBuilder<VideoDownloadWorker>()
                .setInputData(
                    workDataOf(
                        VideoDownloadWorker.KEY_DOWNLOAD_ID to downloadId,
                        VideoDownloadWorker.KEY_SOURCE_ID to sourceId,
                        VideoDownloadWorker.KEY_SOURCE_URL to sourceUrl,
                        VideoDownloadWorker.KEY_DESTINATION_PATH to destinationPath,
                        VideoDownloadWorker.KEY_FINAL_PATH to finalPath,
                        VideoDownloadWorker.KEY_EXPECTED_SIZE to expectedSize,
                        VideoDownloadWorker.KEY_ITEM_NAME to itemName,
                    )
                )
                .build()
        workManager.enqueueUniqueWork(sourceId, ExistingWorkPolicy.KEEP, downloadRequest)
    }

    override suspend fun deleteItem(item: FindroidItem, source: FindroidSource) {
        when (item) {
            is FindroidMovie -> {
                database.deleteMovie(item.id)
            }
            is FindroidEpisode -> {
                database.deleteEpisode(item.id)
                val remainingEpisodes = database.getEpisodesBySeasonId(item.seasonId)
                if (remainingEpisodes.isEmpty()) {
                    database.deleteSeason(item.seasonId)
                    database.deleteUserData(item.seasonId)
                    File(context.filesDir, "trickplay/${item.seasonId}").deleteRecursively()
                    File(context.filesDir, "images/${item.seasonId}").deleteRecursively()
                    val remainingSeasons = database.getSeasonsByShowId(item.seriesId)
                    if (remainingSeasons.isEmpty()) {
                        database.deleteShow(item.seriesId)
                        database.deleteUserData(item.seriesId)
                        File(context.filesDir, "trickplay/${item.seriesId}").deleteRecursively()
                        File(context.filesDir, "images/${item.seriesId}").deleteRecursively()
                    }
                }
            }
        }

        database.deleteSource(source.id)
        File(source.path).delete()

        val mediaStreams = database.getMediaStreamsBySourceId(source.id)
        for (mediaStream in mediaStreams) {
            File(mediaStream.path).delete()
        }
        database.deleteMediaStreamsBySourceId(source.id)

        database.deleteUserData(item.id)

        File(context.filesDir, "trickplay/${item.id}").deleteRecursively()
        File(context.filesDir, "images/${item.id}").deleteRecursively()
    }

    override suspend fun getProgress(downloadId: Long?): Pair<Int, Int> {
        var downloadStatus = -1
        var progress = -1
        if (downloadId == null) {
            return Pair(downloadStatus, progress)
        }
        val source = database.getSourceByDownloadId(downloadId) ?: return Pair(downloadStatus, progress)
        val workInfo =
            workManager.getWorkInfosForUniqueWorkFlow(source.id).first().firstOrNull()
                ?: return Pair(DownloadManager.STATUS_FAILED, progress)

        when (workInfo.state) {
            WorkInfo.State.ENQUEUED,
            WorkInfo.State.BLOCKED -> {
                downloadStatus = DownloadManager.STATUS_PENDING
            }
            WorkInfo.State.RUNNING -> {
                if (workInfo.progress.getBoolean(VideoDownloadWorker.KEY_QUEUED, false)) {
                    downloadStatus = DownloadManager.STATUS_PENDING
                } else {
                    downloadStatus = DownloadManager.STATUS_RUNNING
                    val totalBytes = workInfo.progress.getLong(VideoDownloadWorker.KEY_TOTAL, -1L)
                    val downloadedBytes =
                        workInfo.progress.getLong(VideoDownloadWorker.KEY_DOWNLOADED, -1L)
                    if (totalBytes > 0 && downloadedBytes >= 0) {
                        progress = downloadedBytes.times(100).div(totalBytes).toInt()
                    }
                }
            }
            WorkInfo.State.SUCCEEDED -> {
                downloadStatus = DownloadManager.STATUS_SUCCESSFUL
                progress = 100
            }
            WorkInfo.State.FAILED,
            WorkInfo.State.CANCELLED -> {
                downloadStatus = DownloadManager.STATUS_FAILED
            }
        }
        return Pair(downloadStatus, progress)
    }

    override fun getProgressFlow(downloadId: Long): Flow<DownloadProgress> {
        val sourceId =
            database.getSourceByDownloadId(downloadId)?.id
                ?: return flowOf(DownloadProgress(status = DownloadManager.STATUS_FAILED))

        // Bytes/time from the previous emission, used to derive a speed for the current one -
        // this is per-collector state, safe since each getProgressFlow() call builds a fresh flow.
        var lastBytes = -1L
        var lastTimeMs = 0L

        return workManager.getWorkInfosForUniqueWorkFlow(sourceId).map { infos ->
            val workInfo = infos.firstOrNull() ?: return@map DownloadProgress(DownloadManager.STATUS_FAILED)

            when (workInfo.state) {
                WorkInfo.State.ENQUEUED,
                WorkInfo.State.BLOCKED -> DownloadProgress(status = DownloadManager.STATUS_PENDING)
                WorkInfo.State.RUNNING -> {
                    if (workInfo.progress.getBoolean(VideoDownloadWorker.KEY_QUEUED, false)) {
                        return@map DownloadProgress(status = DownloadManager.STATUS_PENDING)
                    }

                    val totalBytes = workInfo.progress.getLong(VideoDownloadWorker.KEY_TOTAL, -1L)
                    val downloadedBytes =
                        workInfo.progress.getLong(VideoDownloadWorker.KEY_DOWNLOADED, -1L)
                    val now = System.currentTimeMillis()

                    val speed =
                        if (lastBytes >= 0 && downloadedBytes >= lastBytes && lastTimeMs > 0) {
                            val deltaMs = (now - lastTimeMs).coerceAtLeast(1)
                            (downloadedBytes - lastBytes).times(1000L).div(deltaMs)
                        } else {
                            0L
                        }
                    lastBytes = downloadedBytes
                    lastTimeMs = now

                    val percent =
                        if (totalBytes > 0 && downloadedBytes >= 0) {
                            downloadedBytes.times(100).div(totalBytes).toInt()
                        } else {
                            -1
                        }
                    val eta =
                        if (speed > 0 && totalBytes > 0 && downloadedBytes >= 0) {
                            (totalBytes - downloadedBytes) / speed
                        } else {
                            -1L
                        }

                    DownloadProgress(
                        status = DownloadManager.STATUS_RUNNING,
                        percent = percent,
                        downloadedBytes = downloadedBytes.coerceAtLeast(0),
                        totalBytes = totalBytes.coerceAtLeast(0),
                        speedBytesPerSecond = speed,
                        etaSeconds = eta,
                    )
                }
                WorkInfo.State.SUCCEEDED ->
                    DownloadProgress(status = DownloadManager.STATUS_SUCCESSFUL, percent = 100)
                // A CANCELLED job is what pauseDownload() produces (see the interface doc on
                // pauseDownload) - report it as paused rather than failed so the Downloads page can
                // offer Resume instead of treating it as an error.
                WorkInfo.State.CANCELLED -> DownloadProgress(status = DownloadManager.STATUS_PAUSED)
                WorkInfo.State.FAILED -> DownloadProgress(status = DownloadManager.STATUS_FAILED)
            }
        }
    }

    private fun downloadExternalMediaStreams(
        item: FindroidItem,
        source: FindroidSource,
        storageIndex: Int = 0,
    ) {
        val storageLocation = context.getExternalFilesDirs(null)[storageIndex]
        for (mediaStream in source.mediaStreams.filter { it.isExternal }) {
            val id = UUID.randomUUID()
            val streamPath =
                Uri.fromFile(
                    File(storageLocation, "downloads/${item.id}.${source.id}.$id.download")
                )
            database.insertMediaStream(
                mediaStream.toFindroidMediaStreamDto(id, source.id, streamPath.path.orEmpty())
            )
            val request =
                DownloadManager.Request(mediaStream.path!!.toUri())
                    .setTitle(mediaStream.title)
                    .setAllowedOverMetered(
                        appPreferences.getValue(appPreferences.downloadOverMobileData)
                    )
                    .setAllowedOverRoaming(
                        appPreferences.getValue(appPreferences.downloadWhenRoaming)
                    )
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_HIDDEN)
                    .setDestinationUri(streamPath)
            val downloadId = downloadManager.enqueue(request)
            database.setMediaStreamDownloadId(id, downloadId)
        }
    }

    private suspend fun downloadTrickplayData(
        itemId: UUID,
        sourceId: String,
        trickplayInfo: FindroidTrickplayInfo,
    ) {
        val maxIndex =
            ceil(
                    trickplayInfo.thumbnailCount
                        .toDouble()
                        .div(trickplayInfo.tileWidth * trickplayInfo.tileHeight)
                )
                .toInt()
        val byteArrays = mutableListOf<ByteArray>()
        for (i in 0..maxIndex) {
            jellyfinRepository.getTrickplayData(itemId, trickplayInfo.width, i)?.let { byteArray ->
                byteArrays.add(byteArray)
            }
        }
        saveTrickplayData(itemId, sourceId, trickplayInfo, byteArrays)
    }

    private fun saveTrickplayData(
        itemId: UUID,
        sourceId: String,
        trickplayInfo: FindroidTrickplayInfo,
        byteArrays: List<ByteArray>,
    ) {
        val basePath = "trickplay/$itemId/$sourceId"
        database.insertTrickplayInfo(trickplayInfo.toFindroidTrickplayInfoDto(sourceId))
        File(context.filesDir, basePath).mkdirs()
        for ((i, byteArray) in byteArrays.withIndex()) {
            val file = File(context.filesDir, "$basePath/$i")
            file.writeBytes(byteArray)
        }
    }

    private fun startImagesDownloader(item: FindroidItem) {
        val downloadImagesRequest =
            OneTimeWorkRequestBuilder<ImagesDownloaderWorker>()
                .setInputData(workDataOf(ImagesDownloaderWorker.KEY_ITEM_ID to item.id.toString()))
                .build()

        workManager.enqueue(downloadImagesRequest)
    }
}
