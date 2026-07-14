package dev.jdtech.jellyfin.work

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import dev.jdtech.jellyfin.database.ServerDatabase
import dev.jdtech.jellyfin.database.ServerDatabaseDao
import dev.jdtech.jellyfin.models.FindroidSourceDto
import dev.jdtech.jellyfin.models.FindroidSourceType
import dev.jdtech.jellyfin.utils.LargeFileHttpServer
import java.io.File
import java.io.RandomAccessFile
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression coverage for the >4GiB download fix: drives the real [VideoDownloadWorker] (not
 * DownloadManager) against a local Range-capable server serving a sparse ~4.4GiB file, verifying
 * final size, Long-safe progress reporting, and Range-based resume after a partial transfer.
 */
@RunWith(AndroidJUnit4::class)
class VideoDownloadWorkerLargeFileTest {
    private lateinit var context: android.content.Context
    private lateinit var db: ServerDatabase
    private lateinit var dao: ServerDatabaseDao
    private lateinit var sourceFile: File
    private lateinit var destFile: File
    private lateinit var finalFile: File
    private lateinit var server: LargeFileHttpServer
    private var port = 0
    private val sourceId = "video-source-1"
    private val itemId = UUID.randomUUID()

    companion object {
        const val FILE_SIZE = 4L * 1024 * 1024 * 1024 + 100_000_000
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, ServerDatabase::class.java).build()
        dao = db.getServerDatabaseDao()

        val storageDir = context.getExternalFilesDirs(null)[0]
        sourceFile = File(storageDir, "video_worker_test_source.mp4")
        destFile = File(storageDir, "video_worker_test_dest.mp4.download")
        finalFile = File(storageDir, "video_worker_test_dest.mp4")
        sourceFile.delete()
        destFile.delete()
        finalFile.delete()
        RandomAccessFile(sourceFile, "rw").use { it.setLength(FILE_SIZE) }

        port = 19080 + (System.currentTimeMillis() % 1000).toInt()
        server = LargeFileHttpServer(port, sourceFile)
        server.start(60_000, false)

        dao.insertSource(
            FindroidSourceDto(
                id = sourceId,
                itemId = itemId,
                name = "source",
                type = FindroidSourceType.LOCAL,
                path = destFile.path,
            )
        )
    }

    @After
    fun tearDown() {
        server.stop()
        sourceFile.delete()
        destFile.delete()
        finalFile.delete()
        db.close()
    }

    private fun buildWorker(): VideoDownloadWorker {
        val inputData =
            workDataOf(
                VideoDownloadWorker.KEY_SOURCE_ID to sourceId,
                VideoDownloadWorker.KEY_SOURCE_URL to "http://127.0.0.1:$port/source",
                VideoDownloadWorker.KEY_DESTINATION_PATH to destFile.path,
                VideoDownloadWorker.KEY_FINAL_PATH to finalFile.path,
                VideoDownloadWorker.KEY_EXPECTED_SIZE to FILE_SIZE,
            )
        val factory =
            object : WorkerFactory() {
                override fun createWorker(
                    appContext: android.content.Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters,
                ): ListenableWorker = VideoDownloadWorker(appContext, workerParameters, dao)
            }
        return TestListenableWorkerBuilder<VideoDownloadWorker>(context, inputData)
            .setWorkerFactory(factory)
            .build()
    }

    @Test
    fun downloadLargerThan4GiB_completesAndUpdatesSourcePath() {
        val worker = buildWorker()

        val result = worker.startWork().get()

        assertTrue("Expected Result.success(), got $result", result is androidx.work.ListenableWorker.Result.Success)
        assertEquals(FILE_SIZE, finalFile.length())
        val updatedSource = dao.getSources(itemId).first { it.id == sourceId }
        assertEquals(finalFile.path, updatedSource.path)
    }

    @Test
    fun interruptedDownload_resumesViaRangeInsteadOfRestarting() {
        // Simulate a previous partial transfer by pre-writing part of the destination file.
        val alreadyDownloaded = 1_500_000_000L
        RandomAccessFile(destFile, "rw").use { raf ->
            raf.setLength(alreadyDownloaded)
        }

        val worker = buildWorker()
        val result = worker.startWork().get()

        assertTrue("Expected Result.success(), got $result", result is androidx.work.ListenableWorker.Result.Success)
        assertEquals("bytes=$alreadyDownloaded-", server.lastRangeHeader)
        assertEquals(FILE_SIZE, finalFile.length())
    }
}
