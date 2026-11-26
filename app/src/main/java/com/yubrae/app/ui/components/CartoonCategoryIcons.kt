package com.yubrae.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.util.Locale

/**
 * 原创多色扁平卡通矢量图标库：
 * - 覆盖 DefaultSeedData 全部一/二级分类与核心账务语义的多色扁平卡通绘图；
 * - 扩展图标使用柔彩贴纸双色合成方案，确保全量 179 枚分类图标与钱包账户图标风格统一。
 */
object CartoonCategoryIcons {

    private val bespokeCategoryKeys: Set<String> = setOf(
        // 餐饮美食与饮品零食
        "restaurant", "lunch_dining", "dinner_dining", "rice_bowl", "brunch_dining",
        "delivery_dining", "fastfood", "takeout_dining", "bento",
        "local_cafe", "coffee", "free_breakfast", "emoji_food_beverage", "breakfast_dining",
        "bakery_dining", "cake", "cookie", "icecream",
        "local_grocery_store", "shopping_basket",
        "kitchen", "soup_kitchen", "ramen_dining",
        "local_bar", "wine_bar", "sports_bar", "liquor", "tapas", "local_pizza", "egg_alt",
        // 出行与交通
        "directions_car", "electric_car", "local_car_wash",
        "subway", "directions_bus", "tram",
        "local_taxi", "pedal_bike", "two_wheeler", "electric_scooter",
        "train", "directions_boat",
        "local_gas_station", "ev_station", "local_parking", "toll",
        "build", "handyman",
        // 购物百货与快递
        "shopping_bag", "local_mall", "shopping_cart", "add_shopping_cart",
        "store", "storefront", "local_convenience_store",
        "local_shipping", "inventory_2", "local_post_office",
        // 服饰美妆与奢侈品
        "checkroom", "dry_cleaning", "iron", "style",
        "face_retouching_natural", "auto_fix_high", "spa", "filter_vintage",
        "content_cut", "brush", "diamond", "watch",
        // 居家生活与水电网
        "home", "cottage", "chair", "bed", "weekend",
        "bolt", "lightbulb", "water_drop", "bathtub", "ac_unit",
        "propane_tank", "local_fire_department", "outdoor_grill",
        "wifi", "phone_android", "smartphone", "sim_card", "router",
        "cleaning_services", "local_laundry_service", "yard", "local_florist",
        // 数码娱乐与订阅
        "devices", "laptop_mac", "desktop_windows", "tablet_mac", "keyboard", "print",
        "headphones", "speaker", "tv",
        "subscriptions", "cloud_done", "smart_toy",
        "music_note", "mic", "sports_esports", "movie", "theater_comedy", "nightlife",
        "celebration", "confirmation_number",
        "fitness_center", "sports_soccer", "sports_basketball", "sports_tennis", "pool",
        "forest", "park", "hiking",
        // 医疗健康
        "medical_services", "local_hospital", "medication", "local_pharmacy",
        "vaccines", "health_and_safety", "monitor_heart", "healing", "bloodtype",
        // 差旅住宿
        "flight", "airplane_ticket", "hotel", "luggage", "backpack", "beach_access", "explore", "map",
        // 教育文化
        "school", "book", "menu_book", "edit_note", "palette", "science", "translate",
        // 人情社交与宠物育儿
        "favorite", "volunteer_activism", "card_giftcard", "redeem",
        "handshake", "groups", "elderly",
        "pets", "child_care", "stroller", "toys",
        // 金融收入报销与其他
        "currency_exchange", "percent", "calculate", "request_quote",
        "trending_up", "show_chart", "candlestick_chart", "bar_chart", "multiline_chart", "pie_chart",
        "trending_down",
        "more_horiz", "category", "label",
        "work", "business_center", "payments", "local_atm",
        "savings", "attach_money", "monetization_on", "euro", "currency_pound", "currency_yen", "currency_bitcoin",
        "replay", "receipt_long", "sell", "local_offer", "loyalty", "emoji_events", "star",
        "account_balance", "assured_workload", "account_balance_wallet", "wallet",
        "credit_card", "credit_score", "add_card"
    )

    fun hasBespokeIcon(iconKey: String): Boolean =
        iconKey.trim().lowercase(Locale.ROOT) in bespokeCategoryKeys

    fun drawCartoonCategory(scope: DrawScope, iconKey: String, scratchPath: Path) {
        val key = iconKey.trim().lowercase(Locale.ROOT)
        val unit = scope.size.minDimension / 24f
        if (unit <= 0f) return
        scope.scale(scaleX = unit, scaleY = unit, pivot = Offset.Zero) {
            when (key) {
                // 1. 餐饮主食
                "restaurant", "lunch_dining", "dinner_dining", "rice_bowl", "ramen_dining", "brunch_dining" ->
                    drawRamenBowl(scratchPath)
                "delivery_dining", "fastfood", "takeout_dining", "bento" ->
                    drawTakeoutBox(scratchPath)
                "local_cafe", "coffee", "free_breakfast", "emoji_food_beverage", "breakfast_dining" ->
                    drawCoffeeCup(scratchPath)
                "bakery_dining", "cake", "cookie", "icecream" ->
                    drawCupcake(scratchPath)
                "local_grocery_store", "shopping_basket" ->
                    drawFreshApple(scratchPath)
                "kitchen", "soup_kitchen" ->
                    drawCookingPot()
                "local_bar", "wine_bar", "sports_bar", "liquor", "tapas", "local_pizza", "egg_alt" ->
                    drawCocktailDrink(scratchPath)

                // 2. 出行与交通
                "directions_car", "electric_car", "local_car_wash" ->
                    drawCartoonCar()
                "subway", "directions_bus", "tram" ->
                    drawMetroTrain()
                "local_taxi" ->
                    drawYellowTaxi()
                "pedal_bike", "two_wheeler", "electric_scooter" ->
                    drawBicycle()
                "train", "directions_boat" ->
                    drawHighSpeedTrain(scratchPath)
                "local_gas_station", "ev_station" ->
                    drawGasPump(scratchPath)
                "local_parking", "toll" ->
                    drawParkingSign(scratchPath)
                "build", "handyman" ->
                    drawTools(scratchPath)

                // 3. 购物百货与快递
                "shopping_bag", "local_mall" ->
                    drawShoppingBag()
                "shopping_cart", "add_shopping_cart" ->
                    drawShoppingCart(scratchPath)
                "store", "storefront", "local_convenience_store" ->
                    drawShopFront()
                "local_shipping", "inventory_2", "local_post_office" ->
                    drawParcelBox(scratchPath)

                // 4. 服饰美妆
                "checkroom", "dry_cleaning", "iron", "style" ->
                    drawTShirt(scratchPath)
                "face_retouching_natural", "auto_fix_high", "spa", "filter_vintage" ->
                    drawLipstick(scratchPath)
                "content_cut", "brush" ->
                    drawScissors(scratchPath)
                "diamond", "watch" ->
                    drawDiamond(scratchPath)

                // 5. 居家水电网
                "home", "cottage", "chair", "bed", "weekend" ->
                    drawCozyHouse(scratchPath)
                "bolt", "lightbulb" ->
                    drawElectricBolt(scratchPath)
                "water_drop", "bathtub", "ac_unit" ->
                    drawWaterDrop(scratchPath)
                "propane_tank", "local_fire_department", "outdoor_grill" ->
                    drawGasFlame(scratchPath)
                "wifi", "phone_android", "smartphone", "sim_card", "router" ->
                    drawMobileCard()
                "cleaning_services", "local_laundry_service", "yard", "local_florist" ->
                    drawCleaningSparkle(scratchPath)

                // 6. 数码与娱乐
                "devices", "laptop_mac", "desktop_windows", "tablet_mac", "keyboard", "print" ->
                    drawLaptop()
                "headphones", "speaker" ->
                    drawHeadphones()
                "tv" ->
                    drawRetroTv()
                "subscriptions", "cloud_done" ->
                    drawCloudSub(scratchPath)
                "smart_toy" ->
                    drawAiBot()
                "music_note", "mic" ->
                    drawMusicNote()
                "sports_esports" ->
                    drawGamepad()
                "movie", "theater_comedy", "nightlife" ->
                    drawMoviePopcorn(scratchPath)
                "celebration", "confirmation_number" ->
                    drawPartyPopper(scratchPath)
                "fitness_center", "sports_soccer", "sports_basketball", "sports_tennis", "pool" ->
                    drawDumbbell()
                "forest", "park", "hiking" ->
                    drawPineTree(scratchPath)

                // 7. 医疗健康
                "medical_services", "healing" ->
                    drawMedicalKit()
                "local_hospital" ->
                    drawHospital(scratchPath)
                "medication", "local_pharmacy", "vaccines", "bloodtype" ->
                    drawMedicinePill()
                "health_and_safety", "monitor_heart" ->
                    drawHealthShield(scratchPath)

                // 8. 差旅住宿
                "flight", "airplane_ticket" ->
                    drawAirplane(scratchPath)
                "hotel" ->
                    drawHotelBuilding()
                "luggage", "backpack", "beach_access", "explore", "map" ->
                    drawLuggage()

                // 9. 教育文化
                "school", "science", "translate" ->
                    drawGradCap(scratchPath)
                "book", "menu_book", "palette" ->
                    drawBookStack()
                "edit_note" ->
                    drawExamBanner(scratchPath)

                // 10. 人情社交、宠物育儿
                "favorite" ->
                    drawCuteHeart(scratchPath, Color(0xFFF43F5E), Color(0xFFFFA4B6))
                "volunteer_activism" ->
                    drawCareHeart(scratchPath)
                "card_giftcard" ->
                    drawRedEnvelope()
                "redeem" ->
                    drawGiftBox(scratchPath)
                "handshake", "groups", "elderly" ->
                    drawSocialCare(scratchPath)
                "pets" ->
                    drawPetPaw()
                "child_care", "stroller", "toys" ->
                    drawBabyBottle()

                // 11. 金融收入报销与杂项
                "currency_exchange" ->
                    drawExchangeCoins()
                "percent", "calculate", "request_quote" ->
                    drawPercentCoin()
                "trending_up", "show_chart", "candlestick_chart", "bar_chart", "multiline_chart", "pie_chart" ->
                    drawTrendUp(scratchPath)
                "trending_down" ->
                    drawTrendDown(scratchPath)
                "more_horiz", "category", "label" ->
                    drawFourGrid()
                "work", "business_center" ->
                    drawBriefcase()
                "payments", "local_atm" ->
                    drawCashNotes()
                "savings", "attach_money", "monetization_on", "euro", "currency_pound", "currency_yen", "currency_bitcoin" ->
                    drawMoneyPouch(scratchPath)
                "replay" ->
                    drawRefundLoop(scratchPath)
                "receipt_long" ->
                    drawReceiptNote(scratchPath)
                "sell", "local_offer", "loyalty", "emoji_events", "star" ->
                    drawPriceTag(scratchPath)
                "account_balance", "assured_workload" ->
                    drawBankBuilding(scratchPath)
                "account_balance_wallet", "wallet" ->
                    drawLeatherWallet()
                "credit_card", "credit_score", "add_card" ->
                    drawGoldCard()
            }
        }
    }
}

