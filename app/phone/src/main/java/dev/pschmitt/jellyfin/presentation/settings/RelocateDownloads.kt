package dev.pschmitt.jellyfin.presentation.settings

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import dev.pschmitt.jellyfin.settings.presentation.settings.RelocateDownloadsMode
import dev.pschmitt.jellyfin.work.RelocateDownloadsWorker

private const val RELOCATE_DOWNLOADS_WORK_NAME = "relocateDownloads"

fun enqueueRelocateDownloadsWork(
    context: Context,
    mode: RelocateDownloadsMode,
    from: String,
    to: String,
) {
    val request =
        OneTimeWorkRequestBuilder<RelocateDownloadsWorker>()
            .setInputData(
                workDataOf(
                    RelocateDownloadsWorker.KEY_MODE to
                        when (mode) {
                            RelocateDownloadsMode.MOVE -> RelocateDownloadsWorker.MODE_MOVE
                            RelocateDownloadsMode.CLEAR -> RelocateDownloadsWorker.MODE_CLEAR
                        },
                    RelocateDownloadsWorker.KEY_FROM_LOCATION to from,
                    RelocateDownloadsWorker.KEY_TO_LOCATION to to,
                )
            )
            .build()
    WorkManager.getInstance(context)
        .enqueueUniqueWork(RELOCATE_DOWNLOADS_WORK_NAME, ExistingWorkPolicy.KEEP, request)
}

data class RelocateDownloadsProgress(val mode: String, val done: Int, val total: Int)

/**
 * Observes the shared "relocateDownloads" unique work directly via WorkManager rather than going
 * through a ViewModel - the settings module can't depend on core (core already depends on
 * settings for AppPreferences), so this lives in app/phone instead, which depends on both.
 */
@Composable
fun rememberRelocateDownloadsProgress(): RelocateDownloadsProgress? {
    val context = LocalContext.current
    val workInfos by
        remember(context) {
                WorkManager.getInstance(context)
                    .getWorkInfosForUniqueWorkFlow(RELOCATE_DOWNLOADS_WORK_NAME)
            }
            .collectAsStateWithLifecycle(initialValue = emptyList<WorkInfo>())
    val active = workInfos.firstOrNull { !it.state.isFinished } ?: return null
    val mode = active.progress.getString(RelocateDownloadsWorker.KEY_MODE) ?: return null
    return RelocateDownloadsProgress(
        mode = mode,
        done = active.progress.getInt(RelocateDownloadsWorker.KEY_DONE, 0),
        total = active.progress.getInt(RelocateDownloadsWorker.KEY_TOTAL, 0),
    )
}
