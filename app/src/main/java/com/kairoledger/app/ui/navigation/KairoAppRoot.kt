package com.kairoledger.app.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CurrencyExchange
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kairoledger.app.AppContainer
import com.kairoledger.app.data.db.TransactionEntity
import com.kairoledger.app.data.db.TransactionType
import com.kairoledger.app.ui.components.LedgerManageSheet
import com.kairoledger.app.ui.components.LocalPrivacyMasked
import com.kairoledger.app.ui.components.QuickRecordSheet
import com.kairoledger.app.ui.components.SmartInputSheet
import com.kairoledger.app.ui.screens.assets.AccountDetailScreen
import com.kairoledger.app.ui.screens.assets.AssetsScreen
import com.kairoledger.app.ui.screens.categories.CategoryManageScreen
import com.kairoledger.app.ui.screens.finance.DebtCenterScreen
import com.kairoledger.app.ui.screens.finance.ReimburseCenterScreen
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
import com.kairoledger.app.ui.security.AppLockOverlay
import com.kairoledger.app.ui.security.BiometricLockHelper
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

private object OverlayRouteKinds {
    const val NONE = "NONE"
    const val ACCOUNT_DETAIL = "ACCOUNT_DETAIL"
    const val REIMBURSE = "REIMBURSE"
    const val DEBTS = "DEBTS"
    const val CATEGORIES = "CATEGORIES"
}

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
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val activeAccounts by viewModel.activeAccounts.collectAsStateWithLifecycle()
    val allAccounts by viewModel.allAccounts.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val allTransactions by viewModel.allTransactions.collectAsStateWithLifecycle()
    val exchangeRates by viewModel.exchangeRates.collectAsStateWithLifecycle()
    val ledgers by viewModel.ledgers.collectAsStateWithLifecycle()
    val debts by viewModel.debts.collectAsStateWithLifecycle()
    val installments by viewModel.installments.collectAsStateWithLifecycle()
    val pendingCount by viewModel.pendingCount.collectAsStateWithLifecycle()
    val pendingReimburseCount by viewModel.pendingReimburseCount.collectAsStateWithLifecycle()
    val openDebtCount by viewModel.openDebtCount.collectAsStateWithLifecycle()
    val assetsUiState by viewModel.assetsUiState.collectAsStateWithLifecycle()
    val statsUiState by viewModel.statsUiState.collectAsStateWithLifecycle()

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var settingsSubRoute by rememberSaveable { mutableStateOf(SettingsRoutes.HUB) }
    var overlayKind by rememberSaveable { mutableStateOf(OverlayRouteKinds.NONE) }
    var overlayAccountId by rememberSaveable { mutableLongStateOf(0L) }

    // 记账弹窗 / 账本切换 / 智能记账弹窗状态
    var showQuickRecord by remember { mutableStateOf(false) }
    var editingTransaction by remember { mutableStateOf<TransactionEntity?>(null) }
    var refundOriginTransaction by remember { mutableStateOf<TransactionEntity?>(null) }
    var quickRecordInitialType by remember { mutableStateOf(TransactionType.EXPENSE) }
    var quickRecordInitialAccountId by remember { mutableStateOf<Long?>(null) }
    var showLedgerSheet by remember { mutableStateOf(false) }
    var showSmartInput by remember { mutableStateOf(false) }
    var smartInputImagePath by remember { mutableStateOf<String?>(null) }

    // 明细页高级筛选持久态
    var selectedCategoryIdFilter by rememberSaveable { mutableStateOf<Long?>(null) }
    var selectedReimburseFilter by rememberSaveable { mutableStateOf("ALL") }
    var minAmountFilterText by rememberSaveable { mutableStateOf("") }
    var maxAmountFilterText by rememberSaveable { mutableStateOf("") }

    // 应用锁（冷启动与前后台切换触发；设置页初次开启时不会立即锁死当前操作）
    var initialLockEvaluated by rememberSaveable { mutableStateOf(false) }
    var isAppLocked by rememberSaveable { mutableStateOf(false) }
    var wentBackground by rememberSaveable { mutableStateOf(false) }
    var lockError by remember { mutableStateOf<String?>(null) }
    val latestBiometricEnabled by rememberUpdatedState(settings.biometricLockEnabled)

    LaunchedEffect(Unit) {
        if (!initialLockEvaluated) {
            val snapshot = container.settingsStore.getSnapshot()
            if (snapshot.biometricLockEnabled) {
                isAppLocked = true
            }
            initialLockEvaluated = true
        }
    }

    LaunchedEffect(settings.biometricLockEnabled) {
        if (!settings.biometricLockEnabled) {
            isAppLocked = false
            lockError = null
            wentBackground = false
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    if (latestBiometricEnabled) {
                        wentBackground = true
                    }
                }
                Lifecycle.Event.ON_START -> {
                    if (wentBackground && latestBiometricEnabled) {
                        isAppLocked = true
                        lockError = null
                    }
                    wentBackground = false
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 监听 ViewModel 的全局 SnackBar 提示
    LaunchedEffect(viewModel) {
        viewModel.snackbarEvents.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    // 处理桌面小组件 / 悬浮窗 / 外部协议 / 图片分享启动参数
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
            overlayKind = OverlayRouteKinds.NONE
            consumed = true
        }
        if (initialEditTransactionId != null) {
            val found = viewModel.findTransactionById(initialEditTransactionId)
            if (found != null) {
                editingTransaction = found
                refundOriginTransaction = null
                showQuickRecord = true
            } else {
                viewModel.showMessage("未找到指定交易 #$initialEditTransactionId")
            }
            consumed = true
        } else if (!initialReceiptImagePath.isNullOrBlank()) {
            smartInputImagePath = initialReceiptImagePath
            showSmartInput = true
            consumed = true
        } else if (initialOpenSmartInput) {
            smartInputImagePath = null
            showSmartInput = true
            consumed = true
        } else if (initialOpenQuickRecord) {
            editingTransaction = null
            refundOriginTransaction = null
            quickRecordInitialType = TransactionType.EXPENSE
            quickRecordInitialAccountId = null
            showQuickRecord = true
            consumed = true
        }
        if (consumed) {
            onConsumeLaunchArgs()
        }
    }

    val hasOverlayScreen = overlayKind != OverlayRouteKinds.NONE
    val isInsideSettingsSubScreen = !hasOverlayScreen && selectedTab == 3 && settingsSubRoute != SettingsRoutes.HUB

    // 系统返回键：优先退出全屏二级中心，其次退出设置二级页
    BackHandler(enabled = hasOverlayScreen) {
        overlayKind = OverlayRouteKinds.NONE
    }
    BackHandler(enabled = isInsideSettingsSubScreen) {
        settingsSubRoute = SettingsRoutes.HUB
    }

    val activeLedger = remember(ledgers, settings.activeLedgerId) {
        ledgers.firstOrNull { it.id == settings.activeLedgerId }
    }
    val activeLedgerTitle = when {
        settings.activeLedgerId == 0L -> "全部账本"
        activeLedger != null -> activeLedger.name
        else -> "日常账本"
    }

    KairoLedgerTheme(
        themeMode = settings.themeMode,
        dynamicColor = settings.dynamicColor,
        themeStyle = settings.themeStyle
    ) {
        CompositionLocalProvider(LocalPrivacyMasked provides settings.privacyMaskEnabled) {
            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    hasOverlayScreen -> {
                        when (overlayKind) {
                            OverlayRouteKinds.ACCOUNT_DETAIL -> {
                                val targetAccount = allAccounts.firstOrNull { it.id == overlayAccountId }
                                if (targetAccount == null) {
                                    LaunchedEffect(overlayAccountId) {
                                        overlayKind = OverlayRouteKinds.NONE
                                    }
                                } else {
                                    AccountDetailScreen(
                                        account = targetAccount,
                                        transactions = allTransactions,
                                        categories = categories,
                                        installments = installments,
                                        displayCurrency = assetsUiState.displayCurrency,
                                        convertedBalanceMicros = assetsUiState.netWorth.accountConvertedBalances[targetAccount.id],
                                        onBack = { overlayKind = OverlayRouteKinds.NONE },
                                        onQuickRecord = {
                                            editingTransaction = null
                                            refundOriginTransaction = null
                                            quickRecordInitialType = TransactionType.EXPENSE
                                            quickRecordInitialAccountId = targetAccount.id
                                            showQuickRecord = true
                                        },
                                        onQuickTransfer = {
                                            editingTransaction = null
                                            refundOriginTransaction = null
                                            quickRecordInitialType = TransactionType.TRANSFER
                                            quickRecordInitialAccountId = targetAccount.id
                                            showQuickRecord = true
                                        },
                                        onCalibrateBalance = { targetBalance, note ->
                                            viewModel.calibrateAccountBalance(targetAccount.id, targetBalance, note)
                                        },
                                        onEditAccount = { updated -> viewModel.saveAccount(updated) },
                                        onToggleArchive = { archived ->
                                            viewModel.archiveAccount(targetAccount.id, archived)
                                        },
                                        onEditTransaction = { tx ->
                                            editingTransaction = tx
                                            refundOriginTransaction = null
                                            showQuickRecord = true
                                        },
                                        onSaveInstallment = { plan -> viewModel.saveInstallment(plan) },
                                        onRecordInstallmentPeriod = { planId ->
                                            viewModel.recordInstallmentPeriod(planId)
                                        },
                                        onDeleteInstallment = { planId -> viewModel.deleteInstallment(planId) },
                                        pinnedCurrencies = settings.pinnedCurrencies
                                    )
                                }
                            }
                            OverlayRouteKinds.REIMBURSE -> {
                                ReimburseCenterScreen(
                                    transactions = allTransactions,
                                    accounts = activeAccounts,
                                    categories = categories,
                                    baseCurrency = settings.baseCurrency,
                                    rates = exchangeRates,
                                    convertToBase = { tx ->
                                        viewModel.convertTxToDisplayBase(tx, settings.baseCurrency, exchangeRates)
                                    },
                                    onBack = { overlayKind = OverlayRouteKinds.NONE },
                                    onSettleReimbursements = { txIds, targetAccId, note ->
                                        viewModel.settleReimbursements(txIds, targetAccId, note)
                                    },
                                    onMarkStatusNone = { txId -> viewModel.clearTransactionReimburseFlag(txId) },
                                    onClickTransaction = { tx ->
                                        editingTransaction = tx
                                        refundOriginTransaction = null
                                        showQuickRecord = true
                                    }
                                )
                            }
                            OverlayRouteKinds.DEBTS -> {
                                DebtCenterScreen(
                                    debts = debts,
                                    accounts = activeAccounts,
                                    baseCurrency = settings.baseCurrency,
                                    pinnedCurrencies = settings.pinnedCurrencies,
                                    onBack = { overlayKind = OverlayRouteKinds.NONE },
                                    onSaveDebt = { debt, sync -> viewModel.saveDebt(debt, sync) },
                                    onSettleDebt = { debtId, settleAmt, accId, note ->
                                        viewModel.settleDebt(debtId, settleAmt, accId, note)
                                    },
                                    onDeleteDebt = { debtId -> viewModel.deleteDebt(debtId) }
                                )
                            }
                            OverlayRouteKinds.CATEGORIES -> {
                                CategoryManageScreen(
                                    categories = categories,
                                    onSaveCategory = { cat -> viewModel.saveCategory(cat) },
                                    onDeleteCategory = { catId -> viewModel.deleteCategory(catId) },
                                    onBack = { overlayKind = OverlayRouteKinds.NONE }
                                )
                            }
                        }
                    }

                    isInsideSettingsSubScreen -> {
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
                                                refundOriginTransaction = null
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
                    }

                    else -> {
                        Scaffold(
                            topBar = {
                                TopAppBar(
                                    title = {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(999.dp),
                                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(999.dp))
                                                    .clickable { showLedgerSheet = true }
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.MenuBook,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(5.dp))
                                                    Text(
                                                        text = activeLedgerTitle,
                                                        style = MaterialTheme.typography.titleSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.widthIn(max = 128.dp)
                                                    )
                                                    Icon(
                                                        imageVector = Icons.Rounded.ArrowDropDown,
                                                        contentDescription = "切换账本",
                                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                            Surface(
                                                shape = CircleShape,
                                                color = MaterialTheme.colorScheme.surfaceContainerHigh
                                            ) {
                                                Text(
                                                    text = settings.baseCurrency,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                                )
                                            }
                                        }
                                    },
                                    actions = {
                                        IconButton(
                                            onClick = {
                                                viewModel.updateSettings {
                                                    it.copy(privacyMaskEnabled = !it.privacyMaskEnabled)
                                                }
                                            }
                                        ) {
                                            Icon(
                                                imageVector = if (settings.privacyMaskEnabled) {
                                                    Icons.Rounded.VisibilityOff
                                                } else {
                                                    Icons.Rounded.Visibility
                                                },
                                                contentDescription = if (settings.privacyMaskEnabled) "关闭隐私遮罩" else "开启隐私遮罩"
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                smartInputImagePath = null
                                                showSmartInput = true
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.AutoAwesome,
                                                contentDescription = "语音/智能记账"
                                            )
                                        }
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
                                            refundOriginTransaction = null
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
                                            refundOriginTransaction = null
                                            showQuickRecord = true
                                        },
                                        onOpenQuickRecord = {
                                            editingTransaction = null
                                            refundOriginTransaction = null
                                            quickRecordInitialType = TransactionType.EXPENSE
                                            quickRecordInitialAccountId = null
                                            showQuickRecord = true
                                        },
                                        contentPadding = innerPadding,
                                        activeLedgerId = settings.activeLedgerId,
                                        ledgers = ledgers,
                                        selectedCategoryIdFilter = selectedCategoryIdFilter,
                                        onSelectCategoryFilter = { selectedCategoryIdFilter = it },
                                        selectedReimburseFilter = selectedReimburseFilter,
                                        onSelectReimburseFilter = { selectedReimburseFilter = it },
                                        minAmountFilterText = minAmountFilterText,
                                        maxAmountFilterText = maxAmountFilterText,
                                        onAmountRangeFilterChange = { minT, maxT ->
                                            minAmountFilterText = minT
                                            maxAmountFilterText = maxT
                                        },
                                        onRefundTransaction = { tx ->
                                            editingTransaction = null
                                            refundOriginTransaction = tx
                                            quickRecordInitialType = TransactionType.INCOME
                                            quickRecordInitialAccountId = tx.accountId
                                            showQuickRecord = true
                                        },
                                        onBatchUpdateCategory = { ids, catId ->
                                            viewModel.batchUpdateCategory(ids, catId)
                                        },
                                        onBatchDeleteTransactions = { ids ->
                                            viewModel.batchDeleteTransactions(ids)
                                        }
                                    )
                                    1 -> AssetsScreen(
                                        viewModel = viewModel,
                                        onOpenQuickTransfer = {
                                            editingTransaction = null
                                            refundOriginTransaction = null
                                            quickRecordInitialType = TransactionType.TRANSFER
                                            quickRecordInitialAccountId = null
                                            showQuickRecord = true
                                        },
                                        onOpenQuickRecordForAccount = { accId ->
                                            editingTransaction = null
                                            refundOriginTransaction = null
                                            quickRecordInitialType = TransactionType.EXPENSE
                                            quickRecordInitialAccountId = accId
                                            showQuickRecord = true
                                        },
                                        contentPadding = innerPadding,
                                        pendingReimburseCount = pendingReimburseCount,
                                        openDebtCount = openDebtCount,
                                        onOpenAccountDetail = { account ->
                                            overlayAccountId = account.id
                                            overlayKind = OverlayRouteKinds.ACCOUNT_DETAIL
                                        },
                                        onOpenReimburseCenter = {
                                            overlayKind = OverlayRouteKinds.REIMBURSE
                                        },
                                        onOpenDebtCenter = {
                                            overlayKind = OverlayRouteKinds.DEBTS
                                        }
                                    )
                                    2 -> StatsScreen(
                                        viewModel = viewModel,
                                        contentPadding = innerPadding,
                                        granularity = statsUiState.granularity,
                                        onSelectGranularity = { g -> viewModel.setStatsGranularity(g) },
                                        anchorDateMillis = statsUiState.anchorDateMillis,
                                        customStartMillis = statsUiState.customStartMillis,
                                        customEndMillis = statsUiState.customEndMillis,
                                        onShiftPeriod = { delta -> viewModel.shiftStatsPeriod(delta) },
                                        onCustomDateRangeChange = { s, e -> viewModel.setStatsCustomRange(s, e) },
                                        trendBuckets = statsUiState.trendBuckets,
                                        netWorthHistory = statsUiState.netWorthHistory,
                                        allTransactions = allTransactions,
                                        accounts = allAccounts,
                                        ledgers = ledgers,
                                        activeLedgerId = settings.activeLedgerId,
                                        onEditTransaction = { tx ->
                                            editingTransaction = tx
                                            refundOriginTransaction = null
                                            showQuickRecord = true
                                        }
                                    )
                                    else -> AutomationHubScreen(
                                        viewModel = viewModel,
                                        onNavigate = { route ->
                                            when (route) {
                                                SettingsRoutes.CATEGORIES -> overlayKind = OverlayRouteKinds.CATEGORIES
                                                SettingsRoutes.REIMBURSE -> overlayKind = OverlayRouteKinds.REIMBURSE
                                                SettingsRoutes.DEBTS -> overlayKind = OverlayRouteKinds.DEBTS
                                                else -> settingsSubRoute = route
                                            }
                                        },
                                        onOpenLedgerManager = { showLedgerSheet = true },
                                        contentPadding = innerPadding
                                    )
                                }
                            }
                        }
                    }
                }

                // 全局 SnackBar 宿主（同时覆盖主 Tab、设置二级页与全屏业务中心）
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(bottom = if (hasOverlayScreen || isInsideSettingsSubScreen) 16.dp else 88.dp)
                )

                // 隐私生物识别锁屏遮罩
                if (isAppLocked && settings.biometricLockEnabled) {
                    AppLockOverlay(
                        onRequestUnlock = {
                            val activity = context as? FragmentActivity
                            if (activity == null) {
                                isAppLocked = false
                            } else {
                                BiometricLockHelper.authenticate(
                                    activity = activity,
                                    onSuccess = {
                                        isAppLocked = false
                                        lockError = null
                                    },
                                    onFailure = { msg -> lockError = msg }
                                )
                            }
                        },
                        errorMessage = lockError
                    )
                }
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
                    ledgers = ledgers,
                    initialLedgerId = settings.activeLedgerId.takeIf { it > 0L } ?: 1L,
                    refundOfTransaction = refundOriginTransaction,
                    llmEnabled = settings.llmEnabled,
                    onAnalyzeReceiptImage = { path -> viewModel.parseReceiptImage(path) },
                    onOpenCategoryManager = {
                        showQuickRecord = false
                        editingTransaction = null
                        refundOriginTransaction = null
                        overlayKind = OverlayRouteKinds.CATEGORIES
                    },
                    onDismiss = {
                        showQuickRecord = false
                        editingTransaction = null
                        refundOriginTransaction = null
                    }
                )
            }

            if (showLedgerSheet) {
                LedgerManageSheet(
                    ledgers = ledgers,
                    activeLedgerId = settings.activeLedgerId,
                    pinnedCurrencies = settings.pinnedCurrencies,
                    onSelectLedger = { ledgerId -> viewModel.setActiveLedger(ledgerId) },
                    onSaveLedger = { ledger -> viewModel.saveLedger(ledger) },
                    onArchiveLedger = { ledgerId, archived ->
                        viewModel.archiveLedger(ledgerId, archived)
                    },
                    onDismiss = { showLedgerSheet = false }
                )
            }

            if (showSmartInput) {
                SmartInputSheet(
                    accounts = activeAccounts,
                    categories = categories,
                    ledgers = ledgers,
                    activeLedgerId = settings.activeLedgerId,
                    baseCurrency = settings.baseCurrency,
                    llmEnabled = settings.llmEnabled,
                    initialImagePath = smartInputImagePath,
                    onParseText = { text -> viewModel.parseSmartInputText(text) },
                    onParseImage = { path -> viewModel.parseReceiptImage(path) },
                    onSaveDrafts = { drafts, ledgerId, attachmentPath ->
                        viewModel.saveSmartDrafts(drafts, ledgerId, attachmentPath) {
                            showSmartInput = false
                            smartInputImagePath = null
                        }
                    },
                    onDismiss = {
                        showSmartInput = false
                        smartInputImagePath = null
                    }
                )
            }
        }
    }
}