/**
 * 多色扁平卡通分类图标控件：优先使用原创高精多色绘图，其余扩展图标以双色柔彩卡通贴纸呈现。
 */
@Composable
fun CartoonCategoryIcon(
    iconKey: String,
    size: Dp = 28.dp,
    fallbackColorHex: Long = 0xFF2AA876L,
    modifier: Modifier = Modifier
) {
    val normalizedKey = iconKey.trim().lowercase(Locale.ROOT)
    val isBespoke = CartoonCategoryIcons.hasBespokeIcon(normalizedKey)
    if (isBespoke) {
        val scratchPath = remember { Path() }
        Canvas(modifier = modifier.size(size)) {
            CartoonCategoryIcons.drawCartoonCategory(this, normalizedKey, scratchPath)
        }
    } else {
        CartoonStickerIcon(
            vector = KairoIcons.categoryIcon(iconKey),
            accentColor = Color(fallbackColorHex),
            size = size,
            modifier = modifier
        )
    }
}

/**
 * 账户卡通图标：优先识别微信/支付宝/银行/卡片/公交卡语义品牌色，呈现图 2 清爽鲜明的卡通质感。
 */
@Composable
fun CartoonAccountIcon(
    iconKey: String,
    accountName: String = "",
    colorHex: Long = 0xFF2AA876L,
    size: Dp = 30.dp,
    modifier: Modifier = Modifier
) {
    val brandStyle = remember(accountName, iconKey, colorHex) {
        resolveAccountBrandVisual(accountName = accountName, iconKey = iconKey, fallbackColorHex = colorHex)
    }
    if (brandStyle.bespokeKey != null) {
        val scratchPath = remember { Path() }
        Canvas(modifier = modifier.size(size)) {
            drawAccountBrandCanvas(this, brandStyle.bespokeKey, scratchPath)
        }
    } else {
        val accent = Color(brandStyle.accentHex)
        Surface(
            shape = CircleShape,
            color = accent.copy(alpha = 0.14f),
            border = BorderStroke(0.8.dp, accent.copy(alpha = 0.25f)),
            modifier = modifier.size(size)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = KairoIcons.accountIcon(iconKey),
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(size * 0.56f)
                )
            }
        }
    }
}

@Composable
private fun CartoonStickerIcon(
    vector: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: Color,
    size: Dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(size * 0.32f),
            color = accentColor.copy(alpha = 0.16f),
            border = BorderStroke(0.8.dp, accentColor.copy(alpha = 0.34f)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(contentAlignment = Alignment.Center) {
                // 左上角卡通高光气泡
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(x = size * 0.14f, y = size * 0.12f)
                        .size(size * 0.14f)
                        .background(Color.White.copy(alpha = 0.72f), CircleShape)
                )
                Icon(
                    imageVector = vector,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(size * 0.58f)
                )
                // 右下角撞色小徽点
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = (-size * 0.1f), y = (-size * 0.1f))
                        .size(size * 0.17f)
                        .background(Color(0xFFFBBF24), CircleShape)
                )
            }
        }
    }
}

private data class AccountBrandVisual(
    val bespokeKey: String? = null,
    val accentHex: Long
)

private fun resolveAccountBrandVisual(
    accountName: String,
    iconKey: String,
    fallbackColorHex: Long
): AccountBrandVisual {
    val lowerName = accountName.trim().lowercase(Locale.ROOT)
    val lowerKey = iconKey.trim().lowercase(Locale.ROOT)
    return when {
        // 明确图标键优先区分，避免账户图标选择器内同组图标大面积撞车
        lowerKey == "receipt_long" -> AccountBrandVisual("credit_bill", 0xFF0EA5E9L)
        lowerKey == "attach_money" -> AccountBrandVisual(null, 0xFF16A34AL)
        lowerKey == "euro" -> AccountBrandVisual(null, 0xFF2563EBL)
        lowerKey == "currency_pound" -> AccountBrandVisual(null, 0xFF7C3AEDL)
        lowerKey == "currency_yen" -> AccountBrandVisual(null, 0xFFE11D48L)
        lowerKey == "currency_exchange" -> AccountBrandVisual("fx_coins", 0xFF0284C7L)
        lowerKey == "payments" -> AccountBrandVisual("cash_notes", 0xFF16A34AL)
        lowerKey == "local_atm" -> AccountBrandVisual(null, 0xFFF59E0BL)
        lowerKey == "atm" -> AccountBrandVisual(null, 0xFF0284C7L)
        lowerKey == "assured_workload" -> AccountBrandVisual(null, 0xFF0D9488L)
        lowerKey == "credit_score" -> AccountBrandVisual(null, 0xFF059669L)
        lowerKey == "add_card" -> AccountBrandVisual(null, 0xFF6366F1L)
        lowerKey == "calendar_month" -> AccountBrandVisual(null, 0xFFEA580CL)
        lowerKey == "subway" -> AccountBrandVisual(null, 0xFF0284C7L)
        lowerKey == "trending_up" -> AccountBrandVisual("trend_up", 0xFF16A34AL)
        lowerKey == "candlestick_chart" -> AccountBrandVisual(null, 0xFFDC2626L)
        lowerKey == "show_chart" -> AccountBrandVisual(null, 0xFF2563EBL)
        lowerKey == "pie_chart" -> AccountBrandVisual(null, 0xFF7C3AEDL)
        lowerKey == "bar_chart" -> AccountBrandVisual(null, 0xFF0D9488L)
        lowerKey == "currency_bitcoin" -> AccountBrandVisual(null, 0xFFF59E0BL)

        // 常见品牌账户语义识别
        lowerName.contains("微信") || lowerKey == "chat" -> AccountBrandVisual("wechat", 0xFF07C160L)
        lowerName.contains("花呗") && !lowerName.contains("白条") -> AccountBrandVisual("credit_bill", 0xFF06B6D4L)
        lowerName.contains("支付宝") || lowerName.contains("余利宝") ||
            lowerName.contains("余额宝") || lowerKey == "qr_code_2" -> AccountBrandVisual("alipay", 0xFF1677FFL)
        lowerName.contains("qq") -> AccountBrandVisual("qq", 0xFF1E293BL)
        lowerName.contains("京东") || lowerName.contains("白条") -> AccountBrandVisual("jd", 0xFFE11D48L)
        lowerName.contains("建行") || lowerName.contains("建设银行") -> AccountBrandVisual("bank_blue", 0xFF1D4ED8L)
        lowerName.contains("工商") || lowerName.contains("工行") -> AccountBrandVisual("bank_red", 0xFFC81E1EL)
        lowerName.contains("中行") || lowerName.contains("中国银行") -> AccountBrandVisual("bank_crimson", 0xFFB91C1CL)
        lowerName.contains("招商") || lowerName.contains("招行") -> AccountBrandVisual("bank_red", 0xFFE11D48L)
        lowerName.contains("邮政") || lowerName.contains("邮储") -> AccountBrandVisual("bank_green", 0xFF15803DL)
        lowerName.contains("农业") || lowerName.contains("农行") -> AccountBrandVisual("bank_green", 0xFF0D9488L)
        lowerName.contains("农商") || lowerName.contains("渝农") -> AccountBrandVisual("bank_rose", 0xFFDC2626L)
        lowerName.contains("光大") -> AccountBrandVisual("bank_purple", 0xFF7C3AEDL)
        lowerName.contains("公交") || lowerName.contains("畅通") || lowerName.contains("地铁") ||
            lowerName.contains("一卡通") || lowerKey == "directions_bus" ->
            AccountBrandVisual("transit_card", 0xFF10B981L)
        lowerKey == "credit_card" ||
            lowerName.contains("visa") || lowerName.contains("master") || lowerName.contains("plasma") ||
            lowerName.contains("信用卡") -> AccountBrandVisual("gold_card", 0xFFF59E0BL)
        lowerKey == "savings" ||
            lowerName.contains("基金") || lowerName.contains("股票") || lowerName.contains("理财") ->
            AccountBrandVisual("invest_pouch", 0xFFF59E0BL)
        lowerKey == "account_balance" ->
            AccountBrandVisual("bank_building", fallbackColorHex)
        lowerKey in setOf("account_balance_wallet", "wallet") ->
            AccountBrandVisual("wallet", fallbackColorHex)
        else -> AccountBrandVisual(null, fallbackColorHex)
    }
}

