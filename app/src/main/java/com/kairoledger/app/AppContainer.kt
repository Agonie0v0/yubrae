package com.kairoledger.app

import android.content.Context
import com.kairoledger.app.core.contract.BillImporter
import com.kairoledger.app.core.contract.CaptureEngine
import com.kairoledger.app.core.contract.CloudSyncService
import com.kairoledger.app.core.contract.ExchangeRateProvider
import com.kairoledger.app.core.contract.LedgerService
import com.kairoledger.app.data.db.KairoDatabase
import com.kairoledger.app.data.prefs.SettingsStore
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * 轻量级手写依赖注入容器（KISS 原则，避免过多编译期框架复杂度）。
 * Stage 0 先声明接口持有器，Stage 2 总装时接入全部具体实现。
 */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    val database: KairoDatabase = KairoDatabase.build(appContext)
    val settingsStore: SettingsStore = SettingsStore(appContext)

    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    @Volatile
    var exchangeRateProvider: ExchangeRateProvider? = null

    @Volatile
    var ledgerService: LedgerService? = null

    @Volatile
    var captureEngine: CaptureEngine? = null

    @Volatile
    var billImporter: BillImporter? = null

    @Volatile
    var cloudSyncService: CloudSyncService? = null

    fun requireExchangeRateProvider(): ExchangeRateProvider =
        requireNotNull(exchangeRateProvider) { "ExchangeRateProvider not initialized" }

    fun requireLedgerService(): LedgerService =
        requireNotNull(ledgerService) { "LedgerService not initialized" }

    fun requireCaptureEngine(): CaptureEngine =
        requireNotNull(captureEngine) { "CaptureEngine not initialized" }

    fun requireBillImporter(): BillImporter =
        requireNotNull(billImporter) { "BillImporter not initialized" }

    fun requireCloudSyncService(): CloudSyncService =
        requireNotNull(cloudSyncService) { "CloudSyncService not initialized" }
}
