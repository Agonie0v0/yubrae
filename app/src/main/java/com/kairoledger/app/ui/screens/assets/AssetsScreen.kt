package com.kairoledger.app.ui.screens.assets

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CurrencyExchange
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Handshake
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kairoledger.app.core.model.CurrencyCatalog
import com.kairoledger.app.core.model.MoneyAmount
import com.kairoledger.app.data.db.AccountEntity
import com.kairoledger.app.data.db.AccountGroup
import com.kairoledger.app.ui.components.CategoryIconBadge
import com.kairoledger.app.ui.components.CurrencyBadge
import com.kairoledger.app.ui.components.CurrencyPickerDialog
import com.kairoledger.app.ui.components.EmptyStateCard
import com.kairoledger.app.ui.components.KairoIcons
import com.kairoledger.app.ui.components.LocalPrivacyMasked
import com.kairoledger.app.ui.components.YimuCard
import com.kairoledger.app.ui.components.formatPrivacyAmount
import com.kairoledger.app.ui.theme.KairoTheme
import com.kairoledger.app.ui.theme.tabular
import com.kairoledger.app.ui.viewmodel.KairoMainViewModel
import java.time.LocalDate
import java.time.temporal.ChronoUnit

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
    onOpenExchangeRates: () -> Unit = {}
) {
    val uiState by viewModel.assetsUiState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val privacyMasked = LocalPrivacyMasked.current
    val hasDetailCallback = onOpenAccountDetail !== DefaultOpenAccountDetail

    var actionAccount by remember { mutableStateOf<AccountEntity?>(null) }
    var editingAccount by remember { mutableStateOf<AccountEntity?>(null) }
    var showCreateAccountDialog by remember { mutableStateOf(false) }
    var calibrateTarget by remember { mutableStateOf<AccountEntity?>(null) }
    var showDisplayCurrencyMenu by remember { mutableStateOf(false) }
    var showMoreDisplayCurrencies by remember { mutableStateOf(false) }
    var collapsedGroups by remember { mutableStateOf<Set<String>>(emptySet()) }

    val totalVisibleCount = remember(uiState.groupedAccounts) {
        uiState.groupedAccounts.values.sumOf { it.size }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            bottom = contentPadding.calculateBottomPadding() + 88.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 顶部全宽衔接状态栏的柔和薄荷绿渐变净资产英雄卡
        item(key = "net_worth_card", contentType = "net_worth_hero") {
            NetWorthHeroCard(
                displayCurrency = uiState.displayCurrency,
                totalAssetsMicros = uiState.netWorth.totalAssetsMicros,
                totalLiabilitiesMicros = uiState.netWorth.totalLiabilitiesMicros,
                netWorthMicros = uiState.netWorth.netWorthMicros,
                privacyMasked = privacyMasked,
                pinnedCurrencies = settings.pinnedCurrencies,
                showMenu = showDisplayCurrencyMenu,
                onToggleMenu = { showDisplayCurrencyMenu = it },
                onSelectCurrency = { code ->
                    viewModel.setAssetsDisplayCurrency(code)
                    showDisplayCurrencyMenu = false
                },
                onOpenMoreCurrencies = {
                    showDisplayCurrencyMenu = false
                    showMoreDisplayCurrencies = true
                },
                onTogglePrivacy = onTogglePrivacy,
                onOpenExchangeRates = onOpenExchangeRates
            )
        }

        // 4 格圆角快捷功能条
        item(key = "quick_feature_bar", contentType = "quick_strip") {
            AssetsQuickActionStrip(
                pendingReimburseCount = pendingReimburseCount,
                openDebtCount = openDebtCount,
                onOpenReimburseCenter = onOpenReimburseCenter,
                onOpenDebtCenter = onOpenDebtCenter,
                onOpenQuickTransfer = onOpenQuickTransfer,
                onCreateAccount = { showCreateAccountDialog = true },
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        // 账户列表头部与归档筛选
        item(key = "accounts_section_header", contentType = "section_header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "我的账户 ($totalVisibleCount)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                FilterChip(
                    selected = uiState.showArchived,
                    onClick = { viewModel.setShowArchivedAccounts(!uiState.showArchived) },
                    label = { Text(if (uiState.showArchived) "含已归档" else "显示归档") }
                )
            }
        }

        if (uiState.groupedAccounts.isEmpty()) {
            item(key = "empty_accounts", contentType = "empty_state") {
                EmptyStateCard(
                    icon = Icons.Rounded.AccountBalanceWallet,
                    title = "暂无账户",
                    subtitle = "点击「新建账户」添加银行卡、电子钱包或外币账户（USD/EUR/GBP）。",
                    actionLabel = "立即新建",
                    onAction = { showCreateAccountDialog = true },
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        } else {
            AccountGroup.all.forEach { groupCode ->
                val list = uiState.groupedAccounts[groupCode].orEmpty()
                if (list.isNotEmpty()) {
                    item(key = "group_$groupCode", contentType = "account_group") {
                        val isCollapsed = groupCode in collapsedGroups
                        AccountGroupSection(
                            groupLabel = AccountGroup.labelZh(groupCode),
                            accounts = list,
                            displayCurrency = uiState.displayCurrency,
                            convertedMap = uiState.netWorth.accountConvertedBalances,
                            privacyMasked = privacyMasked,
                            collapsed = isCollapsed,
                            onToggleCollapse = {
                                collapsedGroups = if (isCollapsed) {
                                    collapsedGroups - groupCode
                                } else {
                                    collapsedGroups + groupCode
                                }
                            },
                            onClickAccount = { acc ->
                                if (hasDetailCallback) {
                                    onOpenAccountDetail(acc)
                                } else {
                                    actionAccount = acc
                                }
                            },
                            onOpenQuickMenu = { acc -> actionAccount = acc },
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
            }
        }
    }

    // 账户快捷操作菜单弹窗
    actionAccount?.let { acc ->
        val balanceFormatted = formatPrivacyAmount(
            privacyMasked,
            MoneyAmount.format(acc.balanceMicros, acc.currencyCode, showCurrencyCode = true)
        )
        AlertDialog(
            onDismissRequest = { actionAccount = null },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CategoryIconBadge(
                        iconKey = acc.icon,
                        colorHex = acc.colorHex,
                        size = 38.dp,
                        iconSize = 20.dp
                    )
                    Column {
                        Text(acc.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "${AccountGroup.labelZh(acc.groupType)} · ${acc.currencyCode}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (hasDetailCallback) {
                        AccountActionRow(
                            icon = Icons.Rounded.AccountBalanceWallet,
                            label = "查看账户详情与流水",
                            onClick = {
                                actionAccount = null
                                onOpenAccountDetail(acc)
                            }
                        )
                    }
                    AccountActionRow(
                        icon = Icons.Rounded.Add,
                        label = "用此账户记一笔",
                        onClick = {
                            actionAccount = null
                            onOpenQuickRecordForAccount(acc.id)
                        }
                    )
                    AccountActionRow(
                        icon = Icons.Rounded.Tune,
                        label = "余额校准（当前 $balanceFormatted）",
                        onClick = {
                            actionAccount = null
                            calibrateTarget = acc
                        }
                    )
                    AccountActionRow(
                        icon = Icons.Rounded.Edit,
                        label = "编辑账户信息",
                        onClick = {
                            actionAccount = null
                            editingAccount = acc
                        }
                    )
                    AccountActionRow(
                        icon = if (acc.archived) Icons.Rounded.Unarchive else Icons.Rounded.Archive,
                        label = if (acc.archived) "取消归档" else "归档此账户",
                        onClick = {
                            actionAccount = null
                            viewModel.archiveAccount(acc.id, !acc.archived)
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { actionAccount = null }) {
                    Text("关闭")
                }
            }
        )
    }

    // 新建或编辑账户对话框
    if (showCreateAccountDialog || editingAccount != null) {
        AccountEditorDialog(
            existing = editingAccount,
            pinnedCurrencies = settings.pinnedCurrencies,
            onSave = { account ->
                viewModel.saveAccount(account) {
                    showCreateAccountDialog = false
                    editingAccount = null
                }
            },
            onDismiss = {
                showCreateAccountDialog = false
                editingAccount = null
            }
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
private fun NetWorthHeroCard(
    displayCurrency: String,
    totalAssetsMicros: Long,
    totalLiabilitiesMicros: Long,
    netWorthMicros: Long,
    privacyMasked: Boolean,
    pinnedCurrencies: List<String>,
    showMenu: Boolean,
    onToggleMenu: (Boolean) -> Unit,
    onSelectCurrency: (String) -> Unit,
    onOpenMoreCurrencies: () -> Unit,
    onTogglePrivacy: () -> Unit,
    onOpenExchangeRates: () -> Unit
) {
    val semantic = KairoTheme.semantic
    val info = CurrencyCatalog.find(displayCurrency)
    val gradientColors = if (semantic.isDark) {
        listOf(Color(0xFF143E2D), Color(0xFF1D4F3B), Color(0xFF15382A))
    } else {
        listOf(Color(0xFFE3F7EE), Color(0xFFD2EFE1), Color(0xFFEDF9F3))
    }
    val titleColor = if (semantic.isDark) Color(0xFFCFF7E3) else Color(0xFF0A3D28)
    val subtitleColor = titleColor.copy(alpha = 0.76f)
    val pillBgColor = if (semantic.isDark) {
        Color(0xFF0F251C).copy(alpha = 0.72f)
    } else {
        Color.White.copy(alpha = 0.86f)
    }
    val shape = RoundedCornerShape(bottomStart = 26.dp, bottomEnd = 26.dp)

    Surface(
        shape = shape,
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(colors = gradientColors))
                .statusBarsPadding()
                .padding(start = 18.dp, end = 12.dp, top = 8.dp, bottom = 18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "资产",
                            style = MaterialTheme.typography.titleLarge,
                            color = titleColor,
                            fontWeight = FontWeight.Bold
                        )
                        Box {
                            Surface(
                                shape = CircleShape,
                                color = pillBgColor,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable { onToggleMenu(true) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(info.flag, style = MaterialTheme.typography.labelMedium)
                                    Text(
                                        text = info.code,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = titleColor
                                    )
                                    Icon(
                                        imageVector = Icons.Rounded.ArrowDropDown,
                                        contentDescription = "切换展示币种",
                                        tint = titleColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { onToggleMenu(false) }
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
                                    text = { Text("更多币种...") },
                                    onClick = onOpenMoreCurrencies
                                )
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onTogglePrivacy) {
                            Icon(
                                imageVector = if (privacyMasked) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = if (privacyMasked) "关闭隐私遮罩" else "开启隐私遮罩",
                                tint = titleColor
                            )
                        }
                        IconButton(onClick = onOpenExchangeRates) {
                            Icon(
                                imageVector = Icons.Rounded.CurrencyExchange,
                                contentDescription = "汇率中心",
                                tint = titleColor
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier.padding(end = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "净资产估值",
                        style = MaterialTheme.typography.labelMedium,
                        color = subtitleColor,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = formatPrivacyAmount(
                            privacyMasked,
                            MoneyAmount.format(
                                amountMicros = netWorthMicros,
                                currencyCode = displayCurrency,
                                showCurrencyCode = displayCurrency != "CNY"
                            )
                        ),
                        style = MaterialTheme.typography.displaySmall.tabular(),
                        color = titleColor
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = pillBgColor,
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                            Text(
                                text = "总资产",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = formatPrivacyAmount(
                                    privacyMasked,
                                    MoneyAmount.format(totalAssetsMicros, displayCurrency)
                                ),
                                style = MaterialTheme.typography.titleMedium.tabular(),
                                color = semantic.income,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = pillBgColor,
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                            Text(
                                text = "总负债",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = formatPrivacyAmount(
                                    privacyMasked,
                                    MoneyAmount.format(totalLiabilitiesMicros, displayCurrency)
                                ),
                                style = MaterialTheme.typography.titleMedium.tabular(),
                                color = semantic.expense,
                                fontWeight = FontWeight.Bold,
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

@Composable
private fun AssetsQuickActionStrip(
    pendingReimburseCount: Int,
    openDebtCount: Int,
    onOpenReimburseCenter: () -> Unit,
    onOpenDebtCenter: () -> Unit,
    onOpenQuickTransfer: () -> Unit,
    onCreateAccount: () -> Unit,
    modifier: Modifier = Modifier
) {
    YimuCard(cornerRadius = 18.dp, modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            QuickActionGridCell(
                icon = Icons.AutoMirrored.Rounded.ReceiptLong,
                label = "报销中心",
                accentColor = Color(0xFF2AA876),
                badgeCount = pendingReimburseCount,
                onClick = onOpenReimburseCenter,
                modifier = Modifier.weight(1f)
            )
            QuickActionGridCell(
                icon = Icons.Rounded.Handshake,
                label = "借贷往来",
                accentColor = Color(0xFF0284C7),
                badgeCount = openDebtCount,
                onClick = onOpenDebtCenter,
                modifier = Modifier.weight(1f)
            )
            QuickActionGridCell(
                icon = Icons.Rounded.SwapHoriz,
                label = "账户转账",
                accentColor = Color(0xFF6D28D9),
                badgeCount = 0,
                onClick = onOpenQuickTransfer,
                modifier = Modifier.weight(1f)
            )
            QuickActionGridCell(
                icon = Icons.Rounded.Add,
                label = "新建账户",
                accentColor = Color(0xFFEA580C),
                badgeCount = 0,
                onClick = onCreateAccount,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun QuickActionGridCell(
    icon: ImageVector,
    label: String,
    accentColor: Color,
    badgeCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        BadgedBox(
            badge = {
                if (badgeCount > 0) {
                    Badge(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ) {
                        Text(
                            text = if (badgeCount > 99) "99+" else badgeCount.toString(),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        ) {
            Surface(
                shape = CircleShape,
                color = accentColor.copy(alpha = 0.13f),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = accentColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun AccountGroupSection(
    groupLabel: String,
    accounts: List<AccountEntity>,
    displayCurrency: String,
    convertedMap: Map<Long, Long>,
    privacyMasked: Boolean,
    collapsed: Boolean,
    onToggleCollapse: () -> Unit,
    onClickAccount: (AccountEntity) -> Unit,
    onOpenQuickMenu: (AccountEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val groupSubtotalMicros = remember(accounts, convertedMap) {
        accounts.sumOf { convertedMap[it.id] ?: 0L }
    }

    YimuCard(cornerRadius = 18.dp, modifier = modifier) {
        Column(modifier = Modifier.padding(vertical = 6.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleCollapse)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = groupLabel,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Text(
                            text = "${accounts.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (convertedMap.isNotEmpty()) {
                        Text(
                            text = formatPrivacyAmount(
                                privacyMasked,
                                MoneyAmount.format(groupSubtotalMicros, displayCurrency)
                            ),
                            style = MaterialTheme.typography.labelMedium.tabular(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                    accounts.forEachIndexed { idx, acc ->
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 14.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(
                                alpha = if (idx == 0) 0.45f else 0.3f
                            )
                        )
                        AccountRowItem(
                            account = acc,
                            displayCurrency = displayCurrency,
                            convertedBalanceMicros = convertedMap[acc.id],
                            privacyMasked = privacyMasked,
                            onClick = { onClickAccount(acc) },
                            onLongClick = { onOpenQuickMenu(acc) },
                            onMoreClick = { onOpenQuickMenu(acc) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AccountRowItem(
    account: AccountEntity,
    displayCurrency: String,
    convertedBalanceMicros: Long?,
    privacyMasked: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMoreClick: () -> Unit
) {
    val semantic = KairoTheme.semantic
    val accColor = Color(account.colorHex)
    val currencyInfo = CurrencyCatalog.find(account.currencyCode)
    val isForeignFromDisplay = !account.currencyCode.equals(displayCurrency, ignoreCase = true)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CategoryIconBadge(
            iconKey = account.icon,
            colorHex = account.colorHex,
            size = 42.dp,
            iconSize = 22.dp
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = account.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Text(
                        text = "${currencyInfo.flag} ${account.currencyCode}",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }
                if (account.archived) {
                    Text(
                        text = "已归档",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                } else if (!account.includeInNetWorth) {
                    Text(
                        text = "不计净资产",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (account.groupType == AccountGroup.CREDIT) {
                val limitMicros = account.creditLimitMicros.coerceAtLeast(0L)
                val usedMicros = (-account.balanceMicros).coerceAtLeast(0L)
                val availableMicros = (account.creditLimitMicros + account.balanceMicros).coerceAtLeast(0L)
                val usedRatio = if (limitMicros > 0L) {
                    (usedMicros.toDouble() / limitMicros.toDouble()).toFloat().coerceIn(0f, 1f)
                } else {
                    0f
                }
                val usedPercent = (usedRatio * 100f).toInt()
                val progressColor = when {
                    usedRatio >= 0.9f -> semantic.expense
                    usedRatio >= 0.7f -> semantic.warning
                    else -> accColor
                }

                if (limitMicros > 0L) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        LinearProgressIndicator(
                            progress = { usedRatio },
                            color = progressColor,
                            trackColor = progressColor.copy(alpha = 0.14f),
                            strokeCap = StrokeCap.Round,
                            modifier = Modifier
                                .weight(1f)
                                .height(5.dp)
                                .clip(CircleShape)
                        )
                        Text(
                            text = if (privacyMasked) "已用 **" else "已用 $usedPercent%",
                            style = MaterialTheme.typography.labelSmall.tabular(),
                            color = progressColor
                        )
                    }
                }

                val countdownText = formatCreditCountdown(account.billingDay, account.repaymentDay)
                val availableStr = formatPrivacyAmount(
                    privacyMasked,
                    MoneyAmount.format(availableMicros, account.currencyCode)
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "可用 $availableStr",
                        style = MaterialTheme.typography.bodySmall.tabular(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (countdownText.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Schedule,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(10.dp)
                                )
                                Text(
                                    text = countdownText,
                                    style = MaterialTheme.typography.labelSmall.tabular(),
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = formatPrivacyAmount(
                    privacyMasked,
                    MoneyAmount.format(
                        amountMicros = account.balanceMicros,
                        currencyCode = account.currencyCode,
                        showCurrencyCode = !account.currencyCode.equals("CNY", true)
                    )
                ),
                style = MaterialTheme.typography.titleMedium.tabular(),
                color = if (account.balanceMicros < 0L) semantic.expense else MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
            if (isForeignFromDisplay && convertedBalanceMicros != null) {
                Text(
                    text = formatPrivacyAmount(
                        privacyMasked,
                        "≈ ${MoneyAmount.format(convertedBalanceMicros, displayCurrency)}"
                    ),
                    style = MaterialTheme.typography.bodySmall.tabular(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        IconButton(
            onClick = onMoreClick,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.MoreVert,
                contentDescription = "账户操作",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
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
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var groupType by remember { mutableStateOf(existing?.groupType ?: AccountGroup.WALLET) }
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
    var iconKey by remember { mutableStateOf(existing?.icon ?: "account_balance_wallet") }
    var colorHex by remember { mutableLongStateOf(existing?.colorHex ?: 0xFF2AA876L) }
    var includeInNetWorth by remember { mutableStateOf(existing?.includeInNetWorth ?: true) }
    val sortOrder by remember { mutableIntStateOf(existing?.sortOrder ?: 100) }
    var showMoreCurrencies by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "新建账户" else "编辑账户") },
        text = {
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
                    modifier = Modifier.fillMaxWidth()
                )

                Text("账户分组", style = MaterialTheme.typography.labelMedium)
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

                Text("账户币种", style = MaterialTheme.typography.labelMedium)
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
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = billingDayInput,
                            onValueChange = { billingDayInput = it },
                            label = { Text("账单日(1-31)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = repaymentDayInput,
                            onValueChange = { repaymentDayInput = it },
                            label = { Text("还款日(1-31)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Text("图标", style = MaterialTheme.typography.labelMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(KairoIcons.selectableAccountIcons, key = { it.first }) { (key, _) ->
                        val selected = iconKey == key
                        Surface(
                            shape = CircleShape,
                            color = if (selected) Color(colorHex).copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = if (selected) BorderStroke(1.5.dp, Color(colorHex)) else null,
                            modifier = Modifier
                                .size(38.dp)
                                .clickable { iconKey = key }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = KairoIcons.iconFor(key),
                                    contentDescription = null,
                                    tint = if (selected) Color(colorHex) else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                Text("颜色", style = MaterialTheme.typography.labelMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(KairoIcons.selectableColorPalette, key = { it }) { hex ->
                        val selected = colorHex == hex
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(Color(hex))
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
                    Text("计入净资产统计", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = includeInNetWorth, onCheckedChange = { includeInNetWorth = it })
                }

                errorMsg?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
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
                }
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )

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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("校准「${account.name}」余额") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "当前账面余额：${formatPrivacyAmount(privacyMasked, MoneyAmount.format(account.balanceMicros, account.currencyCode, showCurrencyCode = true))}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = targetInput,
                    onValueChange = { targetInput = it },
                    label = { Text("目标实际余额 (${account.currencyCode})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = noteInput,
                    onValueChange = { noteInput = it },
                    label = { Text("校准说明") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                errorMsg?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val targetMicros = MoneyAmount.parseToMicros(targetInput)
                    if (targetMicros == null) {
                        errorMsg = "请输入有效数字金额"
                    } else {
                        onConfirm(targetMicros, noteInput.trim().ifEmpty { "余额校准" })
                    }
                }
            ) {
                Text("确认校准")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
