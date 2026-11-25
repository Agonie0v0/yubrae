package com.yubrae.app.ui.screens.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.automirrored.rounded.Rule
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CurrencyExchange
import androidx.compose.material.icons.rounded.EventRepeat
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LocalOffer
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yubrae.app.BuildConfig
import com.yubrae.app.core.contract.DevicePermissionReport
import com.yubrae.app.data.prefs.ThemeMode
import com.yubrae.app.ui.components.CompactHairlineDivider
import com.yubrae.app.ui.components.CompactSwitchRow
import com.yubrae.app.ui.components.CurrencyBadge
import com.yubrae.app.ui.components.CurrencyPickerDialog
import com.yubrae.app.ui.components.YimuCard
import com.yubrae.app.ui.components.YubraeBrandMark
import com.yubrae.app.ui.security.BiometricLockHelper
import com.yubrae.app.ui.theme.KairoTheme
import com.yubrae.app.ui.theme.KairoThemePreset
import com.yubrae.app.ui.theme.KairoThemePresets
import com.yubrae.app.ui.theme.tabular
import com.yubrae.app.ui.viewmodel.KairoMainViewModel
import java.time.Instant
import java.time.ZoneId

object SettingsRoutes {
    const val HUB = "settings_hub"
    const val AUTO_CAPTURE = "settings_auto_capture"
    const val APPEARANCE = "settings_appearance"
    const val LOGS = "settings_logs"
    const val RULES = "settings_rules"
    const val LLM = "settings_llm"
    const val RATES = "settings_rates"
    const val RECURRING = "settings_recurring"
    const val IMPORT_BACKUP = "settings_import_backup"
    const val CATEGORIES = "settings_categories"
    const val TAGS = "settings_tags"
    const val REIMBURSE = "settings_reimburse"
    const val DEBTS = "settings_debts"
}

/**
 * 兼容旧入口调用的适配器，直接委托至重构后的 [MineHubScreen]。
 */
@Composable
fun AutomationHubScreen(
    viewModel: KairoMainViewModel,
    onNavigate: (String) -> Unit,
    onOpenLedgerManager: () -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    MineHubScreen(
        viewModel = viewModel,
        onNavigate = onNavigate,
        onOpenLedgerManager = onOpenLedgerManager,
        onTogglePrivacy = {
            viewModel.updateSettings { it.copy(privacyMaskEnabled = !it.privacyMaskEnabled) }
        },
        contentPadding = contentPadding
    )
}

/**
 * 第 4 Tab「我的」中心：品牌头卡 + 真实数据统计条 + 3 栏快捷宫格 + 平衡双组设置卡片。
 */
