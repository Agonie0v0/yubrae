package com.kairoledger.app.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kairoledger.app.AppContainer
import com.kairoledger.app.data.db.TagEntity
import com.kairoledger.app.data.db.TransactionEntity
import com.kairoledger.app.data.db.TransactionType
import com.kairoledger.app.data.prefs.AppSettings
import com.kairoledger.app.ui.components.LedgerManageSheet
import com.kairoledger.app.ui.components.LocalPrivacyMasked
import com.kairoledger.app.ui.components.QuickRecordSheet
import com.kairoledger.app.ui.components.SmartInputSheet
import com.kairoledger.app.ui.screens.assets.AccountDetailScreen
import com.kairoledger.app.ui.screens.assets.AssetsScreen
import com.kairoledger.app.ui.screens.categories.CategoryManageScreen
import com.kairoledger.app.ui.screens.categories.TagManageScreen
import com.kairoledger.app.ui.screens.finance.DebtCenterScreen
import com.kairoledger.app.ui.screens.finance.ReimburseCenterScreen
import com.kairoledger.app.ui.screens.settings.AppearanceSecurityScreen
import com.kairoledger.app.ui.screens.settings.AutoCaptureSettingsScreen
import com.kairoledger.app.ui.screens.settings.CaptureLogsScreen
import com.kairoledger.app.ui.screens.settings.ExchangeRatesScreen
import com.kairoledger.app.ui.screens.settings.ImportAndBackupScreen
import com.kairoledger.app.ui.screens.settings.LlmSettingsScreen
import com.kairoledger.app.ui.screens.settings.MineHubScreen
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
    BottomTabItem(0, "明细", Icons.AutoMirrored.Rounded.ReceiptLong),
    BottomTabItem(1, "资产", Icons.Rounded.AccountBalanceWallet),
    BottomTabItem(2, "统计", Icons.Rounded.PieChart),
    BottomTabItem(3, "我的", Icons.Rounded.Person)
)

