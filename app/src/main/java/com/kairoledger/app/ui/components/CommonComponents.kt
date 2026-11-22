package com.kairoledger.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.AttachMoney
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Checkroom
import androidx.compose.material.icons.rounded.ChildCare
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.CurrencyExchange
import androidx.compose.material.icons.rounded.CurrencyPound
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Euro
import androidx.compose.material.icons.rounded.EventRepeat
import androidx.compose.material.icons.rounded.Fastfood
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Hotel
import androidx.compose.material.icons.rounded.LaptopMac
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.LocalMall
import androidx.compose.material.icons.rounded.LocalShipping
import androidx.compose.material.icons.rounded.LocalTaxi
import androidx.compose.material.icons.rounded.LunchDining
import androidx.compose.material.icons.rounded.MedicalServices
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Redeem
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.ShoppingBasket
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Store
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material.icons.rounded.Subway
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kairoledger.app.core.model.CurrencyCatalog
import com.kairoledger.app.data.db.CaptureChannel
import com.kairoledger.app.data.db.ParseStatus
import com.kairoledger.app.data.db.TransactionType
import com.kairoledger.app.ui.theme.KairoTheme
import com.kairoledger.app.ui.theme.tabular
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

object KairoIcons {
    fun iconFor(key: String): ImageVector = when (key.trim().lowercase()) {
        "restaurant" -> Icons.Rounded.Restaurant
        "directions_car" -> Icons.Rounded.DirectionsCar
        "shopping_bag" -> Icons.Rounded.ShoppingBag
        "home" -> Icons.Rounded.Home
        "subscriptions" -> Icons.Rounded.Subscriptions
        "sports_esports" -> Icons.Rounded.SportsEsports
        "medical_services" -> Icons.Rounded.MedicalServices
        "flight" -> Icons.Rounded.Flight
        "currency_exchange" -> Icons.Rounded.CurrencyExchange
        "more_horiz" -> Icons.Rounded.MoreHoriz
        "lunch_dining" -> Icons.Rounded.LunchDining
        "local_cafe" -> Icons.Rounded.LocalCafe
        "shopping_basket" -> Icons.Rounded.ShoppingBasket
        "subway" -> Icons.Rounded.Subway
        "local_taxi" -> Icons.Rounded.LocalTaxi
        "local_mall" -> Icons.Rounded.LocalMall
        "devices" -> Icons.Rounded.Devices
        "bolt" -> Icons.Rounded.Bolt
        "wifi" -> Icons.Rounded.Wifi
        "cloud_done" -> Icons.Rounded.CloudDone
        "smart_toy" -> Icons.Rounded.SmartToy
        "work" -> Icons.Rounded.Work
        "card_giftcard" -> Icons.Rounded.CardGiftcard
        "trending_up" -> Icons.AutoMirrored.Rounded.TrendingUp
        "replay" -> Icons.Rounded.Replay
        "laptop_mac" -> Icons.Rounded.LaptopMac
        "attach_money" -> Icons.Rounded.AttachMoney
        "chat" -> Icons.Rounded.Chat
        "account_balance_wallet", "wallet" -> Icons.Rounded.AccountBalanceWallet
        "payments" -> Icons.Rounded.Payments
        "account_balance" -> Icons.Rounded.AccountBalance
        "credit_card" -> Icons.Rounded.CreditCard
        "euro" -> Icons.Rounded.Euro
        "currency_pound" -> Icons.Rounded.CurrencyPound
        "savings" -> Icons.Rounded.Savings
        "store" -> Icons.Rounded.Store
        "fastfood" -> Icons.Rounded.Fastfood
        "movie" -> Icons.Rounded.Movie
        "school" -> Icons.Rounded.School
        "pets" -> Icons.Rounded.Pets
        "fitness_center" -> Icons.Rounded.FitnessCenter
        "local_hospital" -> Icons.Rounded.LocalHospital
        "receipt_long" -> Icons.Rounded.ReceiptLong
        "swap_horiz" -> Icons.Rounded.SwapHoriz
        "tune" -> Icons.Rounded.Tune
        "menu_book" -> Icons.AutoMirrored.Rounded.MenuBook
        "book" -> Icons.Rounded.Book
        "coffee" -> Icons.Rounded.Coffee
        "shopping_cart" -> Icons.Rounded.ShoppingCart
        "directions_bus" -> Icons.Rounded.DirectionsBus
        "local_gas_station" -> Icons.Rounded.LocalGasStation
        "hotel" -> Icons.Rounded.Hotel
        "music_note" -> Icons.Rounded.MusicNote
        "checkroom" -> Icons.Rounded.Checkroom
        "child_care" -> Icons.Rounded.ChildCare
        "favorite" -> Icons.Rounded.Favorite
        "spa" -> Icons.Rounded.Spa
        "redeem" -> Icons.Rounded.Redeem
        "phone_android" -> Icons.Rounded.PhoneAndroid
        "local_shipping" -> Icons.Rounded.LocalShipping
        "water_drop" -> Icons.Rounded.WaterDrop
        "category" -> Icons.Rounded.Category
        else -> Icons.Rounded.Category
    }

