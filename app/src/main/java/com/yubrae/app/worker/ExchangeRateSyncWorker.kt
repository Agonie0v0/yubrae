package com.yubrae.app.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.yubrae.app.KairoApplication
import com.yubrae.app.data.db.KairoDatabase
import com.yubrae.app.data.prefs.SettingsStore
import com.yubrae.app.data.repository.ExchangeRateRepositoryImpl
import kotlinx.coroutines.CancellationException
import okhttp3.OkHttpClient

/**
 * 后台周期汇率同步 Worker：
 * 依据当前设置的主币种从双源汇率接口拉取最新汇率并写入本地数据库。
 */
class ExchangeRateSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): ListenableWorker.Result {
        return try {
            val container = (applicationContext as? KairoApplication)?.container
            val settingsStore = container?.settingsStore ?: SettingsStore(applicationContext)
            val provider = container?.exchangeRateProvider ?: ExchangeRateRepositoryImpl(
                db = container?.database ?: KairoDatabase.build(applicationContext),
                okHttpClient = container?.okHttpClient ?: OkHttpClient(),
                settingsStore = settingsStore
            )
            val baseCurrency = settingsStore.getSnapshot().baseCurrency
            val syncResult = provider.refreshFromNetwork(baseCurrency)
            if (syncResult.isSuccess) {
                ListenableWorker.Result.success()
            } else {
                if (runAttemptCount < MAX_RETRY_ATTEMPTS) {
                    ListenableWorker.Result.retry()
                } else {
                    ListenableWorker.Result.failure()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            if (runAttemptCount < MAX_RETRY_ATTEMPTS) {
                ListenableWorker.Result.retry()
            } else {
                ListenableWorker.Result.failure()
            }
        }
    }

    companion object {
        private const val MAX_RETRY_ATTEMPTS = 3
    }
}
