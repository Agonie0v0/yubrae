package com.kairoledger.app.ui.components

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CurrencyExchange
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.KeyboardHide
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kairoledger.app.core.contract.ConversionPreview
import com.kairoledger.app.core.contract.RecordTransactionRequest
import com.kairoledger.app.core.model.CurrencyCatalog
import com.kairoledger.app.core.model.MoneyAmount
import com.kairoledger.app.data.db.AccountEntity
import com.kairoledger.app.data.db.CaptureChannel
import com.kairoledger.app.data.db.CategoryEntity
import com.kairoledger.app.data.db.ReimburseStatus
import com.kairoledger.app.data.db.TransactionEntity
import com.kairoledger.app.data.db.TransactionStatus
import com.kairoledger.app.data.db.TransactionType
import com.kairoledger.app.ui.theme.KairoTheme
import com.kairoledger.app.ui.theme.tabular
import com.kairoledger.app.ui.viewmodel.KairoMainViewModel
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

private object QuickExprEval {
    fun appendDigit(expr: String, digit: String): String {
        if (expr == "0") return digit
        val lastSep = maxOf(expr.lastIndexOf('+'), expr.lastIndexOf('-'))
        val currentSeg = expr.substring(lastSep + 1)
        if (currentSeg == "0") {
            return expr.dropLast(1) + digit
        }
        return expr + digit
    }

    fun appendDot(expr: String): String {
        val lastSep = maxOf(expr.lastIndexOf('+'), expr.lastIndexOf('-'))
        val currentSeg = expr.substring(lastSep + 1)
        if (currentSeg.contains('.')) return expr
        return if (currentSeg.isEmpty()) "${expr}0." else "$expr."
    }

    fun appendOperator(expr: String, op: Char): String {
        if (expr.isEmpty()) return ""
        val last = expr.last()
        return if (last == '+' || last == '-') {
            expr.dropLast(1) + op
        } else {
            expr + op
        }
    }

    fun evaluate(expr: String): BigDecimal? {
        val cleaned = expr.trim().trimEnd('+', '-', '.')
        if (cleaned.isEmpty()) return null
        val tokens = mutableListOf<String>()
        val sb = StringBuilder()
        cleaned.forEachIndexed { index, ch ->
            if ((ch == '+' || ch == '-') && index > 0) {
                if (sb.isNotEmpty()) {
                    tokens.add(sb.toString())
                    sb.clear()
                }
                tokens.add(ch.toString())
            } else {
                sb.append(ch)
            }
        }
        if (sb.isNotEmpty()) tokens.add(sb.toString())
        if (tokens.isEmpty()) return null

        return try {
            var acc = BigDecimal(tokens[0])
            var i = 1
            while (i + 1 < tokens.size) {
                val op = tokens[i]
                val rhs = BigDecimal(tokens[i + 1])
                acc = if (op == "+") acc.add(rhs) else acc.subtract(rhs)
                i += 2
            }
            acc
        } catch (_: Exception) {
            null
        }
    }

