package dev.jdtech.jellyfin.repository

import dev.jdtech.jellyfin.api.pvr.PvrApiException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Error mapping and command-polling constants shared by [SonarrSearchRepositoryImpl] and
 * [RadarrSearchRepositoryImpl] - the two services expose byte-identical command/release APIs, so
 * only the service name differs in the messages.
 */

/**
 * Turns raw network exceptions into messages worth showing the user directly (as a toast) -
 * [SocketTimeoutException] in particular is common with slow indexers (e.g. Prowlarr proxying
 * several trackers for an interactive search) and "timeout" alone isn't an obvious cause.
 */
internal fun mapPvrSearchError(serviceName: String, e: Exception): Throwable =
    when {
        e is SocketTimeoutException ->
            IOException("$serviceName timed out - it or one of its indexers may be slow to respond", e)
        e is UnknownHostException -> IOException("Could not reach $serviceName - check the server URL", e)
        // A reverse proxy in front of the service (or its own proxy to an indexer) gave up before
        // the service finished polling indexers - distinct from SocketTimeoutException, which is
        // *this* client giving up, so it needs its own message pointing at the proxy's timeout
        // setting rather than the service/the network.
        e is PvrApiException && e.httpCode in PVR_GATEWAY_ERROR_CODES ->
            IOException(
                "$serviceName's reverse proxy timed out (HTTP ${e.httpCode}) before the search finished - " +
                    "try again, or raise the proxy's timeout if this keeps happening",
                e,
            )
        else -> e
    }

internal val PVR_GATEWAY_ERROR_CODES = setOf(502, 503, 504)

// Sonarr/Radarr's terminal command states - anything else (queued/started) means the search is
// still in progress.
internal val PVR_TERMINAL_COMMAND_STATUSES = setOf("completed", "failed", "aborted", "cancelled", "orphaned")

internal const val PVR_COMMAND_POLL_INTERVAL_MS = 5_000L

// Well above any realistic search duration - a safety net against polling forever if the
// service's command never reaches a terminal state for some reason.
internal const val PVR_COMMAND_AWAIT_TIMEOUT_MS = 15 * 60 * 1000L
