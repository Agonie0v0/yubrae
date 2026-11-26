package com.yubrae.app.ui.screens.settings

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Rule
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yubrae.app.capture.rules.CloudRuleRepository
import com.yubrae.app.capture.rules.CloudRuleStatus
import com.yubrae.app.capture.rules.JsRuleMeta
import com.yubrae.app.capture.rules.JsRuleSource
import com.yubrae.app.core.model.MoneyAmount
import com.yubrae.app.data.db.AccountAliasEntity
import com.yubrae.app.data.db.AccountEntity
import com.yubrae.app.data.db.AliasMatchType
import com.yubrae.app.data.db.CaptureChannel
import com.yubrae.app.data.db.ParseRuleEntity
import com.yubrae.app.data.db.TransactionType
import com.yubrae.app.data.prefs.AppSettings
import com.yubrae.app.ui.components.CompactHairlineDivider
import com.yubrae.app.ui.components.CompactSwitchRow
import com.yubrae.app.ui.components.ConfirmActionDialog
import com.yubrae.app.ui.components.EmptyStateCard
import com.yubrae.app.ui.components.KairoFormats
import com.yubrae.app.ui.components.YimuCard
import com.yubrae.app.ui.components.YubraeDialog
import com.yubrae.app.ui.theme.KairoTheme
import com.yubrae.app.ui.theme.tabular
import com.yubrae.app.ui.viewmodel.KairoMainViewModel
import com.yubrae.app.ui.viewmodel.SandboxTestUiState
import java.time.Instant
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RulesAndAliasesScreen(
    viewModel: KairoMainViewModel,
    onBack: () -> Unit
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val cloudRuleStatus by viewModel.cloudRuleStatus.collectAsStateWithLifecycle()
    val cloudRuleSyncing by viewModel.cloudRuleSyncing.collectAsStateWithLifecycle()
    val aliases by viewModel.accountAliases.collectAsStateWithLifecycle()
    val rules by viewModel.parseRules.collectAsStateWithLifecycle()
    val accounts by viewModel.activeAccounts.collectAsStateWithLifecycle()
    val sandboxState by viewModel.sandboxState.collectAsStateWithLifecycle()

    val accountMap = remember(accounts) { accounts.associateBy { it.id } }

    var selectedTab by remember { mutableIntStateOf(0) }
    var editingAlias by remember { mutableStateOf<AccountAliasEntity?>(null) }
    var showCreateAlias by remember { mutableStateOf(false) }
    var deleteAliasCandidate by remember { mutableStateOf<AccountAliasEntity?>(null) }

    var editingRule by remember { mutableStateOf<ParseRuleEntity?>(null) }
    var showCreateRule by remember { mutableStateOf(false) }
    var deleteRuleCandidate by remember { mutableStateOf<ParseRuleEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("指纹与规则") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        floatingActionButton = {
            when (selectedTab) {
                2 -> FloatingActionButton(onClick = { showCreateAlias = true }) {
                    Icon(Icons.Rounded.Add, contentDescription = "新增指纹")
                }
                3 -> FloatingActionButton(onClick = { showCreateRule = true }) {
                    Icon(Icons.Rounded.Add, contentDescription = "新增规则")
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                edgePadding = 12.dp
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("云端规则库", maxLines = 1, softWrap = false) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("文本沙盒", maxLines = 1, softWrap = false) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("账户指纹 (${aliases.size})", maxLines = 1, softWrap = false) }
                )
                Tab(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    text = { Text("正则规则 (${rules.size})", maxLines = 1, softWrap = false) }
                )
            }

            when (selectedTab) {
                0 -> CloudRulesTab(
                    settings = settings,
                    status = cloudRuleStatus,
                    isSyncing = cloudRuleSyncing,
                    onToggleJsRules = { enabled ->
                        viewModel.updateSettings { it.copy(jsRulesEnabled = enabled) }
                    },
                    onToggleOcrFallback = { enabled ->
                        viewModel.updateSettings { it.copy(jsOcrFallbackEnabled = enabled) }
                    },
                    onToggleCategoryFallback = { enabled ->
                        viewModel.updateSettings { it.copy(jsCategoryFallbackEnabled = enabled) }
                    },
                    onCheckUpdate = { viewModel.checkAndUpdateCloudRules(force = false) },
                    onImportZip = { fileName, bytes -> viewModel.importLocalRuleZip(fileName, bytes) },
                    onResetBuiltin = { viewModel.resetCloudRulesToBuiltin() },
                    onValidateSubscriptionUrl = { raw -> viewModel.validateCloudRuleManifestUrl(raw) },
                    onSaveSubscriptionUrl = { raw -> viewModel.saveCloudRuleManifestUrl(raw) },
                    onShowMessage = { msg -> viewModel.showMessage(msg) }
                )
                1 -> SandboxTestTab(
                    sandboxState = sandboxState,
                    accountMap = accountMap,
                    onRunTest = { title, content, pkg, allowLlm, channel ->
                        viewModel.runSandboxTest(title, content, pkg, allowLlm, channel)
                    }
                )
                2 -> AliasesListTab(
                    aliases = aliases,
                    accountMap = accountMap,
                    onToggleEnabled = { alias, enabled ->
                        viewModel.saveAlias(alias.copy(enabled = enabled))
                    },
                    onCreateAlias = { showCreateAlias = true },
                    onEdit = { editingAlias = it },
                    onDelete = { deleteAliasCandidate = it }
                )
                else -> RulesListTab(
                    rules = rules,
                    onToggleEnabled = { rule, enabled ->
                        viewModel.saveRule(rule.copy(enabled = enabled))
                    },
                    onCreateRule = { showCreateRule = true },
                    onEdit = { editingRule = it },
                    onDelete = { deleteRuleCandidate = it }
                )
            }
        }
    }

    if (showCreateAlias || editingAlias != null) {
        AliasEditorDialog(
            existing = editingAlias,
            accounts = accounts,
            onSave = { alias ->
                viewModel.saveAlias(alias) {
                    showCreateAlias = false
                    editingAlias = null
                }
            },
            onDismiss = {
                showCreateAlias = false
                editingAlias = null
            }
        )
    }

    deleteAliasCandidate?.let { alias ->
        ConfirmActionDialog(
            title = "删除账户指纹？",
            message = "删除模式「${alias.pattern}」后，自动记账将不再按此条件路由账户。",
            confirmText = "删除",
            isDestructive = true,
            onConfirm = {
                viewModel.deleteAlias(alias.id)
                deleteAliasCandidate = null
            },
            onDismiss = { deleteAliasCandidate = null }
        )
    }

    if (showCreateRule || editingRule != null) {
        RuleEditorDialog(
            existing = editingRule,
            onSave = { rule ->
                viewModel.saveRule(rule) {
                    showCreateRule = false
                    editingRule = null
                }
            },
            onDismiss = {
                showCreateRule = false
                editingRule = null
            }
        )
    }

    deleteRuleCandidate?.let { rule ->
        ConfirmActionDialog(
            title = "删除正则规则「${rule.name}」？",
            message = "删除后可能影响对应渠道的本地正则提取能力。",
            confirmText = "删除",
            isDestructive = true,
            onConfirm = {
                viewModel.deleteRule(rule.id)
                deleteRuleCandidate = null
            },
            onDismiss = { deleteRuleCandidate = null }
        )
    }
}

