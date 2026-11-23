package com.kairoledger.app.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CurrencyExchange
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.KeyboardHide
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kairoledger.app.core.contract.ConversionPreview
import com.kairoledger.app.core.contract.ParsedBillDraft
import com.kairoledger.app.core.contract.RecordTransactionRequest
import com.kairoledger.app.core.model.CurrencyCatalog
import com.kairoledger.app.core.model.MoneyAmount
import com.kairoledger.app.data.db.AccountEntity
import com.kairoledger.app.data.db.CaptureChannel
import com.kairoledger.app.data.db.CategoryEntity
import com.kairoledger.app.data.db.LedgerEntity
import com.kairoledger.app.data.db.ReimburseStatus
import com.kairoledger.app.data.db.TagEntity
import com.kairoledger.app.data.db.TagTextCodec
import com.kairoledger.app.data.db.TransactionEntity
import com.kairoledger.app.data.db.TransactionStatus
import com.kairoledger.app.data.db.TransactionType
import com.kairoledger.app.ui.theme.KairoTheme
import com.kairoledger.app.ui.theme.tabular
import com.kairoledger.app.ui.viewmodel.KairoMainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

private object QuickExprEval {
    private val OPERATOR_CHARS = charArrayOf('+', '-', '×', '÷', '*', '/')

    fun isOperator(ch: Char): Boolean = ch in OPERATOR_CHARS

    fun containsOperator(expr: String): Boolean {
        if (expr.isEmpty()) return false
        for (i in 1 until expr.length) {
            if (isOperator(expr[i])) return true
        }
        return false
    }

    private fun lastOperatorIndex(expr: String): Int {
        var idx = -1
        for (i in expr.indices) {
            if (isOperator(expr[i]) && i > 0) {
                idx = i
            }
        }
        return idx
    }

    fun appendDigit(expr: String, digit: String): String {
        if (expr == "0" && digit != "00") return digit
        if (expr == "0" && digit == "00") return "0"
        val lastSep = lastOperatorIndex(expr)
        val currentSeg = expr.substring(lastSep + 1)
        if (currentSeg == "0") {
            return if (digit == "00" || digit == "0") expr else expr.dropLast(1) + digit
        }
        if (currentSeg.isEmpty() && digit == "00") {
            return "${expr}0"
        }
        return expr + digit
    }

    fun appendDot(expr: String): String {
        val lastSep = lastOperatorIndex(expr)
        val currentSeg = expr.substring(lastSep + 1)
        if (currentSeg.contains('.')) return expr
        return if (currentSeg.isEmpty()) "${expr}0." else "$expr."
    }

    fun appendOperator(expr: String, op: Char): String {
        if (expr.isEmpty()) return ""
        val last = expr.last()
        return when {
            isOperator(last) -> expr.dropLast(1) + op
            last == '.' -> expr.dropLast(1) + op
            else -> expr + op
        }
    }

    fun evaluate(expr: String): BigDecimal? {
        val cleaned = expr.trim().trimEnd('+', '-', '×', '÷', '*', '/', '.')
        if (cleaned.isEmpty()) return null
        val tokens = mutableListOf<String>()
        val sb = StringBuilder()
        cleaned.forEachIndexed { index, ch ->
            if (isOperator(ch) && index > 0 && sb.isNotEmpty()) {
                tokens.add(sb.toString())
                sb.clear()
                tokens.add(ch.toString())
            } else {
                sb.append(ch)
            }
        }
        if (sb.isNotEmpty()) tokens.add(sb.toString())
        if (tokens.isEmpty()) return null

        return try {
            // 第一遍：先计算 × 和 ÷（支持高优先级四则运算）
            val reduced = mutableListOf<String>()
            var acc = BigDecimal(tokens[0])
            var i = 1
            while (i + 1 < tokens.size) {
                val op = tokens[i]
                val rhs = BigDecimal(tokens[i + 1])
                when (op) {
                    "×", "*" -> {
                        acc = acc.multiply(rhs)
                    }
                    "÷", "/" -> {
                        if (rhs.compareTo(BigDecimal.ZERO) == 0) return null
                        acc = acc.divide(rhs, 8, RoundingMode.HALF_UP)
                    }
                    else -> {
                        reduced.add(acc.toPlainString())
                        reduced.add(op)
                        acc = rhs
                    }
                }
                i += 2
            }
            reduced.add(acc.toPlainString())

            // 第二遍：再从左向右计算 + 和 -
            var result = BigDecimal(reduced[0])
            var j = 1
            while (j + 1 < reduced.size) {
                val op = reduced[j]
                val rhs = BigDecimal(reduced[j + 1])
                result = when (op) {
                    "+" -> result.add(rhs)
                    "-" -> result.subtract(rhs)
                    else -> return null
                }
                j += 2
            }
            result
        } catch (_: Exception) {
            null
        }
    }

