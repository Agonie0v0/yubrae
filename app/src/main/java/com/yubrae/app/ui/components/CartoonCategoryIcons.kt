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
 * - 115 个内置一/二级分类与 21 个预置标签均配备独立、零撞图的多色扁平卡通绘图；
 * - 全量 180+ 分类与标签拓展键均具备高精卡通绘或柔彩贴纸风格，与账户图标体系统一。
 */
object CartoonCategoryIcons {

    private class RegistryBuilder {
        val entries = LinkedHashMap<String, CartoonVisualEntry>()

        fun reg(variantId: String, vararg keys: String, drawer: DrawScope.(Path) -> Unit) {
            val entry = CartoonVisualEntry(variantId = variantId, drawer = drawer)
            keys.forEach { rawKey ->
                entries[rawKey.trim().lowercase(Locale.ROOT)] = entry
            }
        }
    }

    private data class CartoonVisualEntry(
        val variantId: String,
        val drawer: DrawScope.(Path) -> Unit
    )

    private val cartoonRegistry: Map<String, CartoonVisualEntry> = RegistryBuilder().apply {
        // 1. 餐饮美食 (9 个内置全独立 + 细分早餐/午餐/晚餐/零食专属)
        reg("dining_plate", "restaurant") { drawDiningPlate(it) }
        reg("ramen_bowl", "ramen_dining") { drawRamenBowl(it) }
        reg("breakfast_egg", "breakfast_egg", "breakfast_dining", "free_breakfast", "brunch_dining", "egg_alt") { drawBreakfastEgg(it) }
        reg("lunch_bento", "lunch_bento", "lunch_dining", "bento", "rice_bowl") { drawLunchBento() }
        reg("dinner_bowl", "dinner_bowl", "dinner_dining") { drawDinnerBowl(it) }
        reg("snack_bag", "snack_bag", "cookie", "tapas") { drawSnackBag(it) }
        reg("takeout_box", "delivery_dining", "takeout_dining") { drawTakeoutBox(it) }
        reg("fastfood_burger", "fastfood") { drawFastfoodBurger(it) }
        reg("coffee_cup", "local_cafe", "coffee") { drawCoffeeCup(it) }
        reg("tea_pot", "emoji_food_beverage") { drawTeaPot(it) }
        reg("cupcake", "bakery_dining", "icecream") { drawCupcake(it) }
        reg("fresh_apple", "local_grocery_store") { drawFreshApple(it) }
        reg("cooking_pot", "kitchen", "soup_kitchen") { drawCookingPot() }
        reg("cocktail_drink", "local_bar", "wine_bar", "sports_bar", "liquor", "local_pizza") { drawCocktailDrink(it) }

        // 2. 出行与交通 (8 个内置全独立 + 公共交通巴士)
        reg("cartoon_car", "directions_car", "electric_car") { drawCartoonCar() }
        reg("car_repair", "local_car_wash") { drawCarRepair(it) }
        reg("metro_train", "subway", "tram") { drawMetroTrain() }
        reg("allowance_bus", "directions_bus") { drawAllowanceBus() }
        reg("yellow_taxi", "local_taxi") { drawYellowTaxi() }
        reg("bicycle", "pedal_bike", "two_wheeler", "electric_scooter") { drawBicycle() }
        reg("high_speed_train", "train", "directions_boat") { drawHighSpeedTrain(it) }
        reg("gas_pump", "local_gas_station", "ev_station") { drawGasPump(it) }
        reg("parking_sign", "local_parking", "toll") { drawParkingSign(it) }
        reg("tools", "handyman", "build") { drawTools(it) }

        // 3. 日用购物与快递 (5 个内置全独立 + 日常家居台灯/退货包裹)
        reg("shopping_bag", "shopping_bag") { drawShoppingBag() }
        reg("online_mall", "local_mall") { drawOnlineMall() }
        reg("shopping_cart", "shopping_cart") { drawShoppingCart(it) }
        reg("stockup_cart", "add_shopping_cart") { drawStockupCart(it) }
        reg("home_goods", "weekend") { drawHomeGoods() }
        reg("household_lamp", "household_lamp", "chair", "bed") { drawHouseholdLamp(it) }
        reg("shop_front", "storefront", "store", "local_convenience_store") { drawShopFront() }
        reg("parcel_box", "local_shipping", "inventory_2") { drawParcelBox(it) }
        reg("refund_parcel", "refund_parcel", "local_post_office") { drawRefundParcel(it) }

        // 4. 服饰美妆与配饰 (5 个内置全独立 + 个护洗护/腕表/运动鞋)
        reg("fashion_dress", "style") { drawFashionDress(it) }
        reg("tshirt", "checkroom", "iron") { drawTShirt(it) }
        reg("sport_shoe", "sport_shoe", "sports_soccer", "sports_basketball", "sports_tennis", "hiking") { drawSportShoe(it) }
        reg("lipstick", "face_retouching_natural", "auto_fix_high", "filter_vintage") { drawLipstick(it) }
        reg("personal_care", "personal_care", "spa") { drawPersonalCare() }
        reg("scissors", "content_cut", "brush") { drawScissors(it) }
        reg("diamond", "diamond") { drawDiamond(it) }
        reg("wrist_watch", "wrist_watch", "watch") { drawWristWatch() }

        // 5. 居家水电网与通信 (6 个内置全独立 + 电费插头/水滴/燃气/物业费/房贷契约)
        reg("cozy_house", "home") { drawCozyHouse(it) }
        reg("house_key", "cottage") { drawHouseKey(it) }
        reg("house_contract", "house_contract") { drawHouseContract(it) }
        reg("property_fee", "property_fee") { drawPropertyFee() }
        reg("family_house", "family_restroom") { drawFamilyHouse(it) }
        reg("electric_bolt", "bolt") { drawElectricBolt(it) }
        reg("electric_plug", "electric_plug", "lightbulb") { drawElectricPlug(it) }
        reg("water_drop", "water_drop", "bathtub", "ac_unit") { drawWaterDrop(it) }
        reg("gas_flame", "propane_tank", "local_fire_department", "outdoor_grill") { drawGasFlame(it) }
        reg("mobile_card", "wifi", "sim_card", "router") { drawMobileCard() }
        reg("cleaning_sparkle", "cleaning_services", "yard", "local_florist") { drawCleaningSparkle(it) }

        // 6. 数码家电、订阅与娱乐 (14 个内置全独立 + 手机/洗衣机/服务器/虚拟充值)
        reg("device_hub", "devices") { drawDeviceHub() }
        reg("smartphone_dev", "smartphone_dev", "phone_android", "smartphone") { drawSmartphoneDev() }
        reg("laptop", "laptop_mac", "tablet_mac", "keyboard", "print") { drawLaptop() }
        reg("code_monitor", "desktop_windows") { drawCodeMonitor() }
        reg("headphones", "headphones", "speaker") { drawHeadphones() }
        reg("retro_tv", "tv") { drawRetroTv() }
        reg("appliance_washer", "appliance_washer", "local_laundry_service", "dry_cleaning") { drawApplianceWasher() }
        reg("server_rack", "server_rack") { drawServerRack() }
        reg("virtual_recharge", "virtual_recharge") { drawVirtualRecharge(it) }
        reg("sub_cycle", "subscriptions") { drawSubCycle(it) }
        reg("vip_crown", "cloud_done") { drawCloudSub(it) }
        reg("ai_bot", "smart_toy") { drawAiBot() }
        reg("music_note", "music_note") { drawMusicNote() }
        reg("creator_mic", "mic") { drawCreatorMic() }
        reg("gamepad", "sports_esports") { drawGamepad() }
        reg("leisure_mask", "theater_comedy", "nightlife") { drawLeisureMask(it) }
        reg("movie_popcorn", "movie") { drawMoviePopcorn(it) }
        reg("party_popper", "celebration") { drawPartyPopper(it) }
        reg("scenic_ticket", "confirmation_number") { drawScenicTicket(it) }
        reg("dumbbell", "fitness_center", "pool") { drawDumbbell() }
        reg("yoga_lotus", "self_improvement") { drawYogaLotus(it) }
        reg("camp_tent", "forest", "park") { drawPineTree(it) }
        reg("camera_snap", "camera_alt") { drawCameraSnap() }
        reg("qr_scanner", "qr_code_scanner") { drawQrScanner() }

        // 7. 医疗健康 (5 个内置全独立 + 扩展)
        reg("medical_kit", "medical_services") { drawMedicalKit() }
        reg("hospital", "local_hospital") { drawHospital(it) }
        reg("medicine_pill", "medication", "local_pharmacy", "vaccines", "bloodtype") { drawMedicinePill() }
        reg("heart_monitor", "monitor_heart") { drawHeartMonitor(it) }
        reg("health_shield", "health_and_safety") { drawHealthShield(it) }
        reg("pet_clinic", "healing") { drawPetClinic(it) }
        reg("mind_brain", "psychology") { drawMindHeart(it) }
        reg("eye_care", "visibility") { drawEyeCare() }

        // 8. 差旅住宿 (5 个内置全独立 + 扩展)
        reg("travel_compass", "explore", "map") { drawTravelCompass(it) }
        reg("airplane", "flight", "airplane_ticket") { drawAirplane(it) }
        reg("hotel_building", "hotel") { drawHotelBuilding() }
        reg("souvenir_pack", "backpack") { drawSouvenirPack(it) }
        reg("luggage", "luggage", "beach_access") { drawLuggage() }
        reg("globe_world", "public") { drawGlobeWorld() }

        // 9. 学习成长 (5 个内置全独立 + 办公文件夹)
        reg("grad_cap", "school") { drawGradCap(it) }
        reg("book_stack", "book", "menu_book") { drawBookStack() }
        reg("course_board", "menu_course", "science") { drawCourseBoard(it) }
        reg("exam_banner", "edit_note") { drawExamBanner(it) }
        reg("stationery", "stationery", "palette") { drawStationery(it) }
        reg("office_folder", "office_folder") { drawOfficeFolder() }

        // 10. 人情社交、宠物与育儿 (13 个内置全独立 + 母婴玩具摇铃)
        reg("cute_heart", "favorite") { drawCuteHeart(it, Color(0xFFF43F5E), Color(0xFFFFA4B6)) }
        reg("charity_box", "volunteer_activism") { drawCareHeart(it) }
        reg("elderly_care", "elderly") { drawElderlyCare(it) }
        reg("red_envelope", "card_giftcard") { drawRedEnvelope() }
        reg("social_envelope", "card_membership") { drawSocialEnvelope(it) }
        reg("green_red_packet", "chat") { drawGreenRedPacket(it) }
        reg("gift_box", "redeem") { drawGiftBox(it) }
        reg("social_group", "groups") { drawSocialGroup() }
        reg("social_care", "handshake") { drawSocialCare(it) }
        reg("person_avatar", "person") { drawPersonAvatar() }
        reg("pet_paw", "pets") { drawPetPaw() }
        reg("pet_food_bowl", "pet_food", "set_meal") { drawPetFoodBowl(it) }
        reg("pet_supplies", "pet_supplies", "shopping_basket") { drawPetSupplies(it) }
        reg("baby_stroller", "stroller") { drawBabyStroller() }
        reg("baby_bottle", "child_care") { drawBabyBottle() }
        reg("kids_pinwheel", "toys") { drawKidsPinwheel(it) }
        reg("baby_rattle", "baby_rattle") { drawBabyRattle(it) }
        reg("early_blocks", "early_edu", "translate") { drawEarlyBlocks() }

        // 11. 金融保险、收入与报销 (全部内置全独立 + 其他支出三点徽章)
        reg("finance_vault", "assured_workload") { drawFinanceVault() }
        reg("commercial_shield", "verified_user") { drawCommercialShield(it) }
        reg("verified_seal", "verified") { drawVerifiedSeal(it) }
        reg("exchange_coins", "currency_exchange") { drawExchangeCoins() }
        reg("percent_coin", "percent") { drawPercentCoin() }
        reg("tax_calculator", "calculate") { drawTaxCalculator() }
        reg("insurance_claim", "request_quote") { drawInsuranceClaim(it) }
        reg("trend_up", "trending_up", "show_chart", "multiline_chart") { drawTrendUp(it) }
        reg("candlestick", "candlestick_chart") { drawCandlestick() }
        reg("performance_bars", "bar_chart") { drawPerformanceBars() }
        reg("dividend_pie", "pie_chart") { drawDividendPie() }
        reg("trend_down", "trending_down") { drawTrendDown(it) }
        reg("more_dots", "more_horiz") { drawMoreDots() }
        reg("four_grid", "category", "label") { drawFourGrid() }
        reg("lost_alert", "priority_high") { drawLostAlert(it) }
        reg("briefcase", "work") { drawBriefcase() }
        reg("side_hustle_bag", "business_center") { drawSideHustleBag() }
        reg("overtime_clock", "schedule") { drawOvertimeClock() }
        reg("gold_trophy", "emoji_events") { drawGoldTrophy(it) }
        reg("bonus_star", "star") { drawBonusStar(it) }
        reg("festival_gift", "cake") { drawFestivalGift() }
        reg("cash_notes", "payments", "local_atm") { drawCashNotes() }
        reg("allowance_ticket", "commute_allowance") { drawAllowanceTicket() }
        reg("piggy_bank", "savings") { drawPiggyBank() }
        reg("fx_coin", "monetization_on") { drawFxCoin() }
        reg("money_pouch", "attach_money", "euro", "currency_pound", "currency_yen", "currency_bitcoin") { drawMoneyPouch(it) }
        reg("refund_loop", "replay") { drawRefundLoop(it) }
        reg("receipt_note", "receipt_long") { drawReceiptNote(it) }
        reg("price_tag", "sell") { drawPriceTag(it) }
        reg("loyalty_card", "loyalty") { drawLoyaltyCard(it) }
        reg("coupon_ticket", "local_offer") { drawCouponTicket(it) }
        reg("bank_building", "account_balance") { drawBankBuilding(it) }
        reg("housing_fund_card", "badge") { drawHousingFundCard() }
        reg("cash_register", "point_of_sale") { drawCashRegister() }
        reg("rent_apartment", "corporate_fare") { drawRentApartment() }
        reg("leather_wallet", "account_balance_wallet", "wallet") { drawLeatherWallet() }
        reg("gold_card", "credit_card", "credit_score", "add_card") { drawGoldCard() }

        // 12. 21 个预置标签专属多色卡通图标（与全部分类 100% 物理隔离、零撞图）
        reg("tag_pdd", "tag_pdd") { drawTagPdd() }
        reg("tag_taobao", "tag_taobao") { drawTagTaobao() }
        reg("tag_jd", "tag_jd") { drawTagJd(it) }
        reg("tag_meituan", "tag_meituan") { drawTagMeituan(it) }
        reg("tag_offline", "tag_offline") { drawTagOffline(it) }
        reg("tag_crossborder", "tag_crossborder") { drawTagCrossBorder(it) }
        reg("tag_commute", "tag_commute") { drawTagCommute() }
        reg("tag_businesstrip", "tag_businesstrip") { drawTagBusinessTrip() }
        reg("tag_travel", "tag_travel") { drawTagTravel(it) }
        reg("tag_stockup", "tag_stockup") { drawTagStockup(it) }
        reg("tag_essential", "tag_essential") { drawTagEssential(it) }
        reg("tag_installment", "tag_installment") { drawTagInstallment() }
        reg("tag_discount", "tag_discount") { drawTagDiscount() }
        reg("tag_impulse", "tag_impulse") { drawTagImpulse(it) }
        reg("tag_large", "tag_large") { drawTagLarge(it) }
        reg("tag_self", "tag_self") { drawTagSelf() }
        reg("tag_family", "tag_family") { drawTagFamily(it) }
        reg("tag_partner", "tag_partner") { drawTagPartner(it) }
        reg("tag_aa", "tag_aa") { drawTagAa() }
        reg("tag_taxdeduct", "tag_taxdeduct") { drawTagTaxDeduct() }
        reg("tag_renovation", "tag_renovation") { drawTagRenovation() }
    }.entries

