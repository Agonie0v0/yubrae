package com.kairoledger.app.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CurrencyExchange
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kairoledger.app.AppContainer
import com.kairoledger.app.data.db.TransactionEntity
import com.kairoledger.app.data.db.TransactionType
import com.kairoledger.app.ui.components.QuickRecordSheet
import com.kairoledger.app.ui.screens.assets.AssetsScreen
import com.kairoledger.app.ui.screens.settings.AutomationHubScreen
import com.kairoledger.app.ui.screens.settings.CaptureLogsScreen
import com.kairoledger.app.ui.screens.settings.ExchangeRatesScreen
import com.kairoledger.app.ui.screens.settings.ImportAndBackupScreen
import com.kairoledger.app.ui.screens.settings.LlmSettingsScreen
import com.kairoledger.app.ui.screens.settings.RecurringRulesScreen
import com.kairoledger.app.ui.screens.settings.RulesAndAliasesScreen
import com.kairoledger.app.ui.screens.settings.SettingsRoutes
import com.kairoledger.app.ui.screens.stats.StatsScreen
import com.kairoledger.app.ui.screens.transactions.TransactionsScreen
import com.kairoledger.app.ui.theme.KairoLedgerTheme
import com.kairoledger.app.ui.viewmodel.KairoMainViewModel
import com.kairoledger.app.ui.viewmodel.KairoViewModelFactory
import kotlinx.coroutines.launch

private data class BottomTabItem(
    val index: Int,
    val label: String,
    val icon: ImageVector
)

