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
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Handshake
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LocalOffer
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RequestQuote
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kairoledger.app.core.contract.DevicePermissionReport
import com.kairoledger.app.data.prefs.ThemeMode
import com.kairoledger.app.ui.components.CurrencyBadge
import com.kairoledger.app.ui.components.CurrencyPickerDialog
import com.kairoledger.app.ui.components.YimuCard
import com.kairoledger.app.ui.security.BiometricLockHelper
import com.kairoledger.app.ui.theme.KairoTheme
import com.kairoledger.app.ui.theme.KairoThemePreset
import com.kairoledger.app.ui.theme.KairoThemePresets
import com.kairoledger.app.ui.viewmodel.KairoMainViewModel

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
    val pendingReimburseCount by viewModel.pendingReimburseCount.collectAsStateWithLifecycle()
    val openDebtCount by viewModel.openDebtCount.collectAsStateWithLifecycle()
    MineHubScreen(
        viewModel = viewModel,
        onNavigate = onNavigate,
        onOpenLedgerManager = onOpenLedgerManager,
        pendingReimburseCount = pendingReimburseCount,
        openDebtCount = openDebtCount,
        onTogglePrivacy = {
            viewModel.updateSettings { it.copy(privacyMaskEnabled = !it.privacyMaskEnabled) }
        },
        contentPadding = contentPadding
    )
}

/**
 * 第 4 Tab「我的」中心：顶区贴合状态栏 + 3 张高内聚卡片（账务管理 / 自动记账与智能引擎 / 数据与偏好）。
 */
