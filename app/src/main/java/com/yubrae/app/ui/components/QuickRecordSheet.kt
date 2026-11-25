package com.yubrae.app.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CurrencyExchange
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yubrae.app.core.contract.ConversionPreview
import com.yubrae.app.core.contract.ParsedBillDraft
import com.yubrae.app.core.contract.RecordTransactionRequest
import com.yubrae.app.core.model.CurrencyCatalog
import com.yubrae.app.core.model.MoneyAmount
import com.yubrae.app.data.db.AccountEntity
import com.yubrae.app.data.db.CaptureChannel
import com.yubrae.app.data.db.CategoryEntity
import com.yubrae.app.data.db.LedgerEntity
import com.yubrae.app.data.db.ReimburseStatus
import com.yubrae.app.data.db.TagEntity
import com.yubrae.app.data.db.TagTextCodec
import com.yubrae.app.data.db.TransactionEntity
import com.yubrae.app.data.db.TransactionStatus
import com.yubrae.app.data.db.TransactionType
import com.yubrae.app.domain.RecordUsageRanker
import com.yubrae.app.ui.theme.KairoTheme
import com.yubrae.app.ui.theme.tabular
import com.yubrae.app.ui.viewmodel.KairoMainViewModel
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

private enum class RecordWorkspacePanel {
    CATEGORY,
    TAGS,
    ACCOUNT,
    DATE_TIME,
    MORE
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
    ledgers: List<LedgerEntity> = emptyList(),
    initialLedgerId: Long = 0L,
    refundOfTransaction: TransactionEntity? = null,
    initialDraft: ParsedBillDraft? = null,
    initialAttachmentPath: String? = null,
    llmEnabled: Boolean = false,
    onAnalyzeReceiptImage: (suspend (imagePath: String) -> Result<ParsedBillDraft?>)? = null,
    onOpenCategoryManager: (() -> Unit)? = null,
    availableTags: List<TagEntity> = emptyList(),
    onQuickCreateTag: ((name: String, colorHex: Long) -> Unit)? = null,
    onQuickCreateCategory: (suspend (CategoryEntity) -> Long?)? = null,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val semantic = KairoTheme.semantic
    val recentTransactions by viewModel.allTransactions.collectAsStateWithLifecycle()

    val isEditMode = existingTransaction != null
    val isRefundMode = !isEditMode && refundOfTransaction != null
    val stateKey = remember(
        existingTransaction?.id,
        refundOfTransaction?.id,
        initialDraft,
        initialAttachmentPath,
        initialAccountId,
        initialType,
        initialLedgerId
    ) {
        "${existingTransaction?.id}_${refundOfTransaction?.id}_${initialDraft?.hashCode()}_${initialAttachmentPath}_${initialAccountId}_${initialType}_$initialLedgerId"
    }

    val initialAccount = remember(stateKey, accounts) {
        when {
            existingTransaction != null -> accounts.firstOrNull { it.id == existingTransaction.accountId }
            refundOfTransaction != null -> accounts.firstOrNull { it.id == refundOfTransaction.accountId }
            initialDraft?.accountId != null -> accounts.firstOrNull { it.id == initialDraft.accountId }
            initialAccountId != null -> accounts.firstOrNull { it.id == initialAccountId }
            else -> null
        } ?: accounts.firstOrNull()
    }

    val resolvedInitialType = remember(stateKey) {
        when {
            existingTransaction != null -> existingTransaction.type
            refundOfTransaction != null -> TransactionType.INCOME
            initialDraft != null -> initialDraft.type
            else -> initialType
        }
    }

    var txType by remember(stateKey) { mutableStateOf(resolvedInitialType) }
    var activePanel by remember(stateKey) { mutableStateOf(RecordWorkspacePanel.CATEGORY) }

    var selectedAccountId by remember(stateKey) {
        mutableLongStateOf(
            existingTransaction?.accountId
                ?: refundOfTransaction?.accountId
                ?: initialDraft?.accountId
                ?: initialAccount?.id
                ?: 0L
        )
    }
    var toAccountId by remember(stateKey) {
        val srcId = existingTransaction?.accountId
            ?: refundOfTransaction?.accountId
            ?: initialDraft?.accountId
            ?: initialAccount?.id
        val initialTo = existingTransaction?.toAccountId
            ?: accounts.firstOrNull { it.id != srcId }?.id
        mutableStateOf(initialTo)
    }
    var origCurrency by remember(stateKey) {
        mutableStateOf(
            existingTransaction?.origCurrency
                ?: refundOfTransaction?.origCurrency
                ?: initialDraft?.origCurrency?.takeIf { it.isNotBlank() }
                ?: initialAccount?.currencyCode
                ?: baseCurrency
        )
    }

    LaunchedEffect(accounts, stateKey) {
        if (existingTransaction == null && selectedAccountId == 0L && accounts.isNotEmpty()) {
            val acc = accounts.firstOrNull {
                it.id == (refundOfTransaction?.accountId ?: initialDraft?.accountId ?: initialAccountId)
            } ?: accounts.first()
            selectedAccountId = acc.id
            if (refundOfTransaction == null && initialDraft == null) {
                origCurrency = acc.currencyCode
            }
            if (toAccountId == null || toAccountId == acc.id) {
                toAccountId = accounts.firstOrNull { it.id != acc.id }?.id
            }
        }
    }

    var amountExpr by remember(stateKey) {
        mutableStateOf(
            when {
                existingTransaction != null -> {
                    val digits = CurrencyCatalog.find(existingTransaction.origCurrency).minorDigits
                    MoneyAmount.formatPlain(existingTransaction.origAmountMicros, digits, grouping = false)
                }
                refundOfTransaction != null -> {
                    val digits = CurrencyCatalog.find(refundOfTransaction.origCurrency).minorDigits
                    MoneyAmount.formatPlain(refundOfTransaction.origAmountMicros, digits, grouping = false)
                }
                initialDraft != null && initialDraft.origAmountMicros > 0L -> {
                    val digits = CurrencyCatalog.find(initialDraft.origCurrency).minorDigits
                    MoneyAmount.formatPlain(initialDraft.origAmountMicros, digits, grouping = false)
                }
                else -> ""
            }
        )
    }

    var selectedCategoryId by remember(stateKey) {
        val defaultCat = when {
            existingTransaction != null -> existingTransaction.categoryId
            refundOfTransaction != null -> refundOfTransaction.categoryId
            initialDraft?.categoryId != null -> initialDraft.categoryId
            else -> categories.firstOrNull { it.type == resolvedInitialType && it.parentId == null }?.id
        }
        mutableStateOf(defaultCat)
    }
    LaunchedEffect(categories, txType) {
        if (selectedCategoryId == null && !isRefundMode && (txType == TransactionType.EXPENSE || txType == TransactionType.INCOME)) {
            selectedCategoryId = categories.firstOrNull { it.type == txType && it.parentId == null }?.id
        }
    }

    val selectableLedgers = remember(ledgers) {
        ledgers.filter { !it.archived }.sortedWith(compareBy({ it.sortOrder }, { it.id }))
    }
    val fallbackLedgerId = remember(ledgers, selectableLedgers) {
        selectableLedgers.firstOrNull()?.id ?: ledgers.firstOrNull()?.id ?: 0L
    }
    var selectedLedgerId by remember(stateKey) {
        val defaultLedger = existingTransaction?.ledgerId
            ?: refundOfTransaction?.ledgerId
            ?: initialLedgerId.takeIf { id -> id > 0L && selectableLedgers.any { it.id == id } }
            ?: fallbackLedgerId
        mutableLongStateOf(defaultLedger)
    }
    LaunchedEffect(ledgers, stateKey) {
        if (existingTransaction == null && refundOfTransaction == null) {
            val valid = selectedLedgerId > 0L && selectableLedgers.any { it.id == selectedLedgerId }
            if (!valid && fallbackLedgerId > 0L) {
                selectedLedgerId = fallbackLedgerId
            }
        }
    }