private val bottomTabs = listOf(
    BottomTabItem(0, "明细", Icons.Rounded.ReceiptLong),
    BottomTabItem(1, "资产", Icons.Rounded.AccountBalanceWallet),
    BottomTabItem(2, "统计", Icons.Rounded.PieChart),
    BottomTabItem(3, "自动化", Icons.Rounded.AutoAwesome)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KairoAppRoot(
    container: AppContainer,
    initialOpenQuickRecord: Boolean = false,
    initialEditTransactionId: Long? = null,
    initialTab: Int? = null,
    initialOpenSmartInput: Boolean = false,
    initialReceiptImagePath: String? = null,
    onConsumeLaunchArgs: () -> Unit = {}
) {
    val viewModel: KairoMainViewModel = viewModel(
        factory = remember(container) { KairoViewModelFactory(container) }
    )
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val activeAccounts by viewModel.activeAccounts.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val pendingCount by viewModel.pendingCount.collectAsStateWithLifecycle()

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var settingsSubRoute by rememberSaveable { mutableStateOf(SettingsRoutes.HUB) }

    var showQuickRecord by remember { mutableStateOf(false) }
    var editingTransaction by remember { mutableStateOf<TransactionEntity?>(null) }
    var quickRecordInitialType by remember { mutableStateOf(TransactionType.EXPENSE) }
    var quickRecordInitialAccountId by remember { mutableStateOf<Long?>(null) }

    // 监听 ViewModel 的全局 SnackBar 提示
    LaunchedEffect(viewModel) {
        viewModel.snackbarEvents.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    // 处理桌面小组件 / 悬浮窗 / 外部协议启动参数
    LaunchedEffect(
        initialOpenQuickRecord,
        initialEditTransactionId,
        initialTab,
        initialOpenSmartInput,
        initialReceiptImagePath
    ) {
        var consumed = false
        if (initialTab != null) {
            selectedTab = initialTab.coerceIn(0, 3)
            settingsSubRoute = SettingsRoutes.HUB
            consumed = true
        }
        if (initialEditTransactionId != null) {
            val found = viewModel.findTransactionById(initialEditTransactionId)
            if (found != null) {
                editingTransaction = found
                showQuickRecord = true
            } else {
                viewModel.showMessage("未找到指定交易 #$initialEditTransactionId")
            }
            consumed = true
        } else if (initialOpenQuickRecord) {
            editingTransaction = null
            quickRecordInitialType = TransactionType.EXPENSE
            quickRecordInitialAccountId = null
            showQuickRecord = true
            consumed = true
        }
        if (initialOpenSmartInput || !initialReceiptImagePath.isNullOrBlank()) {
            consumed = true
        }
        if (consumed) {
            onConsumeLaunchArgs()
        }
    }

    val isInsideSettingsSubScreen = selectedTab == 3 && settingsSubRoute != SettingsRoutes.HUB

    // 拦截系统返回手势，从二级设置页退回自动化主页
    BackHandler(enabled = isInsideSettingsSubScreen) {
        settingsSubRoute = SettingsRoutes.HUB
    }

    KairoLedgerTheme(
        themeMode = settings.themeMode,
        dynamicColor = settings.dynamicColor
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (isInsideSettingsSubScreen) {
                Crossfade(targetState = settingsSubRoute, label = "settings_sub_route") { route ->
                    when (route) {
                        SettingsRoutes.LOGS -> CaptureLogsScreen(
                            viewModel = viewModel,
                            onBack = { settingsSubRoute = SettingsRoutes.HUB },
                            onOpenTransaction = { txId ->
                                scope.launch {
                                    val found = viewModel.findTransactionById(txId)
                                    if (found != null) {
                                        editingTransaction = found
                                        showQuickRecord = true
                                    } else {
                                        viewModel.showMessage("未找到关联交易 #$txId")
                                    }
                                }
                            }
                        )
                        SettingsRoutes.RULES -> RulesAndAliasesScreen(
                            viewModel = viewModel,
                            onBack = { settingsSubRoute = SettingsRoutes.HUB }
                        )
                        SettingsRoutes.LLM -> LlmSettingsScreen(
                            viewModel = viewModel,
                            onBack = { settingsSubRoute = SettingsRoutes.HUB }
                        )
                        SettingsRoutes.RATES -> ExchangeRatesScreen(
                            viewModel = viewModel,
                            onBack = { settingsSubRoute = SettingsRoutes.HUB }
                        )
                        SettingsRoutes.RECURRING -> RecurringRulesScreen(
                            viewModel = viewModel,
                            onBack = { settingsSubRoute = SettingsRoutes.HUB }
                        )
                        SettingsRoutes.IMPORT_BACKUP -> ImportAndBackupScreen(
                            viewModel = viewModel,
                            onBack = { settingsSubRoute = SettingsRoutes.HUB }
                        )
                    }
                }
            } else {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = when (selectedTab) {
                                            0 -> "棱账 · 明细"
                                            1 -> "棱账 · 资产"
                                            2 -> "棱账 · 统计"
                                            else -> "棱账 · 自动化"
                                        },
                                        fontWeight = FontWeight.Bold
                                    )
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                                    ) {
                                        Text(
                                            text = settings.baseCurrency,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            },
                            actions = {
                                IconButton(
                                    onClick = {
                                        selectedTab = 3
                                        settingsSubRoute = SettingsRoutes.RATES
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.CurrencyExchange,
                                        contentDescription = "汇率中心"
                                    )
                                }
                            }
                        )
                    },
                    bottomBar = {
                        NavigationBar {
                            bottomTabs.forEach { tab ->
                                NavigationBarItem(
                                    selected = selectedTab == tab.index,
                                    onClick = {
                                        selectedTab = tab.index
                                        if (tab.index != 3) {
                                            settingsSubRoute = SettingsRoutes.HUB
                                        }
                                    },
                                    icon = {
                                        if (tab.index == 0 && pendingCount > 0) {
                                            BadgedBox(
                                                badge = {
                                                    Badge {
                                                        Text(if (pendingCount > 99) "99+" else pendingCount.toString())
                                                    }
                                                }
                                            ) {
                                                Icon(tab.icon, contentDescription = tab.label)
                                            }
                                        } else {
                                            Icon(tab.icon, contentDescription = tab.label)
                                        }
                                    },
                                    label = { Text(tab.label) }
                                )
                            }
                        }
                    },
                    floatingActionButton = {
                        if (selectedTab != 3) {
                            ExtendedFloatingActionButton(
                                onClick = {
                                    editingTransaction = null
                                    quickRecordInitialType = TransactionType.EXPENSE
                                    quickRecordInitialAccountId = null
                                    showQuickRecord = true
                                },
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                shape = CircleShape
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("记一笔", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                ) { innerPadding ->
                    Crossfade(targetState = selectedTab, label = "main_tab_crossfade") { tab ->
                        when (tab) {
                            0 -> TransactionsScreen(
                                viewModel = viewModel,
                                onEditTransaction = { tx ->
                                    editingTransaction = tx
                                    showQuickRecord = true
                                },
                                onOpenQuickRecord = {
                                    editingTransaction = null
                                    quickRecordInitialType = TransactionType.EXPENSE
                                    quickRecordInitialAccountId = null
                                    showQuickRecord = true
                                },
                                contentPadding = innerPadding
                            )
                            1 -> AssetsScreen(
                                viewModel = viewModel,
                                onOpenQuickTransfer = {
                                    editingTransaction = null
                                    quickRecordInitialType = TransactionType.TRANSFER
                                    quickRecordInitialAccountId = null
                                    showQuickRecord = true
                                },
                                onOpenQuickRecordForAccount = { accId ->
                                    editingTransaction = null
                                    quickRecordInitialType = TransactionType.EXPENSE
                                    quickRecordInitialAccountId = accId
                                    showQuickRecord = true
                                },
                                contentPadding = innerPadding
                            )
                            2 -> StatsScreen(
                                viewModel = viewModel,
                                contentPadding = innerPadding
                            )
                            else -> AutomationHubScreen(
                                viewModel = viewModel,
                                onNavigate = { route -> settingsSubRoute = route },
                                contentPadding = innerPadding
                            )
                        }
                    }
                }
            }

            // 全局 SnackBar 宿主（同时覆盖主 Tab 与二级设置页）
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(bottom = if (isInsideSettingsSubScreen) 16.dp else 88.dp)
            )
        }

        if (showQuickRecord) {
            QuickRecordSheet(
                viewModel = viewModel,
                accounts = activeAccounts,
                categories = categories,
                pinnedCurrencies = settings.pinnedCurrencies,
                baseCurrency = settings.baseCurrency,
                existingTransaction = editingTransaction,
                initialType = quickRecordInitialType,
                initialAccountId = quickRecordInitialAccountId,
                onDismiss = {
                    showQuickRecord = false
                    editingTransaction = null
                }
            )
        }
    }
}