@Composable
fun MineHubScreen(
    viewModel: KairoMainViewModel,
    onNavigate: (String) -> Unit,
    onOpenLedgerManager: () -> Unit = {},
    pendingReimburseCount: Int = 0,
    openDebtCount: Int = 0,
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
    val ledgers by viewModel.ledgers.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.ensurePermissionsLoaded()
    }

    val activeLedgerName = remember(ledgers, settings.activeLedgerId) {
        when {
            settings.activeLedgerId == 0L -> "全部账本"
            else -> ledgers.firstOrNull { it.id == settings.activeLedgerId }?.name ?: "日常账本"
        }
    }

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

    val activeChannelCount = remember(settings) {
        listOf(
            settings.notificationCaptureEnabled,
            settings.smsCaptureEnabled,
            settings.accessibilityCaptureEnabled,
            settings.autoAccountingBridgeEnabled,
            settings.floatingConfirmEnabled
        ).count { it }
    }

    val styleLabel = if (settings.dynamicColor) {
        "动态取色"
    } else {
        KairoThemePresets.find(settings.themeStyle).nameZh
    }
    val modeLabel = when (settings.themeMode) {
        ThemeMode.SYSTEM -> "跟随系统"
        ThemeMode.LIGHT -> "浅色模式"
        ThemeMode.DARK -> "深色模式"
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            bottom = contentPadding.calculateBottomPadding() + 88.dp
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 顶部柔和薄荷绿渐变品牌区（贴合状态栏，无全局 TopAppBar 依赖）
        item(key = "mine_brand_hero") {
            MineBrandHeroBanner(
                privacyMasked = settings.privacyMaskEnabled,
                readyCount = readyCount,
                activeChannelCount = activeChannelCount,
                isRooted = report?.isRooted == true,
                topInsetPadding = contentPadding.calculateTopPadding(),
                onTogglePrivacy = onTogglePrivacy,
                onOpenAutoCapture = { onNavigate(SettingsRoutes.AUTO_CAPTURE) }
            )
        }

        // 卡片 1：账务管理（双列 4 格紧凑快捷入口）
        item(key = "mine_finance_card") {
            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                YimuCard(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "账务管理",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                            ) {
                                Text(
                                    text = activeLedgerName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            FinanceGridTile(
                                icon = Icons.AutoMirrored.Rounded.MenuBook,
                                title = "多账本管理",
                                subtitle = activeLedgerName,
                                accentColor = Color(0xFF2AA876),
                                badgeCount = 0,
                                onClick = onOpenLedgerManager,
                                modifier = Modifier.weight(1f)
                            )
                            FinanceGridTile(
                                icon = Icons.Rounded.Category,
                                title = "收支分类",
                                subtitle = "一/二级类目管理",
                                accentColor = Color(0xFF0284C7),
                                badgeCount = 0,
                                onClick = { onNavigate(SettingsRoutes.CATEGORIES) },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            FinanceGridTile(
                                icon = Icons.Rounded.RequestQuote,
                                title = "报销中心",
                                subtitle = if (pendingReimburseCount > 0) "待核销 $pendingReimburseCount 笔" else "多币种报销入账",
                                accentColor = Color(0xFFEA580C),
                                badgeCount = pendingReimburseCount,
                                onClick = { onNavigate(SettingsRoutes.REIMBURSE) },
                                modifier = Modifier.weight(1f)
                            )
                            FinanceGridTile(
                                icon = Icons.Rounded.Handshake,
                                title = "借入借出",
                                subtitle = if (openDebtCount > 0) "未结清 $openDebtCount 笔" else "往来应收应付",
                                accentColor = Color(0xFF6D28D9),
                                badgeCount = openDebtCount,
                                onClick = { onNavigate(SettingsRoutes.DEBTS) },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        FinanceGridTile(
                            icon = Icons.Rounded.LocalOffer,
                            title = "账单标签",
                            subtitle = "标签分组与自定义管理",
                            accentColor = Color(0xFF0D9488),
                            badgeCount = 0,
                            showTrailingArrow = true,
                            onClick = { onNavigate(SettingsRoutes.TAGS) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        // 卡片 2：自动记账与智能引擎
        item(key = "mine_automation_card") {
            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                YimuCard(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest) {
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        Text(
                            text = "自动记账与智能引擎",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                        HubNavRow(
                            icon = Icons.Rounded.Security,
                            title = "自动记账与权限中心",
                            subtitle = if (readyCount != null) {
                                "权限就绪 $readyCount/5 · 通知/短信/无障碍与悬浮窗开关"
                            } else {
                                "HyperOS 体检 · Root 一键授权 · 采集开关配置"
                            },
                            onClick = { onNavigate(SettingsRoutes.AUTO_CAPTURE) }
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                        )
                        HubNavRow(
                            icon = Icons.AutoMirrored.Rounded.ReceiptLong,
                            title = "原始捕获日志",
                            subtitle = "最近记录 ${logs.size} 条 · 报文详情与一键沉淀账户指纹",
                            onClick = { onNavigate(SettingsRoutes.LOGS) }
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                        )
                        HubNavRow(
                            icon = Icons.AutoMirrored.Rounded.Rule,
                            title = "账户指纹与正则规则",
                            subtitle = "已启用 ${rules.count { it.enabled }} 条规则 · 模拟通知文本实时测试",
                            onClick = { onNavigate(SettingsRoutes.RULES) }
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                        )
                        HubNavRow(
                            icon = Icons.Rounded.SmartToy,
                            title = "AI 大模型配置",
                            subtitle = if (settings.llmEnabled) "已启用 (${settings.llmModel})" else "未启用 · 支持 DeepSeek / OpenAI / Gemini",
                            onClick = { onNavigate(SettingsRoutes.LLM) }
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                        )
                        HubNavRow(
                            icon = Icons.Rounded.CurrencyExchange,
                            title = "多币种汇率中心",
                            subtitle = "主币种 ${settings.baseCurrency} · 网络同步与自定义汇率覆写",
                            onClick = { onNavigate(SettingsRoutes.RATES) }
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                        )
                        HubNavRow(
                            icon = Icons.Rounded.EventRepeat,
                            title = "周期性订阅账单",
                            subtitle = "${recurringRules.count { it.enabled }} 条启用中 · 支持外币订阅自动扣款",
                            onClick = { onNavigate(SettingsRoutes.RECURRING) }
                        )
                    }
                }
            }
        }

        // 卡片 3：数据与偏好
        item(key = "mine_preferences_card") {
            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                YimuCard(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest) {
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        Text(
                            text = "数据与偏好",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                        HubNavRow(
                            icon = Icons.Rounded.Backup,
                            title = "数据导入与加密备份",
                            subtitle = "通用记账表格/微信/支付宝 CSV · WebDAV 与本地 .klbk 加密备份",
                            onClick = { onNavigate(SettingsRoutes.IMPORT_BACKUP) }
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                        )
                        HubNavRow(
                            icon = Icons.Rounded.Palette,
                            title = "外观主题与安全隐私",
                            subtitle = "$styleLabel · $modeLabel · 主币种 ${settings.baseCurrency}${if (settings.biometricLockEnabled) " · 已开启应用锁" else ""}",
                            onClick = { onNavigate(SettingsRoutes.APPEARANCE) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MineBrandHeroBanner(
    privacyMasked: Boolean,
    readyCount: Int?,
    activeChannelCount: Int,
    isRooted: Boolean,
    topInsetPadding: androidx.compose.ui.unit.Dp,
    onTogglePrivacy: () -> Unit,
    onOpenAutoCapture: () -> Unit
) {
    val semantic = KairoTheme.semantic
    val gradientColors = semantic.heroGradient
    val titleColor = semantic.heroOnSurface
    val borderColor = semantic.heroBorder
    val pillBgColor = semantic.heroPillBg

    val topModifier = if (topInsetPadding > 0.dp) {
        Modifier.padding(top = topInsetPadding + 12.dp)
    } else {
        Modifier
            .statusBarsPadding()
            .padding(top = 12.dp)
    }

    Surface(
        shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp),
        color = Color.Transparent,
        border = BorderStroke(0.5.dp, borderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .background(Brush.linearGradient(colors = gradientColors))
                .then(topModifier)
                .padding(horizontal = 16.dp, vertical = 16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "我的",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = titleColor
                        )
                        Text(
                            text = "棱账 · 多币种私人账本",
                            style = MaterialTheme.typography.bodySmall,
                            color = titleColor.copy(alpha = 0.78f)
                        )
                    }

                    Surface(
                        shape = CircleShape,
                        color = pillBgColor,
                        border = BorderStroke(0.5.dp, borderColor)
                    ) {
                        IconButton(
                            onClick = onTogglePrivacy,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = if (privacyMasked) {
                                    Icons.Rounded.VisibilityOff
                                } else {
                                    Icons.Rounded.Visibility
                                },
                                contentDescription = if (privacyMasked) "关闭隐私遮罩" else "开启隐私遮罩",
                                tint = titleColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // 自动记账状态摘要条（点击跳转设置页）
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = pillBgColor,
                    border = BorderStroke(0.5.dp, borderColor),
                    onClick = onOpenAutoCapture,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val allReady = readyCount == 5
                        val badgeBg = when {
                            allReady -> semantic.incomeContainer
                            readyCount != null -> semantic.warningContainer
                            else -> MaterialTheme.colorScheme.primaryContainer
                        }
                        val badgeFg = when {
                            allReady -> semantic.income
                            readyCount != null -> semantic.warning
                            else -> MaterialTheme.colorScheme.primary
                        }
                        Surface(
                            shape = CircleShape,
                            color = badgeBg,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (allReady) Icons.Rounded.CheckCircle else Icons.Rounded.AutoAwesome,
                                    contentDescription = null,
                                    tint = badgeFg,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = if (readyCount != null) {
                                    "自动记账就绪 $readyCount/5 项权限 · 点击进入配置/体检"
                                } else {
                                    "正在检测自动记账权限 · 点击进入配置/体检"
                                },
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = buildString {
                                    append("已开启 $activeChannelCount/5 项采集与悬浮窗通道")
                                    if (isRooted) append(" · Root 环境已就绪")
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FinanceGridTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    accentColor: Color,
    badgeCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showTrailingArrow: Boolean = false
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BadgedBox(
                badge = {
                    if (badgeCount > 0) {
                        Badge(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        ) {
                            Text(if (badgeCount > 99) "99+" else badgeCount.toString())
                        }
                    }
                }
            ) {
                Surface(
                    shape = CircleShape,
                    color = accentColor.copy(alpha = 0.14f),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (showTrailingArrow) {
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
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
                title = { Text("自动记账与权限中心") },
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
                top = innerPadding.calculateTopPadding() + 8.dp,
                bottom = innerPadding.calculateBottomPadding() + 32.dp,
                start = 16.dp,
                end = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 小米 HyperOS / Root 权限体检面板
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

            // 全部自动记账开关
            item(key = "automation_switches") {
                YimuCard(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "自动记账核心开关",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
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
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                                SwitchSettingRow(
                                    title = "电商与外卖订单详情识别",
                                    subtitle = "支持拼多多、淘宝、京东、美团、饿了么已支付订单自动提取商品与跨渠道融合",
                                    checked = settings.accessibilityOrderDetailEnabled,
                                    onCheckedChange = { checked ->
                                        viewModel.updateSettings { it.copy(accessibilityOrderDetailEnabled = checked) }
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
                                    title = "悬浮窗快捷编辑面板",
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
        }
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
                title = { Text("外观主题与安全隐私") },
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
                top = innerPadding.calculateTopPadding() + 8.dp,
                bottom = innerPadding.calculateBottomPadding() + 32.dp,
                start = 16.dp,
                end = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 卡片 1：界面主题与主币种
            item(key = "theme_and_currency_card") {
                YimuCard(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Rounded.Palette, null, tint = MaterialTheme.colorScheme.primary)
                            Text(
                                text = "界面主题与主币种",
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
                                text = "界面视觉风格（10 套主题画廊）",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (settings.dynamicColor) {
                                Text(
                                    text = "动态取色已接管",
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
                            text = "深浅色模式",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
                            title = "OLED 纯黑深色背景",
                            subtitle = "深色模式下使用纯黑背景与逐级压暗容器，省电且降低夜间刺眼",
                            checked = settings.oledPureBlack,
                            onCheckedChange = { enabled ->
                                viewModel.updateSettings { it.copy(oledPureBlack = enabled) }
                            }
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

                        SwitchSettingRow(
                            title = "Material You 动态取色",
                            subtitle = "Android 12+ 自动提取壁纸色板，关闭则使用上方所选主题画廊",
                            checked = settings.dynamicColor,
                            onCheckedChange = { enabled ->
                                viewModel.updateSettings { it.copy(dynamicColor = enabled) }
                            }
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

                        Text(
                            text = "收支颜色习惯",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val expenseIncomeOptions = listOf(
                                Triple(false, "红支绿收 (国内习惯)", Color(0xFFE05252) to Color(0xFF22A06B)),
                                Triple(true, "绿支红收 (国际习惯)", Color(0xFF22A06B) to Color(0xFFE05252))
                            )
                            expenseIncomeOptions.forEach { (isGreenExpense, label, dotPair) ->
                                val selected = settings.greenExpenseMode == isGreenExpense
                                ExpenseHabitCapsule(
                                    label = label,
                                    expenseDotColor = dotPair.first,
                                    incomeDotColor = dotPair.second,
                                    selected = selected,
                                    onClick = {
                                        viewModel.updateSettings { it.copy(greenExpenseMode = isGreenExpense) }
                                    }
                                )
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

                        Text(
                            text = "默认主币种（记账折算基准）",
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
                                    label = { Text("更多币种") }
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
                        modifier = Modifier.padding(16.dp),
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

                        SwitchSettingRow(
                            title = "全局金额隐私遮罩",
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
private fun ExpenseHabitCapsule(
    label: String,
    expenseDotColor: Color,
    incomeDotColor: Color,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = CircleShape,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
            }
        ),
        modifier = Modifier
            .clip(CircleShape)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(expenseDotColor)
                )
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(incomeDotColor)
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
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
