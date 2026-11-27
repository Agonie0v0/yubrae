package com.yubrae.app.ui.navigation

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.FabPosition
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yubrae.app.AppContainer
import com.yubrae.app.data.db.AccountEntity
import com.yubrae.app.data.db.AccountGroup
import com.yubrae.app.data.db.TagEntity
import com.yubrae.app.data.db.TransactionEntity
import com.yubrae.app.data.db.TransactionType
import com.yubrae.app.data.prefs.AppSettings
import com.yubrae.app.ui.components.AppUpdateDialog
import com.yubrae.app.ui.components.LedgerManageSheet
import com.yubrae.app.ui.components.LocalPrivacyMasked
import com.yubrae.app.ui.components.QuickRecordSheet
import com.yubrae.app.ui.components.SmartInputSheet
import com.yubrae.app.ui.components.YubraeBrandMark
import kotlinx.coroutines.delay
import com.yubrae.app.ui.screens.assets.AccountDetailScreen
import com.yubrae.app.ui.screens.assets.AssetsScreen
import com.yubrae.app.ui.screens.assets.HiddenAssetsScreen
import com.yubrae.app.ui.screens.categories.CategoryManageScreen
import com.yubrae.app.ui.screens.categories.TagManageScreen
import com.yubrae.app.ui.screens.finance.DebtCenterScreen
import com.yubrae.app.ui.screens.finance.ReimburseCenterScreen
import com.yubrae.app.ui.screens.settings.AppearanceSecurityScreen
import com.yubrae.app.ui.screens.settings.AutoCaptureSettingsScreen
import com.yubrae.app.ui.screens.settings.CaptureLogsScreen
import com.yubrae.app.ui.screens.settings.ExchangeRatesScreen
import com.yubrae.app.ui.screens.settings.ImportAndBackupScreen
import com.yubrae.app.ui.screens.settings.LlmSettingsScreen
import com.yubrae.app.ui.screens.settings.MineHubScreen
import com.yubrae.app.ui.screens.settings.RecurringRulesScreen
import com.yubrae.app.ui.screens.settings.RulesAndAliasesScreen
import com.yubrae.app.ui.screens.settings.SettingsRoutes
import com.yubrae.app.ui.screens.stats.StatsScreen
import com.yubrae.app.ui.screens.transactions.TransactionsScreen
import com.yubrae.app.ui.security.AppLockOverlay
import com.yubrae.app.ui.security.BiometricLockHelper
import com.yubrae.app.ui.theme.YubraeTheme
import com.yubrae.app.ui.viewmodel.KairoMainViewModel
import com.yubrae.app.ui.viewmodel.KairoViewModelFactory
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.abs

internal fun shouldDisplayInRootSnackbarHost(
    latestShowQuickRecord: Boolean,
    currentShowQuickRecord: Boolean
): Boolean = !(latestShowQuickRecord && currentShowQuickRecord)

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
    const val HIDDEN_ASSETS = "HIDDEN_ASSETS"
    const val REIMBURSE = "REIMBURSE"
    const val DEBTS = "DEBTS"
    const val CATEGORIES = "CATEGORIES"
    const val TAGS = "TAGS"
}