    fun hasBespokeIcon(iconKey: String): Boolean =
        iconKey.trim().lowercase(Locale.ROOT) in cartoonRegistry

    internal fun resolveVisualVariantId(iconKey: String): String =
        cartoonRegistry[iconKey.trim().lowercase(Locale.ROOT)]?.variantId ?: "sticker"

    fun drawCartoonCategory(scope: DrawScope, iconKey: String, scratchPath: Path) {
        val entry = cartoonRegistry[iconKey.trim().lowercase(Locale.ROOT)] ?: return
        val unit = scope.size.minDimension / 24f
        if (unit <= 0f) return
        scope.scale(scaleX = unit, scaleY = unit, pivot = Offset.Zero) {
            entry.drawer(this, scratchPath)
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
    drawCircle(color = Color(0xFFDCFCE7), radius = 8.4f, center = Offset(12f, 12.6f))
    rotate(32f, pivot = Offset(12f, 12f)) {
        drawRoundRect(Color(0xFFD97706), topLeft = Offset(11f, 3.2f), size = Size(2f, 10.6f), cornerRadius = CornerRadius(1f, 1f))
        p.reset()
        p.moveTo(8.2f, 13.4f)
        p.lineTo(15.8f, 13.4f)
        p.lineTo(17.2f, 19.6f)
        p.lineTo(6.8f, 19.6f)
        p.close()
        drawPath(p, color = Color(0xFF0EA5E9))
        drawRoundRect(Color(0xFF0284C7), topLeft = Offset(8f, 12.6f), size = Size(8f, 2f), cornerRadius = CornerRadius(0.8f, 0.8f))
    }
    drawCircle(color = Color(0xFF38BDF8), radius = 1.4f, center = Offset(6.2f, 7.6f))
    drawCircle(color = Color(0xFFF59E0B), radius = 1.2f, center = Offset(18f, 8.8f))
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
    drawRoundRect(Color(0xFF7C3AED), topLeft = Offset(3.8f, 4.4f), size = Size(16.4f, 15.2f), cornerRadius = CornerRadius(4f, 4f))
    p.reset()
    p.moveTo(6.6f, 15.2f)
    p.lineTo(7.4f, 8.8f)
    p.lineTo(10.4f, 11.4f)
    p.lineTo(12f, 7.4f)
    p.lineTo(13.6f, 11.4f)
    p.lineTo(16.6f, 8.8f)
    p.lineTo(17.4f, 15.2f)
    p.close()
    drawPath(p, color = Color(0xFFFDE047))
    drawRoundRect(Color(0xFFF59E0B), topLeft = Offset(6.8f, 15.6f), size = Size(10.4f, 1.8f), cornerRadius = CornerRadius(0.9f, 0.9f))
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
    // 户外露营帐篷 + 后景青松
    p.reset()
    p.moveTo(15.6f, 4.2f)
    p.lineTo(20.6f, 12.4f)
    p.lineTo(10.6f, 12.4f)
    p.close()
    drawPath(p, color = Color(0xFF16A34A))
    drawRoundRect(Color(0xFF92400E), topLeft = Offset(14.8f, 12.4f), size = Size(1.6f, 3.2f), cornerRadius = CornerRadius(0.6f, 0.6f))
    p.reset()
    p.moveTo(3.2f, 19.6f)
    p.lineTo(9.8f, 7.6f)
    p.lineTo(16.6f, 19.6f)
    p.close()
    drawPath(p, color = Color(0xFFF97316))
    p.reset()
    p.moveTo(7.4f, 19.6f)
    p.lineTo(9.8f, 13.4f)
    p.lineTo(12.2f, 19.6f)
    p.close()
    drawPath(p, color = Color(0xFFFEF3C7))
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
    // 礼金往来：双手交握 + 顶部金币
    drawCircle(color = Color(0xFFF59E0B), radius = 3.2f, center = Offset(12f, 6.2f))
    drawCircle(color = Color(0xFFFDE047), radius = 1.6f, center = Offset(12f, 6.2f))
    p.reset()
    p.moveTo(3.6f, 13.6f)
    p.lineTo(8.8f, 10.6f)
    p.lineTo(13.2f, 13.4f)
    p.lineTo(9.4f, 17.8f)
    p.close()
    drawPath(p, color = Color(0xFF38BDF8))
    p.reset()
    p.moveTo(20.4f, 13.6f)
    p.lineTo(15.2f, 10.6f)
    p.lineTo(10.8f, 13.4f)
    p.lineTo(14.6f, 17.8f)
    p.close()
    drawPath(p, color = Color(0xFFFB7185))
    drawRoundRect(Color(0xFFFDE047), topLeft = Offset(9.6f, 13.2f), size = Size(4.8f, 3.2f), cornerRadius = CornerRadius(1.2f, 1.2f))
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

private fun DrawScope.drawDiningPlate(p: Path) {
    drawCircle(color = Color(0xFFFED7AA), radius = 7.4f, center = Offset(12f, 12.4f))
    drawCircle(color = Color(0xFFFFF7ED), radius = 5.2f, center = Offset(12f, 12.4f))
    drawCircle(color = Color(0xFFFB923C), radius = 2.4f, center = Offset(12f, 12.4f))
    // 左叉
    drawLine(Color(0xFFEA580C), Offset(3.4f, 6.2f), Offset(3.4f, 18.6f), strokeWidth = 1.4f, cap = StrokeCap.Round)
    drawLine(Color(0xFFEA580C), Offset(2.3f, 6.2f), Offset(2.3f, 9.6f), strokeWidth = 1.1f, cap = StrokeCap.Round)
    drawLine(Color(0xFFEA580C), Offset(4.5f, 6.2f), Offset(4.5f, 9.6f), strokeWidth = 1.1f, cap = StrokeCap.Round)
    // 右刀
    drawLine(Color(0xFFEA580C), Offset(20.6f, 6.2f), Offset(20.6f, 18.6f), strokeWidth = 1.6f, cap = StrokeCap.Round)
    p.reset()
}

private fun DrawScope.drawFastfoodBurger(p: Path) {
    drawArc(
        color = Color(0xFFF59E0B),
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = true,
        topLeft = Offset(4.5f, 4.5f),
        size = Size(15f, 10f)
    )
    drawRoundRect(Color(0xFF22C55E), topLeft = Offset(4f, 9.8f), size = Size(16f, 2f), cornerRadius = CornerRadius(1f, 1f))
    drawRoundRect(Color(0xFF92400E), topLeft = Offset(4.2f, 12f), size = Size(15.6f, 2.8f), cornerRadius = CornerRadius(1.2f, 1.2f))
    drawRoundRect(Color(0xFFFBBF24), topLeft = Offset(4.6f, 15f), size = Size(14.8f, 3.8f), cornerRadius = CornerRadius(1.8f, 1.8f))
    drawCircle(Color(0xFFFEF3C7), radius = 0.7f, center = Offset(9f, 7.2f))
    drawCircle(Color(0xFFFEF3C7), radius = 0.7f, center = Offset(14.8f, 7.2f))
    p.reset()
}

private fun DrawScope.drawTeaPot(p: Path) {
    drawLine(Color(0xFF84CC16), Offset(9.5f, 4.5f), Offset(10f, 2.8f), strokeWidth = 1.2f, cap = StrokeCap.Round)
    drawLine(Color(0xFF84CC16), Offset(14f, 4.5f), Offset(14.5f, 2.8f), strokeWidth = 1.2f, cap = StrokeCap.Round)
    drawCircle(color = Color(0xFFB45309), radius = 1.4f, center = Offset(11.6f, 6.8f))
    drawOval(color = Color(0xFF15803D), topLeft = Offset(5.6f, 8.2f), size = Size(12f, 11.4f))
    drawRoundRect(Color(0xFF22C55E), topLeft = Offset(8f, 7.4f), size = Size(7.2f, 2.2f), cornerRadius = CornerRadius(1f, 1f))
    drawArc(
        color = Color(0xFF15803D),
        startAngle = -85f,
        sweepAngle = 170f,
        useCenter = false,
        topLeft = Offset(15.2f, 10.2f),
        size = Size(5f, 6f),
        style = Stroke(width = 1.7f, cap = StrokeCap.Round)
    )
    p.reset()
    p.moveTo(6.2f, 12.6f)
    p.lineTo(3.2f, 9.6f)
    drawPath(p, color = Color(0xFF16A34A), style = Stroke(width = 2f, cap = StrokeCap.Round))
    drawCircle(color = Color(0xFFBBF7D0), radius = 1.4f, center = Offset(11.6f, 13.8f))
}

private fun DrawScope.drawCarRepair(p: Path) {
    // 养车维修：洗车泡泡星光 + 迷你蓝车 + 橙色扳手
    drawCircle(color = Color(0xFF7DD3FC), radius = 2.1f, center = Offset(5.8f, 6.2f))
    drawCircle(color = Color.White, radius = 0.7f, center = Offset(5.1f, 5.5f))
    drawCircle(color = Color(0xFFBAE6FD), radius = 1.4f, center = Offset(10.2f, 4.6f))
    drawCircle(color = Color(0xFF7DD3FC), radius = 1.5f, center = Offset(19.4f, 15.6f))
    p.reset()
    p.moveTo(12.2f, 5.8f)
    p.lineTo(12.7f, 7.1f)
    p.lineTo(14f, 7.6f)
    p.lineTo(12.7f, 8.1f)
    p.lineTo(12.2f, 9.4f)
    p.lineTo(11.7f, 8.1f)
    p.lineTo(10.4f, 7.6f)
    p.lineTo(11.7f, 7.1f)
    p.close()
    drawPath(p, color = Color(0xFFFACC15))
    drawRoundRect(Color(0xFF38BDF8), topLeft = Offset(4.4f, 10.4f), size = Size(9f, 4.4f), cornerRadius = CornerRadius(2f, 2f))
    drawRoundRect(Color(0xFFE0F2FE), topLeft = Offset(5.6f, 11.4f), size = Size(6.6f, 2.4f), cornerRadius = CornerRadius(0.9f, 0.9f))
    drawRoundRect(Color(0xFF0284C7), topLeft = Offset(2.8f, 13.6f), size = Size(13.2f, 4.4f), cornerRadius = CornerRadius(2f, 2f))
    drawCircle(color = Color(0xFFFDE047), radius = 0.95f, center = Offset(4.6f, 15.6f))
    drawCircle(color = Color(0xFF1E293B), radius = 1.8f, center = Offset(5.8f, 18.2f))
    drawCircle(color = Color(0xFF1E293B), radius = 1.8f, center = Offset(13f, 18.2f))
    drawCircle(color = Color.White, radius = 0.7f, center = Offset(5.8f, 18.2f))
    drawCircle(color = Color.White, radius = 0.7f, center = Offset(13f, 18.2f))
    rotate(32f, pivot = Offset(16.6f, 9.2f)) {
        drawArc(
            color = Color(0xFFF97316),
            startAngle = -45f,
            sweepAngle = 270f,
            useCenter = false,
            topLeft = Offset(14.4f, 4.2f),
            size = Size(4.4f, 4.4f),
            style = Stroke(width = 1.9f, cap = StrokeCap.Round)
        )
        drawRoundRect(Color(0xFFEA580C), topLeft = Offset(15.5f, 7.6f), size = Size(2.2f, 6f), cornerRadius = CornerRadius(1.1f, 1.1f))
        drawCircle(color = Color(0xFFFDBA74), radius = 0.65f, center = Offset(16.6f, 12.6f))
    }
}

private fun DrawScope.drawAllowanceBus() {
    drawRoundRect(Color(0xFF2563EB), topLeft = Offset(4.6f, 4f), size = Size(14.8f, 14.2f), cornerRadius = CornerRadius(3.4f, 3.4f))
    drawRoundRect(Color(0xFFDBEAFE), topLeft = Offset(6.4f, 6.2f), size = Size(11.2f, 5.2f), cornerRadius = CornerRadius(1.4f, 1.4f))
    drawRoundRect(Color(0xFFFDE047), topLeft = Offset(6.6f, 13.6f), size = Size(3f, 2f), cornerRadius = CornerRadius(0.8f, 0.8f))
    drawRoundRect(Color(0xFFFDE047), topLeft = Offset(14.4f, 13.6f), size = Size(3f, 2f), cornerRadius = CornerRadius(0.8f, 0.8f))
    drawRoundRect(Color(0xFF1E293B), topLeft = Offset(6.4f, 18f), size = Size(2.6f, 2.4f), cornerRadius = CornerRadius(0.9f, 0.9f))
    drawRoundRect(Color(0xFF1E293B), topLeft = Offset(15f, 18f), size = Size(2.6f, 2.4f), cornerRadius = CornerRadius(0.9f, 0.9f))
}

private fun DrawScope.drawOnlineMall() {
    // 网购百货：手机电商界面 + 橙色快递包裹 + 绿色购买按钮
    drawRoundRect(Color(0xFF334155), topLeft = Offset(5.6f, 2.8f), size = Size(12.8f, 18.4f), cornerRadius = CornerRadius(2.6f, 2.6f))
    drawRoundRect(Color(0xFFF8FAFC), topLeft = Offset(6.9f, 4.4f), size = Size(10.2f, 14f), cornerRadius = CornerRadius(1.4f, 1.4f))
    drawRoundRect(Color(0xFF6366F1), topLeft = Offset(6.9f, 4.4f), size = Size(10.2f, 2.3f), cornerRadius = CornerRadius(1.2f, 1.2f))
    drawLine(Color.White, Offset(9.4f, 5.55f), Offset(14.6f, 5.55f), strokeWidth = 1.1f, cap = StrokeCap.Round)
    drawRoundRect(Color(0xFFF97316), topLeft = Offset(8.4f, 7.6f), size = Size(7.2f, 5.8f), cornerRadius = CornerRadius(1.2f, 1.2f))
    drawRoundRect(Color(0xFFFDBA74), topLeft = Offset(8.4f, 7.6f), size = Size(7.2f, 2f), cornerRadius = CornerRadius(1.1f, 1.1f))
    drawRect(Color(0xFFFEF3C7), topLeft = Offset(11.2f, 7.6f), size = Size(1.6f, 3f))
    drawRoundRect(Color(0xFF16A34A), topLeft = Offset(8f, 14.6f), size = Size(8f, 2.8f), cornerRadius = CornerRadius(1.4f, 1.4f))
    drawLine(Color.White, Offset(10.2f, 16f), Offset(13.8f, 16f), strokeWidth = 1.2f, cap = StrokeCap.Round)
    drawCircle(color = Color(0xFF94A3B8), radius = 0.85f, center = Offset(12f, 19.8f))
}

private fun DrawScope.drawStockupCart(p: Path) {
    drawShoppingCart(p)
    drawCircle(color = Color(0xFFF59E0B), radius = 3.8f, center = Offset(17.5f, 6.6f))
    drawLine(Color.White, Offset(15.7f, 6.6f), Offset(19.3f, 6.6f), strokeWidth = 1.5f, cap = StrokeCap.Round)
    drawLine(Color.White, Offset(17.5f, 4.8f), Offset(17.5f, 8.4f), strokeWidth = 1.5f, cap = StrokeCap.Round)
}

private fun DrawScope.drawHomeGoods() {
    drawRoundRect(Color(0xFFEA580C), topLeft = Offset(5.2f, 6.6f), size = Size(13.6f, 7.6f), cornerRadius = CornerRadius(3f, 3f))
    drawRoundRect(Color(0xFFFB923C), topLeft = Offset(3.4f, 10.6f), size = Size(17.2f, 6.6f), cornerRadius = CornerRadius(2.4f, 2.4f))
    drawRoundRect(Color(0xFFFED7AA), topLeft = Offset(6.2f, 11.8f), size = Size(11.6f, 2.8f), cornerRadius = CornerRadius(1.2f, 1.2f))
    drawLine(Color(0xFF92400E), Offset(6f, 17.2f), Offset(6f, 19.8f), strokeWidth = 1.8f, cap = StrokeCap.Round)
    drawLine(Color(0xFF92400E), Offset(18f, 17.2f), Offset(18f, 19.8f), strokeWidth = 1.8f, cap = StrokeCap.Round)
}

private fun DrawScope.drawRefundParcel(p: Path) {
    // 购物退款：暖棕色退货纸箱 + 青绿色弧形回寄箭头
    drawRoundRect(Color(0xFFB45309), topLeft = Offset(3.4f, 8.6f), size = Size(13.2f, 11.6f), cornerRadius = CornerRadius(2.2f, 2.2f))
    drawRoundRect(Color(0xFFD97706), topLeft = Offset(3.4f, 8.6f), size = Size(13.2f, 4f), cornerRadius = CornerRadius(2f, 2f))
    drawRect(Color(0xFFFEF3C7), topLeft = Offset(8.9f, 8.6f), size = Size(2.2f, 5.8f))
    drawRoundRect(Color(0xFFFDE68A), topLeft = Offset(5.2f, 15.2f), size = Size(4.6f, 2.8f), cornerRadius = CornerRadius(0.7f, 0.7f))
    drawArc(
        color = Color(0xFF0D9488),
        startAngle = -120f,
        sweepAngle = 165f,
        useCenter = false,
        topLeft = Offset(11.4f, 4.2f),
        size = Size(8.8f, 8.8f),
        style = Stroke(width = 2.2f, cap = StrokeCap.Round)
    )
    p.reset()
    p.moveTo(11.2f, 6.4f)
    p.lineTo(12.8f, 3.2f)
    p.lineTo(15f, 6.1f)
    p.close()
    drawPath(p, color = Color(0xFF0D9488))
}

private fun DrawScope.drawFashionDress(p: Path) {
    p.reset()
    p.moveTo(8.5f, 4.4f)
    p.lineTo(15.5f, 4.4f)
    p.lineTo(14.5f, 10.2f)
    p.lineTo(19f, 19.6f)
    p.lineTo(5f, 19.6f)
    p.lineTo(9.5f, 10.2f)
    p.close()
    drawPath(p, color = Color(0xFFF43F5E))
    drawRoundRect(Color(0xFFFDE047), topLeft = Offset(9f, 9.6f), size = Size(6f, 1.8f), cornerRadius = CornerRadius(0.9f, 0.9f))
}

private fun DrawScope.drawHouseKey(p: Path) {
    drawCircle(color = Color(0xFFF59E0B), radius = 4.6f, center = Offset(8.2f, 9.6f))
    drawCircle(color = Color(0xFFFEF3C7), radius = 2f, center = Offset(8.2f, 9.6f))
    drawRoundRect(Color(0xFFF59E0B), topLeft = Offset(11.4f, 11f), size = Size(8.8f, 2.6f), cornerRadius = CornerRadius(1f, 1f))
    drawRoundRect(Color(0xFFD97706), topLeft = Offset(15.4f, 13.4f), size = Size(2f, 3.6f), cornerRadius = CornerRadius(0.8f, 0.8f))
    drawRoundRect(Color(0xFFD97706), topLeft = Offset(18.2f, 13.4f), size = Size(2f, 2.8f), cornerRadius = CornerRadius(0.8f, 0.8f))
    p.reset()
}

private fun DrawScope.drawFamilyHouse(p: Path) {
    drawCozyHouse(p)
    drawCircle(color = Color(0xFFFB7185), radius = 2.6f, center = Offset(18.2f, 6.2f))
}

private fun DrawScope.drawDeviceHub() {
    drawRoundRect(Color(0xFF4F46E5), topLeft = Offset(3.4f, 5.2f), size = Size(12.2f, 13.6f), cornerRadius = CornerRadius(2.4f, 2.4f))
    drawRoundRect(Color(0xFFE0E7FF), topLeft = Offset(4.8f, 6.8f), size = Size(9.4f, 10f), cornerRadius = CornerRadius(1f, 1f))
    drawRoundRect(Color(0xFF0EA5E9), topLeft = Offset(13.2f, 9.2f), size = Size(7.4f, 10.6f), cornerRadius = CornerRadius(1.8f, 1.8f))
    drawRoundRect(Color(0xFFE0F2FE), topLeft = Offset(14.3f, 10.6f), size = Size(5.2f, 7.2f), cornerRadius = CornerRadius(0.8f, 0.8f))
}

private fun DrawScope.drawCodeMonitor() {
    drawRoundRect(Color(0xFF1E293B), topLeft = Offset(3.6f, 4.4f), size = Size(16.8f, 11.4f), cornerRadius = CornerRadius(2.2f, 2.2f))
    drawRoundRect(Color(0xFF0EA5E9), topLeft = Offset(5.2f, 6f), size = Size(13.6f, 8.2f), cornerRadius = CornerRadius(1f, 1f))
    drawLine(Color(0xFFBAE6FD), Offset(7f, 9f), Offset(13.2f, 9f), strokeWidth = 1.4f, cap = StrokeCap.Round)
    drawLine(Color(0xFFFDE047), Offset(7f, 11.6f), Offset(16f, 11.6f), strokeWidth = 1.4f, cap = StrokeCap.Round)
    drawRect(Color(0xFF64748B), topLeft = Offset(10.5f, 15.8f), size = Size(3f, 2.6f))
    drawRoundRect(Color(0xFF475569), topLeft = Offset(7.4f, 18.2f), size = Size(9.2f, 2f), cornerRadius = CornerRadius(1f, 1f))
}

private fun DrawScope.drawSubCycle(p: Path) {
    drawCircle(color = Color(0xFF7C3AED), radius = 9.2f, center = Offset(12f, 12f))
    drawArc(
        color = Color(0xFFFDE047),
        startAngle = -45f,
        sweepAngle = 260f,
        useCenter = false,
        topLeft = Offset(5.6f, 5.6f),
        size = Size(12.8f, 12.8f),
        style = Stroke(width = 1.8f, cap = StrokeCap.Round)
    )
    p.reset()
    p.moveTo(10.2f, 9f)
    p.lineTo(15.2f, 12f)
    p.lineTo(10.2f, 15f)
    p.close()
    drawPath(p, color = Color.White)
}

private fun DrawScope.drawCreatorMic() {
    drawRoundRect(Color(0xFFF59E0B), topLeft = Offset(8.6f, 3.6f), size = Size(6.8f, 9.8f), cornerRadius = CornerRadius(3.4f, 3.4f))
    drawLine(Color(0xFFFEF3C7), Offset(10.4f, 7.2f), Offset(13.6f, 7.2f), strokeWidth = 1.3f, cap = StrokeCap.Round)
    drawArc(
        color = Color(0xFF475569),
        startAngle = 0f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(6.4f, 7.4f),
        size = Size(11.2f, 8.2f),
        style = Stroke(width = 1.7f, cap = StrokeCap.Round)
    )
    drawLine(Color(0xFF475569), Offset(12f, 15.6f), Offset(12f, 19.6f), strokeWidth = 1.8f, cap = StrokeCap.Round)
    drawLine(Color(0xFF475569), Offset(8.6f, 19.8f), Offset(15.4f, 19.8f), strokeWidth = 1.8f, cap = StrokeCap.Round)
}

private fun DrawScope.drawLeisureMask(p: Path) {
    p.reset()
    p.moveTo(4.6f, 5.4f)
    p.lineTo(19.4f, 5.4f)
    p.lineTo(18.4f, 14.6f)
    p.cubicTo(17.8f, 18.4f, 15f, 20.4f, 12f, 20.4f)
    p.cubicTo(9f, 20.4f, 6.2f, 18.4f, 5.6f, 14.6f)
    p.close()
    drawPath(p, color = Color(0xFFEC4899))
    drawCircle(color = Color.White, radius = 1.3f, center = Offset(9f, 10.6f))
    drawCircle(color = Color.White, radius = 1.3f, center = Offset(15f, 10.6f))
    drawArc(
        color = Color(0xFFFDE047),
        startAngle = 15f,
        sweepAngle = 150f,
        useCenter = false,
        topLeft = Offset(8.5f, 12.6f),
        size = Size(7f, 4.2f),
        style = Stroke(width = 1.6f, cap = StrokeCap.Round)
    )
}

private fun DrawScope.drawScenicTicket(p: Path) {
    drawRoundRect(Color(0xFFF97316), topLeft = Offset(3.4f, 6.2f), size = Size(17.2f, 11.6f), cornerRadius = CornerRadius(2.6f, 2.6f))
    drawRoundRect(Color(0xFFFFEDD5), topLeft = Offset(7.2f, 8.4f), size = Size(9.6f, 7.2f), cornerRadius = CornerRadius(1.2f, 1.2f))
    drawCircle(color = Color.White, radius = 1.6f, center = Offset(3.4f, 12f))
    drawCircle(color = Color.White, radius = 1.6f, center = Offset(20.6f, 12f))
    p.reset()
}

private fun DrawScope.drawYogaLotus(p: Path) {
    drawCircle(color = Color(0xFF14B8A6), radius = 2.6f, center = Offset(12f, 6.2f))
    p.reset()
    p.moveTo(12f, 9.6f)
    p.lineTo(17.6f, 14.2f)
    p.lineTo(14.8f, 18.6f)
    p.lineTo(9.2f, 18.6f)
    p.lineTo(6.4f, 14.2f)
    p.close()
    drawPath(p, color = Color(0xFF2DD4BF))
    drawLine(Color(0xFF0D9488), Offset(5f, 18.6f), Offset(19f, 18.6f), strokeWidth = 2f, cap = StrokeCap.Round)
}

private fun DrawScope.drawCameraSnap() {
    drawRoundRect(Color(0xFF64748B), topLeft = Offset(8.4f, 4.4f), size = Size(7.2f, 3.2f), cornerRadius = CornerRadius(1.2f, 1.2f))
    drawRoundRect(Color(0xFF334155), topLeft = Offset(3.6f, 6.8f), size = Size(16.8f, 12.6f), cornerRadius = CornerRadius(3f, 3f))
    drawCircle(color = Color(0xFF38BDF8), radius = 3.8f, center = Offset(12f, 13.1f))
    drawCircle(color = Color.White, radius = 1.5f, center = Offset(12f, 13.1f))
    drawCircle(color = Color(0xFFF43F5E), radius = 1.1f, center = Offset(17.4f, 9.4f))
}

private fun DrawScope.drawQrScanner() {
    drawRoundRect(Color(0xFF0EA5E9), topLeft = Offset(4.2f, 4.2f), size = Size(6f, 6f), cornerRadius = CornerRadius(1.2f, 1.2f), style = Stroke(width = 1.8f))
    drawRoundRect(Color(0xFF0EA5E9), topLeft = Offset(13.8f, 4.2f), size = Size(6f, 6f), cornerRadius = CornerRadius(1.2f, 1.2f), style = Stroke(width = 1.8f))
    drawRoundRect(Color(0xFF0EA5E9), topLeft = Offset(4.2f, 13.8f), size = Size(6f, 6f), cornerRadius = CornerRadius(1.2f, 1.2f), style = Stroke(width = 1.8f))
    drawRoundRect(Color(0xFFF59E0B), topLeft = Offset(14.2f, 14.2f), size = Size(5.2f, 5.2f), cornerRadius = CornerRadius(1.2f, 1.2f))
    drawLine(Color(0xFFEF4444), Offset(3.5f, 12f), Offset(20.5f, 12f), strokeWidth = 1.5f, cap = StrokeCap.Round)
}

private fun DrawScope.drawHeartMonitor(p: Path) {
    drawCuteHeart(p, Color(0xFFEF4444), Color(0xFFFDA4AF))
    p.reset()
    p.moveTo(5.6f, 12f)
    p.lineTo(9.2f, 12f)
    p.lineTo(10.8f, 9f)
    p.lineTo(13.2f, 15f)
    p.lineTo(14.8f, 12f)
    p.lineTo(18.4f, 12f)
    drawPath(p, color = Color.White, style = Stroke(width = 1.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

private fun DrawScope.drawPetClinic(p: Path) {
    rotate(-45f, pivot = Offset(12f, 12f)) {
        drawRoundRect(Color(0xFFFB923C), topLeft = Offset(3.8f, 8.2f), size = Size(16.4f, 7.6f), cornerRadius = CornerRadius(3.8f, 3.8f))
        drawRoundRect(Color(0xFFFFEDD5), topLeft = Offset(8.4f, 8.8f), size = Size(7.2f, 6.4f), cornerRadius = CornerRadius(1.2f, 1.2f))
    }
    drawCircle(color = Color(0xFFEF4444), radius = 1.4f, center = Offset(12f, 12f))
    p.reset()
}

private fun DrawScope.drawMindHeart(p: Path) {
    drawCircle(color = Color(0xFF8B5CF6), radius = 7.6f, center = Offset(12f, 11f))
    drawRoundRect(Color(0xFF8B5CF6), topLeft = Offset(9f, 16.5f), size = Size(6f, 4f), cornerRadius = CornerRadius(1.5f, 1.5f))
    p.reset()
    p.moveTo(12f, 15.6f)
    p.cubicTo(7.4f, 12.6f, 7.2f, 8.6f, 9.6f, 7.4f)
    p.cubicTo(11f, 6.8f, 12f, 8.2f, 12f, 8.8f)
    p.cubicTo(12f, 8.2f, 13f, 6.8f, 14.4f, 7.4f)
    p.cubicTo(16.8f, 8.6f, 16.6f, 12.6f, 12f, 15.6f)
    p.close()
    drawPath(p, color = Color(0xFFFDE047))
}

private fun DrawScope.drawEyeCare() {
    drawOval(color = Color(0xFFE0F2FE), topLeft = Offset(3.4f, 6.8f), size = Size(17.2f, 10.4f))
    drawOval(color = Color(0xFF0284C7), topLeft = Offset(3.4f, 6.8f), size = Size(17.2f, 10.4f), style = Stroke(width = 1.6f))
    drawCircle(color = Color(0xFF0284C7), radius = 3.4f, center = Offset(12f, 12f))
    drawCircle(color = Color.White, radius = 1.3f, center = Offset(11f, 11f))
}

private fun DrawScope.drawTravelCompass(p: Path) {
    drawCircle(color = Color(0xFF2563EB), radius = 9.2f, center = Offset(12f, 12f))
    drawCircle(color = Color(0xFFDBEAFE), radius = 6.8f, center = Offset(12f, 12f))
    p.reset()
    p.moveTo(15.2f, 8.8f)
    p.lineTo(13.2f, 13.2f)
    p.lineTo(8.8f, 15.2f)
    p.lineTo(10.8f, 10.8f)
    p.close()
    drawPath(p, color = Color(0xFFEF4444))
    drawCircle(color = Color(0xFF1E293B), radius = 1.1f, center = Offset(12f, 12f))
}

private fun DrawScope.drawSouvenirPack(p: Path) {
    drawArc(
        color = Color(0xFFB45309),
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(8.6f, 3.4f),
        size = Size(6.8f, 4.6f),
        style = Stroke(width = 1.7f, cap = StrokeCap.Round)
    )
    drawRoundRect(Color(0xFF0EA5E9), topLeft = Offset(5.4f, 6.2f), size = Size(13.2f, 14f), cornerRadius = CornerRadius(4f, 4f))
    drawRoundRect(Color(0xFFFDE047), topLeft = Offset(7.8f, 12.6f), size = Size(8.4f, 5.4f), cornerRadius = CornerRadius(1.8f, 1.8f))
    p.reset()
}

private fun DrawScope.drawGlobeWorld() {
    drawCircle(color = Color(0xFF0EA5E9), radius = 9f, center = Offset(12f, 12f))
    drawOval(color = Color.White, topLeft = Offset(8.2f, 3.4f), size = Size(7.6f, 17.2f), style = Stroke(width = 1.4f))
    drawLine(Color.White, Offset(3.5f, 12f), Offset(20.5f, 12f), strokeWidth = 1.4f)
    drawLine(Color(0xFFBAE6FD), Offset(5f, 7.6f), Offset(19f, 7.6f), strokeWidth = 1.2f)
    drawLine(Color(0xFFBAE6FD), Offset(5f, 16.4f), Offset(19f, 16.4f), strokeWidth = 1.2f)
}

private fun DrawScope.drawCourseBoard(p: Path) {
    drawRoundRect(Color(0xFF0D9488), topLeft = Offset(3.8f, 4.6f), size = Size(16.4f, 11.4f), cornerRadius = CornerRadius(2.2f, 2.2f))
    drawRoundRect(Color(0xFF99F6E4), topLeft = Offset(5.6f, 6.4f), size = Size(12.8f, 7.8f), cornerRadius = CornerRadius(1f, 1f))
    drawLine(Color(0xFF0F766E), Offset(7.4f, 9f), Offset(14.6f, 9f), strokeWidth = 1.5f, cap = StrokeCap.Round)
    drawLine(Color(0xFFF59E0B), Offset(7.4f, 11.8f), Offset(16.2f, 11.8f), strokeWidth = 1.5f, cap = StrokeCap.Round)
    drawLine(Color(0xFF64748B), Offset(9.2f, 16f), Offset(7.4f, 20f), strokeWidth = 1.6f, cap = StrokeCap.Round)
    drawLine(Color(0xFF64748B), Offset(14.8f, 16f), Offset(16.6f, 20f), strokeWidth = 1.6f, cap = StrokeCap.Round)
    p.reset()
}

private fun DrawScope.drawStationery(p: Path) {
    rotate(-38f, pivot = Offset(12f, 12f)) {
        drawRoundRect(Color(0xFFFBBF24), topLeft = Offset(9.6f, 5.2f), size = Size(4.8f, 11.6f), cornerRadius = CornerRadius(1f, 1f))
        drawRoundRect(Color(0xFFFB7185), topLeft = Offset(9.6f, 3.4f), size = Size(4.8f, 2.6f), cornerRadius = CornerRadius(1f, 1f))
        p.reset()
        p.moveTo(9.6f, 16.8f)
        p.lineTo(14.4f, 16.8f)
        p.lineTo(12f, 21f)
        p.close()
        drawPath(p, color = Color(0xFFFEF3C7))
    }
}

private fun DrawScope.drawElderlyCare(p: Path) {
    // 孝敬长辈：银发发髻长辈肖像（圆眼镜+暖微笑）+ 珊瑚红小爱心
    drawCircle(color = Color(0xFFCBD5E1), radius = 2.4f, center = Offset(11.4f, 5.2f))
    drawCircle(color = Color(0xFF94A3B8), radius = 2.4f, center = Offset(11.4f, 5.2f), style = Stroke(width = 0.8f))
    drawRoundRect(Color(0xFFF59E0B), topLeft = Offset(5.2f, 16.6f), size = Size(12.4f, 4.4f), cornerRadius = CornerRadius(2.2f, 2.2f))
    drawArc(color = Color(0xFFFEF3C7), startAngle = 0f, sweepAngle = 180f, useCenter = true, topLeft = Offset(9f, 15.8f), size = Size(4.8f, 3.2f))
    drawCircle(color = Color(0xFFE2E8F0), radius = 5.6f, center = Offset(11.4f, 11.8f))
    drawCircle(color = Color(0xFF94A3B8), radius = 5.6f, center = Offset(11.4f, 11.8f), style = Stroke(width = 0.8f))
    drawCircle(color = Color(0xFFFED7AA), radius = 4.6f, center = Offset(11.4f, 11.8f))
    drawArc(color = Color(0xFFCBD5E1), startAngle = 180f, sweepAngle = 180f, useCenter = true, topLeft = Offset(6.8f, 7.2f), size = Size(9.2f, 4.2f))
    drawCircle(color = Color(0xFFFDA4AF), radius = 0.9f, center = Offset(8.3f, 13.1f))
    drawCircle(color = Color(0xFFFDA4AF), radius = 0.9f, center = Offset(14.5f, 13.1f))
    drawCircle(color = Color(0xFF475569), radius = 1.55f, center = Offset(9.3f, 11.7f), style = Stroke(width = 1.1f))
    drawCircle(color = Color(0xFF475569), radius = 1.55f, center = Offset(13.5f, 11.7f), style = Stroke(width = 1.1f))
    drawLine(Color(0xFF475569), Offset(10.85f, 11.7f), Offset(11.95f, 11.7f), strokeWidth = 1.1f)
    drawCircle(color = Color(0xFF1E293B), radius = 0.55f, center = Offset(9.3f, 11.7f))
    drawCircle(color = Color(0xFF1E293B), radius = 0.55f, center = Offset(13.5f, 11.7f))
    drawArc(
        color = Color(0xFF9A3412),
        startAngle = 18f,
        sweepAngle = 144f,
        useCenter = false,
        topLeft = Offset(9.5f, 12.8f),
        size = Size(3.8f, 2.4f),
        style = Stroke(width = 1.2f, cap = StrokeCap.Round)
    )
    p.reset()
    p.moveTo(18.8f, 9.4f)
    p.cubicTo(16.1f, 7.6f, 16f, 5.2f, 17.4f, 4.4f)
    p.cubicTo(18.2f, 3.9f, 18.8f, 4.7f, 18.8f, 5.2f)
    p.cubicTo(18.8f, 4.7f, 19.4f, 3.9f, 20.2f, 4.4f)
    p.cubicTo(21.6f, 5.2f, 21.5f, 7.6f, 18.8f, 9.4f)
    p.close()
    drawPath(p, color = Color(0xFFFB7185))
}

private fun DrawScope.drawSocialEnvelope(p: Path) {
    drawRoundRect(Color(0xFFEC4899), topLeft = Offset(3.8f, 6f), size = Size(16.4f, 12f), cornerRadius = CornerRadius(2.6f, 2.6f))
    p.reset()
    p.moveTo(4.4f, 6.8f)
    p.lineTo(12f, 12.6f)
    p.lineTo(19.6f, 6.8f)
    drawPath(p, color = Color(0xFFFCE7F3), style = Stroke(width = 1.6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    drawCircle(color = Color(0xFFFDE047), radius = 2.1f, center = Offset(12f, 12.6f))
}

private fun DrawScope.drawGreenRedPacket(p: Path) {
    drawCircle(color = Color(0xFF07C160), radius = 9.4f, center = Offset(12f, 12f))
    drawRoundRect(Color(0xFFEF4444), topLeft = Offset(7.8f, 6.4f), size = Size(8.4f, 11.2f), cornerRadius = CornerRadius(1.8f, 1.8f))
    drawCircle(color = Color(0xFFFDE047), radius = 2f, center = Offset(12f, 11f))
    p.reset()
}

private fun DrawScope.drawSocialGroup() {
    drawCircle(color = Color(0xFF38BDF8), radius = 2.5f, center = Offset(7.6f, 8.8f))
    drawCircle(color = Color(0xFFEC4899), radius = 3f, center = Offset(12f, 7.6f))
    drawCircle(color = Color(0xFFF59E0B), radius = 2.5f, center = Offset(16.4f, 8.8f))
    drawRoundRect(Color(0xFF0EA5E9), topLeft = Offset(4.2f, 12.4f), size = Size(6.4f, 6f), cornerRadius = CornerRadius(2.6f, 2.6f))
    drawRoundRect(Color(0xFFF59E0B), topLeft = Offset(13.4f, 12.4f), size = Size(6.4f, 6f), cornerRadius = CornerRadius(2.6f, 2.6f))
    drawRoundRect(Color(0xFFF43F5E), topLeft = Offset(8.2f, 11.4f), size = Size(7.6f, 7.6f), cornerRadius = CornerRadius(3f, 3f))
}

private fun DrawScope.drawPersonAvatar() {
    drawCircle(color = Color(0xFF2AA876), radius = 9f, center = Offset(12f, 12f))
    drawCircle(color = Color.White, radius = 3.2f, center = Offset(12f, 9f))
    drawRoundRect(Color.White, topLeft = Offset(6.8f, 13.4f), size = Size(10.4f, 5f), cornerRadius = CornerRadius(2.5f, 2.5f))
}

private fun DrawScope.drawPetFoodBowl(p: Path) {
    drawCircle(color = Color(0xFFFBBF24), radius = 2.1f, center = Offset(9.4f, 9.6f))
    drawCircle(color = Color(0xFFFBBF24), radius = 2.1f, center = Offset(12f, 8.8f))
    drawCircle(color = Color(0xFFFBBF24), radius = 2.1f, center = Offset(14.6f, 9.6f))
    p.reset()
    p.moveTo(4.6f, 12.2f)
    p.lineTo(19.4f, 12.2f)
    p.lineTo(20.6f, 19.2f)
    p.lineTo(3.4f, 19.2f)
    p.close()
    drawPath(p, color = Color(0xFFEA580C))
    drawRoundRect(Color.White, topLeft = Offset(8.8f, 14.8f), size = Size(6.4f, 2f), cornerRadius = CornerRadius(1f, 1f))
}

private fun DrawScope.drawPetSupplies(p: Path) {
    // 宠物用品：青绿项圈+金色铃铛 + 象牙白小狗骨头
    drawOval(
        color = Color(0xFF0D9488),
        topLeft = Offset(5.6f, 3.6f),
        size = Size(12.8f, 6.2f),
        style = Stroke(width = 2.6f)
    )
    drawRoundRect(Color(0xFFF59E0B), topLeft = Offset(10.6f, 9.1f), size = Size(2.8f, 1.6f), cornerRadius = CornerRadius(0.6f, 0.6f))
    drawCircle(color = Color(0xFFF59E0B), radius = 2.4f, center = Offset(12f, 11.8f))
    drawCircle(color = Color(0xFFFDE047), radius = 1.75f, center = Offset(12f, 11.6f))
    drawLine(Color(0xFF92400E), Offset(10.8f, 12.6f), Offset(13.2f, 12.6f), strokeWidth = 1.1f, cap = StrokeCap.Round)
    drawCircle(color = Color.White, radius = 0.55f, center = Offset(11.3f, 11.1f))
    drawCircle(color = Color(0xFFC08448), radius = 2.4f, center = Offset(5.8f, 15.8f))
    drawCircle(color = Color(0xFFC08448), radius = 2.4f, center = Offset(5.8f, 18.6f))
    drawCircle(color = Color(0xFFC08448), radius = 2.4f, center = Offset(18.2f, 15.8f))
    drawCircle(color = Color(0xFFC08448), radius = 2.4f, center = Offset(18.2f, 18.6f))
    drawRoundRect(Color(0xFFC08448), topLeft = Offset(5.8f, 15.2f), size = Size(12.4f, 4f), cornerRadius = CornerRadius(1.2f, 1.2f))
    drawCircle(color = Color(0xFFFEF7DC), radius = 1.8f, center = Offset(5.8f, 15.8f))
    drawCircle(color = Color(0xFFFEF7DC), radius = 1.8f, center = Offset(5.8f, 18.6f))
    drawCircle(color = Color(0xFFFEF7DC), radius = 1.8f, center = Offset(18.2f, 15.8f))
    drawCircle(color = Color(0xFFFEF7DC), radius = 1.8f, center = Offset(18.2f, 18.6f))
    drawRoundRect(Color(0xFFFEF7DC), topLeft = Offset(5.8f, 15.8f), size = Size(12.4f, 2.8f), cornerRadius = CornerRadius(1f, 1f))
    p.reset()
}

private fun DrawScope.drawBabyStroller() {
    drawArc(
        color = Color(0xFF38BDF8),
        startAngle = 180f,
        sweepAngle = 90f,
        useCenter = true,
        topLeft = Offset(5.2f, 5.2f),
        size = Size(12.8f, 12.8f)
    )
    drawRoundRect(Color(0xFF0EA5E9), topLeft = Offset(5.2f, 11.4f), size = Size(12.8f, 5f), cornerRadius = CornerRadius(2.4f, 2.4f))
    drawLine(Color(0xFF64748B), Offset(18f, 11.6f), Offset(20.6f, 7.6f), strokeWidth = 1.8f, cap = StrokeCap.Round)
    drawCircle(color = Color(0xFF1E293B), radius = 2.1f, center = Offset(7.8f, 18.6f))
    drawCircle(color = Color(0xFF1E293B), radius = 2.1f, center = Offset(15.6f, 18.6f))
}

private fun DrawScope.drawKidsPinwheel(p: Path) {
    drawLine(Color(0xFF92400E), Offset(12f, 11.6f), Offset(12f, 20.6f), strokeWidth = 1.8f, cap = StrokeCap.Round)
    drawCircle(color = Color(0xFFEF4444), radius = 3.2f, center = Offset(12f, 7.2f))
    drawCircle(color = Color(0xFF38BDF8), radius = 3.2f, center = Offset(16.4f, 11.6f))
    drawCircle(color = Color(0xFFFACC15), radius = 3.2f, center = Offset(12f, 16f))
    drawCircle(color = Color(0xFF22C55E), radius = 3.2f, center = Offset(7.6f, 11.6f))
    drawCircle(color = Color.White, radius = 1.6f, center = Offset(12f, 11.6f))
    p.reset()
}

private fun DrawScope.drawEarlyBlocks() {
    drawRoundRect(Color(0xFFF59E0B), topLeft = Offset(7.6f, 4.4f), size = Size(8.8f, 7.2f), cornerRadius = CornerRadius(1.8f, 1.8f))
    drawRoundRect(Color(0xFF38BDF8), topLeft = Offset(4f, 12.2f), size = Size(7.4f, 7.4f), cornerRadius = CornerRadius(1.8f, 1.8f))
    drawRoundRect(Color(0xFFEC4899), topLeft = Offset(12.6f, 12.2f), size = Size(7.4f, 7.4f), cornerRadius = CornerRadius(1.8f, 1.8f))
    drawCircle(color = Color.White, radius = 1.4f, center = Offset(12f, 8f))
    drawCircle(color = Color.White, radius = 1.2f, center = Offset(7.7f, 15.9f))
    drawCircle(color = Color.White, radius = 1.2f, center = Offset(16.3f, 15.9f))
}

private fun DrawScope.drawFinanceVault() {
    drawRoundRect(Color(0xFF334155), topLeft = Offset(3.8f, 4.4f), size = Size(16.4f, 15.2f), cornerRadius = CornerRadius(3f, 3f))
    drawRoundRect(Color(0xFF475569), topLeft = Offset(5.6f, 6.2f), size = Size(12.8f, 11.6f), cornerRadius = CornerRadius(1.8f, 1.8f))
    drawCircle(color = Color(0xFFFACC15), radius = 3.4f, center = Offset(12f, 12f))
    drawCircle(color = Color(0xFF92400E), radius = 1.2f, center = Offset(12f, 12f))
}

private fun DrawScope.drawCommercialShield(p: Path) {
    p.reset()
    p.moveTo(12f, 3.5f)
    p.lineTo(19f, 6.5f)
    p.lineTo(19f, 12.2f)
    p.cubicTo(19f, 16.6f, 15.6f, 19.6f, 12f, 20.8f)
    p.cubicTo(8.4f, 19.6f, 5f, 16.6f, 5f, 12.2f)
    p.lineTo(5f, 6.5f)
    p.close()
    drawPath(p, color = Color(0xFF2563EB))
    drawLine(Color(0xFFFDE047), Offset(9.2f, 12.2f), Offset(11.4f, 14.4f), strokeWidth = 2f, cap = StrokeCap.Round)
    drawLine(Color(0xFFFDE047), Offset(11.4f, 14.4f), Offset(15.2f, 9.6f), strokeWidth = 2f, cap = StrokeCap.Round)
}

private fun DrawScope.drawVerifiedSeal(p: Path) {
    drawCircle(color = Color(0xFF16A34A), radius = 8.8f, center = Offset(12f, 12f))
    drawCircle(color = Color(0xFFBBF7D0), radius = 6.4f, center = Offset(12f, 12f), style = Stroke(width = 1.4f))
    drawLine(Color.White, Offset(8.8f, 12.2f), Offset(11.2f, 14.6f), strokeWidth = 2f, cap = StrokeCap.Round)
    drawLine(Color.White, Offset(11.2f, 14.6f), Offset(15.6f, 9.4f), strokeWidth = 2f, cap = StrokeCap.Round)
    p.reset()
}

private fun DrawScope.drawTaxCalculator() {
    drawRoundRect(Color(0xFF0D9488), topLeft = Offset(5.2f, 3.6f), size = Size(13.6f, 16.8f), cornerRadius = CornerRadius(2.6f, 2.6f))
    drawRoundRect(Color(0xFFCCFBF1), topLeft = Offset(7f, 5.4f), size = Size(10f, 3.8f), cornerRadius = CornerRadius(1f, 1f))
    drawCircle(color = Color(0xFFFDE047), radius = 1.2f, center = Offset(8.4f, 12.2f))
    drawCircle(color = Color.White, radius = 1.2f, center = Offset(12f, 12.2f))
    drawCircle(color = Color.White, radius = 1.2f, center = Offset(15.6f, 12.2f))
    drawCircle(color = Color.White, radius = 1.2f, center = Offset(8.4f, 16.2f))
    drawCircle(color = Color.White, radius = 1.2f, center = Offset(12f, 16.2f))
    drawCircle(color = Color(0xFFF97316), radius = 1.2f, center = Offset(15.6f, 16.2f))
}

private fun DrawScope.drawInsuranceClaim(p: Path) {
    // 保险理赔：保单文件 + 赔付金币 + 右下翡翠绿核验对勾盾牌
    drawRoundRect(Color(0xFF3B82F6), topLeft = Offset(3.4f, 3.4f), size = Size(12.2f, 16f), cornerRadius = CornerRadius(2.4f, 2.4f))
    drawRoundRect(Color.White, topLeft = Offset(4.8f, 5f), size = Size(9.4f, 12.8f), cornerRadius = CornerRadius(1.2f, 1.2f))
    drawRoundRect(Color(0xFF93C5FD), topLeft = Offset(6.2f, 6.4f), size = Size(5.6f, 1.6f), cornerRadius = CornerRadius(0.8f, 0.8f))
    drawLine(Color(0xFF2563EB), Offset(6.2f, 9.8f), Offset(12.4f, 9.8f), strokeWidth = 1.4f, cap = StrokeCap.Round)
    drawLine(Color(0xFF2563EB), Offset(6.2f, 12.6f), Offset(11.6f, 12.6f), strokeWidth = 1.4f, cap = StrokeCap.Round)
    drawLine(Color(0xFF60A5FA), Offset(6.2f, 15.4f), Offset(10.8f, 15.4f), strokeWidth = 1.4f, cap = StrokeCap.Round)
    drawCircle(color = Color(0xFFF59E0B), radius = 3.2f, center = Offset(17.4f, 6.8f))
    drawCircle(color = Color(0xFFFDE047), radius = 2.2f, center = Offset(17.4f, 6.8f))
    drawLine(Color(0xFFB45309), Offset(17.4f, 5.5f), Offset(17.4f, 8.1f), strokeWidth = 1.1f, cap = StrokeCap.Round)
    p.reset()
    p.moveTo(17f, 11.4f)
    p.lineTo(21f, 13f)
    p.lineTo(21f, 16.2f)
    p.cubicTo(21f, 18.8f, 19f, 20.4f, 17f, 21.1f)
    p.cubicTo(15f, 20.4f, 13f, 18.8f, 13f, 16.2f)
    p.lineTo(13f, 13f)
    p.close()
    drawPath(p, color = Color.White, style = Stroke(width = 1.2f, join = StrokeJoin.Round))
    drawPath(p, color = Color(0xFF10B981))
    drawLine(Color.White, Offset(15.1f, 16.3f), Offset(16.5f, 17.7f), strokeWidth = 1.6f, cap = StrokeCap.Round)
    drawLine(Color.White, Offset(16.5f, 17.7f), Offset(19f, 14.8f), strokeWidth = 1.6f, cap = StrokeCap.Round)
}

private fun DrawScope.drawCandlestick() {
    drawRoundRect(Color(0xFF1E293B), topLeft = Offset(3.4f, 4f), size = Size(17.2f, 16f), cornerRadius = CornerRadius(3.4f, 3.4f))
    drawLine(Color(0xFFEF4444), Offset(8f, 6.2f), Offset(8f, 17.8f), strokeWidth = 1.3f, cap = StrokeCap.Round)
    drawRoundRect(Color(0xFFEF4444), topLeft = Offset(6.6f, 8.6f), size = Size(2.8f, 6f), cornerRadius = CornerRadius(0.8f, 0.8f))
    drawLine(Color(0xFF22C55E), Offset(12f, 7f), Offset(12f, 17f), strokeWidth = 1.3f, cap = StrokeCap.Round)
    drawRoundRect(Color(0xFF22C55E), topLeft = Offset(10.6f, 9.8f), size = Size(2.8f, 4.6f), cornerRadius = CornerRadius(0.8f, 0.8f))
    drawLine(Color(0xFFEF4444), Offset(16f, 5.8f), Offset(16f, 15.8f), strokeWidth = 1.3f, cap = StrokeCap.Round)
    drawRoundRect(Color(0xFFEF4444), topLeft = Offset(14.6f, 7.2f), size = Size(2.8f, 5.6f), cornerRadius = CornerRadius(0.8f, 0.8f))
}

private fun DrawScope.drawPerformanceBars() {
    drawRoundRect(Color(0xFFE0F2FE), topLeft = Offset(3.5f, 4f), size = Size(17f, 16f), cornerRadius = CornerRadius(3.5f, 3.5f))
    drawRoundRect(Color(0xFF38BDF8), topLeft = Offset(6.2f, 12.6f), size = Size(2.8f, 5.2f), cornerRadius = CornerRadius(1f, 1f))
    drawRoundRect(Color(0xFFF59E0B), topLeft = Offset(10.6f, 9.6f), size = Size(2.8f, 8.2f), cornerRadius = CornerRadius(1f, 1f))
    drawRoundRect(Color(0xFF16A34A), topLeft = Offset(15f, 6.8f), size = Size(2.8f, 11f), cornerRadius = CornerRadius(1f, 1f))
}

private fun DrawScope.drawDividendPie() {
    drawArc(color = Color(0xFF0EA5E9), startAngle = -90f, sweepAngle = 150f, useCenter = true, topLeft = Offset(4f, 4f), size = Size(16f, 16f))
    drawArc(color = Color(0xFFF59E0B), startAngle = 60f, sweepAngle = 110f, useCenter = true, topLeft = Offset(4f, 4f), size = Size(16f, 16f))
    drawArc(color = Color(0xFF22C55E), startAngle = 170f, sweepAngle = 100f, useCenter = true, topLeft = Offset(4f, 4f), size = Size(16f, 16f))
}

private fun DrawScope.drawLostAlert(p: Path) {
    p.reset()
    p.moveTo(12f, 3.8f)
    p.lineTo(20.6f, 19.2f)
    p.lineTo(3.4f, 19.2f)
    p.close()
    drawPath(p, color = Color(0xFFF59E0B))
    drawLine(Color.White, Offset(12f, 9.2f), Offset(12f, 14f), strokeWidth = 2f, cap = StrokeCap.Round)
    drawCircle(color = Color.White, radius = 1.2f, center = Offset(12f, 16.8f))
}

private fun DrawScope.drawSideHustleBag() {
    drawRoundRect(Color(0xFF0EA5E9), topLeft = Offset(4.4f, 6.2f), size = Size(15.2f, 13.6f), cornerRadius = CornerRadius(3.4f, 3.4f))
    drawRoundRect(Color(0xFF38BDF8), topLeft = Offset(4.4f, 6.2f), size = Size(15.2f, 6.4f), cornerRadius = CornerRadius(3f, 3f))
    drawRoundRect(Color(0xFFFDE047), topLeft = Offset(10.4f, 11.4f), size = Size(3.2f, 2.6f), cornerRadius = CornerRadius(0.8f, 0.8f))
    drawLine(Color(0xFF0284C7), Offset(8.6f, 4.2f), Offset(15.4f, 4.2f), strokeWidth = 1.8f, cap = StrokeCap.Round)
}

private fun DrawScope.drawOvertimeClock() {
    drawCircle(color = Color(0xFF16A34A), radius = 8.4f, center = Offset(12f, 12.6f))
    drawCircle(color = Color.White, radius = 6f, center = Offset(12f, 12.6f))
    drawLine(Color(0xFFF97316), Offset(12f, 12.6f), Offset(12f, 8.8f), strokeWidth = 1.6f, cap = StrokeCap.Round)
    drawLine(Color(0xFFF97316), Offset(12f, 12.6f), Offset(15f, 14f), strokeWidth = 1.6f, cap = StrokeCap.Round)
    drawCircle(color = Color(0xFFF59E0B), radius = 1.8f, center = Offset(6f, 6f))
    drawCircle(color = Color(0xFFF59E0B), radius = 1.8f, center = Offset(18f, 6f))
}

private fun DrawScope.drawGoldTrophy(p: Path) {
    drawRoundRect(Color(0xFFF59E0B), topLeft = Offset(7.2f, 4.2f), size = Size(9.6f, 8.6f), cornerRadius = CornerRadius(2.6f, 2.6f))
    drawArc(color = Color(0xFFD97706), startAngle = 90f, sweepAngle = 180f, useCenter = false, topLeft = Offset(4.4f, 5.4f), size = Size(4.6f, 5.2f), style = Stroke(width = 1.5f))
    drawArc(color = Color(0xFFD97706), startAngle = -90f, sweepAngle = 180f, useCenter = false, topLeft = Offset(15f, 5.4f), size = Size(4.6f, 5.2f), style = Stroke(width = 1.5f))
    drawRect(Color(0xFFD97706), topLeft = Offset(10.6f, 12.8f), size = Size(2.8f, 4f))
    drawRoundRect(Color(0xFF92400E), topLeft = Offset(7.6f, 16.8f), size = Size(8.8f, 3f), cornerRadius = CornerRadius(1.2f, 1.2f))
    p.reset()
}

private fun DrawScope.drawBonusStar(p: Path) {
    drawCircle(color = Color(0xFF15803D), radius = 9.2f, center = Offset(12f, 12f))
    p.reset()
    p.moveTo(12f, 5.4f)
    p.lineTo(13.8f, 9.8f)
    p.lineTo(18.4f, 10.1f)
    p.lineTo(14.8f, 13.1f)
    p.lineTo(16f, 17.6f)
    p.lineTo(12f, 15.1f)
    p.lineTo(8f, 17.6f)
    p.lineTo(9.2f, 13.1f)
    p.lineTo(5.6f, 10.1f)
    p.lineTo(10.2f, 9.8f)
    p.close()
    drawPath(p, color = Color(0xFFFDE047))
}

private fun DrawScope.drawFestivalGift() {
    drawCircle(color = Color(0xFFEF4444), radius = 1.7f, center = Offset(12f, 5.4f))
    drawRoundRect(Color(0xFFFCE7F3), topLeft = Offset(6.8f, 7.6f), size = Size(10.4f, 4.8f), cornerRadius = CornerRadius(1.8f, 1.8f))
    drawRoundRect(Color(0xFFF43F5E), topLeft = Offset(4.4f, 12.4f), size = Size(15.2f, 6.8f), cornerRadius = CornerRadius(2.2f, 2.2f))
    drawLine(Color(0xFFFDE047), Offset(6.4f, 15.6f), Offset(17.6f, 15.6f), strokeWidth = 1.6f, cap = StrokeCap.Round)
}

private fun DrawScope.drawPiggyBank() {
    drawOval(color = Color(0xFFF472B6), topLeft = Offset(4.2f, 8.2f), size = Size(15f, 10.8f))
    drawCircle(color = Color(0xFFEC4899), radius = 2.4f, center = Offset(7.2f, 8f))
    drawRoundRect(Color(0xFFFB7185), topLeft = Offset(17.8f, 12f), size = Size(2.8f, 3.8f), cornerRadius = CornerRadius(1.2f, 1.2f))
    drawCircle(color = Color(0xFFFDE047), radius = 2.4f, center = Offset(11.6f, 5.4f))
    drawLine(Color(0xFFEC4899), Offset(7.6f, 18.2f), Offset(7.6f, 20.4f), strokeWidth = 2f, cap = StrokeCap.Round)
    drawLine(Color(0xFFEC4899), Offset(15.6f, 18.2f), Offset(15.6f, 20.4f), strokeWidth = 2f, cap = StrokeCap.Round)
    drawCircle(color = Color(0xFF1E293B), radius = 0.9f, center = Offset(15.6f, 11.4f))
}

private fun DrawScope.drawFxCoin() {
    drawCircle(color = Color(0xFFF59E0B), radius = 9.2f, center = Offset(12f, 12f))
    drawCircle(color = Color(0xFFFDE047), radius = 6.8f, center = Offset(12f, 12f))
    drawLine(Color(0xFFB45309), Offset(12f, 7.8f), Offset(12f, 16.2f), strokeWidth = 1.6f, cap = StrokeCap.Round)
    drawLine(Color(0xFFB45309), Offset(9.4f, 10.6f), Offset(14.6f, 10.6f), strokeWidth = 1.4f, cap = StrokeCap.Round)
    drawLine(Color(0xFFB45309), Offset(9.4f, 13.4f), Offset(14.6f, 13.4f), strokeWidth = 1.4f, cap = StrokeCap.Round)
}

private fun DrawScope.drawLoyaltyCard(p: Path) {
    drawPriceTag(p)
    drawCircle(color = Color(0xFFE11D48), radius = 2.6f, center = Offset(10.8f, 12.6f))
}

private fun DrawScope.drawCouponTicket(p: Path) {
    drawRoundRect(Color(0xFFEF4444), topLeft = Offset(3.4f, 6.2f), size = Size(17.2f, 11.6f), cornerRadius = CornerRadius(2.6f, 2.6f))
    drawCircle(color = Color(0xFFFDE047), radius = 3.2f, center = Offset(12f, 12f))
    drawCircle(color = Color.White, radius = 1.5f, center = Offset(3.4f, 12f))
    drawCircle(color = Color.White, radius = 1.5f, center = Offset(20.6f, 12f))
    p.reset()
}

private fun DrawScope.drawHousingFundCard() {
    drawRoundRect(Color(0xFF006874), topLeft = Offset(3.4f, 5.4f), size = Size(17.2f, 13.2f), cornerRadius = CornerRadius(2.8f, 2.8f))
    drawCircle(color = Color(0xFF99F6E4), radius = 2.4f, center = Offset(8.4f, 11.2f))
    drawLine(Color.White, Offset(12.6f, 9.6f), Offset(18f, 9.6f), strokeWidth = 1.6f, cap = StrokeCap.Round)
    drawLine(Color(0xFFFDE047), Offset(12.6f, 13.4f), Offset(17.2f, 13.4f), strokeWidth = 1.6f, cap = StrokeCap.Round)
}

private fun DrawScope.drawCashRegister() {
    drawRoundRect(Color(0xFFEA580C), topLeft = Offset(5.2f, 4.2f), size = Size(13.6f, 15.8f), cornerRadius = CornerRadius(2.8f, 2.8f))
    drawRoundRect(Color(0xFFE0F2FE), topLeft = Offset(7.2f, 6.2f), size = Size(9.6f, 4.8f), cornerRadius = CornerRadius(1.2f, 1.2f))
    drawRoundRect(Color(0xFFFDE047), topLeft = Offset(7.4f, 13f), size = Size(9.2f, 2.6f), cornerRadius = CornerRadius(0.8f, 0.8f))
    drawLine(Color.White, Offset(8f, 17.6f), Offset(16f, 17.6f), strokeWidth = 1.6f, cap = StrokeCap.Round)
}

private fun DrawScope.drawRentApartment() {
    drawRoundRect(Color(0xFF0284C7), topLeft = Offset(5.4f, 3.8f), size = Size(13.2f, 16.4f), cornerRadius = CornerRadius(2.4f, 2.4f))
    drawRoundRect(Color(0xFFFDE047), topLeft = Offset(7.8f, 6.4f), size = Size(2.6f, 2.4f), cornerRadius = CornerRadius(0.6f, 0.6f))
    drawRoundRect(Color(0xFFFDE047), topLeft = Offset(13.6f, 6.4f), size = Size(2.6f, 2.4f), cornerRadius = CornerRadius(0.6f, 0.6f))
    drawRoundRect(Color(0xFFBAE6FD), topLeft = Offset(7.8f, 10.6f), size = Size(2.6f, 2.4f), cornerRadius = CornerRadius(0.6f, 0.6f))
    drawRoundRect(Color(0xFFBAE6FD), topLeft = Offset(13.6f, 10.6f), size = Size(2.6f, 2.4f), cornerRadius = CornerRadius(0.6f, 0.6f))
    drawRoundRect(Color(0xFFF97316), topLeft = Offset(10.2f, 15.6f), size = Size(3.6f, 4.6f), cornerRadius = CornerRadius(1f, 1f))
}

private fun DrawScope.drawMoreDots() {
    drawCircle(color = Color(0xFFCBD5E1), radius = 9.2f, center = Offset(12f, 12f))
    drawCircle(color = Color(0xFF475569), radius = 1.6f, center = Offset(7.2f, 12f))
    drawCircle(color = Color(0xFF475569), radius = 1.6f, center = Offset(12f, 12f))
    drawCircle(color = Color(0xFF475569), radius = 1.6f, center = Offset(16.8f, 12f))
}

private fun DrawScope.drawAllowanceTicket() {
    drawRoundRect(Color(0xFF16A34A), topLeft = Offset(3.4f, 5.6f), size = Size(17.2f, 12.8f), cornerRadius = CornerRadius(2.8f, 2.8f))
    drawRoundRect(Color(0xFFDCFCE7), topLeft = Offset(5.2f, 7.6f), size = Size(13.6f, 8.8f), cornerRadius = CornerRadius(1.6f, 1.6f))
    drawCircle(color = Color(0xFFF59E0B), radius = 2.6f, center = Offset(9.2f, 12f))
    drawLine(Color(0xFF15803D), Offset(13f, 10.4f), Offset(17f, 10.4f), strokeWidth = 1.5f, cap = StrokeCap.Round)
    drawLine(Color(0xFF15803D), Offset(13f, 13.6f), Offset(16.2f, 13.6f), strokeWidth = 1.5f, cap = StrokeCap.Round)
}

private fun DrawScope.drawBreakfastEgg(p: Path) {
    drawRoundRect(Color(0xFFFDE68A), topLeft = Offset(4f, 5.2f), size = Size(16f, 13.8f), cornerRadius = CornerRadius(3.8f, 3.8f))
    drawRoundRect(Color(0xFFD97706), topLeft = Offset(4f, 5.2f), size = Size(16f, 13.8f), cornerRadius = CornerRadius(3.8f, 3.8f), style = Stroke(width = 1.4f))
    p.reset()
    drawOval(color = Color.White, topLeft = Offset(6.8f, 7.8f), size = Size(10.4f, 9f))
    drawCircle(color = Color(0xFFF97316), radius = 2.8f, center = Offset(12f, 12.3f))
}

private fun DrawScope.drawLunchBento() {
    drawRoundRect(Color(0xFFDC2626), topLeft = Offset(3.4f, 5.2f), size = Size(17.2f, 13.6f), cornerRadius = CornerRadius(3f, 3f))
    drawRoundRect(Color.White, topLeft = Offset(5f, 6.8f), size = Size(6.4f, 10.4f), cornerRadius = CornerRadius(1.4f, 1.4f))
    drawRoundRect(Color(0xFFFDE047), topLeft = Offset(12.6f, 6.8f), size = Size(6.4f, 4.8f), cornerRadius = CornerRadius(1.2f, 1.2f))
    drawRoundRect(Color(0xFF4ADE80), topLeft = Offset(12.6f, 12.6f), size = Size(6.4f, 4.6f), cornerRadius = CornerRadius(1.2f, 1.2f))
    drawCircle(color = Color(0xFFEF4444), radius = 1.3f, center = Offset(8.2f, 12f))
}

private fun DrawScope.drawDinnerBowl(p: Path) {
    drawCircle(color = Color(0xFFFDE047), radius = 2.6f, center = Offset(17.8f, 6f))
    drawCircle(color = Color(0xFF1E293B), radius = 2.1f, center = Offset(18.8f, 5.4f))
    drawOval(color = Color(0xFFFED7AA), topLeft = Offset(4.4f, 9f), size = Size(14.8f, 4.4f))
    p.reset()
    p.moveTo(4f, 11.4f)
    p.lineTo(19.6f, 11.4f)
    p.cubicTo(19.2f, 17.6f, 16.2f, 20.2f, 11.8f, 20.2f)
    p.cubicTo(7.4f, 20.2f, 4.4f, 17.6f, 4f, 11.4f)
    p.close()
    drawPath(p, color = Color(0xFF334155))
    drawRoundRect(Color(0xFFEA580C), topLeft = Offset(8.6f, 14f), size = Size(6.4f, 2f), cornerRadius = CornerRadius(1f, 1f))
}

private fun DrawScope.drawSnackBag(p: Path) {
    p.reset()
    p.moveTo(5.6f, 4.2f)
    p.lineTo(8.8f, 6f)
    p.lineTo(12f, 4.2f)
    p.lineTo(15.2f, 6f)
    p.lineTo(18.4f, 4.2f)
    p.lineTo(17.4f, 19.6f)
    p.lineTo(6.6f, 19.6f)
    p.close()
    drawPath(p, color = Color(0xFFF97316))
    drawRoundRect(Color(0xFFEF4444), topLeft = Offset(6.8f, 9f), size = Size(10.4f, 6f), cornerRadius = CornerRadius(1.4f, 1.4f))
    drawCircle(color = Color(0xFFFDE047), radius = 2f, center = Offset(12f, 12f))
}

private fun DrawScope.drawElectricPlug(p: Path) {
    drawLine(Color(0xFF64748B), Offset(9.4f, 3.8f), Offset(9.4f, 7.6f), strokeWidth = 2f, cap = StrokeCap.Round)
    drawLine(Color(0xFF64748B), Offset(14.6f, 3.8f), Offset(14.6f, 7.6f), strokeWidth = 2f, cap = StrokeCap.Round)
    drawRoundRect(Color(0xFFF59E0B), topLeft = Offset(6.4f, 7.4f), size = Size(11.2f, 6.8f), cornerRadius = CornerRadius(2.6f, 2.6f))
    drawRoundRect(Color(0xFFEA580C), topLeft = Offset(9.8f, 14.2f), size = Size(4.4f, 2.8f), cornerRadius = CornerRadius(1f, 1f))
    p.reset()
    p.moveTo(12f, 17f)
    p.cubicTo(12f, 19.6f, 15.8f, 19.6f, 17.6f, 20.6f)
    drawPath(p, color = Color(0xFF334155), style = Stroke(width = 1.8f, cap = StrokeCap.Round))
}

private fun DrawScope.drawPropertyFee() {
    drawRoundRect(Color(0xFF0D9488), topLeft = Offset(4.4f, 4.2f), size = Size(11.6f, 15.8f), cornerRadius = CornerRadius(2.2f, 2.2f))
    drawRoundRect(Color(0xFFCCFBF1), topLeft = Offset(6.4f, 6.8f), size = Size(3f, 2.4f), cornerRadius = CornerRadius(0.6f, 0.6f))
    drawRoundRect(Color(0xFFCCFBF1), topLeft = Offset(10.8f, 6.8f), size = Size(3f, 2.4f), cornerRadius = CornerRadius(0.6f, 0.6f))
    drawRoundRect(Color(0xFFCCFBF1), topLeft = Offset(6.4f, 11f), size = Size(3f, 2.4f), cornerRadius = CornerRadius(0.6f, 0.6f))
    drawCircle(color = Color(0xFFF59E0B), radius = 4.4f, center = Offset(16.6f, 15.6f))
    drawLine(Color.White, Offset(14.8f, 15.6f), Offset(16.2f, 17f), strokeWidth = 1.4f, cap = StrokeCap.Round)
    drawLine(Color.White, Offset(16.2f, 17f), Offset(18.6f, 14.2f), strokeWidth = 1.4f, cap = StrokeCap.Round)
}

private fun DrawScope.drawHouseContract(p: Path) {
    drawRoundRect(Color(0xFF38BDF8), topLeft = Offset(5f, 3.6f), size = Size(14f, 16.8f), cornerRadius = CornerRadius(2.4f, 2.4f))
    drawRoundRect(Color.White, topLeft = Offset(6.6f, 5.2f), size = Size(10.8f, 13.6f), cornerRadius = CornerRadius(1.2f, 1.2f))
    p.reset()
    p.moveTo(8.8f, 9.6f)
    p.lineTo(12f, 7f)
    p.lineTo(15.2f, 9.6f)
    p.lineTo(14.4f, 9.6f)
    p.lineTo(14.4f, 12.2f)
    p.lineTo(9.6f, 12.2f)
    p.lineTo(9.6f, 9.6f)
    p.close()
    drawPath(p, color = Color(0xFF0EA5E9))
    drawLine(Color(0xFFF97316), Offset(8.6f, 15.2f), Offset(15.4f, 15.2f), strokeWidth = 1.6f, cap = StrokeCap.Round)
}

private fun DrawScope.drawSmartphoneDev() {
    drawRoundRect(Color(0xFF334155), topLeft = Offset(6.6f, 3.2f), size = Size(10.8f, 17.6f), cornerRadius = CornerRadius(2.6f, 2.6f))
    drawRoundRect(Color(0xFF38BDF8), topLeft = Offset(8f, 5.2f), size = Size(8f, 12.6f), cornerRadius = CornerRadius(1.2f, 1.2f))
    drawRoundRect(Color(0xFF1E293B), topLeft = Offset(10.4f, 5.8f), size = Size(3.2f, 1f), cornerRadius = CornerRadius(0.5f, 0.5f))
    drawCircle(color = Color.White, radius = 0.9f, center = Offset(12f, 19.2f))
}

private fun DrawScope.drawApplianceWasher() {
    drawRoundRect(Color(0xFF60A5FA), topLeft = Offset(4.8f, 3.6f), size = Size(14.4f, 16.8f), cornerRadius = CornerRadius(2.8f, 2.8f))
    drawRoundRect(Color(0xFFE0F2FE), topLeft = Offset(6.2f, 5f), size = Size(11.6f, 2.6f), cornerRadius = CornerRadius(1f, 1f))
    drawCircle(color = Color(0xFFF43F5E), radius = 0.9f, center = Offset(15.8f, 6.3f))
    drawCircle(color = Color.White, radius = 4.6f, center = Offset(12f, 13.6f))
    drawCircle(color = Color(0xFF0284C7), radius = 3.1f, center = Offset(12f, 13.6f))
    drawOval(color = Color(0xFFBAE6FD), topLeft = Offset(10f, 12f), size = Size(2.2f, 1.4f))
}

private fun DrawScope.drawWristWatch() {
    drawRoundRect(Color(0xFF475569), topLeft = Offset(9f, 3f), size = Size(6f, 18f), cornerRadius = CornerRadius(1.6f, 1.6f))
    drawCircle(color = Color(0xFFF59E0B), radius = 5.8f, center = Offset(12f, 12f))
    drawCircle(color = Color(0xFFFEF3C7), radius = 4.2f, center = Offset(12f, 12f))
    drawLine(Color(0xFF1E293B), Offset(12f, 12f), Offset(12f, 9f), strokeWidth = 1.4f, cap = StrokeCap.Round)
    drawLine(Color(0xFFEF4444), Offset(12f, 12f), Offset(14.6f, 12.8f), strokeWidth = 1.3f, cap = StrokeCap.Round)
}

private fun DrawScope.drawServerRack() {
    for (i in 0..2) {
        val top = 4.2f + i * 5.6f
        drawRoundRect(Color(0xFF334155), topLeft = Offset(3.8f, top), size = Size(16.4f, 4.4f), cornerRadius = CornerRadius(1.3f, 1.3f))
        drawCircle(color = Color(0xFF22C55E), radius = 1f, center = Offset(6.4f, top + 2.2f))
        drawLine(Color(0xFF38BDF8), Offset(9.2f, top + 2.2f), Offset(17.6f, top + 2.2f), strokeWidth = 1.4f, cap = StrokeCap.Round)
    }
}

private fun DrawScope.drawVirtualRecharge(p: Path) {
    drawRoundRect(Color(0xFF7C3AED), topLeft = Offset(3.4f, 5.2f), size = Size(17.2f, 13.6f), cornerRadius = CornerRadius(2.8f, 2.8f))
    p.reset()
    p.moveTo(13f, 7.6f)
    p.lineTo(9.2f, 12.6f)
    p.lineTo(12f, 12.6f)
    p.lineTo(11f, 16.4f)
    p.lineTo(14.8f, 11.4f)
    p.lineTo(12f, 11.4f)
    p.close()
    drawPath(p, color = Color(0xFFFDE047))
}

private fun DrawScope.drawPersonalCare() {
    drawRoundRect(Color(0xFF0D9488), topLeft = Offset(10.6f, 3.6f), size = Size(2.8f, 3.2f), cornerRadius = CornerRadius(0.8f, 0.8f))
    drawLine(Color(0xFF0D9488), Offset(11.4f, 4.4f), Offset(15.6f, 4.4f), strokeWidth = 1.6f, cap = StrokeCap.Round)
    drawRoundRect(Color(0xFF5EEAD4), topLeft = Offset(7f, 6.8f), size = Size(10f, 13.4f), cornerRadius = CornerRadius(3f, 3f))
    drawCircle(color = Color.White, radius = 2.2f, center = Offset(12f, 13.2f))
    drawCircle(color = Color(0xFFBAE6FD), radius = 1.4f, center = Offset(18.2f, 8.2f))
}

private fun DrawScope.drawSportShoe(p: Path) {
    p.reset()
    p.moveTo(4f, 14.8f)
    p.lineTo(6.4f, 8.6f)
    p.lineTo(10.8f, 10.6f)
    p.lineTo(18.8f, 13.4f)
    p.cubicTo(20.6f, 14f, 20.6f, 17f, 18.6f, 17.2f)
    p.lineTo(4.6f, 17.2f)
    p.close()
    drawPath(p, color = Color(0xFFEF4444))
    drawRoundRect(Color.White, topLeft = Offset(3.8f, 16.6f), size = Size(16.6f, 2.2f), cornerRadius = CornerRadius(1.1f, 1.1f))
    drawLine(Color.White, Offset(9.2f, 11.8f), Offset(11.6f, 11f), strokeWidth = 1.2f, cap = StrokeCap.Round)
}

private fun DrawScope.drawBabyRattle(p: Path) {
    rotate(-35f, pivot = Offset(12f, 12f)) {
        drawRoundRect(Color(0xFF38BDF8), topLeft = Offset(10.8f, 11.4f), size = Size(2.4f, 8.4f), cornerRadius = CornerRadius(1.2f, 1.2f))
        drawCircle(color = Color(0xFFF472B6), radius = 5f, center = Offset(12f, 8.2f))
        drawCircle(color = Color(0xFFFDE047), radius = 2f, center = Offset(12f, 8.2f))
        drawCircle(color = Color(0xFFF472B6), radius = 1.8f, center = Offset(12f, 19.6f))
    }
    p.reset()
}

private fun DrawScope.drawOfficeFolder() {
    drawRoundRect(Color(0xFF2563EB), topLeft = Offset(3.8f, 5.2f), size = Size(7.2f, 3.6f), cornerRadius = CornerRadius(1.4f, 1.4f))
    drawRoundRect(Color(0xFF38BDF8), topLeft = Offset(3.8f, 7.2f), size = Size(16.4f, 12.2f), cornerRadius = CornerRadius(2.6f, 2.6f))
    drawLine(Color.White, Offset(7f, 11.6f), Offset(15.6f, 11.6f), strokeWidth = 1.5f, cap = StrokeCap.Round)
    drawLine(Color(0xFFBAE6FD), Offset(7f, 14.8f), Offset(13.2f, 14.8f), strokeWidth = 1.5f, cap = StrokeCap.Round)
}

private fun DrawScope.drawHouseholdLamp(p: Path) {
    p.reset()
    p.moveTo(6.8f, 5f)
    p.lineTo(17.2f, 5f)
    p.lineTo(19.4f, 12f)
    p.lineTo(4.6f, 12f)
    p.close()
    drawPath(p, color = Color(0xFFFACC15))
    drawLine(Color(0xFF64748B), Offset(12f, 12f), Offset(12f, 18.6f), strokeWidth = 2.2f, cap = StrokeCap.Round)
    drawRoundRect(Color(0xFF475569), topLeft = Offset(7.4f, 18f), size = Size(9.2f, 2.4f), cornerRadius = CornerRadius(1.2f, 1.2f))
}

// =====================================================================
// 21 个预置标签专属多色扁平卡通绘图函数（与分类 100% 独立）
// =====================================================================

private fun DrawScope.drawTagPdd() {
    drawCircle(color = Color(0xFFE11D48), radius = 6.2f, center = Offset(9.2f, 12f))
    drawCircle(color = Color(0xFFFB7185), radius = 6.2f, center = Offset(14.8f, 12f))
    drawCircle(color = Color.White, radius = 2.3f, center = Offset(9.2f, 12f))
    drawCircle(color = Color(0xFFFDE047), radius = 2.3f, center = Offset(14.8f, 12f))
}

private fun DrawScope.drawTagTaobao() {
    drawArc(
        color = Color(0xFFEA580C),
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(8.4f, 3.4f),
        size = Size(7.2f, 5.2f),
        style = Stroke(width = 1.8f, cap = StrokeCap.Round)
    )
    drawRoundRect(Color(0xFFF97316), topLeft = Offset(4.4f, 6.8f), size = Size(15.2f, 13.4f), cornerRadius = CornerRadius(4f, 4f))
    drawCircle(color = Color.White, radius = 4f, center = Offset(12f, 13.6f), style = Stroke(width = 1.5f))
    drawCircle(color = Color(0xFFFDE047), radius = 1.4f, center = Offset(12f, 13.6f))
}

private fun DrawScope.drawTagJd(p: Path) {
    drawLine(Color(0xFFF59E0B), Offset(3.4f, 9f), Offset(6.8f, 9f), strokeWidth = 1.6f, cap = StrokeCap.Round)
    drawLine(Color(0xFFF59E0B), Offset(2.6f, 12.6f), Offset(6.8f, 12.6f), strokeWidth = 1.6f, cap = StrokeCap.Round)
    drawRoundRect(Color(0xFFDC2626), topLeft = Offset(7.2f, 5.8f), size = Size(13f, 12.4f), cornerRadius = CornerRadius(2.8f, 2.8f))
    p.reset()
    p.moveTo(11f, 8.6f)
    p.lineTo(14.4f, 12f)
    p.lineTo(11f, 15.4f)
    drawPath(p, color = Color.White, style = Stroke(width = 1.8f, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

private fun DrawScope.drawTagMeituan(p: Path) {
    drawRoundRect(Color(0xFFFACC15), topLeft = Offset(3.8f, 5.4f), size = Size(16.4f, 14.4f), cornerRadius = CornerRadius(4.2f, 4.2f))
    p.reset()
    p.moveTo(6.4f, 6.2f)
    p.lineTo(8.6f, 2.8f)
    p.lineTo(10.6f, 6.2f)
    p.moveTo(13.4f, 6.2f)
    p.lineTo(15.4f, 2.8f)
    p.lineTo(17.6f, 6.2f)
    p.close()
    drawPath(p, color = Color(0xFFF59E0B))
    drawCircle(color = Color(0xFF1E293B), radius = 1.2f, center = Offset(9.4f, 11.4f))
    drawCircle(color = Color(0xFF1E293B), radius = 1.2f, center = Offset(14.6f, 11.4f))
    drawArc(Color(0xFF92400E), startAngle = 15f, sweepAngle = 150f, useCenter = false, topLeft = Offset(9.2f, 12.4f), size = Size(5.6f, 3.6f), style = Stroke(width = 1.5f, cap = StrokeCap.Round))
}

private fun DrawScope.drawTagOffline(p: Path) {
    p.reset()
    p.moveTo(12f, 20.6f)
    p.cubicTo(6f, 14.8f, 5.2f, 11.4f, 5.2f, 9f)
    p.cubicTo(5.2f, 5.2f, 8.2f, 3.2f, 12f, 3.2f)
    p.cubicTo(15.8f, 3.2f, 18.8f, 5.2f, 18.8f, 9f)
    p.cubicTo(18.8f, 11.4f, 18f, 14.8f, 12f, 20.6f)
    p.close()
    drawPath(p, color = Color(0xFF0D9488))
    drawCircle(color = Color.White, radius = 3f, center = Offset(12f, 9.2f))
}

private fun DrawScope.drawTagCrossBorder(p: Path) {
    drawCircle(color = Color(0xFF4F46E5), radius = 8.4f, center = Offset(12f, 12.6f))
    drawOval(color = Color(0xFF818CF8), topLeft = Offset(8.4f, 4.6f), size = Size(7.2f, 16f), style = Stroke(width = 1.2f))
    p.reset()
    p.moveTo(4.2f, 9.2f)
    p.quadraticTo(12f, 3.6f, 19.8f, 7.6f)
    drawPath(p, color = Color(0xFFFDE047), style = Stroke(width = 1.8f, cap = StrokeCap.Round))
    drawCircle(color = Color.White, radius = 1.4f, center = Offset(19.6f, 7.5f))
}

private fun DrawScope.drawTagCommute() {
    drawRoundRect(Color(0xFF0284C7), topLeft = Offset(10.6f, 3f), size = Size(2.8f, 3.6f), cornerRadius = CornerRadius(1f, 1f))
    drawRoundRect(Color(0xFF38BDF8), topLeft = Offset(5.2f, 6.2f), size = Size(13.6f, 14.4f), cornerRadius = CornerRadius(2.6f, 2.6f))
    drawRoundRect(Color.White, topLeft = Offset(7.2f, 8.8f), size = Size(9.6f, 6.4f), cornerRadius = CornerRadius(1.2f, 1.2f))
    drawCircle(color = Color(0xFFF59E0B), radius = 1.8f, center = Offset(12f, 12f))
}

private fun DrawScope.drawTagBusinessTrip() {
    drawRoundRect(Color(0xFF64748B), topLeft = Offset(9f, 3.4f), size = Size(6f, 3.8f), cornerRadius = CornerRadius(1.2f, 1.2f), style = Stroke(width = 1.6f))
    drawRoundRect(Color(0xFF1D4ED8), topLeft = Offset(4.8f, 6.8f), size = Size(14.4f, 12.6f), cornerRadius = CornerRadius(2.4f, 2.4f))
    drawRoundRect(Color(0xFFFDE047), topLeft = Offset(15f, 9.6f), size = Size(3f, 5f), cornerRadius = CornerRadius(0.9f, 0.9f))
    drawCircle(color = Color(0xFF1E293B), radius = 1.2f, center = Offset(7.6f, 20.2f))
    drawCircle(color = Color(0xFF1E293B), radius = 1.2f, center = Offset(16.4f, 20.2f))
}

private fun DrawScope.drawTagTravel(p: Path) {
    drawRoundRect(Color(0xFFECFDF5), topLeft = Offset(4f, 4f), size = Size(16f, 16f), cornerRadius = CornerRadius(2.6f, 2.6f))
    drawRoundRect(Color(0xFF0F766E), topLeft = Offset(4f, 4f), size = Size(16f, 16f), cornerRadius = CornerRadius(2.6f, 2.6f), style = Stroke(width = 1.4f))
    drawCircle(color = Color(0xFFF59E0B), radius = 1.8f, center = Offset(15.6f, 8f))
    p.reset()
    p.moveTo(5.6f, 15.8f)
    p.lineTo(10.2f, 10.6f)
    p.lineTo(13.8f, 14.2f)
    p.lineTo(16.2f, 12f)
    p.lineTo(18.4f, 15.8f)
    p.close()
    drawPath(p, color = Color(0xFF10B981))
}

private fun DrawScope.drawTagStockup(p: Path) {
    drawRoundRect(Color(0xFF9333EA), topLeft = Offset(4.4f, 11.4f), size = Size(10.8f, 8.6f), cornerRadius = CornerRadius(1.8f, 1.8f))
    drawRoundRect(Color(0xFFC084FC), topLeft = Offset(6.2f, 5.2f), size = Size(8.4f, 6.2f), cornerRadius = CornerRadius(1.4f, 1.4f))
    drawCircle(color = Color(0xFFEF4444), radius = 4f, center = Offset(17.2f, 14.8f))
    drawLine(Color.White, Offset(15.6f, 14.8f), Offset(18.8f, 14.8f), strokeWidth = 1.5f, cap = StrokeCap.Round)
    drawLine(Color.White, Offset(17.2f, 13.2f), Offset(17.2f, 16.4f), strokeWidth = 1.5f, cap = StrokeCap.Round)
    p.reset()
}

private fun DrawScope.drawTagEssential(p: Path) {
    p.reset()
    p.moveTo(12f, 3.4f)
    p.lineTo(19f, 6.4f)
    p.lineTo(19f, 12f)
    p.cubicTo(19f, 16.4f, 15.6f, 19.6f, 12f, 20.8f)
    p.cubicTo(8.4f, 19.6f, 5f, 16.4f, 5f, 12f)
    p.lineTo(5f, 6.4f)
    p.close()
    drawPath(p, color = Color(0xFF16A34A))
    drawLine(Color(0xFFFDE047), Offset(8.6f, 12.2f), Offset(11.2f, 14.8f), strokeWidth = 2.1f, cap = StrokeCap.Round)
    drawLine(Color(0xFFFDE047), Offset(11.2f, 14.8f), Offset(15.8f, 9.4f), strokeWidth = 2.1f, cap = StrokeCap.Round)
}

private fun DrawScope.drawTagInstallment() {
    drawRoundRect(Color(0xFF64748B), topLeft = Offset(4f, 4.8f), size = Size(16f, 14.8f), cornerRadius = CornerRadius(2.8f, 2.8f))
    drawRoundRect(Color(0xFF334155), topLeft = Offset(4f, 4.8f), size = Size(16f, 4.2f), cornerRadius = CornerRadius(2.2f, 2.2f))
    drawCircle(color = Color(0xFF4ADE80), radius = 1.5f, center = Offset(8f, 13.8f))
    drawCircle(color = Color(0xFFFDE047), radius = 1.5f, center = Offset(12f, 13.8f))
    drawCircle(color = Color(0xFFE2E8F0), radius = 1.5f, center = Offset(16f, 13.8f))
}

private fun DrawScope.drawTagDiscount() {
    drawRoundRect(Color(0xFF15803D), topLeft = Offset(3.4f, 6f), size = Size(17.2f, 12f), cornerRadius = CornerRadius(2.6f, 2.6f))
    drawCircle(color = Color.White, radius = 1.6f, center = Offset(3.4f, 12f))
    drawCircle(color = Color.White, radius = 1.6f, center = Offset(20.6f, 12f))
    drawLine(Color(0xFFFDE047), Offset(8.4f, 12f), Offset(15.6f, 12f), strokeWidth = 2.2f, cap = StrokeCap.Round)
}

private fun DrawScope.drawTagImpulse(p: Path) {
    drawCircle(color = Color(0xFFFFE4E6), radius = 9.2f, center = Offset(12f, 12f))
    p.reset()
    p.moveTo(13f, 4.4f)
    p.lineTo(8.2f, 12.6f)
    p.lineTo(11.8f, 12.6f)
    p.lineTo(10.6f, 19.6f)
    p.lineTo(16f, 11f)
    p.lineTo(12.4f, 11f)
    p.close()
    drawPath(p, color = Color(0xFFE11D48))
}

private fun DrawScope.drawTagLarge(p: Path) {
    p.reset()
    p.moveTo(6.2f, 8.6f)
    p.lineTo(17.8f, 8.6f)
    p.lineTo(20.6f, 16.8f)
    p.lineTo(3.4f, 16.8f)
    p.close()
    drawPath(p, color = Color(0xFFF59E0B))
    drawRoundRect(Color(0xFFFDE047), topLeft = Offset(7.2f, 10.6f), size = Size(9.6f, 2.6f), cornerRadius = CornerRadius(1f, 1f))
    drawCircle(color = Color(0xFFEF4444), radius = 1.6f, center = Offset(17.5f, 6.2f))
}

private fun DrawScope.drawTagSelf() {
    drawCircle(color = Color(0xFF2AA876), radius = 9f, center = Offset(12f, 12f))
    drawCircle(color = Color(0xFFDCFCE7), radius = 3.2f, center = Offset(12f, 8.8f))
    drawRoundRect(Color(0xFFDCFCE7), topLeft = Offset(7f, 13f), size = Size(10f, 4.8f), cornerRadius = CornerRadius(2.4f, 2.4f))
}

private fun DrawScope.drawTagFamily(p: Path) {
    p.reset()
    p.moveTo(3.4f, 10.6f)
    p.lineTo(12f, 3.8f)
    p.lineTo(20.6f, 10.6f)
    drawPath(p, color = Color(0xFFEA580C), style = Stroke(width = 2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    drawCircle(color = Color(0xFFFB923C), radius = 2.4f, center = Offset(8.6f, 13f))
    drawCircle(color = Color(0xFFFB923C), radius = 2.4f, center = Offset(15.4f, 13f))
    drawCircle(color = Color(0xFFFACC15), radius = 1.9f, center = Offset(12f, 15.8f))
}

private fun DrawScope.drawTagPartner(p: Path) {
    drawCircle(color = Color(0xFFFCE7F3), radius = 9.2f, center = Offset(12f, 12f))
    p.reset()
    p.moveTo(9.4f, 16.4f)
    p.cubicTo(5.6f, 13.6f, 5.4f, 9.6f, 7.6f, 8.2f)
    p.cubicTo(8.8f, 7.4f, 9.4f, 8.6f, 9.4f, 9.2f)
    p.cubicTo(9.4f, 8.6f, 10f, 7.4f, 11.2f, 8.2f)
    p.cubicTo(13.4f, 9.6f, 13.2f, 13.6f, 9.4f, 16.4f)
    p.close()
    drawPath(p, color = Color(0xFFEC4899))
    drawCircle(color = Color(0xFFF43F5E), radius = 3.2f, center = Offset(15.4f, 11.4f))
}

private fun DrawScope.drawTagAa() {
    drawCircle(color = Color(0xFF0284C7), radius = 9f, center = Offset(12f, 12f))
    drawArc(color = Color(0xFF38BDF8), startAngle = -90f, sweepAngle = 180f, useCenter = true, topLeft = Offset(3f, 3f), size = Size(18f, 18f))
    drawLine(Color.White, Offset(12f, 4.4f), Offset(12f, 19.6f), strokeWidth = 2f, cap = StrokeCap.Round)
}

private fun DrawScope.drawTagTaxDeduct() {
    drawRoundRect(Color(0xFF4F46E5), topLeft = Offset(4.8f, 3.6f), size = Size(12.2f, 15.8f), cornerRadius = CornerRadius(2.4f, 2.4f))
    drawRoundRect(Color.White, topLeft = Offset(6.4f, 5.4f), size = Size(9f, 12f), cornerRadius = CornerRadius(1f, 1f))
    drawLine(Color(0xFF4F46E5), Offset(8f, 8.8f), Offset(13.8f, 8.8f), strokeWidth = 1.4f, cap = StrokeCap.Round)
    drawLine(Color(0xFF4F46E5), Offset(8f, 11.8f), Offset(12.8f, 11.8f), strokeWidth = 1.4f, cap = StrokeCap.Round)
    drawCircle(color = Color(0xFF22C55E), radius = 3.8f, center = Offset(16.8f, 16.4f))
    drawLine(Color.White, Offset(15.2f, 16.4f), Offset(16.4f, 17.6f), strokeWidth = 1.4f, cap = StrokeCap.Round)
    drawLine(Color.White, Offset(16.4f, 17.6f), Offset(18.6f, 15f), strokeWidth = 1.4f, cap = StrokeCap.Round)
}

private fun DrawScope.drawTagRenovation() {
    drawRoundRect(Color(0xFF0D9488), topLeft = Offset(4.4f, 4.6f), size = Size(12.8f, 5.2f), cornerRadius = CornerRadius(1.8f, 1.8f))
    drawLine(Color(0xFF64748B), Offset(17.2f, 7.2f), Offset(19f, 7.2f), strokeWidth = 1.6f, cap = StrokeCap.Round)
    drawLine(Color(0xFF64748B), Offset(19f, 7.2f), Offset(19f, 11.8f), strokeWidth = 1.6f, cap = StrokeCap.Round)
    drawLine(Color(0xFF64748B), Offset(19f, 11.8f), Offset(12f, 11.8f), strokeWidth = 1.6f, cap = StrokeCap.Round)
    drawRoundRect(Color(0xFFF59E0B), topLeft = Offset(10.6f, 11.8f), size = Size(2.8f, 8.4f), cornerRadius = CornerRadius(1.4f, 1.4f))
}
