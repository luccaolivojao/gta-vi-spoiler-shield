package br.com.spoilershield.studio

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import br.com.spoilershield.schedule.Notifications
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PublishWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val n = Notifications.publishProgress(applicationContext, "Enviando publicação…")
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            ForegroundInfo(Notifications.ID_PUBLISH_FG, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else ForegroundInfo(Notifications.ID_PUBLISH_FG, n)
    }

    override suspend fun doWork(): Result {
        val id = inputData.getString(KEY_ID) ?: return Result.failure()
        try { setForeground(getForegroundInfo()) } catch (_: Exception) { /* sem permissão p/ 1º plano agora: segue em 2º plano */ }
        withContext(Dispatchers.IO) { Publisher(applicationContext).run(id) }

        val post = PostQueueStore(applicationContext).get(id)
        if (post != null) {
            val summary = post.request.platforms.sortedBy { it.ordinal }.joinToString("\n") {
                val o = post.outcomes[it]
                "${it.label}: ${o?.state?.label ?: "—"}"
            }
            Notifications.publishDone(applicationContext, summary)
        }
        return Result.success() // falhas ficam registradas no histórico; sem repetição automática (evita publicar em duplicidade)
    }

    companion object {
        const val KEY_ID = "post_id"

        fun enqueue(ctx: Context, id: String, scheduledAtMs: Long?) {
            val delay = ((scheduledAtMs ?: 0L) - System.currentTimeMillis()).coerceAtLeast(0)
            val b = OneTimeWorkRequestBuilder<PublishWorker>()
                .setInputData(workDataOf(KEY_ID to id))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.LINEAR, 30, java.util.concurrent.TimeUnit.SECONDS)
            if (scheduledAtMs != null && delay > 0) b.setInitialDelay(delay, java.util.concurrent.TimeUnit.MILLISECONDS)
            else b.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            WorkManager.getInstance(ctx).enqueueUniqueWork("publish-$id", ExistingWorkPolicy.REPLACE, b.build())
        }

        fun cancel(ctx: Context, id: String) { WorkManager.getInstance(ctx).cancelUniqueWork("publish-$id") }
    }
}