private sealed interface RootLayer {
    data object MainTabs : RootLayer
    data class SettingsSub(val route: String) : RootLayer
    data class Overlay(val kind: String, val accountId: Long) : RootLayer
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
    val hasAvailableUpdate by viewModel.hasAvailableUpdate.collectAsStateWithLifecycle()
    val showUpdateDialog by viewModel.showUpdateDialog.collectAsStateWithLifecycle()
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        delay(1_500L)
        viewModel.checkForUpdates(manual = false)
    }

    // 为 4 个主 Tab 保留持久滚动位置
    val txListState = rememberLazyListState()
    val assetsListState = rememberLazyListState()
    val statsListState = rememberLazyListState()
    val mineListState = rememberLazyListState()

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val pagerState = rememberPagerState(initialPage = selectedTab.coerceIn(0, bottomTabs.lastIndex)) { bottomTabs.size }
    var isTxMultiSelect by remember { mutableStateOf(false) }
    var settingsSubRoute by rememberSaveable { mutableStateOf(SettingsRoutes.HUB) }
    var overlayKind by rememberSaveable { mutableStateOf(OverlayRouteKinds.NONE) }
    var overlayAccountId by rememberSaveable { mutableLongStateOf(0L) }

    // 记账弹窗 / 账本管理弹窗 / 智能记账弹窗状态
    var showQuickRecord by remember { mutableStateOf(false) }
    var editingTransaction by remember { mutableStateOf<TransactionEntity?>(null) }
    var refundOriginTransaction by remember { mutableStateOf<TransactionEntity?>(null) }
    var quickRecordInitialType by remember { mutableStateOf(TransactionType.EXPENSE) }
    var quickRecordInitialAccountId by remember { mutableStateOf<Long?>(null) }
    var quickRecordInitialToAccountId by remember { mutableStateOf<Long?>(null) }
    var quickRecordInitialAmountMicros by remember { mutableStateOf<Long?>(null) }
    var showLedgerSheet by remember { mutableStateOf(false) }
    var showSmartInput by remember { mutableStateOf(false) }
    var smartInputImagePath by remember { mutableStateOf<String?>(null) }

    // 应用锁（冷启动与前后台切换触发；设置页初次开启时不会立即锁死当前操作）
    var initialLockEvaluated by rememberSaveable { mutableStateOf(false) }
    var isAppLocked by rememberSaveable { mutableStateOf(false) }
    var wentBackground by rememberSaveable { mutableStateOf(false) }
    var lockError by remember { mutableStateOf<String?>(null) }
    var isAppInBackground by remember { mutableStateOf(false) }
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
                Lifecycle.Event.ON_PAUSE -> {
                    isAppInBackground = true
                }
                Lifecycle.Event.ON_STOP -> {
                    isAppInBackground = true
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
                Lifecycle.Event.ON_RESUME -> {
                    isAppInBackground = false
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 监听 ViewModel 全局 SnackBar 提示（全屏记账页展示期间跳过底层重复弹窗）
    val latestShowQuickRecord by rememberUpdatedState(showQuickRecord)
    LaunchedEffect(viewModel) {
        viewModel.snackbarEvents.collect { msg ->
            if (shouldDisplayInRootSnackbarHost(latestShowQuickRecord, showQuickRecord)) {
                snackbarHostState.showSnackbar(msg)
            }
        }
    }

    // 双向同步 HorizontalPager 与底栏选中状态
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collect { page ->
                selectedTab = page
                if (page != 0) {
                    isTxMultiSelect = false
                }
                if (page != 3) {
                    settingsSubRoute = SettingsRoutes.HUB
                }
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
            val targetTab = initialTab.coerceIn(0, 3)
            selectedTab = targetTab
            settingsSubRoute = SettingsRoutes.HUB
            overlayKind = OverlayRouteKinds.NONE
            pagerState.scrollToPage(targetTab)
            consumed = true
        }
        if (initialEditTransactionId != null) {
            val found = viewModel.findTransactionById(initialEditTransactionId)
            if (found != null) {
                editingTransaction = found
                refundOriginTransaction = null
                quickRecordInitialToAccountId = null
                quickRecordInitialAmountMicros = null
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
            quickRecordInitialToAccountId = null
            quickRecordInitialAmountMicros = null
            showQuickRecord = true
            consumed = true
        }
        if (consumed) {
            onConsumeLaunchArgs()
        }
    }

    val hasOverlayScreen = overlayKind != OverlayRouteKinds.NONE
    val isInsideSettingsSubScreen = !hasOverlayScreen && selectedTab == 3 && settingsSubRoute != SettingsRoutes.HUB
    val currentLayer = when {
        hasOverlayScreen -> RootLayer.Overlay(overlayKind, overlayAccountId)
        isInsideSettingsSubScreen -> RootLayer.SettingsSub(settingsSubRoute)
        else -> RootLayer.MainTabs
    }

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

    val openCreditRepay: (AccountEntity) -> Unit = { creditAccount ->
        val candidates = viewModel.activeAccounts.value
            .ifEmpty { viewModel.allAccounts.value.filter { !it.archived } }
            .filter { it.id != creditAccount.id }
        val preferredPayer = candidates.firstOrNull {
            (it.groupType == AccountGroup.DEBIT || it.groupType == AccountGroup.WALLET) &&
                it.currencyCode.equals(creditAccount.currencyCode, ignoreCase = true)
        } ?: candidates.firstOrNull {
            it.groupType == AccountGroup.DEBIT || it.groupType == AccountGroup.WALLET
        } ?: candidates.firstOrNull {
            it.groupType != AccountGroup.CREDIT
        } ?: candidates.firstOrNull()

        editingTransaction = null
        refundOriginTransaction = null
        quickRecordInitialType = TransactionType.TRANSFER
        quickRecordInitialAccountId = preferredPayer?.id
        quickRecordInitialToAccountId = creditAccount.id
        quickRecordInitialAmountMicros = (-creditAccount.balanceMicros).takeIf { it > 0L }
        showQuickRecord = true
    }

    YubraeTheme(
        themeMode = settings.themeMode,
        dynamicColor = settings.dynamicColor,
        themeStyle = settings.themeStyle,
        oledPureBlack = settings.oledPureBlack,
        greenExpenseMode = settings.greenExpenseMode
    ) {
        CompositionLocalProvider(LocalPrivacyMasked provides settings.privacyMaskEnabled) {
            val showRecentsPrivacyVeil = settings.hideRecentsPreviewEnabled &&
                isAppInBackground &&
                !(isAppLocked && settings.biometricLockEnabled)

            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (showRecentsPrivacyVeil && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                Modifier.blur(24.dp)
                            } else {
                                Modifier
                            }
                        )
                ) {
                AnimatedContent(
                    targetState = currentLayer,
                    transitionSpec = {
                        when {
                            initialState is RootLayer.MainTabs && targetState !is RootLayer.MainTabs -> {
                                ((slideInHorizontally(
                                    animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
                                    initialOffsetX = { fullWidth -> fullWidth / 3 }
                                ) + fadeIn(animationSpec = tween(durationMillis = 240))) togetherWith
                                    (fadeOut(animationSpec = tween(durationMillis = 180)) +
                                        scaleOut(
                                            targetScale = 0.98f,
                                            animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing)
                                        )))
                                    .apply { targetContentZIndex = 1f }
                                    .using(SizeTransform(clip = false))
                            }
                            initialState !is RootLayer.MainTabs && targetState is RootLayer.MainTabs -> {
                                ((fadeIn(animationSpec = tween(durationMillis = 220)) +
                                    scaleIn(
                                        initialScale = 0.98f,
                                        animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing)
                                    )) togetherWith
                                    (slideOutHorizontally(
                                        animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
                                        targetOffsetX = { fullWidth -> fullWidth / 2 }
                                    ) + fadeOut(animationSpec = tween(durationMillis = 200))))
                                    .apply { targetContentZIndex = -1f }
                                    .using(SizeTransform(clip = false))
                            }
                            else -> {
                                (fadeIn(animationSpec = tween(durationMillis = 200)) togetherWith
                                    fadeOut(animationSpec = tween(durationMillis = 180)))
                                    .using(SizeTransform(clip = false))
                            }
                        }
                    },
                    label = "root_layer_transition"
                ) { layer ->
                    when (layer) {
                        is RootLayer.Overlay -> {
                            when (layer.kind) {
                                OverlayRouteKinds.ACCOUNT_DETAIL -> AccountDetailHost(
                                    viewModel = viewModel,
                                    accountId = layer.accountId,
                                    settings = settings,
                                    onBack = { overlayKind = OverlayRouteKinds.NONE },
                                    onQuickRecord = { accountId ->
                                        editingTransaction = null
                                        refundOriginTransaction = null
                                        quickRecordInitialType = TransactionType.EXPENSE
                                        quickRecordInitialAccountId = accountId
                                        quickRecordInitialToAccountId = null
                                        quickRecordInitialAmountMicros = null
                                        showQuickRecord = true
                                    },
                                    onQuickTransfer = { accountId ->
                                        editingTransaction = null
                                        refundOriginTransaction = null
                                        quickRecordInitialType = TransactionType.TRANSFER
                                        quickRecordInitialAccountId = accountId
                                        quickRecordInitialToAccountId = null
                                        quickRecordInitialAmountMicros = null
                                        showQuickRecord = true
                                    },
                                    onQuickRepayCredit = { creditAccount ->
                                        openCreditRepay(creditAccount)
                                    },
                                    onEditTransaction = { tx ->
                                        editingTransaction = tx
                                        refundOriginTransaction = null
                                        quickRecordInitialToAccountId = null
                                        quickRecordInitialAmountMicros = null
                                        showQuickRecord = true
                                    },
                                    onRefundTransaction = { tx ->
                                        editingTransaction = null
                                        refundOriginTransaction = tx
                                        quickRecordInitialType = TransactionType.INCOME
                                        quickRecordInitialAccountId = tx.accountId
                                        quickRecordInitialToAccountId = null
                                        quickRecordInitialAmountMicros = null
                                        showQuickRecord = true
                                    },
                                    onOpenAccountAliases = {
                                        overlayKind = OverlayRouteKinds.NONE
                                        selectedTab = 3
                                        settingsSubRoute = SettingsRoutes.RULES
                                        scope.launch {
                                            pagerState.scrollToPage(3)
                                        }
                                    }
                                )
                                OverlayRouteKinds.HIDDEN_ASSETS -> HiddenAssetsScreen(
                                    viewModel = viewModel,
                                    onOpenAccountDetail = { account ->
                                        overlayAccountId = account.id
                                        overlayKind = OverlayRouteKinds.ACCOUNT_DETAIL
                                    },
                                    onBack = { overlayKind = OverlayRouteKinds.NONE }
                                )
                                OverlayRouteKinds.REIMBURSE -> ReimburseCenterHost(
                                    viewModel = viewModel,
                                    settings = settings,
                                    onBack = { overlayKind = OverlayRouteKinds.NONE },
                                    onClickTransaction = { tx ->
                                        editingTransaction = tx
                                        refundOriginTransaction = null
                                        quickRecordInitialToAccountId = null
                                        quickRecordInitialAmountMicros = null
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

                        is RootLayer.SettingsSub -> {
                            when (layer.route) {
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
                                                quickRecordInitialToAccountId = null
                                                quickRecordInitialAmountMicros = null
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

                        RootLayer.MainTabs -> {
                            Scaffold(
                                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                                floatingActionButtonPosition = FabPosition.End,
                                floatingActionButton = {
                                    AnimatedVisibility(
                                        visible = pagerState.currentPage == 0 && !isTxMultiSelect,
                                        enter = fadeIn() + scaleIn(initialScale = 0.85f),
                                        exit = fadeOut() + scaleOut(targetScale = 0.85f)
                                    ) {
                                        HomeAiRecordFab(
                                            onSmartInput = {
                                                smartInputImagePath = null
                                                showSmartInput = true
                                            }
                                        )
                                    }
                                },
                                bottomBar = {
                                    YubraeBottomBar(
                                        selectedTab = selectedTab,
                                        pendingCount = pendingCount,
                                        hasUpdateBadge = hasAvailableUpdate,
                                        onSelectTab = { index ->
                                            if (index == 3 || index != pagerState.currentPage) {
                                                settingsSubRoute = SettingsRoutes.HUB
                                            }
                                            if (index != pagerState.currentPage) {
                                                scope.launch {
                                                    pagerState.animateScrollToPage(
                                                        page = index,
                                                        animationSpec = tween(
                                                            durationMillis = if (abs(index - pagerState.currentPage) > 1) 280 else 240,
                                                            easing = FastOutSlowInEasing
                                                        )
                                                    )
                                                }
                                            }
                                        },
                                        onQuickRecord = {
                                            editingTransaction = null
                                            refundOriginTransaction = null
                                            quickRecordInitialType = TransactionType.EXPENSE
                                            quickRecordInitialAccountId = null
                                            quickRecordInitialToAccountId = null
                                            quickRecordInitialAmountMicros = null
                                            showQuickRecord = true
                                        },
                                        onSmartInput = {
                                            smartInputImagePath = null
                                            showSmartInput = true
                                        }
                                    )
                                }
                            ) { innerPadding ->
                                HorizontalPager(
                                    state = pagerState,
                                    userScrollEnabled = !isTxMultiSelect,
                                    beyondViewportPageCount = 0,
                                    modifier = Modifier.fillMaxSize()
                                ) { page ->
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .graphicsLayer {
                                                val pageOffset = abs(pagerState.getOffsetDistanceInPages(page))
                                                val fraction = pageOffset.coerceIn(0f, 1f)
                                                alpha = 1f - 0.14f * fraction
                                                val scale = 1f - 0.02f * fraction
                                                scaleX = scale
                                                scaleY = scale
                                            }
                                    ) {
                                        saveableStateHolder.SaveableStateProvider(page) {
                                            when (page) {
                                                0 -> TransactionsScreen(
                                                    viewModel = viewModel,
                                                    onEditTransaction = { tx ->
                                                        editingTransaction = tx
                                                        refundOriginTransaction = null
                                                        quickRecordInitialToAccountId = null
                                                        quickRecordInitialAmountMicros = null
                                                        showQuickRecord = true
                                                    },
                                                    onOpenQuickRecord = {
                                                        editingTransaction = null
                                                        refundOriginTransaction = null
                                                        quickRecordInitialType = TransactionType.EXPENSE
                                                        quickRecordInitialAccountId = null
                                                        quickRecordInitialToAccountId = null
                                                        quickRecordInitialAmountMicros = null
                                                        showQuickRecord = true
                                                    },
                                                    contentPadding = innerPadding,
                                                    onRefundTransaction = { tx ->
                                                        editingTransaction = null
                                                        refundOriginTransaction = tx
                                                        quickRecordInitialType = TransactionType.INCOME
                                                        quickRecordInitialAccountId = tx.accountId
                                                        quickRecordInitialToAccountId = null
                                                        quickRecordInitialAmountMicros = null
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
                                                    },
                                                    onMultiSelectModeChange = { isTxMultiSelect = it }
                                                )
                                                1 -> AssetsScreen(
                                                    viewModel = viewModel,
                                                    onOpenQuickTransfer = {
                                                        editingTransaction = null
                                                        refundOriginTransaction = null
                                                        quickRecordInitialType = TransactionType.TRANSFER
                                                        quickRecordInitialAccountId = null
                                                        quickRecordInitialToAccountId = null
                                                        quickRecordInitialAmountMicros = null
                                                        showQuickRecord = true
                                                    },
                                                    onOpenQuickRecordForAccount = { accId ->
                                                        editingTransaction = null
                                                        refundOriginTransaction = null
                                                        quickRecordInitialType = TransactionType.EXPENSE
                                                        quickRecordInitialAccountId = accId
                                                        quickRecordInitialToAccountId = null
                                                        quickRecordInitialAmountMicros = null
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
                                                        scope.launch {
                                                            pagerState.scrollToPage(3)
                                                        }
                                                    },
                                                    onOpenHiddenAssets = {
                                                        overlayKind = OverlayRouteKinds.HIDDEN_ASSETS
                                                    },
                                                    onOpenCreditRepay = { creditAccount ->
                                                        openCreditRepay(creditAccount)
                                                    }
                                                )
                                                2 -> StatsScreen(
                                                    viewModel = viewModel,
                                                    contentPadding = innerPadding,
                                                    onEditTransaction = { tx ->
                                                        editingTransaction = tx
                                                        refundOriginTransaction = null
                                                        quickRecordInitialToAccountId = null
                                                        quickRecordInitialAmountMicros = null
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
                                                    onTogglePrivacy = onTogglePrivacy,
                                                    listState = mineListState,
                                                    contentPadding = innerPadding
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 全局 SnackBar 宿主（覆盖主 Tab、设置二级页与全屏业务中心；Tab 0 显示右下角 FAB 时抬高并避让 FAB）
                val showHomeFab = !hasOverlayScreen &&
                    !isInsideSettingsSubScreen &&
                    pagerState.currentPage == 0 &&
                    !isTxMultiSelect
                val snackbarBottomPadding = when {
                    hasOverlayScreen || isInsideSettingsSubScreen -> 16.dp
                    showHomeFab -> 78.dp
                    else -> 68.dp
                }
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(
                            end = if (showHomeFab) 66.dp else 0.dp,
                            bottom = snackbarBottomPadding
                        )
                )

                val dismissQuickRecord = {
                    showQuickRecord = false
                    editingTransaction = null
                    refundOriginTransaction = null
                    quickRecordInitialAccountId = null
                    quickRecordInitialToAccountId = null
                    quickRecordInitialAmountMicros = null
                }
                BackHandler(enabled = showQuickRecord) {
                    dismissQuickRecord()
                }

                if (showQuickRecord) {
                    val activeAccounts by viewModel.activeAccounts.collectAsStateWithLifecycle()
                    val categories by viewModel.categories.collectAsStateWithLifecycle()
                    val ledgers by viewModel.ledgers.collectAsStateWithLifecycle()
                    val tags by viewModel.tags.collectAsStateWithLifecycle()
                    val resolvedLedgerId = settings.activeLedgerId.takeIf { id ->
                        id > 0L && ledgers.any { it.id == id && !it.archived }
                    } ?: viewModel.firstActiveLedgerId(ledgers) ?: 0L
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
                        initialLedgerId = resolvedLedgerId,
                        refundOfTransaction = refundOriginTransaction,
                        llmEnabled = settings.llmEnabled,
                        onAnalyzeReceiptImage = { path -> viewModel.parseReceiptImage(path) },
                        onOpenCategoryManager = {
                            dismissQuickRecord()
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
                        onQuickCreateCategory = { category ->
                            viewModel.createCategoryAndReturnId(category)
                        },
                        initialToAccountId = quickRecordInitialToAccountId,
                        initialAmountMicros = quickRecordInitialAmountMicros,
                        onDismiss = dismissQuickRecord
                    )
                }
                }

                // 后台多任务卡片隐私磨砂遮罩
                if (showRecentsPrivacyVeil) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background.copy(
                            alpha = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 0.74f else 0.96f
                        ),
                        contentColor = MaterialTheme.colorScheme.onBackground
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            YubraeBrandMark(size = 64.dp)
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "余白 · Yubrae",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "隐私保护中",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

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

            if (showLedgerSheet) {
                val ledgers by viewModel.ledgers.collectAsStateWithLifecycle()
                val allTransactions by viewModel.allTransactions.collectAsStateWithLifecycle()
                val budgets by viewModel.budgets.collectAsStateWithLifecycle()
                LedgerManageSheet(
                    ledgers = ledgers,
                    activeLedgerId = settings.activeLedgerId,
                    pinnedCurrencies = settings.pinnedCurrencies,
                    onSelectLedger = { ledgerId -> viewModel.setActiveLedger(ledgerId) },
                    onSaveLedger = { ledger -> viewModel.saveLedger(ledger) },
                    onArchiveLedger = { ledgerId, archived ->
                        viewModel.archiveLedger(ledgerId, archived)
                    },
                    onDismiss = { showLedgerSheet = false },
                    transactions = allTransactions,
                    budgets = budgets,
                    onDeleteLedger = { ledgerId, migrateToLedgerId ->
                        viewModel.deleteLedger(ledgerId, migrateToLedgerId)
                    }
                )
            }

            if (showSmartInput) {
                val activeAccounts by viewModel.activeAccounts.collectAsStateWithLifecycle()
                val categories by viewModel.categories.collectAsStateWithLifecycle()
                val ledgers by viewModel.ledgers.collectAsStateWithLifecycle()
                val resolvedLedgerId = settings.activeLedgerId.takeIf { id ->
                    id > 0L && ledgers.any { it.id == id && !it.archived }
                } ?: viewModel.firstActiveLedgerId(ledgers) ?: 0L
                SmartInputSheet(
                    accounts = activeAccounts,
                    categories = categories,
                    ledgers = ledgers,
                    activeLedgerId = resolvedLedgerId,
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
                    },
                    isVoiceAiFallbackReady = viewModel.isVoiceAiFallbackReady(),
                    onTranscribeVoiceAudio = { wavBytes -> viewModel.transcribeVoiceAudio(wavBytes) }
                )
            }

            if (showUpdateDialog && !(isAppLocked && settings.biometricLockEnabled)) {
                AppUpdateDialog(
                    state = updateState,
                    mirrorPrefix = settings.updateMirrorPrefix,
                    canInstallPackages = { viewModel.canRequestPackageInstalls(context) },
                    onDismiss = { viewModel.dismissUpdateDialog() },
                    onRetryCheck = { viewModel.checkForUpdates(manual = true) },
                    onStartDownload = { info -> viewModel.startUpdateDownload(info) },
                    onCancelDownload = { viewModel.cancelUpdateDownload() },
                    onInstallReady = { viewModel.installReadyUpdate(context) },
                    onOpenUnknownSources = { viewModel.openUnknownAppSourcesSettings(context) },
                    onIgnoreVersion = { tag -> viewModel.ignoreUpdateVersion(tag) },
                    onOpenBrowser = { url -> viewModel.openUpdateReleasePage(context, url) },
                    onSaveMirrorPrefix = { prefix -> viewModel.saveUpdateMirrorPrefix(prefix) }
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
    onQuickRepayCredit: (AccountEntity) -> Unit,
    onEditTransaction: (TransactionEntity) -> Unit,
    onRefundTransaction: (TransactionEntity) -> Unit,
    onOpenAccountAliases: () -> Unit
) {
    val allAccounts by viewModel.allAccounts.collectAsStateWithLifecycle()
    val allTransactions by viewModel.allTransactions.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val installments by viewModel.installments.collectAsStateWithLifecycle()
    val assetsUiState by viewModel.assetsUiState.collectAsStateWithLifecycle()

    val targetAccount = allAccounts.firstOrNull { it.id == accountId }
    var seenTargetAccount by remember(accountId) { mutableStateOf(false) }
    LaunchedEffect(targetAccount != null) {
        if (targetAccount != null) {
            seenTargetAccount = true
        }
    }
    if (targetAccount == null) {
        LaunchedEffect(accountId, seenTargetAccount, allAccounts.size) {
            if (seenTargetAccount || allAccounts.isNotEmpty()) {
                onBack()
            }
        }
        return
    }

    AccountDetailScreen(
        account = targetAccount,
        allAccounts = allAccounts,
        transactions = allTransactions,
        categories = categories,
        installments = installments,
        displayCurrency = assetsUiState.displayCurrency,
        convertedBalanceMicros = assetsUiState.netWorth.accountConvertedBalances[targetAccount.id],
        onBack = onBack,
        onQuickRecord = { onQuickRecord(targetAccount.id) },
        onQuickTransfer = { onQuickTransfer(targetAccount.id) },
        onCalibrateBalance = { targetBalance, note, createRecord ->
            viewModel.calibrateAccountBalance(targetAccount.id, targetBalance, note, createRecord)
        },
        onEditAccount = { updated -> viewModel.saveAccount(updated) },
        onToggleArchive = { archived ->
            viewModel.archiveAccount(targetAccount.id, archived)
        },
        onDeleteAccount = { migrateToAccountId ->
            viewModel.deleteAccount(targetAccount.id, migrateToAccountId) {
                onBack()
            }
        },
        onEditTransaction = onEditTransaction,
        onSaveInstallment = { plan -> viewModel.saveInstallment(plan) },
        onRecordInstallmentPeriod = { planId -> viewModel.recordInstallmentPeriod(planId) },
        onDeleteInstallment = { planId -> viewModel.deleteInstallment(planId) },
        pinnedCurrencies = settings.pinnedCurrencies,
        onQuickRepayCredit = { onQuickRepayCredit(targetAccount) },
        onDeleteTransaction = { txId -> viewModel.deleteTransaction(txId) },
        onDuplicateTransaction = { tx -> viewModel.duplicateTransaction(tx) },
        onRefundTransaction = onRefundTransaction,
        onBatchUpdateCategory = { ids, catId -> viewModel.batchUpdateCategory(ids, catId) },
        onBatchMoveAccount = { ids, targetAccId -> viewModel.batchMoveTransactionsAccount(ids, targetAccId) },
        onBatchDeleteTransactions = { ids -> viewModel.batchDeleteTransactions(ids) },
        onOpenAccountAliases = onOpenAccountAliases
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

@Composable
private fun HomeAiRecordFab(
    onSmartInput: () -> Unit
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .size(50.dp)
            .shadow(
                elevation = 5.dp,
                shape = CircleShape,
                ambientColor = primaryColor.copy(alpha = 0.25f),
                spotColor = primaryColor.copy(alpha = 0.25f)
            )
            .clip(CircleShape)
            .background(primaryColor)
            .clickable(onClick = onSmartInput),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.AutoAwesome,
            contentDescription = "AI 快速记账",
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(22.dp)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun YubraeBottomBar(
    selectedTab: Int,
    pendingCount: Int,
    hasUpdateBadge: Boolean = false,
    onSelectTab: (Int) -> Unit,
    onQuickRecord: () -> Unit,
    onSmartInput: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLowest
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                bottomTabs.take(2).forEach { tab ->
                    YubraeNavTabItem(
                        tab = tab,
                        selected = selectedTab == tab.index,
                        pendingCount = if (tab.index == 0) pendingCount else 0,
                        showDotBadge = false,
                        onClick = { onSelectTab(tab.index) },
                        modifier = Modifier.weight(1f)
                    )
                }

                // 中央手动 + 记账按钮（单击手动记账，长按可选直达 AI 速记）
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .combinedClickable(
                                onClick = onQuickRecord,
                                onClickLabel = "手动记一笔",
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onSmartInput()
                                },
                                onLongClickLabel = "AI速记"
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = "记一笔",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(23.dp)
                        )
                    }
                }

                bottomTabs.drop(2).forEach { tab ->
                    YubraeNavTabItem(
                        tab = tab,
                        selected = selectedTab == tab.index,
                        pendingCount = 0,
                        showDotBadge = tab.index == 3 && hasUpdateBadge,
                        onClick = { onSelectTab(tab.index) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun YubraeNavTabItem(
    tab: BottomTabItem,
    selected: Boolean,
    pendingCount: Int,
    showDotBadge: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tint by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(durationMillis = 200),
        label = "nav_tab_tint"
    )
    val pillProgress by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "nav_tab_pill"
    )

    Column(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .width(48.dp)
                .height(28.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        alpha = pillProgress
                        scaleX = 0.65f + 0.35f * pillProgress
                        scaleY = 0.8f + 0.2f * pillProgress
                    }
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f))
            )
            Box(
                modifier = Modifier.size(24.dp),
                contentAlignment = Alignment.Center
            ) {
                if (tab.index == 0 && pendingCount > 0) {
                    BadgedBox(
                        badge = {
                            Badge(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            ) {
                                Text(
                                    text = if (pendingCount > 99) "99+" else pendingCount.toString(),
                                    fontSize = 9.sp,
                                    lineHeight = 10.sp
                                )
                            }
                        }
                    ) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.label,
                            tint = tint,
                            modifier = Modifier.size(21.dp)
                        )
                    }
                } else if (showDotBadge) {
                    BadgedBox(
                        badge = {
                            Badge(containerColor = MaterialTheme.colorScheme.error)
                        }
                    ) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.label,
                            tint = tint,
                            modifier = Modifier.size(21.dp)
                        )
                    }
                } else {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.label,
                        tint = tint,
                        modifier = Modifier.size(21.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = tab.label,
            color = tint,
            fontSize = 10.5.sp,
            lineHeight = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1
        )
    }
}