    var excludeFromStats by remember(stateKey) {
        mutableStateOf(existingTransaction?.excludeFromStats ?: false)
    }
    var excludeFromBudget by remember(stateKey) {
        mutableStateOf(existingTransaction?.excludeFromBudget ?: false)
    }
    var merchant by remember(stateKey) {
        mutableStateOf(
            existingTransaction?.merchant
                ?: refundOfTransaction?.merchant
                ?: initialDraft?.merchant
                ?: ""
        )
    }
    var note by remember(stateKey) {
        mutableStateOf(
            when {
                existingTransaction != null -> existingTransaction.note
                refundOfTransaction != null -> {
                    val refLabel = refundOfTransaction.merchant.ifBlank { "原单 #${refundOfTransaction.id}" }
                    "退款：$refLabel"
                }
                initialDraft != null -> initialDraft.note
                else -> ""
            }
        )
    }
    var tags by remember(stateKey) {
        mutableStateOf(
            TagTextCodec.format(
                TagTextCodec.parse(
                    existingTransaction?.tags
                        ?: initialDraft?.tags
                        ?: ""
                )
            )
        )
    }
    var reimburseStatus by remember(stateKey) {
        mutableStateOf(existingTransaction?.reimburseStatus ?: ReimburseStatus.NONE)
    }
    var timestampMillis by remember(stateKey) {
        mutableLongStateOf(
            existingTransaction?.timestamp
                ?: initialDraft?.timestamp
                ?: System.currentTimeMillis()
        )
    }

    var attachmentPaths by remember(stateKey) {
        val existingList = existingTransaction?.attachmentPaths
            ?.split(";")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()
        val initialList = listOfNotNull(initialAttachmentPath?.trim()?.takeIf { it.isNotEmpty() })
        mutableStateOf((existingList + initialList).distinct())
    }
    var previewImagePath by remember { mutableStateOf<String?>(null) }
    var isAnalyzingReceipt by remember { mutableStateOf(false) }

    // 跨币种换算手动覆写状态
    var manualAccountAmountMicros by remember(stateKey) {
        mutableStateOf(
            when {
                existingTransaction != null && existingTransaction.origCurrency != existingTransaction.accountCurrency ->
                    existingTransaction.accountAmountMicros
                initialDraft?.manualAccountAmountMicros != null ->
                    initialDraft.manualAccountAmountMicros
                else -> null
            }
        )
    }
    var manualRateToAccountMicros by remember(stateKey) { mutableStateOf<Long?>(null) }
    var accountAmountText by remember(stateKey) {
        mutableStateOf(
            if (existingTransaction != null && existingTransaction.origCurrency != existingTransaction.accountCurrency) {
                val digits = CurrencyCatalog.find(existingTransaction.accountCurrency).minorDigits
                MoneyAmount.formatPlain(existingTransaction.accountAmountMicros, digits, grouping = false)
            } else {
                ""
            }
        )
    }
    var rateText by remember(stateKey) {
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

    var manualToAmountMicros by remember(stateKey) {
        mutableStateOf(existingTransaction?.toAmountMicros)
    }
    var manualTransferRateMicros by remember(stateKey) { mutableStateOf<Long?>(null) }
    var transferToAmountText by remember(stateKey) {
        mutableStateOf(
            if (existingTransaction?.toAmountMicros != null && existingTransaction.toCurrency != null) {
                val digits = CurrencyCatalog.find(existingTransaction.toCurrency).minorDigits
                MoneyAmount.formatPlain(existingTransaction.toAmountMicros, digits, grouping = false)
            } else {
                ""
            }
        )
    }
    var transferRateText by remember(stateKey) { mutableStateOf("1.00") }
    var editingTransferToAmountField by remember { mutableStateOf(false) }
    var editingTransferRateField by remember { mutableStateOf(false) }

    var preview by remember { mutableStateOf<ConversionPreview?>(null) }
    var showKeypad by remember { mutableStateOf(true) }
    var showCurrencyPicker by remember { mutableStateOf(false) }
    var showLedgerPicker by remember { mutableStateOf(false) }
    var showRateOverrideDialog by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val handlePickedImageUri: (Uri?) -> Unit = { uri ->
        if (uri != null) {
            coroutineScope.launch {
                val savedPath = ReceiptImageUtils.savePickedImageToReceipts(context, uri)
                if (savedPath != null) {
                    attachmentPaths = (attachmentPaths + savedPath).distinct()
                } else {
                    viewModel.showMessage("小票图片保存失败，请重试")
                }
            }
        }
    }

    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = handlePickedImageUri
    )