    val selectableCategoryIcons: List<Pair<String, String>> = listOf(
        "restaurant" to "餐饮",
        "lunch_dining" to "正餐",
        "local_cafe" to "咖啡",
        "shopping_basket" to "买菜",
        "shopping_bag" to "购物",
        "shopping_cart" to "超市",
        "local_mall" to "百货",
        "checkroom" to "服饰",
        "directions_car" to "汽车",
        "subway" to "地铁",
        "directions_bus" to "公交",
        "local_taxi" to "打车",
        "local_gas_station" to "加油",
        "flight" to "差旅",
        "hotel" to "住宿",
        "home" to "居家",
        "bolt" to "水电",
        "water_drop" to "水费",
        "wifi" to "网络",
        "phone_android" to "话费",
        "devices" to "数码",
        "subscriptions" to "订阅",
        "smart_toy" to "AI",
        "cloud_done" to "云服务",
        "sports_esports" to "游戏",
        "movie" to "电影",
        "music_note" to "音乐",
        "medical_services" to "医疗",
        "fitness_center" to "健身",
        "spa" to "美容",
        "school" to "学习",
        "book" to "书籍",
        "pets" to "宠物",
        "child_care" to "育儿",
        "card_giftcard" to "礼金",
        "redeem" to "礼物",
        "favorite" to "人情",
        "local_shipping" to "快递",
        "work" to "工资",
        "trending_up" to "理财",
        "replay" to "退款",
        "currency_exchange" to "汇兑",
        "more_horiz" to "其他"
    )

    val selectableAccountIcons: List<Pair<String, String>> = listOf(
        "account_balance_wallet" to "钱包",
        "chat" to "微信",
        "payments" to "现金",
        "account_balance" to "银行",
        "credit_card" to "信用卡",
        "attach_money" to "美元",
        "euro" to "欧元",
        "currency_pound" to "英镑",
        "savings" to "储蓄",
        "trending_up" to "理财"
    )

    val selectableColorPalette: List<Long> = listOf(
        0xFF2AA876L,
        0xFF006874L,
        0xFF07C160L,
        0xFF1677FFL,
        0xFF1D4ED8L,
        0xFF6D28D9L,
        0xFFDC2626L,
        0xFFEA580CL,
        0xFF0D9488L,
        0xFFDB2777L,
        0xFFE89B2DL,
        0xFF475569L
    )
}

object KairoFormats {
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private val monthDayFormatter = DateTimeFormatter.ofPattern("M月d日")

