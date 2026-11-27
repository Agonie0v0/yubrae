package com.yubrae.app.ui.screens.assets

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material.icons.rounded.VerticalAlignTop
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yubrae.app.core.model.CurrencyCatalog
import com.yubrae.app.core.model.MoneyAmount
import com.yubrae.app.data.db.AccountEntity
import com.yubrae.app.data.db.AccountGroup
import com.yubrae.app.data.prefs.AssetHiddenModule
import com.yubrae.app.ui.components.CartoonAccountIcon
import com.yubrae.app.ui.components.CompactHairlineDivider
import com.yubrae.app.ui.components.CurrencyPickerDialog
import com.yubrae.app.ui.components.EmptyStateCard
import com.yubrae.app.ui.components.GroupedIconPicker
import com.yubrae.app.ui.components.KairoIcons
import com.yubrae.app.ui.components.LocalPrivacyMasked
import com.yubrae.app.ui.components.QuickCurrencySelectorRow
import com.yubrae.app.ui.components.TopStatusBarScrim
import com.yubrae.app.ui.components.YimuCard
import com.yubrae.app.ui.components.YubraeDialog
import com.yubrae.app.ui.components.formatPrivacyAmount
import com.yubrae.app.ui.theme.KairoTheme
import com.yubrae.app.ui.theme.tabular
import com.yubrae.app.ui.viewmodel.KairoMainViewModel
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlinx.coroutines.launch

private val DefaultOpenAccountDetail: (AccountEntity) -> Unit = {}

private data class AssetCompositionBucket(
    val id: String,
    val label: String,
    val amountMicros: Long,
    val ratio: Float,
    val color: Color
)