private fun drawAccountBrandCanvas(scope: DrawScope, bespokeKey: String, p: Path) {
    val unit = scope.size.minDimension / 24f
    if (unit <= 0f) return
    scope.scale(scaleX = unit, scaleY = unit, pivot = Offset.Zero) {
        when (bespokeKey) {
            "wechat" -> {
                drawCircle(color = Color(0xFF07C160), radius = 10.5f, center = Offset(12f, 12f))
                drawOval(color = Color.White, topLeft = Offset(5.5f, 7.2f), size = Size(10f, 7.6f))
                drawCircle(color = Color(0xFF07C160), radius = 0.75f, center = Offset(8.8f, 10.6f))
                drawCircle(color = Color(0xFF07C160), radius = 0.75f, center = Offset(12.2f, 10.6f))
                drawOval(color = Color(0xFFDCFCE7), topLeft = Offset(10.5f, 10.2f), size = Size(8.2f, 6.4f))
                drawCircle(color = Color(0xFF07C160), radius = 0.65f, center = Offset(13.2f, 13f))
                drawCircle(color = Color(0xFF07C160), radius = 0.65f, center = Offset(15.8f, 13f))
            }
            "alipay" -> {
                drawRoundRect(
                    color = Color(0xFF1677FF),
                    topLeft = Offset(2f, 2f),
                    size = Size(20f, 20f),
                    cornerRadius = CornerRadius(5.5f, 5.5f)
                )
                drawLine(Color.White, Offset(7.2f, 8.2f), Offset(16.8f, 8.2f), strokeWidth = 1.6f, cap = StrokeCap.Round)
                drawLine(Color.White, Offset(12f, 5.5f), Offset(12f, 12f), strokeWidth = 1.6f, cap = StrokeCap.Round)
                drawLine(Color.White, Offset(8.5f, 11.5f), Offset(15.5f, 11.5f), strokeWidth = 1.4f, cap = StrokeCap.Round)
                drawArc(
                    color = Color.White,
                    startAngle = 10f,
                    sweepAngle = 210f,
                    useCenter = false,
                    topLeft = Offset(6.8f, 12.2f),
                    size = Size(9.5f, 6f),
                    style = Stroke(width = 1.6f, cap = StrokeCap.Round)
                )
                drawLine(Color.White, Offset(13.2f, 13.8f), Offset(18.2f, 17.8f), strokeWidth = 1.6f, cap = StrokeCap.Round)
            }
            "qq" -> {
                drawCircle(color = Color(0xFF0F172A), radius = 10f, center = Offset(12f, 12.2f))
                drawOval(color = Color.White, topLeft = Offset(7.2f, 10.8f), size = Size(9.6f, 8.6f))
                drawOval(color = Color(0xFFF43F5E), topLeft = Offset(7.2f, 9.6f), size = Size(9.6f, 2.3f))
                drawCircle(color = Color.White, radius = 1.4f, center = Offset(9.8f, 7.6f))
                drawCircle(color = Color.White, radius = 1.4f, center = Offset(14.2f, 7.6f))
                drawCircle(color = Color(0xFF0F172A), radius = 0.6f, center = Offset(10f, 7.6f))
                drawCircle(color = Color(0xFF0F172A), radius = 0.6f, center = Offset(14f, 7.6f))
                drawOval(color = Color(0xFFF59E0B), topLeft = Offset(9.5f, 8.4f), size = Size(5f, 2f))
            }
            "jd" -> {
                drawCircle(color = Color(0xFFE11D48), radius = 10f, center = Offset(12f, 12f))
                drawCircle(color = Color.White, radius = 5.4f, center = Offset(12f, 13f))
                drawCircle(color = Color.White, radius = 2.6f, center = Offset(7.4f, 8.6f))
                drawCircle(color = Color.White, radius = 2.6f, center = Offset(16.6f, 8.6f))
                drawCircle(color = Color(0xFFE11D48), radius = 0.8f, center = Offset(10.2f, 12f))
                drawCircle(color = Color(0xFFE11D48), radius = 0.8f, center = Offset(13.8f, 12f))
                drawCircle(color = Color(0xFFE11D48), radius = 1.1f, center = Offset(12f, 14f))
            }
            "bank_blue" -> {
                drawCircle(color = Color(0xFF1D4ED8), radius = 10f, center = Offset(12f, 12f))
                drawCircle(color = Color.White, radius = 5.4f, center = Offset(12f, 12f), style = Stroke(width = 2.1f))
                drawRect(color = Color(0xFF1D4ED8), topLeft = Offset(11.2f, 5.5f), size = Size(1.6f, 13f))
                drawRect(color = Color.White, topLeft = Offset(10f, 10f), size = Size(4f, 4f))
            }
            "bank_red", "bank_crimson" -> {
                val red = if (bespokeKey == "bank_red") Color(0xFFD92638) else Color(0xFFB91C1C)
                drawCircle(color = red, radius = 10f, center = Offset(12f, 12f))
                drawCircle(color = Color.White, radius = 5.4f, center = Offset(12f, 12f), style = Stroke(width = 1.8f))
                drawLine(Color.White, Offset(12f, 6.4f), Offset(12f, 17.6f), strokeWidth = 1.6f)
                drawRoundRect(
                    color = red,
                    topLeft = Offset(9.7f, 9.7f),
                    size = Size(4.6f, 4.6f),
                    cornerRadius = CornerRadius(0.6f, 0.6f)
                )
                drawRoundRect(
                    color = Color.White,
                    topLeft = Offset(9.7f, 9.7f),
                    size = Size(4.6f, 4.6f),
                    cornerRadius = CornerRadius(0.6f, 0.6f),
                    style = Stroke(width = 1.3f)
                )
            }
            "bank_rose" -> {
                drawCircle(color = Color(0xFFDC2626), radius = 10f, center = Offset(12f, 12f))
                drawArc(
                    color = Color.White,
                    startAngle = 35f,
                    sweepAngle = 290f,
                    useCenter = false,
                    topLeft = Offset(6.5f, 6.5f),
                    size = Size(11f, 11f),
                    style = Stroke(width = 2.2f, cap = StrokeCap.Round)
                )
                drawCircle(color = Color(0xFFFDE047), radius = 2.1f, center = Offset(12f, 12f))
            }
            "bank_green" -> {
                drawCircle(color = Color(0xFF15803D), radius = 10f, center = Offset(12f, 12f))
                drawLine(Color.White, Offset(7f, 8.5f), Offset(17f, 8.5f), strokeWidth = 1.8f, cap = StrokeCap.Round)
                drawLine(Color.White, Offset(8.5f, 12f), Offset(15.5f, 12f), strokeWidth = 1.8f, cap = StrokeCap.Round)
                drawLine(Color.White, Offset(7f, 15.5f), Offset(17f, 15.5f), strokeWidth = 1.8f, cap = StrokeCap.Round)
            }
            "bank_purple" -> {
                drawCircle(color = Color(0xFF7C3AED), radius = 10f, center = Offset(12f, 12f))
                drawOval(
                    color = Color(0xFFFDE047),
                    topLeft = Offset(6.5f, 8f),
                    size = Size(11f, 8f),
                    style = Stroke(width = 2f)
                )
                drawCircle(color = Color.White, radius = 2.1f, center = Offset(12f, 12f))
            }
            "transit_card" -> {
                drawRoundRect(
                    color = Color(0xFF10B981),
                    topLeft = Offset(2.5f, 4.5f),
                    size = Size(19f, 15f),
                    cornerRadius = CornerRadius(3.5f, 3.5f)
                )
                drawRoundRect(
                    color = Color(0xFFECFDF5),
                    topLeft = Offset(6.5f, 7f),
                    size = Size(11f, 5.2f),
                    cornerRadius = CornerRadius(1.5f, 1.5f)
                )
                drawCircle(color = Color(0xFFFDE047), radius = 1.2f, center = Offset(7.8f, 15.6f))
                drawCircle(color = Color(0xFFFDE047), radius = 1.2f, center = Offset(16.2f, 15.6f))
            }
            "gold_card" -> drawGoldCard()
            "invest_pouch" -> drawMoneyPouch(p)
            "trend_up" -> drawTrendUp(p)
            "fx_coins" -> drawExchangeCoins()
            "cash_notes" -> drawCashNotes()
            "credit_bill" -> drawReceiptNote(p)
            "bank_building" -> drawBankBuilding(p)
            else -> drawLeatherWallet()
        }
    }
}

// =====================================================================
// 以下为高精多色扁平卡通绘图函数（统一 24x24 坐标系）
// =====================================================================

private fun DrawScope.drawRamenBowl(p: Path) {
    // 筷子
    drawLine(Color(0xFF92400E), Offset(13.5f, 3.2f), Offset(20.5f, 10.5f), strokeWidth = 1.4f, cap = StrokeCap.Round)
    drawLine(Color(0xFFB45309), Offset(16.5f, 2.8f), Offset(21f, 9.2f), strokeWidth = 1.4f, cap = StrokeCap.Round)
    // 面汤与热面
    drawOval(color = Color(0xFFFDE68A), topLeft = Offset(4f, 8.8f), size = Size(16f, 5.2f))
    drawCircle(color = Color(0xFFFB923C), radius = 1.6f, center = Offset(9f, 10.8f))
    drawCircle(color = Color(0xFF22C55E), radius = 1.1f, center = Offset(13.8f, 11f))
    // 瓷碗下半圆弧
    p.reset()
    p.moveTo(3.5f, 11.8f)
    p.lineTo(20.5f, 11.8f)
    p.cubicTo(20.2f, 17.5f, 17f, 20f, 12f, 20f)
    p.cubicTo(7f, 20f, 3.8f, 17.5f, 3.5f, 11.8f)
    p.close()
    drawPath(p, color = Color(0xFFF97316))
    drawRoundRect(Color(0xFFEA580C), topLeft = Offset(8f, 19.3f), size = Size(8f, 1.8f), cornerRadius = CornerRadius(0.9f, 0.9f))
    drawLine(Color(0xFFFFEDD5), Offset(6f, 14f), Offset(18f, 14f), strokeWidth = 1.2f, cap = StrokeCap.Round)
}

private fun DrawScope.drawTakeoutBox(p: Path) {
    drawArc(
        color = Color(0xFFEA580C),
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(8f, 3.2f),
        size = Size(8f, 5f),
        style = Stroke(width = 1.6f, cap = StrokeCap.Round)
    )
    p.reset()
    p.moveTo(4.2f, 8.2f)
    p.lineTo(19.8f, 8.2f)
    p.lineTo(18.2f, 20.5f)
    p.lineTo(5.8f, 20.5f)
    p.close()
    drawPath(p, color = Color(0xFFFBBF24))
    drawRoundRect(Color(0xFFF59E0B), topLeft = Offset(3.5f, 6.8f), size = Size(17f, 3.2f), cornerRadius = CornerRadius(1.4f, 1.4f))
    drawCircle(color = Color.White, radius = 2.4f, center = Offset(12f, 14.5f))
    drawCircle(color = Color(0xFFEA580C), radius = 1.1f, center = Offset(12f, 14.5f))
}

private fun DrawScope.drawCoffeeCup(p: Path) {
    drawLine(Color(0xFFD97706), Offset(8.5f, 4.2f), Offset(9.2f, 2.5f), strokeWidth = 1.2f, cap = StrokeCap.Round)
    drawLine(Color(0xFFD97706), Offset(13.5f, 4.2f), Offset(14.2f, 2.5f), strokeWidth = 1.2f, cap = StrokeCap.Round)
    drawRoundRect(Color(0xFF92400E), topLeft = Offset(5.2f, 6.4f), size = Size(11.6f, 2.8f), cornerRadius = CornerRadius(1.2f, 1.2f))
    p.reset()
    p.moveTo(6f, 9f)
    p.lineTo(16f, 9f)
    p.lineTo(14.8f, 20.4f)
    p.lineTo(7.2f, 20.4f)
    p.close()
    drawPath(p, color = Color(0xFFFEF3C7))
    drawRoundRect(Color(0xFFD97706), topLeft = Offset(6.3f, 12.2f), size = Size(9.4f, 4.8f), cornerRadius = CornerRadius(1f, 1f))
    drawCircle(color = Color.White, radius = 1.5f, center = Offset(11f, 14.6f))
    drawArc(
        color = Color(0xFF92400E),
        startAngle = -90f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(14.2f, 10.6f),
        size = Size(5f, 5.2f),
        style = Stroke(width = 1.6f, cap = StrokeCap.Round)
    )
}

private fun DrawScope.drawCupcake(p: Path) {
    drawCircle(color = Color(0xFFFDA4AF), radius = 4.6f, center = Offset(12f, 9.4f))
    drawCircle(color = Color(0xFFFBCFE8), radius = 3.2f, center = Offset(8.4f, 10.6f))
    drawCircle(color = Color(0xFFFBCFE8), radius = 3.2f, center = Offset(15.6f, 10.6f))
    drawCircle(color = Color(0xFFE11D48), radius = 1.6f, center = Offset(12f, 4.5f))
    p.reset()
    p.moveTo(6f, 12.6f)
    p.lineTo(18f, 12.6f)
    p.lineTo(16.2f, 20.4f)
    p.lineTo(7.8f, 20.4f)
    p.close()
    drawPath(p, color = Color(0xFFFBBF24))
    drawLine(Color(0xFFD97706), Offset(9.5f, 13.4f), Offset(10f, 19.6f), strokeWidth = 1.2f, cap = StrokeCap.Round)
    drawLine(Color(0xFFD97706), Offset(14.5f, 13.4f), Offset(14f, 19.6f), strokeWidth = 1.2f, cap = StrokeCap.Round)
}

