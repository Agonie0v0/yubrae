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
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.AttachMoney
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BakeryDining
import androidx.compose.material.icons.rounded.BeachAccess
import androidx.compose.material.icons.rounded.Bed
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.Chair
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Checkroom
import androidx.compose.material.icons.rounded.ChildCare
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.ConfirmationNumber
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.CurrencyExchange
import androidx.compose.material.icons.rounded.CurrencyPound
import androidx.compose.material.icons.rounded.DeliveryDining
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Diamond
import androidx.compose.material.icons.rounded.DirectionsBoat
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.ElectricCar
import androidx.compose.material.icons.rounded.Euro
import androidx.compose.material.icons.rounded.EventRepeat
import androidx.compose.material.icons.rounded.FaceRetouchingNatural
import androidx.compose.material.icons.rounded.Fastfood
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Forest
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Handshake
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Hotel
import androidx.compose.material.icons.rounded.Icecream
import androidx.compose.material.icons.rounded.Kitchen
import androidx.compose.material.icons.rounded.LaptopMac
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.LocalBar
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.LocalGroceryStore
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.LocalLaundryService
import androidx.compose.material.icons.rounded.LocalMall
import androidx.compose.material.icons.rounded.LocalOffer
import androidx.compose.material.icons.rounded.LocalParking
import androidx.compose.material.icons.rounded.LocalPizza
import androidx.compose.material.icons.rounded.LocalShipping
import androidx.compose.material.icons.rounded.LocalTaxi
import androidx.compose.material.icons.rounded.Loyalty
import androidx.compose.material.icons.rounded.Luggage
import androidx.compose.material.icons.rounded.LunchDining
import androidx.compose.material.icons.rounded.MedicalServices
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.MonetizationOn
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.PedalBike
import androidx.compose.material.icons.rounded.Percent
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.RamenDining
import androidx.compose.material.icons.rounded.Redeem
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.Sell
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.ShoppingBasket
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.SportsSoccer
import androidx.compose.material.icons.rounded.Store
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.Stroller
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material.icons.rounded.Subway
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Toys
import androidx.compose.material.icons.rounded.Train
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.TwoWheeler
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material.icons.rounded.VolunteerActivism
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WineBar
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material.icons.rounded.Yard
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
        "lunch_dining" -> Icons.Rounded.LunchDining
        "delivery_dining" -> Icons.Rounded.DeliveryDining
        "fastfood" -> Icons.Rounded.Fastfood
        "local_cafe" -> Icons.Rounded.LocalCafe
        "coffee" -> Icons.Rounded.Coffee
        "bakery_dining" -> Icons.Rounded.BakeryDining
        "icecream" -> Icons.Rounded.Icecream
        "cake" -> Icons.Rounded.Cake
        "ramen_dining" -> Icons.Rounded.RamenDining
        "local_pizza" -> Icons.Rounded.LocalPizza
        "local_grocery_store" -> Icons.Rounded.LocalGroceryStore
        "shopping_basket" -> Icons.Rounded.ShoppingBasket
        "kitchen" -> Icons.Rounded.Kitchen
        "local_bar" -> Icons.Rounded.LocalBar
        "wine_bar" -> Icons.Rounded.WineBar
        "shopping_bag" -> Icons.Rounded.ShoppingBag
        "local_mall" -> Icons.Rounded.LocalMall
        "shopping_cart" -> Icons.Rounded.ShoppingCart
        "store" -> Icons.Rounded.Store
        "storefront" -> Icons.Rounded.Storefront
        "local_shipping" -> Icons.Rounded.LocalShipping
        "local_offer" -> Icons.Rounded.LocalOffer
        "loyalty" -> Icons.Rounded.Loyalty
        "sell" -> Icons.Rounded.Sell
        "directions_car" -> Icons.Rounded.DirectionsCar
        "electric_car" -> Icons.Rounded.ElectricCar
        "subway" -> Icons.Rounded.Subway
        "directions_bus" -> Icons.Rounded.DirectionsBus
        "local_taxi" -> Icons.Rounded.LocalTaxi
        "pedal_bike" -> Icons.Rounded.PedalBike
        "two_wheeler" -> Icons.Rounded.TwoWheeler
        "train" -> Icons.Rounded.Train
        "directions_boat" -> Icons.Rounded.DirectionsBoat
        "local_gas_station" -> Icons.Rounded.LocalGasStation
        "local_parking" -> Icons.Rounded.LocalParking
        "build" -> Icons.Rounded.Build
        "checkroom" -> Icons.Rounded.Checkroom
        "face_retouching_natural" -> Icons.Rounded.FaceRetouchingNatural
        "spa" -> Icons.Rounded.Spa
        "content_cut" -> Icons.Rounded.ContentCut
        "diamond" -> Icons.Rounded.Diamond
        "watch" -> Icons.Rounded.Watch
        "devices" -> Icons.Rounded.Devices
        "laptop_mac" -> Icons.Rounded.LaptopMac
        "phone_android" -> Icons.Rounded.PhoneAndroid
        "headphones" -> Icons.Rounded.Headphones
        "tv" -> Icons.Rounded.Tv
        "camera_alt" -> Icons.Rounded.CameraAlt
        "home" -> Icons.Rounded.Home
        "chair" -> Icons.Rounded.Chair
        "bed" -> Icons.Rounded.Bed
        "bolt" -> Icons.Rounded.Bolt
        "water_drop" -> Icons.Rounded.WaterDrop
        "lightbulb" -> Icons.Rounded.Lightbulb
        "wifi" -> Icons.Rounded.Wifi
        "cleaning_services" -> Icons.Rounded.CleaningServices
        "local_laundry_service" -> Icons.Rounded.LocalLaundryService
        "yard" -> Icons.Rounded.Yard
        "subscriptions" -> Icons.Rounded.Subscriptions
        "cloud_done" -> Icons.Rounded.CloudDone
        "smart_toy" -> Icons.Rounded.SmartToy
        "music_note" -> Icons.Rounded.MusicNote
        "sports_esports" -> Icons.Rounded.SportsEsports
        "movie" -> Icons.Rounded.Movie
        "celebration" -> Icons.Rounded.Celebration
        "confirmation_number" -> Icons.Rounded.ConfirmationNumber
        "fitness_center" -> Icons.Rounded.FitnessCenter
        "sports_soccer" -> Icons.Rounded.SportsSoccer
        "self_improvement" -> Icons.Rounded.SelfImprovement
        "forest" -> Icons.Rounded.Forest
        "beach_access" -> Icons.Rounded.BeachAccess
        "medical_services" -> Icons.Rounded.MedicalServices
        "local_hospital" -> Icons.Rounded.LocalHospital
        "medication" -> Icons.Rounded.Medication
        "health_and_safety" -> Icons.Rounded.HealthAndSafety
        "flight" -> Icons.Rounded.Flight
        "hotel" -> Icons.Rounded.Hotel
        "luggage" -> Icons.Rounded.Luggage
        "school" -> Icons.Rounded.School
        "book" -> Icons.Rounded.Book
        "menu_book" -> Icons.AutoMirrored.Rounded.MenuBook
        "edit_note" -> Icons.Rounded.EditNote
        "calculate" -> Icons.Rounded.Calculate
        "palette" -> Icons.Rounded.Palette
        "favorite" -> Icons.Rounded.Favorite
        "card_giftcard" -> Icons.Rounded.CardGiftcard
        "redeem" -> Icons.Rounded.Redeem
        "volunteer_activism" -> Icons.Rounded.VolunteerActivism
        "handshake" -> Icons.Rounded.Handshake
        "groups" -> Icons.Rounded.Groups
        "pets" -> Icons.Rounded.Pets
        "child_care" -> Icons.Rounded.ChildCare
        "stroller" -> Icons.Rounded.Stroller
        "toys" -> Icons.Rounded.Toys
        "work" -> Icons.Rounded.Work
        "payments" -> Icons.Rounded.Payments
        "account_balance_wallet", "wallet" -> Icons.Rounded.AccountBalanceWallet
        "account_balance" -> Icons.Rounded.AccountBalance
        "credit_card" -> Icons.Rounded.CreditCard
        "savings" -> Icons.Rounded.Savings
        "trending_up" -> Icons.AutoMirrored.Rounded.TrendingUp
        "trending_down" -> Icons.AutoMirrored.Rounded.TrendingDown
        "monetization_on" -> Icons.Rounded.MonetizationOn
        "attach_money" -> Icons.Rounded.AttachMoney
        "euro" -> Icons.Rounded.Euro
        "currency_pound" -> Icons.Rounded.CurrencyPound
        "currency_exchange" -> Icons.Rounded.CurrencyExchange
        "percent" -> Icons.Rounded.Percent
        "receipt_long" -> Icons.AutoMirrored.Rounded.ReceiptLong
        "replay" -> Icons.Rounded.Replay
        "chat" -> Icons.Rounded.Chat
        "swap_horiz" -> Icons.Rounded.SwapHoriz
        "tune" -> Icons.Rounded.Tune
        "label" -> Icons.AutoMirrored.Rounded.Label
        "more_horiz" -> Icons.Rounded.MoreHoriz
        "category" -> Icons.Rounded.Category
        else -> Icons.Rounded.Category
    }

    val selectableCategoryIcons: List<Pair<String, String>> = listOf(
        "restaurant" to "餐饮",
        "lunch_dining" to "正餐",
        "delivery_dining" to "外卖",
        "fastfood" to "快餐",
        "local_cafe" to "咖啡",
        "coffee" to "茶饮",
        "bakery_dining" to "甜品",
        "icecream" to "冷饮",
        "cake" to "蛋糕",
        "ramen_dining" to "面食",
        "local_pizza" to "披萨",
        "local_grocery_store" to "生鲜",
        "shopping_basket" to "买菜",
        "kitchen" to "做饭",
        "local_bar" to "聚餐",
        "wine_bar" to "酒水",
        "shopping_bag" to "购物",
        "local_mall" to "百货",
        "shopping_cart" to "超市",
        "store" to "日用",
        "storefront" to "门店",
        "local_shipping" to "快递",
        "local_offer" to "补贴",
        "loyalty" to "会员",
        "sell" to "闲置",
        "directions_car" to "汽车",
        "electric_car" to "电车",
        "subway" to "地铁",
        "directions_bus" to "公交",
        "local_taxi" to "打车",
        "pedal_bike" to "单车",
        "two_wheeler" to "骑行",
        "train" to "高铁",
        "directions_boat" to "轮渡",
        "local_gas_station" to "加油",
        "local_parking" to "停车",
        "build" to "维修",
        "checkroom" to "服饰",
        "face_retouching_natural" to "护肤",
        "spa" to "美容",
        "content_cut" to "理发",
        "diamond" to "首饰",
        "watch" to "腕表",
        "devices" to "数码",
        "laptop_mac" to "电脑",
        "phone_android" to "话费",
        "headphones" to "配件",
        "tv" to "家电",
        "camera_alt" to "摄影",
        "home" to "居家",
        "chair" to "家具",
        "bed" to "床品",
        "bolt" to "水电",
        "water_drop" to "水费",
        "lightbulb" to "照明",
        "wifi" to "网络",
        "cleaning_services" to "家政",
        "local_laundry_service" to "洗护",
        "yard" to "园艺",
        "subscriptions" to "订阅",
        "cloud_done" to "云服务",
        "smart_toy" to "AI",
        "music_note" to "音乐",
        "sports_esports" to "游戏",
        "movie" to "电影",
        "celebration" to "聚会",
        "confirmation_number" to "门票",
        "fitness_center" to "健身",
        "sports_soccer" to "运动",
        "self_improvement" to "瑜伽",
        "forest" to "露营",
        "beach_access" to "度假",
        "medical_services" to "医疗",
        "local_hospital" to "挂号",
        "medication" to "买药",
        "health_and_safety" to "保险",
        "flight" to "差旅",
        "hotel" to "住宿",
        "luggage" to "旅游",
        "school" to "学习",
        "book" to "书籍",
        "menu_book" to "文具",
        "edit_note" to "考证",
        "calculate" to "账务",
        "palette" to "艺术",
        "favorite" to "人情",
        "card_giftcard" to "礼金",
        "redeem" to "礼物",
        "volunteer_activism" to "捐赠",
        "handshake" to "往来",
        "groups" to "社交",
        "pets" to "宠物",
        "child_care" to "育儿",
        "stroller" to "童车",
        "toys" to "玩具",
        "work" to "工资",
        "payments" to "薪酬",
        "account_balance_wallet" to "钱包",
        "account_balance" to "银行",
        "credit_card" to "刷卡",
        "savings" to "储蓄",
        "trending_up" to "理财",
        "trending_down" to "亏损",
        "monetization_on" to "收益",
        "attach_money" to "外币",
        "currency_exchange" to "汇兑",
        "percent" to "利息",
        "receipt_long" to "报销",
        "replay" to "退款",
        "label" to "标签",
        "category" to "分类",
        "more_horiz" to "其他"
    )

    val selectableAccountIcons: List<Pair<String, String>> = listOf(
        "account_balance_wallet" to "钱包",
        "chat" to "微信",
        "payments" to "现金",
        "directions_bus" to "公交卡",
        "subway" to "地铁卡",
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
        0xFF16A34AL,
        0xFF0D9488L,
        0xFF0F766EL,
        0xFF0284C7L,
        0xFF1677FFL,
        0xFF1D4ED8L,
        0xFF4F46E5L,
        0xFF6D28D9L,
        0xFF7C3AEDL,
        0xFFDB2777L,
        0xFFEC4899L,
        0xFFE11D48L,
        0xFFDC2626L,
        0xFFEA580CL,
        0xFFD97706L,
        0xFFE89B2DL,
        0xFF475569L
    )

    fun fallbackTagColorHex(tagName: String): Long {
        val idx = kotlin.math.abs(tagName.hashCode()) % selectableColorPalette.size
        return selectableColorPalette[idx]
    }
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
        else -> Icons.AutoMirrored.Rounded.ReceiptLong
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

