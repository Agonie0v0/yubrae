package com.kairoledger.app.ui.screens.assets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kairoledger.app.core.model.CurrencyCatalog
import com.kairoledger.app.core.model.MoneyAmount
import com.kairoledger.app.data.db.AccountEntity
import com.kairoledger.app.data.db.AccountGroup
import com.kairoledger.app.ui.components.CurrencyBadge
import com.kairoledger.app.ui.components.CurrencyPickerDialog
import com.kairoledger.app.ui.components.EmptyStateCard
import com.kairoledger.app.ui.components.KairoIcons
import com.kairoledger.app.ui.theme.KairoTheme
import com.kairoledger.app.ui.theme.tabular
import com.kairoledger.app.ui.viewmodel.KairoMainViewModel

@Composable
fun AssetsScreen(
    viewModel: KairoMainViewModel,
    onOpenQuickTransfer: () -> Unit,
    onOpenQuickRecordForAccount: (Long) -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val uiState by viewModel.assetsUiState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    var actionAccount by remember { mutableStateOf<AccountEntity?>(null) }
    var editingAccount by remember { mutableStateOf<AccountEntity?>(null) }
    var showCreateAccountDialog by remember { mutableStateOf(false) }
    var calibrateTarget by remember { mutableStateOf<AccountEntity?>(null) }
    var showDisplayCurrencyMenu by remember { mutableStateOf(false) }
    var showMoreDisplayCurrencies by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 88.dp,
            start = 16.dp,
            end = 16.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 顶部净资产卡（含展示币种即时切换）
        item(key = "net_worth_card") {
            NetWorthHeroCard(
                displayCurrency = uiState.displayCurrency,
                totalAssetsMicros = uiState.netWorth.totalAssetsMicros,
                totalLiabilitiesMicros = uiState.netWorth.totalLiabilitiesMicros,
                netWorthMicros = uiState.netWorth.netWorthMicros,
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
                }
            )
        }

        // 快捷操作条
        item(key = "assets_actions") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { showCreateAccountDialog = true },
                    shape = CircleShape,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Rounded.Add, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("新建账户")
                }
                OutlinedButton(
                    onClick = onOpenQuickTransfer,
                    shape = CircleShape,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Rounded.SwapHoriz, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("快捷转账")
                }
                Spacer(modifier = Modifier.weight(1f))
                FilterChip(
                    selected = uiState.showArchived,
                    onClick = { viewModel.setShowArchivedAccounts(!uiState.showArchived) },
                    label = { Text(if (uiState.showArchived) "含已归档" else "显示归档") }
                )
            }
        }

        if (uiState.groupedAccounts.isEmpty()) {
            item(key = "empty_accounts") {
                EmptyStateCard(
                    icon = Icons.Rounded.AccountBalanceWallet,
                    title = "暂无账户",
                    subtitle = "点击「新建账户」添加银行卡、电子钱包或外币账户（USD/EUR/GBP）。",
                    actionLabel = "立即新建",
                    onAction = { showCreateAccountDialog = true }
                )
            }
        } else {
            AccountGroup.all.forEach { groupCode ->
                val list = uiState.groupedAccounts[groupCode].orEmpty()
                if (list.isNotEmpty()) {
                    item(key = "group_$groupCode") {
                        AccountGroupSection(
                            groupLabel = AccountGroup.labelZh(groupCode),
                            accounts = list,
                            displayCurrency = uiState.displayCurrency,
                            convertedMap = uiState.netWorth.accountConvertedBalances,
                            onClickAccount = { actionAccount = it }
                        )
                    }
                }
            }
        }
    }

    // 账户操作菜单弹窗
    actionAccount?.let { acc ->
        AlertDialog(
            onDismissRequest = { actionAccount = null },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color(acc.colorHex).copy(alpha = 0.16f),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = KairoIcons.iconFor(acc.icon),
                                contentDescription = null,
                                tint = Color(acc.colorHex),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
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
                        label = "余额校准（当前 ${MoneyAmount.format(acc.balanceMicros, acc.currencyCode, showCurrencyCode = true)}）",
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
    pinnedCurrencies: List<String>,
    showMenu: Boolean,
    onToggleMenu: (Boolean) -> Unit,
    onSelectCurrency: (String) -> Unit,
    onOpenMoreCurrencies: () -> Unit
) {
    val semantic = KairoTheme.semantic
    val info = CurrencyCatalog.find(displayCurrency)
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.58f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
                            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f)
                        )
                    )
                )
                .padding(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "净资产估值",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Box {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                            modifier = Modifier.clickable { onToggleMenu(true) }
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
                                    fontWeight = FontWeight.Bold
                                )
                                Icon(Icons.Rounded.ArrowDropDown, null, modifier = Modifier.size(16.dp))
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

                Text(
                    text = MoneyAmount.format(netWorthMicros, displayCurrency, showCurrencyCode = displayCurrency != "CNY"),
                    style = MaterialTheme.typography.displaySmall.tabular(),
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
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
                                text = MoneyAmount.format(totalAssetsMicros, displayCurrency),
                                style = MaterialTheme.typography.titleMedium.tabular(),
                                color = semantic.income,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
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
                                text = MoneyAmount.format(totalLiabilitiesMicros, displayCurrency),
                                style = MaterialTheme.typography.titleMedium.tabular(),
                                color = semantic.expense,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountGroupSection(
    groupLabel: String,
    accounts: List<AccountEntity>,
    displayCurrency: String,
    convertedMap: Map<Long, Long>,
    onClickAccount: (AccountEntity) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(vertical = 10.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = groupLabel,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "${accounts.size} 个账户",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            accounts.forEachIndexed { idx, acc ->
                if (idx > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 14.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                    )
                }
                AccountRowItem(
                    account = acc,
                    displayCurrency = displayCurrency,
                    convertedBalanceMicros = convertedMap[acc.id],
                    onClick = { onClickAccount(acc) }
                )
            }
        }
    }
}

