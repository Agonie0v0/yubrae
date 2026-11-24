package com.yubrae.app.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.yubrae.app.KairoApplication
import com.yubrae.app.data.db.KairoDatabase
import com.yubrae.app.data.prefs.SettingsStore
import com.yubrae.app.data.repository.ExchangeRateRepositoryImpl
import com.yubrae.app.data.repository.LedgerRepositoryImpl
import kotlinx.coroutines.CancellationException
import okhttp3.OkHttpClient

/**
 * 后台周期记账执行 Worker：
 * 定期检查所有已启用且到期的周期账单规则，自动完成记账与周期推进。
 */
class RecurringTransactionWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): ListenableWorker.Result {
        return try {
            val container = (applicationContext as? KairoApplication)?.container
            val ledgerService = container?.ledgerService ?: run {
                val db = container?.database ?: KairoDatabase.build(applicationContext)
                val settingsStore = container?.settingsStore ?: SettingsStore(applicationContext)
                val okHttpClient = container?.okHttpClient ?: OkHttpClient()
                val rateProvider = container?.exchangeRateProvider
                    ?: ExchangeRateRepositoryImpl(db, okHttpClient, settingsStore)
                LedgerRepositoryImpl(db, rateProvider, settingsStore)
            }
            ledgerService.processDueRecurringRules(System.currentTimeMillis())
            ListenableWorker.Result.success()
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