    fun formatDate(epochMillis: Long): String {
        val zdt = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault())
        return zdt.format(dateFormatter)
    }

    fun formatTime(epochMillis: Long): String {
        val zdt = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault())
        return zdt.format(timeFormatter)
    }

    fun formatDateTime(epochMillis: Long): String {
        val zdt = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault())
        return zdt.format(dateTimeFormatter)
    }

    fun formatDayHeader(date: LocalDate): String {
        val today = LocalDate.now()
        val prefix = when (date) {
            today -> "今天 · "
            today.minusDays(1) -> "昨天 · "
            else -> ""
        }
        val dayOfWeek = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.SIMPLIFIED_CHINESE)
        return "$prefix${date.format(monthDayFormatter)} $dayOfWeek"
    }

    fun channelLabel(channel: String): String = when (channel) {
        CaptureChannel.MANUAL -> "手动记账"
        CaptureChannel.NOTIFICATION -> "自动通知"
        CaptureChannel.SMS -> "银行短信"
        CaptureChannel.SCREEN -> "页面识别"
        CaptureChannel.AUTO_ACCOUNTING -> "AutoAccounting"
        CaptureChannel.SHARE -> "分享导入"
        CaptureChannel.AI -> "AI 识别"
        CaptureChannel.IMPORT -> "账单导入"
        CaptureChannel.RECURRING -> "周期订阅"
        else -> channel
    }

    fun channelIcon(channel: String): ImageVector = when (channel) {
        CaptureChannel.MANUAL -> Icons.Rounded.Edit
        CaptureChannel.NOTIFICATION -> Icons.Rounded.Notifications
        CaptureChannel.SMS -> Icons.Rounded.Sms
        CaptureChannel.SCREEN -> Icons.Rounded.DocumentScanner
        CaptureChannel.AUTO_ACCOUNTING -> Icons.Rounded.AutoAwesome
        CaptureChannel.SHARE -> Icons.Rounded.Share
        CaptureChannel.AI -> Icons.Rounded.SmartToy
        CaptureChannel.IMPORT -> Icons.Rounded.UploadFile
        CaptureChannel.RECURRING -> Icons.Rounded.EventRepeat
        else -> Icons.Rounded.ReceiptLong
    }

    fun parseStatusLabel(status: String): String = when (status) {
        ParseStatus.MATCHED_LOCAL -> "本地规则命中"
        ParseStatus.MATCHED_AI -> "AI 兜底命中"
        ParseStatus.MERGED_DEDUP -> "跨渠道去重融合"
        ParseStatus.IGNORED -> "非动账已忽略"
        ParseStatus.FAILED -> "解析失败"
        else -> status
    }

    fun transactionTypeLabel(type: String): String = when (type) {
        TransactionType.EXPENSE -> "支出"
        TransactionType.INCOME -> "收入"
        TransactionType.TRANSFER -> "转账"
        TransactionType.ADJUST -> "余额校准"
        else -> type
    }
}

