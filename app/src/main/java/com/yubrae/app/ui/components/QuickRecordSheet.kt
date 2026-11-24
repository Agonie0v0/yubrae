package com.yubrae.app.ui.components

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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.AccountBalanceWallet
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
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardHide
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
import com.yubrae.app.ui.theme.KairoTheme
import com.yubrae.app.ui.theme.tabular
import com.yubrae.app.ui.viewmodel.KairoMainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
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
    initialLedgerId: Long = 0L,
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
    var showCurrencyPicker by remember { mutableStateOf(false) }
    var showAccountPicker by remember { mutableStateOf(false) }
    var showLedgerPicker by remember { mutableStateOf(false) }
    var showRateOverrideDialog by remember { mutableStateOf(false) }
    var showMerchantDialog by remember { mutableStateOf(false) }
    var showReimburseMenu by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showTagPanel by remember(stateKey) { mutableStateOf(false) }

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

    val selectedTagNames = remember(tags) { TagTextCodec.parse(tags) }
    val selectedLedger = remember(ledgers, selectableLedgers, selectedLedgerId) {
        selectableLedgers.firstOrNull { it.id == selectedLedgerId }
            ?: ledgers.firstOrNull { it.id == selectedLedgerId }
            ?: selectableLedgers.firstOrNull()
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
                        viewModel.showMessage("AI 已识别小票并填入表单")
                    } else {
                        viewModel.showMessage(
                            if (llmEnabled) "未识别出有效账单" else "请先开启 AI 配置"
                        )
                    }
                }.onFailure { err ->
                    viewModel.showMessage("小票识别失败：${err.message ?: "请检查 AI 配置"}")
                }
            }
        }
    }

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
                    ledgerId = selectedLedgerId.takeIf { id -> id > 0L && ledgers.any { it.id == id } }
                        ?: viewModel.firstActiveLedgerId(ledgers)
                        ?: 0L,
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

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
        ) {
            // 1. 顶部紧凑控制条（~38dp）：居中 支出 / 收入 / 转账 分段微胶囊 + 右侧「分类管理」与「关闭」
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 38.dp)
                    .padding(horizontal = 10.dp, vertical = 2.dp)
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
                            modifier = Modifier.size(17.dp)
                        )
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
                                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp),
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 3.5.dp)
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.align(Alignment.CenterEnd),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (onOpenCategoryManager != null) {
                        IconButton(
                            onClick = onOpenCategoryManager,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Tune,
                                contentDescription = "分类管理",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "关闭",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // 退款模式精巧提示条
            if (isRefundMode) {
                val origCatName = refundOfTransaction.categoryId?.let { id ->
                    categories.firstOrNull { it.id == id }?.name
                }
                val refundOrigCategory = refundOfTransaction.categoryId?.let { id ->
                    categories.firstOrNull { it.id == id }
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
                        if (refundOrigCategory != null && selectedCategoryId == refundOrigCategory.id && refundOrigCategory.type != txType) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(
                                    text = refundOrigCategory.name,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 2. 中部高频分类网格 / 转账双格卡（单手友好限高 120~172/220dp，确保 3 整行 15 个分类完整可见）
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .heightIn(min = 120.dp, max = if (showKeypad) 172.dp else 220.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 2.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                when (txType) {
                    TransactionType.EXPENSE, TransactionType.INCOME -> {
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

                        if (parents.isEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "暂无分类",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (onOpenCategoryManager != null) {
                                    FilledTonalButton(
                                        onClick = onOpenCategoryManager,
                                        shape = CircleShape,
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                        modifier = Modifier.height(34.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Add,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("添加分类", style = MaterialTheme.typography.labelLarge)
                                    }
                                }
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                parentRows.forEach { rowCats ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(2.dp)
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

                                    // 紧贴当前点击行轻巧展开二级分类微胶囊
                                    val rowHasActiveParent = activeParentId != null && rowCats.any { it.id == activeParentId }
                                    if (rowHasActiveParent && subCategories.isNotEmpty()) {
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                                            border = BorderStroke(
                                                0.5.dp,
                                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                                            ),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            FlowRow(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                                verticalArrangement = Arrangement.spacedBy(5.dp)
                                            ) {
                                                subCategories.forEach { sub ->
                                                    val isSubSelected = selectedCategoryId == sub.id
                                                    CategorySelectChip(
                                                        category = sub,
                                                        selected = isSubSelected,
                                                        compact = true,
                                                        onClick = {
                                                            selectedCategoryId = if (isSubSelected) activeParentId else sub.id
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
                            }
                        )
                    }

                    else -> {
                        YimuCard(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            cornerRadius = 12.dp
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
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

            // 3. 底部一体化工作台（紧贴自定义数字键盘上方）
            CompactHairlineDivider(startIndent = 0.dp)

            // 3a. 金额与备注紧凑融合行
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                    modifier = Modifier
                        .height(30.dp)
                        .clip(CircleShape)
                        .clickable { showCurrencyPicker = true }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(text = currencyInfo.flag, fontSize = 12.sp)
                        Text(
                            text = currencyInfo.code,
                            style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.5.sp),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Icon(
                            imageVector = Icons.Rounded.KeyboardArrowDown,
                            contentDescription = "切换币种",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(9.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier
                        .weight(1f)
                        .height(32.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
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
                                .onFocusChanged { if (it.isFocused) showKeypad = false },
                            decorationBox = { innerTextField ->
                                if (note.isEmpty()) {
                                    Text(
                                        text = "备注…",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.5.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                innerTextField()
                            }
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
                            .widthIn(max = 172.dp)
                            .horizontalScroll(amountScroll)
                    ) {
                        Text(
                            text = "${currencyInfo.symbol}${amountExpr.ifEmpty { "0.00" }}",
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontSize = 22.sp,
                                lineHeight = 26.sp,
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
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp).tabular(),
                            color = accentColor.copy(alpha = 0.85f),
                            maxLines = 1
                        )
                    }
                }
            }

            // 3b. 跨币种汇率折算微条（仅当与账户币种或本币不同时显示）
            val baseHintText = preview
                ?.takeIf { !origCurrency.equals(baseCurrency, ignoreCase = true) && parsedOrigMicros > 0L }
                ?.let { "折算本币 ≈ ${MoneyAmount.format(it.baseAmountMicros, baseCurrency, showCurrencyCode = true)}" }
            if (needsSourceConversion || needsTransferConversion || baseHintText != null) {
                val canEditRate = needsSourceConversion || needsTransferConversion
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f),
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 5.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .then(if (canEditRate) Modifier.clickable { showRateOverrideDialog = true } else Modifier)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
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
                                text = if (isManual) "已自定义" else "调整汇率",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            // 3c. 水平滚动属性微胶囊栏（~28dp 高）
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                item(key = "attr_account") {
                    WorkbenchAttrChip(
                        icon = Icons.Rounded.AccountBalanceWallet,
                        label = selectedAccount?.let { acc ->
                            if (acc.currencyCode.equals(origCurrency, ignoreCase = true)) acc.name else "${acc.name}(${acc.currencyCode})"
                        } ?: "选账户",
                        selected = selectedAccount != null,
                        accentColor = selectedAccount?.let { Color(it.colorHex) } ?: MaterialTheme.colorScheme.error,
                        onClick = { showAccountPicker = true }
                    )
                }

                if (selectableLedgers.isNotEmpty()) {
                    item(key = "attr_ledger") {
                        WorkbenchAttrChip(
                            icon = selectedLedger?.let { KairoIcons.ledgerIcon(it.icon) }
                                ?: Icons.AutoMirrored.Rounded.MenuBook,
                            label = selectedLedger?.name ?: "账本",
                            selected = selectableLedgers.size > 1,
                            accentColor = selectedLedger?.let { Color(it.colorHex) } ?: MaterialTheme.colorScheme.primary,
                            onClick = {
                                if (selectableLedgers.size > 1) {
                                    showLedgerPicker = true
                                } else {
                                    viewModel.showMessage("当前仅有一个可用账本")
                                }
                            }
                        )
                    }
                }

                item(key = "attr_date") {
                    WorkbenchAttrChip(
                        icon = Icons.Rounded.CalendarMonth,
                        label = formatCompactDateChip(timestampMillis),
                        selected = false,
                        onClick = { showDatePicker = true }
                    )
                }

                item(key = "attr_time") {
                    WorkbenchAttrChip(
                        icon = Icons.Rounded.Schedule,
                        label = KairoFormats.formatTime(timestampMillis),
                        selected = false,
                        onClick = { showTimePicker = true }
                    )
                }

                item(key = "attr_tags") {
                    WorkbenchAttrChip(
                        icon = Icons.AutoMirrored.Rounded.Label,
                        label = if (selectedTagNames.isEmpty()) "标签" else "标签(${selectedTagNames.size})",
                        selected = showTagPanel || selectedTagNames.isNotEmpty(),
                        onClick = { showTagPanel = !showTagPanel }
                    )
                }

                item(key = "attr_receipt") {
                    WorkbenchAttrChip(
                        icon = Icons.Rounded.AddPhotoAlternate,
                        label = if (attachmentPaths.isEmpty()) "票据/AI" else "票据(${attachmentPaths.size})",
                        selected = attachmentPaths.isNotEmpty(),
                        onClick = {
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
                    )
                }

                item(key = "attr_merchant") {
                    WorkbenchAttrChip(
                        icon = Icons.Rounded.Storefront,
                        label = if (merchant.isBlank()) "商户" else merchant,
                        selected = merchant.isNotBlank(),
                        onClick = { showMerchantDialog = true }
                    )
                }

                item(key = "attr_reimburse") {
                    Box {
                        val reimburseLabel = when (reimburseStatus) {
                            ReimburseStatus.PENDING -> "待报销"
                            ReimburseStatus.REIMBURSED -> "已报销"
                            else -> "报销"
                        }
                        WorkbenchAttrChip(
                            icon = Icons.AutoMirrored.Rounded.ReceiptLong,
                            label = reimburseLabel,
                            selected = reimburseStatus != ReimburseStatus.NONE,
                            onClick = { showReimburseMenu = true }
                        )
                        DropdownMenu(
                            expanded = showReimburseMenu,
                            onDismissRequest = { showReimburseMenu = false }
                        ) {
                            listOf(
                                ReimburseStatus.NONE to "不报销",
                                ReimburseStatus.PENDING to "待报销",
                                ReimburseStatus.REIMBURSED to "已报销"
                            ).forEach { (statusCode, label) ->
                                DropdownMenuItem(
                                    text = { Text(label, style = MaterialTheme.typography.bodyMedium) },
                                    trailingIcon = if (reimburseStatus == statusCode) {
                                        {
                                            Icon(
                                                imageVector = Icons.Rounded.Check,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    } else null,
                                    onClick = {
                                        reimburseStatus = statusCode
                                        showReimburseMenu = false
                                    }
                                )
                            }
                        }
                    }
                }

                item(key = "attr_exclude_stats") {
                    WorkbenchAttrChip(
                        icon = null,
                        label = "不计收支",
                        selected = excludeFromStats,
                        onClick = { excludeFromStats = !excludeFromStats }
                    )
                }

                item(key = "attr_exclude_budget") {
                    WorkbenchAttrChip(
                        icon = null,
                        label = "不计预算",
                        selected = excludeFromBudget,
                        onClick = { excludeFromBudget = !excludeFromBudget }
                    )
                }
            }

            // 3d. 可展开标签选择与快速新建面板
            AnimatedVisibility(visible = showTagPanel) {
                val allTxs by viewModel.allTransactions.collectAsStateWithLifecycle()
                TagSelectorPanel(
                    selectedTagNames = selectedTagNames,
                    availableTags = availableTags,
                    localCreatedTagColors = localCreatedTagColors,
                    allTransactions = allTxs,
                    onToggleTag = { tagName ->
                        val updated = if (tagName in selectedTagNames) {
                            selectedTagNames.filterNot { it == tagName }
                        } else {
                            selectedTagNames + tagName
                        }
                        tags = TagTextCodec.format(updated)
                    },
                    onClearTags = { tags = "" },
                    onNewTag = { showQuickCreateTagDialog = true },
                    onCollapse = { showTagPanel = false }
                )
            }

            // 3e. 已选标签或票据缩略图极薄一行预览带
            AnimatedVisibility(visible = (!showTagPanel && selectedTagNames.isNotEmpty()) || attachmentPaths.isNotEmpty()) {
                val tagColorMap = remember(availableTags, localCreatedTagColors) {
                    val map = LinkedHashMap<String, Long>(availableTags.size + localCreatedTagColors.size)
                    availableTags.forEach { map[it.name] = it.colorHex }
                    localCreatedTagColors.forEach { (k, v) -> map[k] = v }
                    map
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
                    if (!showTagPanel) {
                        items(selectedTagNames, key = { "tag_$it" }) { tagName ->
                            val colorHex = tagColorMap[tagName] ?: KairoIcons.fallbackTagColorHex(tagName)
                            TagBadgeChip(
                                name = tagName,
                                colorHex = colorHex,
                                selected = true,
                                compact = true,
                                onClick = { showTagPanel = true },
                                iconKey = tagIconMap[tagName].orEmpty()
                            )
                        }
                    }

                    items(attachmentPaths, key = { "img_$it" }) { path ->
                        Box(modifier = Modifier.size(28.dp)) {
                            ReceiptThumbnailImage(
                                path = path,
                                maxSizePx = 160,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { previewImagePath = path }
                            )
                            Surface(
                                shape = CircleShape,
                                color = Color.Black.copy(alpha = 0.65f),
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(13.dp)
                                    .clickable {
                                        attachmentPaths = attachmentPaths.filterNot { it == path }
                                    }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "移除图片",
                                        tint = Color.White,
                                        modifier = Modifier.size(9.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (attachmentPaths.isNotEmpty() && onAnalyzeReceiptImage != null) {
                        item(key = "ai_scan_pill") {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                                modifier = Modifier
                                    .height(26.dp)
                                    .clip(CircleShape)
                                    .clickable(enabled = !isAnalyzingReceipt, onClick = analyzeLastReceipt)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    if (isAnalyzingReceipt) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(11.dp),
                                            strokeWidth = 1.6.dp
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Rounded.AutoAwesome,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                    Text(
                                        text = if (isAnalyzingReceipt) "识别中..." else "AI识图填单",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 3f. 紧贴底部的轻薄 4 列自定义数字/计算器键盘或确认栏
            CompactHairlineDivider(startIndent = 0.dp)

            if (showKeypad) {
                CalculatorKeypad(
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
                    onSave = performSave,
                    saveLabel = if (isEditMode) "保存" else "完成"
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            focusManager.clearFocus()
                            showKeypad = true
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Rounded.Calculate, null, modifier = Modifier.size(17.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("数字键盘", style = MaterialTheme.typography.labelLarge)
                    }
                    Button(
                        onClick = performSave,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
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

    if (showAccountPicker) {
        AccountPickerDialog(
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
                showAccountPicker = false
            },
            onDismiss = { showAccountPicker = false }
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

    if (showMerchantDialog) {
        MerchantEditDialog(
            initialMerchant = merchant,
            onConfirm = { value ->
                merchant = value
                showMerchantDialog = false
            },
            onDismiss = { showMerchantDialog = false }
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
                    placeholder = { Text("如：聚会、咖啡") },
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
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    KairoIcons.selectableColorPalette.forEach { hex ->
                        val isSelected = selectedColor == hex
                        Box(
                            modifier = Modifier
                                .size(28.dp)
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

private fun formatCompactDateChip(epochMillis: Long): String {
    val zone = ZoneId.systemDefault()
    val date = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()
    val today = LocalDate.now(zone)
    return when (date) {
        today -> "今天"
        today.minusDays(1) -> "昨天"
        else -> if (date.year == today.year) {
            "${date.monthValue}/${date.dayOfMonth}"
        } else {
            "${date.year % 100}/${date.monthValue}/${date.dayOfMonth}"
        }
    }
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
            .clip(RoundedCornerShape(9.dp))
            .background(if (selected) catColor.copy(alpha = 0.08f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 2.5.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        CategoryIconBadge(
            iconKey = category.icon,
            colorHex = category.colorHex,
            size = 32.dp,
            iconSize = 16.dp,
            selected = selected
        )
        Text(
            text = category.name,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp, lineHeight = 13.sp),
            color = if (selected) catColor else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
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
        color = if (selected) catColor.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceContainerHigh,
        border = if (selected) BorderStroke(1.dp, catColor.copy(alpha = 0.85f)) else null,
        modifier = Modifier
            .clip(CircleShape)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = if (compact) 8.dp else 11.dp,
                vertical = if (compact) 3.5.dp else 6.dp
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = KairoIcons.iconFor(category.icon),
                contentDescription = null,
                tint = if (selected) catColor else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(if (compact) 13.dp else 15.dp)
            )
            Text(
                text = category.name,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = if (compact) 11.5.sp else 12.sp
                ),
                color = if (selected) catColor else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
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
    onSwap: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // 紧致转出 / 互换 / 转入双格卡
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
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .clickable(enabled = selectedAccount != null && selectedToAccount != null, onClick = onSwap)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.SwapHoriz,
                        contentDescription = "互换账户",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
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
                text = "至少需要 2 个账户",
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
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
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
                    CategoryIconBadge(
                        iconKey = account.icon,
                        colorHex = account.colorHex,
                        size = 24.dp,
                        iconSize = 13.dp
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
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
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
                        .height(29.dp)
                        .clip(CircleShape)
                        .clickable { onSelect(acc) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = KairoIcons.iconFor(acc.icon),
                            contentDescription = null,
                            tint = accColor,
                            modifier = Modifier.size(14.dp)
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
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    label: String,
    selected: Boolean,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit
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
        modifier = Modifier
            .height(28.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = fgColor,
                    modifier = Modifier.size(13.5.dp)
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.5.sp),
                color = if (selected) accentColor else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 110.dp)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagSelectorPanel(
    selectedTagNames: List<String>,
    availableTags: List<TagEntity>,
    localCreatedTagColors: Map<String, Long>,
    allTransactions: List<TransactionEntity>,
    onToggleTag: (String) -> Unit,
    onClearTags: () -> Unit,
    onNewTag: () -> Unit,
    onCollapse: () -> Unit
) {
    val selectedTagSet = remember(selectedTagNames) { selectedTagNames.toSet() }
    val tagColorMap = remember(availableTags, localCreatedTagColors) {
        val map = LinkedHashMap<String, Long>(availableTags.size + localCreatedTagColors.size)
        availableTags.forEach { map[it.name] = it.colorHex }
        localCreatedTagColors.forEach { (k, v) -> map[k] = v }
        map
    }
    val tagIconMap = remember(availableTags) {
        availableTags.associate { it.name to it.icon }
    }
    val displayTagNames = remember(selectedTagNames, availableTags, allTransactions, localCreatedTagColors) {
        val ordered = LinkedHashSet<String>()
        ordered.addAll(selectedTagNames)
        availableTags.sortedWith(compareBy({ it.sortOrder }, { it.id })).forEach { ordered.add(it.name) }
        localCreatedTagColors.keys.forEach { ordered.add(it) }
        allTransactions.forEach { tx ->
            if (tx.tags.isNotBlank()) {
                TagTextCodec.parse(tx.tags).forEach { ordered.add(it) }
            }
        }
        ordered.toList()
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(bottom = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (selectedTagNames.isEmpty()) "标签" else "已选 ${selectedTagNames.size} 个",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (selectedTagNames.isNotEmpty()) {
                        Text(
                            text = "清空",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable(onClick = onClearTags)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = "+ 新建",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable(onClick = onNewTag)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                    Text(
                        text = "收起",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable(onClick = onCollapse)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 98.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    displayTagNames.forEach { tagName ->
                        val isSelected = tagName in selectedTagSet
                        val tagColorHex = tagColorMap[tagName] ?: KairoIcons.fallbackTagColorHex(tagName)
                        TagBadgeChip(
                            name = tagName,
                            colorHex = tagColorHex,
                            selected = isSelected,
                            onClick = { onToggleTag(tagName) },
                            iconKey = tagIconMap[tagName].orEmpty()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountPickerDialog(
    accounts: List<AccountEntity>,
    selectedAccountId: Long,
    onSelect: (AccountEntity) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择账户", style = MaterialTheme.typography.titleLarge) },
        text = {
            if (accounts.isEmpty()) {
                Text(
                    text = "暂无可用账户",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(accounts, key = { it.id }) { acc ->
                        val isSelected = acc.id == selectedAccountId
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerLow
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onSelect(acc) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                CategoryIconBadge(
                                    iconKey = acc.icon,
                                    colorHex = acc.colorHex,
                                    size = 32.dp,
                                    iconSize = 16.dp
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = acc.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${acc.currencyCode} · 余额 ${MoneyAmount.format(acc.balanceMicros, acc.currencyCode)}",
                                        style = MaterialTheme.typography.bodySmall.tabular(),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = "已选中",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}

@Composable
private fun LedgerPickerDialog(
    ledgers: List<LedgerEntity>,
    selectedLedgerId: Long,
    onSelect: (LedgerEntity) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择账本", style = MaterialTheme.typography.titleLarge) },
        text = {
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
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}

@Composable
private fun MerchantEditDialog(
    initialMerchant: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember(initialMerchant) { mutableStateOf(initialMerchant) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("商户", style = MaterialTheme.typography.titleLarge) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("商户名称") },
                placeholder = { Text("如：星巴克") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(onClick = { onConfirm(text.trim()) }) {
                Text("确定")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (text.isNotEmpty()) {
                    TextButton(onClick = { onConfirm("") }) {
                        Text("清空")
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("取消")
                }
            }
        }
    )
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.CurrencyExchange,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text("汇率与折算", style = MaterialTheme.typography.titleLarge)
            }
        },
        text = {
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
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text("确定") }
        },
        dismissButton = {
            if (hasManualOverride) {
                TextButton(onClick = onReset) {
                    Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("重置汇率")
                }
            }
        }
    )
}

@Composable
private fun CalculatorKeypad(
    hasOperator: Boolean,
    onDigit: (String) -> Unit,
    onDot: () -> Unit,
    onOperator: (Char) -> Unit,
    onEquals: () -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    onHideKeypad: () -> Unit,
    onSave: () -> Unit,
    saveLabel: String
) {
    val gap = 4.dp
    val keyHeight = 38.dp
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        val colWidth = (maxWidth - gap * 3) / 4
        val leftGroupWidth = colWidth * 3 + gap * 2

        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                CalcKey("清零", Modifier.width(colWidth), height = keyHeight, isSecondary = true, onClick = onClear)
                CalcKey("÷", Modifier.width(colWidth), height = keyHeight, isOperator = true) { onOperator('÷') }
                CalcKey("×", Modifier.width(colWidth), height = keyHeight, isOperator = true) { onOperator('×') }
                CalcIconKey(Icons.AutoMirrored.Rounded.Backspace, "退格", Modifier.width(colWidth), height = keyHeight, onClick = onBackspace)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                CalcKey("7", Modifier.width(colWidth), height = keyHeight) { onDigit("7") }
                CalcKey("8", Modifier.width(colWidth), height = keyHeight) { onDigit("8") }
                CalcKey("9", Modifier.width(colWidth), height = keyHeight) { onDigit("9") }
                CalcKey("-", Modifier.width(colWidth), height = keyHeight, isOperator = true) { onOperator('-') }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                CalcKey("4", Modifier.width(colWidth), height = keyHeight) { onDigit("4") }
                CalcKey("5", Modifier.width(colWidth), height = keyHeight) { onDigit("5") }
                CalcKey("6", Modifier.width(colWidth), height = keyHeight) { onDigit("6") }
                CalcKey("+", Modifier.width(colWidth), height = keyHeight, isOperator = true) { onOperator('+') }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                Column(
                    modifier = Modifier.width(leftGroupWidth),
                    verticalArrangement = Arrangement.spacedBy(gap)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        CalcKey("1", Modifier.width(colWidth), height = keyHeight) { onDigit("1") }
                        CalcKey("2", Modifier.width(colWidth), height = keyHeight) { onDigit("2") }
                        CalcKey("3", Modifier.width(colWidth), height = keyHeight) { onDigit("3") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        CalcIconKey(Icons.Rounded.KeyboardHide, "收起键盘", Modifier.width(colWidth), height = keyHeight, onClick = onHideKeypad)
                        CalcKey("0", Modifier.width(colWidth), height = keyHeight) { onDigit("0") }
                        CalcKey(".", Modifier.width(colWidth), height = keyHeight, onClick = onDot)
                    }
                }
                Button(
                    onClick = if (hasOperator) onEquals else onSave,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier
                        .width(colWidth)
                        .height(keyHeight * 2 + gap)
                ) {
                    Text(
                        text = if (hasOperator) "=" else saveLabel,
                        style = if (hasOperator) {
                            MaterialTheme.typography.headlineSmall.tabular()
                        } else {
                            MaterialTheme.typography.titleMedium
                        },
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun CalcKey(
    label: String,
    modifier: Modifier = Modifier,
    height: Dp = 38.dp,
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
        shape = RoundedCornerShape(10.dp),
        color = bg,
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = if (isSecondary) {
                    MaterialTheme.typography.labelLarge
                } else {
                    MaterialTheme.typography.titleLarge.copy(fontSize = 17.sp).tabular()
                },
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
    height: Dp = 38.dp,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(19.dp)
            )
        }
    }
}