    fun evaluateAndFormat(expr: String, maxScale: Int = 4): String? {
        val bd = evaluate(expr) ?: return null
        val scaled = bd.setScale(maxScale.coerceIn(2, 4), RoundingMode.HALF_UP)
            .stripTrailingZeros()
        return if (scaled.compareTo(BigDecimal.ZERO) == 0) "0" else scaled.toPlainString()
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

internal object ReceiptImageUtils {
    private const val MAX_EDGE_PX = 1600

    private fun receiptsDir(context: Context): File =
        File(context.filesDir, "receipts").apply { if (!exists()) mkdirs() }

    private fun computeInSampleSize(width: Int, height: Int, maxEdge: Int): Int {
        var inSampleSize = 1
        var longest = maxOf(width, height)
        while (longest / inSampleSize > maxEdge) {
            inSampleSize *= 2
        }
        return inSampleSize.coerceAtLeast(1)
    }

    private fun rotateIfNeeded(bitmap: Bitmap, orientation: Int): Bitmap {
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (degrees == 0f) return bitmap
        val matrix = Matrix().apply { postRotate(degrees) }
        return runCatching {
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotated !== bitmap) bitmap.recycle()
            rotated
        }.getOrDefault(bitmap)
    }

    private fun scaleBitmapDown(bitmap: Bitmap, maxEdge: Int): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= maxEdge) return bitmap
        val ratio = maxEdge.toFloat() / longest.toFloat()
        val targetW = (bitmap.width * ratio).toInt().coerceAtLeast(1)
        val targetH = (bitmap.height * ratio).toInt().coerceAtLeast(1)
        return runCatching {
            val scaled = Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
            if (scaled !== bitmap) bitmap.recycle()
            scaled
        }.getOrDefault(bitmap)
    }

    suspend fun savePickedImageToReceipts(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val dir = receiptsDir(context)
        val destFile = File(dir, "receipt_${System.currentTimeMillis()}.jpg")

        val decoded = runCatching {
            val orientation = resolver.openInputStream(uri)?.use { stream ->
                runCatching {
                    ExifInterface(stream).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )
                }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
            } ?: ExifInterface.ORIENTATION_NORMAL

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, bounds)
            }
            if (bounds.outWidth > 0 && bounds.outHeight > 0) {
                val opts = BitmapFactory.Options().apply {
                    inSampleSize = computeInSampleSize(bounds.outWidth, bounds.outHeight, MAX_EDGE_PX)
                }
                resolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, opts)
                }?.let { bmp ->
                    scaleBitmapDown(rotateIfNeeded(bmp, orientation), MAX_EDGE_PX)
                }
            } else {
                null
            }
        }.getOrNull()

        if (decoded != null) {
            return@withContext runCatching {
                FileOutputStream(destFile).use { out ->
                    decoded.compress(Bitmap.CompressFormat.JPEG, 88, out)
                }
                decoded.recycle()
                destFile.absolutePath
            }.getOrNull()
        }

        // 若位图解码不可用，回退流式复制原始字节
        runCatching {
            resolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
                destFile.absolutePath
            }
        }.getOrNull()
    }

    suspend fun saveBitmapToReceipts(context: Context, bitmap: Bitmap): String? = withContext(Dispatchers.IO) {
        runCatching {
            val destFile = File(receiptsDir(context), "receipt_cam_${System.currentTimeMillis()}.jpg")
            val longest = maxOf(bitmap.width, bitmap.height)
            val toSave = if (longest > MAX_EDGE_PX) {
                val ratio = MAX_EDGE_PX.toFloat() / longest.toFloat()
                Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * ratio).toInt().coerceAtLeast(1),
                    (bitmap.height * ratio).toInt().coerceAtLeast(1),
                    true
                )
            } else {
                bitmap
            }
            FileOutputStream(destFile).use { out ->
                toSave.compress(Bitmap.CompressFormat.JPEG, 88, out)
            }
            if (toSave !== bitmap) toSave.recycle()
            destFile.absolutePath
        }.getOrNull()
    }

    suspend fun loadThumbnailBitmap(path: String, maxSizePx: Int = 600): Bitmap? = withContext(Dispatchers.IO) {
        val file = File(path)
        if (!file.exists() || !file.isFile) return@withContext null
        runCatching {
            val orientation = runCatching {
                ExifInterface(file.absolutePath).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

            val opts = BitmapFactory.Options().apply {
                inSampleSize = computeInSampleSize(bounds.outWidth, bounds.outHeight, maxSizePx)
            }
            val bmp = BitmapFactory.decodeFile(file.absolutePath, opts) ?: return@withContext null
            rotateIfNeeded(bmp, orientation)
        }.getOrNull()
    }
}

