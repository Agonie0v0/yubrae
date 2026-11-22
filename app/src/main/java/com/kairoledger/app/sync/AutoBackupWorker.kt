package com.kairoledger.app.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.kairoledger.app.KairoApplication
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 后台周期自动加密备份 Worker：
 * 当启用自动备份且 WebDAV 地址与加密口令均已配置时，上传最新 .klbk 快照。
 */
class AutoBackupWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? KairoApplication ?: return Result.success()
        val container = runCatching { app.container }.getOrNull() ?: return Result.retry()
        val settings = container.settingsStore.getSnapshot()

        if (!settings.autoBackupEnabled ||
            settings.webDavUrl.isBlank() ||
            settings.backupPassphrase.isBlank()
        ) {
            return Result.success()
        }

        val syncService = container.cloudSyncService ?: WebDavSyncManager(
            db = container.database,
            settingsStore = container.settingsStore,
            okHttpClient = container.okHttpClient
        )

        val uploadResult = syncService.uploadEncryptedBackup()
        if (uploadResult.isSuccess) {
            return Result.success()
        }

        val error = uploadResult.exceptionOrNull()
        return if (error is IOException && runAttemptCount < 3) {
            Result.retry()
        } else {
            Result.failure()
        }
    }
}

object BackupWorkScheduler {
    private const val UNIQUE_WORK_NAME = "kairo_auto_webdav_backup"

    fun scheduleAutoBackup(context: Context, enabled: Boolean) {
        val workManager = WorkManager.getInstance(context.applicationContext)
        if (!enabled) {
            workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
            return
        }

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(24, TimeUnit.HOURS)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .build()

        workManager.enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }
}
