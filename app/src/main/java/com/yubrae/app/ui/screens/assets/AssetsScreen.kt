package com.yubrae.app.ui.screens.assets

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yubrae.app.core.model.CurrencyCatalog
import com.yubrae.app.core.model.MoneyAmount
import com.yubrae.app.data.db.AccountEntity
import com.yubrae.app.data.db.AccountGroup
import com.yubrae.app.data.prefs.AssetHiddenModule
import com.yubrae.app.ui.components.CartoonAccountIcon
import com.yubrae.app.ui.components.CategoryIconBadge
import com.yubrae.app.ui.components.CompactHairlineDivider
import com.yubrae.app.ui.components.CurrencyBadge
import com.yubrae.app.ui.components.CurrencyPickerDialog
import com.yubrae.app.ui.components.EmptyStateCard
import com.yubrae.app.ui.components.GroupedIconPicker
import com.yubrae.app.ui.components.KairoIcons
import com.yubrae.app.ui.components.LocalPrivacyMasked
import com.yubrae.app.ui.components.YimuCard
import com.yubrae.app.ui.components.YubraeDialog
import com.yubrae.app.ui.components.formatPrivacyAmount
import com.yubrae.app.ui.theme.KairoTheme
import com.yubrae.app.ui.theme.tabular
import com.yubrae.app.ui.viewmodel.KairoMainViewModel
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.launch

private val DefaultOpenAccountDetail: (AccountEntity) -> Unit = {}