@Composable
fun MineHubScreen(
    viewModel: KairoMainViewModel,
    onNavigate: (String) -> Unit,
    onOpenLedgerManager: () -> Unit = {},
    onTogglePrivacy: () -> Unit = {
        viewModel.updateSettings { it.copy(privacyMaskEnabled = !it.privacyMaskEnabled) }
    },
    listState: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val report by viewModel.permissionReport.collectAsStateWithLifecycle()
    val logs by viewModel.captureLogs.collectAsStateWithLifecycle()
    val rules by viewModel.parseRules.collectAsStateWithLifecycle()
    val recurringRules by viewModel.recurringRules.collectAsStateWithLifecycle()
    val transactions by viewModel.allTransactions.collectAsStateWithLifecycle()
    val ledgers by viewModel.ledgers.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val tags by viewModel.tags.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.ensurePermissionsLoaded()
    }

    val zone = remember { ZoneId.systemDefault() }
    val recordingDays = remember(transactions, zone) {
        transactions.asSequence()
            .map { Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate() }
            .distinct()
            .count()
    }
    val totalTransactionCount = transactions.size
    val activeLedgerCount = remember(ledgers) {
        ledgers.count { !it.archived }
    }

    val autoCaptureEnabled = settings.notificationCaptureEnabled ||
        settings.smsCaptureEnabled ||
        settings.accessibilityCaptureEnabled ||
        settings.autoAccountingBridgeEnabled

    val readyCount = remember(report) {
        report?.let { r ->
            listOf(
                r.notificationListenerEnabled,
                r.accessibilityServiceEnabled,
                r.smsPermissionGranted,
                r.overlayPermissionGranted,
                r.ignoringBatteryOptimizations
            ).count { it }
        }
    }

    val styleLabel = if (settings.dynamicColor) {
        "动态取色"
    } else {
        KairoThemePresets.find(settings.themeStyle).nameZh
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            bottom = contentPadding.calculateBottomPadding() + 28.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 品牌抬头 + 真实记账统计条
        item(key = "mine_brand_hero") {
            MineBrandHeaderCard(
                privacyMasked = settings.privacyMaskEnabled,
                autoCaptureEnabled = autoCaptureEnabled,
                readyCount = readyCount,
                recordingDays = recordingDays,
                totalTransactions = totalTransactionCount,
                ledgerCount = activeLedgerCount,
                topInsetPadding = contentPadding.calculateTopPadding(),
                onTogglePrivacy = onTogglePrivacy,
                onOpenAutoCapture = { onNavigate(SettingsRoutes.AUTO_CAPTURE) }
            )
        }

        // 3 栏快捷工具宫格：账本 · 分类 · 标签
        item(key = "mine_finance_card") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                QuickToolCard(
                    icon = Icons.AutoMirrored.Rounded.MenuBook,
                    label = "账本",
                    countText = "$activeLedgerCount 个",
                    accentColor = Color(0xFF2AA876),
                    onClick = onOpenLedgerManager,
                    modifier = Modifier.weight(1f)
                )
                QuickToolCard(
                    icon = Icons.Rounded.Category,
                    label = "分类",
                    countText = "${categories.size} 项",
                    accentColor = Color(0xFF0284C7),
                    onClick = { onNavigate(SettingsRoutes.CATEGORIES) },
                    modifier = Modifier.weight(1f)
                )
                QuickToolCard(
                    icon = Icons.Rounded.LocalOffer,
                    label = "标签",
                    countText = "${tags.size} 个",
                    accentColor = Color(0xFF0D9488),
                    onClick = { onNavigate(SettingsRoutes.TAGS) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // 第一组（4 项）：自动记账与引擎
        item(key = "mine_automation_card") {
            Box(modifier = Modifier.padding(horizontal = 12.dp)) {
                YimuCard(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest) {
                    Column(modifier = Modifier.padding(vertical = 6.dp)) {
                        Text(
                            text = "自动记账与引擎",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 4.dp)
                        )
                        HubNavRow(
                            icon = Icons.Rounded.Security,
                            title = "自动记账与权限",
                            subtitle = "通知监听、无障碍、短信、悬浮窗与外部协议",
                            statusText = readyCount?.let { "$it/5" },
                            onClick = { onNavigate(SettingsRoutes.AUTO_CAPTURE) }
                        )
                        CompactHairlineDivider(startIndent = 58.dp)
                        HubNavRow(
                            icon = Icons.AutoMirrored.Rounded.ReceiptLong,
                            title = "捕获日志",
                            subtitle = "自动捕获报文回溯与指纹沉淀",
                            statusText = "${logs.size} 条",
                            onClick = { onNavigate(SettingsRoutes.LOGS) }
                        )
                        CompactHairlineDivider(startIndent = 58.dp)
                        HubNavRow(
                            icon = Icons.AutoMirrored.Rounded.Rule,
                            title = "指纹与规则",
                            subtitle = "文本沙盒、账户指纹与 NFC/正则规则",
                            statusText = "${rules.count { it.enabled }} 条",
                            onClick = { onNavigate(SettingsRoutes.RULES) }
                        )
                        CompactHairlineDivider(startIndent = 58.dp)
                        HubNavRow(
                            icon = Icons.Rounded.SmartToy,
                            title = "AI 大模型",
                            subtitle = "智能兜底解析与小票图像识别",
                            statusText = if (settings.llmEnabled) settings.llmModel.ifBlank { "已启用" } else "未启用",
                            onClick = { onNavigate(SettingsRoutes.LLM) }
                        )
                    }
                }
            }
        }

        // 第二组（4 项）：数据与偏好
        item(key = "mine_preferences_card") {
            Box(modifier = Modifier.padding(horizontal = 12.dp)) {
                YimuCard(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest) {
                    Column(modifier = Modifier.padding(vertical = 6.dp)) {
                        Text(
                            text = "数据与偏好",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 4.dp)
                        )
                        HubNavRow(
                            icon = Icons.Rounded.EventRepeat,
                            title = "周期账单",
                            subtitle = "定期自动入账与订阅扣费管理",
                            statusText = "${recurringRules.count { it.enabled }} 条",
                            onClick = { onNavigate(SettingsRoutes.RECURRING) }
                        )
                        CompactHairlineDivider(startIndent = 58.dp)
                        HubNavRow(
                            icon = Icons.Rounded.CurrencyExchange,
                            title = "汇率中心",
                            subtitle = "实时多币种换算与参考汇率",
                            statusText = settings.baseCurrency,
                            onClick = { onNavigate(SettingsRoutes.RATES) }
                        )
                        CompactHairlineDivider(startIndent = 58.dp)
                        HubNavRow(
                            icon = Icons.Rounded.Backup,
                            title = "导入与备份",
                            subtitle = "多格式账单导入、WebDAV 云同步与本地 .klbk",
                            statusText = if (settings.autoBackupEnabled) "自动备份" else "本地/云端",
                            onClick = { onNavigate(SettingsRoutes.IMPORT_BACKUP) }
                        )
                        CompactHairlineDivider(startIndent = 58.dp)
                        HubNavRow(
                            icon = Icons.Rounded.Palette,
                            title = "外观与隐私",
                            subtitle = "主题配色、隐私隐藏、生物锁与应用信息",
                            statusText = styleLabel,
                            onClick = { onNavigate(SettingsRoutes.APPEARANCE) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MineBrandHeaderCard(
    privacyMasked: Boolean,
    autoCaptureEnabled: Boolean,
    readyCount: Int?,
    recordingDays: Int,
    totalTransactions: Int,
    ledgerCount: Int,
    topInsetPadding: androidx.compose.ui.unit.Dp,
    onTogglePrivacy: () -> Unit,
    onOpenAutoCapture: () -> Unit
) {
    val semantic = KairoTheme.semantic
    val topModifier = if (topInsetPadding > 0.dp) {
        Modifier.padding(top = topInsetPadding + 6.dp)
    } else {
        Modifier
            .statusBarsPadding()
            .padding(top = 6.dp)
    }

    val allReady = autoCaptureEnabled && readyCount == 5
    val pillBg = when {
        !autoCaptureEnabled -> semantic.heroPillBg
        allReady -> semantic.incomeContainer.copy(alpha = 0.86f)
        readyCount != null -> semantic.warningContainer.copy(alpha = 0.86f)
        else -> semantic.heroPillBg
    }
    val pillFg = when {
        !autoCaptureEnabled -> semantic.heroOnSurface.copy(alpha = 0.78f)
        allReady -> semantic.income
        readyCount != null -> semantic.warning
        else -> MaterialTheme.colorScheme.primary
    }
    val pillLabel = when {
        !autoCaptureEnabled -> "自动记账 已关"
        allReady -> "自动记账 5/5"
        readyCount != null -> "自动记账 $readyCount/5"
        else -> "自动记账"
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        // 全宽柔和渐变顶栏：右上角轻量状态与隐私开关，中央水平居中展示品牌徽标与「余白」
        Surface(
            shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp),
            color = Color.Transparent,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.linearGradient(colors = semantic.heroGradient))
                    .then(topModifier)
                    .padding(start = 14.dp, end = 14.dp, bottom = 18.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = pillBg,
                            onClick = onOpenAutoCapture
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = if (allReady) Icons.Rounded.CheckCircle else Icons.Rounded.AutoAwesome,
                                    contentDescription = null,
                                    tint = pillFg,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = pillLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = pillFg,
                                    maxLines = 1
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = CircleShape,
                            color = semantic.heroPillBg,
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .clickable(onClick = onTogglePrivacy)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (privacyMasked) {
                                        Icons.Rounded.VisibilityOff
                                    } else {
                                        Icons.Rounded.Visibility
                                    },
                                    contentDescription = if (privacyMasked) "关闭隐私遮罩" else "开启隐私遮罩",
                                    tint = semantic.heroOnSurface.copy(alpha = 0.78f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    YubraeBrandMark(size = 56.dp)

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "余白",
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
                        fontWeight = FontWeight.Bold,
                        color = semantic.heroOnSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Box(modifier = Modifier.padding(horizontal = 12.dp)) {
            YimuCard(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BrandStatMetricCell(
                        value = recordingDays.toString(),
                        label = "记账天数",
                        modifier = Modifier.weight(1f)
                    )
                    VerticalDivider(
                        modifier = Modifier.height(24.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                    )
                    BrandStatMetricCell(
                        value = totalTransactions.toString(),
                        label = "累计笔数",
                        modifier = Modifier.weight(1f)
                    )
                    VerticalDivider(
                        modifier = Modifier.height(24.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                    )
                    BrandStatMetricCell(
                        value = ledgerCount.toString(),
                        label = "账本数",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun BrandStatMetricCell(
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.tabular(),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

@Composable
private fun QuickToolCard(
    icon: ImageVector,
    label: String,
    countText: String,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    YimuCard(
        cornerRadius = 14.dp,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        onClick = onClick,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = accentColor.copy(alpha = 0.13f),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = accentColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = countText,
                style = MaterialTheme.typography.labelSmall.tabular(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * 独立二级页：自动记账与权限中心。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AutoCaptureSettingsScreen(
    viewModel: KairoMainViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val report by viewModel.permissionReport.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()

    var showHyperOsGuide by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val smsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.refreshPermissions()
    }

    val postNotifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        viewModel.refreshPermissions()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("自动记账与权限") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + 6.dp,
                bottom = innerPadding.calculateBottomPadding() + 28.dp,
                start = 12.dp,
                end = 12.dp
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 权限体检与 Root 一键授权
            item(key = "permission_health") {
                PermissionHealthCard(
                    report = report,
                    isBusy = isBusy,
                    onOpenHyperOsGuide = { showHyperOsGuide = true },
                    onRefresh = { viewModel.refreshPermissions() },
                    onRootGrant = { viewModel.tryRootGrantPermissions() },
                    onOpenNotificationListener = {
                        safeStartActivity(context, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    },
                    onOpenAccessibility = {
                        safeStartActivity(context, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    },
                    onRequestSms = {
                        runCatching {
                            smsLauncher.launch(
                                arrayOf(
                                    Manifest.permission.RECEIVE_SMS,
                                    Manifest.permission.READ_SMS
                                )
                            )
                        }.onFailure {
                            viewModel.showMessage("无法发起短信权限请求，请在系统应用权限设置中手动开启")
                        }
                    },
                    onOpenOverlay = {
                        safeStartActivity(
                            context,
                            Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            )
                        )
                    },
                    onRequestPostNotification = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            runCatching {
                                postNotifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }.onFailure {
                                viewModel.showMessage("无法发起通知权限请求，请在系统设置中手动开启通知权限")
                            }
                        } else {
                            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            }
                            safeStartActivity(context, intent)
                        }
                    },
                    onOpenBatteryWhitelist = {
                        val reqIntent = Intent(
                            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:${context.packageName}")
                        )
                        if (!safeStartActivity(context, reqIntent)) {
                            safeStartActivity(context, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                        }
                    }
                )
            }

            // 自动记账核心开关
            item(key = "automation_switches") {
                YimuCard(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "自动记账开关",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )

                        CompactSwitchRow(
                            title = "通知监听自动记账",
                            checked = settings.notificationCaptureEnabled,
                            onCheckedChange = { checked ->
                                viewModel.updateSettings { it.copy(notificationCaptureEnabled = checked) }
                            }
                        )
                        CompactHairlineDivider(startIndent = 0.dp)

                        CompactSwitchRow(
                            title = "银行短信解析",
                            checked = settings.smsCaptureEnabled,
                            onCheckedChange = { checked ->
                                viewModel.updateSettings { it.copy(smsCaptureEnabled = checked) }
                            }
                        )
                        CompactHairlineDivider(startIndent = 0.dp)

                        CompactSwitchRow(
                            title = "无障碍屏幕识别",
                            checked = settings.accessibilityCaptureEnabled,
                            onCheckedChange = { checked ->
                                viewModel.updateSettings { it.copy(accessibilityCaptureEnabled = checked) }
                            }
                        )

                        AnimatedVisibility(visible = settings.accessibilityCaptureEnabled) {
                            Column(
                                modifier = Modifier.padding(start = 14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CompactSwitchRow(
                                    title = "支付成功页识别",
                                    checked = settings.accessibilityPaySuccessEnabled,
                                    onCheckedChange = { checked ->
                                        viewModel.updateSettings { it.copy(accessibilityPaySuccessEnabled = checked) }
                                    }
                                )
                                CompactHairlineDivider(startIndent = 0.dp)
                                CompactSwitchRow(
                                    title = "历史账单页补记",
                                    checked = settings.accessibilityBillDetailEnabled,
                                    onCheckedChange = { checked ->
                                        viewModel.updateSettings { it.copy(accessibilityBillDetailEnabled = checked) }
                                    }
                                )
                                CompactHairlineDivider(startIndent = 0.dp)
                                CompactSwitchRow(
                                    title = "电商与外卖订单识别",
                                    checked = settings.accessibilityOrderDetailEnabled,
                                    onCheckedChange = { checked ->
                                        viewModel.updateSettings { it.copy(accessibilityOrderDetailEnabled = checked) }
                                    }
                                )
                            }
                        }
                        CompactHairlineDivider(startIndent = 0.dp)

                        CompactSwitchRow(
                            title = "AutoAccounting 协议兼容",
                            checked = settings.autoAccountingBridgeEnabled,
                            onCheckedChange = { checked ->
                                viewModel.updateSettings { it.copy(autoAccountingBridgeEnabled = checked) }
                            }
                        )
                        CompactHairlineDivider(startIndent = 0.dp)

                        CompactSwitchRow(
                            title = "自动记账悬浮窗",
                            checked = settings.floatingConfirmEnabled,
                            onCheckedChange = { checked ->
                                viewModel.updateSettings { it.copy(floatingConfirmEnabled = checked) }
                            }
                        )

                        AnimatedVisibility(visible = settings.floatingConfirmEnabled) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                CompactSwitchRow(
                                    title = "悬浮窗快捷编辑",
                                    checked = settings.floatingRichEditorEnabled,
                                    onCheckedChange = { checked ->
                                        viewModel.updateSettings { it.copy(floatingRichEditorEnabled = checked) }
                                    }
                                )
                                Text(
                                    text = "自动确认倒计时",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf(3 to "3秒", 5 to "5秒", 8 to "8秒", 15 to "15秒", 0 to "不自动").forEach { (sec, label) ->
                                        FilterChip(
                                            selected = settings.floatingAutoConfirmSeconds == sec,
                                            onClick = {
                                                viewModel.updateSettings { it.copy(floatingAutoConfirmSeconds = sec) }
                                            },
                                            label = { Text(label) }
                                        )
                                    }
                                }
                            }
                        }

                        CompactHairlineDivider(startIndent = 0.dp)
                        CompactSwitchRow(
                            title = "低置信度待核对",
                            checked = settings.defaultPendingWhenUncertain,
                            onCheckedChange = { checked ->
                                viewModel.updateSettings { it.copy(defaultPendingWhenUncertain = checked) }
                            }
                        )
                    }
                }
            }
        }
    }

    if (showHyperOsGuide) {
        AlertDialog(
            onDismissRequest = { showHyperOsGuide = false },
            title = { Text("小米 HyperOS 防冻结指引") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("1. 应用信息 → 省电策略 → 无限制", style = MaterialTheme.typography.bodySmall)
                    Text("2. 应用信息 → 权限管理 → 开启「自启动」与「显示悬浮窗」", style = MaterialTheme.typography.bodySmall)
                    Text("3. 系统设置 → 通知使用权 → 开启「余白」", style = MaterialTheme.typography.bodySmall)
                    Text("4. 系统设置 → 无障碍 → 已下载的应用 → 开启「余白」", style = MaterialTheme.typography.bodySmall)
                    Text("5. 多任务界面长按或下拉「余白」卡片锁定后台", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = { showHyperOsGuide = false }) { Text("知道了") }
            }
        )
    }
}

/**
 * 独立二级页：外观主题与安全隐私。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AppearanceSecurityScreen(
    viewModel: KairoMainViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var showBaseCurrencyPicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("外观与隐私") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + 6.dp,
                bottom = innerPadding.calculateBottomPadding() + 28.dp,
                start = 12.dp,
                end = 12.dp
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 卡片 1：界面主题与主币种
            item(key = "theme_and_currency_card") {
                YimuCard(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Rounded.Palette, null, tint = MaterialTheme.colorScheme.primary)
                            Text(
                                text = "外观与币种",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "主题画廊",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (settings.dynamicColor) {
                                Text(
                                    text = "动态取色中",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            KairoThemePresets.all.chunked(2).forEach { rowPresets ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    rowPresets.forEach { preset ->
                                        val isSelected = !settings.dynamicColor &&
                                            preset.id.equals(settings.themeStyle, ignoreCase = true)
                                        ThemePresetGalleryCard(
                                            preset = preset,
                                            selected = isSelected,
                                            onClick = {
                                                viewModel.updateSettings {
                                                    it.copy(themeStyle = preset.id, dynamicColor = false)
                                                }
                                            },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    if (rowPresets.size == 1) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }

                        Text(
                            text = "深浅模式",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                ThemeMode.SYSTEM to "跟随系统",
                                ThemeMode.LIGHT to "浅色",
                                ThemeMode.DARK to "深色"
                            ).forEach { (mode, label) ->
                                FilterChip(
                                    selected = settings.themeMode == mode,
                                    onClick = { viewModel.updateSettings { it.copy(themeMode = mode) } },
                                    label = { Text(label) }
                                )
                            }
                        }

                        CompactHairlineDivider(startIndent = 0.dp)

                        CompactSwitchRow(
                            title = "OLED 纯黑背景",
                            checked = settings.oledPureBlack,
                            onCheckedChange = { enabled ->
                                viewModel.updateSettings { it.copy(oledPureBlack = enabled) }
                            }
                        )

                        CompactHairlineDivider(startIndent = 0.dp)

                        CompactSwitchRow(
                            title = "壁纸动态取色",
                            checked = settings.dynamicColor,
                            onCheckedChange = { enabled ->
                                viewModel.updateSettings { it.copy(dynamicColor = enabled) }
                            }
                        )

                        CompactHairlineDivider(startIndent = 0.dp)

                        CompactSwitchRow(
                            title = "支出使用绿色",
                            checked = settings.greenExpenseMode,
                            onCheckedChange = { enabled ->
                                viewModel.updateSettings { it.copy(greenExpenseMode = enabled) }
                            }
                        )

                        CompactHairlineDivider(startIndent = 0.dp)

                        Text(
                            text = "默认主币种",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val quick = if (settings.pinnedCurrencies.contains(settings.baseCurrency)) {
                                settings.pinnedCurrencies
                            } else {
                                settings.pinnedCurrencies + settings.baseCurrency
                            }
                            items(quick, key = { it }) { code ->
                                CurrencyBadge(
                                    currencyCode = code,
                                    selected = code.equals(settings.baseCurrency, true),
                                    onClick = {
                                        viewModel.updateSettings { it.copy(baseCurrency = code) }
                                    }
                                )
                            }
                            item(key = "more_base_currency") {
                                FilterChip(
                                    selected = false,
                                    onClick = { showBaseCurrencyPicker = true },
                                    label = { Text("更多") }
                                )
                            }
                        }
                    }
                }
            }

            // 卡片 2：隐私与安全
            item(key = "privacy_and_security_card") {
                YimuCard(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Rounded.Security, null, tint = MaterialTheme.colorScheme.primary)
                            Text(
                                text = "隐私与安全",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        CompactSwitchRow(
                            title = "隐藏全部金额",
                            checked = settings.privacyMaskEnabled,
                            onCheckedChange = { checked ->
                                viewModel.updateSettings { it.copy(privacyMaskEnabled = checked) }
                            }
                        )

                        CompactHairlineDivider(startIndent = 0.dp)

                        CompactSwitchRow(
                            title = "应用生物识别锁",
                            checked = settings.biometricLockEnabled,
                            onCheckedChange = { checked ->
                                if (checked && !BiometricLockHelper.canAuthenticate(context)) {
                                    viewModel.showMessage("当前设备未录入指纹/面容或锁屏密码，请先在系统设置中开启")
                                } else {
                                    viewModel.updateSettings { it.copy(biometricLockEnabled = checked) }
                                }
                            }
                        )

                        CompactHairlineDivider(startIndent = 0.dp)

                        CompactSwitchRow(
                            title = "后台卡片隐私模糊",
                            detail = "切到后台多任务隐藏界面内容，开启期间防截屏",
                            checked = settings.hideRecentsPreviewEnabled,
                            onCheckedChange = { checked ->
                                viewModel.updateSettings { it.copy(hideRecentsPreviewEnabled = checked) }
                            }
                        )
                    }
                }
            }

            // 卡片 3：应用信息
            item(key = "app_info_card") {
                YimuCard(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            YubraeBrandMark(size = 36.dp)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "余白 · Yubrae",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "版本 ${BuildConfig.VERSION_NAME}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        CompactHairlineDivider(startIndent = 0.dp)
                        Text(
                            text = "本地优先存储 · 支持 AES-256-GCM 加密备份与 yubrae://addbill 外部协议记账",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    if (showBaseCurrencyPicker) {
        CurrencyPickerDialog(
            selectedCode = settings.baseCurrency,
            pinnedCodes = settings.pinnedCurrencies,
            onSelect = { code -> viewModel.updateSettings { it.copy(baseCurrency = code) } },
            onDismiss = { showBaseCurrencyPicker = false }
        )
    }
}

@Composable
private fun ThemePresetGalleryCard(
    preset: KairoThemePreset,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val border = if (selected) {
        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
    }
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        border = border,
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .background(Brush.linearGradient(colors = preset.previewGradient))
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.align(Alignment.CenterStart),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(preset.previewPrimary)
                            .border(1.5.dp, Color.White.copy(alpha = 0.92f), CircleShape)
                    )
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(preset.previewSecondary)
                            .border(1.dp, Color.White.copy(alpha = 0.88f), CircleShape)
                    )
                }
                if (selected) {
                    Surface(
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.94f),
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .size(20.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = "已选中",
                                tint = preset.previewPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = preset.nameZh,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = preset.taglineZh,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun PermissionHealthCard(
    report: DevicePermissionReport?,
    isBusy: Boolean,
    onOpenHyperOsGuide: () -> Unit,
    onRefresh: () -> Unit,
    onRootGrant: () -> Unit,
    onOpenNotificationListener: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onRequestSms: () -> Unit,
    onOpenOverlay: () -> Unit,
    onRequestPostNotification: () -> Unit,
    onOpenBatteryWhitelist: () -> Unit
) {
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
                        imageVector = Icons.Rounded.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column {
                        Text(
                            text = "设备与权限体检",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = if (report != null) "${report.manufacturer} · ${report.osDisplay}" else "检测中...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "刷新体检")
                }
            }

            if (report != null) {
                PermissionRow("Root 环境 (su)", report.isRooted, "已获取", "未检测", null)
                PermissionRow("LSPosed 框架", report.hasLsposedHint, "已就绪", "未检测", null)
                PermissionRow("通知监听", report.notificationListenerEnabled, "已授权", "未开启", onOpenNotificationListener)
                PermissionRow("无障碍服务", report.accessibilityServiceEnabled, "已开启", "未开启", onOpenAccessibility)
                PermissionRow("短信权限", report.smsPermissionGranted, "已授权", "未授权", onRequestSms)
                PermissionRow("悬浮窗权限", report.overlayPermissionGranted, "已授权", "未授权", onOpenOverlay)
                PermissionRow("系统通知", report.postNotificationGranted, "已授权", "未授权", onRequestPostNotification)
                PermissionRow("电池无限制", report.ignoringBatteryOptimizations, "无限制", "受限制", onOpenBatteryWhitelist)
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onRootGrant,
                    enabled = !isBusy,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    } else {
                        Icon(Icons.Rounded.AdminPanelSettings, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text("Root 一键授予权限")
                }

                OutlinedButton(
                    onClick = onOpenHyperOsGuide,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.Info, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("小米 HyperOS 防冻结指引")
                }
            }
        }
    }
}

@Composable
private fun PermissionRow(
    name: String,
    granted: Boolean,
    okLabel: String,
    failLabel: String,
    onAction: (() -> Unit)?
) {
    val semantic = KairoTheme.semantic
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = if (granted) semantic.incomeContainer else semantic.warningContainer
            ) {
                Text(
                    text = if (granted) okLabel else failLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (granted) semantic.income else semantic.warning,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
            if (!granted && onAction != null) {
                TextButton(
                    onClick = onAction,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Text("去设置", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun HubNavRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    statusText: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
            modifier = Modifier.size(34.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontSize = 14.5.sp,
                    lineHeight = 19.sp
                ),
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 11.5.sp,
                        lineHeight = 15.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (!statusText.isNullOrBlank()) {
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        lineHeight = 14.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(4.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
            modifier = Modifier.size(16.dp)
        )
    }
}

private fun safeStartActivity(context: Context, intent: Intent): Boolean {
    return runCatching {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    }.getOrDefault(false)
}
