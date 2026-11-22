package com.kairoledger.app.ui.components

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kairoledger.app.core.contract.ParsedBillDraft
import com.kairoledger.app.core.model.CurrencyCatalog
import com.kairoledger.app.core.model.MoneyAmount
import com.kairoledger.app.data.db.AccountEntity
import com.kairoledger.app.data.db.CategoryEntity
import com.kairoledger.app.data.db.LedgerEntity
import com.kairoledger.app.data.db.TransactionType
import com.kairoledger.app.ui.theme.KairoTheme
import com.kairoledger.app.ui.theme.tabular
import kotlinx.coroutines.launch

private data class IndexedDraft(
    val uid: Long,
    val draft: ParsedBillDraft
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SmartInputSheet(
    accounts: List<AccountEntity>,
    categories: List<CategoryEntity>,
    ledgers: List<LedgerEntity>,
    activeLedgerId: Long,
    baseCurrency: String,
    llmEnabled: Boolean,
    initialImagePath: String? = null,
    onParseText: suspend (String) -> List<ParsedBillDraft>,
    onParseImage: suspend (String) -> Result<ParsedBillDraft?>,
    onSaveDrafts: (List<ParsedBillDraft>, ledgerId: Long, attachmentPath: String?) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val hasInitialImage = !initialImagePath.isNullOrBlank()
    var selectedTab by remember(initialImagePath) {
        mutableIntStateOf(if (hasInitialImage) 1 else 0)
    }

    val selectableLedgers = remember(ledgers) { ledgers.filter { !it.archived } }
    var selectedLedgerId by remember(activeLedgerId) {
        mutableLongStateOf(activeLedgerId.takeIf { it > 0L } ?: 1L)
    }

    // Tab 0 状态：一句话 / 语音速记
    var inputText by remember { mutableStateOf("") }
    var isParsingText by remember { mutableStateOf(false) }
    var textParseError by remember { mutableStateOf<String?>(null) }
    var nextDraftUid by remember { mutableLongStateOf(1L) }
    var textDrafts by remember { mutableStateOf<List<IndexedDraft>>(emptyList()) }

    // Tab 1 状态：小票拍照 / 选图识别
    var currentImagePath by remember(initialImagePath) { mutableStateOf(initialImagePath) }
    var isParsingImage by remember { mutableStateOf(false) }
    var imageParseError by remember { mutableStateOf<String?>(null) }
    var imageParseGen by remember { mutableIntStateOf(0) }
    var imageDraft by remember { mutableStateOf<ParsedBillDraft?>(null) }
    var zoomImagePath by remember { mutableStateOf<String?>(null) }

    var currencyPickerTargetUid by remember { mutableStateOf<Long?>(null) } // -1L 表示小票草稿，>0L 表示文本草稿 uid

    val runImageAnalysis: (String) -> Unit = { path ->
        currentImagePath = path
        isParsingImage = true
        imageParseError = null
        coroutineScope.launch {
            val res = runCatching { onParseImage(path) }.getOrElse { Result.failure(it) }
            isParsingImage = false
            res.onSuccess { draft ->
                if (draft != null) {
                    imageParseGen++
                    imageDraft = draft
                } else {
                    imageParseError = if (llmEnabled) {
                        "未能从小票中提取出有效账单，可重新识别或手动建单。"
                    } else {
                        "尚未启用 AI 识图（可在「自动化 -> AI 设置」配置 Vision 模型），您也可手动建单。"
                    }
                }
            }.onFailure { err ->
                imageParseError = "识图失败：${err.message ?: "请检查网络与 AI 模型配置"}"
            }
        }
    }

    LaunchedEffect(initialImagePath) {
        if (!initialImagePath.isNullOrBlank()) {
            selectedTab = 1
            runImageAnalysis(initialImagePath)
        }
    }

    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                ?.trim()
            if (!spoken.isNullOrEmpty()) {
                inputText = if (inputText.isBlank()) spoken else "${inputText.trim()}，$spoken"
                textParseError = null
            }
        }
    }

    val pickReceiptLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                val saved = ReceiptImageUtils.savePickedImageToReceipts(context, uri)
                if (saved != null) {
                    runImageAnalysis(saved)
                } else {
                    imageParseError = "读取所选图片失败，请重试"
                }
            }
        }
    }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            coroutineScope.launch {
                val saved = ReceiptImageUtils.saveBitmapToReceipts(context, bitmap)
                if (saved != null) {
                    runImageAnalysis(saved)
                } else {
                    imageParseError = "保存拍照图片失败，请重试"
                }
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
                .imePadding()
        ) {
            // 顶部标题栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "AI / 语音速记",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        shape = CircleShape,
                        color = if (llmEnabled) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        }
                    ) {
                        Text(
                            text = if (llmEnabled) "AI 大模型已就绪" else "本地规则引擎",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (llmEnabled) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "关闭")
                }
            }

            // Tab 切换
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("一句话 / 语音速记") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("小票拍照 / 选图识别") }
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 多账本入账选择
                if (selectableLedgers.size > 1) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "入账账本：",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(selectableLedgers, key = { it.id }) { ledger ->
                                val selected = ledger.id == selectedLedgerId
                                FilterChip(
                                    selected = selected,
                                    onClick = { selectedLedgerId = ledger.id },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Rounded.MenuBook,
                                            contentDescription = null,
                                            tint = Color(ledger.colorHex),
                                            modifier = Modifier.size(14.dp)
                                        )
                                    },
                                    label = { Text(ledger.name) }
                                )
                            }
                        }
                    }
                }

                if (selectedTab == 0) {
                    // Tab 0: 一句话 / 语音速记
                    val sampleText = "早饭 18.5 元微信，美元订阅 USD 15 信用卡"
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                if (inputText.isBlank()) {
                                    inputText = sampleText
                                }
                            }
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = "支持一次说多笔、外币、昨日/前天及账户自动匹配：",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "示例：“$sampleText”（点击可填入体验）",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    OutlinedTextField(
                        value = inputText,
                        onValueChange = {
                            inputText = it
                            textParseError = null
                        },
                        label = { Text("输入或语音说出消费/收入/转账") },
                        placeholder = { Text(sampleText) },
                        minLines = 3,
                        maxLines = 5,
                        trailingIcon = {
                            if (inputText.isNotEmpty()) {
                                IconButton(onClick = { inputText = "" }) {
                                    Icon(Icons.Rounded.Close, contentDescription = "清空")
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                    putExtra(
                                        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                                    )
                                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
                                    putExtra(RecognizerIntent.EXTRA_PROMPT, "请说出要记的账单，可连说多笔")
                                }
                                runCatching {
                                    speechLauncher.launch(intent)
                                }.onFailure {
                                    textParseError = "当前设备未安装系统语音识别服务，请直接输入或使用输入法语音按钮。"
                                }
                            },
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Rounded.Mic, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("语音输入")
                        }

                        Button(
                            onClick = {
                                val trimmed = inputText.trim()
                                if (trimmed.isEmpty()) {
                                    textParseError = "请先输入或语音录入口语账单内容"
                                    return@Button
                                }
                                isParsingText = true
                                textParseError = null
                                coroutineScope.launch {
                                    val parsed = runCatching { onParseText(trimmed) }
                                        .onFailure { err ->
                                            textParseError = "解析异常：${err.message ?: "未知错误"}"
                                        }
                                        .getOrDefault(emptyList())
                                    isParsingText = false
                                    if (parsed.isEmpty() && textParseError == null) {
                                        textParseError = "未能识别出有效金额或流水，请补充金额与币种（如：午饭 25 元）"
                                    } else if (parsed.isNotEmpty()) {
                                        var uid = nextDraftUid
                                        textDrafts = parsed.map { item ->
                                            IndexedDraft(uid++, item)
                                        }
                                        nextDraftUid = uid
                                    }
                                }
                            },
                            enabled = !isParsingText,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            if (isParsingText) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Icon(Icons.Rounded.AutoAwesome, null, modifier = Modifier.size(18.dp))
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isParsingText) "正在解析..." else "智能解析")
                        }
                    }

                    textParseError?.let { err ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = err,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    if (textDrafts.isNotEmpty()) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "已拆出 ${textDrafts.size} 笔草稿（可逐条调整）",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            TextButton(onClick = { textDrafts = emptyList() }) {
                                Text("清空草稿")
                            }
                        }

                        textDrafts.forEachIndexed { idx, item ->
                            key(item.uid) {
                                DraftEditorCard(
                                    draftKey = item.uid,
                                    indexLabel = "#${idx + 1}",
                                    draft = item.draft,
                                    accounts = accounts,
                                    categories = categories,
                                    onUpdate = { updated ->
                                        textDrafts = textDrafts.map { existing ->
                                            if (existing.uid == item.uid) existing.copy(draft = updated) else existing
                                        }
                                    },
                                    onPickCurrency = { currencyPickerTargetUid = item.uid },
                                    onRemove = {
                                        textDrafts = textDrafts.filterNot { it.uid == item.uid }
                                    }
                                )
                            }
                        }
                    }
                } else {
                    // Tab 1: 小票拍照 / 选图识别
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                runCatching {
                                    takePictureLauncher.launch(null)
                                }.onFailure {
                                    imageParseError = "无法启动系统相机，请尝试「相册选图」"
                                }
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.CameraAlt, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("拍照识图")
                        }
                        Button(
                            onClick = {
                                pickReceiptLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.PhotoLibrary, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("相册选图")
                        }
                    }

                    currentImagePath?.let { path ->
                        YimuCard(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            cornerRadius = 16.dp
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                ReceiptThumbnailImage(
                                    path = path,
                                    modifier = Modifier
                                        .size(84.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { zoomImagePath = path }
                                )
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "小票已就绪（点击缩略图放大）",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(
                                            onClick = { runImageAnalysis(path) },
                                            enabled = !isParsingImage,
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                            modifier = Modifier.height(34.dp)
                                        ) {
                                            if (isParsingImage) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(14.dp),
                                                    strokeWidth = 2.dp
                                                )
                                            } else {
                                                Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(15.dp))
                                            }
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = if (isParsingImage) "识别中..." else "重新识别",
                                                style = MaterialTheme.typography.labelMedium
                                            )
                                        }
                                        if (imageDraft == null && !isParsingImage) {
                                            TextButton(
                                                onClick = {
                                                    val defaultAcc = accounts.firstOrNull()
                                                    val defaultCat = categories.firstOrNull {
                                                        it.type == TransactionType.EXPENSE && it.parentId == null
                                                    }
                                                    imageParseGen++
                                                    imageDraft = ParsedBillDraft(
                                                        type = TransactionType.EXPENSE,
                                                        origAmountMicros = 0L,
                                                        origCurrency = defaultAcc?.currencyCode ?: baseCurrency,
                                                        accountId = defaultAcc?.id,
                                                        categoryId = defaultCat?.id,
                                                        categoryName = defaultCat?.name ?: "",
                                                        parserUsed = "RECEIPT_MANUAL"
                                                    )
                                                    imageParseError = null
                                                },
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                                modifier = Modifier.height(34.dp)
                                            ) {
                                                Text("手动建单", style = MaterialTheme.typography.labelMedium)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    imageParseError?.let { err ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = err,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    imageDraft?.let { draft ->
                        key(imageParseGen) {
                            DraftEditorCard(
                                draftKey = "img_$imageParseGen",
                                indexLabel = "识图结果",
                                draft = draft,
                                accounts = accounts,
                                categories = categories,
                                onUpdate = { imageDraft = it },
                                onPickCurrency = { currencyPickerTargetUid = -1L },
                                onRemove = null
                            )
                        }
                    }
                }
            }

            // 底部确认入账按钮
            val validTextDrafts = textDrafts.map { it.draft }.filter { it.origAmountMicros > 0L }
            val currentReceiptDraft = imageDraft?.takeIf { it.origAmountMicros > 0L }
            val canSubmit = (selectedTab == 0 && validTextDrafts.isNotEmpty()) ||
                (selectedTab == 1 && currentReceiptDraft != null)

            if ((selectedTab == 0 && textDrafts.isNotEmpty()) || (selectedTab == 1 && imageDraft != null)) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("取消")
                    }
                    Button(
                        onClick = {
                            if (selectedTab == 0 && validTextDrafts.isNotEmpty()) {
                                onSaveDrafts(validTextDrafts, selectedLedgerId, null)
                                onDismiss()
                            } else if (selectedTab == 1 && currentReceiptDraft != null) {
                                onSaveDrafts(listOf(currentReceiptDraft), selectedLedgerId, currentImagePath)
                                onDismiss()
                            }
                        },
                        enabled = canSubmit,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.Check, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (selectedTab == 0) {
                                "全部入账 (${validTextDrafts.size} 笔)"
                            } else {
                                "确认保存入账"
                            },
                            fontWeight = FontWeight.Bold
                        )
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

    currencyPickerTargetUid?.let { targetUid ->
        val selectedCode = if (targetUid == -1L) {
            imageDraft?.origCurrency ?: baseCurrency
        } else {
            textDrafts.firstOrNull { it.uid == targetUid }?.draft?.origCurrency ?: baseCurrency
        }
        CurrencyPickerDialog(
            selectedCode = selectedCode,
            onSelect = { code ->
                if (targetUid == -1L) {
                    imageDraft = imageDraft?.copy(origCurrency = code)
                } else {
                    textDrafts = textDrafts.map { item ->
                        if (item.uid == targetUid) {
                            item.copy(draft = item.draft.copy(origCurrency = code))
                        } else {
                            item
                        }
                    }
                }
            },
            onDismiss = { currencyPickerTargetUid = null }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DraftEditorCard(
    draftKey: Any,
    indexLabel: String,
    draft: ParsedBillDraft,
    accounts: List<AccountEntity>,
    categories: List<CategoryEntity>,
    onUpdate: (ParsedBillDraft) -> Unit,
    onPickCurrency: () -> Unit,
    onRemove: (() -> Unit)?
) {
    val semantic = KairoTheme.semantic
    var amountText by remember(draftKey) {
        mutableStateOf(
            if (draft.origAmountMicros > 0L) {
                val digits = CurrencyCatalog.find(draft.origCurrency).minorDigits
                MoneyAmount.formatPlain(draft.origAmountMicros, digits, grouping = false)
            } else {
                ""
            }
        )
    }
    var showAccountMenu by remember { mutableStateOf(false) }
    var showCategoryMenu by remember { mutableStateOf(false) }

    val selectedAccount = accounts.firstOrNull { it.id == draft.accountId } ?: accounts.firstOrNull()
    val typedCategories = remember(categories, draft.type) {
        val typed = categories.filter { it.type == draft.type }
        val parents = typed.filter { it.parentId == null }.sortedWith(compareBy({ it.sortOrder }, { it.id }))
        val childMap = typed.filter { it.parentId != null }.groupBy { it.parentId!! }
        val parentIds = parents.map { it.id }.toSet()
        val treeList = parents.flatMap { p ->
            listOf(p) + childMap[p.id].orEmpty().sortedWith(compareBy({ it.sortOrder }, { it.id }))
        }
        val orphans = typed.filter { it.parentId != null && it.parentId !in parentIds }
        treeList + orphans
    }
    val selectedCategory = categories.firstOrNull { it.id == draft.categoryId }

    YimuCard(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        cornerRadius = 16.dp
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 头部：编号 + 类型切换 + 删除按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = indexLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                    listOf(
                        TransactionType.EXPENSE to "支出",
                        TransactionType.INCOME to "收入",
                        TransactionType.TRANSFER to "转账"
                    ).forEach { (mode, label) ->
                        val selected = draft.type == mode
                        val pillColor = when {
                            !selected -> MaterialTheme.colorScheme.surfaceContainerHigh
                            mode == TransactionType.EXPENSE -> semantic.expenseContainer
                            mode == TransactionType.INCOME -> semantic.incomeContainer
                            else -> semantic.transferContainer
                        }
                        val txtColor = when {
                            !selected -> MaterialTheme.colorScheme.onSurfaceVariant
                            mode == TransactionType.EXPENSE -> semantic.expense
                            mode == TransactionType.INCOME -> semantic.income
                            else -> semantic.transfer
                        }
                        Surface(
                            shape = CircleShape,
                            color = pillColor,
                            modifier = Modifier.clickable {
                                val nextCat = if (mode == TransactionType.TRANSFER) {
                                    null
                                } else {
                                    categories.firstOrNull { it.id == draft.categoryId && it.type == mode }
                                        ?: categories.firstOrNull { it.type == mode && it.parentId == null }
                                }
                                onUpdate(
                                    draft.copy(
                                        type = mode,
                                        categoryId = nextCat?.id,
                                        categoryName = nextCat?.name ?: ""
                                    )
                                )
                            }
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium,
                                color = txtColor,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                if (onRemove != null) {
                    IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "移除此条",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // 金额 + 币种
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CurrencyBadge(
                    currencyCode = draft.origCurrency,
                    selected = true,
                    onClick = onPickCurrency
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { input ->
                        amountText = input
                        if (input.isBlank()) {
                            onUpdate(draft.copy(origAmountMicros = 0L))
                        } else {
                            val parsed = MoneyAmount.parseToMicros(input)
                            if (parsed != null && parsed >= 0L) {
                                onUpdate(draft.copy(origAmountMicros = parsed))
                            }
                        }
                    },
                    label = { Text("金额") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    textStyle = MaterialTheme.typography.titleMedium.tabular(),
                    modifier = Modifier.weight(1f)
                )
            }

            // 分类 + 账户选择胶囊
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (draft.type != TransactionType.TRANSFER) {
                    Box {
                        FilterChip(
                            selected = selectedCategory != null,
                            onClick = { showCategoryMenu = true },
                            leadingIcon = {
                                Icon(
                                    imageVector = selectedCategory?.let { KairoIcons.iconFor(it.icon) }
                                        ?: Icons.Rounded.Category,
                                    contentDescription = null,
                                    tint = selectedCategory?.let { Color(it.colorHex) }
                                        ?: MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            trailingIcon = {
                                Icon(Icons.Rounded.ArrowDropDown, null, modifier = Modifier.size(16.dp))
                            },
                            label = {
                                Text(selectedCategory?.name ?: draft.categoryName.ifBlank { "选择分类" })
                            }
                        )
                        DropdownMenu(
                            expanded = showCategoryMenu,
                            onDismissRequest = { showCategoryMenu = false }
                        ) {
                            typedCategories.forEach { cat ->
                                DropdownMenuItem(
                                    text = {
                                        Text(if (cat.parentId != null) "  └ ${cat.name}" else cat.name)
                                    },
                                    onClick = {
                                        onUpdate(draft.copy(categoryId = cat.id, categoryName = cat.name))
                                        showCategoryMenu = false
                                    }
                                )
                            }
                        }
                    }
                }

                Box {
                    FilterChip(
                        selected = selectedAccount != null,
                        onClick = { showAccountMenu = true },
                        leadingIcon = {
                            Icon(Icons.Rounded.AccountBalanceWallet, null, modifier = Modifier.size(16.dp))
                        },
                        trailingIcon = {
                            Icon(Icons.Rounded.ArrowDropDown, null, modifier = Modifier.size(16.dp))
                        },
                        label = {
                            Text(
                                selectedAccount?.let { "${it.name} (${it.currencyCode})" }
                                    ?: draft.accountHint.ifBlank { "选择账户" },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    )
                    DropdownMenu(
                        expanded = showAccountMenu,
                        onDismissRequest = { showAccountMenu = false }
                    ) {
                        accounts.forEach { acc ->
                            DropdownMenuItem(
                                text = { Text("${acc.name} (${acc.currencyCode})") },
                                onClick = {
                                    onUpdate(draft.copy(accountId = acc.id, accountHint = acc.name))
                                    showAccountMenu = false
                                }
                            )
                        }
                    }
                }
            }

            // 商户 & 备注
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = draft.merchant,
                    onValueChange = { onUpdate(draft.copy(merchant = it)) },
                    label = { Text("商户") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = draft.note,
                    onValueChange = { onUpdate(draft.copy(note = it)) },
                    label = { Text("备注") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            Text(
                text = "时间：${KairoFormats.formatDateTime(draft.timestamp)} · 解析：${draft.parserUsed}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
