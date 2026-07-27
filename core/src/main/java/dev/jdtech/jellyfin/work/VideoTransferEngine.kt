package dev.jdtech.jellyfin.work

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * The actual byte-shuffling for a video download - streamed via raw OkHttp in fixed-size chunks
 * under our own control rather than delegated to the system DownloadManager (DownloadManager is
 * the root cause of a >4GiB failure elsewhere in this app's history; see git history on the old
 * VideoDownloadWorker for the full writeup) - plus the post-download SHA-256 verification pass.
 *
 * Deliberately decoupled from [VideoDownloadService]'s lifecycle/notification/repository
 * concerns (callers get plain progress callbacks) so this can be driven directly from an
 * instrumented test against a local HTTP server, with no Service/WorkManager test harness needed -
 * see VideoTransferEngineLargeFileTest for the >4GiB regression coverage this replaced.
 */
internal class VideoTransferEngine {
    /** (downloadedBytes, totalBytes, percent, speedBytesPerSecond) */
    suspend fun download(
        sourceUrl: String,
        destinationPath: String,
        expectedSize: Long,
        progressIntervalMs: Long = 1000L,
        onProgress: suspend (Long, Long, Int, Long) -> Unit,
    ) {
        val destFile = File(destinationPath)
        destFile.parentFile?.mkdirs()

        var downloadedSoFar = if (destFile.exists()) destFile.length() else 0L

        val client = OkHttpClient()
        val requestBuilder = Request.Builder().url(sourceUrl)
        if (downloadedSoFar > 0) {
            requestBuilder.header("Range", "bytes=$downloadedSoFar-")
        }

        client.newCall(requestBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Unexpected response ${response.code} for $sourceUrl")
            }

            val append = response.code == 206 && downloadedSoFar > 0
            if (!append) {
                downloadedSoFar = 0L
            }

            val body = response.body
            val contentLength = body.contentLength()
            val totalBytes =
                when {
                    contentLength > 0 && append -> downloadedSoFar + contentLength
                    contentLength > 0 -> contentLength
                    expectedSize > 0 -> expectedSize
                    else -> -1L
                }

            body.byteStream().use { input ->
                FileOutputStream(destFile, append).use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var lastReportMs = System.currentTimeMillis()
                    var bytesAtLastReport = downloadedSoFar
                    while (currentCoroutineContext().isActive) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        downloadedSoFar += read

                        val now = System.currentTimeMillis()
                        val elapsedMs = now - lastReportMs
                        if (elapsedMs >= progressIntervalMs) {
                            val percent =
                                if (totalBytes > 0) {
                                    (downloadedSoFar.times(100).div(totalBytes)).toInt()
                                } else {
                                    0
                                }
                            val bytesSinceLast = downloadedSoFar - bytesAtLastReport
                            val speedBytesPerSecond =
                                bytesSinceLast.times(1000).div(elapsedMs.coerceAtLeast(1))
                            onProgress(downloadedSoFar, totalBytes.coerceAtLeast(0), percent, speedBytesPerSecond)
                            lastReportMs = now
                            bytesAtLastReport = downloadedSoFar
                        }
                    }
                    output.flush()
                }
            }

            currentCoroutineContext().ensureActive()

            if (totalBytes > 0 && downloadedSoFar != totalBytes) {
                throw IOException(
                    "Incomplete download: got $downloadedSoFar of $totalBytes bytes for $sourceUrl"
                )
            }
        }
    }

    /** (hashedBytes, totalBytes, percent) */
    suspend fun verifyAndHash(
        file: File,
        progressIntervalMs: Long = 1000L,
        onProgress: suspend (Long, Long, Int) -> Unit,
    ): String {
        val totalBytes = file.length()
        val digest = MessageDigest.getInstance("SHA-256")
        var hashedSoFar = 0L
        var lastReportMs = System.currentTimeMillis()

        FileInputStream(file).use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (currentCoroutineContext().isActive) {
                val read = input.read(buffer)
                if (read == -1) break
                digest.update(buffer, 0, read)
                hashedSoFar += read

                val now = System.currentTimeMillis()
                if (now - lastReportMs >= progressIntervalMs) {
                    val percent =
                        if (totalBytes > 0) (hashedSoFar.times(100).div(totalBytes)).toInt() else 0
                    onProgress(hashedSoFar, totalBytes, percent)
                    lastReportMs = now
                }
            }
        }

        currentCoroutineContext().ensureActive()

        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val BUFFER_SIZE = 64 * 1024
    }
}
