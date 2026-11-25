package com.yubrae.app

import android.content.Context
import com.yubrae.app.capture.CaptureOrchestrator
import com.yubrae.app.core.contract.BillImporter
import com.yubrae.app.core.contract.CaptureEngine
import com.yubrae.app.core.contract.CloudSyncService
import com.yubrae.app.core.contract.ExchangeRateProvider
import com.yubrae.app.core.contract.LedgerService
import com.yubrae.app.data.db.KairoDatabase
import com.yubrae.app.data.prefs.SettingsStore
import com.yubrae.app.data.repository.ExchangeRateRepositoryImpl
import com.yubrae.app.data.repository.LedgerRepositoryImpl
import com.yubrae.app.importer.YimuCsvImporter
import com.yubrae.app.sync.WebDavSyncManager
import com.yubrae.app.update.AppUpdateManager
import okhttp3.OkHttpClient
import java.io.File
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
        settingsStore = settingsStore,
        receiptsDir = File(appContext.filesDir, "receipts")
    )

    val cloudSyncService: CloudSyncService = WebDavSyncManager(
        db = database,
        settingsStore = settingsStore,
        okHttpClient = okHttpClient
    )

    val appUpdateManager: AppUpdateManager = AppUpdateManager(
        context = appContext,
        baseHttpClient = okHttpClient
    )

    fun requireExchangeRateProvider(): ExchangeRateProvider = exchangeRateProvider
    fun requireLedgerService(): LedgerService = ledgerService
    fun requireCaptureEngine(): CaptureEngine = captureEngine
    fun requireBillImporter(): BillImporter = billImporter
    fun requireCloudSyncService(): CloudSyncService = cloudSyncService
}