private fun DrawScope.drawFreshApple(p: Path) {
    drawLine(Color(0xFF92400E), Offset(12f, 7f), Offset(12.6f, 3.6f), strokeWidth = 1.5f, cap = StrokeCap.Round)
    p.reset()
    p.moveTo(12.6f, 5.4f)
    p.quadraticTo(17f, 2.5f, 17.5f, 6.2f)
    p.quadraticTo(14.5f, 7.6f, 12.6f, 5.4f)
    p.close()
    drawPath(p, color = Color(0xFF22C55E))
    drawCircle(color = Color(0xFFEF4444), radius = 5.4f, center = Offset(9.8f, 13.2f))
    drawCircle(color = Color(0xFFEF4444), radius = 5.4f, center = Offset(14.2f, 13.2f))
    drawCircle(color = Color(0xFFEF4444), radius = 4.2f, center = Offset(12f, 15.8f))
    drawOval(color = Color(0xFFFCA5A5), topLeft = Offset(7.2f, 10.2f), size = Size(2.2f, 3.6f))
}

private fun DrawScope.drawCookingPot() {
    drawLine(Color(0xFF94A3B8), Offset(9f, 5.2f), Offset(9.4f, 3.2f), strokeWidth = 1.2f, cap = StrokeCap.Round)
    drawLine(Color(0xFF94A3B8), Offset(14.8f, 5f), Offset(15.2f, 3f), strokeWidth = 1.2f, cap = StrokeCap.Round)
    drawCircle(color = Color(0xFFF59E0B), radius = 1.5f, center = Offset(12f, 6.5f))
    drawRoundRect(Color(0xFF14B8A6), topLeft = Offset(4.8f, 7.8f), size = Size(14.4f, 2.5f), cornerRadius = CornerRadius(1.2f, 1.2f))
    drawRoundRect(Color(0xFF0D9488), topLeft = Offset(5.6f, 10.4f), size = Size(12.8f, 9.6f), cornerRadius = CornerRadius(3.5f, 3.5f))
    drawRoundRect(Color(0xFF64748B), topLeft = Offset(3f, 11.4f), size = Size(2.8f, 1.8f), cornerRadius = CornerRadius(0.9f, 0.9f))
    drawRoundRect(Color(0xFF64748B), topLeft = Offset(18.2f, 11.4f), size = Size(2.8f, 1.8f), cornerRadius = CornerRadius(0.9f, 0.9f))
    drawCircle(color = Color(0xFF99F6E4), radius = 1.2f, center = Offset(9f, 14f))
}

private fun DrawScope.drawCocktailDrink(p: Path) {
    drawCircle(color = Color(0xFF22C55E), radius = 2.8f, center = Offset(16.8f, 6.2f))
    drawLine(Color(0xFFEA580C), Offset(12f, 9f), Offset(15.2f, 3.2f), strokeWidth = 1.4f, cap = StrokeCap.Round)
    p.reset()
    p.moveTo(5.2f, 7f)
    p.lineTo(18.8f, 7f)
    p.lineTo(12f, 14.5f)
    p.close()
    drawPath(p, color = Color(0xFFFBBF24))
    drawLine(Color(0xFF60A5FA), Offset(12f, 14.2f), Offset(12f, 19.5f), strokeWidth = 1.8f)
    drawLine(Color(0xFF60A5FA), Offset(8f, 19.8f), Offset(16f, 19.8f), strokeWidth = 1.8f, cap = StrokeCap.Round)
}

private fun DrawScope.drawCartoonCar() {
    drawRoundRect(Color(0xFF38BDF8), topLeft = Offset(5.5f, 6.2f), size = Size(13f, 6.4f), cornerRadius = CornerRadius(3f, 3f))
    drawRoundRect(Color(0xFFE0F2FE), topLeft = Offset(7f, 7.6f), size = Size(10f, 3.6f), cornerRadius = CornerRadius(1.2f, 1.2f))
    drawRoundRect(Color(0xFF0284C7), topLeft = Offset(2.8f, 11f), size = Size(18.4f, 6f), cornerRadius = CornerRadius(2.6f, 2.6f))
    drawCircle(color = Color(0xFFFDE047), radius = 1.4f, center = Offset(5.6f, 13.8f))
    drawCircle(color = Color(0xFFFDE047), radius = 1.4f, center = Offset(18.4f, 13.8f))
    drawCircle(color = Color(0xFF1E293B), radius = 2.2f, center = Offset(6.8f, 17.8f))
    drawCircle(color = Color(0xFF1E293B), radius = 2.2f, center = Offset(17.2f, 17.8f))
    drawCircle(color = Color.White, radius = 0.8f, center = Offset(6.8f, 17.8f))
    drawCircle(color = Color.White, radius = 0.8f, center = Offset(17.2f, 17.8f))
}

private fun DrawScope.drawMetroTrain() {
    drawRoundRect(Color(0xFF10B981), topLeft = Offset(5.2f, 3.6f), size = Size(13.6f, 14.4f), cornerRadius = CornerRadius(4f, 4f))
    drawRoundRect(Color(0xFFBAE6FD), topLeft = Offset(7f, 6f), size = Size(10f, 5.4f), cornerRadius = CornerRadius(1.6f, 1.6f))
    drawCircle(color = Color(0xFFFDE047), radius = 1.3f, center = Offset(8.2f, 14.8f))
    drawCircle(color = Color(0xFFFDE047), radius = 1.3f, center = Offset(15.8f, 14.8f))
    drawLine(Color(0xFF64748B), Offset(6.2f, 20.6f), Offset(8.4f, 18.2f), strokeWidth = 1.6f, cap = StrokeCap.Round)
    drawLine(Color(0xFF64748B), Offset(17.8f, 20.6f), Offset(15.6f, 18.2f), strokeWidth = 1.6f, cap = StrokeCap.Round)
}

private fun DrawScope.drawYellowTaxi() {
    drawRoundRect(Color(0xFF1E293B), topLeft = Offset(9.5f, 4.4f), size = Size(5f, 2f), cornerRadius = CornerRadius(0.8f, 0.8f))
    drawRoundRect(Color(0xFFFACC15), topLeft = Offset(5.5f, 6.2f), size = Size(13f, 6f), cornerRadius = CornerRadius(2.8f, 2.8f))
    drawRoundRect(Color(0xFFFEF9C3), topLeft = Offset(7f, 7.6f), size = Size(10f, 3.2f), cornerRadius = CornerRadius(1f, 1f))
    drawRoundRect(Color(0xFFEAB308), topLeft = Offset(2.8f, 11f), size = Size(18.4f, 6f), cornerRadius = CornerRadius(2.6f, 2.6f))
    drawCircle(color = Color(0xFF1E293B), radius = 2.2f, center = Offset(6.8f, 17.8f))
    drawCircle(color = Color(0xFF1E293B), radius = 2.2f, center = Offset(17.2f, 17.8f))
}

private fun DrawScope.drawBicycle() {
    drawCircle(color = Color(0xFFF97316), radius = 3.8f, center = Offset(6.2f, 16f), style = Stroke(width = 1.7f))
    drawCircle(color = Color(0xFFF97316), radius = 3.8f, center = Offset(17.8f, 16f), style = Stroke(width = 1.7f))
    drawLine(Color(0xFF0EA5E9), Offset(6.2f, 16f), Offset(11.4f, 10.6f), strokeWidth = 1.7f, cap = StrokeCap.Round)
    drawLine(Color(0xFF0EA5E9), Offset(11.4f, 10.6f), Offset(16f, 10.6f), strokeWidth = 1.7f, cap = StrokeCap.Round)
    drawLine(Color(0xFF0EA5E9), Offset(16f, 10.6f), Offset(17.8f, 16f), strokeWidth = 1.7f, cap = StrokeCap.Round)
    drawLine(Color(0xFF1E293B), Offset(14.5f, 8.2f), Offset(17.2f, 8.2f), strokeWidth = 1.5f, cap = StrokeCap.Round)
    drawLine(Color(0xFF1E293B), Offset(8.5f, 8.5f), Offset(11.5f, 8.5f), strokeWidth = 1.6f, cap = StrokeCap.Round)
}

private fun DrawScope.drawHighSpeedTrain(p: Path) {
    p.reset()
    p.moveTo(6f, 5.2f)
    p.cubicTo(10f, 2.6f, 14f, 2.6f, 18f, 5.2f)
    p.lineTo(18.6f, 16f)
    p.cubicTo(18.6f, 18.2f, 16.5f, 18.8f, 12f, 18.8f)
    p.cubicTo(7.5f, 18.8f, 5.4f, 18.2f, 5.4f, 16f)
    p.close()
    drawPath(p, color = Color(0xFF3B82F6))
    drawRoundRect(Color(0xFFE0F2FE), topLeft = Offset(7f, 6.6f), size = Size(10f, 4.6f), cornerRadius = CornerRadius(1.8f, 1.8f))
    drawRoundRect(Color(0xFFFDE047), topLeft = Offset(7.2f, 13.6f), size = Size(9.6f, 2f), cornerRadius = CornerRadius(1f, 1f))
    drawLine(Color(0xFF64748B), Offset(7f, 20.6f), Offset(17f, 20.6f), strokeWidth = 1.6f, cap = StrokeCap.Round)
}

private fun DrawScope.drawGasPump(p: Path) {
    drawRoundRect(Color(0xFFEF4444), topLeft = Offset(5f, 4.2f), size = Size(10.8f, 16f), cornerRadius = CornerRadius(2.4f, 2.4f))
    drawRoundRect(Color(0xFFE0F2FE), topLeft = Offset(6.8f, 6.2f), size = Size(7.2f, 4.4f), cornerRadius = CornerRadius(1f, 1f))
    drawCircle(color = Color(0xFFFDE047), radius = 1.7f, center = Offset(10.4f, 14.5f))
    p.reset()
    p.moveTo(15.8f, 8.2f)
    p.cubicTo(19.5f, 8.2f, 19.5f, 16.5f, 16.2f, 16.5f)
    drawPath(p, color = Color(0xFF334155), style = Stroke(width = 1.6f, cap = StrokeCap.Round))
}

