package com.kairoledger.app.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * WorkManager 周期任务调度器。
 */
object WorkScheduler {

    const val WORK_EXCHANGE_RATE_SYNC = "kairo_exchange_rate_sync"
    const val WORK_RECURRING_TRANSACTIONS = "kairo_recurring_transactions"

    fun schedulePeriodicWork(context: Context) {
        val workManager = WorkManager.getInstance(context.applicationContext)

        val rateConstraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val rateSyncRequest = PeriodicWorkRequestBuilder<ExchangeRateSyncWorker>(
            12L,
            TimeUnit.HOURS
        )
            .setConstraints(rateConstraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15L, TimeUnit.MINUTES)
            .build()

        workManager.enqueueUniquePeriodicWork(
            WORK_EXCHANGE_RATE_SYNC,
            ExistingPeriodicWorkPolicy.KEEP,
            rateSyncRequest
        )

        val recurringRequest = PeriodicWorkRequestBuilder<RecurringTransactionWorker>(
            6L,
            TimeUnit.HOURS
        )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15L, TimeUnit.MINUTES)
            .build()

        workManager.enqueueUniquePeriodicWork(
            WORK_RECURRING_TRANSACTIONS,
            ExistingPeriodicWorkPolicy.KEEP,
            recurringRequest
        )
    }

    fun triggerImmediateRateSync(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<ExchangeRateSyncWorker>()
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context.applicationContext).enqueue(request)
    }

    fun triggerImmediateRecurringCheck(context: Context) {
        val request = OneTimeWorkRequestBuilder<RecurringTransactionWorker>().build()
        WorkManager.getInstance(context.applicationContext).enqueue(request)
    }
}
