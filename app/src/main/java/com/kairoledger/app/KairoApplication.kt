package com.kairoledger.app

import android.app.Application
import com.kairoledger.app.data.db.DefaultSeedData
import com.kairoledger.app.sync.BackupWorkScheduler
import com.kairoledger.app.widget.KairoWidgetUpdater
import com.kairoledger.app.worker.WorkScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class KairoApplication : Application() {

    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @OptIn(FlowPreview::class)
    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        WorkScheduler.schedulePeriodicWork(this)

        appScope.launch {
            DefaultSeedData.ensureSeeded(container.database)
            runCatching { KairoWidgetUpdater.updateAll(this@KairoApplication) }
        }

        // 根据自动备份开关动态调度 WebDAV 周期任务
        appScope.launch {
            container.settingsStore.settingsFlow
                .map { it.autoBackupEnabled }
                .distinctUntilChanged()
                .collect { enabled ->
                    BackupWorkScheduler.scheduleAutoBackup(this@KairoApplication, enabled)
                }
        }

        // 流水或汇率变更后静默防抖刷新桌面小组件
        appScope.launch {
            combine(
                container.database.transactionDao().observeAll(),
                container.database.exchangeRateDao().observeAll(),
                container.settingsStore.settingsFlow.map { it.baseCurrency }.distinctUntilChanged()
            ) { _, _, _ -> Unit }
                .drop(1)
                .debounce(800L)
                .collect {
                    runCatching { KairoWidgetUpdater.updateAll(this@KairoApplication) }
                }
        }
    }
}
