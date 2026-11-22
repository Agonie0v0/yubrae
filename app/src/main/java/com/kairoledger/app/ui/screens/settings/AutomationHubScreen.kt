package com.kairoledger.app.ui.screens.settings

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.CurrencyExchange
import androidx.compose.material.icons.rounded.EventRepeat
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Handshake
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RequestQuote
import androidx.compose.material.icons.rounded.Rule
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kairoledger.app.core.contract.DevicePermissionReport
import com.kairoledger.app.data.prefs.ThemeMode
import com.kairoledger.app.data.prefs.ThemeStyle
import com.kairoledger.app.ui.components.YimuCard
import com.kairoledger.app.ui.security.BiometricLockHelper
import com.kairoledger.app.ui.theme.KairoTheme
import com.kairoledger.app.ui.viewmodel.KairoMainViewModel

object SettingsRoutes {
    const val HUB = "settings_hub"
    const val LOGS = "settings_logs"
    const val RULES = "settings_rules"
    const val LLM = "settings_llm"
    const val RATES = "settings_rates"
    const val RECURRING = "settings_recurring"
    const val IMPORT_BACKUP = "settings_import_backup"
    const val CATEGORIES = "settings_categories"
    const val REIMBURSE = "settings_reimburse"
    const val DEBTS = "settings_debts"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AutomationHubScreen(
    viewModel: KairoMainViewModel,
    onNavigate: (String) -> Unit,
    onOpenLedgerManager: () -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val report by viewModel.permissionReport.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
    val logs by viewModel.captureLogs.collectAsStateWithLifecycle()
    val rules by viewModel.parseRules.collectAsStateWithLifecycle()
    val recurringRules by viewModel.recurringRules.collectAsStateWithLifecycle()

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

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 88.dp,
            start = 16.dp,
            end = 16.dp
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 小米 14 / Root / HyperOS 权限体检面板
        item(key = "permission_health") {
            PermissionHealthCard(
                report = report,
                isBusy = isBusy,
                showHyperOsGuide = showHyperOsGuide,
                onToggleHyperOsGuide = { showHyperOsGuide = !showHyperOsGuide },
                onRefresh = { viewModel.refreshPermissions() },
                onRootGrant = { viewModel.tryRootGrantPermissions() },
                onOpenNotificationListener = {
                    safeStartActivity(context, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                },
                onOpenAccessibility = {
                    safeStartActivity(context, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                },
                onRequestSms = {
                    smsLauncher.launch(
                        arrayOf(
                            Manifest.permission.RECEIVE_SMS,
                            Manifest.permission.READ_SMS
                        )
                    )
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
                        postNotifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
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

        // 核心自动化开关
        item(key = "automation_switches") {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "自动记账核心开关",
                        style = MaterialTheme.typography.titleMedium
                    )

                    SwitchSettingRow(
                        title = "通知监听自动记账",
                        subtitle = "捕获微信、支付宝、云闪付、银行与境外卡 App 通知",
                        checked = settings.notificationCaptureEnabled,
                        onCheckedChange = { checked ->
                            viewModel.updateSettings { it.copy(notificationCaptureEnabled = checked) }
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

                    SwitchSettingRow(
                        title = "银行短信自动解析",
                        subtitle = "识别动账短信并与 60 秒内通知自动去重融合",
                        checked = settings.smsCaptureEnabled,
                        onCheckedChange = { checked ->
                            viewModel.updateSettings { it.copy(smsCaptureEnabled = checked) }
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

                    SwitchSettingRow(
                        title = "屏幕账单页自动识别（无障碍）",
                        subtitle = "仅在打开微信、支付宝、云闪付及银行支付/账单页时本地提取，不读取聊天",
                        checked = settings.accessibilityCaptureEnabled,
                        onCheckedChange = { checked ->
                            viewModel.updateSettings { it.copy(accessibilityCaptureEnabled = checked) }
                        }
                    )

                    AnimatedVisibility(visible = settings.accessibilityCaptureEnabled) {
                        Column(
                            modifier = Modifier.padding(start = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SwitchSettingRow(
                                title = "支付成功页自动唤起弹窗",
                                subtitle = "付款后自动提取单号与实付金额，并与 60 秒内通知/短信融合",
                                checked = settings.accessibilityPaySuccessEnabled,
                                onCheckedChange = { checked ->
                                    viewModel.updateSettings { it.copy(accessibilityPaySuccessEnabled = checked) }
                                }
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                            SwitchSettingRow(
                                title = "历史账单详情页自动唤起（补记模式）",
                                subtitle = "打开历史账单详情时自动补记并还原真实交易时间，同单号自动防重",
                                checked = settings.accessibilityBillDetailEnabled,
                                onCheckedChange = { checked ->
                                    viewModel.updateSettings { it.copy(accessibilityBillDetailEnabled = checked) }
                                }
                            )
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

                    SwitchSettingRow(
                        title = "AutoAccounting 协议兼容",
                        subtitle = "支持外部广播与 kairoledger://addbill 链接记账",
                        checked = settings.autoAccountingBridgeEnabled,
                        onCheckedChange = { checked ->
                            viewModel.updateSettings { it.copy(autoAccountingBridgeEnabled = checked) }
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

                    SwitchSettingRow(
                        title = "悬浮窗极速确认卡片",
                        subtitle = "捕获账单后弹出悬浮窗，支持倒计时自动确认（无权限降级为通知）",
                        checked = settings.floatingConfirmEnabled,
                        onCheckedChange = { checked ->
                            viewModel.updateSettings { it.copy(floatingConfirmEnabled = checked) }
                        }
                    )

                    AnimatedVisibility(visible = settings.floatingConfirmEnabled) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SwitchSettingRow(
                                title = "一木风可交互悬浮记账面板",
                                subtitle = "直接在悬浮窗内切换支出/收入、改分类、换账户、设报销与编辑备注",
                                checked = settings.floatingRichEditorEnabled,
                                onCheckedChange = { checked ->
                                    viewModel.updateSettings { it.copy(floatingRichEditorEnabled = checked) }
                                }
                            )
                            Text(
                                text = "悬浮窗自动确认倒计时：",
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

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                    SwitchSettingRow(
                        title = "不确定账单默认进入待核对收件箱",
                        subtitle = "未匹配到明确账户或低置信度识别时先标为待核对",
                        checked = settings.defaultPendingWhenUncertain,
                        onCheckedChange = { checked ->
                            viewModel.updateSettings { it.copy(defaultPendingWhenUncertain = checked) }
                        }
                    )
                }
            }
        }

        // 外观与隐私安全设置（一木清新风 / 棱镜青蓝 / 深浅色 / 隐私打码 / 指纹应用锁）
        item(key = "appearance_and_security") {
            YimuCard(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Rounded.Palette, null, tint = MaterialTheme.colorScheme.primary)
                        Text(text = "一木风外观与隐私安全", style = MaterialTheme.typography.titleMedium)
                    }

                    Text(
                        text = "界面视觉风格",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = settings.themeStyle != ThemeStyle.PRISM_TEAL,
                            onClick = { viewModel.updateSettings { it.copy(themeStyle = ThemeStyle.YIMU_FRESH, dynamicColor = false) } },
                            label = { Text("一木清新绿卡片 (推荐)") }
                        )
                        FilterChip(
                            selected = settings.themeStyle == ThemeStyle.PRISM_TEAL,
                            onClick = { viewModel.updateSettings { it.copy(themeStyle = ThemeStyle.PRISM_TEAL) } },
                            label = { Text("棱镜极简青蓝") }
                        )
                    }

                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            ThemeMode.SYSTEM to "跟随系统",
                            ThemeMode.LIGHT to "浅色模式",
                            ThemeMode.DARK to "深色模式"
                        ).forEach { (mode, label) ->
                            FilterChip(
                                selected = settings.themeMode == mode,
                                onClick = { viewModel.updateSettings { it.copy(themeMode = mode) } },
                                label = { Text(label) }
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                    SwitchSettingRow(
                        title = "全局金额隐私打码",
                        subtitle = "首页、资产与报表所有金额隐藏显示为 ****",
                        checked = settings.privacyMaskEnabled,
                        onCheckedChange = { checked ->
                            viewModel.updateSettings { it.copy(privacyMaskEnabled = checked) }
                        }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                    SwitchSettingRow(
                        title = "指纹 / 面容应用锁",
                        subtitle = "启动或从后台切回棱账时自动要求生物识别或设备密码解锁",
                        checked = settings.biometricLockEnabled,
                        onCheckedChange = { checked ->
                            if (checked && !BiometricLockHelper.canAuthenticate(context)) {
                                viewModel.showMessage("当前设备未录入指纹/面容或锁屏密码，请先在系统设置中开启")
                            } else {
                                viewModel.updateSettings { it.copy(biometricLockEnabled = checked) }
                            }
                        }
                    )
                }
            }
        }

        // 一木风格账务中心快捷入口
        item(key = "ledger_finance_entries") {
            YimuCard(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest) {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    Text(
                        text = "账本 · 分类 · 报销与借贷",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    HubNavRow(
                        icon = Icons.Rounded.Category,
                        title = "一/二级分类管理",
                        subtitle = "自定义收支一级分类、二级子分类、图标与色卡",
                        onClick = { onNavigate(SettingsRoutes.CATEGORIES) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                    HubNavRow(
                        icon = Icons.AutoMirrored.Rounded.MenuBook,
                        title = "多账本管理",
                        subtitle = "新建或切换日常、旅行、装修独立账本与默认币种",
                        onClick = onOpenLedgerManager
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                    HubNavRow(
                        icon = Icons.Rounded.RequestQuote,
                        title = "报销中心",
                        subtitle = "多选待报销账单、自动汇率折算并一键核销回款",
                        onClick = { onNavigate(SettingsRoutes.REIMBURSE) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                    HubNavRow(
                        icon = Icons.Rounded.Handshake,
                        title = "借入借出往来",
                        subtitle = "按往来人管理应收/应付、分次核销并联动账户余额",
                        onClick = { onNavigate(SettingsRoutes.DEBTS) }
                    )
                }
            }
        }

        // 二级功能页面导航列表
        item(key = "feature_entries") {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    Text(
                        text = "规则引擎 · 汇率 · 导入与同步",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    HubNavRow(
                        icon = Icons.Rounded.ReceiptLong,
                        title = "原始捕获日志",
                        subtitle = "最近记录 ${logs.size} 条 · 报文详情与一键沉淀账户指纹",
                        onClick = { onNavigate(SettingsRoutes.LOGS) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                    HubNavRow(
                        icon = Icons.Rounded.Rule,
                        title = "账户指纹、正则规则与测试沙盒",
                        subtitle = "已启用 ${rules.count { it.enabled }} 条规则 · 模拟通知文本实时测试",
                        onClick = { onNavigate(SettingsRoutes.RULES) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                    HubNavRow(
                        icon = Icons.Rounded.SmartToy,
                        title = "AI 大模型兜底配置",
                        subtitle = if (settings.llmEnabled) "已启用 (${settings.llmModel})" else "未启用 · 支持 DeepSeek / OpenAI / Gemini",
                        onClick = { onNavigate(SettingsRoutes.LLM) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                    HubNavRow(
                        icon = Icons.Rounded.CurrencyExchange,
                        title = "多币种汇率中心",
                        subtitle = "主币种 ${settings.baseCurrency} · 网络同步与自定义汇率覆写",
                        onClick = { onNavigate(SettingsRoutes.RATES) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                    HubNavRow(
                        icon = Icons.Rounded.EventRepeat,
                        title = "周期性订阅账单",
                        subtitle = "${recurringRules.count { it.enabled }} 条启用中 · 支持外币订阅自动扣款",
                        onClick = { onNavigate(SettingsRoutes.RECURRING) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                    HubNavRow(
                        icon = Icons.Rounded.Backup,
                        title = "账单导入、加密同步与外观设置",
                        subtitle = "一木/微信/支付宝 CSV 导入 · WebDAV 加密备份 · 深色主题",
                        onClick = { onNavigate(SettingsRoutes.IMPORT_BACKUP) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionHealthCard(
    report: DevicePermissionReport?,
    isBusy: Boolean,
    showHyperOsGuide: Boolean,
    onToggleHyperOsGuide: () -> Unit,
    onRefresh: () -> Unit,
    onRootGrant: () -> Unit,
    onOpenNotificationListener: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onRequestSms: () -> Unit,
    onOpenOverlay: () -> Unit,
    onRequestPostNotification: () -> Unit,
    onOpenBatteryWhitelist: () -> Unit
) {
    val semantic = KairoTheme.semantic
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
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
                            text = if (report != null) "${report.manufacturer} · ${report.osDisplay}" else "正在检测权限状态...",
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
                PermissionRow("Root 环境 (su)", report.isRooted, "已获取", "未检测到", null)
                PermissionRow("LSPosed 框架环境", report.hasLsposedHint, "已就绪", "未检测到", null)
                PermissionRow("通知监听使用权", report.notificationListenerEnabled, "已授权", "未开启", onOpenNotificationListener)
                PermissionRow("无障碍页面监听", report.accessibilityServiceEnabled, "已开启", "未开启", onOpenAccessibility)
                PermissionRow("短信读取与接收权限", report.smsPermissionGranted, "已授权", "未授权", onRequestSms)
                PermissionRow("悬浮窗显示权限", report.overlayPermissionGranted, "已授权", "未授权", onOpenOverlay)
                PermissionRow("系统通知发送权限", report.postNotificationGranted, "已授权", "未授权", onRequestPostNotification)
                PermissionRow("电池优化白名单", report.ignoringBatteryOptimizations, "无限制", "受限制", onOpenBatteryWhitelist)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onRootGrant,
                    enabled = !isBusy,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f)
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
                    onClick = onToggleHyperOsGuide,
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Rounded.Info, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("保活指引")
                    Icon(
                        imageVector = if (showHyperOsGuide) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            AnimatedVisibility(visible = showHyperOsGuide) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = semantic.transferContainer.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "小米 14 / HyperOS 后台防杀设置清单",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text("1. 长按应用图标 → 应用信息 → 省电策略 → 选择「无限制」", style = MaterialTheme.typography.bodySmall)
                        Text("2. 应用信息 → 权限管理 → 开启「自启动」与「显示悬浮窗」", style = MaterialTheme.typography.bodySmall)
                        Text("3. 系统设置 → 通知与控制中心 → 通知使用权 → 勾选「棱账自动记账监听」", style = MaterialTheme.typography.bodySmall)
                        Text("4. 系统设置 → 更多设置 → 无障碍 → 已下载的应用 → 无障碍设置开启「棱账」", style = MaterialTheme.typography.bodySmall)
                        Text("5. 多任务界面长按/下拉「棱账」卡片点击锁头图标，防止系统清理", style = MaterialTheme.typography.bodySmall)
                    }
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
private fun SwitchSettingRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun HubNavRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
            modifier = Modifier.size(38.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
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
