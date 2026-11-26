package com.yubrae.app.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EventRepeat
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yubrae.app.core.model.CurrencyCatalog
import com.yubrae.app.core.model.MoneyAmount
import com.yubrae.app.data.db.AccountEntity
import com.yubrae.app.data.db.CategoryEntity
import com.yubrae.app.data.db.RecurringRuleEntity
import com.yubrae.app.data.db.TransactionType
import com.yubrae.app.ui.components.ConfirmActionDialog
import com.yubrae.app.ui.components.CurrencyPickerDialog
import com.yubrae.app.ui.components.EmptyStateCard
import com.yubrae.app.ui.components.KairoFormats
import com.yubrae.app.ui.components.QuickCurrencySelectorRow
import com.yubrae.app.ui.components.YubraeDialog
import com.yubrae.app.ui.components.YubraeDialogShape
import com.yubrae.app.ui.components.transactionAmountColor
import com.yubrae.app.ui.components.yubraeDialogContainerColor
import com.yubrae.app.ui.theme.tabular
import com.yubrae.app.ui.viewmodel.KairoMainViewModel
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringRulesScreen(
    viewModel: KairoMainViewModel,
    onBack: () -> Unit
) {
    val rules by viewModel.recurringRules.collectAsStateWithLifecycle()
    val accounts by viewModel.activeAccounts.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()

    val accountMap = remember(accounts) { accounts.associateBy { it.id } }
    val categoryMap = remember(categories) { categories.associateBy { it.id } }

    var editingRule by remember { mutableStateOf<RecurringRuleEntity?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var deleteCandidate by remember { mutableStateOf<RecurringRuleEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("周期账单") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateDialog = true }) {
                Icon(Icons.Rounded.Add, contentDescription = "新增订阅")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + 8.dp,
                bottom = innerPadding.calculateBottomPadding() + 88.dp,
                start = 16.dp,
                end = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "run_due_header") {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.48f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("周期自动入账", style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = "${rules.count { it.enabled }} 条已启用",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { viewModel.runDueRecurringRules() },
                            enabled = !isBusy,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (isBusy) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Icon(Icons.Rounded.PlayArrow, null, modifier = Modifier.size(16.dp))
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("立即执行")
                        }
                    }
                }
            }

            if (rules.isEmpty()) {
                item(key = "empty_recurring") {
                    EmptyStateCard(
                        icon = Icons.Rounded.EventRepeat,
                        title = "暂无周期账单",
                        actionLabel = "新增订阅",
                        onAction = { showCreateDialog = true }
                    )
                }
            } else {
                items(rules, key = { it.id }) { rule ->
                    val acct = accountMap[rule.accountId]
                    val cat = rule.categoryId?.let { categoryMap[it] }
                    val cycleLabel = when (rule.cycleType) {
                        "WEEKLY" -> "每周"
                        "YEARLY" -> "每年"
                        else -> "每月 ${rule.dayOfCycle} 日"
                    }
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        tonalElevation = 1.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = rule.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "$cycleLabel · ${acct?.name ?: "账户"} · ${cat?.name ?: KairoFormats.transactionTypeLabel(rule.type)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = MoneyAmount.format(
                                        rule.amountMicros,
                                        rule.currencyCode,
                                        showCurrencyCode = rule.currencyCode != "CNY"
                                    ),
                                    style = MaterialTheme.typography.titleMedium.tabular(),
                                    color = transactionAmountColor(rule.type),
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "下次执行：${KairoFormats.formatDate(rule.nextRunEpochMillis)}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { editingRule = rule }, modifier = Modifier.size(34.dp)) {
                                        Icon(Icons.Rounded.Edit, "编辑", modifier = Modifier.size(18.dp))
                                    }
                                    IconButton(onClick = { deleteCandidate = rule }, modifier = Modifier.size(34.dp)) {
                                        Icon(Icons.Rounded.Delete, "删除", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Switch(
                                        checked = rule.enabled,
                                        onCheckedChange = { enabled ->
                                            viewModel.saveRecurringRule(rule.copy(enabled = enabled))
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

    if (showCreateDialog || editingRule != null) {
        RecurringRuleEditorDialog(
            existing = editingRule,
            accounts = accounts,
            categories = categories,
            pinnedCurrencies = settings.pinnedCurrencies,
            defaultCurrency = settings.baseCurrency,
            onSave = { rule ->
                viewModel.saveRecurringRule(rule) {
                    showCreateDialog = false
                    editingRule = null
                }
            },
            onDismiss = {
                showCreateDialog = false
                editingRule = null
            }
        )
    }

    deleteCandidate?.let { rule ->
        ConfirmActionDialog(
            title = "删除周期订阅「${rule.name}」？",
            message = "删除后不会影响已生成的历史流水，但未来不再自动扣款记账。",
            confirmText = "删除",
            isDestructive = true,
            onConfirm = {
                viewModel.deleteRecurringRule(rule.id)
                deleteCandidate = null
            },
            onDismiss = { deleteCandidate = null }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun RecurringRuleEditorDialog(
    existing: RecurringRuleEntity?,
    accounts: List<AccountEntity>,
    categories: List<CategoryEntity>,
    pinnedCurrencies: List<String>,
    defaultCurrency: String,
    onSave: (RecurringRuleEntity) -> Unit,
    onDismiss: () -> Unit
) {
    val initialAccount = accounts.firstOrNull { it.id == existing?.accountId } ?: accounts.firstOrNull()
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var txType by remember { mutableStateOf(existing?.type ?: TransactionType.EXPENSE) }
    var selectedAccountId by remember { mutableLongStateOf(existing?.accountId ?: initialAccount?.id ?: 0L) }
    var toAccountId by remember { mutableStateOf(existing?.toAccountId ?: accounts.firstOrNull { it.id != selectedAccountId }?.id) }
    var currencyCode by remember { mutableStateOf(existing?.currencyCode ?: initialAccount?.currencyCode ?: defaultCurrency) }
    var amountInput by remember {
        mutableStateOf(
            if (existing != null) {
                MoneyAmount.formatPlain(existing.amountMicros, CurrencyCatalog.find(existing.currencyCode).minorDigits, false)
            } else {
                "20.00"
            }
        )
    }
    var selectedCategoryId by remember {
        mutableStateOf(existing?.categoryId ?: categories.firstOrNull { it.type == txType && it.parentId == null }?.id)
    }
    var merchant by remember { mutableStateOf(existing?.merchant ?: "") }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    var cycleType by remember { mutableStateOf(existing?.cycleType ?: "MONTHLY") }
    var dayOfCycleInput by remember { mutableStateOf((existing?.dayOfCycle ?: 1).toString()) }
    var nextRunMillis by remember { mutableLongStateOf(existing?.nextRunEpochMillis ?: System.currentTimeMillis()) }
    var enabled by remember { mutableStateOf(existing?.enabled ?: true) }
    var showMoreCurrencies by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    YubraeDialog(
        onDismissRequest = onDismiss,
        title = if (existing == null) "新增周期订阅" else "编辑周期订阅",
        icon = Icons.Rounded.EventRepeat,
        confirmButton = {
            Button(
                onClick = {
                    val micros = MoneyAmount.parseToMicros(amountInput)
                    when {
                        name.trim().isEmpty() -> errorMsg = "请输入订阅名称"
                        selectedAccountId == 0L -> errorMsg = "请选择账户"
                        txType == TransactionType.TRANSFER && (toAccountId == null || toAccountId == selectedAccountId) ->
                            errorMsg = "请选择不同的转入账户"
                        micros == null || micros <= 0L -> errorMsg = "请输入有效金额"
                        else -> onSave(
                            RecurringRuleEntity(
                                id = existing?.id ?: 0L,
                                name = name.trim(),
                                type = txType,
                                amountMicros = micros,
                                currencyCode = currencyCode,
                                accountId = selectedAccountId,
                                toAccountId = if (txType == TransactionType.TRANSFER) toAccountId else null,
                                categoryId = if (txType == TransactionType.TRANSFER) null else selectedCategoryId,
                                merchant = merchant.trim(),
                                note = note.trim(),
                                cycleType = cycleType,
                                dayOfCycle = (dayOfCycleInput.toIntOrNull() ?: 1).coerceIn(1, 31),
                                nextRunEpochMillis = nextRunMillis,
                                enabled = enabled
                            )
                        )
                    }
                },
                shape = RoundedCornerShape(12.dp)
            ) { Text("保存") }
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
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("名称") },
                placeholder = { Text("如 ChatGPT Plus") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    TransactionType.EXPENSE to "支出",
                    TransactionType.INCOME to "收入",
                    TransactionType.TRANSFER to "转账"
                ).forEach { (type, label) ->
                    FilterChip(
                        selected = txType == type,
                        onClick = {
                            txType = type
                            if (type != TransactionType.TRANSFER) {
                                selectedCategoryId = categories.firstOrNull { it.type == type && it.parentId == null }?.id
                            }
                        },
                        label = { Text(label) }
                    )
                }
            }

            Text("扣款/入账账户", style = MaterialTheme.typography.labelMedium)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(accounts, key = { it.id }) { acc ->
                    FilterChip(
                        selected = selectedAccountId == acc.id,
                        onClick = {
                            selectedAccountId = acc.id
                            currencyCode = acc.currencyCode
                        },
                        label = { Text("${acc.name}(${acc.currencyCode})") }
                    )
                }
            }

            if (txType == TransactionType.TRANSFER) {
                Text("转入目标账户", style = MaterialTheme.typography.labelMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(accounts.filter { it.id != selectedAccountId }, key = { it.id }) { acc ->
                        FilterChip(
                            selected = toAccountId == acc.id,
                            onClick = { toAccountId = acc.id },
                            label = { Text("${acc.name}(${acc.currencyCode})") }
                        )
                    }
                }
            }

            Text("订阅币种", style = MaterialTheme.typography.labelMedium)
            QuickCurrencySelectorRow(
                selectedCurrency = currencyCode,
                pinnedCurrencies = pinnedCurrencies,
                onSelectCurrency = { currencyCode = it },
                onOpenMore = { showMoreCurrencies = true }
            )

            OutlinedTextField(
                value = amountInput,
                onValueChange = { amountInput = it },
                label = { Text("周期金额 ($currencyCode)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )

            if (txType != TransactionType.TRANSFER) {
                Text("分类", style = MaterialTheme.typography.labelMedium)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    categories.filter { it.type == txType && it.parentId == null }.forEach { cat ->
                        FilterChip(
                            selected = selectedCategoryId == cat.id,
                            onClick = { selectedCategoryId = cat.id },
                            label = { Text(cat.name) }
                        )
                    }
                }
            }

            Text("周期类型", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("WEEKLY" to "每周", "MONTHLY" to "每月", "YEARLY" to "每年").forEach { (code, label) ->
                    FilterChip(selected = cycleType == code, onClick = { cycleType = code }, label = { Text(label) })
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = dayOfCycleInput,
                    onValueChange = { dayOfCycleInput = it },
                    label = { Text(if (cycleType == "WEEKLY") "周几(1-7)" else "几号(1-31)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f)
                )
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                    modifier = Modifier
                        .weight(1.2f)
                        .padding(top = 6.dp)
                ) {
                    Icon(Icons.Rounded.CalendarMonth, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = KairoFormats.formatDate(nextRunMillis),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            OutlinedTextField(
                value = merchant,
                onValueChange = { merchant = it },
                label = { Text("商户（可选）") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("备注（可选）") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("启用", style = MaterialTheme.typography.bodyMedium)
                Switch(checked = enabled, onCheckedChange = { enabled = it })
            }

            errorMsg?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
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

    if (showDatePicker) {
        val zone = ZoneId.systemDefault()
        val currentLocal = Instant.ofEpochMilli(nextRunMillis).atZone(zone).toLocalDate()
        val utcMillis = currentLocal.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = utcMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            shape = YubraeDialogShape,
            colors = DatePickerDefaults.colors(containerColor = yubraeDialogContainerColor()),
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { selectedUtc ->
                            val pickedDate = Instant.ofEpochMilli(selectedUtc).atZone(ZoneOffset.UTC).toLocalDate()
                            nextRunMillis = pickedDate.atTime(LocalTime.of(9, 0)).atZone(zone).toInstant().toEpochMilli()
                        }
                        showDatePicker = false
                    },
                    shape = RoundedCornerShape(12.dp)
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDatePicker = false },
                    shape = RoundedCornerShape(12.dp)
                ) { Text("取消") }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }
}