    fun evaluateToMicros(expr: String): Long? {
        val bd = evaluate(expr) ?: return null
        if (bd <= BigDecimal.ZERO) return null
        return try {
            bd.multiply(BigDecimal(MoneyAmount.AMOUNT_SCALE))
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact()
        } catch (_: Exception) {
            null
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun QuickRecordSheet(
    viewModel: KairoMainViewModel,
    accounts: List<AccountEntity>,
    categories: List<CategoryEntity>,
    pinnedCurrencies: List<String>,
    baseCurrency: String,
    existingTransaction: TransactionEntity? = null,
    initialType: String = TransactionType.EXPENSE,
    initialAccountId: Long? = null,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val focusManager = LocalFocusManager.current
    val semantic = KairoTheme.semantic

    val isEditMode = existingTransaction != null
    val initialAccount = remember(existingTransaction?.id, initialAccountId) {
        if (existingTransaction != null) {
            accounts.firstOrNull { it.id == existingTransaction.accountId } ?: accounts.firstOrNull()
        } else {
            accounts.firstOrNull { it.id == initialAccountId } ?: accounts.firstOrNull()
        }
    }

    var txType by remember(existingTransaction?.id, initialType) {
        mutableStateOf(existingTransaction?.type ?: initialType)
    }
    var selectedAccountId by remember(existingTransaction?.id, initialAccountId) {
        mutableLongStateOf(existingTransaction?.accountId ?: initialAccount?.id ?: 0L)
    }
    var toAccountId by remember(existingTransaction?.id, initialAccountId) {
        val initialTo = existingTransaction?.toAccountId
            ?: accounts.firstOrNull { it.id != (existingTransaction?.accountId ?: initialAccount?.id) }?.id
        mutableStateOf(initialTo)
    }
    var origCurrency by remember(existingTransaction?.id, initialAccountId) {
        mutableStateOf(existingTransaction?.origCurrency ?: initialAccount?.currencyCode ?: baseCurrency)
    }

    LaunchedEffect(accounts, existingTransaction?.id, initialAccountId) {
        if (existingTransaction == null && selectedAccountId == 0L && accounts.isNotEmpty()) {
            val acc = accounts.firstOrNull { it.id == initialAccountId } ?: accounts.first()
            selectedAccountId = acc.id
            origCurrency = acc.currencyCode
            if (toAccountId == null || toAccountId == acc.id) {
                toAccountId = accounts.firstOrNull { it.id != acc.id }?.id
            }
        }
    }
    var amountExpr by remember(existingTransaction?.id) {
        mutableStateOf(
            if (existingTransaction != null) {
                val digits = CurrencyCatalog.find(existingTransaction.origCurrency).minorDigits
                MoneyAmount.formatPlain(existingTransaction.origAmountMicros, digits, grouping = false)
            } else {
                ""
            }
        )
    }
    var selectedCategoryId by remember(existingTransaction?.id, initialType) {
        val defaultCat = existingTransaction?.categoryId
            ?: categories.firstOrNull { it.type == (existingTransaction?.type ?: initialType) && it.parentId == null }?.id
        mutableStateOf(defaultCat)
    }
    LaunchedEffect(categories, txType) {
        if (selectedCategoryId == null && (txType == TransactionType.EXPENSE || txType == TransactionType.INCOME)) {
            selectedCategoryId = categories.firstOrNull { it.type == txType && it.parentId == null }?.id
        }
    }
    var merchant by remember(existingTransaction?.id) { mutableStateOf(existingTransaction?.merchant ?: "") }
    var note by remember(existingTransaction?.id) { mutableStateOf(existingTransaction?.note ?: "") }
    var tags by remember(existingTransaction?.id) { mutableStateOf(existingTransaction?.tags ?: "") }
    var reimburseStatus by remember(existingTransaction?.id) {
        mutableStateOf(existingTransaction?.reimburseStatus ?: ReimburseStatus.NONE)
    }
    var timestampMillis by remember(existingTransaction?.id) {
        mutableLongStateOf(existingTransaction?.timestamp ?: System.currentTimeMillis())
    }

    // 跨币种换算手动覆写状态
    var manualAccountAmountMicros by remember(existingTransaction?.id) {
        mutableStateOf(
            if (existingTransaction != null && existingTransaction.origCurrency != existingTransaction.accountCurrency) {
                existingTransaction.accountAmountMicros
            } else {
                null
            }
        )
    }
    var manualRateToAccountMicros by remember(existingTransaction?.id) { mutableStateOf<Long?>(null) }
    var accountAmountText by remember(existingTransaction?.id) {
        mutableStateOf(
            if (existingTransaction != null && existingTransaction.origCurrency != existingTransaction.accountCurrency) {
                val digits = CurrencyCatalog.find(existingTransaction.accountCurrency).minorDigits
                MoneyAmount.formatPlain(existingTransaction.accountAmountMicros, digits, grouping = false)
            } else {
                ""
            }
        )
    }
    var rateText by remember(existingTransaction?.id) {
        mutableStateOf(
            if (existingTransaction != null && existingTransaction.origCurrency != existingTransaction.accountCurrency) {
                MoneyAmount.formatRate(existingTransaction.rateToAccountMicros, 4)
            } else {
                ""
            }
        )
    }
    var editingAccountAmountField by remember { mutableStateOf(false) }
    var editingRateField by remember { mutableStateOf(false) }

    // 转账目标账户跨币种状态
    var manualToAmountMicros by remember(existingTransaction?.id) {
        mutableStateOf(existingTransaction?.toAmountMicros)
    }
    var manualTransferRateMicros by remember(existingTransaction?.id) { mutableStateOf<Long?>(null) }
    var transferToAmountText by remember(existingTransaction?.id) {
        mutableStateOf(
            if (existingTransaction?.toAmountMicros != null && existingTransaction.toCurrency != null) {
                val digits = CurrencyCatalog.find(existingTransaction.toCurrency).minorDigits
                MoneyAmount.formatPlain(existingTransaction.toAmountMicros, digits, grouping = false)
            } else {
                ""
            }
        )
    }
    var transferRateText by remember(existingTransaction?.id) { mutableStateOf("1.00") }
    var editingTransferToAmountField by remember { mutableStateOf(false) }
    var editingTransferRateField by remember { mutableStateOf(false) }

    var preview by remember { mutableStateOf<ConversionPreview?>(null) }
    var showKeypad by remember { mutableStateOf(true) }
    var showMoreCurrencies by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showExtraDetails by remember(existingTransaction?.id) {
        mutableStateOf(
            existingTransaction != null && (
                existingTransaction.merchant.isNotBlank() ||
                    existingTransaction.note.isNotBlank() ||
                    existingTransaction.tags.isNotBlank() ||
                    existingTransaction.reimburseStatus != ReimburseStatus.NONE
                )
        )
    }

    val selectedAccount = accounts.firstOrNull { it.id == selectedAccountId }
    val selectedToAccount = accounts.firstOrNull { it.id == toAccountId }
    val parsedOrigMicros = QuickExprEval.evaluateToMicros(amountExpr) ?: 0L

    val needsSourceConversion = selectedAccount != null &&
        !origCurrency.equals(selectedAccount.currencyCode, ignoreCase = true)
    val needsTransferConversion = txType == TransactionType.TRANSFER &&
        selectedToAccount != null &&
        (!selectedToAccount.currencyCode.equals(origCurrency, ignoreCase = true) ||
            (selectedAccount != null && !selectedToAccount.currencyCode.equals(selectedAccount.currencyCode, ignoreCase = true)))

    // 实时调用 ledgerService.previewConversion
    LaunchedEffect(
        parsedOrigMicros,
        origCurrency,
        selectedAccountId,
        manualAccountAmountMicros,
        manualRateToAccountMicros,
        baseCurrency
    ) {
        if (selectedAccount != null) {
            val result = runCatching {
                viewModel.previewConversion(
                    origAmountMicros = parsedOrigMicros,
                    origCurrency = origCurrency,
                    accountId = selectedAccount.id,
                    manualAccountAmountMicros = manualAccountAmountMicros,
                    manualRateToAccountMicros = manualRateToAccountMicros,
                    baseCurrency = baseCurrency
                )
            }.getOrNull()
            preview = result
            if (result != null && needsSourceConversion) {
                if (!editingAccountAmountField) {
                    val digits = CurrencyCatalog.find(result.accountCurrency).minorDigits
                    accountAmountText = MoneyAmount.formatPlain(result.accountAmountMicros, digits, grouping = false)
                }
                if (!editingRateField) {
                    rateText = MoneyAmount.formatRate(result.rateToAccountMicros, 4)
                }
            }
        }
    }

    // 转账跨币种实时联动
    LaunchedEffect(
        txType,
        parsedOrigMicros,
        origCurrency,
        selectedAccountId,
        toAccountId,
        manualToAmountMicros,
        manualTransferRateMicros
    ) {
        if (txType == TransactionType.TRANSFER && selectedToAccount != null) {
            val toCode = selectedToAccount.currencyCode
            if (toCode.equals(origCurrency, ignoreCase = true)) {
                if (!editingTransferToAmountField) {
                    val digits = CurrencyCatalog.find(toCode).minorDigits
                    transferToAmountText = MoneyAmount.formatPlain(parsedOrigMicros, digits, grouping = false)
                }
                if (!editingTransferRateField) {
                    transferRateText = "1.00"
                }
            } else {
                val effectiveRate = when {
                    manualToAmountMicros != null && parsedOrigMicros > 0L ->
                        MoneyAmount.computeRate(parsedOrigMicros, manualToAmountMicros!!)
                    manualTransferRateMicros != null -> manualTransferRateMicros!!
                    else -> viewModel.getRateQuote(origCurrency, toCode).rateMicros
                }
                val effectiveToMicros = manualToAmountMicros ?: MoneyAmount.convert(parsedOrigMicros, effectiveRate)
                if (!editingTransferToAmountField) {
                    val digits = CurrencyCatalog.find(toCode).minorDigits
                    transferToAmountText = MoneyAmount.formatPlain(effectiveToMicros, digits, grouping = false)
                }
                if (!editingTransferRateField) {
                    transferRateText = MoneyAmount.formatRate(effectiveRate, 4)
                }
            }
        }
    }

    val quickCurrencyList = remember(pinnedCurrencies, origCurrency) {
        val base = pinnedCurrencies.ifEmpty { CurrencyCatalog.PINNED_CURRENCIES }
        if (base.any { it.equals(origCurrency, true) }) base else base + origCurrency
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
        ) {
            // 顶部栏：交易类型切换 + 删除/关闭按钮
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (txType == TransactionType.ADJUST) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = "余额校准记录",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val modes = listOf(
                            TransactionType.EXPENSE to "支出",
                            TransactionType.INCOME to "收入",
                            TransactionType.TRANSFER to "转账"
                        )
                        modes.forEach { (mode, label) ->
                            val selected = txType == mode
                            val pillColor = when {
                                !selected -> Color.Transparent
                                mode == TransactionType.EXPENSE -> semantic.expenseContainer
                                mode == TransactionType.INCOME -> semantic.incomeContainer
                                else -> semantic.transferContainer
                            }
                            val textColor = when {
                                !selected -> MaterialTheme.colorScheme.onSurfaceVariant
                                mode == TransactionType.EXPENSE -> semantic.expense
                                mode == TransactionType.INCOME -> semantic.income
                                else -> semantic.transfer
                            }
                            Surface(
                                shape = CircleShape,
                                color = pillColor,
                                modifier = Modifier.clickable {
                                    txType = mode
                                    if (mode != TransactionType.TRANSFER) {
                                        val matchingCat = categories.firstOrNull { it.id == selectedCategoryId && it.type == mode }
                                        if (matchingCat == null) {
                                            selectedCategoryId = categories.firstOrNull { it.type == mode && it.parentId == null }?.id
                                        }
                                    } else if (toAccountId == null || toAccountId == selectedAccountId) {
                                        toAccountId = accounts.firstOrNull { it.id != selectedAccountId }?.id
                                    }
                                }
                            ) {
                                Text(
                                    text = label,
                                    color = textColor,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp)
                                )
                            }
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isEditMode && existingTransaction != null) {
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(
                                imageVector = Icons.Rounded.Delete,
                                contentDescription = "删除交易",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Rounded.Close, contentDescription = "关闭")
                    }
                }
            }

            // 中部可滚动内容区
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 大字号金额与表达式展示卡片（点击唤起内置计算器）
                val currencyInfo = CurrencyCatalog.find(origCurrency)
                val accentColor = transactionAmountColor(txType)
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, accentColor.copy(alpha = 0.25f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            focusManager.clearFocus()
                            showKeypad = true
                        }
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(text = currencyInfo.flag, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = "${currencyInfo.code} · ${currencyInfo.nameZh}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (amountExpr.contains("+") || amountExpr.contains("-")) {
                                val evalMicros = parsedOrigMicros
                                Text(
                                    text = "= ${MoneyAmount.format(evalMicros, origCurrency, showCurrencyCode = true)}",
                                    style = MaterialTheme.typography.titleSmall.tabular(),
                                    color = accentColor,
                                    fontWeight = FontWeight.SemiBold
                                )
                            } else if (preview != null && !origCurrency.equals(baseCurrency, ignoreCase = true) && parsedOrigMicros > 0L) {
                                Text(
                                    text = "≈ ${MoneyAmount.format(preview!!.baseAmountMicros, baseCurrency)}",
                                    style = MaterialTheme.typography.labelMedium.tabular(),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${currencyInfo.symbol} ${amountExpr.ifEmpty { "0.00" }}",
                                style = MaterialTheme.typography.displaySmall.tabular(),
                                color = if (amountExpr.isEmpty()) accentColor.copy(alpha = 0.45f) else accentColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = {
                                    focusManager.clearFocus()
                                    showKeypad = !showKeypad
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = if (showKeypad) Icons.Rounded.KeyboardHide else Icons.Rounded.Calculate,
                                    contentDescription = "切换键盘",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                // 快捷币种芯片（置顶 CNY / USD / EUR / GBP + 更多）
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(quickCurrencyList, key = { it }) { code ->
                        CurrencyBadge(
                            currencyCode = code,
                            selected = code.equals(origCurrency, ignoreCase = true),
                            onClick = {
                                origCurrency = code
                                manualAccountAmountMicros = null
                                manualRateToAccountMicros = null
                                manualToAmountMicros = null
                            }
                        )
                    }
                    item(key = "more_currencies") {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.clickable { showMoreCurrencies = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(text = "更多币种", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }

                // 账户选择器（选中某账户时自动切换输入币种至该账户币种）
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = if (txType == TransactionType.TRANSFER) "转出账户（选择自动切换币种）" else "账户（选择自动切换至账户币种）",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (accounts.isEmpty()) {
                        Text(
                            text = "暂无可用账户，请先在「资产」页创建账户",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(accounts, key = { it.id }) { acc ->
                                val selected = acc.id == selectedAccountId
                                AccountSelectPill(
                                    account = acc,
                                    selected = selected,
                                    onClick = {
                                        selectedAccountId = acc.id
                                        // 核心痛点修复：选定账户时自动切到该账户币种
                                        origCurrency = acc.currencyCode
                                        manualAccountAmountMicros = null
                                        manualRateToAccountMicros = null
                                        manualToAmountMicros = null
                                        if (txType == TransactionType.TRANSFER && toAccountId == acc.id) {
                                            toAccountId = accounts.firstOrNull { it.id != acc.id }?.id
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                // 转账目标账户选择器
                if (txType == TransactionType.TRANSFER) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "转入目标账户",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (selectedAccount != null && selectedToAccount != null) {
                                TextButton(
                                    onClick = {
                                        val oldFrom = selectedAccount.id
                                        val oldTo = selectedToAccount.id
                                        selectedAccountId = oldTo
                                        toAccountId = oldFrom
                                        origCurrency = selectedToAccount.currencyCode
                                        manualAccountAmountMicros = null
                                        manualRateToAccountMicros = null
                                        manualToAmountMicros = null
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.SwapHoriz,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("互换账户", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                        val targetCandidates = accounts.filter { it.id != selectedAccountId }
                        if (targetCandidates.isEmpty()) {
                            Text(
                                text = "至少需要两个账户才能发起转账",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        } else {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(targetCandidates, key = { it.id }) { acc ->
                                    AccountSelectPill(
                                        account = acc,
                                        selected = acc.id == toAccountId,
                                        onClick = {
                                            toAccountId = acc.id
                                            manualToAmountMicros = null
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // 跨币种实时汇率换算联动面板
                AnimatedVisibility(visible = needsSourceConversion || needsTransferConversion) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.CurrencyExchange,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "跨币种实时汇率联动（支持反算汇损）",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                if (manualAccountAmountMicros != null || manualRateToAccountMicros != null || manualToAmountMicros != null || manualTransferRateMicros != null) {
                                    TextButton(
                                        onClick = {
                                            manualAccountAmountMicros = null
                                            manualRateToAccountMicros = null
                                            manualToAmountMicros = null
                                            manualTransferRateMicros = null
                                            editingAccountAmountField = false
                                            editingRateField = false
                                            editingTransferToAmountField = false
                                            editingTransferRateField = false
                                        },
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                                    ) {
                                        Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Text("重置汇率", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }

                            if (needsSourceConversion && selectedAccount != null) {
                                val acctCode = selectedAccount.currencyCode
                                val actionLabel = if (txType == TransactionType.INCOME) "实际入账($acctCode)" else "实际扣款($acctCode)"
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = accountAmountText,
                                        onValueChange = { input ->
                                            accountAmountText = input
                                            val parsed = MoneyAmount.parseToMicros(input)
                                            if (parsed != null && parsed > 0L) {
                                                manualAccountAmountMicros = parsed
                                                manualRateToAccountMicros = null
                                            }
                                        },
                                        label = { Text(actionLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        textStyle = MaterialTheme.typography.bodyLarge.tabular(),
                                        modifier = Modifier
                                            .weight(1.1f)
                                            .onFocusChanged { focusState ->
                                                editingAccountAmountField = focusState.isFocused
                                                if (focusState.isFocused) showKeypad = false
                                            }
                                    )
                                    OutlinedTextField(
                                        value = rateText,
                                        onValueChange = { input ->
                                            rateText = input
                                            val parsedRate = MoneyAmount.parseRateToMicros(input)
                                            if (parsedRate != null && parsedRate > 0L) {
                                                manualRateToAccountMicros = parsedRate
                                                manualAccountAmountMicros = null
                                            }
                                        },
                                        label = { Text("1 $origCurrency → $acctCode", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        textStyle = MaterialTheme.typography.bodyLarge.tabular(),
                                        modifier = Modifier
                                            .weight(0.9f)
                                            .onFocusChanged { focusState ->
                                                editingRateField = focusState.isFocused
                                                if (focusState.isFocused) showKeypad = false
                                            }
                                    )
                                }
                                val rateSourceLabel = when (preview?.rateSource) {
                                    "MANUAL_AMOUNT" -> "已按实际扣款额反算成交汇率"
                                    "MANUAL_RATE" -> "已使用手动指定汇率"
                                    else -> "参考市场汇率（修改左侧实际金额可自动反算汇损）"
                                }
                                Text(
                                    text = rateSourceLabel,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (needsTransferConversion && selectedToAccount != null) {
                                val toCode = selectedToAccount.currencyCode
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = transferToAmountText,
                                        onValueChange = { input ->
                                            transferToAmountText = input
                                            val parsed = MoneyAmount.parseToMicros(input)
                                            if (parsed != null && parsed > 0L) {
                                                manualToAmountMicros = parsed
                                                manualTransferRateMicros = null
                                            }
                                        },
                                        label = { Text("转入到账($toCode)", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        textStyle = MaterialTheme.typography.bodyLarge.tabular(),
                                        modifier = Modifier
                                            .weight(1.1f)
                                            .onFocusChanged { focusState ->
                                                editingTransferToAmountField = focusState.isFocused
                                                if (focusState.isFocused) showKeypad = false
                                            }
                                    )
                                    OutlinedTextField(
                                        value = transferRateText,
                                        onValueChange = { input ->
                                            transferRateText = input
                                            val parsedRate = MoneyAmount.parseRateToMicros(input)
                                            if (parsedRate != null && parsedRate > 0L) {
                                                manualTransferRateMicros = parsedRate
                                                manualToAmountMicros = null
                                            }
                                        },
                                        label = { Text("1 $origCurrency → $toCode", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        textStyle = MaterialTheme.typography.bodyLarge.tabular(),
                                        modifier = Modifier
                                            .weight(0.9f)
                                            .onFocusChanged { focusState ->
                                                editingTransferRateField = focusState.isFocused
                                                if (focusState.isFocused) showKeypad = false
                                            }
                                    )
                                }
                            }
                        }
                    }
                }

                // 一级 / 二级分类网格（支出 / 收入）
                if (txType == TransactionType.EXPENSE || txType == TransactionType.INCOME) {
                    val typeCats = categories.filter { it.type == txType }
                    val parents = typeCats.filter { it.parentId == null }
                    val selectedCatObj = typeCats.firstOrNull { it.id == selectedCategoryId }
                    val activeParentId = selectedCatObj?.parentId ?: selectedCatObj?.id
                    val subCategories = if (activeParentId != null) {
                        typeCats.filter { it.parentId == activeParentId }
                    } else {
                        emptyList()
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "分类",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            parents.forEach { cat ->
                                val isSelected = activeParentId == cat.id
                                CategorySelectChip(
                                    category = cat,
                                    selected = isSelected,
                                    onClick = { selectedCategoryId = cat.id }
                                )
                            }
                        }
                        if (subCategories.isNotEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                FlowRow(
                                    modifier = Modifier.padding(8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    subCategories.forEach { sub ->
                                        CategorySelectChip(
                                            category = sub,
                                            selected = selectedCategoryId == sub.id,
                                            compact = true,
                                            onClick = { selectedCategoryId = sub.id }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 日期时间与补充信息开关条
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = false,
                        onClick = { showDatePicker = true },
                        leadingIcon = {
                            Icon(Icons.Rounded.CalendarMonth, null, modifier = Modifier.size(16.dp))
                        },
                        label = { Text(KairoFormats.formatDate(timestampMillis)) }
                    )
                    FilterChip(
                        selected = false,
                        onClick = { showTimePicker = true },
                        leadingIcon = {
                            Icon(Icons.Rounded.Schedule, null, modifier = Modifier.size(16.dp))
                        },
                        label = { Text(KairoFormats.formatTime(timestampMillis)) }
                    )
                    FilterChip(
                        selected = showExtraDetails,
                        onClick = { showExtraDetails = !showExtraDetails },
                        label = { Text(if (showExtraDetails) "收起商户/备注/报销" else "商户 · 备注 · 报销") }
                    )
                }

                // 商户、备注、标签、报销状态
                AnimatedVisibility(visible = showExtraDetails) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = merchant,
                            onValueChange = { merchant = it },
                            label = { Text("商户 / 交易对方（如 星巴克、Hetzner）") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged { if (it.isFocused) showKeypad = false }
                        )
                        OutlinedTextField(
                            value = note,
                            onValueChange = { note = it },
                            label = { Text("备注") },
                            maxLines = 2,
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged { if (it.isFocused) showKeypad = false }
                        )
                        OutlinedTextField(
                            value = tags,
                            onValueChange = { tags = it },
                            label = { Text("标签（多个用英文逗号分隔）") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged { if (it.isFocused) showKeypad = false }
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "报销状态：",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            listOf(
                                ReimburseStatus.NONE to "不报销",
                                ReimburseStatus.PENDING to "待报销",
                                ReimburseStatus.REIMBURSED to "已报销"
                            ).forEach { (statusCode, label) ->
                                FilterChip(
                                    selected = reimburseStatus == statusCode,
                                    onClick = { reimburseStatus = statusCode },
                                    label = { Text(label) }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
            }

            // 底部计算器键盘或确认按钮
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            val performSave: () -> Unit = {
                val finalAmountMicros = QuickExprEval.evaluateToMicros(amountExpr)
                val acct = selectedAccount
                when {
                    acct == null -> viewModel.showMessage("请先选择账户")
                    finalAmountMicros == null || finalAmountMicros <= 0L -> viewModel.showMessage("请输入有效金额")
                    txType == TransactionType.TRANSFER && (selectedToAccount == null || selectedToAccount.id == acct.id) -> {
                        viewModel.showMessage("请选择不同的转入目标账户")
                    }
                    else -> {
                        val req = RecordTransactionRequest(
                            existingId = existingTransaction?.id,
                            type = txType,
                            timestamp = timestampMillis,
                            origAmountMicros = finalAmountMicros,
                            origCurrency = origCurrency,
                            accountId = acct.id,
                            manualAccountAmountMicros = manualAccountAmountMicros,
                            manualRateToAccountMicros = manualRateToAccountMicros,
                            toAccountId = if (txType == TransactionType.TRANSFER) selectedToAccount?.id else null,
                            manualToAmountMicros = if (txType == TransactionType.TRANSFER) {
                                manualToAmountMicros ?: manualTransferRateMicros?.let { MoneyAmount.convert(finalAmountMicros, it) }
                            } else null,
                            categoryId = if (txType == TransactionType.TRANSFER || txType == TransactionType.ADJUST) null else selectedCategoryId,
                            merchant = merchant.trim(),
                            note = note.trim(),
                            tags = tags.trim(),
                            reimburseStatus = reimburseStatus,
                            sourceType = existingTransaction?.sourceType ?: CaptureChannel.MANUAL,
                            rawEventId = existingTransaction?.rawEventId,
                            status = TransactionStatus.CONFIRMED
                        )
                        viewModel.saveTransaction(req, onSuccess = onDismiss)
                    }
                }
            }

            if (showKeypad) {
                CalculatorKeypad(
                    onDigit = { d -> amountExpr = QuickExprEval.appendDigit(amountExpr, d) },
                    onDot = { amountExpr = QuickExprEval.appendDot(amountExpr) },
                    onOperator = { op -> amountExpr = QuickExprEval.appendOperator(amountExpr, op) },
                    onBackspace = {
                        if (amountExpr.isNotEmpty()) amountExpr = amountExpr.dropLast(1)
                    },
                    onClear = { amountExpr = "" },
                    onSave = performSave,
                    saveLabel = if (isEditMode) "保存修改" else "完成"
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TextButton(onClick = {
                        focusManager.clearFocus()
                        showKeypad = true
                    }) {
                        Icon(Icons.Rounded.Calculate, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("计算器键盘")
                    }
                    Button(
                        onClick = performSave,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.Check, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isEditMode) "保存修改" else "保存记账")
                    }
                }
            }
        }
    }

    if (showMoreCurrencies) {
        CurrencyPickerDialog(
            selectedCode = origCurrency,
            pinnedCodes = pinnedCurrencies,
            onSelect = { code ->
                origCurrency = code
                manualAccountAmountMicros = null
                manualRateToAccountMicros = null
                manualToAmountMicros = null
            },
            onDismiss = { showMoreCurrencies = false }
        )
    }

    if (showDatePicker) {
        val zone = ZoneId.systemDefault()
        val currentLocalDateTime = Instant.ofEpochMilli(timestampMillis).atZone(zone).toLocalDateTime()
        val utcMidnight = currentLocalDateTime.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = utcMidnight)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val selectedUtc = datePickerState.selectedDateMillis
                        if (selectedUtc != null) {
                            val pickedDate = Instant.ofEpochMilli(selectedUtc).atZone(ZoneOffset.UTC).toLocalDate()
                            val combined = LocalDateTime.of(pickedDate, currentLocalDateTime.toLocalTime())
                            timestampMillis = combined.atZone(zone).toInstant().toEpochMilli()
                        }
                        showDatePicker = false
                    }
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("取消") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        val zone = ZoneId.systemDefault()
        val currentLocalDateTime = Instant.ofEpochMilli(timestampMillis).atZone(zone).toLocalDateTime()
        val timePickerState = rememberTimePickerState(
            initialHour = currentLocalDateTime.hour,
            initialMinute = currentLocalDateTime.minute,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("选择时间") },
            text = { TimePicker(state = timePickerState) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val combined = LocalDateTime.of(
                            currentLocalDateTime.toLocalDate(),
                            LocalTime.of(timePickerState.hour, timePickerState.minute)
                        )
                        timestampMillis = combined.atZone(zone).toInstant().toEpochMilli()
                        showTimePicker = false
                    }
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("取消") }
            }
        )
    }

    if (showDeleteConfirm && existingTransaction != null) {
        ConfirmActionDialog(
            title = "删除这笔记录？",
            message = "删除后将自动回滚关联账户余额，此操作不可撤销。",
            confirmText = "确认删除",
            isDestructive = true,
            onConfirm = {
                showDeleteConfirm = false
                viewModel.deleteTransaction(existingTransaction.id, onSuccess = onDismiss)
            },
            onDismiss = { showDeleteConfirm = false }
        )
    }
}

@Composable
private fun AccountSelectPill(
    account: AccountEntity,
    selected: Boolean,
    onClick: () -> Unit
) {
    val accountColor = Color(account.colorHex)
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        ),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = accountColor.copy(alpha = 0.18f),
                modifier = Modifier.size(24.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = KairoIcons.iconFor(account.icon),
                        contentDescription = null,
                        tint = accountColor,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            Column {
                Text(
                    text = account.name,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = account.currencyCode,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CategorySelectChip(
    category: CategoryEntity,
    selected: Boolean,
    compact: Boolean = false,
    onClick: () -> Unit
) {
    val catColor = Color(category.colorHex)
    Surface(
        shape = CircleShape,
        color = if (selected) catColor.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceContainerHigh,
        border = if (selected) BorderStroke(1.2.dp, catColor) else null,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = if (compact) 10.dp else 12.dp,
                vertical = if (compact) 6.dp else 7.dp
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = KairoIcons.iconFor(category.icon),
                contentDescription = null,
                tint = if (selected) catColor else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(if (compact) 14.dp else 16.dp)
            )
            Text(
                text = category.name,
                style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
                color = if (selected) catColor else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

@Composable
private fun CalculatorKeypad(
    onDigit: (String) -> Unit,
    onDot: () -> Unit,
    onOperator: (Char) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    onSave: () -> Unit,
    saveLabel: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CalcKey("7", Modifier.weight(1f)) { onDigit("7") }
            CalcKey("8", Modifier.weight(1f)) { onDigit("8") }
            CalcKey("9", Modifier.weight(1f)) { onDigit("9") }
            CalcIconKey(Icons.AutoMirrored.Rounded.Backspace, "退格", Modifier.weight(1f), onBackspace)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CalcKey("4", Modifier.weight(1f)) { onDigit("4") }
            CalcKey("5", Modifier.weight(1f)) { onDigit("5") }
            CalcKey("6", Modifier.weight(1f)) { onDigit("6") }
            CalcKey("+", Modifier.weight(1f), isOperator = true) { onOperator('+') }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CalcKey("1", Modifier.weight(1f)) { onDigit("1") }
            CalcKey("2", Modifier.weight(1f)) { onDigit("2") }
            CalcKey("3", Modifier.weight(1f)) { onDigit("3") }
            CalcKey("-", Modifier.weight(1f), isOperator = true) { onOperator('-') }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CalcKey("清零", Modifier.weight(1f), isSecondary = true, onClick = onClear)
            CalcKey("0", Modifier.weight(1f)) { onDigit("0") }
            CalcKey(".", Modifier.weight(1f), onClick = onDot)
            Button(
                onClick = onSave,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
            ) {
                Text(
                    text = saveLabel,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun CalcKey(
    label: String,
    modifier: Modifier = Modifier,
    isOperator: Boolean = false,
    isSecondary: Boolean = false,
    onClick: () -> Unit
) {
    val bg = when {
        isOperator -> MaterialTheme.colorScheme.secondaryContainer
        isSecondary -> MaterialTheme.colorScheme.surfaceContainerHigh
        else -> MaterialTheme.colorScheme.surfaceContainerLow
    }
    val fg = when {
        isOperator -> MaterialTheme.colorScheme.onSecondaryContainer
        isSecondary -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurface
    }
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = bg,
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = if (isSecondary) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleLarge.tabular(),
                color = fg,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun CalcIconKey(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
