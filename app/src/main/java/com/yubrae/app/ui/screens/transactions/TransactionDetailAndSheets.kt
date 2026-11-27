package com.yubrae.app.ui.screens.transactions

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yubrae.app.core.model.MoneyAmount
import com.yubrae.app.data.db.AccountEntity
import com.yubrae.app.data.db.AliasMatchType
import com.yubrae.app.data.db.CategoryEntity
import com.yubrae.app.data.db.RawCaptureLogEntity
import com.yubrae.app.data.db.ReimburseStatus
import com.yubrae.app.data.db.TagEntity
import com.yubrae.app.data.db.TagTextCodec
import com.yubrae.app.data.db.TransactionEntity
import com.yubrae.app.data.db.TransactionStatus
import com.yubrae.app.data.db.TransactionType
import com.yubrae.app.ui.components.CategoryIconBadge
import com.yubrae.app.ui.components.ChannelChip
import com.yubrae.app.ui.components.CompactHairlineDivider
import com.yubrae.app.ui.components.KairoFormats
import com.yubrae.app.ui.components.KairoIcons
import com.yubrae.app.ui.components.ReceiptThumbnailImage
import com.yubrae.app.ui.components.ReceiptZoomPreviewDialog
import com.yubrae.app.ui.components.TagBadgeChip
import com.yubrae.app.ui.components.YubraeDialog
import com.yubrae.app.ui.components.YubraeSheetShape
import com.yubrae.app.ui.components.formatPrivacyAmount
import com.yubrae.app.ui.components.transactionAmountColor
import com.yubrae.app.ui.components.yubraeSheetContainerColor
import com.yubrae.app.ui.theme.KairoTheme
import com.yubrae.app.ui.theme.tabular
import com.yubrae.app.ui.viewmodel.KairoMainViewModel
import kotlin.math.abs