private object OverlayRouteKinds {
    const val NONE = "NONE"
    const val ACCOUNT_DETAIL = "ACCOUNT_DETAIL"
    const val REIMBURSE = "REIMBURSE"
    const val DEBTS = "DEBTS"
    const val CATEGORIES = "CATEGORIES"
    const val TAGS = "TAGS"
}

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
    val saveableStateHolder = rememberSaveableStateHolder()

    // 根组件仅订阅轻量全局元数据与角标计数，重数据流下沉到各子页面按需收集
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val pendingCount by viewModel.pendingCount.collectAsStateWithLifecycle()
    val pendingReimburseCount by viewModel.pendingReimburseCount.collectAsStateWithLifecycle()
    val openDebtCount by viewModel.openDebtCount.collectAsStateWithLifecycle()

    // 为 4 个主 Tab 保留持久滚动位置
    val txListState = rememberLazyListState()
    val assetsListState = rememberLazyListState()
    val statsListState = rememberLazyListState()
    val mineListState = rememberLazyListState()

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var settingsSubRoute by rememberSaveable { mutableStateOf(SettingsRoutes.HUB) }
    var overlayKind by rememberSaveable { mutableStateOf(OverlayRouteKinds.NONE) }
    var overlayAccountId by rememberSaveable { mutableLongStateOf(0L) }

    // 记账弹窗 / 账本管理弹窗 / 智能记账弹窗状态
    var showQuickRecord by remember { mutableStateOf(false) }
    var editingTransaction by remember { mutableStateOf<TransactionEntity?>(null) }
    var refundOriginTransaction by remember { mutableStateOf<TransactionEntity?>(null) }
    var quickRecordInitialType by remember { mutableStateOf(TransactionType.EXPENSE) }
    var quickRecordInitialAccountId by remember { mutableStateOf<Long?>(null) }
    var showLedgerSheet by remember { mutableStateOf(false) }
    var showSmartInput by remember { mutableStateOf(false) }
    var smartInputImagePath by remember { mutableStateOf<String?>(null) }

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

    // 监听 ViewModel 全局 SnackBar 提示
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

    // 系统返回键：优先退出全屏二级中心，其次退出二级设置页
    BackHandler(enabled = hasOverlayScreen) {
        overlayKind = OverlayRouteKinds.NONE
    }
    BackHandler(enabled = isInsideSettingsSubScreen) {
        settingsSubRoute = SettingsRoutes.HUB
    }

    val onTogglePrivacy = remember(viewModel) {
        {
            viewModel.updateSettings { it.copy(privacyMaskEnabled = !it.privacyMaskEnabled) }
        }
    }

    KairoLedgerTheme(
        themeMode = settings.themeMode,
        dynamicColor = settings.dynamicColor,
        themeStyle = settings.themeStyle,
        oledPureBlack = settings.oledPureBlack,
        greenExpenseMode = settings.greenExpenseMode
    ) {
        CompositionLocalProvider(LocalPrivacyMasked provides settings.privacyMaskEnabled) {
            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    hasOverlayScreen -> {
                        when (overlayKind) {
                            OverlayRouteKinds.ACCOUNT_DETAIL -> AccountDetailHost(
                                viewModel = viewModel,
                                accountId = overlayAccountId,
                                settings = settings,
                                onBack = { overlayKind = OverlayRouteKinds.NONE },
                                onQuickRecord = { accountId ->
                                    editingTransaction = null
                                    refundOriginTransaction = null
                                    quickRecordInitialType = TransactionType.EXPENSE
                                    quickRecordInitialAccountId = accountId
                                    showQuickRecord = true
                                },
                                onQuickTransfer = { accountId ->
                                    editingTransaction = null
                                    refundOriginTransaction = null
                                    quickRecordInitialType = TransactionType.TRANSFER
                                    quickRecordInitialAccountId = accountId
                                    showQuickRecord = true
                                },
                                onEditTransaction = { tx ->
                                    editingTransaction = tx
                                    refundOriginTransaction = null
                                    showQuickRecord = true
                                }
                            )
                            OverlayRouteKinds.REIMBURSE -> ReimburseCenterHost(
                                viewModel = viewModel,
                                settings = settings,
                                onBack = { overlayKind = OverlayRouteKinds.NONE },
                                onClickTransaction = { tx ->
                                    editingTransaction = tx
                                    refundOriginTransaction = null
                                    showQuickRecord = true
                                }
                            )
                            OverlayRouteKinds.DEBTS -> DebtCenterHost(
                                viewModel = viewModel,
                                settings = settings,
                                onBack = { overlayKind = OverlayRouteKinds.NONE }
                            )
                            OverlayRouteKinds.CATEGORIES -> CategoryManageHost(
                                viewModel = viewModel,
                                onBack = { overlayKind = OverlayRouteKinds.NONE }
                            )
                            OverlayRouteKinds.TAGS -> TagManageHost(
                                viewModel = viewModel,
                                onBack = { overlayKind = OverlayRouteKinds.NONE }
                            )
                        }
                    }

                    isInsideSettingsSubScreen -> {
                        Crossfade(targetState = settingsSubRoute, label = "settings_sub_route") { route ->
                            when (route) {
                                SettingsRoutes.AUTO_CAPTURE -> AutoCaptureSettingsScreen(
                                    viewModel = viewModel,
                                    onBack = { settingsSubRoute = SettingsRoutes.HUB }
                                )
                                SettingsRoutes.APPEARANCE -> AppearanceSecurityScreen(
                                    viewModel = viewModel,
                                    onBack = { settingsSubRoute = SettingsRoutes.HUB }
                                )
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
                            contentWindowInsets = WindowInsets(0, 0, 0, 0),
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
                            // 零动画瞬时切换 + 状态持久化，消除 Crossfade 双树同帧重组开销
                            saveableStateHolder.SaveableStateProvider(selectedTab) {
                                when (selectedTab) {
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
                                        },
                                        listState = txListState,
                                        onOpenLedgerManager = { showLedgerSheet = true },
                                        onTogglePrivacy = onTogglePrivacy,
                                        onOpenSmartInput = {
                                            smartInputImagePath = null
                                            showSmartInput = true
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
                                        },
                                        listState = assetsListState,
                                        onTogglePrivacy = onTogglePrivacy,
                                        onOpenExchangeRates = {
                                            selectedTab = 3
                                            settingsSubRoute = SettingsRoutes.RATES
                                        }
                                    )
                                    2 -> StatsScreen(
                                        viewModel = viewModel,
                                        contentPadding = innerPadding,
                                        onEditTransaction = { tx ->
                                            editingTransaction = tx
                                            refundOriginTransaction = null
                                            showQuickRecord = true
                                        },
                                        listState = statsListState,
                                        onOpenLedgerManager = { showLedgerSheet = true },
                                        onTogglePrivacy = onTogglePrivacy
                                    )
                                    else -> MineHubScreen(
                                        viewModel = viewModel,
                                        onNavigate = { route ->
                                            when (route) {
                                                SettingsRoutes.CATEGORIES -> overlayKind = OverlayRouteKinds.CATEGORIES
                                                SettingsRoutes.TAGS -> overlayKind = OverlayRouteKinds.TAGS
                                                SettingsRoutes.REIMBURSE -> overlayKind = OverlayRouteKinds.REIMBURSE
                                                SettingsRoutes.DEBTS -> overlayKind = OverlayRouteKinds.DEBTS
                                                else -> settingsSubRoute = route
                                            }
                                        },
                                        onOpenLedgerManager = { showLedgerSheet = true },
                                        pendingReimburseCount = pendingReimburseCount,
                                        openDebtCount = openDebtCount,
                                        onTogglePrivacy = onTogglePrivacy,
                                        listState = mineListState,
                                        contentPadding = innerPadding
                                    )
                                }
                            }
                        }
                    }
                }

                // 全局 SnackBar 宿主（覆盖主 Tab、设置二级页与全屏业务中心）
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
                val activeAccounts by viewModel.activeAccounts.collectAsStateWithLifecycle()
                val categories by viewModel.categories.collectAsStateWithLifecycle()
                val ledgers by viewModel.ledgers.collectAsStateWithLifecycle()
                val tags by viewModel.tags.collectAsStateWithLifecycle()
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
                    availableTags = tags,
                    onQuickCreateTag = { name, colorHex ->
                        viewModel.saveTag(
                            TagEntity(
                                name = name,
                                groupName = "自定义",
                                colorHex = colorHex,
                                sortOrder = 300,
                                isSystem = false
                            )
                        )
                    },
                    onDismiss = {
                        showQuickRecord = false
                        editingTransaction = null
                        refundOriginTransaction = null
                    }
                )
            }

            if (showLedgerSheet) {
                val ledgers by viewModel.ledgers.collectAsStateWithLifecycle()
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
                val activeAccounts by viewModel.activeAccounts.collectAsStateWithLifecycle()
                val categories by viewModel.categories.collectAsStateWithLifecycle()
                val ledgers by viewModel.ledgers.collectAsStateWithLifecycle()
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

@Composable
private fun AccountDetailHost(
    viewModel: KairoMainViewModel,
    accountId: Long,
    settings: AppSettings,
    onBack: () -> Unit,
    onQuickRecord: (Long) -> Unit,
    onQuickTransfer: (Long) -> Unit,
    onEditTransaction: (TransactionEntity) -> Unit
) {
    val allAccounts by viewModel.allAccounts.collectAsStateWithLifecycle()
    val allTransactions by viewModel.allTransactions.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val installments by viewModel.installments.collectAsStateWithLifecycle()
    val assetsUiState by viewModel.assetsUiState.collectAsStateWithLifecycle()

    val targetAccount = allAccounts.firstOrNull { it.id == accountId }
    if (targetAccount == null) {
        LaunchedEffect(accountId, allAccounts.size) {
            if (allAccounts.isNotEmpty()) {
                onBack()
            }
        }
        return
    }

    AccountDetailScreen(
        account = targetAccount,
        transactions = allTransactions,
        categories = categories,
        installments = installments,
        displayCurrency = assetsUiState.displayCurrency,
        convertedBalanceMicros = assetsUiState.netWorth.accountConvertedBalances[targetAccount.id],
        onBack = onBack,
        onQuickRecord = { onQuickRecord(targetAccount.id) },
        onQuickTransfer = { onQuickTransfer(targetAccount.id) },
        onCalibrateBalance = { targetBalance, note ->
            viewModel.calibrateAccountBalance(targetAccount.id, targetBalance, note)
        },
        onEditAccount = { updated -> viewModel.saveAccount(updated) },
        onToggleArchive = { archived ->
            viewModel.archiveAccount(targetAccount.id, archived)
        },
        onEditTransaction = onEditTransaction,
        onSaveInstallment = { plan -> viewModel.saveInstallment(plan) },
        onRecordInstallmentPeriod = { planId -> viewModel.recordInstallmentPeriod(planId) },
        onDeleteInstallment = { planId -> viewModel.deleteInstallment(planId) },
        pinnedCurrencies = settings.pinnedCurrencies
    )
}

@Composable
private fun ReimburseCenterHost(
    viewModel: KairoMainViewModel,
    settings: AppSettings,
    onBack: () -> Unit,
    onClickTransaction: (TransactionEntity) -> Unit
) {
    val allTransactions by viewModel.allTransactions.collectAsStateWithLifecycle()
    val activeAccounts by viewModel.activeAccounts.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val exchangeRates by viewModel.exchangeRates.collectAsStateWithLifecycle()

    ReimburseCenterScreen(
        transactions = allTransactions,
        accounts = activeAccounts,
        categories = categories,
        baseCurrency = settings.baseCurrency,
        rates = exchangeRates,
        convertToBase = { tx ->
            viewModel.convertTxToDisplayBase(tx, settings.baseCurrency, exchangeRates)
        },
        onBack = onBack,
        onSettleReimbursements = { txIds, targetAccId, note ->
            viewModel.settleReimbursements(txIds, targetAccId, note)
        },
        onMarkStatusNone = { txId -> viewModel.clearTransactionReimburseFlag(txId) },
        onClickTransaction = onClickTransaction
    )
}

@Composable
private fun DebtCenterHost(
    viewModel: KairoMainViewModel,
    settings: AppSettings,
    onBack: () -> Unit
) {
    val debts by viewModel.debts.collectAsStateWithLifecycle()
    val activeAccounts by viewModel.activeAccounts.collectAsStateWithLifecycle()

    DebtCenterScreen(
        debts = debts,
        accounts = activeAccounts,
        baseCurrency = settings.baseCurrency,
        pinnedCurrencies = settings.pinnedCurrencies,
        onBack = onBack,
        onSaveDebt = { debt, sync -> viewModel.saveDebt(debt, sync) },
        onSettleDebt = { debtId, settleAmt, accId, note ->
            viewModel.settleDebt(debtId, settleAmt, accId, note)
        },
        onDeleteDebt = { debtId -> viewModel.deleteDebt(debtId) }
    )
}

@Composable
private fun CategoryManageHost(
    viewModel: KairoMainViewModel,
    onBack: () -> Unit
) {
    val categories by viewModel.categories.collectAsStateWithLifecycle()

    CategoryManageScreen(
        categories = categories,
        onSaveCategory = { cat -> viewModel.saveCategory(cat) },
        onDeleteCategory = { catId -> viewModel.deleteCategory(catId) },
        onSeedPresets = { viewModel.seedPresetCatalog() },
        onBack = onBack
    )
}

@Composable
private fun TagManageHost(
    viewModel: KairoMainViewModel,
    onBack: () -> Unit
) {
    val tags by viewModel.tags.collectAsStateWithLifecycle()
    val transactions by viewModel.allTransactions.collectAsStateWithLifecycle()

    TagManageScreen(
        tags = tags,
        transactions = transactions,
        onSaveTag = { tag -> viewModel.saveTag(tag) },
        onDeleteTag = { tagId -> viewModel.deleteTag(tagId) },
        onSeedPresets = { viewModel.seedPresetCatalog() },
        onBack = onBack
    )
}
