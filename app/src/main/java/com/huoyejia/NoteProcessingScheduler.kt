package com.huoyejia

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.os.PersistableBundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

object NoteProcessingScheduler {
    private const val EXTRA_NOTE_ID = "note_id"
    private const val JOB_ID_BASE = 0x4A17
    private const val JOB_ID_BUCKETS = 100_000

    fun schedule(context: Context, noteId: String) {
        if (noteId.isBlank()) return
        val appContext = context.applicationContext
        val scheduler = appContext.getSystemService(JobScheduler::class.java) ?: return
        val extras = PersistableBundle().apply { putString(EXTRA_NOTE_ID, noteId) }
        val info = JobInfo.Builder(jobIdFor(noteId), ComponentName(appContext, NoteProcessingJobService::class.java))
            .setExtras(extras)
            .setMinimumLatency(300L)
            .setOverrideDeadline(2_000L)
            .setBackoffCriteria(15_000L, JobInfo.BACKOFF_POLICY_LINEAR)
            .build()
        scheduler.schedule(info)
    }

    fun cancel(context: Context, noteId: String) {
        if (noteId.isBlank()) return
        context.applicationContext.getSystemService(JobScheduler::class.java)?.cancel(jobIdFor(noteId))
    }

    internal fun noteIdFrom(params: JobParameters): String {
        return params.extras.getString(EXTRA_NOTE_ID).orEmpty()
    }

    private fun jobIdFor(noteId: String): Int {
        return JOB_ID_BASE + (noteId.hashCode() and Int.MAX_VALUE) % JOB_ID_BUCKETS
    }
}

class NoteProcessingJobService : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartJob(params: JobParameters): Boolean {
        val noteId = NoteProcessingScheduler.noteIdFrom(params)
        if (noteId.isBlank()) return false
        scope.launch {
            val shouldRetry = runCatching {
                (application as HuoyejiaApp).container.processor.process(noteId)
            }.isFailure
            jobFinished(params, shouldRetry)
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean = true

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
