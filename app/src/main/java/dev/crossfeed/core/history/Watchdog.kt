package dev.crossfeed.core.history

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context

/**
 * Wakes the diary back up after the phone has put it to sleep.
 *
 * Battery savers on many phones stop the listener overnight, and Android does not start it again
 * on its own, so a fresh install would record for a day and then go quiet until someone noticed.
 * Every fifteen minutes, whenever the system is waking anyway, this looks once and asks for the
 * listener back if it is gone. No network, no wake lock, done in a few milliseconds.
 */
class Watchdog : JobService() {

    override fun onStartJob(params: JobParameters?): Boolean {
        if (ListeningService.enabled(this) && !ListeningService.connected) {
            ListeningService.rebind(this)
        }
        return false
    }

    override fun onStopJob(params: JobParameters?): Boolean = false

    companion object {
        private const val JOB_ID = 4810
        private const val EVERY_MS = 15 * 60_000L

        fun schedule(context: Context) {
            val jobs = context.getSystemService(JobScheduler::class.java) ?: return
            if (jobs.getPendingJob(JOB_ID) != null) return
            runCatching {
                jobs.schedule(
                    JobInfo.Builder(JOB_ID, ComponentName(context, Watchdog::class.java))
                        .setPeriodic(EVERY_MS)
                        .setPersisted(true)
                        .build(),
                )
            }
        }
    }
}