/**
 * 彩色标签胶囊组件（支持展示态与多选交互态）。
 */
@Composable
fun TagBadgeChip(
    name: String,
    colorHex: Long,
    selected: Boolean = false,
    compact: Boolean = false,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val tagColor = Color(colorHex)
    val shape = if (compact) RoundedCornerShape(7.dp) else CircleShape
    val bgColor = when {
        selected -> tagColor.copy(alpha = 0.18f)
        onClick == null -> tagColor.copy(alpha = 0.11f)
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val fgColor = when {
        selected -> tagColor
        onClick == null -> tagColor
        else -> MaterialTheme.colorScheme.onSurface
    }
    val border = when {
        selected -> BorderStroke(1.2.dp, tagColor)
        onClick == null -> BorderStroke(0.5.dp, tagColor.copy(alpha = 0.25f))
        else -> null
    }

    val clickModifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Surface(
        modifier = modifier.then(clickModifier),
        shape = shape,
        color = bgColor,
        contentColor = fgColor,
        border = border
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = if (compact) 6.dp else 10.dp,
                vertical = if (compact) 2.dp else 5.dp
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 5.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = tagColor,
                modifier = Modifier.size(if (compact) 6.dp else 7.dp)
            ) {}
            Text(
                text = name,
                style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
                color = fgColor,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (selected && onClick != null && !compact) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = tagColor,
                    modifier = Modifier.size(13.dp)
                )
            }
        }
    }
}
