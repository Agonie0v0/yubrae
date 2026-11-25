package com.yubrae.app.ui.components

import android.Manifest
import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.SettingsVoice
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yubrae.app.capture.DeviceRomProfile
import com.yubrae.app.capture.SpeechEngineInfo
import com.yubrae.app.capture.SpeechErrorDiagnosis
import com.yubrae.app.capture.SpeechErrorKind
import com.yubrae.app.capture.SpeechInputHelper
import com.yubrae.app.core.contract.ParsedBillDraft
import com.yubrae.app.core.model.CurrencyCatalog
import com.yubrae.app.core.model.MoneyAmount
import com.yubrae.app.data.db.AccountEntity
import com.yubrae.app.data.db.CategoryEntity
import com.yubrae.app.data.db.LedgerEntity
import com.yubrae.app.data.db.TransactionType
import com.yubrae.app.ui.theme.KairoTheme
import com.yubrae.app.ui.theme.tabular
import kotlinx.coroutines.launch

private data class IndexedDraft(
    val uid: Long,
    val draft: ParsedBillDraft
)

private val SMART_INPUT_SAMPLES = listOf(
    "午饭 35 微信",
    "打车 18 支付宝",
    "USD 15 订阅 信用卡"
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

    val selectableLedgers = remember(ledgers) {
        ledgers.filter { !it.archived }.sortedWith(compareBy({ it.sortOrder }, { it.id }))
    }
    val fallbackLedgerId = remember(ledgers, selectableLedgers) {
        selectableLedgers.firstOrNull()?.id ?: ledgers.firstOrNull()?.id ?: 0L
    }
    var selectedLedgerId by remember(activeLedgerId) {
        val resolved = activeLedgerId.takeIf { id ->
            id > 0L && selectableLedgers.any { it.id == id }
        } ?: fallbackLedgerId
        mutableLongStateOf(resolved)
    }
    LaunchedEffect(selectableLedgers, activeLedgerId) {
        val valid = selectedLedgerId > 0L && selectableLedgers.any { it.id == selectedLedgerId }
        if (!valid && fallbackLedgerId > 0L) {
            selectedLedgerId = fallbackLedgerId
        }
    }

    // Tab 0 状态：一句话 / 语音速记
    var inputText by remember { mutableStateOf("") }
    var isParsingText by remember { mutableStateOf(false) }
    var textParseError by remember { mutableStateOf<String?>(null) }
    var nextDraftUid by remember { mutableLongStateOf(1L) }
    var textDrafts by remember { mutableStateOf<List<IndexedDraft>>(emptyList()) }

    // 语音识别状态
    val isXiaomiRom = remember { DeviceRomProfile.detectCurrent().isXiaomiFamily }
    var activeSpeechRecognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }
    var speechSessionGen by remember { mutableIntStateOf(0) }
    var isListeningDialogVisible by remember { mutableStateOf(false) }
    var isFinishingSpeech by remember { mutableStateOf(false) }
    var listeningRmsDb by remember { mutableFloatStateOf(0f) }
    var listeningPartialText by remember { mutableStateOf("") }
    var activeSpeechEngine by remember { mutableStateOf<SpeechEngineInfo?>(null) }
    var speechDiagnosis by remember { mutableStateOf<SpeechErrorDiagnosis?>(null) }
    var canReRequestMicPermission by remember { mutableStateOf(false) }

    // Tab 1 状态：小票拍照 / 选图识别
    var currentImagePath by remember(initialImagePath) { mutableStateOf(initialImagePath) }
    var isParsingImage by remember { mutableStateOf(false) }
    var imageParseError by remember { mutableStateOf<String?>(null) }
    var imageParseGen by remember { mutableIntStateOf(0) }
    var imageDraft by remember { mutableStateOf<ParsedBillDraft?>(null) }
    var zoomImagePath by remember { mutableStateOf<String?>(null) }

    var currencyPickerTargetUid by remember { mutableStateOf<Long?>(null) } // -1L 表示小票草稿，>0L 表示文本草稿 uid

    val runImageAnalysis: (String) -> Unit = { path ->
        val reqGen = ++imageParseGen
        currentImagePath = path
        isParsingImage = true
        imageParseError = null
        coroutineScope.launch {
            val res = runCatching { onParseImage(path) }.getOrElse { Result.failure(it) }
            if (reqGen != imageParseGen) return@launch
            isParsingImage = false
            res.onSuccess { draft ->
                if (draft != null) {
                    imageParseGen++
                    imageDraft = draft
                } else {
                    imageParseError = if (llmEnabled) {
                        "未识别出有效账单，可重试或手动建单"
                    } else {
                        "未启用 AI 识图，可直接「手动建单」"
                    }
                }
            }.onFailure { err ->
                imageParseError = "识图失败：${err.message ?: "请检查网络与 AI 配置"}"
            }
        }
    }

    LaunchedEffect(initialImagePath) {
        if (!initialImagePath.isNullOrBlank()) {
            selectedTab = 1
            runImageAnalysis(initialImagePath)
        }
    }

    val appendRecognizedSpeech: (String) -> Unit = { raw ->
        val spoken = raw.trim()
        if (spoken.isNotEmpty()) {
            inputText = if (inputText.isBlank()) spoken else "${inputText.trim()}，$spoken"
            textParseError = null
            speechDiagnosis = null
            canReRequestMicPermission = false
        }
    }

    val cleanupSpeechRecognizer: (Boolean) -> Unit = { cancelActive ->
        speechSessionGen++
        val recognizer = activeSpeechRecognizer
        activeSpeechRecognizer = null
        isListeningDialogVisible = false
        isFinishingSpeech = false
        listeningRmsDb = 0f
        if (recognizer != null) {
            runCatching {
                if (cancelActive) recognizer.cancel()
            }
            runCatching { recognizer.destroy() }
        }
    }

    DisposableEffect(selectedTab) {
        onDispose {
            cleanupSpeechRecognizer(true)
        }
    }

    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val engine = SpeechInputHelper.resolveActiveSpeechEngine(context)
        activeSpeechEngine = engine
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                ?.trim()
            if (!spoken.isNullOrEmpty()) {
                appendRecognizedSpeech(spoken)
            } else {
                canReRequestMicPermission = false
                speechDiagnosis = SpeechInputHelper.diagnoseActivityResultError(
                    resultCode = RecognizerIntent.RESULT_NO_MATCH,
                    engine = engine,
                    isXiaomiRom = isXiaomiRom
                )
            }
        } else {
            canReRequestMicPermission = false
            speechDiagnosis = SpeechInputHelper.diagnoseActivityResultError(
                resultCode = result.resultCode,
                engine = engine,
                isXiaomiRom = isXiaomiRom
            )
        }
    }

    val launchSystemSpeechDialog: () -> Unit = {
        cleanupSpeechRecognizer(true)
        val engine = SpeechInputHelper.resolveActiveSpeechEngine(context)
        activeSpeechEngine = engine
        speechDiagnosis = null
        canReRequestMicPermission = false
        val intent = SpeechInputHelper.buildRecognitionIntent(callingPackage = context.packageName)
        runCatching {
            speechLauncher.launch(intent)
        }.onFailure {
            speechDiagnosis = SpeechInputHelper.diagnoseSpeechRecognizerError(
                errorCode = SpeechRecognizer.ERROR_CLIENT,
                engine = engine,
                isXiaomiRom = isXiaomiRom
            ).copy(
                title = "未找到可用的系统语音服务",
                message = "当前设备未检测到可用的系统语音识别弹窗或服务，请检查语音引擎设置或直接使用输入法语音。",
                showVoiceSettingsAction = true,
                showSystemDialogFallback = false
            )
        }
    }

    val startInAppSpeechRecognition: () -> Unit = {
        cleanupSpeechRecognizer(true)
        val engine = SpeechInputHelper.resolveActiveSpeechEngine(context)
        activeSpeechEngine = engine
        speechDiagnosis = null
        canReRequestMicPermission = false

        if (!SpeechInputHelper.isInAppSpeechAvailable(context)) {
            launchSystemSpeechDialog()
        } else {
            val sessionId = ++speechSessionGen
            listeningPartialText = ""
            listeningRmsDb = 0f
            isFinishingSpeech = false
            isListeningDialogVisible = true

            val createRes = runCatching {
                val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
                recognizer.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {}

                    override fun onBeginningOfSpeech() {}

                    override fun onRmsChanged(rmsdB: Float) {
                        if (sessionId == speechSessionGen) {
                            listeningRmsDb = rmsdB
                        }
                    }

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        if (sessionId == speechSessionGen) {
                            isFinishingSpeech = true
                        }
                    }

                    override fun onError(error: Int) {
                        if (sessionId != speechSessionGen) return
                        val partialFallback = listeningPartialText.trim()
                        val shouldRescuePartial = isFinishingSpeech &&
                            partialFallback.isNotEmpty() &&
                            (error == SpeechRecognizer.ERROR_NO_MATCH ||
                                error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT)
                        cleanupSpeechRecognizer(false)
                        if (shouldRescuePartial) {
                            appendRecognizedSpeech(partialFallback)
                        } else {
                            speechDiagnosis = SpeechInputHelper.diagnoseSpeechRecognizerError(
                                errorCode = error,
                                engine = engine,
                                isXiaomiRom = isXiaomiRom
                            )
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        if (sessionId != speechSessionGen) return
                        val spoken = results
                            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull()
                            ?.trim()
                            ?.takeIf { it.isNotEmpty() }
                            ?: listeningPartialText.trim()
                        cleanupSpeechRecognizer(false)
                        if (spoken.isNotEmpty()) {
                            appendRecognizedSpeech(spoken)
                        } else {
                            speechDiagnosis = SpeechInputHelper.diagnoseSpeechRecognizerError(
                                errorCode = SpeechRecognizer.ERROR_NO_MATCH,
                                engine = engine,
                                isXiaomiRom = isXiaomiRom
                            )
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        if (sessionId != speechSessionGen) return
                        val partial = partialResults
                            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull()
                            ?.trim()
                        if (!partial.isNullOrEmpty()) {
                            listeningPartialText = partial
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
                activeSpeechRecognizer = recognizer
                recognizer.startListening(
                    SpeechInputHelper.buildRecognitionIntent(
                        callingPackage = context.packageName,
                        partialResults = true
                    )
                )
            }
            createRes.onFailure {
                cleanupSpeechRecognizer(true)
                speechDiagnosis = SpeechInputHelper.diagnoseSpeechRecognizerError(
                    errorCode = SpeechRecognizer.ERROR_CLIENT,
                    engine = engine,
                    isXiaomiRom = isXiaomiRom
                )
            }
        }
    }

    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            canReRequestMicPermission = false
            speechDiagnosis = null
            startInAppSpeechRecognition()
        } else {
            val engine = SpeechInputHelper.resolveActiveSpeechEngine(context)
            activeSpeechEngine = engine
            canReRequestMicPermission = true
            speechDiagnosis = SpeechErrorDiagnosis(
                kind = SpeechErrorKind.PERMISSION_DENIED,
                title = "需要麦克风录音权限",
                message = "未获得录音权限，无法启动语音记账。请允许「余白」访问麦克风后继续。",
                guideSteps = listOf(
                    "1. 点击下方「重新申请」，在系统权限弹窗中选择「仅在使用中允许」",
                    "2. 若系统已勾选不再询问，请点击「前往应用权限设置」手动开启「麦克风」权限"
                ),
                showAppPermissionAction = true,
                showEngineSettingsAction = false,
                showVoiceSettingsAction = false,
                showSystemDialogFallback = false
            )
        }
    }

    val onVoiceInputClick: () -> Unit = {
        textParseError = null
        if (SpeechInputHelper.hasRecordAudioPermission(context)) {
            startInAppSpeechRecognition()
        } else {
            recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val handlePickedReceiptUri: (Uri?) -> Unit = { uri ->
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

    val pickReceiptLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = handlePickedReceiptUri
    )

    val fallbackImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = handlePickedReceiptUri
    )

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
        onDismissRequest = {
            cleanupSpeechRecognizer(true)
            onDismiss()
        },
        sheetState = sheetState,
        dragHandle = null,
        shape = YubraeSheetShape,
        containerColor = yubraeSheetContainerColor()
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
                    if (llmEnabled) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = "AI",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }
                IconButton(
                    onClick = {
                        cleanupSpeechRecognizer(true)
                        onDismiss()
                    }
                ) {
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
                            text = "账本",
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
                                            imageVector = KairoIcons.ledgerIcon(ledger.icon),
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
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = {
                            inputText = it
                            textParseError = null
                        },
                        placeholder = { Text("如：午饭35、打车18") },
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

                    if (textDrafts.isEmpty()) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            items(SMART_INPUT_SAMPLES, key = { it }) { sample ->
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .clickable {
                                            inputText = sample
                                            textParseError = null
                                        }
                                ) {
                                    Text(
                                        text = sample,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onVoiceInputClick,
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
                                        textParseError = "未识别出有效金额，请补充金额（如：午饭 25）"
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

                    speechDiagnosis?.let { diagnosis ->
                        SpeechDiagnosisCard(
                            diagnosis = diagnosis,
                            engine = activeSpeechEngine,
                            showRequestPermission = canReRequestMicPermission &&
                                !SpeechInputHelper.hasRecordAudioPermission(context),
                            onDismiss = {
                                speechDiagnosis = null
                                canReRequestMicPermission = false
                            },
                            onRequestPermission = {
                                recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            },
                            onOpenAppPermission = {
                                SpeechInputHelper.openAppPermissionSettings(context)
                            },
                            onOpenEngineSettings = {
                                SpeechInputHelper.openSpeechEngineAppDetails(
                                    context = context,
                                    packageName = activeSpeechEngine?.packageName
                                )
                            },
                            onOpenVoiceSettings = {
                                SpeechInputHelper.openVoiceInputSettings(context)
                            },
                            onFallbackSystemDialog = launchSystemSpeechDialog
                        )
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
                                text = "已识别 ${textDrafts.size} 笔",
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
                                runCatching {
                                    pickReceiptLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                }.onFailure {
                                    runCatching {
                                        fallbackImageLauncher.launch("image/*")
                                    }.onFailure {
                                        imageParseError = "无法打开系统相册，请检查存储或相册权限后重试"
                                    }
                                }
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
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                ReceiptThumbnailImage(
                                    path = path,
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { zoomImagePath = path }
                                )
                                FlowRow(
                                    modifier = Modifier.weight(1f),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
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
                                            Icon(Icons.Rounded.AutoAwesome, null, modifier = Modifier.size(15.dp))
                                        }
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = if (isParsingImage) "识别中..." else "识图解析",
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    }
                                    TextButton(
                                        onClick = {
                                            imageParseGen++
                                            currentImagePath = null
                                            imageDraft = null
                                            imageParseError = null
                                            isParsingImage = false
                                        },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        modifier = Modifier.height(34.dp)
                                    ) {
                                        Text(
                                            text = "移除",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.error
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
                            val targetLedgerId = selectedLedgerId.takeIf { id ->
                                id > 0L && ledgers.any { it.id == id }
                            } ?: fallbackLedgerId
                            if (selectedTab == 0 && validTextDrafts.isNotEmpty()) {
                                onSaveDrafts(validTextDrafts, targetLedgerId, null)
                                onDismiss()
                            } else if (selectedTab == 1 && currentReceiptDraft != null) {
                                onSaveDrafts(listOf(currentReceiptDraft), targetLedgerId, currentImagePath)
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

    if (isListeningDialogVisible) {
        SpeechListeningDialog(
            engineLabel = activeSpeechEngine?.label?.takeIf { it.isNotBlank() } ?: "系统语音引擎",
            rmsDb = listeningRmsDb,
            partialText = listeningPartialText,
            isFinishing = isFinishingSpeech,
            onCancel = { cleanupSpeechRecognizer(true) },
            onFinish = {
                val recognizer = activeSpeechRecognizer
                if (recognizer == null) {
                    cleanupSpeechRecognizer(false)
                } else {
                    isFinishingSpeech = true
                    runCatching { recognizer.stopListening() }
                        .onFailure {
                            val rescued = listeningPartialText.trim()
                            cleanupSpeechRecognizer(true)
                            if (rescued.isNotEmpty()) {
                                appendRecognizedSpeech(rescued)
                            }
                        }
                }
            }
        )
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
                text = KairoFormats.formatDateTime(draft.timestamp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SpeechListeningDialog(
    engineLabel: String,
    rmsDb: Float,
    partialText: String,
    isFinishing: Boolean,
    onCancel: () -> Unit,
    onFinish: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "voice_pulse")
    val idleBreath by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 950, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "voice_idle_breath"
    )
    val normalizedRms = ((rmsDb.coerceIn(-2f, 12f) + 2f) / 14f)
    val rmsScale by animateFloatAsState(
        targetValue = 1.0f + normalizedRms * 0.42f,
        animationSpec = tween(durationMillis = 120, easing = FastOutSlowInEasing),
        label = "voice_rms_scale"
    )
    val outerScale = maxOf(idleBreath, rmsScale)
    val innerScale = 1.0f + (outerScale - 1.0f) * 0.55f

    YubraeDialog(
        onDismissRequest = onCancel,
        title = "正在聆听语音记账",
        subtitle = engineLabel,
        icon = Icons.Rounded.Mic,
        confirmButton = {
            Button(
                onClick = onFinish,
                enabled = !isFinishing,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(if (isFinishing) "识别中..." else "说完了")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onCancel,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("取消")
            }
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier.size(112.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(84.dp)
                        .graphicsLayer {
                            scaleX = outerScale
                            scaleY = outerScale
                        }
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                )
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .graphicsLayer {
                            scaleX = innerScale
                            scaleY = innerScale
                        }
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.24f))
                )
                Surface(
                    modifier = Modifier.size(54.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    shadowElevation = 4.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Mic,
                            contentDescription = "正在聆听",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(
                    width = 0.6.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                )
            ) {
                val hasText = partialText.isNotBlank()
                Text(
                    text = if (hasText) {
                        partialText
                    } else {
                        "试试说：午饭花了35元，打车18元"
                    },
                    style = if (hasText) {
                        MaterialTheme.typography.bodyLarge
                    } else {
                        MaterialTheme.typography.bodyMedium
                    },
                    color = if (hasText) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 14.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SpeechDiagnosisCard(
    diagnosis: SpeechErrorDiagnosis,
    engine: SpeechEngineInfo?,
    showRequestPermission: Boolean,
    onDismiss: () -> Unit,
    onRequestPermission: () -> Unit,
    onOpenAppPermission: () -> Unit,
    onOpenEngineSettings: () -> Unit,
    onOpenVoiceSettings: () -> Unit,
    onFallbackSystemDialog: () -> Unit
) {
    val semantic = KairoTheme.semantic
    val isHintOnly = diagnosis.kind == SpeechErrorKind.NO_SPEECH_OR_TIMEOUT
    val isEngineAuth = diagnosis.kind == SpeechErrorKind.ENGINE_AUTHORIZATION_OR_SERVICE

    val containerColor = when {
        isHintOnly -> MaterialTheme.colorScheme.surfaceContainerLow
        isEngineAuth -> semantic.warningContainer.copy(alpha = 0.62f)
        else -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f)
    }
    val accentColor = when {
        isHintOnly -> MaterialTheme.colorScheme.primary
        isEngineAuth -> semantic.warning
        else -> MaterialTheme.colorScheme.error
    }
    val headerIcon = when (diagnosis.kind) {
        SpeechErrorKind.PERMISSION_DENIED -> Icons.Rounded.MicOff
        SpeechErrorKind.ENGINE_AUTHORIZATION_OR_SERVICE -> Icons.Rounded.WarningAmber
        SpeechErrorKind.NO_SPEECH_OR_TIMEOUT -> Icons.Rounded.Info
        SpeechErrorKind.NETWORK_OR_UNAVAILABLE -> Icons.Rounded.SettingsVoice
        SpeechErrorKind.UNKNOWN -> Icons.Rounded.WarningAmber
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        border = BorderStroke(
            width = 0.6.dp,
            color = accentColor.copy(alpha = 0.30f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    modifier = Modifier.size(30.dp),
                    shape = RoundedCornerShape(9.dp),
                    color = accentColor.copy(alpha = 0.16f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = headerIcon,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = diagnosis.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (engine != null && !isHintOnly) {
                        Text(
                            text = "当前引擎：${engine.label} (${engine.packageName})",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(26.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "关闭提示",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Text(
                text = diagnosis.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (diagnosis.guideSteps.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    diagnosis.guideSteps.forEach { step ->
                        Text(
                            text = step,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            val hasActions = showRequestPermission ||
                diagnosis.showAppPermissionAction ||
                diagnosis.showEngineSettingsAction ||
                diagnosis.showVoiceSettingsAction ||
                diagnosis.showSystemDialogFallback

            if (hasActions) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (showRequestPermission) {
                        FilledTonalButton(
                            onClick = onRequestPermission,
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text("重新申请", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    if (diagnosis.showAppPermissionAction) {
                        OutlinedButton(
                            onClick = onOpenAppPermission,
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text("前往应用权限设置", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    if (diagnosis.showEngineSettingsAction) {
                        FilledTonalButton(
                            onClick = onOpenEngineSettings,
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text("去授权语音引擎", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    if (diagnosis.showVoiceSettingsAction) {
                        OutlinedButton(
                            onClick = onOpenVoiceSettings,
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text("系统语音设置", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    if (diagnosis.showSystemDialogFallback) {
                        OutlinedButton(
                            onClick = onFallbackSystemDialog,
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text("系统语音弹窗", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
}