private fun DrawScope.drawParkingSign(p: Path) {
    drawRoundRect(Color(0xFF2563EB), topLeft = Offset(3.5f, 3.5f), size = Size(17f, 17f), cornerRadius = CornerRadius(4.2f, 4.2f))
    p.reset()
    p.moveTo(9.2f, 17f)
    p.lineTo(9.2f, 7.2f)
    p.lineTo(13.2f, 7.2f)
    p.cubicTo(16.2f, 7.2f, 16.2f, 12.2f, 13.2f, 12.2f)
    p.lineTo(9.2f, 12.2f)
    drawPath(p, color = Color.White, style = Stroke(width = 2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

private fun DrawScope.drawTools(p: Path) {
    rotate(-40f, pivot = Offset(12f, 12f)) {
        drawRoundRect(Color(0xFF60A5FA), topLeft = Offset(10.5f, 3.5f), size = Size(3f, 17f), cornerRadius = CornerRadius(1.5f, 1.5f))
    }
    rotate(40f, pivot = Offset(12f, 12f)) {
        drawRoundRect(Color(0xFFF97316), topLeft = Offset(10.5f, 12f), size = Size(3f, 8.5f), cornerRadius = CornerRadius(1.5f, 1.5f))
        drawLine(Color(0xFF94A3B8), Offset(12f, 3.5f), Offset(12f, 12.2f), strokeWidth = 2f, cap = StrokeCap.Round)
    }
    p.reset()
}

private fun DrawScope.drawShoppingBag() {
    drawArc(
        color = Color(0xFFF59E0B),
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(8.2f, 3.2f),
        size = Size(7.6f, 6.2f),
        style = Stroke(width = 1.8f, cap = StrokeCap.Round)
    )
    drawRoundRect(Color(0xFFEC4899), topLeft = Offset(4.8f, 7f), size = Size(14.4f, 13.4f), cornerRadius = CornerRadius(2.8f, 2.8f))
    drawArc(
        color = Color.White,
        startAngle = 15f,
        sweepAngle = 150f,
        useCenter = false,
        topLeft = Offset(8.6f, 10.6f),
        size = Size(6.8f, 4.4f),
        style = Stroke(width = 1.6f, cap = StrokeCap.Round)
    )
}

private fun DrawScope.drawShoppingCart(p: Path) {
    drawRoundRect(Color(0xFFFBBF24), topLeft = Offset(8.5f, 5.2f), size = Size(4.8f, 4.8f), cornerRadius = CornerRadius(1f, 1f))
    drawRoundRect(Color(0xFFFB7185), topLeft = Offset(13.2f, 6.4f), size = Size(4.2f, 4f), cornerRadius = CornerRadius(1f, 1f))
    p.reset()
    p.moveTo(3.6f, 6f)
    p.lineTo(6.2f, 6f)
    p.lineTo(8.2f, 15.2f)
    p.lineTo(18.6f, 15.2f)
    p.lineTo(20f, 9f)
    p.lineTo(7f, 9f)
    drawPath(p, color = Color(0xFF0EA5E9), style = Stroke(width = 1.8f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    drawCircle(color = Color(0xFF1E293B), radius = 1.7f, center = Offset(9.2f, 18.8f))
    drawCircle(color = Color(0xFF1E293B), radius = 1.7f, center = Offset(17.4f, 18.8f))
}

private fun DrawScope.drawShopFront() {
    drawRoundRect(Color(0xFFFEF3C7), topLeft = Offset(4.8f, 9.5f), size = Size(14.4f, 10.6f), cornerRadius = CornerRadius(2f, 2f))
    drawRoundRect(Color(0xFF38BDF8), topLeft = Offset(9.2f, 13f), size = Size(5.6f, 7.1f), cornerRadius = CornerRadius(1.2f, 1.2f))
    drawRoundRect(Color(0xFFEF4444), topLeft = Offset(3.5f, 4.5f), size = Size(17f, 5.6f), cornerRadius = CornerRadius(2f, 2f))
    drawCircle(color = Color.White, radius = 1.4f, center = Offset(7.2f, 7.3f))
    drawCircle(color = Color.White, radius = 1.4f, center = Offset(12f, 7.3f))
    drawCircle(color = Color.White, radius = 1.4f, center = Offset(16.8f, 7.3f))
}

private fun DrawScope.drawParcelBox(p: Path) {
    drawRoundRect(Color(0xFFD97706), topLeft = Offset(4f, 5.2f), size = Size(16f, 14.4f), cornerRadius = CornerRadius(2.6f, 2.6f))
    drawRoundRect(Color(0xFFF59E0B), topLeft = Offset(4f, 5.2f), size = Size(16f, 5.2f), cornerRadius = CornerRadius(2.2f, 2.2f))
    drawRect(Color(0xFFFEF3C7), topLeft = Offset(10.6f, 5.2f), size = Size(2.8f, 7.2f))
    drawRoundRect(Color.White, topLeft = Offset(13.2f, 13.6f), size = Size(4.6f, 3.2f), cornerRadius = CornerRadius(0.6f, 0.6f))
    p.reset()
}

private fun DrawScope.drawTShirt(p: Path) {
    p.reset()
    p.moveTo(7.2f, 4.5f)
    p.lineTo(16.8f, 4.5f)
    p.lineTo(20.8f, 8.6f)
    p.lineTo(18.2f, 11.4f)
    p.lineTo(16.8f, 9.8f)
    p.lineTo(16.8f, 19.8f)
    p.lineTo(7.2f, 19.8f)
    p.lineTo(7.2f, 9.8f)
    p.lineTo(5.8f, 11.4f)
    p.lineTo(3.2f, 8.6f)
    p.close()
    drawPath(p, color = Color(0xFF38BDF8))
    drawArc(Color.White, startAngle = 0f, sweepAngle = 180f, useCenter = true, topLeft = Offset(9.4f, 3.2f), size = Size(5.2f, 3.4f))
    drawRoundRect(Color(0xFFFDE047), topLeft = Offset(12.2f, 10.6f), size = Size(3f, 2.8f), cornerRadius = CornerRadius(0.6f, 0.6f))
}

private fun DrawScope.drawLipstick(p: Path) {
    p.reset()
    p.moveTo(9.8f, 9.6f)
    p.lineTo(9.8f, 4.6f)
    p.lineTo(14.2f, 3.2f)
    p.lineTo(14.2f, 9.6f)
    p.close()
    drawPath(p, color = Color(0xFFF43F5E))
    drawRoundRect(Color(0xFFFBBF24), topLeft = Offset(8.8f, 9.5f), size = Size(6.4f, 4f), cornerRadius = CornerRadius(1f, 1f))
    drawRoundRect(Color(0xFF1E293B), topLeft = Offset(8.2f, 13.5f), size = Size(7.6f, 7.2f), cornerRadius = CornerRadius(1.5f, 1.5f))
}

private fun DrawScope.drawScissors(p: Path) {
    drawLine(Color(0xFF64748B), Offset(16.5f, 4.5f), Offset(7.2f, 15.8f), strokeWidth = 2f, cap = StrokeCap.Round)
    drawLine(Color(0xFF94A3B8), Offset(7.5f, 4.5f), Offset(16.8f, 15.8f), strokeWidth = 2f, cap = StrokeCap.Round)
    drawCircle(color = Color(0xFFEC4899), radius = 3f, center = Offset(7.4f, 17.6f), style = Stroke(width = 2f))
    drawCircle(color = Color(0xFFEC4899), radius = 3f, center = Offset(16.6f, 17.6f), style = Stroke(width = 2f))
    p.reset()
}

private fun DrawScope.drawDiamond(p: Path) {
    p.reset()
    p.moveTo(6.8f, 5f)
    p.lineTo(17.2f, 5f)
    p.lineTo(21f, 10f)
    p.lineTo(12f, 20.2f)
    p.lineTo(3f, 10f)
    p.close()
    drawPath(p, color = Color(0xFF38BDF8))
    drawLine(Color(0xFFBAE6FD), Offset(3.5f, 10f), Offset(20.5f, 10f), strokeWidth = 1.2f)
    drawLine(Color(0xFFBAE6FD), Offset(8.5f, 5.2f), Offset(12f, 19.6f), strokeWidth = 1f)
    drawLine(Color(0xFFBAE6FD), Offset(15.5f, 5.2f), Offset(12f, 19.6f), strokeWidth = 1f)
}

private fun DrawScope.drawCozyHouse(p: Path) {
    drawRoundRect(Color(0xFFFEF3C7), topLeft = Offset(5.2f, 10.6f), size = Size(13.6f, 9.8f), cornerRadius = CornerRadius(2f, 2f))
    p.reset()
    p.moveTo(3.2f, 11.4f)
    p.lineTo(12f, 3.8f)
    p.lineTo(20.8f, 11.4f)
    p.close()
    drawPath(p, color = Color(0xFF2AA876))
    drawRoundRect(Color(0xFFF59E0B), topLeft = Offset(9.4f, 13.6f), size = Size(5.2f, 6.8f), cornerRadius = CornerRadius(1.5f, 1.5f))
    drawCircle(color = Color.White, radius = 1.2f, center = Offset(12f, 16.5f))
}

private fun DrawScope.drawElectricBolt(p: Path) {
    drawCircle(color = Color(0xFFFDE047), radius = 9.5f, center = Offset(12f, 12f))
    drawCircle(color = Color(0xFF1E293B), radius = 9.5f, center = Offset(12f, 12f), style = Stroke(width = 1.3f))
    p.reset()
    p.moveTo(13.2f, 5.5f)
    p.lineTo(7.8f, 12.8f)
    p.lineTo(11.6f, 12.8f)
    p.lineTo(10.6f, 18.6f)
    p.lineTo(16.2f, 11.2f)
    p.lineTo(12.4f, 11.2f)
    p.close()
    drawPath(p, color = Color(0xFFEA580C))
}

private fun DrawScope.drawWaterDrop(p: Path) {
    p.reset()
    p.moveTo(12f, 3.2f)
    p.cubicTo(16.8f, 9.2f, 18.8f, 12.6f, 18.8f, 15.2f)
    p.cubicTo(18.8f, 19f, 15.8f, 21f, 12f, 21f)
    p.cubicTo(8.2f, 21f, 5.2f, 19f, 5.2f, 15.2f)
    p.cubicTo(5.2f, 12.6f, 7.2f, 9.2f, 12f, 3.2f)
    p.close()
    drawPath(p, color = Color(0xFF38BDF8))
    drawOval(color = Color.White.copy(alpha = 0.78f), topLeft = Offset(7.6f, 13.5f), size = Size(2f, 4f))
}

private fun DrawScope.drawGasFlame(p: Path) {
    p.reset()
    p.moveTo(12.5f, 3f)
    p.cubicTo(17.2f, 7.2f, 18.5f, 11.4f, 18.5f, 14.6f)
    p.cubicTo(18.5f, 18.5f, 15.6f, 21f, 12f, 21f)
    p.cubicTo(8.4f, 21f, 5.5f, 18.5f, 5.5f, 14.6f)
    p.cubicTo(5.5f, 11.6f, 7.8f, 8.8f, 9.6f, 6.4f)
    p.cubicTo(10f, 8.8f, 11.6f, 9.4f, 12.5f, 3f)
    p.close()
    drawPath(p, color = Color(0xFFF97316))
    drawCircle(color = Color(0xFFFDE047), radius = 3.2f, center = Offset(12f, 16.5f))
}

private fun DrawScope.drawMobileCard() {
    drawRoundRect(Color(0xFFFEF3C7), topLeft = Offset(6.2f, 3.5f), size = Size(11.6f, 17f), cornerRadius = CornerRadius(2.4f, 2.4f))
    drawRoundRect(Color(0xFF64748B), topLeft = Offset(6.2f, 3.5f), size = Size(11.6f, 17f), cornerRadius = CornerRadius(2.4f, 2.4f), style = Stroke(width = 1.2f))
    drawArc(Color(0xFF0EA5E9), startAngle = 225f, sweepAngle = 90f, useCenter = false, topLeft = Offset(7.5f, 6.8f), size = Size(9f, 9f), style = Stroke(width = 1.6f, cap = StrokeCap.Round))
    drawArc(Color(0xFF0EA5E9), startAngle = 225f, sweepAngle = 90f, useCenter = false, topLeft = Offset(9.4f, 9.2f), size = Size(5.2f, 5.2f), style = Stroke(width = 1.5f, cap = StrokeCap.Round))
    drawCircle(color = Color(0xFFF59E0B), radius = 1.2f, center = Offset(12f, 14.2f))
}

private fun DrawScope.drawCleaningSparkle(p: Path) {
    drawCircle(color = Color(0xFFBAE6FD), radius = 7.8f, center = Offset(12f, 13f))
    p.reset()
    p.moveTo(12f, 4.5f)
    p.lineTo(13.6f, 10.4f)
    p.lineTo(19.5f, 12f)
    p.lineTo(13.6f, 13.6f)
    p.lineTo(12f, 19.5f)
    p.lineTo(10.4f, 13.6f)
    p.lineTo(4.5f, 12f)
    p.lineTo(10.4f, 10.4f)
    p.close()
    drawPath(p, color = Color(0xFF0284C7))
}

private fun DrawScope.drawLaptop() {
    drawRoundRect(Color(0xFF334155), topLeft = Offset(4.5f, 5.2f), size = Size(15f, 10.4f), cornerRadius = CornerRadius(1.8f, 1.8f))
    drawRoundRect(Color(0xFF38BDF8), topLeft = Offset(5.9f, 6.6f), size = Size(12.2f, 7.6f), cornerRadius = CornerRadius(0.8f, 0.8f))
    drawRoundRect(Color(0xFF94A3B8), topLeft = Offset(2.5f, 16.2f), size = Size(19f, 2.6f), cornerRadius = CornerRadius(1.3f, 1.3f))
}

private fun DrawScope.drawHeadphones() {
    drawArc(
        color = Color(0xFF6366F1),
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(5f, 4.8f),
        size = Size(14f, 13f),
        style = Stroke(width = 2.4f, cap = StrokeCap.Round)
    )
    drawRoundRect(Color(0xFFEC4899), topLeft = Offset(3.5f, 11.4f), size = Size(3.6f, 6.8f), cornerRadius = CornerRadius(1.6f, 1.6f))
    drawRoundRect(Color(0xFFEC4899), topLeft = Offset(16.9f, 11.4f), size = Size(3.6f, 6.8f), cornerRadius = CornerRadius(1.6f, 1.6f))
}

private fun DrawScope.drawRetroTv() {
    drawLine(Color(0xFF64748B), Offset(10f, 6.8f), Offset(7.2f, 3.6f), strokeWidth = 1.5f, cap = StrokeCap.Round)
    drawLine(Color(0xFF64748B), Offset(14f, 6.8f), Offset(16.8f, 3.6f), strokeWidth = 1.5f, cap = StrokeCap.Round)
    drawRoundRect(Color(0xFF7C3AED), topLeft = Offset(3.5f, 6.8f), size = Size(17f, 13f), cornerRadius = CornerRadius(3f, 3f))
    drawRoundRect(Color(0xFF99F6E4), topLeft = Offset(5.4f, 8.8f), size = Size(10.5f, 9f), cornerRadius = CornerRadius(1.6f, 1.6f))
    drawCircle(color = Color(0xFFFDE047), radius = 1.1f, center = Offset(18.2f, 11.2f))
    drawCircle(color = Color(0xFFFB7185), radius = 1.1f, center = Offset(18.2f, 15.2f))
}

private fun DrawScope.drawCloudSub(p: Path) {
    drawCircle(color = Color(0xFF60A5FA), radius = 5f, center = Offset(9.5f, 12.2f))
    drawCircle(color = Color(0xFF60A5FA), radius = 4.2f, center = Offset(14.8f, 12.8f))
    drawCircle(color = Color(0xFF60A5FA), radius = 3.8f, center = Offset(12.2f, 9.6f))
    drawRoundRect(Color(0xFF60A5FA), topLeft = Offset(5.2f, 12.2f), size = Size(13.6f, 4.8f), cornerRadius = CornerRadius(2.4f, 2.4f))
    drawCircle(color = Color(0xFFFDE047), radius = 2.6f, center = Offset(17.5f, 7.6f))
    p.reset()
}

private fun DrawScope.drawAiBot() {
    drawLine(Color(0xFFF59E0B), Offset(12f, 6.5f), Offset(12f, 3.8f), strokeWidth = 1.6f, cap = StrokeCap.Round)
    drawCircle(color = Color(0xFFFDE047), radius = 1.5f, center = Offset(12f, 3.6f))
    drawRoundRect(Color(0xFF6366F1), topLeft = Offset(4.5f, 6.5f), size = Size(15f, 12.4f), cornerRadius = CornerRadius(4f, 4f))
    drawRoundRect(Color(0xFFE0E7FF), topLeft = Offset(6.5f, 9f), size = Size(11f, 6f), cornerRadius = CornerRadius(2f, 2f))
    drawCircle(color = Color(0xFF4F46E5), radius = 1.2f, center = Offset(9.5f, 12f))
    drawCircle(color = Color(0xFF4F46E5), radius = 1.2f, center = Offset(14.5f, 12f))
}

private fun DrawScope.drawMusicNote() {
    drawLine(Color(0xFFEC4899), Offset(9.2f, 16.5f), Offset(9.2f, 6f), strokeWidth = 2f, cap = StrokeCap.Round)
    drawLine(Color(0xFFEC4899), Offset(17.2f, 14.5f), Offset(17.2f, 4.5f), strokeWidth = 2f, cap = StrokeCap.Round)
    drawLine(Color(0xFFF43F5E), Offset(9.2f, 6f), Offset(17.2f, 4.5f), strokeWidth = 3f, cap = StrokeCap.Round)
    drawOval(color = Color(0xFFF43F5E), topLeft = Offset(5.2f, 14.4f), size = Size(5.2f, 4f))
    drawOval(color = Color(0xFFF43F5E), topLeft = Offset(13.2f, 12.6f), size = Size(5.2f, 4f))
}

private fun DrawScope.drawGamepad() {
    drawRoundRect(Color(0xFF7C3AED), topLeft = Offset(3f, 7.5f), size = Size(18f, 10f), cornerRadius = CornerRadius(5f, 5f))
    drawLine(Color.White, Offset(6.4f, 12.5f), Offset(10f, 12.5f), strokeWidth = 1.6f, cap = StrokeCap.Round)
    drawLine(Color.White, Offset(8.2f, 10.7f), Offset(8.2f, 14.3f), strokeWidth = 1.6f, cap = StrokeCap.Round)
    drawCircle(color = Color(0xFFFDE047), radius = 1.1f, center = Offset(15.2f, 11.2f))
    drawCircle(color = Color(0xFF4ADE80), radius = 1.1f, center = Offset(17.2f, 13.4f))
}

private fun DrawScope.drawMoviePopcorn(p: Path) {
    drawCircle(color = Color(0xFFFEF3C7), radius = 2.4f, center = Offset(9f, 7f))
    drawCircle(color = Color(0xFFFDE68A), radius = 2.6f, center = Offset(12f, 6f))
    drawCircle(color = Color(0xFFFEF3C7), radius = 2.4f, center = Offset(15f, 7.2f))
    p.reset()
    p.moveTo(6.2f, 9.2f)
    p.lineTo(17.8f, 9.2f)
    p.lineTo(16f, 20.4f)
    p.lineTo(8f, 20.4f)
    p.close()
    drawPath(p, color = Color(0xFFEF4444))
    drawLine(Color.White, Offset(10.2f, 9.4f), Offset(10.6f, 20f), strokeWidth = 1.8f)
    drawLine(Color.White, Offset(13.8f, 9.4f), Offset(13.4f, 20f), strokeWidth = 1.8f)
}

private fun DrawScope.drawPartyPopper(p: Path) {
    p.reset()
    p.moveTo(4.5f, 19.5f)
    p.lineTo(9f, 8.8f)
    p.lineTo(15.2f, 15f)
    p.close()
    drawPath(p, color = Color(0xFFF59E0B))
    drawCircle(color = Color(0xFFEC4899), radius = 1.5f, center = Offset(13.5f, 5.5f))
    drawCircle(color = Color(0xFF38BDF8), radius = 1.4f, center = Offset(18.5f, 8.5f))
    drawCircle(color = Color(0xFF22C55E), radius = 1.3f, center = Offset(17.6f, 13.5f))
}

private fun DrawScope.drawDumbbell() {
    drawLine(Color(0xFF64748B), Offset(4f, 12f), Offset(20f, 12f), strokeWidth = 2.6f, cap = StrokeCap.Round)
    drawRoundRect(Color(0xFF0EA5E9), topLeft = Offset(4.5f, 7.2f), size = Size(3.2f, 9.6f), cornerRadius = CornerRadius(1.2f, 1.2f))
    drawRoundRect(Color(0xFF0EA5E9), topLeft = Offset(16.3f, 7.2f), size = Size(3.2f, 9.6f), cornerRadius = CornerRadius(1.2f, 1.2f))
}

private fun DrawScope.drawPineTree(p: Path) {
    drawRoundRect(Color(0xFF92400E), topLeft = Offset(10.6f, 16.5f), size = Size(2.8f, 4.4f), cornerRadius = CornerRadius(0.8f, 0.8f))
    p.reset()
    p.moveTo(12f, 3.6f)
    p.lineTo(18.6f, 12.2f)
    p.lineTo(5.4f, 12.2f)
    p.close()
    drawPath(p, color = Color(0xFF22C55E))
    p.reset()
    p.moveTo(12f, 8.4f)
    p.lineTo(19.6f, 17f)
    p.lineTo(4.4f, 17f)
    p.close()
    drawPath(p, color = Color(0xFF16A34A))
}

private fun DrawScope.drawMedicalKit() {
    drawRoundRect(
        color = Color(0xFFF59E0B),
        topLeft = Offset(8.2f, 4.4f),
        size = Size(7.6f, 3.6f),
        cornerRadius = CornerRadius(1.6f, 1.6f),
        style = Stroke(width = 1.8f)
    )
    drawRoundRect(Color(0xFFFBBF24), topLeft = Offset(3.5f, 7.2f), size = Size(17f, 12.6f), cornerRadius = CornerRadius(3f, 3f))
    drawRoundRect(Color(0xFFEF4444), topLeft = Offset(10.6f, 10.2f), size = Size(2.8f, 6.8f), cornerRadius = CornerRadius(0.8f, 0.8f))
    drawRoundRect(Color(0xFFEF4444), topLeft = Offset(8.6f, 12.2f), size = Size(6.8f, 2.8f), cornerRadius = CornerRadius(0.8f, 0.8f))
}

private fun DrawScope.drawHospital(p: Path) {
    drawRoundRect(Color(0xFFE0F2FE), topLeft = Offset(4.5f, 8.8f), size = Size(15f, 11.4f), cornerRadius = CornerRadius(2f, 2f))
    p.reset()
    p.moveTo(3.2f, 9.6f)
    p.lineTo(12f, 4.2f)
    p.lineTo(20.8f, 9.6f)
    p.close()
    drawPath(p, color = Color(0xFFEF4444))
    drawRoundRect(Color(0xFFEF4444), topLeft = Offset(11f, 11.4f), size = Size(2f, 5.2f), cornerRadius = CornerRadius(0.6f, 0.6f))
    drawRoundRect(Color(0xFFEF4444), topLeft = Offset(9.4f, 13f), size = Size(5.2f, 2f), cornerRadius = CornerRadius(0.6f, 0.6f))
}

private fun DrawScope.drawMedicinePill() {
    rotate(-45f, pivot = Offset(12f, 12f)) {
        drawRoundRect(Color(0xFFF472B6), topLeft = Offset(5.2f, 8.6f), size = Size(7.4f, 6.8f), cornerRadius = CornerRadius(3.4f, 3.4f))
        drawRoundRect(Color(0xFFE2E8F0), topLeft = Offset(11.4f, 8.6f), size = Size(7.4f, 6.8f), cornerRadius = CornerRadius(3.4f, 3.4f))
        drawOval(Color.White.copy(alpha = 0.72f), topLeft = Offset(7f, 9.6f), size = Size(2.4f, 1.2f))
    }
}

private fun DrawScope.drawHealthShield(p: Path) {
    p.reset()
    p.moveTo(12f, 3.5f)
    p.lineTo(19f, 6.5f)
    p.lineTo(19f, 12.2f)
    p.cubicTo(19f, 16.6f, 15.6f, 19.6f, 12f, 20.8f)
    p.cubicTo(8.4f, 19.6f, 5f, 16.6f, 5f, 12.2f)
    p.lineTo(5f, 6.5f)
    p.close()
    drawPath(p, color = Color(0xFF10B981))
    drawRoundRect(Color.White, topLeft = Offset(10.8f, 8.8f), size = Size(2.4f, 6.4f), cornerRadius = CornerRadius(0.7f, 0.7f))
    drawRoundRect(Color.White, topLeft = Offset(8.8f, 10.8f), size = Size(6.4f, 2.4f), cornerRadius = CornerRadius(0.7f, 0.7f))
}

private fun DrawScope.drawAirplane(p: Path) {
    p.reset()
    p.moveTo(12f, 3.5f)
    p.lineTo(14f, 9.5f)
    p.lineTo(20.5f, 13.5f)
    p.lineTo(20.5f, 15.6f)
    p.lineTo(14f, 13.8f)
    p.lineTo(13.6f, 18.2f)
    p.lineTo(16.2f, 20f)
    p.lineTo(16.2f, 21f)
    p.lineTo(12f, 19.8f)
    p.lineTo(7.8f, 21f)
    p.lineTo(7.8f, 20f)
    p.lineTo(10.4f, 18.2f)
    p.lineTo(10f, 13.8f)
    p.lineTo(3.5f, 15.6f)
    p.lineTo(3.5f, 13.5f)
    p.lineTo(10f, 9.5f)
    p.close()
    drawPath(p, color = Color(0xFF38BDF8))
}

private fun DrawScope.drawHotelBuilding() {
    drawRoundRect(Color(0xFF6366F1), topLeft = Offset(5.2f, 4.2f), size = Size(13.6f, 16f), cornerRadius = CornerRadius(2.4f, 2.4f))
    for (row in 0..2) {
        val y = 6.4f + row * 3.6f
        drawRoundRect(Color(0xFFFDE047), topLeft = Offset(7.4f, y), size = Size(2.4f, 2f), cornerRadius = CornerRadius(0.5f, 0.5f))
        drawRoundRect(Color(0xFFFDE047), topLeft = Offset(11f, y), size = Size(2.4f, 2f), cornerRadius = CornerRadius(0.5f, 0.5f))
        drawRoundRect(Color(0xFFFDE047), topLeft = Offset(14.4f, y), size = Size(2.4f, 2f), cornerRadius = CornerRadius(0.5f, 0.5f))
    }
    drawRoundRect(Color(0xFFF43F5E), topLeft = Offset(10f, 16.8f), size = Size(4f, 3.4f), cornerRadius = CornerRadius(1f, 1f))
}

private fun DrawScope.drawLuggage() {
    drawRoundRect(
        color = Color(0xFF64748B),
        topLeft = Offset(9.2f, 3.6f),
        size = Size(5.6f, 4f),
        cornerRadius = CornerRadius(1.4f, 1.4f),
        style = Stroke(width = 1.6f)
    )
    drawRoundRect(Color(0xFFFB7185), topLeft = Offset(5.8f, 6.8f), size = Size(12.4f, 12.6f), cornerRadius = CornerRadius(2.6f, 2.6f))
    drawRoundRect(Color(0xFFFDE047), topLeft = Offset(8.5f, 10f), size = Size(7f, 2.2f), cornerRadius = CornerRadius(1f, 1f))
    drawCircle(color = Color(0xFF1E293B), radius = 1.3f, center = Offset(8.2f, 20.2f))
    drawCircle(color = Color(0xFF1E293B), radius = 1.3f, center = Offset(15.8f, 20.2f))
}

private fun DrawScope.drawGradCap(p: Path) {
    p.reset()
    p.moveTo(12f, 4.5f)
    p.lineTo(21f, 9.2f)
    p.lineTo(12f, 14f)
    p.lineTo(3f, 9.2f)
    p.close()
    drawPath(p, color = Color(0xFF0D9488))
    drawRoundRect(Color(0xFF14B8A6), topLeft = Offset(7f, 12f), size = Size(10f, 5.2f), cornerRadius = CornerRadius(1.8f, 1.8f))
    drawLine(Color(0xFFF59E0B), Offset(18.8f, 10.2f), Offset(18.8f, 17f), strokeWidth = 1.5f, cap = StrokeCap.Round)
    drawCircle(color = Color(0xFFEF4444), radius = 1.3f, center = Offset(18.8f, 17.5f))
}

private fun DrawScope.drawBookStack() {
    drawRoundRect(Color(0xFFEC4899), topLeft = Offset(5.2f, 5f), size = Size(13f, 4.4f), cornerRadius = CornerRadius(1.4f, 1.4f))
    drawRoundRect(Color(0xFF38BDF8), topLeft = Offset(6f, 10f), size = Size(13.2f, 4.4f), cornerRadius = CornerRadius(1.4f, 1.4f))
    drawRoundRect(Color(0xFF8B5CF6), topLeft = Offset(4.8f, 15f), size = Size(13.8f, 4.6f), cornerRadius = CornerRadius(1.4f, 1.4f))
    drawLine(Color.White, Offset(7.2f, 7.2f), Offset(15.5f, 7.2f), strokeWidth = 1.2f, cap = StrokeCap.Round)
    drawLine(Color.White, Offset(8f, 12.2f), Offset(16.5f, 12.2f), strokeWidth = 1.2f, cap = StrokeCap.Round)
    drawLine(Color.White, Offset(6.8f, 17.3f), Offset(15.8f, 17.3f), strokeWidth = 1.2f, cap = StrokeCap.Round)
}

private fun DrawScope.drawExamBanner(p: Path) {
    p.reset()
    p.moveTo(5.5f, 4f)
    p.lineTo(18.5f, 4f)
    p.lineTo(18.5f, 19.5f)
    p.lineTo(12f, 16.5f)
    p.lineTo(5.5f, 19.5f)
    p.close()
    drawPath(p, color = Color(0xFF22C55E))
    drawCircle(color = Color.White, radius = 4f, center = Offset(12f, 10.2f))
    drawLine(Color(0xFF16A34A), Offset(9.8f, 10.4f), Offset(11.4f, 12f), strokeWidth = 1.6f, cap = StrokeCap.Round)
    drawLine(Color(0xFF16A34A), Offset(11.4f, 12f), Offset(14.5f, 8.6f), strokeWidth = 1.6f, cap = StrokeCap.Round)
}

private fun DrawScope.drawCuteHeart(p: Path, mainColor: Color, shineColor: Color) {
    p.reset()
    p.moveTo(12f, 20f)
    p.cubicTo(4f, 14.5f, 3.4f, 7.5f, 7.6f, 5.2f)
    p.cubicTo(10f, 3.8f, 12f, 6.2f, 12f, 7.4f)
    p.cubicTo(12f, 6.2f, 14f, 3.8f, 16.4f, 5.2f)
    p.cubicTo(20.6f, 7.5f, 20f, 14.5f, 12f, 20f)
    p.close()
    drawPath(p, color = mainColor)
    drawCircle(color = shineColor, radius = 1.3f, center = Offset(8.2f, 8.5f))
}

private fun DrawScope.drawCareHeart(p: Path) {
    drawCuteHeart(p, Color(0xFFEF4444), Color(0xFFFDA4AF))
    drawRoundRect(Color(0xFFFDBA74), topLeft = Offset(4.5f, 17.6f), size = Size(15f, 3.2f), cornerRadius = CornerRadius(1.6f, 1.6f))
}

private fun DrawScope.drawRedEnvelope() {
    drawRoundRect(Color(0xFFDC2626), topLeft = Offset(5.2f, 3.5f), size = Size(13.6f, 17f), cornerRadius = CornerRadius(2.6f, 2.6f))
    drawArc(
        color = Color(0xFFB91C1C),
        startAngle = 0f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(5.2f, 4.5f),
        size = Size(13.6f, 7f),
        style = Stroke(width = 1.4f)
    )
    drawCircle(color = Color(0xFFFDE047), radius = 3.2f, center = Offset(12f, 11.4f))
    drawCircle(color = Color(0xFFDC2626), radius = 1.2f, center = Offset(12f, 11.4f))
}

private fun DrawScope.drawGiftBox(p: Path) {
    drawRoundRect(Color(0xFFEF4444), topLeft = Offset(4.8f, 9.2f), size = Size(14.4f, 11f), cornerRadius = CornerRadius(2f, 2f))
    drawRoundRect(Color(0xFFDC2626), topLeft = Offset(3.8f, 6.8f), size = Size(16.4f, 3.6f), cornerRadius = CornerRadius(1.4f, 1.4f))
    drawRect(Color(0xFFFDE047), topLeft = Offset(10.6f, 6.8f), size = Size(2.8f, 13.4f))
    drawCircle(color = Color(0xFFFACC15), radius = 2.1f, center = Offset(9.5f, 5f))
    drawCircle(color = Color(0xFFFACC15), radius = 2.1f, center = Offset(14.5f, 5f))
    p.reset()
}

private fun DrawScope.drawSocialCare(p: Path) {
    drawCircle(color = Color(0xFF38BDF8), radius = 3.2f, center = Offset(8.5f, 7.6f))
    drawRoundRect(Color(0xFF0284C7), topLeft = Offset(4.4f, 11.8f), size = Size(8.2f, 7.6f), cornerRadius = CornerRadius(3.5f, 3.5f))
    drawCircle(color = Color(0xFFFBBF24), radius = 2.8f, center = Offset(16f, 8.6f))
    drawRoundRect(Color(0xFFF59E0B), topLeft = Offset(12.2f, 12.6f), size = Size(7.4f, 6.8f), cornerRadius = CornerRadius(3f, 3f))
    p.reset()
}

private fun DrawScope.drawPetPaw() {
    drawOval(color = Color(0xFFF59E0B), topLeft = Offset(7.2f, 11.4f), size = Size(9.6f, 8.2f))
    drawCircle(color = Color(0xFFFB923C), radius = 2.1f, center = Offset(5.6f, 9.4f))
    drawCircle(color = Color(0xFFFB923C), radius = 2.1f, center = Offset(9.8f, 6.4f))
    drawCircle(color = Color(0xFFFB923C), radius = 2.1f, center = Offset(14.4f, 6.4f))
    drawCircle(color = Color(0xFFFB923C), radius = 2.1f, center = Offset(18.4f, 9.4f))
}

private fun DrawScope.drawBabyBottle() {
    drawRoundRect(Color(0xFFFBBF24), topLeft = Offset(10.4f, 3.2f), size = Size(3.2f, 3.2f), cornerRadius = CornerRadius(1.6f, 1.6f))
    drawRoundRect(Color(0xFF38BDF8), topLeft = Offset(7.8f, 6f), size = Size(8.4f, 2.8f), cornerRadius = CornerRadius(1.2f, 1.2f))
    drawRoundRect(Color(0xFFE0F2FE), topLeft = Offset(7.2f, 8.8f), size = Size(9.6f, 11.4f), cornerRadius = CornerRadius(2.8f, 2.8f))
    drawLine(Color(0xFF0EA5E9), Offset(10.2f, 12f), Offset(13.2f, 12f), strokeWidth = 1.3f, cap = StrokeCap.Round)
    drawLine(Color(0xFF0EA5E9), Offset(10.2f, 15.2f), Offset(13.2f, 15.2f), strokeWidth = 1.3f, cap = StrokeCap.Round)
}

private fun DrawScope.drawExchangeCoins() {
    drawCircle(color = Color(0xFFFBBF24), radius = 5.4f, center = Offset(14.8f, 14.2f))
    drawCircle(color = Color(0xFFFDE047), radius = 3.8f, center = Offset(14.8f, 14.2f), style = Stroke(width = 1.2f))
    drawCircle(color = Color(0xFF38BDF8), radius = 5.4f, center = Offset(9.2f, 9.8f))
    drawCircle(color = Color.White, radius = 3.6f, center = Offset(9.2f, 9.8f), style = Stroke(width = 1.2f))
}

private fun DrawScope.drawPercentCoin() {
    drawCircle(color = Color(0xFF8B5CF6), radius = 9.2f, center = Offset(12f, 12f))
    drawLine(Color.White, Offset(8.5f, 15.5f), Offset(15.5f, 8.5f), strokeWidth = 1.8f, cap = StrokeCap.Round)
    drawCircle(color = Color(0xFFFDE047), radius = 1.8f, center = Offset(9f, 9f))
    drawCircle(color = Color(0xFFFDE047), radius = 1.8f, center = Offset(15f, 15f))
}

private fun DrawScope.drawTrendUp(p: Path) {
    drawRoundRect(Color(0xFFDCFCE7), topLeft = Offset(3.5f, 4f), size = Size(17f, 16f), cornerRadius = CornerRadius(3.5f, 3.5f))
    p.reset()
    p.moveTo(6.2f, 15.8f)
    p.lineTo(10.5f, 11.4f)
    p.lineTo(13.6f, 13.8f)
    p.lineTo(17.8f, 8.2f)
    drawPath(p, color = Color(0xFF16A34A), style = Stroke(width = 2.1f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    drawCircle(color = Color(0xFFF59E0B), radius = 1.6f, center = Offset(17.8f, 8.2f))
}

private fun DrawScope.drawTrendDown(p: Path) {
    drawRoundRect(Color(0xFFFEE2E2), topLeft = Offset(3.5f, 4f), size = Size(17f, 16f), cornerRadius = CornerRadius(3.5f, 3.5f))
    p.reset()
    p.moveTo(6.2f, 8.4f)
    p.lineTo(10.5f, 12.6f)
    p.lineTo(13.6f, 10.2f)
    p.lineTo(17.8f, 15.8f)
    drawPath(p, color = Color(0xFFEF4444), style = Stroke(width = 2.1f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    drawCircle(color = Color(0xFFEF4444), radius = 1.5f, center = Offset(17.8f, 15.8f))
}

private fun DrawScope.drawFourGrid() {
    drawRoundRect(Color(0xFF38BDF8), topLeft = Offset(4.4f, 4.4f), size = Size(6.4f, 6.4f), cornerRadius = CornerRadius(2f, 2f), style = Stroke(width = 1.8f))
    drawRoundRect(Color(0xFF38BDF8), topLeft = Offset(13.2f, 4.4f), size = Size(6.4f, 6.4f), cornerRadius = CornerRadius(2f, 2f), style = Stroke(width = 1.8f))
    drawRoundRect(Color(0xFF38BDF8), topLeft = Offset(4.4f, 13.2f), size = Size(6.4f, 6.4f), cornerRadius = CornerRadius(2f, 2f), style = Stroke(width = 1.8f))
    drawRoundRect(Color(0xFF0EA5E9), topLeft = Offset(13.2f, 13.2f), size = Size(6.4f, 6.4f), cornerRadius = CornerRadius(2f, 2f))
}

private fun DrawScope.drawBriefcase() {
    drawRoundRect(
        color = Color(0xFF92400E),
        topLeft = Offset(8.6f, 4.2f),
        size = Size(6.8f, 3.4f),
        cornerRadius = CornerRadius(1.2f, 1.2f),
        style = Stroke(width = 1.7f)
    )
    drawRoundRect(Color(0xFFB45309), topLeft = Offset(3.5f, 7f), size = Size(17f, 12.8f), cornerRadius = CornerRadius(2.8f, 2.8f))
    drawLine(Color(0xFFFDE047), Offset(4f, 12.4f), Offset(20f, 12.4f), strokeWidth = 1.4f)
    drawRoundRect(Color(0xFFFDE047), topLeft = Offset(10.6f, 11.4f), size = Size(2.8f, 2.4f), cornerRadius = CornerRadius(0.6f, 0.6f))
}

private fun DrawScope.drawCashNotes() {
    drawRoundRect(Color(0xFF22C55E), topLeft = Offset(3.8f, 6.2f), size = Size(16.4f, 11.4f), cornerRadius = CornerRadius(2.4f, 2.4f))
    drawRoundRect(Color(0xFF16A34A), topLeft = Offset(5.6f, 8f), size = Size(12.8f, 7.8f), cornerRadius = CornerRadius(1.6f, 1.6f), style = Stroke(width = 1.3f))
    drawCircle(color = Color(0xFFFDE047), radius = 2.5f, center = Offset(12f, 11.9f))
}

private fun DrawScope.drawMoneyPouch(p: Path) {
    drawRoundRect(Color(0xFFEA580C), topLeft = Offset(8.5f, 4f), size = Size(7f, 3.4f), cornerRadius = CornerRadius(1.4f, 1.4f))
    drawCircle(color = Color(0xFFFBBF24), radius = 7.4f, center = Offset(12f, 13.8f))
    drawRoundRect(Color(0xFFEF4444), topLeft = Offset(8.2f, 6.8f), size = Size(7.6f, 1.8f), cornerRadius = CornerRadius(0.9f, 0.9f))
    p.reset()
    p.moveTo(9.8f, 11.4f)
    p.lineTo(12f, 14f)
    p.lineTo(14.2f, 11.4f)
    p.moveTo(12f, 14f)
    p.lineTo(12f, 17f)
    p.moveTo(9.8f, 14.8f)
    p.lineTo(14.2f, 14.8f)
    drawPath(p, color = Color(0xFF92400E), style = Stroke(width = 1.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

private fun DrawScope.drawRefundLoop(p: Path) {
    drawCircle(color = Color(0xFF0EA5E9), radius = 9.2f, center = Offset(12f, 12f))
    drawArc(
        color = Color.White,
        startAngle = 30f,
        sweepAngle = 280f,
        useCenter = false,
        topLeft = Offset(6.8f, 6.8f),
        size = Size(10.4f, 10.4f),
        style = Stroke(width = 2f, cap = StrokeCap.Round)
    )
    p.reset()
    p.moveTo(15.4f, 6.6f)
    p.lineTo(17.6f, 9.6f)
    p.lineTo(14f, 9.6f)
    p.close()
    drawPath(p, color = Color(0xFFFDE047))
}

private fun DrawScope.drawReceiptNote(p: Path) {
    drawRoundRect(Color(0xFF38BDF8), topLeft = Offset(5.8f, 3.8f), size = Size(12.4f, 16.4f), cornerRadius = CornerRadius(2.2f, 2.2f))
    drawRoundRect(Color.White, topLeft = Offset(7.2f, 5.4f), size = Size(9.6f, 13.2f), cornerRadius = CornerRadius(1.2f, 1.2f))
    drawLine(Color(0xFF0284C7), Offset(9f, 8.6f), Offset(15f, 8.6f), strokeWidth = 1.4f, cap = StrokeCap.Round)
    drawLine(Color(0xFF0284C7), Offset(9f, 11.8f), Offset(15f, 11.8f), strokeWidth = 1.4f, cap = StrokeCap.Round)
    drawLine(Color(0xFFF97316), Offset(9f, 15f), Offset(13f, 15f), strokeWidth = 1.6f, cap = StrokeCap.Round)
    p.reset()
}

private fun DrawScope.drawPriceTag(p: Path) {
    p.reset()
    p.moveTo(4.5f, 11.2f)
    p.lineTo(11.4f, 4.4f)
    p.lineTo(19.6f, 4.4f)
    p.lineTo(19.6f, 12.6f)
    p.lineTo(12.8f, 19.5f)
    p.close()
    drawPath(p, color = Color(0xFFF59E0B))
    drawCircle(color = Color.White, radius = 1.8f, center = Offset(15.6f, 8.4f))
}

private fun DrawScope.drawBankBuilding(p: Path) {
    p.reset()
    p.moveTo(3.5f, 9.5f)
    p.lineTo(12f, 3.8f)
    p.lineTo(20.5f, 9.5f)
    p.close()
    drawPath(p, color = Color(0xFF2563EB))
    drawLine(Color(0xFF60A5FA), Offset(6.5f, 11f), Offset(6.5f, 17f), strokeWidth = 2.2f, cap = StrokeCap.Round)
    drawLine(Color(0xFF60A5FA), Offset(12f, 11f), Offset(12f, 17f), strokeWidth = 2.2f, cap = StrokeCap.Round)
    drawLine(Color(0xFF60A5FA), Offset(17.5f, 11f), Offset(17.5f, 17f), strokeWidth = 2.2f, cap = StrokeCap.Round)
    drawRoundRect(Color(0xFF1D4ED8), topLeft = Offset(3.5f, 17.4f), size = Size(17f, 2.8f), cornerRadius = CornerRadius(1f, 1f))
}

private fun DrawScope.drawLeatherWallet() {
    drawRoundRect(Color(0xFF2AA876), topLeft = Offset(3.6f, 5.8f), size = Size(16.8f, 13.2f), cornerRadius = CornerRadius(3.2f, 3.2f))
    drawRoundRect(Color(0xFF15803D), topLeft = Offset(13.2f, 10.2f), size = Size(7.8f, 4.6f), cornerRadius = CornerRadius(2.2f, 2.2f))
    drawCircle(color = Color(0xFFFDE047), radius = 1.3f, center = Offset(16f, 12.5f))
}

private fun DrawScope.drawGoldCard() {
    drawRoundRect(Color(0xFFF59E0B), topLeft = Offset(3f, 5.2f), size = Size(18f, 13.4f), cornerRadius = CornerRadius(2.8f, 2.8f))
    drawRect(Color(0xFF92400E), topLeft = Offset(3f, 8.2f), size = Size(18f, 2.6f))
    drawRoundRect(Color(0xFFFEF08A), topLeft = Offset(5.2f, 12.8f), size = Size(4.2f, 3f), cornerRadius = CornerRadius(0.8f, 0.8f))
}