@Composable
fun AssetsScreen(
    viewModel: KairoMainViewModel,
    onOpenQuickTransfer: () -> Unit,
    onOpenQuickRecordForAccount: (Long) -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    pendingReimburseCount: Int = 0,
    openDebtCount: Int = 0,
    onOpenAccountDetail: (AccountEntity) -> Unit = DefaultOpenAccountDetail,
    onOpenReimburseCenter: () -> Unit = {},
    onOpenDebtCenter: () -> Unit = {},
    listState: LazyListState = rememberLazyListState(),
    onTogglePrivacy: () -> Unit = {
        viewModel.updateSettings { it.copy(privacyMaskEnabled = !it.privacyMaskEnabled) }
    },
    onOpenExchangeRates: () -> Unit = {},
    onOpenHiddenAssets: () -> Unit = {}
) {
    val uiState by viewModel.assetsUiState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val privacyMasked = LocalPrivacyMasked.current
    val hasDetailCallback = onOpenAccountDetail !== DefaultOpenAccountDetail
    val semantic = KairoTheme.semantic
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    var actionAccount by remember { mutableStateOf<AccountEntity?>(null) }
    var editingAccount by remember { mutableStateOf<AccountEntity?>(null) }
    var createInitialGroup by remember { mutableStateOf<String?>(null) }
    var calibrateTarget by remember { mutableStateOf<AccountEntity?>(null) }
    var showDisplayCurrencyMenu by remember { mutableStateOf(false) }
    var showNetWorthMoreMenu by remember { mutableStateOf(false) }
    var showMoreDisplayCurrencies by remember { mutableStateOf(false) }
    var collapsedSections by remember { mutableStateOf<Set<String>>(emptySet()) }

    // 顶部下拉进入「资产隐藏」手势状态
    var pullOffsetPx by remember { mutableFloatStateOf(0f) }
    var thresholdHapticFired by remember { mutableStateOf(false) }
    val triggerThresholdPx = remember(density) { with(density) { 72.dp.toPx() } }
    val maxPullPx = remember(density) { with(density) { 108.dp.toPx() } }
    val latestOnOpenHiddenAssets by rememberUpdatedState(onOpenHiddenAssets)

    fun settlePullGesture() {
        if (pullOffsetPx >= triggerThresholdPx) {
            pullOffsetPx = 0f
            thresholdHapticFired = false
            latestOnOpenHiddenAssets()
        } else if (pullOffsetPx > 0f) {
            val start = pullOffsetPx
            thresholdHapticFired = false
            scope.launch {
                Animatable(start).animateTo(0f, tween(220)) {
                    pullOffsetPx = value
                }
            }
        } else {
            thresholdHapticFired = false
        }
    }

    val pullToHiddenConnection = remember(triggerThresholdPx, maxPullPx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y < 0f && pullOffsetPx > 0f) {
                    val consumedY = available.y.coerceAtLeast(-pullOffsetPx)
                    pullOffsetPx = (pullOffsetPx + consumedY).coerceAtLeast(0f)
                    if (pullOffsetPx < triggerThresholdPx) {
                        thresholdHapticFired = false
                    }
                    return Offset(0f, consumedY)
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                val atTop = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
                if (atTop && available.y > 0f) {
                    val next = (pullOffsetPx + available.y * 0.48f).coerceIn(0f, maxPullPx)
                    if (next >= triggerThresholdPx && !thresholdHapticFired) {
                        thresholdHapticFired = true
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    } else if (next < triggerThresholdPx) {
                        thresholdHapticFired = false
                    }
                    pullOffsetPx = next
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (pullOffsetPx > 0f) {
                    settlePullGesture()
                }
                return Velocity.Zero
            }
        }
    }

    val allGroupedAccounts = remember(uiState.groupedAccounts) {
        AccountGroup.all.flatMap { uiState.groupedAccounts[it].orEmpty() }
            .sortedWith(compareBy({ it.sortOrder }, { it.id }))
    }
    val visibleAccounts = remember(allGroupedAccounts, uiState.hiddenAccountIds) {
        allGroupedAccounts.filter { it.id !in uiState.hiddenAccountIds }
    }
    val displayDigits = remember(uiState.displayCurrency) {
        CurrencyCatalog.find(uiState.displayCurrency).minorDigits
    }

    val showReimburseCard = AssetHiddenModule.REIMBURSE !in uiState.hiddenModules
    val showDebtCard = AssetHiddenModule.DEBT !in uiState.hiddenModules
    val showInvestCard = AssetHiddenModule.INVEST !in uiState.hiddenModules
    val hasAnySummaryCard = showReimburseCard || showDebtCard || showInvestCard

    Box(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(pullToHiddenConnection)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Final)
                        if (pullOffsetPx > 0f && event.changes.none { it.pressed }) {
                            settlePullGesture()
                        }
                    }
                }
            }
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                bottom = contentPadding.calculateBottomPadding() + 84.dp
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (pullOffsetPx > 4f) {
                item(key = "pull_hidden_indicator") {
                    val ready = pullOffsetPx >= triggerThresholdPx
                    val bannerHeightDp = with(density) { pullOffsetPx.toDp() }.coerceAtLeast(24.dp)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .height(bannerHeightDp),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = if (ready) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = if (ready) Icons.Rounded.VisibilityOff else Icons.Rounded.ArrowDownward,
                                    contentDescription = null,
                                    tint = if (ready) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = if (ready) "松手进入资产隐藏" else "下拉进入资产隐藏",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = if (ready) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // 1. 顶部一木风净资产圆角大卡（左：净资产 + 小眼睛；右：总资产 / 负资产 + ⋮ 菜单）
            item(key = "net_worth_card", contentType = "net_worth_hero") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(start = 12.dp, end = 12.dp, top = 8.dp)
                ) {
                    YimuNetWorthCard(
                        displayCurrency = uiState.displayCurrency,
                        displayDigits = displayDigits,
                        totalAssetsMicros = uiState.netWorth.totalAssetsMicros,
                        totalLiabilitiesMicros = uiState.netWorth.totalLiabilitiesMicros,
                        netWorthMicros = uiState.netWorth.netWorthMicros,
                        privacyMasked = privacyMasked,
                        pinnedCurrencies = settings.pinnedCurrencies,
                        showArchived = uiState.showArchived,
                        showCurrencyMenu = showDisplayCurrencyMenu,
                        showMoreMenu = showNetWorthMoreMenu,
                        onToggleCurrencyMenu = { showDisplayCurrencyMenu = it },
                        onToggleMoreMenu = { showNetWorthMoreMenu = it },
                        onSelectCurrency = { code ->
                            viewModel.setAssetsDisplayCurrency(code)
                            showDisplayCurrencyMenu = false
                        },
                        onOpenMoreCurrencies = {
                            showDisplayCurrencyMenu = false
                            showMoreDisplayCurrencies = true
                        },
                        onTogglePrivacy = onTogglePrivacy,
                        onOpenHiddenAssets = {
                            showNetWorthMoreMenu = false
                            onOpenHiddenAssets()
                        },
                        onOpenQuickTransfer = {
                            showNetWorthMoreMenu = false
                            onOpenQuickTransfer()
                        },
                        onOpenExchangeRates = {
                            showNetWorthMoreMenu = false
                            onOpenExchangeRates()
                        },
                        onToggleShowArchived = {
                            showNetWorthMoreMenu = false
                            viewModel.setShowArchivedAccounts(!uiState.showArchived)
                        },
                        onCreateAccount = {
                            showNetWorthMoreMenu = false
                            createInitialGroup = AccountGroup.WALLET
                        }
                    )
                }
            }

            // 2. 三等分汇总卡：报销 · 债务 · 理财（随隐藏配置动态隐藏）
            if (hasAnySummaryCard) {
                item(key = "asset_summary_modules", contentType = "summary_modules") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (showReimburseCard) {
                            YimuSummaryModuleCard(
                                title = "报销",
                                badgeCount = pendingReimburseCount,
                                firstLabel = "可报",
                                firstValue = formatPrivacyAmount(
                                    privacyMasked,
                                    MoneyAmount.formatPlain(uiState.pendingReimburseMicros, displayDigits, grouping = true)
                                ),
                                secondLabel = "已报",
                                secondValue = formatPrivacyAmount(
                                    privacyMasked,
                                    MoneyAmount.formatPlain(uiState.reimbursedMicros, displayDigits, grouping = true)
                                ),
                                firstValueColor = MaterialTheme.colorScheme.onSurface,
                                secondValueColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                onClick = onOpenReimburseCenter,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (showDebtCard) {
                            YimuSummaryModuleCard(
                                title = "债务",
                                badgeCount = openDebtCount,
                                firstLabel = "应付",
                                firstValue = formatPrivacyAmount(
                                    privacyMasked,
                                    MoneyAmount.formatPlain(uiState.debtPayableMicros, displayDigits, grouping = true)
                                ),
                                secondLabel = "应收",
                                secondValue = formatPrivacyAmount(
                                    privacyMasked,
                                    MoneyAmount.formatPlain(uiState.debtReceivableMicros, displayDigits, grouping = true)
                                ),
                                firstValueColor = if (uiState.debtPayableMicros > 0L) semantic.expense else MaterialTheme.colorScheme.onSurfaceVariant,
                                secondValueColor = semantic.income,
                                onClick = onOpenDebtCenter,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (showInvestCard) {
                            val profitPrefix = if (uiState.investProfitMicros > 0L) "+" else ""
                            YimuSummaryModuleCard(
                                title = "理财",
                                badgeCount = 0,
                                firstLabel = "总额",
                                firstValue = formatPrivacyAmount(
                                    privacyMasked,
                                    MoneyAmount.formatPlain(uiState.investTotalMicros, displayDigits, grouping = true)
                                ),
                                secondLabel = "盈亏",
                                secondValue = formatPrivacyAmount(
                                    privacyMasked,
                                    "$profitPrefix${MoneyAmount.formatPlain(uiState.investProfitMicros, displayDigits, grouping = true)}"
                                ),
                                firstValueColor = MaterialTheme.colorScheme.onSurface,
                                secondValueColor = if (uiState.investProfitMicros < 0L) semantic.expense else semantic.income,
                                onClick = {
                                    val investAccounts = visibleAccounts.filter { it.groupType == AccountGroup.INVEST }
                                    if (investAccounts.isNotEmpty()) {
                                        collapsedSections = collapsedSections - "invest_accounts"
                                        scope.launch {
                                            listState.animateScrollToItem(2)
                                        }
                                    } else {
                                        createInitialGroup = AccountGroup.INVEST
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // 3. 简洁账户大卡片（资金账户 / 信用账户 / 理财账户 / 借贷往来）
            if (visibleAccounts.isEmpty()) {
                item(key = "empty_accounts", contentType = "empty_state") {
                    if (allGroupedAccounts.isNotEmpty()) {
                        EmptyStateCard(
                            icon = Icons.Rounded.VisibilityOff,
                            title = "账户已全部隐藏",
                            subtitle = "下拉列表或前往「资产隐藏」恢复显示",
                            actionLabel = "管理资产隐藏",
                            onAction = onOpenHiddenAssets,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                    } else {
                        EmptyStateCard(
                            icon = Icons.Rounded.AccountBalanceWallet,
                            title = "暂无账户",
                            actionLabel = "新建账户",
                            onAction = { createInitialGroup = AccountGroup.WALLET },
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                    }
                }
            } else {
                YimuAssetSections.forEach { section ->
                    val sectionAccounts = visibleAccounts.filter { it.groupType in section.groupCodes }
                    if (sectionAccounts.isNotEmpty()) {
                        item(key = "section_${section.id}", contentType = "account_group") {
                            val isCollapsed = section.id in collapsedSections
                            YimuAccountSectionCard(
                                sectionTitle = section.title,
                                accounts = sectionAccounts,
                                displayDigits = displayDigits,
                                convertedMap = uiState.netWorth.accountConvertedBalances,
                                privacyMasked = privacyMasked,
                                collapsed = isCollapsed,
                                onToggleCollapse = {
                                    collapsedSections = if (isCollapsed) {
                                        collapsedSections - section.id
                                    } else {
                                        collapsedSections + section.id
                                    }
                                },
                                onClickAccount = { acc ->
                                    if (hasDetailCallback) {
                                        onOpenAccountDetail(acc)
                                    } else {
                                        actionAccount = acc
                                    }
                                },
                                onLongClickAccount = { acc -> actionAccount = acc },
                                modifier = Modifier.padding(horizontal = 12.dp)
                            )
                        }
                    }
                }
            }
        }

        // 右下角柔和圆形 + 新建账户悬浮按钮（底栏上方）
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = contentPadding.calculateBottomPadding() + 14.dp)
                .size(50.dp)
                .shadow(
                    elevation = 5.dp,
                    shape = CircleShape,
                    ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                    spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                )
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .clickable { createInitialGroup = AccountGroup.WALLET },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = "新建账户",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(24.dp)
            )
        }
    }

    // 账户长按快捷菜单弹窗（含隐藏此账户）
    actionAccount?.let { acc ->
        val balanceFormatted = formatPrivacyAmount(
            privacyMasked,
            MoneyAmount.format(acc.balanceMicros, acc.currencyCode, showCurrencyCode = true)
        )
        YubraeDialog(
            onDismissRequest = { actionAccount = null },
            title = acc.name,
            subtitle = "${AccountGroup.labelZh(acc.groupType)} · ${acc.currencyCode}",
            leadingBadge = {
                CartoonAccountIcon(
                    iconKey = acc.icon,
                    accountName = acc.name,
                    colorHex = acc.colorHex,
                    size = 40.dp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { actionAccount = null },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("关闭")
                }
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (hasDetailCallback) {
                    AccountActionRow(
                        icon = Icons.Rounded.AccountBalanceWallet,
                        label = "账户详情",
                        onClick = {
                            actionAccount = null
                            onOpenAccountDetail(acc)
                        }
                    )
                }
                AccountActionRow(
                    icon = Icons.Rounded.Add,
                    label = "记一笔",
                    onClick = {
                        actionAccount = null
                        onOpenQuickRecordForAccount(acc.id)
                    }
                )
                AccountActionRow(
                    icon = Icons.Rounded.Tune,
                    label = "余额校准 · $balanceFormatted",
                    onClick = {
                        actionAccount = null
                        calibrateTarget = acc
                    }
                )
                AccountActionRow(
                    icon = Icons.Rounded.VisibilityOff,
                    label = "隐藏此账户（下拉资产页可管理）",
                    onClick = {
                        actionAccount = null
                        viewModel.toggleAccountHidden(acc.id, acc.name)
                    }
                )
                AccountActionRow(
                    icon = Icons.Rounded.Edit,
                    label = "编辑账户",
                    onClick = {
                        actionAccount = null
                        editingAccount = acc
                    }
                )
                AccountActionRow(
                    icon = if (acc.archived) Icons.Rounded.Unarchive else Icons.Rounded.Archive,
                    label = if (acc.archived) "取消归档" else "归档",
                    onClick = {
                        actionAccount = null
                        viewModel.archiveAccount(acc.id, !acc.archived)
                    }
                )
            }
        }
    }

    // 新建或编辑账户对话框
    if (createInitialGroup != null || editingAccount != null) {
        AccountEditorDialog(
            existing = editingAccount,
            pinnedCurrencies = settings.pinnedCurrencies,
            onSave = { account ->
                viewModel.saveAccount(account) {
                    createInitialGroup = null
                    editingAccount = null
                }
            },
            onDismiss = {
                createInitialGroup = null
                editingAccount = null
            },
            initialGroupType = createInitialGroup ?: AccountGroup.WALLET
        )
    }

    // 余额校准对话框
    calibrateTarget?.let { acc ->
        CalibrateBalanceDialog(
            account = acc,
            privacyMasked = privacyMasked,
            onConfirm = { targetMicros, note ->
                viewModel.calibrateAccountBalance(acc.id, targetMicros, note) {
                    calibrateTarget = null
                }
            },
            onDismiss = { calibrateTarget = null }
        )
    }

    if (showMoreDisplayCurrencies) {
        CurrencyPickerDialog(
            selectedCode = uiState.displayCurrency,
            pinnedCodes = settings.pinnedCurrencies,
            onSelect = { viewModel.setAssetsDisplayCurrency(it) },
            onDismiss = { showMoreDisplayCurrencies = false }
        )
    }
}

@Composable
private fun YimuNetWorthCard(
    displayCurrency: String,
    displayDigits: Int,
    totalAssetsMicros: Long,
    totalLiabilitiesMicros: Long,
    netWorthMicros: Long,
    privacyMasked: Boolean,
    pinnedCurrencies: List<String>,
    showArchived: Boolean,
    showCurrencyMenu: Boolean,
    showMoreMenu: Boolean,
    onToggleCurrencyMenu: (Boolean) -> Unit,
    onToggleMoreMenu: (Boolean) -> Unit,
    onSelectCurrency: (String) -> Unit,
    onOpenMoreCurrencies: () -> Unit,
    onTogglePrivacy: () -> Unit,
    onOpenHiddenAssets: () -> Unit,
    onOpenQuickTransfer: () -> Unit,
    onOpenExchangeRates: () -> Unit,
    onToggleShowArchived: () -> Unit,
    onCreateAccount: () -> Unit
) {
    val semantic = KairoTheme.semantic
    val titleColor = semantic.heroOnSurface
    val subtitleColor = titleColor.copy(alpha = 0.68f)
    val unitSuffix = if (displayCurrency == "CNY") "元" else displayCurrency
    val netWorthText = if (displayCurrency == "CNY") {
        MoneyAmount.formatPlain(netWorthMicros, displayDigits, grouping = true)
    } else {
        MoneyAmount.format(netWorthMicros, displayCurrency, showCurrencyCode = false)
    }
    val assetsText = if (displayCurrency == "CNY") {
        MoneyAmount.formatPlain(totalAssetsMicros, displayDigits, grouping = true)
    } else {
        MoneyAmount.format(totalAssetsMicros, displayCurrency, showCurrencyCode = false)
    }
    val liabilitiesText = if (displayCurrency == "CNY") {
        MoneyAmount.formatPlain(totalLiabilitiesMicros, displayDigits, grouping = true)
    } else {
        MoneyAmount.format(totalLiabilitiesMicros, displayCurrency, showCurrencyCode = false)
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.Transparent,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(colors = semantic.heroGradient))
                .padding(start = 16.dp, end = 6.dp, top = 14.dp, bottom = 14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 左侧：净资产(元) + 隐私小眼睛 + 大号净资产金额
                Column(
                    modifier = Modifier.weight(1.1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box {
                            Row(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable { onToggleCurrencyMenu(true) }
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(1.dp)
                            ) {
                                Text(
                                    text = "净资产($unitSuffix)",
                                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
                                    color = subtitleColor,
                                    fontWeight = FontWeight.Medium
                                )
                                Icon(
                                    imageVector = Icons.Rounded.ArrowDropDown,
                                    contentDescription = "切换展示币种",
                                    tint = subtitleColor,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            DropdownMenu(
                                expanded = showCurrencyMenu,
                                onDismissRequest = { onToggleCurrencyMenu(false) }
                            ) {
                                pinnedCurrencies.forEach { code ->
                                    val itemInfo = CurrencyCatalog.find(code)
                                    DropdownMenuItem(
                                        text = { Text("${itemInfo.flag} ${itemInfo.code} · ${itemInfo.nameZh}") },
                                        onClick = { onSelectCurrency(itemInfo.code) }
                                    )
                                }
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("更多币种") },
                                    onClick = onOpenMoreCurrencies
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .clickable(onClick = onTogglePrivacy),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (privacyMasked) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = if (privacyMasked) "显示金额" else "隐藏金额",
                                tint = subtitleColor,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Text(
                        text = formatPrivacyAmount(privacyMasked, netWorthText),
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontSize = 25.sp,
                            lineHeight = 30.sp,
                            letterSpacing = (-0.5).sp
                        ).tabular(),
                        fontWeight = FontWeight.Bold,
                        color = titleColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // 右侧：总资产 · 负资产 双行对称 + 右上 ⋮ 更多菜单
                Row(
                    modifier = Modifier.weight(0.95f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "总资产",
                                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                                color = subtitleColor
                            )
                            Text(
                                text = formatPrivacyAmount(privacyMasked, assetsText),
                                style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.5.sp).tabular(),
                                fontWeight = FontWeight.SemiBold,
                                color = titleColor,
                                maxLines = 1
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "负资产",
                                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                                color = subtitleColor
                            )
                            Text(
                                text = formatPrivacyAmount(privacyMasked, liabilitiesText),
                                style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.5.sp).tabular(),
                                fontWeight = FontWeight.SemiBold,
                                color = titleColor,
                                maxLines = 1
                            )
                        }
                    }

                    Box(modifier = Modifier.align(Alignment.Top)) {
                        IconButton(
                            onClick = { onToggleMoreMenu(true) },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MoreVert,
                                contentDescription = "更多资产功能",
                                tint = subtitleColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = showMoreMenu,
                            onDismissRequest = { onToggleMoreMenu(false) }
                        ) {
                            DropdownMenuItem(
                                text = { Text("资产隐藏") },
                                onClick = onOpenHiddenAssets
                            )
                            DropdownMenuItem(
                                text = { Text("账户转账") },
                                onClick = onOpenQuickTransfer
                            )
                            DropdownMenuItem(
                                text = { Text("新建账户") },
                                onClick = onCreateAccount
                            )
                            DropdownMenuItem(
                                text = { Text("汇率中心") },
                                onClick = onOpenExchangeRates
                            )
                            DropdownMenuItem(
                                text = { Text(if (showArchived) "隐藏已归档账户" else "显示已归档账户") },
                                onClick = onToggleShowArchived
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun YimuSummaryModuleCard(
    title: String,
    badgeCount: Int,
    firstLabel: String,
    firstValue: String,
    secondLabel: String,
    secondValue: String,
    firstValueColor: Color,
    secondValueColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    YimuCard(
        cornerRadius = 14.dp,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        onClick = onClick,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (badgeCount > 0) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Text(
                            text = if (badgeCount > 99) "99+" else badgeCount.toString(),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, lineHeight = 11.sp),
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 4.5.dp, vertical = 1.dp)
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = firstLabel,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = firstValue,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp).tabular(),
                        fontWeight = FontWeight.Medium,
                        color = firstValueColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = secondLabel,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = secondValue,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp).tabular(),
                        fontWeight = FontWeight.Medium,
                        color = secondValueColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun YimuAccountSectionCard(
    sectionTitle: String,
    accounts: List<AccountEntity>,
    displayDigits: Int,
    convertedMap: Map<Long, Long>,
    privacyMasked: Boolean,
    collapsed: Boolean,
    onToggleCollapse: () -> Unit,
    onClickAccount: (AccountEntity) -> Unit,
    onLongClickAccount: (AccountEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val subtotalMicros = remember(accounts, convertedMap) {
        accounts.sumOf { convertedMap[it.id] ?: it.balanceMicros }
    }

    YimuCard(cornerRadius = 16.dp, modifier = modifier) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleCollapse)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = sectionTitle,
                    style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = formatPrivacyAmount(
                            privacyMasked,
                            MoneyAmount.formatPlain(subtotalMicros, displayDigits, grouping = true)
                        ),
                        style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp).tabular(),
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Icon(
                        imageVector = if (collapsed) Icons.Rounded.ExpandMore else Icons.Rounded.ExpandLess,
                        contentDescription = if (collapsed) "展开分组" else "收起分组",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            AnimatedVisibility(visible = !collapsed) {
                Column {
                    accounts.forEachIndexed { idx, acc ->
                        CompactHairlineDivider(
                            modifier = Modifier.padding(end = 16.dp),
                            startIndent = if (idx == 0) 16.dp else 56.dp
                        )
                        YimuAccountRowItem(
                            account = acc,
                            privacyMasked = privacyMasked,
                            onClick = { onClickAccount(acc) },
                            onLongClick = { onLongClickAccount(acc) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun YimuAccountRowItem(
    account: AccountEntity,
    privacyMasked: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val semantic = KairoTheme.semantic
    val formattedAmount = formatPrivacyAmount(
        privacyMasked,
        MoneyAmount.format(
            amountMicros = account.balanceMicros,
            currencyCode = account.currencyCode,
            showCurrencyCode = false
        )
    )
    val amountColor = if (account.balanceMicros < 0L) semantic.expense else semantic.income

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CartoonAccountIcon(
            iconKey = account.icon,
            accountName = account.name,
            colorHex = account.colorHex,
            size = 28.dp
        )
        Spacer(modifier = Modifier.width(12.dp))
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = account.name,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.5.sp),
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (account.archived) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Text(
                        text = "已归档",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = formattedAmount,
            style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.5.sp).tabular(),
            fontWeight = FontWeight.SemiBold,
            color = amountColor,
            maxLines = 1,
            softWrap = false
        )
    }
}

internal fun daysUntilDayOfMonth(dayOfMonth: Int, today: LocalDate = LocalDate.now()): Int {
    val safeDayThisMonth = dayOfMonth.coerceIn(1, today.lengthOfMonth())
    val candidateThisMonth = today.withDayOfMonth(safeDayThisMonth)
    val nextOccurrence = if (!candidateThisMonth.isBefore(today)) {
        candidateThisMonth
    } else {
        val nextMonth = today.plusMonths(1)
        nextMonth.withDayOfMonth(dayOfMonth.coerceIn(1, nextMonth.lengthOfMonth()))
    }
    return ChronoUnit.DAYS.between(today, nextOccurrence).toInt().coerceAtLeast(0)
}

internal fun formatCreditCountdown(
    billingDay: Int?,
    repaymentDay: Int?,
    today: LocalDate = LocalDate.now()
): String {
    val parts = mutableListOf<String>()
    repaymentDay?.let { day ->
        val days = daysUntilDayOfMonth(day, today)
        parts += if (days == 0) "今日还款" else "${days}天后还款"
    }
    billingDay?.let { day ->
        val days = daysUntilDayOfMonth(day, today)
        parts += if (days == 0) "今日出账" else "${days}天后出账"
    }
    return parts.joinToString(" · ")
}

@Composable
private fun AccountActionRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Text(label, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AccountEditorDialog(
    existing: AccountEntity?,
    pinnedCurrencies: List<String>,
    onSave: (AccountEntity) -> Unit,
    onDismiss: () -> Unit,
    initialGroupType: String = AccountGroup.WALLET
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var groupType by remember { mutableStateOf(existing?.groupType ?: initialGroupType) }
    var currencyCode by remember { mutableStateOf(existing?.currencyCode ?: CurrencyCatalog.DEFAULT_BASE_CURRENCY) }
    var balanceInput by remember {
        mutableStateOf(
            if (existing != null) {
                MoneyAmount.formatPlain(existing.balanceMicros, CurrencyCatalog.find(existing.currencyCode).minorDigits, false)
            } else {
                "0.00"
            }
        )
    }
    var creditLimitInput by remember {
        mutableStateOf(
            if (existing != null && existing.creditLimitMicros > 0L) {
                MoneyAmount.formatPlain(existing.creditLimitMicros, CurrencyCatalog.find(existing.currencyCode).minorDigits, false)
            } else {
                "50000.00"
            }
        )
    }
    var billingDayInput by remember { mutableStateOf(existing?.billingDay?.toString() ?: "5") }
    var repaymentDayInput by remember { mutableStateOf(existing?.repaymentDay?.toString() ?: "23") }
    var iconKey by remember {
        mutableStateOf(existing?.icon?.takeUnless { it == "wallet" } ?: "account_balance_wallet")
    }
    var colorHex by remember {
        mutableLongStateOf(existing?.colorHex ?: KairoIcons.selectableColorPalette.first())
    }
    var includeInNetWorth by remember { mutableStateOf(existing?.includeInNetWorth ?: true) }
    val sortOrder by remember { mutableIntStateOf(existing?.sortOrder ?: 100) }
    var showMoreCurrencies by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    YubraeDialog(
        onDismissRequest = onDismiss,
        title = if (existing == null) "新建账户" else "编辑账户",
        icon = Icons.Rounded.AccountBalanceWallet,
        confirmButton = {
            Button(
                onClick = {
                    val trimmedName = name.trim()
                    if (trimmedName.isEmpty()) {
                        errorMsg = "请输入账户名称"
                        return@Button
                    }
                    val balanceMicros = if (existing == null) {
                        MoneyAmount.parseToMicros(balanceInput) ?: 0L
                    } else {
                        existing.balanceMicros
                    }
                    val creditLimit = if (groupType == AccountGroup.CREDIT) {
                        MoneyAmount.parseToMicros(creditLimitInput) ?: 0L
                    } else {
                        0L
                    }
                    val bDay = if (groupType == AccountGroup.CREDIT) billingDayInput.toIntOrNull()?.coerceIn(1, 31) else null
                    val rDay = if (groupType == AccountGroup.CREDIT) repaymentDayInput.toIntOrNull()?.coerceIn(1, 31) else null

                    onSave(
                        AccountEntity(
                            id = existing?.id ?: 0L,
                            name = trimmedName,
                            groupType = groupType,
                            currencyCode = currencyCode,
                            balanceMicros = balanceMicros,
                            creditLimitMicros = creditLimit,
                            billingDay = bDay,
                            repaymentDay = rDay,
                            icon = iconKey,
                            colorHex = colorHex,
                            includeInNetWorth = includeInNetWorth,
                            sortOrder = existing?.sortOrder ?: sortOrder,
                            archived = existing?.archived ?: false,
                            createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                },
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp)
            ) { Text("取消") }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("账户名称") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Text("分组", style = MaterialTheme.typography.labelMedium)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AccountGroup.all.forEach { g ->
                    FilterChip(
                        selected = groupType == g,
                        onClick = { groupType = g },
                        label = { Text(AccountGroup.labelZh(g)) }
                    )
                }
            }

            Text("币种", style = MaterialTheme.typography.labelMedium)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val quickCodes = if (pinnedCurrencies.contains(currencyCode)) pinnedCurrencies else pinnedCurrencies + currencyCode
                items(quickCodes, key = { it }) { code ->
                    CurrencyBadge(
                        currencyCode = code,
                        selected = code.equals(currencyCode, true),
                        onClick = { currencyCode = code }
                    )
                }
                item(key = "more") {
                    FilterChip(
                        selected = false,
                        onClick = { showMoreCurrencies = true },
                        label = { Text("更多") }
                    )
                }
            }

            if (existing == null) {
                OutlinedTextField(
                    value = balanceInput,
                    onValueChange = { balanceInput = it },
                    label = { Text("期初余额 ($currencyCode，负债填负数)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (groupType == AccountGroup.CREDIT) {
                OutlinedTextField(
                    value = creditLimitInput,
                    onValueChange = { creditLimitInput = it },
                    label = { Text("信用额度 ($currencyCode)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = billingDayInput,
                        onValueChange = { billingDayInput = it },
                        label = { Text("账单日") },
                        placeholder = { Text("1-31") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = repaymentDayInput,
                        onValueChange = { repaymentDayInput = it },
                        label = { Text("还款日") },
                        placeholder = { Text("1-31") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Text("图标", style = MaterialTheme.typography.labelMedium)
            GroupedIconPicker(
                groups = KairoIcons.accountIconGroups,
                selectedKey = iconKey,
                onSelect = { iconKey = it },
                accentColor = Color(colorHex),
                gridMaxHeight = 220.dp,
                useCartoonAccountIcons = true
            )

            Text("颜色", style = MaterialTheme.typography.labelMedium)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                KairoIcons.selectableColorPalette.forEach { hex ->
                    val selected = colorHex == hex
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(Color(hex))
                            .then(
                                if (selected) {
                                    Modifier.border(
                                        BorderStroke(2.5.dp, MaterialTheme.colorScheme.onSurface),
                                        CircleShape
                                    )
                                } else {
                                    Modifier
                                }
                            )
                            .clickable { colorHex = hex }
                    ) {
                        if (selected) {
                            Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("计入净资产", style = MaterialTheme.typography.bodyMedium)
                Switch(checked = includeInNetWorth, onCheckedChange = { includeInNetWorth = it })
            }

            errorMsg?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (showMoreCurrencies) {
        CurrencyPickerDialog(
            selectedCode = currencyCode,
            pinnedCodes = pinnedCurrencies,
            onSelect = { currencyCode = it },
            onDismiss = { showMoreCurrencies = false }
        )
    }
}

@Composable
internal fun CalibrateBalanceDialog(
    account: AccountEntity,
    privacyMasked: Boolean = LocalPrivacyMasked.current,
    onConfirm: (Long, String) -> Unit,
    onDismiss: () -> Unit
) {
    val digits = CurrencyCatalog.find(account.currencyCode).minorDigits
    var targetInput by remember {
        mutableStateOf(
            if (privacyMasked) "" else MoneyAmount.formatPlain(account.balanceMicros, digits, grouping = false)
        )
    }
    var noteInput by remember { mutableStateOf("余额校准") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    YubraeDialog(
        onDismissRequest = onDismiss,
        title = "余额校准",
        subtitle = "${account.name} · ${account.currencyCode}",
        icon = Icons.Rounded.Tune,
        confirmButton = {
            Button(
                onClick = {
                    val targetMicros = MoneyAmount.parseToMicros(targetInput)
                    if (targetMicros == null) {
                        errorMsg = "请输入有效数字金额"
                    } else {
                        onConfirm(targetMicros, noteInput.trim().ifEmpty { "余额校准" })
                    }
                },
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("确认")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp)
            ) { Text("取消") }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "当前余额：${formatPrivacyAmount(privacyMasked, MoneyAmount.format(account.balanceMicros, account.currencyCode, showCurrencyCode = true))}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = targetInput,
                onValueChange = { targetInput = it },
                label = { Text("实际余额 (${account.currencyCode})") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = noteInput,
                onValueChange = { noteInput = it },
                label = { Text("备注") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )
            errorMsg?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

