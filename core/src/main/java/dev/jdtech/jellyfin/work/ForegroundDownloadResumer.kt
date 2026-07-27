package dev.jdtech.jellyfin.work

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import dev.jdtech.jellyfin.utils.Downloader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Re-adopts any download left in the ".download" state with no active transfer (the app was
 * killed mid-download, or [VideoDownloadService] itself was killed while backgrounded with no
 * Android 14 user-initiated job to wake it) the moment the app is foregrounded again - an Activity
 * becoming visible is itself one of the guaranteed foreground-eligible moments this whole
 * subsystem depends on, unlike WorkManager's own background rescheduling (retry backoff,
 * PACKAGE_REPLACED, boot), which is what caused the bug this subsystem exists to route around.
 */
class ForegroundDownloadResumer(private val downloader: Downloader) : DefaultLifecycleObserver {
    fun start() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        CoroutineScope(Dispatchers.IO).launch { downloader.reconcilePendingDownloads() }
    }
}