private enum class JsRuleFilterType(val label: String) {
    ALL("全部"),
    SMS("短信"),
    NOTICE("通知"),
    OCR("屏幕")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CloudRulesTab(
    settings: AppSettings,
    status: CloudRuleStatus,
    isSyncing: Boolean,
    onToggleJsRules: (Boolean) -> Unit,
    onToggleOcrFallback: (Boolean) -> Unit,
    onToggleCategoryFallback: (Boolean) -> Unit,
    onCheckUpdate: () -> Unit,
    onImportZip: (fileName: String, bytes: ByteArray) -> Unit,
    onResetBuiltin: () -> Unit,
    onValidateSubscriptionUrl: (String) -> String?,
    onSaveSubscriptionUrl: (String) -> Boolean,
    onShowMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val semantic = KairoTheme.semantic

    var showResetBuiltinConfirm by remember { mutableStateOf(false) }
    var manifestUrlInput by remember(settings.cloudRuleManifestUrl) {
        mutableStateOf(settings.cloudRuleManifestUrl)
    }
    var manifestUrlError by remember(settings.cloudRuleManifestUrl) {
        mutableStateOf<String?>(null)
    }
    var ruleSearchQuery by remember { mutableStateOf("") }
    var selectedRuleFilter by remember { mutableStateOf(JsRuleFilterType.ALL) }

    val zipOpenLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val fileName = resolveRuleZipDisplayName(context, uri)?.takeIf { it.isNotBlank() } ?: "rules.zip"
                val bytes = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    }.getOrNull()
                }
                when {
                    bytes == null -> onShowMessage("无法读取所选规则压缩包")
                    bytes.isEmpty() -> onShowMessage("所选规则压缩包内容为空")
                    else -> onImportZip(fileName, bytes)
                }
            }
        }
    }

    val filteredRules = remember(status.rules, ruleSearchQuery, selectedRuleFilter) {
        val queryLower = ruleSearchQuery.trim().lowercase(Locale.ROOT)
        status.rules.filter { rule ->
            val matchType = when (selectedRuleFilter) {
                JsRuleFilterType.ALL -> true
                JsRuleFilterType.SMS -> rule.ruleType.equals("app", ignoreCase = true) ||
                    rule.ruleType.equals("sms", ignoreCase = true)
                JsRuleFilterType.NOTICE -> rule.ruleType.equals("notice", ignoreCase = true)
                JsRuleFilterType.OCR -> rule.ruleType.equals("ocr", ignoreCase = true)
            }
            val matchQuery = queryLower.isEmpty() ||
                rule.ruleChineseName.lowercase(Locale.ROOT).contains(queryLower) ||
                rule.ruleName.lowercase(Locale.ROOT).contains(queryLower) ||
                rule.ruleApp.lowercase(Locale.ROOT).contains(queryLower)
            matchType && matchQuery
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. 状态概览卡
        item(key = "cloud_rule_overview") {
            val (sourceBg, sourceFg) = when (status.source) {
                JsRuleSource.CLOUD -> semantic.incomeContainer to semantic.income
                JsRuleSource.IMPORTED -> semantic.transferContainer to semantic.transfer
                JsRuleSource.BUILTIN -> MaterialTheme.colorScheme.secondaryContainer to
                    MaterialTheme.colorScheme.onSecondaryContainer
            }
            val lastCheckedMillis = maxOf(status.lastCheckedAtMillis, settings.lastRuleSyncMillis)

            YimuCard(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CloudSync,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Column {
                                Text(
                                    text = "规则包版本 ${status.version}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (settings.jsRulesEnabled) "JS 规则引擎已开启" else "JS 规则引擎已停用",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (settings.jsRulesEnabled) {
                                        semantic.income
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }
                        Surface(
                            shape = CircleShape,
                            color = sourceBg
                        ) {
                            Text(
                                text = status.source.labelZh,
                                style = MaterialTheme.typography.labelSmall,
                                color = sourceFg,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        RuleStatPill(label = "生效总数", count = status.totalCount, modifier = Modifier.weight(1f))
                        RuleStatPill(label = "短信", count = status.smsCount, modifier = Modifier.weight(1f))
                        RuleStatPill(label = "通知", count = status.noticeCount, modifier = Modifier.weight(1f))
                        RuleStatPill(label = "屏幕", count = status.ocrCount, modifier = Modifier.weight(1f))
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "上次检查：${if (lastCheckedMillis > 0L) KairoFormats.formatDateTime(lastCheckedMillis) else "尚未检查"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (status.updatedAt.isNotBlank()) {
                            Text(
                                text = "包构建时间：${formatRuleUpdatedAt(status.updatedAt)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (status.changelog.isNotBlank()) {
                            Text(
                                text = "更新说明：${status.changelog}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    status.lastError?.takeIf { it.isNotBlank() }?.let { errorMsg ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = semantic.expenseContainer.copy(alpha = 0.65f),
                            border = BorderStroke(1.dp, semantic.expense.copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "同步异常：$errorMsg",
                                style = MaterialTheme.typography.bodySmall,
                                color = semantic.expense,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }
        }

        // 2. 开关区
        item(key = "cloud_rule_switches") {
            YimuCard(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "执行策略开关",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    CompactSwitchRow(
                        title = "启用 JS 规则库",
                        detail = "优先匹配用户自定义正则，未命中时执行短信/通知/屏幕 JS 规则",
                        checked = settings.jsRulesEnabled,
                        onCheckedChange = onToggleJsRules
                    )
                    CompactHairlineDivider(startIndent = 0.dp)
                    CompactSwitchRow(
                        title = "屏幕 OCR 规则兜底",
                        detail = "无障碍页面语义未识别时，尝试使用屏幕 OCR JS 规则提取",
                        checked = settings.jsOcrFallbackEnabled,
                        enabled = settings.jsRulesEnabled,
                        onCheckedChange = onToggleOcrFallback
                    )
                    CompactHairlineDivider(startIndent = 0.dp)
                    CompactSwitchRow(
                        title = "智能分类词库补充",
                        detail = "本地关键词未命中或落在「其他」分类时使用 category.js 智能映射",
                        checked = settings.jsCategoryFallbackEnabled,
                        enabled = settings.jsRulesEnabled,
                        onCheckedChange = onToggleCategoryFallback
                    )
                }
            }
        }

        // 3. 操作区
        item(key = "cloud_rule_actions") {
            YimuCard(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "同步与导入",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Button(
                        onClick = onCheckUpdate,
                        enabled = !isSyncing,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("正在检查并同步...")
                        } else {
                            Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("检查更新")
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                runCatching {
                                    zipOpenLauncher.launch(arrayOf("*/*"))
                                }.onFailure {
                                    onShowMessage("无法打开系统文件选择器，请检查文件管理应用权限")
                                }
                            },
                            enabled = !isSyncing,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("导入本地 .zip", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        OutlinedButton(
                            onClick = { showResetBuiltinConfirm = true },
                            enabled = !isSyncing,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("恢复内置", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }

        // 4. 订阅地址设置卡
        item(key = "cloud_rule_subscription") {
            YimuCard(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "订阅地址设置",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "默认地址：${CloudRuleRepository.DEFAULT_MANIFEST_URL}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = manifestUrlInput,
                        onValueChange = {
                            manifestUrlInput = it
                            manifestUrlError = null
                        },
                        label = { Text("自定义 Manifest / ZIP 地址（留空使用默认）") },
                        placeholder = { Text(CloudRuleRepository.DEFAULT_MANIFEST_URL) },
                        isError = manifestUrlError != null,
                        supportingText = manifestUrlError?.let { err -> { Text(err) } },
                        singleLine = true,
                        trailingIcon = {
                            TextButton(
                                onClick = {
                                    val err = onValidateSubscriptionUrl(manifestUrlInput)
                                    if (err != null) {
                                        manifestUrlError = err
                                        onSaveSubscriptionUrl(manifestUrlInput)
                                    } else {
                                        manifestUrlError = null
                                        val trimmed = manifestUrlInput.trim()
                                        manifestUrlInput = trimmed
                                        onSaveSubscriptionUrl(trimmed)
                                    }
                                }
                            ) {
                                Text("保存")
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (manifestUrlInput.isNotBlank() || settings.cloudRuleManifestUrl.isNotBlank()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = {
                                    manifestUrlInput = ""
                                    manifestUrlError = null
                                    onSaveSubscriptionUrl("")
                                }
                            ) {
                                Text("清空并恢复默认地址")
                            }
                        }
                    }
                    Text(
                        text = if (settings.updateMirrorPrefix.isNotBlank()) {
                            "提示：规则同步会自动复用「外观与隐私 → 版本更新」中配置的 GitHub 镜像加速（当前：${settings.updateMirrorPrefix}）。"
                        } else {
                            "提示：若无法直连 GitHub，可在「外观与隐私 → 版本更新」中配置 GitHub 镜像加速前缀，规则下载将自动复用。"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 5. 规则清单浏览区（搜索 + 筛选 + 列表）
        item(key = "cloud_rule_browser_header") {
            YimuCard(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "规则清单",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "${filteredRules.size} / ${status.rules.size} 条",
                            style = MaterialTheme.typography.labelMedium.tabular(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedTextField(
                        value = ruleSearchQuery,
                        onValueChange = { ruleSearchQuery = it },
                        placeholder = { Text("搜索规则中文名、英文标识或应用包名") },
                        leadingIcon = {
                            Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                        },
                        trailingIcon = if (ruleSearchQuery.isNotEmpty()) {
                            {
                                IconButton(onClick = { ruleSearchQuery = "" }) {
                                    Icon(Icons.Rounded.Close, contentDescription = "清空搜索", modifier = Modifier.size(18.dp))
                                }
                            }
                        } else {
                            null
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(JsRuleFilterType.entries) { filterType ->
                            val count = when (filterType) {
                                JsRuleFilterType.ALL -> status.totalCount
                                JsRuleFilterType.SMS -> status.smsCount
                                JsRuleFilterType.NOTICE -> status.noticeCount
                                JsRuleFilterType.OCR -> status.ocrCount
                            }
                            FilterChip(
                                selected = selectedRuleFilter == filterType,
                                onClick = { selectedRuleFilter = filterType },
                                label = { Text("${filterType.label} ($count)") }
                            )
                        }
                    }
                }
            }
        }

        if (filteredRules.isEmpty()) {
            item(key = "cloud_rule_empty") {
                EmptyStateCard(
                    icon = Icons.AutoMirrored.Rounded.Rule,
                    title = if (status.rules.isEmpty()) "尚未加载 JS 规则" else "未找到匹配的规则",
                    subtitle = if (status.rules.isEmpty()) {
                        "可点击上方「检查更新」或「导入本地 .zip」加载规则包"
                    } else {
                        "请尝试调整搜索关键字或切换类型筛选"
                    }
                )
            }
        } else {
            itemsIndexed(
                items = filteredRules,
                key = { index, rule -> "${index}_${rule.ruleType}_${rule.ruleApp}_${rule.ruleName}_${rule.path}" }
            ) { _, rule ->
                JsRuleMetaRowCard(rule = rule)
            }
        }
    }

    if (showResetBuiltinConfirm) {
        ConfirmActionDialog(
            title = "恢复内置基线规则？",
            message = "将清除当前同步或本地导入的规则包，恢复为应用内置的基线规则库。",
            confirmText = "恢复内置",
            isDestructive = true,
            onConfirm = {
                showResetBuiltinConfirm = false
                onResetBuiltin()
            },
            onDismiss = { showResetBuiltinConfirm = false }
        )
    }
}

@Composable
private fun RuleStatPill(
    label: String,
    count: Int,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.titleSmall.tabular(),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun JsRuleMetaRowCard(rule: JsRuleMeta) {
    val semantic = KairoTheme.semantic
    val (typeLabel, badgeBg, badgeFg) = when {
        rule.ruleType.equals("app", ignoreCase = true) ||
            rule.ruleType.equals("sms", ignoreCase = true) -> Triple(
            "短信",
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer
        )
        rule.ruleType.equals("notice", ignoreCase = true) -> Triple(
            "通知",
            semantic.incomeContainer,
            semantic.income
        )
        rule.ruleType.equals("ocr", ignoreCase = true) -> Triple(
            "屏幕",
            semantic.transferContainer,
            semantic.transfer
        )
        else -> Triple(
            rule.ruleType.ifBlank { "未知" },
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer
        )
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.32f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = rule.ruleChineseName.ifBlank { rule.ruleName.ifBlank { "未命名规则" } },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = buildString {
                        append(rule.ruleApp.ifBlank { "*" })
                        if (rule.ruleName.isNotBlank()) {
                            append(" · ")
                            append(rule.ruleName)
                        }
                    },
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = badgeBg
            ) {
                Text(
                    text = typeLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = badgeFg,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
    }
}

private fun formatRuleUpdatedAt(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return ""
    val millis = runCatching { Instant.parse(trimmed).toEpochMilli() }.getOrNull()
    return if (millis != null) KairoFormats.formatDateTime(millis) else trimmed
}

private fun resolveRuleZipDisplayName(context: Context, uri: Uri): String? {
    return runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) cursor.getString(idx) else null
            } else {
                null
            }
        }
    }.getOrNull()
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SandboxTestTab(
    sandboxState: SandboxTestUiState,
    accountMap: Map<Long, AccountEntity>,
    onRunTest: (title: String, content: String, sourcePackage: String, allowLlm: Boolean, channel: String) -> Unit
) {
    val semantic = KairoTheme.semantic
    var selectedChannel by remember { mutableStateOf(CaptureChannel.NOTIFICATION) }
    var sourcePackage by remember { mutableStateOf("*") }
    var title by remember { mutableStateOf("银行消费通知") }
    var content by remember { mutableStateOf("您尾号6688信用卡于22日消费USD 15.00，当前可用额度人民币48200元。") }
    var allowLlm by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "捕获通道",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(
                CaptureChannel.NOTIFICATION to "通知",
                CaptureChannel.SMS to "短信",
                CaptureChannel.SCREEN to "屏幕"
            ).forEach { (ch, label) ->
                FilterChip(
                    selected = selectedChannel == ch,
                    onClick = { selectedChannel = ch },
                    label = { Text(label) }
                )
            }
        }

        Text(
            text = "快捷示例",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(
                selected = false,
                onClick = {
                    selectedChannel = CaptureChannel.SMS
                    sourcePackage = "*"
                    title = "外币消费短信"
                    content = "您尾号6688信用卡消费USD 19.99，商户Hetzner Online。"
                },
                label = { Text("外币卡") }
            )
            FilterChip(
                selected = false,
                onClick = {
                    selectedChannel = CaptureChannel.NOTIFICATION
                    sourcePackage = "com.tencent.mm"
                    title = "微信支付"
                    content = "向星巴克付款32.50元"
                },
                label = { Text("微信付款") }
            )
            FilterChip(
                selected = false,
                onClick = {
                    selectedChannel = CaptureChannel.NOTIFICATION
                    sourcePackage = "com.wise.android"
                    title = "Wise"
                    content = "You spent €24.80 at Amazon EU"
                },
                label = { Text("Wise 欧元") }
            )
            FilterChip(
                selected = false,
                onClick = {
                    selectedChannel = CaptureChannel.SMS
                    sourcePackage = "com.android.phone"
                    title = "95555"
                    content = "【招商银行】您账户6688于05月18日12:30在星巴克支付38.50元，余额1234.56元。"
                },
                label = { Text("招行短信") }
            )
            FilterChip(
                selected = false,
                onClick = {
                    selectedChannel = CaptureChannel.NOTIFICATION
                    sourcePackage = "com.unionpay"
                    title = "支付助手：入账通知"
                    content = "您尾号为6688的银行卡于18日12时30分入账1000.00元"
                },
                label = { Text("云闪付入账") }
            )
            FilterChip(
                selected = false,
                onClick = {
                    selectedChannel = CaptureChannel.NOTIFICATION
                    sourcePackage = "com.eg.android.AlipayGphone"
                    title = "支付宝小荷包自动攒执行成功"
                    content = "成功转入50.00元"
                },
                label = { Text("小荷包自动攒(转账)") }
            )
            FilterChip(
                selected = false,
                onClick = {
                    selectedChannel = CaptureChannel.SCREEN
                    sourcePackage = "com.eg.android.AlipayGphone"
                    title = "支付宝"
                    content = "支付成功\n￥ 28.50\n美团外卖\n付款方式\n招商银行储蓄卡(6688)"
                },
                label = { Text("支付宝支付成功(屏幕)") }
            )
        }

        OutlinedTextField(
            value = sourcePackage,
            onValueChange = { sourcePackage = it },
            label = { Text("来源包名") },
            placeholder = { Text("* 表示通用，短信通常为 com.android.phone") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = {
                Text(
                    when (selectedChannel) {
                        CaptureChannel.SMS -> "发件人/标题"
                        CaptureChannel.SCREEN -> "页面/应用标题"
                        else -> "通知标题"
                    }
                )
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = content,
            onValueChange = { content = it },
            label = {
                Text(
                    if (selectedChannel == CaptureChannel.SCREEN) {
                        "屏幕识别文本（支持换行）"
                    } else {
                        "通知/短信正文"
                    }
                )
            },
            minLines = 3,
            maxLines = 6,
            modifier = Modifier.fillMaxWidth()
        )

        CompactSwitchRow(
            title = "允许 AI 兜底",
            checked = allowLlm,
            onCheckedChange = { allowLlm = it }
        )

        Button(
            onClick = { onRunTest(title, content, sourcePackage, allowLlm, selectedChannel) },
            enabled = !sandboxState.isRunning && content.isNotBlank(),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (sandboxState.isRunning) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("解析中...")
            } else {
                Icon(Icons.Rounded.PlayArrow, null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("运行解析测试")
            }
        }

        if (sandboxState.hasResult) {
            val draft = sandboxState.draft
            if (draft != null) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = semantic.incomeContainer.copy(alpha = 0.55f),
                    border = BorderStroke(1.dp, semantic.income.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "解析成功 · 置信度 ${(draft.confidence * 100).toInt()}%",
                            style = MaterialTheme.typography.titleSmall,
                            color = semantic.income,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "命中引擎：${draft.parserUsed}",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "类型：${KairoFormats.transactionTypeLabel(draft.type)} · 金额：${MoneyAmount.format(draft.origAmountMicros, draft.origCurrency, showCurrencyCode = true)}",
                            style = MaterialTheme.typography.bodyMedium.tabular()
                        )
                        val acctName = draft.accountId?.let { accountMap[it]?.name }
                        val toAcctName = draft.toAccountId?.let { accountMap[it]?.name }
                        val isTransferLike = draft.type == TransactionType.TRANSFER ||
                            draft.toAccountId != null ||
                            draft.toAccountHint.isNotBlank()
                        if (isTransferLike) {
                            Text(
                                text = "转出账户：${acctName ?: "未匹配"}${if (draft.accountHint.isNotBlank()) " (线索: ${draft.accountHint})" else ""}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "转入账户：${toAcctName ?: "未匹配"}${if (draft.toAccountHint.isNotBlank()) " (线索: ${draft.toAccountHint})" else ""}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        } else {
                            Text(
                                text = "路由账户：${acctName ?: "未匹配"}${if (draft.accountHint.isNotBlank()) " (线索: ${draft.accountHint})" else ""}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Text(
                            text = "分类：${draft.categoryName.ifBlank { "未匹配" }} · 商户：${draft.merchant.ifBlank { "-" }}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (draft.note.isNotBlank()) {
                            Text(
                                text = "备注：${draft.note}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = semantic.expenseContainer.copy(alpha = 0.55f),
                    border = BorderStroke(1.dp, semantic.expense.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = sandboxState.errorMessage ?: "未识别为动账消息",
                        style = MaterialTheme.typography.bodyMedium,
                        color = semantic.expense,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AliasesListTab(
    aliases: List<AccountAliasEntity>,
    accountMap: Map<Long, AccountEntity>,
    onToggleEnabled: (AccountAliasEntity, Boolean) -> Unit,
    onCreateAlias: () -> Unit,
    onEdit: (AccountAliasEntity) -> Unit,
    onDelete: (AccountAliasEntity) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 88.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (aliases.isEmpty()) {
            item(key = "empty_aliases") {
                EmptyStateCard(
                    icon = Icons.AutoMirrored.Rounded.Rule,
                    title = "暂无账户指纹",
                    actionLabel = "新增指纹",
                    onAction = onCreateAlias
                )
            }
        } else {
            items(aliases, key = { it.id }) { alias ->
                val accName = accountMap[alias.accountId]?.name ?: "账户 #${alias.accountId}"
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Text(
                                        text = alias.matchType,
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Text(
                                    text = accName,
                                    style = MaterialTheme.typography.titleSmall
                                )
                            }
                            Text(
                                text = "模式：${alias.pattern}",
                                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
                            )
                            Text(
                                text = "优先级 ${alias.priority}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = alias.enabled,
                            onCheckedChange = { onToggleEnabled(alias, it) }
                        )
                        IconButton(onClick = { onEdit(alias) }) {
                            Icon(Icons.Rounded.Edit, "编辑", modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = { onDelete(alias) }) {
                            Icon(Icons.Rounded.Delete, "删除", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RulesListTab(
    rules: List<ParseRuleEntity>,
    onToggleEnabled: (ParseRuleEntity, Boolean) -> Unit,
    onCreateRule: () -> Unit,
    onEdit: (ParseRuleEntity) -> Unit,
    onDelete: (ParseRuleEntity) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 88.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (rules.isEmpty()) {
            item(key = "empty_rules") {
                EmptyStateCard(
                    icon = Icons.AutoMirrored.Rounded.Rule,
                    title = "暂无规则",
                    actionLabel = "新增规则",
                    onAction = onCreateRule
                )
            }
        } else {
            items(rules, key = { it.id }) { rule ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(rule.name, style = MaterialTheme.typography.titleSmall)
                                    if (rule.isBuiltIn) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.secondaryContainer
                                        ) {
                                            Text(
                                                text = "内置",
                                                style = MaterialTheme.typography.labelSmall,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = "${KairoFormats.transactionTypeLabel(rule.transactionType)} · ${rule.sourcePackage} · 优先级 ${rule.priority}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = rule.enabled,
                                onCheckedChange = { onToggleEnabled(rule, it) }
                            )
                        }
                        Text(
                            text = rule.regexPattern,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { onEdit(rule) }) {
                                Icon(Icons.Rounded.Edit, null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("编辑")
                            }
                            TextButton(onClick = { onDelete(rule) }) {
                                Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("删除", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AliasEditorDialog(
    existing: AccountAliasEntity?,
    accounts: List<AccountEntity>,
    onSave: (AccountAliasEntity) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedAccountId by remember { mutableLongStateOf(existing?.accountId ?: accounts.firstOrNull()?.id ?: 0L) }
    var matchType by remember { mutableStateOf(existing?.matchType ?: AliasMatchType.CARD_LAST4) }
    var pattern by remember { mutableStateOf(existing?.pattern ?: "") }
    var priorityInput by remember { mutableStateOf((existing?.priority ?: 70).toString()) }
    var enabled by remember { mutableStateOf(existing?.enabled ?: true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    YubraeDialog(
        onDismissRequest = onDismiss,
        title = if (existing == null) "新增账户指纹" else "编辑账户指纹",
        icon = Icons.Rounded.Fingerprint,
        confirmButton = {
            Button(
                onClick = {
                    when {
                        selectedAccountId == 0L -> errorMsg = "请选择目标账户"
                        pattern.trim().isEmpty() -> errorMsg = "请输入匹配模式"
                        else -> onSave(
                            AccountAliasEntity(
                                id = existing?.id ?: 0L,
                                accountId = selectedAccountId,
                                matchType = matchType,
                                pattern = pattern.trim(),
                                priority = priorityInput.toIntOrNull() ?: 50,
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
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("目标账户：", style = MaterialTheme.typography.labelMedium)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(accounts, key = { it.id }) { acc ->
                    FilterChip(
                        selected = selectedAccountId == acc.id,
                        onClick = { selectedAccountId = acc.id },
                        label = { Text("${acc.name}(${acc.currencyCode})") }
                    )
                }
            }
            Text("匹配方式：", style = MaterialTheme.typography.labelMedium)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(
                    listOf(
                        AliasMatchType.CARD_LAST4 to "卡尾号",
                        AliasMatchType.PACKAGE to "包名",
                        AliasMatchType.KEYWORD to "关键词",
                        AliasMatchType.REGEX to "正则"
                    )
                ) { (type, label) ->
                    FilterChip(
                        selected = matchType == type,
                        onClick = { matchType = type },
                        label = { Text(label) }
                    )
                }
            }
            OutlinedTextField(
                value = pattern,
                onValueChange = { pattern = it },
                label = { Text("匹配模式") },
                placeholder = { Text("如 6688 或 com.tencent.mm") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = priorityInput,
                onValueChange = { priorityInput = it },
                label = { Text("优先级") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )
            CompactSwitchRow(
                title = "启用",
                checked = enabled,
                onCheckedChange = { enabled = it }
            )
            errorMsg?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun RuleEditorDialog(
    existing: ParseRuleEntity?,
    onSave: (ParseRuleEntity) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var sourcePackage by remember { mutableStateOf(existing?.sourcePackage ?: "*") }
    var channelType by remember { mutableStateOf(existing?.channelType ?: "ANY") }
    var txType by remember { mutableStateOf(existing?.transactionType ?: TransactionType.EXPENSE) }
    var regexPattern by remember { mutableStateOf(existing?.regexPattern ?: "") }
    var amountGroup by remember { mutableStateOf((existing?.amountGroup ?: 1).toString()) }
    var currencyGroup by remember { mutableStateOf((existing?.currencyGroup ?: 0).toString()) }
    var defaultCurrency by remember { mutableStateOf(existing?.defaultCurrency ?: "CNY") }
    var accountHintGroup by remember { mutableStateOf((existing?.accountHintGroup ?: 0).toString()) }
    var merchantGroup by remember { mutableStateOf((existing?.merchantGroup ?: 0).toString()) }
    var defaultCategoryName by remember { mutableStateOf(existing?.defaultCategoryName ?: "其他支出") }
    var priority by remember { mutableStateOf((existing?.priority ?: 60).toString()) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    YubraeDialog(
        onDismissRequest = onDismiss,
        title = if (existing == null) "新增正则规则" else "编辑正则规则",
        icon = Icons.AutoMirrored.Rounded.Rule,
        confirmButton = {
            Button(
                onClick = {
                    val compiledOk = runCatching { Regex(regexPattern) }.isSuccess
                    when {
                        name.trim().isEmpty() -> errorMsg = "请输入规则名称"
                        regexPattern.isBlank() || !compiledOk -> errorMsg = "请输入合法的正则表达式"
                        else -> onSave(
                            ParseRuleEntity(
                                id = existing?.id ?: 0L,
                                name = name.trim(),
                                sourcePackage = sourcePackage.trim().ifEmpty { "*" },
                                channelType = channelType,
                                regexPattern = regexPattern,
                                transactionType = txType,
                                amountGroup = amountGroup.toIntOrNull() ?: 1,
                                currencyGroup = currencyGroup.toIntOrNull() ?: 0,
                                defaultCurrency = defaultCurrency.trim().ifEmpty { "CNY" },
                                accountHintGroup = accountHintGroup.toIntOrNull() ?: 0,
                                merchantGroup = merchantGroup.toIntOrNull() ?: 0,
                                defaultCategoryName = defaultCategoryName.trim().ifEmpty { "其他支出" },
                                priority = priority.toIntOrNull() ?: 60,
                                enabled = existing?.enabled ?: true,
                                isBuiltIn = existing?.isBuiltIn ?: false
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
                label = { Text("规则名称") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = sourcePackage,
                onValueChange = { sourcePackage = it },
                label = { Text("包名 (* 为任意)") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(TransactionType.EXPENSE to "支出", TransactionType.INCOME to "收入").forEach { (type, label) ->
                    FilterChip(selected = txType == type, onClick = { txType = type }, label = { Text(label) })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("ANY", "NOTIFICATION", "SMS").forEach { ch ->
                    FilterChip(selected = channelType == ch, onClick = { channelType = ch }, label = { Text(ch) })
                }
            }
            OutlinedTextField(
                value = regexPattern,
                onValueChange = { regexPattern = it },
                label = { Text("正则表达式") },
                minLines = 2,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = amountGroup,
                    onValueChange = { amountGroup = it },
                    label = { Text("金额组号") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = currencyGroup,
                    onValueChange = { currencyGroup = it },
                    label = { Text("币种组(0无)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = accountHintGroup,
                    onValueChange = { accountHintGroup = it },
                    label = { Text("卡尾号组") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = merchantGroup,
                    onValueChange = { merchantGroup = it },
                    label = { Text("商户组") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = defaultCurrency,
                    onValueChange = { defaultCurrency = it.uppercase() },
                    label = { Text("默认币种") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = priority,
                    onValueChange = { priority = it },
                    label = { Text("优先级") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f)
                )
            }
            OutlinedTextField(
                value = defaultCategoryName,
                onValueChange = { defaultCategoryName = it },
                label = { Text("默认分类名称") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )
            errorMsg?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
    }
}