private val DetailCardShape = RoundedCornerShape(16.dp)
private val DetailBadgeShape = RoundedCornerShape(10.dp)
private val DetailLogCardShape = RoundedCornerShape(14.dp)
private val DetailThumbShape = RoundedCornerShape(12.dp)
private val BatchCategoryRowShape = RoundedCornerShape(12.dp)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun TransactionDetailSheet(
    transaction: TransactionEntity,
    account: AccountEntity?,
    toAccount: AccountEntity?,
    category: CategoryEntity?,
    parentCategory: CategoryEntity? = null,
    refundedMicros: Long = 0L,
    ledgerName: String?,
    tagMap: Map<String, TagEntity>,
    accounts: List<AccountEntity>,
    privacyMasked: Boolean,
    viewModel: KairoMainViewModel,
    onConfirm: () -> Unit,
    onEdit: () -> Unit,
    onRefund: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var captureLog by remember(transaction.rawEventId) { mutableStateOf<RawCaptureLogEntity?>(null) }
    var showRawCaptureLog by remember(transaction.id, transaction.rawEventId) { mutableStateOf(false) }
    var showPromoteDialog by remember { mutableStateOf(false) }
    var zoomImagePath by remember { mutableStateOf<String?>(null) }

    val attachments = remember(transaction.attachmentPaths) {
        transaction.attachmentPaths
            .split(";")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }
    val categoryDisplayName = remember(category?.name, parentCategory?.name) {
        when {
            category != null && parentCategory != null -> "${parentCategory.name}-${category.name}"
            category != null -> category.name
            else -> null
        }
    }

    LaunchedEffect(transaction.rawEventId) {
        val logId = transaction.rawEventId
        captureLog = if (logId != null) viewModel.findCaptureLogById(logId) else null
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = YubraeSheetShape,
        containerColor = yubraeSheetContainerColor()
    ) {
        val semantic = KairoTheme.semantic
        val isPendingStatus = transaction.status == TransactionStatus.PENDING
        val signedOrig = when (transaction.type) {
            TransactionType.EXPENSE -> -abs(transaction.origAmountMicros)
            TransactionType.INCOME -> abs(transaction.origAmountMicros)
            else -> transaction.origAmountMicros
        }
        val formattedHeaderAmount = formatPrivacyAmount(
            privacyMasked,
            MoneyAmount.format(
                signedOrig,
                transaction.origCurrency,
                showSign = transaction.type != TransactionType.TRANSFER,
                showCurrencyCode = true
            )
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "账单详情",
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    ChannelChip(channel = transaction.sourceType)
                    IconButton(
                        onClick = {
                            val summary = buildTransactionCopySummary(
                                tx = transaction,
                                formattedAmount = formattedHeaderAmount,
                                categoryLabel = categoryDisplayName,
                                accountName = account?.name,
                                toAccountName = toAccount?.name,
                                ledgerName = ledgerName
                            )
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            clipboard?.setPrimaryClip(ClipData.newPlainText("transaction_detail", summary))
                            viewModel.showMessage("已复制账单详情")
                        },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ContentCopy,
                            contentDescription = "复制账单详情",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    if (isPendingStatus) {
                        TextButton(
                            onClick = onDelete,
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Icon(Icons.Rounded.Delete, null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("删除", style = MaterialTheme.typography.labelMedium, maxLines = 1, softWrap = false)
                        }
                    }
                }
            }

            Surface(
                shape = DetailCardShape,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = formattedHeaderAmount,
                        style = MaterialTheme.typography.headlineMedium.tabular(),
                        color = transactionAmountColor(transaction.type)
                    )
                    if (!transaction.origCurrency.equals(transaction.accountCurrency, true)) {
                        Text(
                            text = formatPrivacyAmount(
                                privacyMasked,
                                "账户入账：${MoneyAmount.format(transaction.accountAmountMicros, transaction.accountCurrency, showCurrencyCode = true)}（成交汇率 1 ${transaction.origCurrency} = ${MoneyAmount.formatRate(transaction.rateToAccountMicros)} ${transaction.accountCurrency}）"
                            ),
                            style = MaterialTheme.typography.bodySmall.tabular(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (!transaction.origCurrency.equals(transaction.baseCurrency, true)) {
                        val frozenBase = MoneyAmount.convert(transaction.origAmountMicros, transaction.rateToBaseMicros)
                        Text(
                            text = formatPrivacyAmount(
                                privacyMasked,
                                "折算 ${transaction.baseCurrency}：${MoneyAmount.format(frozenBase, transaction.baseCurrency)}（汇率 ${MoneyAmount.formatRate(transaction.rateToBaseMicros)}）"
                            ),
                            style = MaterialTheme.typography.bodySmall.tabular(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    val hasLinkedRefund = transaction.type == TransactionType.EXPENSE && refundedMicros > 0L
                    if (transaction.discountAmountMicros > 0L || transaction.feeAmountMicros > 0L || hasLinkedRefund) {
                        val showOrigCode = !transaction.origCurrency.equals("CNY", ignoreCase = true)
                        CompactHairlineDivider(modifier = Modifier.padding(vertical = 4.dp), startIndent = 0.dp)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            when (transaction.type) {
                                TransactionType.TRANSFER -> {
                                    val resolvedToCur = transaction.toCurrency ?: transaction.origCurrency
                                    val resolvedToMicros = transaction.toAmountMicros
                                        ?: (transaction.origAmountMicros + transaction.discountAmountMicros - transaction.feeAmountMicros)
                                            .coerceAtLeast(0L)
                                    val showToCode = !resolvedToCur.equals("CNY", ignoreCase = true)
                                    DetailAmountBreakdownRow(
                                        label = "转出实扣",
                                        value = formatPrivacyAmount(
                                            privacyMasked,
                                            MoneyAmount.format(
                                                transaction.origAmountMicros,
                                                transaction.origCurrency,
                                                showCurrencyCode = showOrigCode
                                            )
                                        )
                                    )
                                    DetailAmountBreakdownRow(
                                        label = "转入到账",
                                        value = formatPrivacyAmount(
                                            privacyMasked,
                                            MoneyAmount.format(
                                                resolvedToMicros,
                                                resolvedToCur,
                                                showCurrencyCode = showToCode
                                            )
                                        ),
                                        valueColor = semantic.transfer,
                                        bold = true
                                    )
                                    if (transaction.discountAmountMicros > 0L) {
                                        DetailAmountBreakdownRow(
                                            label = "已享优惠",
                                            value = formatPrivacyAmount(
                                                privacyMasked,
                                                MoneyAmount.format(
                                                    transaction.discountAmountMicros,
                                                    transaction.origCurrency,
                                                    showCurrencyCode = showOrigCode
                                                )
                                            ),
                                            valueColor = semantic.income,
                                            bold = true
                                        )
                                    }
                                    if (transaction.feeAmountMicros > 0L) {
                                        DetailAmountBreakdownRow(
                                            label = "手续费",
                                            value = formatPrivacyAmount(
                                                privacyMasked,
                                                MoneyAmount.format(
                                                    transaction.feeAmountMicros,
                                                    transaction.origCurrency,
                                                    showCurrencyCode = showOrigCode
                                                )
                                            ),
                                            valueColor = semantic.warning,
                                            bold = true
                                        )
                                    }
                                }
                                TransactionType.EXPENSE -> {
                                    if (transaction.discountAmountMicros > 0L || transaction.feeAmountMicros > 0L) {
                                        val nominalMicros = (transaction.origAmountMicros + transaction.discountAmountMicros - transaction.feeAmountMicros)
                                            .coerceAtLeast(0L)
                                        DetailAmountBreakdownRow(
                                            label = "商品原价",
                                            value = formatPrivacyAmount(
                                                privacyMasked,
                                                MoneyAmount.format(
                                                    nominalMicros,
                                                    transaction.origCurrency,
                                                    showCurrencyCode = showOrigCode
                                                )
                                            )
                                        )
                                        if (transaction.discountAmountMicros > 0L) {
                                            DetailAmountBreakdownRow(
                                                label = "已享优惠",
                                                value = formatPrivacyAmount(
                                                    privacyMasked,
                                                    "-${MoneyAmount.format(transaction.discountAmountMicros, transaction.origCurrency, showCurrencyCode = showOrigCode)}"
                                                ),
                                                valueColor = semantic.income,
                                                bold = true
                                            )
                                        }
                                        if (transaction.feeAmountMicros > 0L) {
                                            DetailAmountBreakdownRow(
                                                label = "手续费",
                                                value = formatPrivacyAmount(
                                                    privacyMasked,
                                                    "+${MoneyAmount.format(transaction.feeAmountMicros, transaction.origCurrency, showCurrencyCode = showOrigCode)}"
                                                ),
                                                valueColor = semantic.warning,
                                                bold = true
                                            )
                                        }
                                        DetailAmountBreakdownRow(
                                            label = "实际支付",
                                            value = formatPrivacyAmount(
                                                privacyMasked,
                                                MoneyAmount.format(
                                                    transaction.origAmountMicros,
                                                    transaction.origCurrency,
                                                    showCurrencyCode = showOrigCode
                                                )
                                            ),
                                            valueColor = semantic.expense,
                                            bold = true
                                        )
                                    }
                                    if (hasLinkedRefund) {
                                        val netMicros = (abs(transaction.origAmountMicros) - refundedMicros).coerceAtLeast(0L)
                                        DetailAmountBreakdownRow(
                                            label = "已关联退款",
                                            value = formatPrivacyAmount(
                                                privacyMasked,
                                                MoneyAmount.format(refundedMicros, transaction.origCurrency, showCurrencyCode = showOrigCode)
                                            ),
                                            valueColor = semantic.warning,
                                            bold = true
                                        )
                                        DetailAmountBreakdownRow(
                                            label = "净支出",
                                            value = formatPrivacyAmount(
                                                privacyMasked,
                                                MoneyAmount.format(netMicros, transaction.origCurrency, showCurrencyCode = showOrigCode)
                                            ),
                                            valueColor = semantic.expense,
                                            bold = true
                                        )
                                    }
                                }
                                TransactionType.INCOME -> {
                                    val grossMicros = transaction.origAmountMicros + transaction.feeAmountMicros
                                    DetailAmountBreakdownRow(
                                        label = "应收原额",
                                        value = formatPrivacyAmount(
                                            privacyMasked,
                                            MoneyAmount.format(
                                                grossMicros,
                                                transaction.origCurrency,
                                                showCurrencyCode = showOrigCode
                                            )
                                        )
                                    )
                                    if (transaction.feeAmountMicros > 0L) {
                                        DetailAmountBreakdownRow(
                                            label = "手续费",
                                            value = formatPrivacyAmount(
                                                privacyMasked,
                                                "-${MoneyAmount.format(transaction.feeAmountMicros, transaction.origCurrency, showCurrencyCode = showOrigCode)}"
                                            ),
                                            valueColor = semantic.warning,
                                            bold = true
                                        )
                                    }
                                    DetailAmountBreakdownRow(
                                        label = "实际入账",
                                        value = formatPrivacyAmount(
                                            privacyMasked,
                                            MoneyAmount.format(
                                                transaction.origAmountMicros,
                                                transaction.origCurrency,
                                                showCurrencyCode = showOrigCode
                                            )
                                        ),
                                        valueColor = semantic.income,
                                        bold = true
                                    )
                                }
                                else -> Unit
                            }
                        }
                    }
                }
            }

            // 退款 / 不计入统计提示标记
            if (transaction.refundOfTxId != null || transaction.excludeFromStats || transaction.excludeFromBudget) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (transaction.refundOfTxId != null) {
                        Surface(
                            shape = DetailBadgeShape,
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                        ) {
                            Text(
                                text = "关联退款",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                    if (transaction.excludeFromStats) {
                        Surface(
                            shape = DetailBadgeShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Text(
                                text = "不计入收支统计",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                    if (transaction.excludeFromBudget) {
                        Surface(
                            shape = DetailBadgeShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Text(
                                text = "不计入预算",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }

            DetailRow("类型", KairoFormats.transactionTypeLabel(transaction.type))
            if (!ledgerName.isNullOrBlank()) {
                DetailRow("账本", ledgerName)
            }
            DetailRow("账户", if (toAccount != null) "${account?.name ?: ""} → ${toAccount.name}" else (account?.name ?: "未知账户"))
            if (categoryDisplayName != null) DetailRow("分类", categoryDisplayName)
            if (transaction.merchant.isNotBlank()) DetailRow("商户", transaction.merchant)
            if (transaction.note.isNotBlank()) DetailRow("备注", transaction.note)

            val detailTags = remember(transaction.tags) { TagTextCodec.parse(transaction.tags) }
            if (detailTags.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = "标签",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        detailTags.forEach { tagName ->
                            val tag = tagMap[tagName]
                            TagBadgeChip(
                                name = tagName,
                                colorHex = tag?.colorHex ?: KairoIcons.fallbackTagColorHex(tagName),
                                compact = true,
                                iconKey = tag?.icon.orEmpty()
                            )
                        }
                    }
                }
            }
            if (transaction.reimburseStatus != ReimburseStatus.NONE) {
                val reimburseLabel = when (transaction.reimburseStatus) {
                    ReimburseStatus.PENDING -> "待报销"
                    ReimburseStatus.REIMBURSED -> "已报销"
                    else -> "不报销"
                }
                DetailRow("报销状态", reimburseLabel)
            }
            DetailRow("时间", KairoFormats.formatDateTime(transaction.timestamp))
            DetailRow("状态", if (transaction.status == TransactionStatus.PENDING) "待核对" else "已确认")

            // 小票图片附件缩略图展示（点击放大预览）
            if (attachments.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "小票 (${attachments.size})",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(attachments, key = { it }, contentType = { "receipt_thumb" }) { path ->
                            ReceiptThumbnailImage(
                                path = path,
                                modifier = Modifier
                                    .size(76.dp)
                                    .clip(DetailThumbShape)
                                    .clickable { zoomImagePath = path }
                            )
                        }
                    }
                }
            }

            // 原始捕获报文溯源卡片（默认折叠，点击展开）
            captureLog?.let { log ->
                Surface(
                    shape = DetailLogCardShape,
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(DetailLogCardShape)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showRawCaptureLog = !showRawCaptureLog }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = KairoFormats.channelIcon(log.channel),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "原始报文 #${log.id} · ${KairoFormats.channelLabel(log.channel)}",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = if (showRawCaptureLog) "收起" else "查看",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Icon(
                                    imageVector = if (showRawCaptureLog) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                    contentDescription = if (showRawCaptureLog) "收起原始报文" else "查看原始报文",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        AnimatedVisibility(visible = showRawCaptureLog) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "${log.sourcePackage} · ${log.parserUsed.ifBlank { "-" }}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (log.title.isNotBlank()) {
                                    Text("标题：${log.title}", style = MaterialTheme.typography.bodySmall)
                                }
                                Text("原文：${log.content}", style = MaterialTheme.typography.bodySmall)
                                TextButton(
                                    onClick = { showPromoteDialog = true },
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text("设为账户指纹")
                                }
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!isPendingStatus) {
                    OutlinedButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        contentPadding = PaddingValues(horizontal = 10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.Delete, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("删除", maxLines = 1, softWrap = false)
                    }
                }
                if (transaction.type == TransactionType.EXPENSE) {
                    OutlinedButton(
                        onClick = onRefund,
                        contentPadding = PaddingValues(horizontal = 10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.Replay, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("退款", maxLines = 1, softWrap = false)
                    }
                }
                OutlinedButton(
                    onClick = onEdit,
                    contentPadding = PaddingValues(horizontal = 10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.Edit, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("编辑", maxLines = 1, softWrap = false)
                }
                if (isPendingStatus) {
                    Button(
                        onClick = onConfirm,
                        contentPadding = PaddingValues(horizontal = 10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.Check, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("确认", maxLines = 1, softWrap = false)
                    }
                }
            }
        }
    }

    zoomImagePath?.let { path ->
        ReceiptZoomPreviewDialog(
            imagePath = path,
            onDismiss = { zoomImagePath = null }
        )
    }

    if (showPromoteDialog && captureLog != null) {
        val log = captureLog!!
        var selectedAccountId by remember { mutableStateOf(transaction.accountId) }
        var matchType by remember { mutableStateOf(AliasMatchType.PACKAGE) }
        var pattern by remember { mutableStateOf(log.sourcePackage) }
        YubraeDialog(
            onDismissRequest = { showPromoteDialog = false },
            title = "沉淀为账户指纹",
            icon = Icons.Rounded.Fingerprint,
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.promoteLogToAlias(log.id, selectedAccountId, pattern.trim(), matchType) {
                            showPromoteDialog = false
                        }
                    },
                    shape = BatchCategoryRowShape
                ) { Text("保存指纹") }
            },
            dismissButton = {
                TextButton(
                    onClick = { showPromoteDialog = false },
                    shape = BatchCategoryRowShape
                ) { Text("取消") }
            }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("目标账户：", style = MaterialTheme.typography.labelMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(accounts, key = { it.id }, contentType = { "account_chip" }) { acc ->
                        FilterChip(
                            selected = selectedAccountId == acc.id,
                            onClick = { selectedAccountId = acc.id },
                            label = { Text(acc.name) }
                        )
                    }
                }
                Text("匹配方式：", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        AliasMatchType.PACKAGE to "包名",
                        AliasMatchType.CARD_LAST4 to "尾号",
                        AliasMatchType.KEYWORD to "关键词"
                    ).forEach { (type, label) ->
                        FilterChip(
                            selected = matchType == type,
                            onClick = {
                                matchType = type
                                if (type == AliasMatchType.PACKAGE) pattern = log.sourcePackage
                            },
                            label = { Text(label) }
                        )
                    }
                }
                OutlinedTextField(
                    value = pattern,
                    onValueChange = { pattern = it },
                    label = { Text("匹配模式") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

private fun buildTransactionCopySummary(
    tx: TransactionEntity,
    formattedAmount: String,
    categoryLabel: String?,
    accountName: String?,
    toAccountName: String?,
    ledgerName: String?
): String = buildString {
    append("${KairoFormats.transactionTypeLabel(tx.type)} $formattedAmount")
    if (!categoryLabel.isNullOrBlank()) append(" · $categoryLabel")
    if (toAccountName != null) {
        append("\n账户：${accountName ?: ""} → $toAccountName")
    } else if (!accountName.isNullOrBlank()) {
        append("\n账户：$accountName")
    }
    if (!ledgerName.isNullOrBlank()) append("\n账本：$ledgerName")
    if (tx.merchant.isNotBlank()) append("\n商户：${tx.merchant}")
    if (tx.note.isNotBlank()) append("\n备注：${tx.note}")
    append("\n时间：${KairoFormats.formatDateTime(tx.timestamp)}")
}

@Composable
internal fun BatchCategoryPickerDialog(
    categories: List<CategoryEntity>,
    selectedCount: Int,
    onPickCategory: (Long?) -> Unit,
    onDismiss: () -> Unit
) {
    var filterType by remember { mutableStateOf(TransactionType.EXPENSE) }
    val filteredCats = remember(categories, filterType) {
        val typed = categories.filter { it.type == filterType }
        val parents = typed.filter { it.parentId == null }.sortedWith(compareBy({ it.sortOrder }, { it.id }))
        val childMap = typed.filter { it.parentId != null }.groupBy { it.parentId!! }
        val parentIds = parents.map { it.id }.toSet()
        val treeList = parents.flatMap { p ->
            listOf(p) + childMap[p.id].orEmpty().sortedWith(compareBy({ it.sortOrder }, { it.id }))
        }
        val orphans = typed.filter { it.parentId != null && it.parentId !in parentIds }
        treeList + orphans
    }
    YubraeDialog(
        onDismissRequest = onDismiss,
        title = "批量修改分类 ($selectedCount 笔)",
        icon = Icons.Rounded.Category,
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                shape = BatchCategoryRowShape
            ) { Text("取消") }
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = filterType == TransactionType.EXPENSE,
                    onClick = { filterType = TransactionType.EXPENSE },
                    label = { Text("支出") }
                )
                FilterChip(
                    selected = filterType == TransactionType.INCOME,
                    onClick = { filterType = TransactionType.INCOME },
                    label = { Text("收入") }
                )
            }
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(filteredCats, key = { it.id }, contentType = { "batch_cat_item" }) { cat ->
                    val isSub = cat.parentId != null
                    Surface(
                        shape = BatchCategoryRowShape,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPickCategory(cat.id) }
                    ) {
                        Row(
                            modifier = Modifier.padding(
                                start = if (isSub) 24.dp else 12.dp,
                                end = 12.dp,
                                top = 9.dp,
                                bottom = 9.dp
                            ),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CategoryIconBadge(
                                iconKey = cat.icon,
                                colorHex = cat.colorHex,
                                size = 32.dp,
                                iconSize = 16.dp
                            )
                            Text(
                                text = cat.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSub) FontWeight.Normal else FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailAmountBreakdownRow(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    bold: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.tabular(),
            fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Medium,
            color = valueColor,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
    }
}