@Suppress("UNUSED_PARAMETER")
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
    onOpenHiddenAssets: () -> Unit = {},
    onOpenCreditRepay: ((AccountEntity) -> Unit)? = null
) {
    val uiState by viewModel.assetsUiState.collectAsStateWithLifecycle()
    val allAccounts by viewModel.allAccounts.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val privacyMasked = LocalPrivacyMasked.current
    val hasDetailCallback = onOpenAccountDetail !== DefaultOpenAccountDetail
    val semantic = KairoTheme.semantic
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    var editingAccount by remember { mutableStateOf<AccountEntity?>(null) }
    var createInitialGroup by remember { mutableStateOf<String?>(null) }
    var deleteTarget by remember { mutableStateOf<AccountEntity?>(null) }
    var showDisplayCurrencyMenu by remember { mutableStateOf(false) }
    var showNetWorthMoreMenu by remember { mutableStateOf(false) }
    var showMoreDisplayCurrencies by remember { mutableStateOf(false) }
    var showReorderDialog by remember { mutableStateOf(false) }
    var searchExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var archivedSectionExpanded by remember { mutableStateOf(false) }

    val searchFocusRequester = remember { FocusRequester() }
    LaunchedEffect(searchExpanded) {
        if (searchExpanded) {
            runCatching { searchFocusRequester.requestFocus() }
        }
    }

    // 主列表分组与账户长按直接拖拽状态
    val orderedSections = remember(settings.assetGroupOrder) {
        resolveOrderedAssetSections(settings.assetGroupOrder)
    }
    val sectionById = remember { YimuAssetSections.associateBy { it.id } }
    var draggingSectionId by remember { mutableStateOf<String?>(null) }
    var sectionDragOffsetY by remember { mutableFloatStateOf(0f) }
    var sectionDragStartOrder by remember { mutableStateOf<List<String>>(emptyList()) }
    val localSectionIds = remember { orderedSections.map { it.id }.toMutableStateList() }
    val sectionHeightsPx = remember { mutableStateMapOf<String, Int>() }
    var isDraggingAnyAccount by remember { mutableStateOf(false) }
    val sectionSpacingPx = remember(density) { with(density) { 10.dp.toPx() } }
    val defaultSectionHeightPx = remember(density) { with(density) { 140.dp.toPx() } }
    val sectionDragElevationPx = remember(density) { with(density) { 12.dp.toPx() } }
    val sectionCardShape = remember { RoundedCornerShape(16.dp) }

    LaunchedEffect(orderedSections) {
        if (draggingSectionId == null) {
            val targetIds = orderedSections.map { it.id }
            if (localSectionIds.toList() != targetIds) {
                localSectionIds.clear()
                localSectionIds.addAll(targetIds)
            }
        }
    }

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
                if (draggingSectionId != null || isDraggingAnyAccount) return Offset.Zero
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
                if (draggingSectionId != null || isDraggingAnyAccount) return Offset.Zero
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
                if (draggingSectionId != null || isDraggingAnyAccount) return Velocity.Zero
                if (pullOffsetPx > 0f) {
                    settlePullGesture()
                }
                return Velocity.Zero
            }
        }
    }

    val collapsedSections = settings.collapsedAssetGroups

    val activeAccountsSorted = remember(allAccounts) {
        allAccounts
            .filter { !it.archived }
            .sortedWith(compareBy({ it.sortOrder }, { it.id }))
    }
    val archivedAccountsSorted = remember(allAccounts) {
        allAccounts
            .filter { it.archived }
            .sortedWith(compareBy({ it.sortOrder }, { it.id }))
    }
    val visibleActiveAccounts = remember(activeAccountsSorted, uiState.hiddenAccountIds) {
        activeAccountsSorted.filter { it.id !in uiState.hiddenAccountIds }
    }
    val normalizedQuery = searchQuery.trim().lowercase(Locale.ROOT)
    val isSearchFiltering = normalizedQuery.isNotEmpty()

    val filteredActiveAccounts = remember(visibleActiveAccounts, normalizedQuery) {
        if (normalizedQuery.isEmpty()) {
            visibleActiveAccounts
        } else {
            visibleActiveAccounts.filter { matchesAccountSearchQuery(it, normalizedQuery) }
        }
    }
    val filteredArchivedAccounts = remember(archivedAccountsSorted, normalizedQuery) {
        if (normalizedQuery.isEmpty()) {
            archivedAccountsSorted
        } else {
            archivedAccountsSorted.filter { matchesAccountSearchQuery(it, normalizedQuery) }
        }
    }
    val totalSearchMatchCount = filteredActiveAccounts.size + filteredArchivedAccounts.size

    val displayDigits = remember(uiState.displayCurrency) {
        CurrencyCatalog.find(uiState.displayCurrency).minorDigits
    }

    // 按折算后正资产计算资产构成进度条分桶（流动资金 / 理财投资 / 外币资产 / 借贷应收）
    val compositionBuckets = remember(
        activeAccountsSorted,
        uiState.netWorth.accountConvertedBalances,
        uiState.debtReceivableIncludedMicros,
        semantic.isDark
    ) {
        var liquidMicros = 0L
        var investMicros = 0L
        var foreignMicros = 0L
        var receivableMicros = uiState.debtReceivableIncludedMicros.coerceAtLeast(0L)
        activeAccountsSorted.forEach { acc ->
            if (!acc.includeInNetWorth) return@forEach
            val converted = uiState.netWorth.accountConvertedBalances[acc.id] ?: acc.balanceMicros
            if (converted <= 0L) return@forEach
            when (acc.groupType) {
                AccountGroup.WALLET, AccountGroup.DEBIT -> liquidMicros += converted
                AccountGroup.INVEST -> investMicros += converted
                AccountGroup.FOREIGN -> foreignMicros += converted
                AccountGroup.DEBT -> receivableMicros += converted
                else -> liquidMicros += converted
            }
        }
        val totalPositive = liquidMicros + investMicros + foreignMicros + receivableMicros
        if (totalPositive <= 0L) {
            emptyList()
        } else {
            val raw = listOf(
                Triple("liquid", "流动资金", liquidMicros) to if (semantic.isDark) Color(0xFF5BD2A0) else Color(0xFF2AA876),
                Triple("invest", "理财投资", investMicros) to if (semantic.isDark) Color(0xFF7CB2FF) else Color(0xFF3B82F6),
                Triple("foreign", "外币资产", foreignMicros) to if (semantic.isDark) Color(0xFFB794F4) else Color(0xFF7C3AED),
                Triple("receivable", "借贷应收", receivableMicros) to if (semantic.isDark) Color(0xFFFBBF24) else Color(0xFFE89B2D)
            ).filter { it.first.third > 0L }
            raw.map { (triple, color) ->
                AssetCompositionBucket(
                    id = triple.first,
                    label = triple.second,
                    amountMicros = triple.third,
                    ratio = (triple.third.toDouble() / totalPositive.toDouble()).toFloat().coerceIn(0f, 1f),
                    color = color
                )
            }
        }
    }

    val showReimburseCard = AssetHiddenModule.REIMBURSE !in uiState.hiddenModules
    val showDebtCard = AssetHiddenModule.DEBT !in uiState.hiddenModules
    val showInvestCard = AssetHiddenModule.INVEST !in uiState.hiddenModules
    val hasAnySummaryCard = showReimburseCard || showDebtCard || showInvestCard
    val showStatusBarScrim by remember(listState) {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 40
        }
    }

    val currentSectionOrderSnapshot = localSectionIds.toList()
    val displayedSections = remember(currentSectionOrderSnapshot, filteredActiveAccounts) {
        currentSectionOrderSnapshot.mapNotNull { id ->
            val section = sectionById[id] ?: return@mapNotNull null
            val list = filteredActiveAccounts.filter { it.groupType in section.groupCodes }
            if (list.isNotEmpty()) section to list else null
        }
    }
    val nonEmptySectionIds = remember(displayedSections) {
        displayedSections.map { it.first.id }.toSet()
    }
    val latestNonEmptySectionIds by rememberUpdatedState(nonEmptySectionIds)
    val canDragSections = !isSearchFiltering && displayedSections.size > 1

    fun buildReorderedAccountIds(
        changedSectionId: String,
        newVisibleIdsInSection: List<Long>
    ): List<Long> {
        val orderedSpecs = resolveOrderedAssetSections(localSectionIds)
        val result = ArrayList<Long>(activeAccountsSorted.size + archivedAccountsSorted.size)
        val seen = HashSet<Long>()
        orderedSpecs.forEach { spec ->
            if (spec.id == changedSectionId) {
                newVisibleIdsInSection.forEach { id ->
                    if (seen.add(id)) result.add(id)
                }
                activeAccountsSorted.forEach { acc ->
                    if (acc.groupType in spec.groupCodes && seen.add(acc.id)) {
                        result.add(acc.id)
                    }
                }
            } else {
                activeAccountsSorted.forEach { acc ->
                    if (acc.groupType in spec.groupCodes && seen.add(acc.id)) {
                        result.add(acc.id)
                    }
                }
            }
        }
        activeAccountsSorted.forEach { acc ->
            if (seen.add(acc.id)) result.add(acc.id)
        }
        archivedAccountsSorted.forEach { acc ->
            if (seen.add(acc.id)) result.add(acc.id)
        }
        return result
    }

    fun startSectionDrag(sectionId: String) {
        if (isDraggingAnyAccount) return
        draggingSectionId = sectionId
        sectionDragOffsetY = 0f
        sectionDragStartOrder = localSectionIds.toList()
        pullOffsetPx = 0f
        thresholdHapticFired = false
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    fun dragSectionBy(sectionId: String, deltaY: Float) {
        if (draggingSectionId != sectionId) return
        sectionDragOffsetY += deltaY
        while (sectionDragOffsetY > 0f) {
            val visibleIds = localSectionIds.filter { it in latestNonEmptySectionIds }
            val curVisIdx = visibleIds.indexOf(sectionId)
            if (curVisIdx < 0 || curVisIdx >= visibleIds.lastIndex) break
            val nextId = visibleIds[curVisIdx + 1]
            val stepPx = (sectionHeightsPx[nextId]?.toFloat() ?: defaultSectionHeightPx) + sectionSpacingPx
            if (sectionDragOffsetY >= stepPx * 0.55f) {
                val fromAll = localSectionIds.indexOf(sectionId)
                val toAll = localSectionIds.indexOf(nextId)
                if (fromAll >= 0 && toAll >= 0 && fromAll != toAll) {
                    val isFirstVisible = listState.layoutInfo.visibleItemsInfo.firstOrNull()?.key == "section_$sectionId"
                    val anchorIdx = listState.firstVisibleItemIndex
                    val anchorOff = listState.firstVisibleItemScrollOffset
                    val moved = localSectionIds.removeAt(fromAll)
                    localSectionIds.add(toAll, moved)
                    if (isFirstVisible) {
                        listState.requestScrollToItem(anchorIdx, anchorOff)
                    }
                    sectionDragOffsetY -= stepPx
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                } else {
                    break
                }
            } else {
                break
            }
        }
        while (sectionDragOffsetY < 0f) {
            val visibleIds = localSectionIds.filter { it in latestNonEmptySectionIds }
            val curVisIdx = visibleIds.indexOf(sectionId)
            if (curVisIdx <= 0) break
            val prevId = visibleIds[curVisIdx - 1]
            val stepPx = (sectionHeightsPx[prevId]?.toFloat() ?: defaultSectionHeightPx) + sectionSpacingPx
            if (sectionDragOffsetY <= -stepPx * 0.55f) {
                val fromAll = localSectionIds.indexOf(sectionId)
                val toAll = localSectionIds.indexOf(prevId)
                if (fromAll >= 0 && toAll >= 0 && fromAll != toAll) {
                    val isFirstVisible = listState.layoutInfo.visibleItemsInfo.firstOrNull()?.key == "section_$sectionId"
                    val anchorIdx = listState.firstVisibleItemIndex
                    val anchorOff = listState.firstVisibleItemScrollOffset
                    val moved = localSectionIds.removeAt(fromAll)
                    localSectionIds.add(toAll, moved)
                    if (isFirstVisible) {
                        listState.requestScrollToItem(anchorIdx, anchorOff)
                    }
                    sectionDragOffsetY += stepPx
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                } else {
                    break
                }
            } else {
                break
            }
        }
    }

    fun finishSectionDrag(sectionId: String) {
        if (draggingSectionId != sectionId) return
        val finalOrder = localSectionIds.toList()
        val changed = sectionDragStartOrder.isNotEmpty() && finalOrder != sectionDragStartOrder
        draggingSectionId = null
        sectionDragOffsetY = 0f
        sectionDragStartOrder = emptyList()
        if (changed) {
            viewModel.updateAssetGroupOrder(finalOrder)
        } else {
            val latestIds = orderedSections.map { it.id }
            if (localSectionIds.toList() != latestIds) {
                localSectionIds.clear()
                localSectionIds.addAll(latestIds)
            }
        }
    }

    val isDraggingAny = draggingSectionId != null || isDraggingAnyAccount

    Box(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(pullToHiddenConnection)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Final)
                        if (draggingSectionId == null && !isDraggingAnyAccount &&
                            pullOffsetPx > 0f && event.changes.none { it.pressed }
                        ) {
                            settlePullGesture()
                        }
                    }
                }
            }
    ) {
        LazyColumn(
            state = listState,
            userScrollEnabled = !isDraggingAny,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                bottom = contentPadding.calculateBottomPadding() + 84.dp
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 1. 顶部净资产圆角大卡（净资产 + 总/负资产 + 资产构成进度条 + 3 枚快捷操作按钮）
            item(key = "net_worth_card", contentType = "net_worth_hero") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                ) {
                    if (pullOffsetPx > 0.5f) {
                        val ready = pullOffsetPx >= triggerThresholdPx
                        val bannerHeightDp = with(density) { pullOffsetPx.toDp() }
                        val indicatorAlpha = ((pullOffsetPx / triggerThresholdPx) * 1.35f).coerceIn(0f, 1f)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(bannerHeightDp)
                                .clipToBounds(),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = if (ready) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                },
                                modifier = Modifier.alpha(indicatorAlpha)
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
                                        color = if (ready) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, end = 12.dp, top = 8.dp)
                    ) {
                        YimuNetWorthCard(
                            displayCurrency = uiState.displayCurrency,
                            displayDigits = displayDigits,
                            totalAssetsMicros = uiState.netWorth.totalAssetsMicros,
                            totalLiabilitiesMicros = uiState.netWorth.totalLiabilitiesMicros,
                            netWorthMicros = uiState.netWorth.netWorthMicros,
                            compositionBuckets = compositionBuckets,
                            privacyMasked = privacyMasked,
                            pinnedCurrencies = settings.pinnedCurrencies,
                            archivedCount = archivedAccountsSorted.size,
                            archivedExpanded = archivedSectionExpanded,
                            searchExpanded = searchExpanded,
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
                            onToggleSearch = {
                                if (searchExpanded) {
                                    searchExpanded = false
                                    searchQuery = ""
                                } else {
                                    searchExpanded = true
                                }
                            },
                            onOpenReorder = {
                                showNetWorthMoreMenu = false
                                showReorderDialog = true
                            },
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
                                if (archivedAccountsSorted.isEmpty()) {
                                    viewModel.showMessage("当前暂无已归档账户")
                                } else {
                                    archivedSectionExpanded = !archivedSectionExpanded
                                    viewModel.setShowArchivedAccounts(archivedSectionExpanded)
                                }
                            },
                            onCreateAccount = {
                                showNetWorthMoreMenu = false
                                createInitialGroup = AccountGroup.WALLET
                            }
                        )
                    }
                }
            }

            // 1.5 实时账户搜索栏（按名称 / 备注 / 币种 / 分组过滤）
            if (searchExpanded) {
                item(key = "asset_search_bar", contentType = "search_bar") {
                    AssetSearchBar(
                        query = searchQuery,
                        matchCount = totalSearchMatchCount,
                        focusRequester = searchFocusRequester,
                        onQueryChange = { searchQuery = it },
                        onClear = { searchQuery = "" },
                        onClose = {
                            searchQuery = ""
                            searchExpanded = false
                        },
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
            }

            // 2. 三等分汇总卡：报销 · 债务 · 理财（随隐藏配置动态隐藏，搜索过滤时自动收起聚焦搜索结果）
            if (hasAnySummaryCard && !isSearchFiltering) {
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
                                    val investIdx = displayedSections.indexOfFirst { it.first.id == "invest_accounts" }
                                    if (investIdx >= 0) {
                                        if ("invest_accounts" in collapsedSections) {
                                            viewModel.toggleAssetGroupCollapsed("invest_accounts")
                                        }
                                        val prefixItems = 1 + (if (searchExpanded) 1 else 0) + (if (hasAnySummaryCard) 1 else 0)
                                        scope.launch {
                                            listState.animateScrollToItem(prefixItems + investIdx)
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

            // 3. 活跃账户分组卡片（严格按 settings.assetGroupOrder 与 sortOrder 排序，持久化折叠）
            if (isSearchFiltering && totalSearchMatchCount == 0) {
                item(key = "empty_search_accounts", contentType = "empty_state") {
                    EmptyStateCard(
                        icon = Icons.Rounded.Search,
                        title = "未找到匹配的账户",
                        subtitle = "支持按账户名称、备注、币种代码或分组名称搜索",
                        actionLabel = "清空搜索",
                        onAction = { searchQuery = "" },
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
            } else if (!isSearchFiltering && visibleActiveAccounts.isEmpty()) {
                item(key = "empty_accounts", contentType = "empty_state") {
                    if (activeAccountsSorted.isNotEmpty()) {
                        EmptyStateCard(
                            icon = Icons.Rounded.VisibilityOff,
                            title = "活跃账户已全部隐藏",
                            subtitle = "下拉列表或前往「资产隐藏」恢复显示",
                            actionLabel = "管理资产隐藏",
                            onAction = onOpenHiddenAssets,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                    } else {
                        EmptyStateCard(
                            icon = Icons.Rounded.AccountBalanceWallet,
                            title = "暂无活跃账户",
                            actionLabel = "新建账户",
                            onAction = { createInitialGroup = AccountGroup.WALLET },
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                    }
                }
            } else {
                displayedSections.forEach { (section, sectionAccounts) ->
                    item(key = "section_${section.id}", contentType = "account_group") {
                        val isCollapsed = if (isSearchFiltering) false else section.id in collapsedSections
                        val isDraggingThisSection = draggingSectionId == section.id
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .zIndex(if (isDraggingThisSection) 5f else 0f)
                                .padding(horizontal = 12.dp)
                                .onSizeChanged { size ->
                                    if (size.height > 0) {
                                        sectionHeightsPx[section.id] = size.height
                                    }
                                }
                                .graphicsLayer {
                                    translationY = if (isDraggingThisSection) sectionDragOffsetY else 0f
                                    scaleX = if (isDraggingThisSection) 1.015f else 1f
                                    scaleY = if (isDraggingThisSection) 1.015f else 1f
                                    shadowElevation = if (isDraggingThisSection) sectionDragElevationPx else 0f
                                    shape = sectionCardShape
                                    clip = false
                                }
                        ) {
                            YimuAccountSectionCard(
                                section = section,
                                accounts = sectionAccounts,
                                displayCurrency = uiState.displayCurrency,
                                displayDigits = displayDigits,
                                convertedMap = uiState.netWorth.accountConvertedBalances,
                                hiddenAccountIds = uiState.hiddenAccountIds,
                                privacyMasked = privacyMasked,
                                collapsed = isCollapsed,
                                isSearchFiltering = isSearchFiltering,
                                canDragSection = canDragSections,
                                isDraggingSection = isDraggingThisSection,
                                onToggleCollapse = { viewModel.toggleAssetGroupCollapsed(section.id) },
                                onQuickAddAccount = { createInitialGroup = section.defaultGroupType },
                                onClickAccount = { acc ->
                                    if (hasDetailCallback) {
                                        onOpenAccountDetail(acc)
                                    } else {
                                        editingAccount = acc
                                    }
                                },
                                onSectionDragStart = { startSectionDrag(section.id) },
                                onSectionDragDelta = { deltaY -> dragSectionBy(section.id, deltaY) },
                                onSectionDragFinish = { finishSectionDrag(section.id) },
                                onAccountDraggingChanged = { dragging ->
                                    isDraggingAnyAccount = dragging
                                    if (dragging) {
                                        pullOffsetPx = 0f
                                        thresholdHapticFired = false
                                    }
                                },
                                onReorderSectionAccounts = { reorderedVisibleIds ->
                                    val fullOrderedIds = buildReorderedAccountIds(section.id, reorderedVisibleIds)
                                    if (fullOrderedIds.isNotEmpty()) {
                                        viewModel.reorderAccounts(fullOrderedIds)
                                    }
                                },
                                modifier = if (isDraggingThisSection) {
                                    Modifier.border(
                                        width = 1.4.dp,
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f),
                                        shape = sectionCardShape
                                    )
                                } else {
                                    Modifier
                                }
                            )
                        }
                    }
                }
            }

            // 4. 底部专属可折叠「已归档账户 (N)」区
            if (filteredArchivedAccounts.isNotEmpty()) {
                item(key = "archived_accounts_section", contentType = "archived_group") {
                    val expanded = if (isSearchFiltering) true else archivedSectionExpanded
                    ArchivedAccountsSectionCard(
                        accounts = filteredArchivedAccounts,
                        displayCurrency = uiState.displayCurrency,
                        privacyMasked = privacyMasked,
                        expanded = expanded,
                        hasDetailCallback = hasDetailCallback,
                        onToggleExpand = { archivedSectionExpanded = !archivedSectionExpanded },
                        onOpenDetail = { acc ->
                            if (hasDetailCallback) onOpenAccountDetail(acc) else editingAccount = acc
                        },
                        onRestoreAccount = { acc -> viewModel.archiveAccount(acc.id, false) },
                        onDeleteAccount = { acc -> deleteTarget = acc },
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
            }

            // 5. 列表最底部轻提示：直接长按拖动排序
            if (!isSearchFiltering && displayedSections.isNotEmpty()) {
                item(key = "asset_reorder_hint", contentType = "reorder_hint") {
                    Text(
                        text = "长按账户或分组标题可直接拖动排序",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    )
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

        TopStatusBarScrim(
            visible = showStatusBarScrim,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }

    // 资产排序管理弹窗（大跨度批量调序兜底：组内账户拖拽排序 + 分组顺序拖拽排序）
    if (showReorderDialog) {
        AssetReorderDialog(
            initialGroupOrder = settings.assetGroupOrder,
            activeAccounts = activeAccountsSorted,
            archivedAccounts = archivedAccountsSorted,
            privacyMasked = privacyMasked,
            onSaveOrder = { orderedGroupIds, orderedAccountIds ->
                viewModel.updateAssetGroupOrder(orderedGroupIds)
                if (orderedAccountIds.isNotEmpty()) {
                    viewModel.reorderAccounts(orderedAccountIds) {
                        showReorderDialog = false
                    }
                } else {
                    showReorderDialog = false
                }
            },
            onDismiss = { showReorderDialog = false }
        )
    }

    // 新建或编辑账户对话框
    if (createInitialGroup != null || editingAccount != null) {
        val currentEditing = editingAccount
        AccountEditorDialog(
            existing = currentEditing,
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

    // 删除账户确认/迁移对话框
    deleteTarget?.let { acc ->
        val linkedCount = remember(acc.id) {
            viewModel.countLinkedTransactionsForAccount(acc.id)
        }
        val targets = remember(allAccounts, acc.id) {
            allAccounts.filter { !it.archived && it.id != acc.id }
        }
        DeleteAccountConfirmDialog(
            account = acc,
            linkedTxCount = linkedCount,
            availableTargets = targets,
            onConfirmDelete = { migrateToId ->
                viewModel.deleteAccount(acc.id, migrateToId) {
                    deleteTarget = null
                }
            },
            onDismiss = { deleteTarget = null }
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

private fun matchesAccountSearchQuery(account: AccountEntity, queryLower: String): Boolean {
    if (queryLower.isEmpty()) return true
    val currencyInfo = CurrencyCatalog.find(account.currencyCode)
    val sectionTitle = findAssetSectionForGroup(account.groupType).title
    val groupLabel = AccountGroup.labelZh(account.groupType)
    return account.name.lowercase(Locale.ROOT).contains(queryLower) ||
        account.note.lowercase(Locale.ROOT).contains(queryLower) ||
        account.currencyCode.lowercase(Locale.ROOT).contains(queryLower) ||
        currencyInfo.nameZh.lowercase(Locale.ROOT).contains(queryLower) ||
        groupLabel.lowercase(Locale.ROOT).contains(queryLower) ||
        sectionTitle.lowercase(Locale.ROOT).contains(queryLower)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun YimuNetWorthCard(
    displayCurrency: String,
    displayDigits: Int,
    totalAssetsMicros: Long,
    totalLiabilitiesMicros: Long,
    netWorthMicros: Long,
    compositionBuckets: List<AssetCompositionBucket>,
    privacyMasked: Boolean,
    pinnedCurrencies: List<String>,
    archivedCount: Int,
    archivedExpanded: Boolean,
    searchExpanded: Boolean,
    showCurrencyMenu: Boolean,
    showMoreMenu: Boolean,
    onToggleCurrencyMenu: (Boolean) -> Unit,
    onToggleMoreMenu: (Boolean) -> Unit,
    onSelectCurrency: (String) -> Unit,
    onOpenMoreCurrencies: () -> Unit,
    onTogglePrivacy: () -> Unit,
    onToggleSearch: () -> Unit,
    onOpenReorder: () -> Unit,
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

    val heroGradientBrush = remember(semantic.heroGradient) {
        Brush.linearGradient(colors = semantic.heroGradient)
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color.Transparent,
        border = BorderStroke(0.5.dp, semantic.heroBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(heroGradientBrush)
                .padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 左侧：净资产(元) + 隐私小眼睛 + 大号净资产金额
                Column(
                    modifier = Modifier.weight(1.1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
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
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    softWrap = false
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
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "总资产",
                                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                                color = subtitleColor,
                                maxLines = 1,
                                softWrap = false
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = formatPrivacyAmount(privacyMasked, assetsText),
                                style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.5.sp).tabular(),
                                fontWeight = FontWeight.SemiBold,
                                color = titleColor,
                                textAlign = TextAlign.End,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
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
                                color = subtitleColor,
                                maxLines = 1,
                                softWrap = false
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = formatPrivacyAmount(privacyMasked, liabilitiesText),
                                style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.5.sp).tabular(),
                                fontWeight = FontWeight.SemiBold,
                                color = titleColor,
                                textAlign = TextAlign.End,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
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
                                text = { Text("批量排序管理") },
                                onClick = onOpenReorder
                            )
                            DropdownMenuItem(
                                text = { Text("隐藏资产管理") },
                                onClick = onOpenHiddenAssets
                            )
                            DropdownMenuItem(
                                text = { Text("汇率管理") },
                                onClick = onOpenExchangeRates
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        when {
                                            archivedCount == 0 -> "已归档账户 (0)"
                                            archivedExpanded -> "收起已归档账户 ($archivedCount)"
                                            else -> "展开已归档账户 ($archivedCount)"
                                        }
                                    )
                                },
                                onClick = onToggleShowArchived
                            )
                        }
                    }
                }
            }

            // 资产构成进度条 + 紧凑图例（仅存在正资产时展示，隐私模式脱敏）
            if (compositionBuckets.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (privacyMasked) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(CircleShape)
                                .background(titleColor.copy(alpha = 0.16f))
                        )
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(CircleShape),
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            compositionBuckets.forEach { bucket ->
                                Box(
                                    modifier = Modifier
                                        .weight(bucket.ratio.coerceAtLeast(0.03f))
                                        .height(6.dp)
                                        .clip(CircleShape)
                                        .background(bucket.color)
                                )
                            }
                        }
                    }

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        compositionBuckets.forEach { bucket ->
                            val percentText = if (privacyMasked) {
                                "••%"
                            } else {
                                val pct = (bucket.ratio * 100f).toInt()
                                if (pct == 0 && bucket.amountMicros > 0L) "<1%" else "$pct%"
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (privacyMasked) titleColor.copy(alpha = 0.35f) else bucket.color
                                        )
                                )
                                Text(
                                    text = "${bucket.label} $percentText",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp).tabular(),
                                    color = subtitleColor,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }
            }

            // 精简 3 枚高价值快捷操作胶囊：搜索 / 转账 / 新建账户
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HeroQuickActionPill(
                    icon = Icons.Rounded.Search,
                    label = "搜索",
                    active = searchExpanded,
                    contentColor = titleColor,
                    pillBg = semantic.heroPillBg,
                    onClick = onToggleSearch,
                    modifier = Modifier.weight(1f)
                )
                HeroQuickActionPill(
                    icon = Icons.Rounded.SwapHoriz,
                    label = "转账",
                    active = false,
                    contentColor = titleColor,
                    pillBg = semantic.heroPillBg,
                    onClick = onOpenQuickTransfer,
                    modifier = Modifier.weight(1f)
                )
                HeroQuickActionPill(
                    icon = Icons.Rounded.Add,
                    label = "新建账户",
                    active = false,
                    contentColor = titleColor,
                    pillBg = semantic.heroPillBg,
                    onClick = onCreateAccount,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun HeroQuickActionPill(
    icon: ImageVector,
    label: String,
    active: Boolean,
    contentColor: Color,
    pillBg: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor = if (active) {
        MaterialTheme.colorScheme.primary
    } else {
        pillBg
    }
    val fgColor = if (active) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        contentColor
    }
    Surface(
        modifier = modifier
            .height(32.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = bgColor,
        border = if (active) null else BorderStroke(0.5.dp, contentColor.copy(alpha = 0.12f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = fgColor,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.5.sp),
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                color = fgColor,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun AssetSearchBar(
    query: String,
    matchCount: Int,
    focusRequester: FocusRequester,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart
            ) {
                if (query.isEmpty()) {
                    Text(
                        text = "搜索账户名称、备注、币种或分组",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                )
            }
            if (query.isNotBlank()) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
                ) {
                    Text(
                        text = "${matchCount}项",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp).tabular(),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable(onClick = onClear),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "清空搜索",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                }
            } else {
                Text(
                    text = "收起",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(onClick = onClose)
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                )
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
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    softWrap = false
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
                            maxLines = 1,
                            softWrap = false,
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = firstValue,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp).tabular(),
                        fontWeight = FontWeight.Medium,
                        color = firstValueColor,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = secondValue,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp).tabular(),
                        fontWeight = FontWeight.Medium,
                        color = secondValueColor,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun YimuAccountSectionCard(
    section: AssetSectionSpec,
    accounts: List<AccountEntity>,
    displayCurrency: String,
    displayDigits: Int,
    convertedMap: Map<Long, Long>,
    hiddenAccountIds: Set<Long>,
    privacyMasked: Boolean,
    collapsed: Boolean,
    isSearchFiltering: Boolean,
    canDragSection: Boolean,
    isDraggingSection: Boolean,
    onToggleCollapse: () -> Unit,
    onQuickAddAccount: () -> Unit,
    onClickAccount: (AccountEntity) -> Unit,
    onSectionDragStart: () -> Unit,
    onSectionDragDelta: (Float) -> Unit,
    onSectionDragFinish: () -> Unit,
    onAccountDraggingChanged: (Boolean) -> Unit,
    onReorderSectionAccounts: (List<Long>) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val subtotalMicros = remember(accounts, convertedMap) {
        accounts.sumOf { convertedMap[it.id] ?: it.balanceMicros }
    }
    val subtotalFormatted = if (displayCurrency.equals("CNY", ignoreCase = true)) {
        MoneyAmount.formatPlain(subtotalMicros, displayDigits, grouping = true)
    } else {
        MoneyAmount.format(subtotalMicros, displayCurrency, showCurrencyCode = false)
    }
    val sectionAccent = Color(section.accentColorHex)

    // 组内账户直接长按拖拽排序本地状态
    val localAccounts = remember(section.id) { accounts.toMutableStateList() }
    var draggingAccountId by remember(section.id) { mutableStateOf<Long?>(null) }
    var dragOffsetY by remember(section.id) { mutableFloatStateOf(0f) }
    var dragStartAccountOrder by remember(section.id) { mutableStateOf<List<Long>>(emptyList()) }
    val rowHeightsPx = remember(section.id) { mutableStateMapOf<Long, Int>() }
    val defaultRowHeightPx = remember(density) { with(density) { 62.dp.toPx() } }
    val rowDragElevationPx = remember(density) { with(density) { 10.dp.toPx() } }
    val dragRowShape = remember { RoundedCornerShape(12.dp) }
    val canDragAccounts = !isSearchFiltering && accounts.size > 1

    val latestAccounts by rememberUpdatedState(accounts)
    val latestIsDraggingSection by rememberUpdatedState(isDraggingSection)
    val latestOnSectionDragStart by rememberUpdatedState(onSectionDragStart)
    val latestOnSectionDragDelta by rememberUpdatedState(onSectionDragDelta)
    val latestOnSectionDragFinish by rememberUpdatedState(onSectionDragFinish)
    val latestOnAccountDraggingChanged by rememberUpdatedState(onAccountDraggingChanged)
    val latestOnReorderSectionAccounts by rememberUpdatedState(onReorderSectionAccounts)

    LaunchedEffect(accounts) {
        if (draggingAccountId == null) {
            localAccounts.clear()
            localAccounts.addAll(accounts)
        }
    }

    DisposableEffect(section.id) {
        onDispose {
            if (draggingAccountId != null) {
                latestOnAccountDraggingChanged(false)
            }
        }
    }

    fun finishAccountDrag(accId: Long) {
        if (draggingAccountId != accId) return
        val currentIds = localAccounts.map { it.id }
        val changed = dragStartAccountOrder.isNotEmpty() && currentIds != dragStartAccountOrder
        draggingAccountId = null
        dragOffsetY = 0f
        dragStartAccountOrder = emptyList()
        latestOnAccountDraggingChanged(false)
        if (changed) {
            latestOnReorderSectionAccounts(currentIds)
        } else if (localAccounts.toList() != latestAccounts) {
            localAccounts.clear()
            localAccounts.addAll(latestAccounts)
        }
    }

    YimuCard(
        cornerRadius = 16.dp,
        containerColor = if (isDraggingSection) {
            MaterialTheme.colorScheme.surfaceContainerLow
        } else {
            MaterialTheme.colorScheme.surfaceContainerLowest
        },
        modifier = modifier
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleCollapse)
                    .then(
                        if (canDragSection) {
                            Modifier.pointerInput(section.id) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        if (draggingAccountId == null) {
                                            latestOnSectionDragStart()
                                        }
                                    },
                                    onDragEnd = { latestOnSectionDragFinish() },
                                    onDragCancel = { latestOnSectionDragFinish() },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        latestOnSectionDragDelta(dragAmount.y)
                                    }
                                )
                            }
                        } else {
                            Modifier
                        }
                    )
                    .padding(start = 14.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(sectionAccent.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = section.icon,
                            contentDescription = null,
                            tint = sectionAccent,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    Text(
                        text = section.title,
                        style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Text(
                            text = "${localAccounts.size}",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp).tabular(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = formatPrivacyAmount(privacyMasked, subtotalFormatted),
                        style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp).tabular(),
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        softWrap = false
                    )
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f))
                            .clickable(onClick = onQuickAddAccount),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = "在${section.title}新建账户",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                    }
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
                    localAccounts.forEachIndexed { idx, acc ->
                        key(acc.id) {
                            val isDragging = draggingAccountId == acc.id
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .zIndex(if (isDragging) 2f else 0f)
                                    .onSizeChanged { size ->
                                        if (size.height > 0) {
                                            rowHeightsPx[acc.id] = size.height
                                        }
                                    }
                                    .graphicsLayer {
                                        translationY = if (isDragging) dragOffsetY else 0f
                                        scaleX = if (isDragging) 1.02f else 1f
                                        scaleY = if (isDragging) 1.02f else 1f
                                        shadowElevation = if (isDragging) rowDragElevationPx else 0f
                                        shape = dragRowShape
                                        clip = isDragging
                                    }
                                    .then(
                                        if (isDragging) {
                                            Modifier
                                                .background(
                                                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                                                    shape = dragRowShape
                                                )
                                                .background(
                                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.16f),
                                                    shape = dragRowShape
                                                )
                                                .border(
                                                    width = 1.2.dp,
                                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                                                    shape = dragRowShape
                                                )
                                        } else {
                                            Modifier
                                        }
                                    )
                            ) {
                                if (!isDragging) {
                                    CompactHairlineDivider(
                                        modifier = Modifier.padding(end = 14.dp),
                                        startIndent = if (idx == 0) 14.dp else 58.dp
                                    )
                                } else {
                                    Spacer(modifier = Modifier.height(0.5.dp))
                                }
                                YimuAccountRowItem(
                                    account = acc,
                                    displayCurrency = displayCurrency,
                                    convertedBalanceMicros = convertedMap[acc.id],
                                    isHidden = acc.id in hiddenAccountIds,
                                    privacyMasked = privacyMasked,
                                    onClick = { onClickAccount(acc) },
                                    dragModifier = if (canDragAccounts) {
                                        Modifier.pointerInput(acc.id) {
                                            detectDragGesturesAfterLongPress(
                                                onDragStart = {
                                                    if (!latestIsDraggingSection) {
                                                        draggingAccountId = acc.id
                                                        dragOffsetY = 0f
                                                        dragStartAccountOrder = localAccounts.map { it.id }
                                                        latestOnAccountDraggingChanged(true)
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    }
                                                },
                                                onDragEnd = { finishAccountDrag(acc.id) },
                                                onDragCancel = { finishAccountDrag(acc.id) },
                                                onDrag = { change, dragAmount ->
                                                    if (draggingAccountId != acc.id) return@detectDragGesturesAfterLongPress
                                                    change.consume()
                                                    dragOffsetY += dragAmount.y
                                                    var curIdx = localAccounts.indexOfFirst { it.id == acc.id }
                                                    if (curIdx < 0) return@detectDragGesturesAfterLongPress
                                                    while (dragOffsetY > 0f && curIdx < localAccounts.lastIndex) {
                                                        val nextId = localAccounts[curIdx + 1].id
                                                        val stepPx = rowHeightsPx[nextId]?.toFloat() ?: defaultRowHeightPx
                                                        if (dragOffsetY >= stepPx * 0.55f) {
                                                            val moved = localAccounts.removeAt(curIdx)
                                                            localAccounts.add(curIdx + 1, moved)
                                                            dragOffsetY -= stepPx
                                                            curIdx += 1
                                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                        } else {
                                                            break
                                                        }
                                                    }
                                                    while (dragOffsetY < 0f && curIdx > 0) {
                                                        val prevId = localAccounts[curIdx - 1].id
                                                        val stepPx = rowHeightsPx[prevId]?.toFloat() ?: defaultRowHeightPx
                                                        if (dragOffsetY <= -stepPx * 0.55f) {
                                                            val moved = localAccounts.removeAt(curIdx)
                                                            localAccounts.add(curIdx - 1, moved)
                                                            dragOffsetY += stepPx
                                                            curIdx -= 1
                                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                        } else {
                                                            break
                                                        }
                                                    }
                                                }
                                            )
                                        }
                                    } else {
                                        Modifier
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun YimuAccountRowItem(
    account: AccountEntity,
    displayCurrency: String,
    convertedBalanceMicros: Long?,
    isHidden: Boolean,
    privacyMasked: Boolean,
    onClick: () -> Unit,
    dragModifier: Modifier = Modifier
) {
    val semantic = KairoTheme.semantic
    val isForeign = !account.currencyCode.equals(displayCurrency, ignoreCase = true)
    val formattedAmount = formatPrivacyAmount(
        privacyMasked,
        MoneyAmount.format(
            amountMicros = account.balanceMicros,
            currencyCode = account.currencyCode,
            showCurrencyCode = isForeign
        )
    )
    val amountColor = when {
        account.balanceMicros < 0L -> semantic.expense
        account.balanceMicros > 0L -> semantic.income
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val isCredit = account.groupType == AccountGroup.CREDIT
    val creditCountdown = remember(isCredit, account.billingDay, account.repaymentDay) {
        if (isCredit) formatCreditCountdown(account.billingDay, account.repaymentDay) else ""
    }
    val showCreditSubBar = isCredit && (account.creditLimitMicros > 0L || creditCountdown.isNotEmpty())

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .then(dragModifier)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CartoonAccountIcon(
                iconKey = account.icon,
                accountName = account.name,
                colorHex = account.colorHex,
                size = 34.dp
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        text = account.name,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.5.sp),
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (!account.includeInNetWorth) {
                        AccountStatusMiniTag(
                            text = "不计净资产",
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (isHidden) {
                        AccountStatusMiniTag(
                            text = "已隐藏",
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (account.archived) {
                        AccountStatusMiniTag(
                            text = "已归档",
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    }
                }
                if (account.note.isNotBlank()) {
                    Text(
                        text = account.note,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                Text(
                    text = formattedAmount,
                    style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.5.sp).tabular(),
                    fontWeight = FontWeight.SemiBold,
                    color = amountColor,
                    maxLines = 1,
                    softWrap = false
                )
                if (isForeign && convertedBalanceMicros != null) {
                    Text(
                        text = formatPrivacyAmount(
                            privacyMasked,
                            "≈ ${MoneyAmount.format(convertedBalanceMicros, displayCurrency, showCurrencyCode = false)}"
                        ),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp).tabular(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }

        // 信用卡专属增强行：额度进度条（超 80% 警示色）+ 已用/可用额度 + 账单/还款倒计时
        if (showCreditSubBar) {
            Spacer(modifier = Modifier.height(8.dp))
            val limitMicros = account.creditLimitMicros.coerceAtLeast(0L)
            val usedMicros = (-account.balanceMicros).coerceAtLeast(0L)
            val availableMicros = (limitMicros + account.balanceMicros).coerceAtLeast(0L)
            val usedRatio = if (limitMicros > 0L) {
                (usedMicros.toDouble() / limitMicros.toDouble()).toFloat().coerceIn(0f, 1f)
            } else {
                0f
            }
            val usedPercent = (usedRatio * 100f).toInt()
            val isHighUsage = limitMicros > 0L && usedRatio >= 0.8f
            val barColor = when {
                isHighUsage -> semantic.expense
                usedRatio >= 0.6f -> semantic.warning
                else -> MaterialTheme.colorScheme.primary
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 46.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                if (limitMicros > 0L) {
                    LinearProgressIndicator(
                        progress = { if (privacyMasked) 0f else usedRatio },
                        color = barColor,
                        trackColor = if (privacyMasked) {
                            MaterialTheme.colorScheme.surfaceContainerHighest
                        } else {
                            barColor.copy(alpha = 0.14f)
                        },
                        strokeCap = StrokeCap.Round,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(CircleShape)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val usedStr = formatPrivacyAmount(
                            privacyMasked,
                            MoneyAmount.format(usedMicros, account.currencyCode)
                        )
                        val availStr = formatPrivacyAmount(
                            privacyMasked,
                            MoneyAmount.format(availableMicros, account.currencyCode)
                        )
                        Text(
                            text = "已用 $usedStr · 可用 $availStr",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp).tabular(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (privacyMasked) "••%" else "$usedPercent%",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp).tabular(),
                            fontWeight = if (isHighUsage) FontWeight.Bold else FontWeight.Medium,
                            color = if (privacyMasked) MaterialTheme.colorScheme.onSurfaceVariant else barColor,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
                if (creditCountdown.isNotEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Schedule,
                            contentDescription = null,
                            tint = if (isHighUsage) semantic.expense else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(11.dp)
                        )
                        Text(
                            text = creditCountdown,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp).tabular(),
                            color = if (isHighUsage) semantic.expense else MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountStatusMiniTag(
    text: String,
    containerColor: Color,
    contentColor: Color
) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = containerColor
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp, lineHeight = 12.sp),
            color = contentColor,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
        )
    }
}

@Composable
private fun ArchivedAccountsSectionCard(
    accounts: List<AccountEntity>,
    displayCurrency: String,
    privacyMasked: Boolean,
    expanded: Boolean,
    hasDetailCallback: Boolean,
    onToggleExpand: () -> Unit,
    onOpenDetail: (AccountEntity) -> Unit,
    onRestoreAccount: (AccountEntity) -> Unit,
    onDeleteAccount: (AccountEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val semantic = KairoTheme.semantic
    YimuCard(
        cornerRadius = 16.dp,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpand)
                    .padding(horizontal = 14.dp, vertical = 13.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Archive,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    Text(
                        text = "已归档账户",
                        style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.5.sp),
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest
                    ) {
                        Text(
                            text = "${accounts.size}",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp).tabular(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                        )
                    }
                }
                Icon(
                    imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = if (expanded) "收起已归档账户" else "展开已归档账户",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column {
                    accounts.forEachIndexed { idx, acc ->
                        CompactHairlineDivider(
                            modifier = Modifier.padding(end = 14.dp),
                            startIndent = if (idx == 0) 14.dp else 56.dp
                        )
                        val isForeign = !acc.currencyCode.equals(displayCurrency, ignoreCase = true)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenDetail(acc) }
                                .padding(horizontal = 14.dp, vertical = 11.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .alpha(0.84f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CartoonAccountIcon(
                                    iconKey = acc.icon,
                                    accountName = acc.name,
                                    colorHex = acc.colorHex,
                                    size = 30.dp
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Text(
                                            text = acc.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        AccountStatusMiniTag(
                                            text = "已归档",
                                            containerColor = MaterialTheme.colorScheme.errorContainer,
                                            contentColor = MaterialTheme.colorScheme.error
                                        )
                                    }
                                    if (acc.note.isNotBlank()) {
                                        Text(
                                            text = acc.note,
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = formatPrivacyAmount(
                                        privacyMasked,
                                        MoneyAmount.format(
                                            amountMicros = acc.balanceMicros,
                                            currencyCode = acc.currencyCode,
                                            showCurrencyCode = isForeign
                                        )
                                    ),
                                    style = MaterialTheme.typography.titleSmall.copy(fontSize = 13.5.sp).tabular(),
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 42.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ArchivedInlineActionChip(
                                    icon = Icons.Rounded.Unarchive,
                                    label = "恢复",
                                    tint = MaterialTheme.colorScheme.primary,
                                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                                    onClick = { onRestoreAccount(acc) }
                                )
                                if (hasDetailCallback) {
                                    ArchivedInlineActionChip(
                                        icon = Icons.Rounded.AccountBalanceWallet,
                                        label = "详情",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                        onClick = { onOpenDetail(acc) }
                                    )
                                }
                                ArchivedInlineActionChip(
                                    icon = Icons.Rounded.Delete,
                                    label = "删除",
                                    tint = semantic.expense,
                                    containerColor = semantic.expenseContainer.copy(alpha = 0.72f),
                                    onClick = { onDeleteAccount(acc) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ArchivedInlineActionChip(
    icon: ImageVector,
    label: String,
    tint: Color,
    containerColor: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .clip(CircleShape)
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = containerColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                fontWeight = FontWeight.Medium,
                color = tint,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

/**
 * 资产排序管理弹窗：支持「账户排序」与「分组顺序」双 Tab，
 * 左侧抓手支持纯 Compose 丝滑拖拽 + 触觉反馈，右侧提供 置顶/上移/下移 快捷操作。
 */
@Composable
private fun AssetReorderDialog(
    initialGroupOrder: List<String>,
    activeAccounts: List<AccountEntity>,
    archivedAccounts: List<AccountEntity>,
    privacyMasked: Boolean,
    onSaveOrder: (orderedGroupIds: List<String>, orderedAccountIds: List<Long>) -> Unit,
    onDismiss: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = 账户排序, 1 = 分组顺序
    var isDraggingAny by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    val groupList = remember(initialGroupOrder) {
        resolveOrderedAssetSections(initialGroupOrder).toMutableStateList()
    }
    val accountsBySection = remember(activeAccounts) {
        val map = LinkedHashMap<String, SnapshotStateList<AccountEntity>>()
        YimuAssetSections.forEach { section ->
            val list = activeAccounts
                .filter { it.groupType in section.groupCodes }
                .sortedWith(compareBy({ it.sortOrder }, { it.id }))
                .toMutableStateList()
            map[section.id] = list
        }
        map
    }

    YubraeDialog(
        onDismissRequest = onDismiss,
        title = "资产排序管理",
        subtitle = "拖动左侧抓手或点击右侧按钮调整展示顺序",
        icon = Icons.Rounded.SwapVert,
        confirmButton = {
            Button(
                onClick = {
                    val orderedGroupIds = groupList.map { it.id }
                    val orderedAccountIds = ArrayList<Long>(activeAccounts.size + archivedAccounts.size)
                    val seenIds = HashSet<Long>()
                    groupList.forEach { section ->
                        accountsBySection[section.id].orEmpty().forEach { acc ->
                            if (seenIds.add(acc.id)) {
                                orderedAccountIds.add(acc.id)
                            }
                        }
                    }
                    activeAccounts.forEach { acc ->
                        if (seenIds.add(acc.id)) orderedAccountIds.add(acc.id)
                    }
                    archivedAccounts.forEach { acc ->
                        if (seenIds.add(acc.id)) orderedAccountIds.add(acc.id)
                    }
                    onSaveOrder(orderedGroupIds, orderedAccountIds)
                },
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "保存排序",
                    maxLines = 1,
                    softWrap = false
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "取消",
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 双 Tab 切换器：账户排序 / 分组顺序
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    ReorderModeTab(
                        label = "账户排序",
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        modifier = Modifier.weight(1f)
                    )
                    ReorderModeTab(
                        label = "分组顺序",
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(scrollState, enabled = !isDraggingAny),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (selectedTab == 0) {
                    if (activeAccounts.isEmpty()) {
                        EmptyStateCard(
                            icon = Icons.Rounded.AccountBalanceWallet,
                            title = "暂无可排序的活跃账户"
                        )
                    } else {
                        groupList.forEach { section ->
                            val sectionList = accountsBySection[section.id]
                            if (sectionList != null && sectionList.isNotEmpty()) {
                                val accent = Color(section.accentColorHex)
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                                    border = BorderStroke(
                                        0.5.dp,
                                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        ) {
                                            Icon(
                                                imageVector = section.icon,
                                                contentDescription = null,
                                                tint = accent,
                                                modifier = Modifier.size(15.dp)
                                            )
                                            Text(
                                                text = section.title,
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "(${sectionList.size})",
                                                style = MaterialTheme.typography.labelSmall.tabular(),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        }

                                        DraggableReorderColumn(
                                            items = sectionList,
                                            keySelector = { it.id },
                                            onHapticStep = {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            },
                                            onDraggingChanged = { isDraggingAny = it }
                                        ) { acc, _, _ ->
                                            Row(
                                                modifier = Modifier.weight(1f),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                CartoonAccountIcon(
                                                    iconKey = acc.icon,
                                                    accountName = acc.name,
                                                    colorHex = acc.colorHex,
                                                    size = 26.dp
                                                )
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = acc.name,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = FontWeight.Medium,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    val sub = acc.note.ifBlank {
                                                        formatPrivacyAmount(
                                                            privacyMasked,
                                                            MoneyAmount.format(acc.balanceMicros, acc.currencyCode)
                                                        )
                                                    }
                                                    Text(
                                                        text = sub,
                                                        style = MaterialTheme.typography.labelSmall.tabular(),
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    DraggableReorderColumn(
                        items = groupList,
                        keySelector = { it.id },
                        onHapticStep = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        },
                        onDraggingChanged = { isDraggingAny = it }
                    ) { section, _, _ ->
                        val accent = Color(section.accentColorHex)
                        val count = accountsBySection[section.id]?.size ?: 0
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(accent.copy(alpha = 0.14f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = section.icon,
                                    contentDescription = null,
                                    tint = accent,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = section.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${count} 个活跃账户",
                                    style = MaterialTheme.typography.labelSmall.tabular(),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReorderModeTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .height(34.dp)
            .clip(RoundedCornerShape(9.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(9.dp),
        color = if (selected) MaterialTheme.colorScheme.surfaceContainerLowest else Color.Transparent,
        shadowElevation = if (selected) 1.dp else 0.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
private fun <T> DraggableReorderColumn(
    items: SnapshotStateList<T>,
    keySelector: (T) -> Any,
    onHapticStep: () -> Unit,
    onDraggingChanged: (Boolean) -> Unit = {},
    itemContent: @Composable androidx.compose.foundation.layout.RowScope.(item: T, index: Int, isDragging: Boolean) -> Unit
) {
    val density = LocalDensity.current
    val spacingDp = 6.dp
    val spacingPx = remember(density) { with(density) { spacingDp.toPx() } }
    val defaultRowHeightPx = remember(density) { with(density) { 52.dp.toPx() } }

    var draggedKey by remember { mutableStateOf<Any?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    val itemHeightsPx = remember { mutableStateMapOf<Any, Int>() }
    val latestOnHaptic by rememberUpdatedState(onHapticStep)
    val latestOnDraggingChanged by rememberUpdatedState(onDraggingChanged)

    fun moveItem(fromIndex: Int, toIndex: Int) {
        if (fromIndex == toIndex || fromIndex !in items.indices || toIndex !in items.indices) return
        val moved = items.removeAt(fromIndex)
        items.add(toIndex, moved)
        latestOnHaptic()
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacingDp)
    ) {
        items.forEachIndexed { index, item ->
            val itemKey = keySelector(item)
            key(itemKey) {
                val isDragging = draggedKey == itemKey
                val canMoveUp = index > 0
                val canMoveDown = index < items.lastIndex

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .zIndex(if (isDragging) 10f else 0f)
                        .onSizeChanged { size ->
                            if (size.height > 0) {
                                itemHeightsPx[itemKey] = size.height
                            }
                        }
                        .graphicsLayer {
                            translationY = if (isDragging) dragOffsetY else 0f
                            scaleX = if (isDragging) 1.02f else 1f
                            scaleY = if (isDragging) 1.02f else 1f
                            shadowElevation = if (isDragging) 14f else 0f
                        },
                    shape = RoundedCornerShape(12.dp),
                    color = if (isDragging) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.32f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLowest
                    },
                    border = BorderStroke(
                        width = if (isDragging) 1.2.dp else 0.5.dp,
                        color = if (isDragging) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                        } else {
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                        }
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 4.dp, end = 6.dp, top = 7.dp, bottom = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // 左侧拖拽抓手（纯 Compose pointerInput + detectDragGestures）
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .pointerInput(itemKey) {
                                    detectDragGestures(
                                        onDragStart = {
                                            draggedKey = itemKey
                                            dragOffsetY = 0f
                                            latestOnDraggingChanged(true)
                                            latestOnHaptic()
                                        },
                                        onDragEnd = {
                                            draggedKey = null
                                            dragOffsetY = 0f
                                            latestOnDraggingChanged(false)
                                        },
                                        onDragCancel = {
                                            draggedKey = null
                                            dragOffsetY = 0f
                                            latestOnDraggingChanged(false)
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            dragOffsetY += dragAmount.y
                                            var curIdx = items.indexOfFirst { keySelector(it) == itemKey }
                                            if (curIdx < 0) return@detectDragGestures
                                            while (dragOffsetY > 0f && curIdx < items.lastIndex) {
                                                val nextKey = keySelector(items[curIdx + 1])
                                                val stepPx = (itemHeightsPx[nextKey]?.toFloat() ?: defaultRowHeightPx) + spacingPx
                                                if (dragOffsetY >= stepPx * 0.55f) {
                                                    val moved = items.removeAt(curIdx)
                                                    items.add(curIdx + 1, moved)
                                                    dragOffsetY -= stepPx
                                                    curIdx += 1
                                                    latestOnHaptic()
                                                } else {
                                                    break
                                                }
                                            }
                                            while (dragOffsetY < 0f && curIdx > 0) {
                                                val prevKey = keySelector(items[curIdx - 1])
                                                val stepPx = (itemHeightsPx[prevKey]?.toFloat() ?: defaultRowHeightPx) + spacingPx
                                                if (dragOffsetY <= -stepPx * 0.55f) {
                                                    val moved = items.removeAt(curIdx)
                                                    items.add(curIdx - 1, moved)
                                                    dragOffsetY += stepPx
                                                    curIdx -= 1
                                                    latestOnHaptic()
                                                } else {
                                                    break
                                                }
                                            }
                                        }
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DragHandle,
                                contentDescription = "拖动排序",
                                tint = if (isDragging) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
                                },
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        itemContent(item, index, isDragging)

                        // 右侧快捷操作按钮：置顶 / 上移 / 下移
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            ReorderQuickStepButton(
                                icon = Icons.Rounded.VerticalAlignTop,
                                contentDescription = "置顶",
                                enabled = canMoveUp,
                                onClick = { moveItem(index, 0) }
                            )
                            ReorderQuickStepButton(
                                icon = Icons.Rounded.KeyboardArrowUp,
                                contentDescription = "上移",
                                enabled = canMoveUp,
                                onClick = { moveItem(index, index - 1) }
                            )
                            ReorderQuickStepButton(
                                icon = Icons.Rounded.KeyboardArrowDown,
                                contentDescription = "下移",
                                enabled = canMoveDown,
                                onClick = { moveItem(index, index + 1) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReorderQuickStepButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(26.dp)
            .clip(CircleShape)
            .then(
                if (enabled) {
                    Modifier
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f))
                        .clickable(onClick = onClick)
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
            },
            modifier = Modifier.size(16.dp)
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

@Suppress("UNUSED_PARAMETER")
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AccountEditorDialog(
    existing: AccountEntity?,
    pinnedCurrencies: List<String>,
    onSave: (AccountEntity) -> Unit,
    onDismiss: () -> Unit,
    initialGroupType: String = AccountGroup.WALLET,
    onQuickCalibrate: ((AccountEntity) -> Unit)? = null
) {
    var name by remember(existing?.id) { mutableStateOf(existing?.name ?: "") }
    var noteInput by remember(existing?.id) { mutableStateOf(existing?.note ?: "") }
    var groupType by remember(existing?.id, initialGroupType) { mutableStateOf(existing?.groupType ?: initialGroupType) }
    var currencyCode by remember(existing?.id) { mutableStateOf(existing?.currencyCode ?: CurrencyCatalog.DEFAULT_BASE_CURRENCY) }
    var balanceInput by remember(existing?.id) {
        mutableStateOf(
            if (existing != null) {
                MoneyAmount.formatPlain(existing.balanceMicros, CurrencyCatalog.find(existing.currencyCode).minorDigits, false)
            } else {
                "0.00"
            }
        )
    }
    var creditLimitInput by remember(existing?.id) {
        mutableStateOf(
            if (existing != null && existing.creditLimitMicros > 0L) {
                MoneyAmount.formatPlain(existing.creditLimitMicros, CurrencyCatalog.find(existing.currencyCode).minorDigits, false)
            } else {
                "50000.00"
            }
        )
    }
    var billingDayInput by remember(existing?.id) { mutableStateOf(existing?.billingDay?.toString() ?: "5") }
    var repaymentDayInput by remember(existing?.id) { mutableStateOf(existing?.repaymentDay?.toString() ?: "23") }
    var iconKey by remember(existing?.id) {
        mutableStateOf(existing?.icon?.takeUnless { it == "wallet" } ?: "account_balance_wallet")
    }
    var colorHex by remember(existing?.id) {
        mutableLongStateOf(existing?.colorHex ?: KairoIcons.selectableColorPalette.first())
    }
    var includeInNetWorth by remember(existing?.id) { mutableStateOf(existing?.includeInNetWorth ?: true) }
    val sortOrder by remember(existing?.id) { mutableIntStateOf(existing?.sortOrder ?: 100) }
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
                            updatedAt = System.currentTimeMillis(),
                            note = noteInput.trim()
                        )
                    )
                },
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("保存", maxLines = 1, softWrap = false)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp)
            ) { Text("取消", maxLines = 1, softWrap = false) }
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
                onValueChange = {
                    name = it
                    errorMsg = null
                },
                label = { Text("账户名称") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = noteInput,
                onValueChange = { noteInput = it },
                label = { Text("账户备注（可选）") },
                placeholder = {
                    Text(
                        text = "如：工资卡 · 尾号 8821 / 免年费需刷 6 次",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                maxLines = 2,
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
                        label = { Text(AccountGroup.labelZh(g), maxLines = 1, softWrap = false) }
                    )
                }
            }

            Text("币种", style = MaterialTheme.typography.labelMedium)
            QuickCurrencySelectorRow(
                selectedCurrency = currencyCode,
                pinnedCurrencies = pinnedCurrencies,
                onSelectCurrency = { currencyCode = it },
                onOpenMore = { showMoreCurrencies = true }
            )

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
    onConfirm: (Long, String, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val semantic = KairoTheme.semantic
    val digits = CurrencyCatalog.find(account.currencyCode).minorDigits
    var targetInput by remember {
        mutableStateOf(
            if (privacyMasked) "" else MoneyAmount.formatPlain(account.balanceMicros, digits, grouping = false)
        )
    }
    var noteInput by remember { mutableStateOf("余额校准") }
    var createRecord by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val parsedTargetMicros = remember(targetInput) { MoneyAmount.parseToMicros(targetInput) }
    val deltaMicros = parsedTargetMicros?.let { it - account.balanceMicros }

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
                        val finalNote = if (createRecord) noteInput.trim().ifEmpty { "余额校准" } else ""
                        onConfirm(targetMicros, finalNote, createRecord)
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
                onValueChange = {
                    targetInput = it
                    errorMsg = null
                },
                label = { Text("实际余额 (${account.currencyCode})") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )
            if (deltaMicros != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "差额变动",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = formatPrivacyAmount(
                                privacyMasked,
                                MoneyAmount.format(
                                    amountMicros = deltaMicros,
                                    currencyCode = account.currencyCode,
                                    showSign = true,
                                    showCurrencyCode = true
                                )
                            ),
                            style = MaterialTheme.typography.bodyMedium.tabular(),
                            fontWeight = FontWeight.SemiBold,
                            color = when {
                                deltaMicros > 0L -> semantic.income
                                deltaMicros < 0L -> semantic.expense
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { createRecord = !createRecord }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                        Text(
                            text = "生成差额变动记录",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = if (createRecord) {
                                "在流水中生成余额校准记录，删除可回滚"
                            } else {
                                "仅直接修改账户余额，不写入流水明细"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = createRecord,
                        onCheckedChange = { createRecord = it }
                    )
                }
            }
            AnimatedVisibility(visible = createRecord) {
                OutlinedTextField(
                    value = noteInput,
                    onValueChange = { noteInput = it },
                    label = { Text("备注") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            errorMsg?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
internal fun DeleteAccountConfirmDialog(
    account: AccountEntity,
    linkedTxCount: Int,
    availableTargets: List<AccountEntity>,
    onConfirmDelete: (migrateToAccountId: Long?) -> Unit,
    onDismiss: () -> Unit
) {
    val semantic = KairoTheme.semantic
    val privacyMasked = LocalPrivacyMasked.current
    var migrateMode by remember(account.id, linkedTxCount, availableTargets.size) {
        mutableStateOf(linkedTxCount > 0 && availableTargets.isNotEmpty())
    }
    var selectedTargetId by remember(account.id, availableTargets) {
        mutableLongStateOf(availableTargets.firstOrNull()?.id ?: 0L)
    }

    YubraeDialog(
        onDismissRequest = onDismiss,
        title = if (linkedTxCount == 0) "删除账户「${account.name}」？" else "删除或迁移「${account.name}」",
        subtitle = "${AccountGroup.labelZh(account.groupType)} · ${account.currencyCode}" +
            if (linkedTxCount > 0) " · 关联 $linkedTxCount 笔账单" else "",
        icon = Icons.Rounded.Delete,
        confirmButton = {
            val canConfirm = !migrateMode || selectedTargetId > 0L
            Button(
                onClick = {
                    val target = if (linkedTxCount > 0 && migrateMode && selectedTargetId > 0L) {
                        selectedTargetId
                    } else {
                        null
                    }
                    onConfirmDelete(target)
                },
                enabled = canConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = semantic.expense,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(if (linkedTxCount > 0 && migrateMode) "迁移并删除" else "确认删除")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("取消")
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (linkedTxCount == 0) {
                Text(
                    text = "当前账户余额：${
                        formatPrivacyAmount(
                            privacyMasked,
                            MoneyAmount.format(account.balanceMicros, account.currencyCode, showCurrencyCode = true)
                        )
                    }。暂无关联账单，删除后将移除该账户及识别别名，重启也不会自动重建。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = "该账户共有 $linkedTxCount 笔历史关联账单，请选择处理方式：",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (availableTargets.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { migrateMode = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = migrateMode,
                                onClick = { migrateMode = true }
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "迁移账单与余额到其他账户后删除",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "保留全部流水、周期、分期与借贷记录",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    if (migrateMode) {
                        Column(
                            modifier = Modifier.padding(start = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            availableTargets.forEach { target ->
                                val selected = target.id == selectedTargetId
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerLowest
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { selectedTargetId = target.id }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        RadioButton(
                                            selected = selected,
                                            onClick = { selectedTargetId = target.id }
                                        )
                                        CartoonAccountIcon(
                                            iconKey = target.icon,
                                            accountName = target.name,
                                            colorHex = target.colorHex,
                                            size = 28.dp
                                        )
                                        Text(
                                            text = "${target.name} (${target.currencyCode})",
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { migrateMode = false }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = !migrateMode,
                            onClick = { migrateMode = false }
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "直接删除账户及关联 $linkedTxCount 笔账单",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = semantic.expense
                            )
                            Text(
                                text = "自动回滚跨账户转账对方余额并清理别名",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
