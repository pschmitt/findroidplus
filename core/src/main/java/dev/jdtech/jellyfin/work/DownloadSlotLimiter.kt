package dev.jdtech.jellyfin.work

import java.util.ArrayDeque
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Caps how many [VideoDownloadWorker]s transfer bytes at once. WorkManager has no built-in notion
 * of "run at most N of this worker type", so we gate the actual transfer here - extra workers
 * stay queued (visible to the user via a "Queued" notification) and wait their turn instead of
 * running unbounded or being rejected.
 *
 * Waiters are served strictly in the order they called [acquire], so a season download started as
 * S01E01, S01E02, ... keeps that order instead of whichever worker happens to win a race for the
 * next free slot - a plain poll-and-retry loop gives no such guarantee.
 */
internal object DownloadSlotLimiter {
    private val mutex = Mutex()
    private var limit = 1
    private var active = 0
    private val waiters = ArrayDeque<CompletableDeferred<Unit>>()

    suspend fun acquire(maxParallel: Int) {
        val deferred = CompletableDeferred<Unit>()
        mutex.withLock {
            limit = maxParallel.coerceAtLeast(1)
            if (active < limit) {
                active++
                deferred.complete(Unit)
            } else {
                waiters.addLast(deferred)
            }
        }
        deferred.await()
    }

    suspend fun release() {
        mutex.withLock {
            active = (active - 1).coerceAtLeast(0)
            while (active < limit && waiters.isNotEmpty()) {
                active++
                waiters.removeFirst().complete(Unit)
            }
        }
    }
}
