package com.kairoledger.app

import android.content.Context
import com.kairoledger.app.capture.CaptureOrchestrator
import com.kairoledger.app.core.contract.BillImporter
import com.kairoledger.app.core.contract.CaptureEngine
import com.kairoledger.app.core.contract.CloudSyncService
import com.kairoledger.app.core.contract.ExchangeRateProvider
import com.kairoledger.app.core.contract.LedgerService
import com.kairoledger.app.data.db.KairoDatabase
import com.kairoledger.app.data.prefs.SettingsStore
import com.kairoledger.app.data.repository.ExchangeRateRepositoryImpl
import com.kairoledger.app.data.repository.LedgerRepositoryImpl
import com.kairoledger.app.importer.YimuCsvImporter
import com.kairoledger.app.sync.WebDavSyncManager
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * 轻量级手写依赖注入容器（KISS 原则）。
 * 在容器构造时同步装配全量核心服务，保证任何后台 Receiver / Service / Widget 均可安全获取。
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

    val exchangeRateProvider: ExchangeRateProvider = ExchangeRateRepositoryImpl(
        db = database,
        okHttpClient = okHttpClient,
        settingsStore = settingsStore
    )

    val ledgerService: LedgerService = LedgerRepositoryImpl(
        db = database,
        exchangeRateProvider = exchangeRateProvider,
        settingsStore = settingsStore
    )

    val captureEngine: CaptureEngine = CaptureOrchestrator(
        context = appContext,
        db = database,
        settingsStore = settingsStore,
        okHttpClient = okHttpClient,
        ledgerService = ledgerService
    )

    val billImporter: BillImporter = YimuCsvImporter(
        db = database,
        exchangeRateProvider = exchangeRateProvider,
        ledgerService = ledgerService,
        settingsStore = settingsStore
    )

    val cloudSyncService: CloudSyncService = WebDavSyncManager(
        db = database,
        settingsStore = settingsStore,
        okHttpClient = okHttpClient
    )

    fun requireExchangeRateProvider(): ExchangeRateProvider = exchangeRateProvider
    fun requireLedgerService(): LedgerService = ledgerService
    fun requireCaptureEngine(): CaptureEngine = captureEngine
    fun requireBillImporter(): BillImporter = billImporter
    fun requireCloudSyncService(): CloudSyncService = cloudSyncService
}
