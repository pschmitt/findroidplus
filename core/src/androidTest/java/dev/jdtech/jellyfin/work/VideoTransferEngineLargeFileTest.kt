package dev.jdtech.jellyfin.work

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.jdtech.jellyfin.utils.LargeFileHttpServer
import java.io.File
import java.io.RandomAccessFile
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression coverage for the >4GiB download fix: drives the real [VideoTransferEngine] (not
 * DownloadManager) against a local Range-capable server serving a sparse ~4.4GiB file, verifying
 * final size, Long-safe progress reporting, and Range-based resume after a partial transfer.
 *
 * Exercises [VideoTransferEngine] directly rather than through [VideoDownloadService] - the engine
 * is deliberately decoupled from the Service's lifecycle/notification concerns for exactly this
 * reason, so this test needs no Service/WorkManager test harness at all.
 */
@RunWith(AndroidJUnit4::class)
class VideoTransferEngineLargeFileTest {
    private lateinit var sourceFile: File
    private lateinit var destFile: File
    private lateinit var server: LargeFileHttpServer
    private var port = 0
    private val engine = VideoTransferEngine()

    companion object {
        const val FILE_SIZE = 4L * 1024 * 1024 * 1024 + 100_000_000
    }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val storageDir = context.getExternalFilesDirs(null)[0]
        sourceFile = File(storageDir, "video_engine_test_source.mp4")
        destFile = File(storageDir, "video_engine_test_dest.mp4.download")
        sourceFile.delete()
        destFile.delete()
        RandomAccessFile(sourceFile, "rw").use { it.setLength(FILE_SIZE) }

        port = 19080 + (System.currentTimeMillis() % 1000).toInt()
        server = LargeFileHttpServer(port, sourceFile)
        server.start(60_000, false)
    }

    @After
    fun tearDown() {
        server.stop()
        sourceFile.delete()
        destFile.delete()
    }

    @Test
    fun downloadLargerThan4GiB_completesAndReportsCorrectSize() = runBlocking {
        var lastTotalBytes = -1L
        var lastDownloadedBytes = -1L

        engine.download(
            sourceUrl = "http://127.0.0.1:$port/source",
            destinationPath = destFile.path,
            expectedSize = FILE_SIZE,
        ) { downloadedBytes, totalBytes, _, _ ->
            lastDownloadedBytes = downloadedBytes
            lastTotalBytes = totalBytes
        }

        assertEquals(FILE_SIZE, destFile.length())
        assertEquals(
            "Reported total size did not match the source file size (Int/Long truncation?)",
            FILE_SIZE,
            lastTotalBytes,
        )
        assertEquals(FILE_SIZE, lastDownloadedBytes)
    }

    @Test
    fun interruptedDownload_resumesViaRangeInsteadOfRestarting() = runBlocking {
        // Simulate a previous partial transfer by pre-writing part of the destination file.
        val alreadyDownloaded = 1_500_000_000L
        RandomAccessFile(destFile, "rw").use { raf -> raf.setLength(alreadyDownloaded) }

        engine.download(
            sourceUrl = "http://127.0.0.1:$port/source",
            destinationPath = destFile.path,
            expectedSize = FILE_SIZE,
        ) { _, _, _, _ -> }

        assertEquals("bytes=$alreadyDownloaded-", server.lastRangeHeader)
        assertEquals(FILE_SIZE, destFile.length())
    }
}