    val fallbackImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = handlePickedImageUri
    )

    val openImagePicker: () -> Unit = {
        runCatching {
            pickImageLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }.onFailure {
            runCatching {
                fallbackImageLauncher.launch("image/*")
            }.onFailure {
                viewModel.showMessage("无法打开系统相册，请检查相册权限后重试")
            }
        }
    }

    val selectedAccount = remember(accounts, selectedAccountId) {
        accounts.firstOrNull { it.id == selectedAccountId }
    }
    val selectedToAccount = remember(accounts, toAccountId) {
        accounts.firstOrNull { it.id == toAccountId }
    }
    val parsedOrigMicros = remember(amountExpr) {
        QuickExprEval.evaluateToMicros(amountExpr) ?: 0L
    }

    val needsSourceConversion = selectedAccount != null &&
        !origCurrency.equals(selectedAccount.currencyCode, ignoreCase = true)
    val needsTransferConversion = txType == TransactionType.TRANSFER &&
        selectedToAccount != null &&
        (!selectedToAccount.currencyCode.equals(origCurrency, ignoreCase = true) ||
            (selectedAccount != null && !selectedToAccount.currencyCode.equals(selectedAccount.currencyCode, ignoreCase = true)))

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

    val selectedTagNames = remember(tags) { TagTextCodec.parse(tags) }
    val selectedLedger = remember(ledgers, selectableLedgers, selectedLedgerId) {
        selectableLedgers.firstOrNull { it.id == selectedLedgerId }
            ?: ledgers.firstOrNull { it.id == selectedLedgerId }
            ?: selectableLedgers.firstOrNull()
    }
    val selectedCategoryObj = remember(categories, selectedCategoryId) {
        categories.firstOrNull { it.id == selectedCategoryId }
    }
    val selectedCategoryParent = remember(categories, selectedCategoryObj) {
        selectedCategoryObj?.parentId?.let { pid -> categories.firstOrNull { it.id == pid } }
    }
    val currencyInfo = remember(origCurrency) { CurrencyCatalog.find(origCurrency) }
    val accentColor = transactionAmountColor(txType)

    val analyzeLastReceipt: () -> Unit = {
        val analyzer = onAnalyzeReceiptImage
        val targetPath = attachmentPaths.lastOrNull()
        if (analyzer != null && targetPath != null && !isAnalyzingReceipt) {
            isAnalyzingReceipt = true
            coroutineScope.launch {
                val res = runCatching { analyzer(targetPath) }.getOrElse { Result.failure(it) }
                isAnalyzingReceipt = false
                res.onSuccess { draft ->
                    if (draft != null) {
                        if (draft.type == TransactionType.EXPENSE ||
                            draft.type == TransactionType.INCOME ||
                            draft.type == TransactionType.TRANSFER
                        ) {
                            txType = draft.type
                        }
                        if (draft.origAmountMicros > 0L) {
                            val digits = CurrencyCatalog.find(draft.origCurrency).minorDigits
                            amountExpr = MoneyAmount.formatPlain(draft.origAmountMicros, digits, grouping = false)
                        }
                        if (draft.origCurrency.isNotBlank()) origCurrency = draft.origCurrency
                        if (draft.merchant.isNotBlank()) merchant = draft.merchant
                        if (draft.note.isNotBlank()) note = draft.note
                        if (draft.tags.isNotBlank()) tags = TagTextCodec.merge(tags, draft.tags)
                        if (draft.categoryId != null) selectedCategoryId = draft.categoryId
                        if (draft.accountId != null) selectedAccountId = draft.accountId
                        if (draft.timestamp > 0L) timestampMillis = draft.timestamp
                        if (draft.manualAccountAmountMicros != null) {
                            manualAccountAmountMicros = draft.manualAccountAmountMicros
                        }
                        viewModel.showMessage("AI 已识别小票并填入表单")
                    } else {
                        viewModel.showMessage(if (llmEnabled) "未识别出有效账单" else "请先开启 AI 配置")
                    }
                }.onFailure { err ->
                    viewModel.showMessage("小票识别失败：${err.message ?: "请检查 AI 配置"}")
                }
            }
        }
    }

    val saveWithMode: (Boolean) -> Unit = { continueNext ->
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
                    tags = TagTextCodec.format(TagTextCodec.parse(tags)),
                    reimburseStatus = if (txType == TransactionType.EXPENSE) reimburseStatus else ReimburseStatus.NONE,
                    sourceType = existingTransaction?.sourceType ?: CaptureChannel.MANUAL,
                    rawEventId = existingTransaction?.rawEventId,
                    status = TransactionStatus.CONFIRMED,
                    ledgerId = selectedLedgerId.takeIf { id -> id > 0L && ledgers.any { it.id == id } }
                        ?: viewModel.firstActiveLedgerId(ledgers)
                        ?: 0L,
                    excludeFromStats = excludeFromStats,
                    excludeFromBudget = excludeFromBudget,
                    refundOfTxId = existingTransaction?.refundOfTxId ?: refundOfTransaction?.id,
                    attachmentPaths = attachmentPaths.joinToString(";"),
                    reimbursedTxId = existingTransaction?.reimbursedTxId
                )
                viewModel.saveTransaction(
                    request = req,
                    onSuccess = {
                        if (continueNext) {
                            amountExpr = ""
                            merchant = ""
                            note = ""
                            tags = ""
                            reimburseStatus = ReimburseStatus.NONE
                            excludeFromStats = false
                            excludeFromBudget = false
                            attachmentPaths = emptyList()
                            manualAccountAmountMicros = null
                            manualRateToAccountMicros = null
                            manualToAmountMicros = null
                            manualTransferRateMicros = null
                            activePanel = RecordWorkspacePanel.CATEGORY
                            showKeypad = true
                            viewModel.showMessage("已记一笔，可继续记录下一笔")
                        } else {
                            onDismiss()
                        }
                    }
                )
            }
        }
    }

    val moreActiveCount = remember(merchant, reimburseStatus, excludeFromStats, excludeFromBudget, attachmentPaths) {
        var count = 0
        if (merchant.isNotBlank()) count++
        if (reimburseStatus != ReimburseStatus.NONE) count++
        if (excludeFromStats) count++
        if (excludeFromBudget) count++
        if (attachmentPaths.isNotEmpty()) count++
        count
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null,
        shape = YubraeSheetShape,
        containerColor = yubraeSheetContainerColor()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.90f)
                .navigationBarsPadding()
                .imePadding()
        ) {
            // 1. 顶栏：左侧(删除/账本) | 中央(支出/收入/转账) | 右侧(关闭)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                if (isEditMode) {
                    IconButton(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Delete,
                            contentDescription = "删除交易",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                } else if (selectableLedgers.size > 1 && selectedLedger != null) {
                    val ledgerColor = Color(selectedLedger.colorHex)
                    Surface(
                        shape = CircleShape,
                        color = ledgerColor.copy(alpha = 0.12f),
                        border = BorderStroke(0.6.dp, ledgerColor.copy(alpha = 0.3f)),
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .height(28.dp)
                            .clip(CircleShape)
                            .clickable { showLedgerPicker = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = KairoIcons.ledgerIcon(selectedLedger.icon),
                                contentDescription = null,
                                tint = ledgerColor,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = selectedLedger.name,
                                style = MaterialTheme.typography.labelSmall,
                                color = ledgerColor,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 72.dp)
                            )
                        }
                    }
                }

                if (txType == TransactionType.ADJUST) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.align(Alignment.Center)
                    ) {
                        Text(
                            text = "余额校准记录",
                            style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .padding(2.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
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
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable {
                                        txType = mode
                                        if (mode != TransactionType.TRANSFER) {
                                            val matchingCat = categories.firstOrNull {
                                                it.id == selectedCategoryId && it.type == mode
                                            }
                                            if (matchingCat == null && !isRefundMode) {
                                                selectedCategoryId = categories.firstOrNull {
                                                    it.type == mode && it.parentId == null
                                                }?.id
                                            }
                                        } else if (toAccountId == null || toAccountId == selectedAccountId) {
                                            toAccountId = accounts.firstOrNull { it.id != selectedAccountId }?.id
                                        }
                                        if (activePanel != RecordWorkspacePanel.CATEGORY) {
                                            activePanel = RecordWorkspacePanel.CATEGORY
                                        }
                                    }
                            ) {
                                Text(
                                    text = label,
                                    color = textColor,
                                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.5.sp),
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "关闭",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }

            // 退款模式提示条
            if (isRefundMode) {
                val origCatName = refundOfTransaction.categoryId?.let { id ->
                    categories.firstOrNull { it.id == id }?.name
                }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Replay,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(15.dp)
                        )
                        val origSummary = buildString {
                            append("退款 · ")
                            append(
                                MoneyAmount.format(
                                    refundOfTransaction.origAmountMicros,
                                    refundOfTransaction.origCurrency,
                                    showCurrencyCode = true
                                )
                            )
                            if (!origCatName.isNullOrBlank()) append(" · $origCatName")
                            if (refundOfTransaction.merchant.isNotBlank()) append(" · ${refundOfTransaction.merchant}")
                        }
                        Text(
                            text = origSummary,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // 2. 独立大金额 & 当前分类摘要带（彻底告别三合一拥挤行）
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // 左侧：当前分类或转账流向徽章（点击随时回到分类视图）
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { activePanel = RecordWorkspacePanel.CATEGORY }
                                .padding(vertical = 2.dp, horizontal = 2.dp)
                        ) {
                            when (txType) {
                                TransactionType.TRANSFER -> {
                                    CartoonAccountIcon(
                                        iconKey = selectedAccount?.icon ?: "wallet",
                                        accountName = selectedAccount?.name.orEmpty(),
                                        colorHex = selectedAccount?.colorHex ?: 0xFF2AA876L,
                                        size = 34.dp
                                    )
                                    Column {
                                        Text(
                                            text = "${selectedAccount?.name ?: "转出"} → ${selectedToAccount?.name ?: "转入"}",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "账户互转",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                TransactionType.ADJUST -> {
                                    CartoonAccountIcon(
                                        iconKey = selectedAccount?.icon ?: "wallet",
                                        accountName = selectedAccount?.name.orEmpty(),
                                        colorHex = selectedAccount?.colorHex ?: 0xFF2AA876L,
                                        size = 34.dp
                                    )
                                    Column {
                                        Text(
                                            text = selectedAccount?.name ?: "余额校准",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1
                                        )
                                        Text(
                                            text = "校准差额",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                else -> {
                                    val cat = selectedCategoryObj
                                    CartoonCategoryIcon(
                                        iconKey = cat?.icon ?: "category",
                                        size = 34.dp,
                                        fallbackColorHex = cat?.colorHex ?: 0xFF2AA876L
                                    )
                                    Column {
                                        Text(
                                            text = cat?.name ?: "选分类",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (selectedCategoryParent != null) {
                                            Text(
                                                text = selectedCategoryParent.name,
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        // 右侧：紧凑币种胶囊 + 醒目大号数字金额
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                                modifier = Modifier
                                    .height(26.dp)
                                    .clip(CircleShape)
                                    .clickable { showCurrencyPicker = true }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(text = currencyInfo.flag, fontSize = 11.sp)
                                    Text(
                                        text = currencyInfo.code,
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                        fontWeight = FontWeight.Bold
                                    )
                                    Icon(
                                        imageVector = Icons.Rounded.KeyboardArrowDown,
                                        contentDescription = "切换币种",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }

                            val amountScroll = rememberScrollState()
                            LaunchedEffect(amountExpr) {
                                amountScroll.scrollTo(amountScroll.maxValue)
                            }
                            Column(
                                horizontalAlignment = Alignment.End,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        focusManager.clearFocus()
                                        showKeypad = true
                                    }
                                    .padding(horizontal = 2.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .widthIn(max = 178.dp)
                                        .horizontalScroll(amountScroll)
                                ) {
                                    Text(
                                        text = "${currencyInfo.symbol}${amountExpr.ifEmpty { "0.00" }}",
                                        style = MaterialTheme.typography.headlineMedium.copy(
                                            fontSize = 26.sp,
                                            lineHeight = 30.sp,
                                            fontWeight = FontWeight.Bold
                                        ).tabular(),
                                        color = if (amountExpr.isEmpty()) accentColor.copy(alpha = 0.42f) else accentColor,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                                if (QuickExprEval.containsOperator(amountExpr) && parsedOrigMicros > 0L) {
                                    Text(
                                        text = "= ${MoneyAmount.format(parsedOrigMicros, origCurrency, showCurrencyCode = false)}",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp).tabular(),
                                        color = accentColor.copy(alpha = 0.85f),
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }

                    // 跨币种汇率折算微条
                    val baseHintText = preview
                        ?.takeIf { !origCurrency.equals(baseCurrency, ignoreCase = true) && parsedOrigMicros > 0L }
                        ?.let { "折算本币 ≈ ${MoneyAmount.format(it.baseAmountMicros, baseCurrency, showCurrencyCode = true)}" }
                    if (needsSourceConversion || needsTransferConversion || baseHintText != null) {
                        val canEditRate = needsSourceConversion || needsTransferConversion
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f))
                                .then(if (canEditRate) Modifier.clickable { showRateOverrideDialog = true } else Modifier)
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CurrencyExchange,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(13.dp)
                            )
                            val conversionSummary = buildString {
                                if (needsSourceConversion) {
                                    val act = if (txType == TransactionType.INCOME) "实入" else "实扣"
                                    append("$act ${accountAmountText.ifBlank { "0" }} ${selectedAccount.currencyCode} · 汇率 $rateText")
                                } else if (needsTransferConversion) {
                                    append("到账 ${transferToAmountText.ifBlank { "0" }} ${selectedToAccount.currencyCode} · 汇率 $transferRateText")
                                } else if (baseHintText != null) {
                                    append(baseHintText)
                                }
                            }
                            Text(
                                text = conversionSummary,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp).tabular(),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            if (canEditRate) {
                                val isManual = manualAccountAmountMicros != null ||
                                    manualRateToAccountMicros != null ||
                                    manualToAmountMicros != null ||
                                    manualTransferRateMicros != null
                                Text(
                                    text = if (isManual) "已自定义" else "调汇率",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            // 3. 大视野主工作区（根据屏幕余量自适应撑开，流畅切换 5 大面板）
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                AnimatedContent(
                    targetState = activePanel,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "quick_record_workspace_panel"
                ) { panel ->
                    when (panel) {
                        RecordWorkspacePanel.CATEGORY -> {
                            when (txType) {
                                TransactionType.EXPENSE, TransactionType.INCOME -> {
                                    CategoryExplorerPanel(
                                        txType = txType,
                                        categories = categories,
                                        selectedCategoryId = selectedCategoryId,
                                        recentTransactions = recentTransactions,
                                        onSelectCategory = { selectedCategoryId = it },
                                        onCreateCategory = { entity ->
                                            onQuickCreateCategory?.invoke(entity)
                                                ?: viewModel.createCategoryAndReturnId(entity)
                                        },
                                        onOpenCategoryManager = onOpenCategoryManager,
                                        onRequestImeFocus = { showKeypad = false },
                                        onReleaseImeFocus = { },
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                                TransactionType.TRANSFER -> {
                                    TransferModePanel(
                                        accounts = accounts,
                                        selectedAccount = selectedAccount,
                                        selectedToAccount = selectedToAccount,
                                        onSelectFrom = { acc ->
                                            selectedAccountId = acc.id
                                            origCurrency = acc.currencyCode
                                            manualAccountAmountMicros = null
                                            manualRateToAccountMicros = null
                                            manualToAmountMicros = null
                                            if (toAccountId == acc.id) {
                                                toAccountId = accounts.firstOrNull { it.id != acc.id }?.id
                                            }
                                        },
                                        onSelectTo = { acc ->
                                            toAccountId = acc.id
                                            manualToAmountMicros = null
                                        },
                                        onSwap = {
                                            val oldFrom = selectedAccount
                                            val oldTo = selectedToAccount
                                            if (oldFrom != null && oldTo != null) {
                                                selectedAccountId = oldTo.id
                                                toAccountId = oldFrom.id
                                                origCurrency = oldTo.currencyCode
                                                manualAccountAmountMicros = null
                                                manualRateToAccountMicros = null
                                                manualToAmountMicros = null
                                            }
                                        },
                                        modifier = Modifier.verticalScroll(rememberScrollState())
                                    )
                                }
                                else -> {
                                    YimuCard(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                        cornerRadius = 14.dp
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(14.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = "余额校准",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = "${selectedAccount?.name ?: "未选择"} · ${selectedAccount?.currencyCode ?: origCurrency}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        RecordWorkspacePanel.TAGS -> {
                            GroupedTagPickerPanel(
                                selectedTagNames = selectedTagNames,
                                availableTags = availableTags,
                                allTransactions = recentTransactions,
                                onToggleTag = { tagName ->
                                    val updated = if (tagName in selectedTagNames) {
                                        selectedTagNames.filterNot { it == tagName }
                                    } else {
                                        selectedTagNames + tagName
                                    }
                                    tags = TagTextCodec.format(updated)
                                },
                                onClearTags = { tags = "" },
                                onQuickCreateTag = { name, groupName, colorHex, iconKey ->
                                    tags = TagTextCodec.merge(tags, name)
                                    val nextSort = (availableTags.maxOfOrNull { it.sortOrder } ?: 280) + 10
                                    if (onQuickCreateTag != null && groupName == "自定义" && iconKey.isBlank()) {
                                        onQuickCreateTag.invoke(name, colorHex)
                                    } else {
                                        viewModel.createTagSuspend(
                                            TagEntity(
                                                name = name,
                                                groupName = groupName,
                                                colorHex = colorHex,
                                                sortOrder = nextSort,
                                                isSystem = false,
                                                icon = iconKey
                                            )
                                        )
                                    }
                                },
                                onDone = { activePanel = RecordWorkspacePanel.CATEGORY },
                                onRequestImeFocus = { showKeypad = false },
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        RecordWorkspacePanel.ACCOUNT -> {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                PanelHeaderRow(
                                    title = if (txType == TransactionType.TRANSFER) "选择转出账户" else "选择结算账户",
                                    onDone = { activePanel = RecordWorkspacePanel.CATEGORY }
                                )
                                GroupedAccountPickerPanel(
                                    accounts = accounts,
                                    selectedAccountId = selectedAccountId,
                                    onSelect = { acc ->
                                        selectedAccountId = acc.id
                                        origCurrency = acc.currencyCode
                                        manualAccountAmountMicros = null
                                        manualRateToAccountMicros = null
                                        manualToAmountMicros = null
                                        if (txType == TransactionType.TRANSFER && toAccountId == acc.id) {
                                            toAccountId = accounts.firstOrNull { it.id != acc.id }?.id
                                        }
                                        activePanel = RecordWorkspacePanel.CATEGORY
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        RecordWorkspacePanel.DATE_TIME -> {
                            DateTimeQuickPanel(
                                timestampMillis = timestampMillis,
                                onUpdateTimestamp = { timestampMillis = it },
                                onOpenDatePicker = { showDatePicker = true },
                                onOpenTimePicker = { showTimePicker = true },
                                onDone = { activePanel = RecordWorkspacePanel.CATEGORY },
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        RecordWorkspacePanel.MORE -> {
                            MoreAttributesPanel(
                                txType = txType,
                                merchant = merchant,
                                onMerchantChange = { merchant = it },
                                reimburseStatus = reimburseStatus,
                                onReimburseChange = { reimburseStatus = it },
                                excludeFromStats = excludeFromStats,
                                onExcludeStatsChange = { excludeFromStats = it },
                                excludeFromBudget = excludeFromBudget,
                                onExcludeBudgetChange = { excludeFromBudget = it },
                                selectableLedgers = selectableLedgers,
                                selectedLedgerId = selectedLedgerId,
                                onOpenLedgerPicker = { showLedgerPicker = true },
                                attachmentPaths = attachmentPaths,
                                isAnalyzingReceipt = isAnalyzingReceipt,
                                onAddAttachment = openImagePicker,
                                onRemoveAttachment = { path ->
                                    attachmentPaths = attachmentPaths.filterNot { it == path }
                                },
                                onPreviewAttachment = { previewImagePath = it },
                                onAnalyzeReceipt = if (onAnalyzeReceiptImage != null) analyzeLastReceipt else null,
                                onRequestImeFocus = { showKeypad = false },
                                onDone = { activePanel = RecordWorkspacePanel.CATEGORY },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }

            // 4. 底部高频工作台（仅保留 4 颗高价值属性胶囊 + 独立宽敞备注行 + 舒适键盘）
            CompactHairlineDivider(startIndent = 0.dp)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                WorkbenchAttrChip(
                    icon = Icons.Rounded.AccountBalanceWallet,
                    customLeading = selectedAccount?.let { acc ->
                        {
                            CartoonAccountIcon(
                                iconKey = acc.icon,
                                accountName = acc.name,
                                colorHex = acc.colorHex,
                                size = 16.dp
                            )
                        }
                    },
                    label = selectedAccount?.name ?: "选账户",
                    selected = activePanel == RecordWorkspacePanel.ACCOUNT || selectedAccount != null,
                    accentColor = selectedAccount?.let { Color(it.colorHex) } ?: MaterialTheme.colorScheme.error,
                    onClick = {
                        activePanel = if (activePanel == RecordWorkspacePanel.ACCOUNT) {
                            RecordWorkspacePanel.CATEGORY
                        } else {
                            RecordWorkspacePanel.ACCOUNT
                        }
                    },
                    modifier = Modifier.weight(1.1f)
                )

                WorkbenchAttrChip(
                    icon = Icons.Rounded.Schedule,
                    label = formatSmartDateTimeChip(timestampMillis),
                    selected = activePanel == RecordWorkspacePanel.DATE_TIME,
                    accentColor = MaterialTheme.colorScheme.primary,
                    onClick = {
                        activePanel = if (activePanel == RecordWorkspacePanel.DATE_TIME) {
                            RecordWorkspacePanel.CATEGORY
                        } else {
                            RecordWorkspacePanel.DATE_TIME
                        }
                    },
                    modifier = Modifier.weight(1.15f)
                )

                WorkbenchAttrChip(
                    icon = Icons.AutoMirrored.Rounded.Label,
                    label = if (selectedTagNames.isEmpty()) "标签" else "标签(${selectedTagNames.size})",
                    selected = activePanel == RecordWorkspacePanel.TAGS || selectedTagNames.isNotEmpty(),
                    accentColor = MaterialTheme.colorScheme.primary,
                    onClick = {
                        activePanel = if (activePanel == RecordWorkspacePanel.TAGS) {
                            RecordWorkspacePanel.CATEGORY
                        } else {
                            RecordWorkspacePanel.TAGS
                        }
                    },
                    modifier = Modifier.weight(0.95f)
                )

                WorkbenchAttrChip(
                    icon = Icons.Rounded.Tune,
                    label = if (moreActiveCount == 0) "更多" else "更多($moreActiveCount)",
                    selected = activePanel == RecordWorkspacePanel.MORE || moreActiveCount > 0,
                    accentColor = MaterialTheme.colorScheme.primary,
                    onClick = {
                        activePanel = if (activePanel == RecordWorkspacePanel.MORE) {
                            RecordWorkspacePanel.CATEGORY
                        } else {
                            RecordWorkspacePanel.MORE
                        }
                    },
                    modifier = Modifier.weight(0.9f)
                )
            }

            // 已选标签 / 票据轻量摘要带（仅当非标签页且存在内容时展示，点标签直达标签页）
            AnimatedVisibility(
                visible = (activePanel != RecordWorkspacePanel.TAGS && selectedTagNames.isNotEmpty()) ||
                    (activePanel != RecordWorkspacePanel.MORE && attachmentPaths.isNotEmpty())
            ) {
                val tagColorMap = remember(availableTags) {
                    availableTags.associate { it.name to it.colorHex }
                }
                val tagIconMap = remember(availableTags) {
                    availableTags.associate { it.name to it.icon }
                }
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (activePanel != RecordWorkspacePanel.TAGS) {
                        items(selectedTagNames, key = { "sum_tag_$it" }) { tagName ->
                            val colorHex = tagColorMap[tagName] ?: RecordUsageRanker.stableFallbackColorHex(tagName)
                            TagBadgeChip(
                                name = tagName,
                                colorHex = colorHex,
                                selected = true,
                                compact = true,
                                onClick = { activePanel = RecordWorkspacePanel.TAGS },
                                iconKey = tagIconMap[tagName].orEmpty()
                            )
                        }
                    }

                    items(attachmentPaths, key = { "sum_img_$it" }) { path ->
                        Box(modifier = Modifier.size(26.dp)) {
                            ReceiptThumbnailImage(
                                path = path,
                                maxSizePx = 140,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { previewImagePath = path }
                            )
                        }
                    }

                    if (attachmentPaths.isNotEmpty() && onAnalyzeReceiptImage != null) {
                        item(key = "sum_ai_scan") {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier
                                    .height(24.dp)
                                    .clip(CircleShape)
                                    .clickable(enabled = !isAnalyzingReceipt, onClick = analyzeLastReceipt)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    if (isAnalyzingReceipt) {
                                        CircularProgressIndicator(modifier = Modifier.size(10.dp), strokeWidth = 1.5.dp)
                                    } else {
                                        Icon(
                                            imageVector = Icons.Rounded.AutoAwesome,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(11.dp)
                                        )
                                    }
                                    Text(
                                        text = if (isAnalyzingReceipt) "识别中" else "AI识图",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 独立宽敞备注行 + 票据快捷图标
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (note.isEmpty()) {
                                Text(
                                    text = if (merchant.isNotBlank()) "备注（商户：$merchant）" else "添加备注…",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            BasicTextField(
                                value = note,
                                onValueChange = { note = it },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(
                                    onDone = {
                                        focusManager.clearFocus()
                                        showKeypad = true
                                    }
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onFocusChanged { if (it.isFocused) showKeypad = false }
                            )
                        }
                        if (note.isNotEmpty()) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "清空备注",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .size(15.dp)
                                    .clip(CircleShape)
                                    .clickable { note = "" }
                            )
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (attachmentPaths.isNotEmpty()) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f)
                    },
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = openImagePicker)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.PhotoLibrary,
                            contentDescription = "小票图片",
                            tint = if (attachmentPaths.isNotEmpty()) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // 5. 舒适触控计算器键盘 / 完成栏
            CompactHairlineDivider(startIndent = 0.dp)

            if (showKeypad) {
                CompactCalculatorKeypad(
                    hasOperator = QuickExprEval.containsOperator(amountExpr),
                    onDigit = { d -> amountExpr = QuickExprEval.appendDigit(amountExpr, d) },
                    onDot = { amountExpr = QuickExprEval.appendDot(amountExpr) },
                    onOperator = { op -> amountExpr = QuickExprEval.appendOperator(amountExpr, op) },
                    onEquals = {
                        if (QuickExprEval.containsOperator(amountExpr)) {
                            val evaluated = QuickExprEval.evaluateAndFormat(
                                amountExpr,
                                CurrencyCatalog.find(origCurrency).minorDigits
                            )
                            if (evaluated != null) {
                                amountExpr = evaluated
                            } else {
                                viewModel.showMessage("算式格式有误")
                            }
                        }
                    },
                    onBackspace = {
                        if (amountExpr.isNotEmpty()) amountExpr = amountExpr.dropLast(1)
                    },
                    onClear = { amountExpr = "" },
                    onHideKeypad = { showKeypad = false },
                    onSave = { saveWithMode(false) },
                    saveLabel = if (isEditMode) "保存" else "完成",
                    showSaveAndContinue = !isEditMode && !isRefundMode,
                    onSaveAndContinue = { saveWithMode(true) },
                    keyHeight = 44.dp
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            focusManager.clearFocus()
                            showKeypad = true
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Rounded.Calculate, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("键盘", style = MaterialTheme.typography.labelLarge)
                    }
                    if (!isEditMode && !isRefundMode) {
                        FilledTonalButton(
                            onClick = { saveWithMode(true) },
                            shape = RoundedCornerShape(11.dp),
                            modifier = Modifier.height(42.dp)
                        ) {
                            Text("再记一笔", fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Button(
                        onClick = { saveWithMode(false) },
                        shape = RoundedCornerShape(11.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                    ) {
                        Icon(Icons.Rounded.Check, null, modifier = Modifier.size(17.dp))
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = if (isEditMode) "保存修改" else "完成记账",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }

    previewImagePath?.let { path ->
        ReceiptZoomPreviewDialog(
            imagePath = path,
            onDismiss = { previewImagePath = null }
        )
    }

    if (showCurrencyPicker) {
        CurrencyPickerDialog(
            selectedCode = origCurrency,
            pinnedCodes = pinnedCurrencies,
            onSelect = { code ->
                origCurrency = code
                manualAccountAmountMicros = null
                manualRateToAccountMicros = null
                manualToAmountMicros = null
            },
            onDismiss = { showCurrencyPicker = false }
        )
    }

    if (showLedgerPicker) {
        LedgerPickerDialog(
            ledgers = selectableLedgers,
            selectedLedgerId = selectedLedgerId,
            onSelect = { ledger ->
                selectedLedgerId = ledger.id
                showLedgerPicker = false
            },
            onDismiss = { showLedgerPicker = false }
        )
    }

    if (showRateOverrideDialog && (needsSourceConversion || needsTransferConversion)) {
        ExchangeRateOverrideDialog(
            origCurrency = origCurrency,
            txType = txType,
            selectedAccount = selectedAccount,
            selectedToAccount = selectedToAccount,
            needsSourceConversion = needsSourceConversion,
            needsTransferConversion = needsTransferConversion,
            accountAmountText = accountAmountText,
            rateText = rateText,
            transferToAmountText = transferToAmountText,
            transferRateText = transferRateText,
            rateSource = preview?.rateSource,
            hasManualOverride = manualAccountAmountMicros != null ||
                manualRateToAccountMicros != null ||
                manualToAmountMicros != null ||
                manualTransferRateMicros != null,
            onAccountAmountChange = { input ->
                accountAmountText = input
                val parsed = MoneyAmount.parseToMicros(input)
                if (parsed != null && parsed > 0L) {
                    manualAccountAmountMicros = parsed
                    manualRateToAccountMicros = null
                }
            },
            onRateChange = { input ->
                rateText = input
                val parsedRate = MoneyAmount.parseRateToMicros(input)
                if (parsedRate != null && parsedRate > 0L) {
                    manualRateToAccountMicros = parsedRate
                    manualAccountAmountMicros = null
                }
            },
            onTransferToAmountChange = { input ->
                transferToAmountText = input
                val parsed = MoneyAmount.parseToMicros(input)
                if (parsed != null && parsed > 0L) {
                    manualToAmountMicros = parsed
                    manualTransferRateMicros = null
                }
            },
            onTransferRateChange = { input ->
                transferRateText = input
                val parsedRate = MoneyAmount.parseRateToMicros(input)
                if (parsedRate != null && parsedRate > 0L) {
                    manualTransferRateMicros = parsedRate
                    manualToAmountMicros = null
                }
            },
            onAccountAmountFocusChange = { editingAccountAmountField = it },
            onRateFocusChange = { editingRateField = it },
            onTransferToAmountFocusChange = { editingTransferToAmountField = it },
            onTransferRateFocusChange = { editingTransferRateField = it },
            onReset = {
                manualAccountAmountMicros = null
                manualRateToAccountMicros = null
                manualToAmountMicros = null
                manualTransferRateMicros = null
                editingAccountAmountField = false
                editingRateField = false
                editingTransferToAmountField = false
                editingTransferRateField = false
            },
            onDismiss = {
                editingAccountAmountField = false
                editingRateField = false
                editingTransferToAmountField = false
                editingTransferRateField = false
                showRateOverrideDialog = false
            }
        )
    }

    if (showDatePicker) {
        val zone = ZoneId.systemDefault()
        val currentLocalDateTime = Instant.ofEpochMilli(timestampMillis).atZone(zone).toLocalDateTime()
        val utcMidnight = currentLocalDateTime.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = utcMidnight)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            shape = YubraeDialogShape,
            colors = DatePickerDefaults.colors(containerColor = yubraeDialogContainerColor()),
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
            DatePicker(
                state = datePickerState,
                colors = DatePickerDefaults.colors(containerColor = yubraeDialogContainerColor())
            )
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
        YubraeDialog(
            onDismissRequest = { showTimePicker = false },
            title = "选择时间",
            icon = Icons.Rounded.Schedule,
            confirmButton = {
                Button(
                    onClick = {
                        val combined = LocalDateTime.of(
                            currentLocalDateTime.toLocalDate(),
                            LocalTime.of(timePickerState.hour, timePickerState.minute)
                        )
                        timestampMillis = combined.atZone(zone).toInstant().toEpochMilli()
                        showTimePicker = false
                    },
                    shape = RoundedCornerShape(12.dp)
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(
                    onClick = { showTimePicker = false },
                    shape = RoundedCornerShape(12.dp)
                ) { Text("取消") }
            }
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                TimePicker(state = timePickerState)
            }
        }
    }

    if (showDeleteConfirm && existingTransaction != null) {
        ConfirmActionDialog(
            title = "删除这笔记录？",
            message = "删除后自动回滚账户余额，不可恢复。",
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
private fun PanelHeaderRow(
    title: String,
    onDone: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier
                .clip(CircleShape)
                .clickable(onClick = onDone)
        ) {
            Text(
                text = "返回分类",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DateTimeQuickPanel(
    timestampMillis: Long,
    onUpdateTimestamp: (Long) -> Unit,
    onOpenDatePicker: () -> Unit,
    onOpenTimePicker: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val zone = ZoneId.systemDefault()
    val currentTime = remember(timestampMillis) {
        Instant.ofEpochMilli(timestampMillis).atZone(zone).toLocalDateTime().toLocalTime()
    }
    val today = remember { LocalDate.now(zone) }

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        PanelHeaderRow(title = "日期与时间", onDone = onDone)

        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "当前记账时间",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = KairoFormats.formatDateTime(timestampMillis),
                    style = MaterialTheme.typography.titleLarge.tabular(),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Text(
            text = "快捷切换",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val presets = listOf(
                "此刻" to { System.currentTimeMillis() },
                "今天" to { LocalDateTime.of(today, currentTime).atZone(zone).toInstant().toEpochMilli() },
                "昨天" to { LocalDateTime.of(today.minusDays(1), currentTime).atZone(zone).toInstant().toEpochMilli() },
                "前天" to { LocalDateTime.of(today.minusDays(2), currentTime).atZone(zone).toInstant().toEpochMilli() }
            )
            presets.forEach { (label, resolver) ->
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { onUpdateTimestamp(resolver()) }
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FilledTonalButton(
                onClick = onOpenDatePicker,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Rounded.CalendarMonth, null, modifier = Modifier.size(17.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("自选日期")
            }
            FilledTonalButton(
                onClick = onOpenTimePicker,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Rounded.Schedule, null, modifier = Modifier.size(17.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("自选时间")
            }
        }
    }
}

@Composable
private fun MoreAttributesPanel(
    txType: String,
    merchant: String,
    onMerchantChange: (String) -> Unit,
    reimburseStatus: String,
    onReimburseChange: (String) -> Unit,
    excludeFromStats: Boolean,
    onExcludeStatsChange: (Boolean) -> Unit,
    excludeFromBudget: Boolean,
    onExcludeBudgetChange: (Boolean) -> Unit,
    selectableLedgers: List<LedgerEntity>,
    selectedLedgerId: Long,
    onOpenLedgerPicker: () -> Unit,
    attachmentPaths: List<String>,
    isAnalyzingReceipt: Boolean,
    onAddAttachment: () -> Unit,
    onRemoveAttachment: (String) -> Unit,
    onPreviewAttachment: (String) -> Unit,
    onAnalyzeReceipt: (() -> Unit)?,
    onRequestImeFocus: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        PanelHeaderRow(title = "商户、报销与高级设置", onDone = onDone)

        // 商户直输（无需二级 Dialog）
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "商户或交易对象",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            InlineSearchInputBar(
                query = merchant,
                onQueryChange = onMerchantChange,
                placeholder = "如：星巴克、京东超市、房东",
                leadingIcon = Icons.Rounded.Storefront,
                onFocusGained = onRequestImeFocus,
                onDone = { focusManager.clearFocus() },
                height = 38.dp
            )
        }

        if (txType == TransactionType.EXPENSE) {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    text = "报销状态",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        ReimburseStatus.NONE to "不报销",
                        ReimburseStatus.PENDING to "待报销",
                        ReimburseStatus.REIMBURSED to "已报销"
                    ).forEach { (code, label) ->
                        val selected = reimburseStatus == code
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (selected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerLow
                            },
                            border = BorderStroke(
                                width = if (selected) 1.dp else 0.5.dp,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                                }
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onReimburseChange(code) }
                        ) {
                            Box(
                                modifier = Modifier.padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
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
            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                CompactSwitchRow(
                    title = "不计入收支统计",
                    detail = "常见于转账垫付、内部资金调拨",
                    checked = excludeFromStats,
                    onCheckedChange = onExcludeStatsChange
                )
                CompactHairlineDivider(startIndent = 0.dp)
                CompactSwitchRow(
                    title = "不计入预算消耗",
                    detail = "保留账单明细，但不扣减月度预算",
                    checked = excludeFromBudget,
                    onCheckedChange = onExcludeBudgetChange
                )
            }
        }

        if (selectableLedgers.size > 1) {
            val currentLedger = selectableLedgers.firstOrNull { it.id == selectedLedgerId }
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onOpenLedgerPicker)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = currentLedger?.let { KairoIcons.ledgerIcon(it.icon) }
                            ?: Icons.AutoMirrored.Rounded.MenuBook,
                        contentDescription = null,
                        tint = currentLedger?.let { Color(it.colorHex) } ?: MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "所属账本",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = currentLedger?.name ?: "默认账本",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // 小票附件与 AI 识图管理
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "票据照片 (${attachmentPaths.size})",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (attachmentPaths.isNotEmpty() && onAnalyzeReceipt != null) {
                        FilledTonalButton(
                            onClick = onAnalyzeReceipt,
                            enabled = !isAnalyzingReceipt,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            if (isAnalyzingReceipt) {
                                CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.6.dp)
                            } else {
                                Icon(Icons.Rounded.AutoAwesome, null, modifier = Modifier.size(13.dp))
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (isAnalyzingReceipt) "识别中" else "AI识图填单", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    FilledTonalButton(
                        onClick = onAddAttachment,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Icon(Icons.Rounded.Add, null, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("添加图片", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            if (attachmentPaths.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(attachmentPaths, key = { "more_img_$it" }) { path ->
                        Box(modifier = Modifier.size(56.dp)) {
                            ReceiptThumbnailImage(
                                path = path,
                                maxSizePx = 220,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { onPreviewAttachment(path) }
                            )
                            Surface(
                                shape = CircleShape,
                                color = Color.Black.copy(alpha = 0.65f),
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(2.dp)
                                    .size(16.dp)
                                    .clickable { onRemoveAttachment(path) }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "移除图片",
                                        tint = Color.White,
                                        modifier = Modifier.size(11.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TransferModePanel(
    accounts: List<AccountEntity>,
    selectedAccount: AccountEntity?,
    selectedToAccount: AccountEntity?,
    onSelectFrom: (AccountEntity) -> Unit,
    onSelectTo: (AccountEntity) -> Unit,
    onSwap: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TransferSummaryTile(
                title = "转出账户",
                account = selectedAccount,
                modifier = Modifier.weight(1f)
            )
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable(enabled = selectedAccount != null && selectedToAccount != null, onClick = onSwap)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.SwapHoriz,
                        contentDescription = "互换账户",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
            TransferSummaryTile(
                title = "转入账户",
                account = selectedToAccount,
                modifier = Modifier.weight(1f)
            )
        }

        if (accounts.size < 2) {
            Text(
                text = "至少需要 2 个账户才能记录转账",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        } else {
            TransferAccountRow(
                label = "选择转出",
                accounts = accounts,
                selectedId = selectedAccount?.id,
                onSelect = onSelectFrom
            )
            TransferAccountRow(
                label = "选择转入",
                accounts = accounts.filter { it.id != selectedAccount?.id },
                selectedId = selectedToAccount?.id,
                onSelect = onSelectTo
            )
        }
    }
}

@Composable
private fun TransferSummaryTile(
    title: String,
    account: AccountEntity?,
    modifier: Modifier = Modifier
) {
    val accent = account?.let { Color(it.colorHex) } ?: MaterialTheme.colorScheme.onSurfaceVariant
    YimuCard(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        cornerRadius = 12.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (account != null) {
                    CartoonAccountIcon(
                        iconKey = account.icon,
                        accountName = account.name,
                        colorHex = account.colorHex,
                        size = 26.dp
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = account?.name ?: "请选择",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (account != null) MaterialTheme.colorScheme.onSurface else accent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (account != null) {
                        Text(
                            text = account.currencyCode,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TransferAccountRow(
    label: String,
    accounts: List<AccountEntity>,
    selectedId: Long?,
    onSelect: (AccountEntity) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(accounts, key = { it.id }) { acc ->
                val selected = acc.id == selectedId
                val accColor = Color(acc.colorHex)
                Surface(
                    shape = CircleShape,
                    color = if (selected) accColor.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(
                        width = if (selected) 1.dp else 0.5.dp,
                        color = if (selected) accColor else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                    ),
                    modifier = Modifier
                        .height(32.dp)
                        .clip(CircleShape)
                        .clickable { onSelect(acc) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        CartoonAccountIcon(
                            iconKey = acc.icon,
                            accountName = acc.name,
                            colorHex = acc.colorHex,
                            size = 18.dp
                        )
                        Text(
                            text = "${acc.name} · ${acc.currencyCode}",
                            style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.5.sp),
                            color = if (selected) accColor else MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkbenchAttrChip(
    icon: ImageVector?,
    label: String,
    selected: Boolean,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    customLeading: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor = if (selected) {
        accentColor.copy(alpha = 0.13f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.72f)
    }
    val fgColor = if (selected) accentColor else MaterialTheme.colorScheme.onSurfaceVariant
    val border = if (selected) {
        BorderStroke(0.8.dp, accentColor.copy(alpha = 0.45f))
    } else {
        BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    }
    Surface(
        shape = CircleShape,
        color = bgColor,
        border = border,
        modifier = modifier
            .height(30.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally)
        ) {
            if (customLeading != null) {
                customLeading()
            } else if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = fgColor,
                    modifier = Modifier.size(13.5.dp)
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
                color = if (selected) accentColor else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun LedgerPickerDialog(
    ledgers: List<LedgerEntity>,
    selectedLedgerId: Long,
    onSelect: (LedgerEntity) -> Unit,
    onDismiss: () -> Unit
) {
    YubraeDialog(
        onDismissRequest = onDismiss,
        title = "选择账本",
        icon = Icons.AutoMirrored.Rounded.MenuBook,
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp)
            ) { Text("关闭") }
        }
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 320.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(ledgers, key = { it.id }) { ledger ->
                val isSelected = ledger.id == selectedLedgerId
                val ledgerColor = Color(ledger.colorHex)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) {
                        ledgerColor.copy(alpha = 0.14f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    },
                    border = if (isSelected) BorderStroke(1.dp, ledgerColor.copy(alpha = 0.6f)) else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSelect(ledger) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = KairoIcons.ledgerIcon(ledger.icon),
                            contentDescription = null,
                            tint = ledgerColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = ledger.name,
                            style = MaterialTheme.typography.titleSmall,
                            color = if (isSelected) ledgerColor else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = "已选中",
                                tint = ledgerColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExchangeRateOverrideDialog(
    origCurrency: String,
    txType: String,
    selectedAccount: AccountEntity?,
    selectedToAccount: AccountEntity?,
    needsSourceConversion: Boolean,
    needsTransferConversion: Boolean,
    accountAmountText: String,
    rateText: String,
    transferToAmountText: String,
    transferRateText: String,
    rateSource: String?,
    hasManualOverride: Boolean,
    onAccountAmountChange: (String) -> Unit,
    onRateChange: (String) -> Unit,
    onTransferToAmountChange: (String) -> Unit,
    onTransferRateChange: (String) -> Unit,
    onAccountAmountFocusChange: (Boolean) -> Unit,
    onRateFocusChange: (Boolean) -> Unit,
    onTransferToAmountFocusChange: (Boolean) -> Unit,
    onTransferRateFocusChange: (Boolean) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    YubraeDialog(
        onDismissRequest = onDismiss,
        title = "汇率与折算",
        icon = Icons.Rounded.CurrencyExchange,
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp)
            ) { Text("确定") }
        },
        dismissButton = if (hasManualOverride) {
            {
                TextButton(
                    onClick = onReset,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("重置汇率")
                }
            }
        } else {
            null
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (needsSourceConversion && selectedAccount != null) {
                val acctCode = selectedAccount.currencyCode
                val actionLabel = if (txType == TransactionType.INCOME) "实际入账($acctCode)" else "实际扣款($acctCode)"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = accountAmountText,
                        onValueChange = onAccountAmountChange,
                        label = { Text(actionLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        textStyle = MaterialTheme.typography.bodyLarge.tabular(),
                        modifier = Modifier
                            .weight(1.1f)
                            .onFocusChanged { onAccountAmountFocusChange(it.isFocused) }
                    )
                    OutlinedTextField(
                        value = rateText,
                        onValueChange = onRateChange,
                        label = { Text("1 $origCurrency → $acctCode", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        textStyle = MaterialTheme.typography.bodyLarge.tabular(),
                        modifier = Modifier
                            .weight(0.9f)
                            .onFocusChanged { onRateFocusChange(it.isFocused) }
                    )
                }
                val rateSourceLabel = when (rateSource) {
                    "MANUAL_AMOUNT" -> "按实扣金额反算"
                    "MANUAL_RATE" -> "手动汇率"
                    else -> "市场参考汇率"
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
                        onValueChange = onTransferToAmountChange,
                        label = { Text("转入到账($toCode)", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        textStyle = MaterialTheme.typography.bodyLarge.tabular(),
                        modifier = Modifier
                            .weight(1.1f)
                            .onFocusChanged { onTransferToAmountFocusChange(it.isFocused) }
                    )
                    OutlinedTextField(
                        value = transferRateText,
                        onValueChange = onTransferRateChange,
                        label = { Text("1 $origCurrency → $toCode", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        textStyle = MaterialTheme.typography.bodyLarge.tabular(),
                        modifier = Modifier
                            .weight(0.9f)
                            .onFocusChanged { onTransferRateFocusChange(it.isFocused) }
                    )
                }
            }
        }
    }
}
