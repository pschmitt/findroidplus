package dev.jdtech.jellyfin.utils

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.RandomAccessFile
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Root-cause diagnostic for the >4GiB download failure: drives the system [DownloadManager]
 * directly (the transport DownloaderImpl used before the fix) against a sparse ~4.4GiB local file
 * served over a loopback HTTP server, to determine empirically whether the platform's
 * DownloadManager itself is the failure point on real hardware. The actual regression test for
 * the shipped fix is VideoDownloadWorkerLargeFileTest, which exercises the new transport.
 */
@RunWith(AndroidJUnit4::class)
class DownloadManagerLargeFileTest {
    private lateinit var context: Context
    private lateinit var downloadManager: DownloadManager
    private lateinit var sourceFile: File
    private lateinit var destFile: File
    private lateinit var server: LargeFileHttpServer
    private var port = 0

    companion object {
        // 2^32 (4 GiB) + ~100MB of margin, so we unambiguously cross both the decimal "4GB" and
        // the binary 2^32-byte boundaries that Int-based/unsigned-32-bit truncation bugs hit.
        const val FILE_SIZE = 4L * 1024 * 1024 * 1024 + 100_000_000
        const val TIMEOUT_MS = 10 * 60 * 1000L
    }

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        downloadManager = context.getSystemService(DownloadManager::class.java)

        val storageDir = context.getExternalFilesDirs(null)[0]
        sourceFile = File(storageDir, "large_download_test_source.mp4")
        destFile = File(storageDir, "large_download_test_dest.mp4")
        sourceFile.delete()
        destFile.delete()

        RandomAccessFile(sourceFile, "rw").use { it.setLength(FILE_SIZE) }

        port = 18080 + (System.currentTimeMillis() % 1000).toInt()
        server = LargeFileHttpServer(port, sourceFile)
        server.start(NanoHttpdTimeout, false)
    }

    @After
    fun tearDown() {
        server.stop()
        sourceFile.delete()
        destFile.delete()
    }

    @Test
    fun downloadLargerThan4GiB_completesWithCorrectSize() {
        val uri: Uri = "http://127.0.0.1:$port/source".toUri()
        val request =
            DownloadManager.Request(uri)
                .setTitle("large_download_test")
                .setDestinationUri(Uri.fromFile(destFile))
        val downloadId = downloadManager.enqueue(request)

        val (finalStatus, lastTotalBytes, lastDownloadedBytes) = pollUntilTerminal(downloadId)

        assertEquals(
            "Expected DownloadManager.STATUS_SUCCESSFUL, got status=$finalStatus " +
                "(total=$lastTotalBytes, downloaded=$lastDownloadedBytes)",
            DownloadManager.STATUS_SUCCESSFUL,
            finalStatus,
        )
        assertEquals(
            "Reported total size did not match the source file size (Int/Long truncation?)",
            FILE_SIZE,
            lastTotalBytes,
        )
        assertEquals(
            "Destination file size does not match source size after download",
            FILE_SIZE,
            destFile.length(),
        )
    }

    /** Polls DownloadManager exactly like DownloaderImpl.getProgress() does, but keeps raw Longs. */
    private fun pollUntilTerminal(downloadId: Long): Triple<Int, Long, Long> {
        val deadline = System.currentTimeMillis() + TIMEOUT_MS
        var status = DownloadManager.STATUS_PENDING
        var totalBytes = -1L
        var downloadedBytes = -1L

        while (System.currentTimeMillis() < deadline) {
            val query = DownloadManager.Query().setFilterById(downloadId)
            downloadManager.query(query).use { cursor ->
                assertTrue("Download disappeared from DownloadManager", cursor.moveToFirst())
                status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                totalBytes =
                    cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                downloadedBytes =
                    cursor.getLong(
                        cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                    )
            }
            if (status == DownloadManager.STATUS_SUCCESSFUL || status == DownloadManager.STATUS_FAILED) {
                break
            }
            Thread.sleep(500)
        }

        return Triple(status, totalBytes, downloadedBytes)
    }
}

// NanoHTTPD.start() default socket-read timeout, kept as a named constant for clarity.
private const val NanoHttpdTimeout = 60_000