@Composable
fun CurrencyBadge(
    currencyCode: String,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val info = CurrencyCatalog.find(currencyCode)
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val border = if (selected) {
        BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
    } else {
        null
    }

    Surface(
        modifier = modifier.then(
            if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
        ),
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor,
        border = border
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(text = info.flag, style = MaterialTheme.typography.labelMedium)
            Text(
                text = info.code,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun ChannelChip(
    channel: String,
    modifier: Modifier = Modifier
) {
    val label = KairoFormats.channelLabel(channel)
    val icon = KairoFormats.channelIcon(channel)
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(11.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun ParseStatusChip(
    status: String,
    modifier: Modifier = Modifier
) {
    val semantic = KairoTheme.semantic
    val (bg, fg) = when (status) {
        ParseStatus.MATCHED_LOCAL -> semantic.incomeContainer to semantic.income
        ParseStatus.MATCHED_AI -> semantic.transferContainer to semantic.transfer
        ParseStatus.MERGED_DEDUP -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        ParseStatus.IGNORED -> MaterialTheme.colorScheme.surfaceContainerHighest to MaterialTheme.colorScheme.onSurfaceVariant
        ParseStatus.FAILED -> semantic.expenseContainer to semantic.expense
        else -> MaterialTheme.colorScheme.surfaceContainerHigh to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = bg,
        contentColor = fg
    ) {
        Text(
            text = KairoFormats.parseStatusLabel(status),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
fun EmptyStateCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                modifier = Modifier.size(56.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (actionLabel != null && onAction != null) {
                Spacer(modifier = Modifier.height(14.dp))
                Button(onClick = onAction, shape = CircleShape) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
fun ConfirmActionDialog(
    title: String,
    message: String,
    confirmText: String = "确认",
    isDestructive: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = { Text(message, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = if (isDestructive) {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                } else {
                    ButtonDefaults.buttonColors()
                }
            ) {
                Text(confirmText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
fun CurrencyPickerDialog(
    selectedCode: String,
    pinnedCodes: List<String> = CurrencyCatalog.PINNED_CURRENCIES,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val ordered = CurrencyCatalog.orderedCodes(pinnedCodes)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择币种", style = MaterialTheme.typography.titleLarge) },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(ordered, key = { it }) { code ->
                    val info = CurrencyCatalog.find(code)
                    val isSelected = code.equals(selectedCode, ignoreCase = true)
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelect(info.code)
                                onDismiss()
                            },
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLow
                        }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = info.flag, style = MaterialTheme.typography.titleLarge)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${info.code} · ${info.nameZh}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    }
                                )
                                Text(
                                    text = "符号 ${info.symbol} · 小数位 ${info.minorDigits}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = "已选中",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}

@Composable
fun transactionAmountColor(type: String): Color {
    val semantic = KairoTheme.semantic
    return when (type) {
        TransactionType.EXPENSE -> semantic.expense
        TransactionType.INCOME -> semantic.income
        TransactionType.TRANSFER -> semantic.transfer
        else -> MaterialTheme.colorScheme.onSurface
    }
}

val LocalPrivacyMasked = compositionLocalOf { false }

fun formatPrivacyAmount(masked: Boolean, raw: String): String =
    if (masked) "****" else raw

/**
 * 棱账风格统一大圆角柔和卡片容器。
 */
@Composable
fun YimuCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLowest,
    cornerRadius: Dp = 18.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    val baseModifier = modifier.fillMaxWidth()
    val border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
    if (onClick != null) {
        Surface(
            modifier = baseModifier,
            shape = shape,
            color = containerColor,
            border = border,
            onClick = onClick
        ) {
            Column(content = content)
        }
    } else {
        Surface(
            modifier = baseModifier,
            shape = shape,
            color = containerColor,
            border = border
        ) {
            Column(content = content)
        }
    }
}

/**
 * 圆形彩色图标徽章（柔和浅底 + 高对比主题色图标）。
 */
@Composable
fun CategoryIconBadge(
    iconKey: String,
    colorHex: Long,
    size: Dp = 40.dp,
    iconSize: Dp = 20.dp,
    selected: Boolean = false,
    modifier: Modifier = Modifier
) {
    val accentColor = Color(colorHex)
    val bgColor = if (selected) accentColor else accentColor.copy(alpha = 0.14f)
    val fgColor = if (selected) Color.White else accentColor
    Surface(
        modifier = modifier.size(size),
        shape = CircleShape,
        color = bgColor,
        border = if (selected) BorderStroke(1.5.dp, accentColor) else null
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = KairoIcons.iconFor(iconKey),
                contentDescription = null,
                tint = fgColor,
                modifier = Modifier.size(iconSize)
            )
        }
    }
}

@Composable
fun AmountText(
    amountMicros: Long,
    currencyCode: String,
    showSign: Boolean = false,
    showCurrencyCode: Boolean = false,
    color: Color = MaterialTheme.colorScheme.onSurface,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.titleMedium,
    modifier: Modifier = Modifier
) {
    val privacyMasked = LocalPrivacyMasked.current
    val formatted = if (privacyMasked) {
        "****"
    } else {
        com.kairoledger.app.core.model.MoneyAmount.format(
            amountMicros = amountMicros,
            currencyCode = currencyCode,
            showSign = showSign,
            showCurrencyCode = showCurrencyCode
        )
    }
    Text(
        text = formatted,
        color = color,
        style = style.tabular(),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}