@Composable
private fun AccountRowItem(
    account: AccountEntity,
    displayCurrency: String,
    convertedBalanceMicros: Long?,
    onClick: () -> Unit
) {
    val accColor = Color(account.colorHex)
    val currencyInfo = CurrencyCatalog.find(account.currencyCode)
    val isForeignFromDisplay = !account.currencyCode.equals(displayCurrency, ignoreCase = true)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = CircleShape,
            color = accColor.copy(alpha = 0.14f),
            modifier = Modifier.size(42.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = KairoIcons.iconFor(account.icon),
                    contentDescription = null,
                    tint = accColor,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = account.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
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
                val availableMicros = (account.creditLimitMicros + account.balanceMicros).coerceAtLeast(0L)
                val billingText = listOfNotNull(
                    account.billingDay?.let { "账单日${it}日" },
                    account.repaymentDay?.let { "还款日${it}日" }
                ).joinToString(" · ")
                Text(
                    text = "可用 ${MoneyAmount.format(availableMicros, account.currencyCode)}" +
                        if (billingText.isNotEmpty()) " · $billingText" else "",
                    style = MaterialTheme.typography.bodySmall.tabular(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = MoneyAmount.format(
                    amountMicros = account.balanceMicros,
                    currencyCode = account.currencyCode,
                    showCurrencyCode = !account.currencyCode.equals("CNY", true)
                ),
                style = MaterialTheme.typography.titleMedium.tabular(),
                color = if (account.balanceMicros < 0L) KairoTheme.semantic.expense else MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
            if (isForeignFromDisplay && convertedBalanceMicros != null) {
                Text(
                    text = "≈ ${MoneyAmount.format(convertedBalanceMicros, displayCurrency)}",
                    style = MaterialTheme.typography.bodySmall.tabular(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AccountActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
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
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Text(label, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccountEditorDialog(
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
    var colorHex by remember { mutableLongStateOf(existing?.colorHex ?: 0xFF006874L) }
    var includeInNetWorth by remember { mutableStateOf(existing?.includeInNetWorth ?: true) }
    var sortOrder by remember { mutableIntStateOf(existing?.sortOrder ?: 100) }
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
private fun CalibrateBalanceDialog(
    account: AccountEntity,
    onConfirm: (Long, String) -> Unit,
    onDismiss: () -> Unit
) {
    val digits = CurrencyCatalog.find(account.currencyCode).minorDigits
    var targetInput by remember {
        mutableStateOf(MoneyAmount.formatPlain(account.balanceMicros, digits, grouping = false))
    }
    var noteInput by remember { mutableStateOf("余额校准") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("校准「${account.name}」余额") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "当前账面余额：${MoneyAmount.format(account.balanceMicros, account.currencyCode, showCurrencyCode = true)}",
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
