package dev.jdtech.jellyfin.utils

import fi.iki.elonen.NanoHTTPD
import java.io.File
import java.io.FileInputStream

/**
 * Minimal local HTTP server serving a single file with manual Range support, used to
 * reproduce/regression-test large (>4GiB) downloads without needing a real Jellyfin server. Reads
 * the file lazily from disk via a seeked FileInputStream - never buffers it in memory.
 */
class LargeFileHttpServer(port: Int, private val file: File) : NanoHTTPD(port) {
    /** Records the most recent incoming Range header, so tests can assert resume actually used it. */
    @Volatile var lastRangeHeader: String? = null

    override fun serve(session: IHTTPSession): Response {
        val fileLength = file.length()
        val rangeHeader = session.headers["range"]
        lastRangeHeader = rangeHeader

        if (rangeHeader == null) {
            val response =
                newFixedLengthResponse(
                    Response.Status.OK,
                    "video/mp4",
                    FileInputStream(file),
                    fileLength,
                )
            response.addHeader("Accept-Ranges", "bytes")
            return response
        }

        // "bytes=<start>-[<end>]"
        val spec = rangeHeader.substringAfter("bytes=")
        val start = spec.substringBefore("-").toLong()
        val end = spec.substringAfter("-").toLongOrNull() ?: (fileLength - 1)
        val length = end - start + 1

        val stream = FileInputStream(file)
        stream.skip(start)

        val response =
            newFixedLengthResponse(Response.Status.PARTIAL_CONTENT, "video/mp4", stream, length)
        response.addHeader("Accept-Ranges", "bytes")
        response.addHeader("Content-Range", "bytes $start-$end/$fileLength")
        return response
    }
}
