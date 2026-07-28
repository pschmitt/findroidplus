package dev.pschmitt.jellyfin.work

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import timber.log.Timber

/**
 * API 34+ backstop for [VideoDownloadService] dying while the app is fully backgrounded (rare -
 * foreground services are high on the LMK priority list, but possible under memory pressure).
 * User-initiated data transfer jobs are explicitly exempted from the "no starting a new
 * foreground service from a background process" restriction that causes the bug this whole
 * subsystem exists to route around (see the "Real fix for stuck background downloads" plan) - the
 * OS treats "the user asked for this transfer" as sufficient justification on its own. On API < 34
 * there is no equivalent; [schedule]/[cancel] are no-ops there and the app relies on
 * [ForegroundDownloadResumer] (the app being reopened) instead.
 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
class ResumeDownloadsJobService : JobService() {
    override fun onStartJob(params: JobParameters?): Boolean {
        try {
            ContextCompat.startForegroundService(this, Intent(this, VideoDownloadService::class.java))
        } catch (e: Exception) {
            Timber.w(e, "ResumeDownloadsJobService could not start VideoDownloadService")
        }
        // Nothing asynchronous left for this job to track - VideoDownloadService takes over as
        // its own independent foreground service from here.
        return false
    }

    override fun onStopJob(params: JobParameters?): Boolean = false

    companion object {
        private const val JOB_ID = 279_412_100

        fun schedule(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
            val jobScheduler = context.getSystemService(JobScheduler::class.java) ?: return
            val jobInfo =
                JobInfo.Builder(JOB_ID, ComponentName(context, ResumeDownloadsJobService::class.java))
                    .setUserInitiated(true)
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .build()
            jobScheduler.schedule(jobInfo)
        }

        fun cancel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
            context.getSystemService(JobScheduler::class.java)?.cancel(JOB_ID)
        }
    }
}