@Composable
internal fun ReceiptThumbnailImage(
    path: String,
    modifier: Modifier = Modifier,
    maxSizePx: Int = 480,
    contentScale: ContentScale = ContentScale.Crop
) {
    var bitmap by remember(path, maxSizePx) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(path, maxSizePx) {
        bitmap = ReceiptImageUtils.loadThumbnailBitmap(path, maxSizePx)
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = "小票图片",
            contentScale = contentScale,
            modifier = modifier
        )
    } else {
        Box(
            modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Image,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
internal fun ReceiptZoomPreviewDialog(
    imagePath: String,
    onDismiss: () -> Unit
) {
    var bitmap by remember(imagePath) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(imagePath) {
        bitmap = ReceiptImageUtils.loadThumbnailBitmap(imagePath, maxSizePx = 1800)
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.92f))
                .clickable(onClick = onDismiss)
                .padding(16.dp)
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap!!.asImageBitmap(),
                    contentDescription = "小票原图预览",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .align(Alignment.Center)
                )
            } else {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Image,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = "无法加载图片或文件已被清理",
                        color = Color.White.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.6f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 12.dp, end = 4.dp)
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "关闭预览",
                        tint = Color.White
                    )
                }
            }
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
    ledgers: List<LedgerEntity> = emptyList(),
    initialLedgerId: Long = 1L,
    refundOfTransaction: TransactionEntity? = null,
    initialDraft: ParsedBillDraft? = null,
    initialAttachmentPath: String? = null,
    llmEnabled: Boolean = false,
    onAnalyzeReceiptImage: (suspend (imagePath: String) -> Result<ParsedBillDraft?>)? = null,
    onOpenCategoryManager: (() -> Unit)? = null,
    availableTags: List<TagEntity> = emptyList(),
    onQuickCreateTag: ((name: String, colorHex: Long) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val semantic = KairoTheme.semantic

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

    var selectedLedgerId by remember(stateKey) {
        val defaultLedger = existingTransaction?.ledgerId
            ?: refundOfTransaction?.ledgerId
            ?: initialLedgerId.takeIf { it > 0L }
            ?: 1L
        mutableLongStateOf(defaultLedger)
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
    var localCreatedTagColors by remember(stateKey) { mutableStateOf<Map<String, Long>>(emptyMap()) }
    var showQuickCreateTagDialog by remember { mutableStateOf(false) }
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

    // 转账目标账户跨币种状态
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
    var showMoreCurrencies by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showExtraDetails by remember(stateKey) {
        mutableStateOf(
            (existingTransaction != null && (
                existingTransaction.merchant.isNotBlank() ||
                    existingTransaction.note.isNotBlank() ||
                    existingTransaction.tags.isNotBlank() ||
                    existingTransaction.reimburseStatus != ReimburseStatus.NONE
                )) ||
                refundOfTransaction != null ||
                (initialDraft != null && (
                    initialDraft.merchant.isNotBlank() ||
                        initialDraft.note.isNotBlank() ||
                        initialDraft.tags.isNotBlank()
                    ))
        )
    }

    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
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

    val selectableLedgers = remember(ledgers) {
        ledgers.filter { !it.archived }
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
                                        if (matchingCat == null && !isRefundMode) {
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
                // 关联原单退款提示横幅
                if (isRefundMode && refundOfTransaction != null) {
                    val origCatName = refundOfTransaction.categoryId?.let { id ->
                        categories.firstOrNull { it.id == id }?.name
                    }
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Replay,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "关联原单退款，统计将自动冲减原分类支出",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.SemiBold
                                )
                                val origSummary = buildString {
                                    append("原支出 ")
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
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }

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
                            if (QuickExprEval.containsOperator(amountExpr)) {
                                val evalMicros = parsedOrigMicros
                                Text(
                                    text = if (evalMicros > 0L) {
                                        "= ${MoneyAmount.format(evalMicros, origCurrency, showCurrencyCode = true)}"
                                    } else {
                                        "继续输入..."
                                    },
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

                // 多账本切换胶囊（存在多个未归档账本时展示）
                if (selectableLedgers.size > 1) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "所属账本",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(selectableLedgers, key = { it.id }) { ledger ->
                                val selected = ledger.id == selectedLedgerId
                                val ledgerColor = Color(ledger.colorHex)
                                Surface(
                                    shape = CircleShape,
                                    color = if (selected) ledgerColor.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                                    border = if (selected) BorderStroke(1.2.dp, ledgerColor) else null,
                                    modifier = Modifier.clickable { selectedLedgerId = ledger.id }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Rounded.MenuBook,
                                            contentDescription = null,
                                            tint = if (selected) ledgerColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = ledger.name,
                                            style = MaterialTheme.typography.labelLarge,
                                            color = if (selected) ledgerColor else MaterialTheme.colorScheme.onSurface,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                        )
                                    }
                                }
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

                // 清爽 5 列网格分类选择器（一级网格 + 二级胶囊展开）
                if (txType == TransactionType.EXPENSE || txType == TransactionType.INCOME) {
                    val typeCats = remember(categories, txType) {
                        categories.filter { it.type == txType }.sortedWith(compareBy({ it.sortOrder }, { it.id }))
                    }
                    val parents = remember(typeCats) { typeCats.filter { it.parentId == null } }
                    val parentRows = remember(parents) { parents.chunked(5) }
                    val selectedCatObj = remember(typeCats, selectedCategoryId) {
                        typeCats.firstOrNull { it.id == selectedCategoryId }
                    }
                    val activeParentId = selectedCatObj?.parentId ?: selectedCatObj?.id
                    val subCategories = remember(typeCats, activeParentId) {
                        if (activeParentId != null) typeCats.filter { it.parentId == activeParentId } else emptyList()
                    }
                    val refundOrigCategory = if (isRefundMode && refundOfTransaction?.categoryId != null) {
                        categories.firstOrNull { it.id == refundOfTransaction.categoryId }
                    } else null

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "分类",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (refundOrigCategory != null && selectedCategoryId == refundOrigCategory.id && refundOrigCategory.type != txType) {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.secondaryContainer
                                    ) {
                                        Text(
                                            text = "沿用原分类：${refundOrigCategory.name}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            if (onOpenCategoryManager != null) {
                                TextButton(
                                    onClick = onOpenCategoryManager,
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Icon(Icons.Rounded.Tune, null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("管理分类", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }

                        // 5 列规整分类网格
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            parentRows.forEach { rowCats ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    rowCats.forEach { cat ->
                                        val isSelected = activeParentId == cat.id
                                        CategoryGridCell(
                                            category = cat,
                                            selected = isSelected,
                                            onClick = { selectedCategoryId = cat.id },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    repeat(5 - rowCats.size) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }

                        // 二级分类胶囊展开区
                        if (subCategories.isNotEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "二级分类",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    FlowRow(
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
                }

                // 日期时间、高级统计开关与小票附件开关条
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
                        selected = excludeFromStats,
                        onClick = { excludeFromStats = !excludeFromStats },
                        label = { Text("不计入收支") }
                    )
                    FilterChip(
                        selected = excludeFromBudget,
                        onClick = { excludeFromBudget = !excludeFromBudget },
                        label = { Text("不计入预算") }
                    )
                    FilterChip(
                        selected = attachmentPaths.isNotEmpty(),
                        onClick = {
                            pickImageLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        leadingIcon = {
                            Icon(Icons.Rounded.AddPhotoAlternate, null, modifier = Modifier.size(16.dp))
                        },
                        label = {
                            Text(if (attachmentPaths.isEmpty()) "小票图片" else "小票(${attachmentPaths.size})")
                        }
                    )
                    val selectedTagCount = remember(tags) { TagTextCodec.parse(tags).size }
                    FilterChip(
                        selected = showExtraDetails,
                        onClick = { showExtraDetails = !showExtraDetails },
                        label = {
                            Text(
                                when {
                                    showExtraDetails -> "收起商户/标签/备注"
                                    selectedTagCount > 0 -> "商户 · 标签($selectedTagCount) · 备注"
                                    else -> "商户 · 标签 · 备注"
                                }
                            )
                        }
                    )
                }

                // 小票图片附件缩略图区 + AI 识别小票填单胶囊按钮
                AnimatedVisibility(visible = attachmentPaths.isNotEmpty()) {
                    YimuCard(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        cornerRadius = 14.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "小票图片附件 (${attachmentPaths.size})",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (onAnalyzeReceiptImage != null) {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                                        modifier = Modifier.clickable(enabled = !isAnalyzingReceipt) {
                                            val targetPath = attachmentPaths.lastOrNull() ?: return@clickable
                                            isAnalyzingReceipt = true
                                            coroutineScope.launch {
                                                val res = runCatching { onAnalyzeReceiptImage(targetPath) }
                                                    .getOrElse { Result.failure(it) }
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
                                                        if (draft.origCurrency.isNotBlank()) {
                                                            origCurrency = draft.origCurrency
                                                        }
                                                        if (draft.merchant.isNotBlank()) merchant = draft.merchant
                                                        if (draft.note.isNotBlank()) note = draft.note
                                                        if (draft.tags.isNotBlank()) {
                                                            tags = TagTextCodec.merge(tags, draft.tags)
                                                        }
                                                        if (draft.categoryId != null) selectedCategoryId = draft.categoryId
                                                        if (draft.accountId != null) selectedAccountId = draft.accountId
                                                        if (draft.timestamp > 0L) timestampMillis = draft.timestamp
                                                        if (draft.manualAccountAmountMicros != null) {
                                                            manualAccountAmountMicros = draft.manualAccountAmountMicros
                                                        }
                                                        showExtraDetails = true
                                                        viewModel.showMessage("AI 已识别小票并填入表单")
                                                    } else {
                                                        viewModel.showMessage(
                                                            if (llmEnabled) "未能从小票中提取出有效账单" else "请先在「我的 -> AI 大模型配置」中开启并配置大模型"
                                                        )
                                                    }
                                                }.onFailure { err ->
                                                    viewModel.showMessage("小票识别失败：${err.message ?: "请检查 AI 配置"}")
                                                }
                                            }
                                        }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                                        ) {
                                            if (isAnalyzingReceipt) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(14.dp),
                                                    strokeWidth = 2.dp
                                                )
                                            } else {
                                                Icon(
                                                    imageVector = Icons.Rounded.AutoAwesome,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                            Text(
                                                text = if (isAnalyzingReceipt) "正在识别..." else "AI 识别小票填单",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }
                            }

                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(attachmentPaths, key = { it }) { path ->
                                    Box(modifier = Modifier.size(72.dp)) {
                                        ReceiptThumbnailImage(
                                            path = path,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(RoundedCornerShape(10.dp))
                                                .clickable { previewImagePath = path }
                                        )
                                        Surface(
                                            shape = CircleShape,
                                            color = Color.Black.copy(alpha = 0.65f),
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(3.dp)
                                                .size(20.dp)
                                                .clickable {
                                                    attachmentPaths = attachmentPaths.filterNot { it == path }
                                                }
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Close,
                                                    contentDescription = "移除图片",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 商户、备注、标签、报销状态
                AnimatedVisibility(visible = showExtraDetails) {
                    val allTxs by viewModel.allTransactions.collectAsStateWithLifecycle()
                    val selectedTagNames = remember(tags) { TagTextCodec.parse(tags) }
                    val selectedTagSet = remember(selectedTagNames) { selectedTagNames.toSet() }
                    val tagColorMap = remember(availableTags, localCreatedTagColors) {
                        val map = LinkedHashMap<String, Long>(availableTags.size + localCreatedTagColors.size)
                        availableTags.forEach { map[it.name] = it.colorHex }
                        localCreatedTagColors.forEach { (k, v) -> map[k] = v }
                        map
                    }
                    val displayTagNames = remember(selectedTagNames, availableTags, allTxs, localCreatedTagColors) {
                        val ordered = LinkedHashSet<String>()
                        ordered.addAll(selectedTagNames)
                        availableTags.sortedWith(compareBy({ it.sortOrder }, { it.id })).forEach { ordered.add(it.name) }
                        localCreatedTagColors.keys.forEach { ordered.add(it) }
                        allTxs.forEach { tx ->
                            if (tx.tags.isNotBlank()) {
                                TagTextCodec.parse(tx.tags).forEach { ordered.add(it) }
                            }
                        }
                        ordered.toList()
                    }

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

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = if (selectedTagNames.isEmpty()) "标签（可多选）" else "标签（已选 ${selectedTagNames.size}）",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (selectedTagNames.isNotEmpty()) {
                                    TextButton(
                                        onClick = { tags = "" },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                        modifier = Modifier.height(26.dp)
                                    ) {
                                        Text("清空", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                FlowRow(
                                    modifier = Modifier.padding(10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    displayTagNames.forEach { tagName ->
                                        val isSelected = tagName in selectedTagSet
                                        val tagColorHex = tagColorMap[tagName] ?: KairoIcons.fallbackTagColorHex(tagName)
                                        TagBadgeChip(
                                            name = tagName,
                                            colorHex = tagColorHex,
                                            selected = isSelected,
                                            onClick = {
                                                val updated = if (isSelected) {
                                                    selectedTagNames.filterNot { it == tagName }
                                                } else {
                                                    selectedTagNames + tagName
                                                }
                                                tags = TagTextCodec.format(updated)
                                            }
                                        )
                                    }
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                                        border = BorderStroke(
                                            1.dp,
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                                        ),
                                        modifier = Modifier.clickable { showQuickCreateTagDialog = true }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Add,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Text(
                                                text = "新建标签",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }
                            }
                        }

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

            // 底部四则计算器键盘或确认按钮
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
                            tags = TagTextCodec.format(TagTextCodec.parse(tags)),
                            reimburseStatus = reimburseStatus,
                            sourceType = existingTransaction?.sourceType ?: CaptureChannel.MANUAL,
                            rawEventId = existingTransaction?.rawEventId,
                            status = TransactionStatus.CONFIRMED,
                            ledgerId = selectedLedgerId.takeIf { it > 0L } ?: 1L,
                            excludeFromStats = excludeFromStats,
                            excludeFromBudget = excludeFromBudget,
                            refundOfTxId = existingTransaction?.refundOfTxId ?: refundOfTransaction?.id,
                            attachmentPaths = attachmentPaths.joinToString(";"),
                            reimbursedTxId = existingTransaction?.reimbursedTxId
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
                    onEquals = {
                        if (QuickExprEval.containsOperator(amountExpr)) {
                            val evaluated = QuickExprEval.evaluateAndFormat(
                                amountExpr,
                                CurrencyCatalog.find(origCurrency).minorDigits
                            )
                            if (evaluated != null) {
                                amountExpr = evaluated
                            } else {
                                viewModel.showMessage("表达式无法计算（请检查除数或格式）")
                            }
                        }
                    },
                    onBackspace = {
                        if (amountExpr.isNotEmpty()) amountExpr = amountExpr.dropLast(1)
                    },
                    onClear = { amountExpr = "" },
                    onSave = performSave,
                    saveLabel = if (isEditMode) "保存" else "完成"
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

    previewImagePath?.let { path ->
        ReceiptZoomPreviewDialog(
            imagePath = path,
            onDismiss = { previewImagePath = null }
        )
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

    if (showQuickCreateTagDialog) {
        QuickCreateTagDialog(
            onConfirm = { name, colorHex ->
                localCreatedTagColors = localCreatedTagColors + (name to colorHex)
                tags = TagTextCodec.merge(tags, name)
                onQuickCreateTag?.invoke(name, colorHex)
                showQuickCreateTagDialog = false
            },
            onDismiss = { showQuickCreateTagDialog = false }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickCreateTagDialog(
    onConfirm: (name: String, colorHex: Long) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var selectedColor by remember { mutableStateOf(KairoIcons.selectableColorPalette.first()) }
    var nameError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建标签") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        if (it.isNotBlank()) nameError = false
                    },
                    label = { Text("标签名称") },
                    placeholder = { Text("例如：聚会、咖啡、机场") },
                    isError = nameError,
                    supportingText = if (nameError) {
                        { Text("请输入标签名称") }
                    } else null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "选择颜色",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    KairoIcons.selectableColorPalette.forEach { hex ->
                        val isSelected = selectedColor == hex
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(Color(hex))
                                .then(
                                    if (isSelected) {
                                        Modifier.border(
                                            BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface),
                                            CircleShape
                                        )
                                    } else {
                                        Modifier
                                    }
                                )
                                .clickable { selectedColor = hex },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = "已选颜色",
                                    tint = Color.White,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmed = name.trim()
                    if (trimmed.isEmpty()) {
                        nameError = true
                    } else {
                        onConfirm(trimmed, selectedColor)
                    }
                }
            ) {
                Text("创建并选中")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun CategoryGridCell(
    category: CategoryEntity,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val catColor = Color(category.colorHex)
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        CategoryIconBadge(
            iconKey = category.icon,
            colorHex = category.colorHex,
            size = 40.dp,
            iconSize = 20.dp,
            selected = selected
        )
        Text(
            text = category.name,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) catColor else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
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
    onEquals: () -> Unit,
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
        // 5 列 × 4 行布局：1~3 列为数字与小数点，第 4 列为 ÷ × - +，第 5 列为 退格 / 清零 / = / 完成
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            CalcKey("7", Modifier.weight(1f)) { onDigit("7") }
            CalcKey("8", Modifier.weight(1f)) { onDigit("8") }
            CalcKey("9", Modifier.weight(1f)) { onDigit("9") }
            CalcKey("÷", Modifier.weight(1f), isOperator = true) { onOperator('÷') }
            CalcIconKey(Icons.AutoMirrored.Rounded.Backspace, "退格", Modifier.weight(1f), onBackspace)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            CalcKey("4", Modifier.weight(1f)) { onDigit("4") }
            CalcKey("5", Modifier.weight(1f)) { onDigit("5") }
            CalcKey("6", Modifier.weight(1f)) { onDigit("6") }
            CalcKey("×", Modifier.weight(1f), isOperator = true) { onOperator('×') }
            CalcKey("清零", Modifier.weight(1f), isSecondary = true, onClick = onClear)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            CalcKey("1", Modifier.weight(1f)) { onDigit("1") }
            CalcKey("2", Modifier.weight(1f)) { onDigit("2") }
            CalcKey("3", Modifier.weight(1f)) { onDigit("3") }
            CalcKey("-", Modifier.weight(1f), isOperator = true) { onOperator('-') }
            CalcKey("=", Modifier.weight(1f), isOperator = true, onClick = onEquals)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            CalcKey("0", Modifier.weight(1f)) { onDigit("0") }
            CalcKey("00", Modifier.weight(1f)) { onDigit("00") }
            CalcKey(".", Modifier.weight(1f), onClick = onDot)
            CalcKey("+", Modifier.weight(1f), isOperator = true) { onOperator('+') }
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
                    .height(46.dp)
            ) {
                Text(
                    text = saveLabel,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
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
            .height(46.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = if (isSecondary || label == "00") MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleLarge.tabular(),
                color = fg,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
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
            .height(46.dp)
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
