package com.yubrae.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.AirplaneTicket
import androidx.compose.material.icons.automirrored.rounded.Assignment
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.MultilineChart
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.AccountBox
import androidx.compose.material.icons.rounded.AddCard
import androidx.compose.material.icons.rounded.AddShoppingCart
import androidx.compose.material.icons.rounded.AssuredWorkload
import androidx.compose.material.icons.rounded.Atm
import androidx.compose.material.icons.rounded.AttachMoney
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Backpack
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.BakeryDining
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Bathtub
import androidx.compose.material.icons.rounded.BeachAccess
import androidx.compose.material.icons.rounded.Bed
import androidx.compose.material.icons.rounded.Bento
import androidx.compose.material.icons.rounded.Bloodtype
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BreakfastDining
import androidx.compose.material.icons.rounded.BrunchDining
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.BusinessCenter
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CandlestickChart
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.CardMembership
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.Chair
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Checkroom
import androidx.compose.material.icons.rounded.ChildCare
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.ConfirmationNumber
import androidx.compose.material.icons.rounded.Contactless
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Cookie
import androidx.compose.material.icons.rounded.CorporateFare
import androidx.compose.material.icons.rounded.Cottage
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.CreditScore
import androidx.compose.material.icons.rounded.CurrencyBitcoin
import androidx.compose.material.icons.rounded.CurrencyExchange
import androidx.compose.material.icons.rounded.CurrencyPound
import androidx.compose.material.icons.rounded.CurrencyYen
import androidx.compose.material.icons.rounded.DeliveryDining
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Diamond
import androidx.compose.material.icons.rounded.DinnerDining
import androidx.compose.material.icons.rounded.DirectionsBoat
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.DryCleaning
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.EggAlt
import androidx.compose.material.icons.rounded.Elderly
import androidx.compose.material.icons.rounded.ElectricCar
import androidx.compose.material.icons.rounded.ElectricScooter
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.EmojiFoodBeverage
import androidx.compose.material.icons.rounded.Euro
import androidx.compose.material.icons.rounded.EvStation
import androidx.compose.material.icons.rounded.EventRepeat
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.FaceRetouchingNatural
import androidx.compose.material.icons.rounded.FamilyRestroom
import androidx.compose.material.icons.rounded.Fastfood
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FilterVintage
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Forest
import androidx.compose.material.icons.rounded.FreeBreakfast
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Handshake
import androidx.compose.material.icons.rounded.Handyman
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Healing
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.Hiking
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Hotel
import androidx.compose.material.icons.rounded.Icecream
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Iron
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Kitchen
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LaptopMac
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Liquor
import androidx.compose.material.icons.rounded.LocalAtm
import androidx.compose.material.icons.rounded.LocalBar
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.LocalCarWash
import androidx.compose.material.icons.rounded.LocalConvenienceStore
import androidx.compose.material.icons.rounded.LocalFlorist
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.LocalGroceryStore
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.LocalLaundryService
import androidx.compose.material.icons.rounded.LocalMall
import androidx.compose.material.icons.rounded.LocalOffer
import androidx.compose.material.icons.rounded.LocalParking
import androidx.compose.material.icons.rounded.LocalPharmacy
import androidx.compose.material.icons.rounded.LocalPizza
import androidx.compose.material.icons.rounded.LocalPostOffice
import androidx.compose.material.icons.rounded.LocalShipping
import androidx.compose.material.icons.rounded.LocalTaxi
import androidx.compose.material.icons.rounded.Loyalty
import androidx.compose.material.icons.rounded.Luggage
import androidx.compose.material.icons.rounded.LunchDining
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.MedicalServices
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MonetizationOn
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Nfc
import androidx.compose.material.icons.rounded.Nightlife
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.OutdoorGrill
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Park
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.PedalBike
import androidx.compose.material.icons.rounded.Percent
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material.icons.rounded.PointOfSale
import androidx.compose.material.icons.rounded.Pool
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.PriorityHigh
import androidx.compose.material.icons.rounded.PropaneTank
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.RamenDining
import androidx.compose.material.icons.rounded.Redeem
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.RequestQuote
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.RiceBowl
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.Sell
import androidx.compose.material.icons.rounded.SetMeal
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.ShoppingBasket
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.SimCard
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.SoupKitchen
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.SportsBar
import androidx.compose.material.icons.rounded.SportsBasketball
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.SportsSoccer
import androidx.compose.material.icons.rounded.SportsTennis
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Store
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.Stroller
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material.icons.rounded.Subway
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.TabletMac
import androidx.compose.material.icons.rounded.TakeoutDining
import androidx.compose.material.icons.rounded.TapAndPlay
import androidx.compose.material.icons.rounded.Tapas
import androidx.compose.material.icons.rounded.TheaterComedy
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material.icons.rounded.Toll
import androidx.compose.material.icons.rounded.Toys
import androidx.compose.material.icons.rounded.Train
import androidx.compose.material.icons.rounded.Tram
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.TwoWheeler
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material.icons.rounded.Vaccines
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VolunteerActivism
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.Weekend
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WineBar
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material.icons.rounded.Yard
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yubrae.app.core.model.CurrencyCatalog
import com.yubrae.app.data.db.CaptureChannel
import com.yubrae.app.data.db.ParseStatus
import com.yubrae.app.data.db.TransactionType
import com.yubrae.app.ui.theme.KairoTheme
import com.yubrae.app.ui.theme.tabular
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

data class IconChoice(
    val key: String,
    val label: String,
    val vector: ImageVector,
    val group: String
)

data class IconGroupSpec(
    val group: String,
    val items: List<IconChoice>
)

private fun iconGroup(
    group: String,
    vararg entries: Triple<String, String, ImageVector>
): IconGroupSpec = IconGroupSpec(
    group = group,
    items = entries.map { (key, label, vector) ->
        IconChoice(key = key, label = label, vector = vector, group = group)
    }
)

object KairoIcons {
    val accountIconGroups: List<IconGroupSpec> = listOf(
        iconGroup(
            "现金与钱包",
            Triple("account_balance_wallet", "钱包", Icons.Rounded.AccountBalanceWallet),
            Triple("payments", "现金", Icons.Rounded.Payments),
            Triple("local_atm", "零钱", Icons.Rounded.LocalAtm),
            Triple("monetization_on", "硬币", Icons.Rounded.MonetizationOn),
            Triple("attach_money", "美元", Icons.Rounded.AttachMoney),
            Triple("euro", "欧元", Icons.Rounded.Euro),
            Triple("currency_pound", "英镑", Icons.Rounded.CurrencyPound),
            Triple("currency_yen", "日元", Icons.Rounded.CurrencyYen)
        ),
        iconGroup(
            "银行卡与借记",
            Triple("account_balance", "银行", Icons.Rounded.AccountBalance),
            Triple("atm", "ATM卡", Icons.Rounded.Atm),
            Triple("assured_workload", "工资卡", Icons.Rounded.AssuredWorkload),
            Triple("badge", "联名卡", Icons.Rounded.Badge),
            Triple("account_box", "个人户", Icons.Rounded.AccountBox),
            Triple("verified_user", "安全卡", Icons.Rounded.VerifiedUser),
            Triple("currency_exchange", "外币卡", Icons.Rounded.CurrencyExchange),
            Triple("language", "跨境户", Icons.Rounded.Language)
        ),
        iconGroup(
            "信用卡与分期",
            Triple("credit_card", "信用卡", Icons.Rounded.CreditCard),
            Triple("credit_score", "信用分", Icons.Rounded.CreditScore),
            Triple("add_card", "副卡", Icons.Rounded.AddCard),
            Triple("calendar_month", "账单卡", Icons.Rounded.CalendarMonth),
            Triple("event_repeat", "分期付", Icons.Rounded.EventRepeat),
            Triple("percent", "免息期", Icons.Rounded.Percent),
            Triple("diamond", "白金卡", Icons.Rounded.Diamond),
            Triple("receipt_long", "花呗白条", Icons.AutoMirrored.Rounded.ReceiptLong)
        ),
        iconGroup(
            "第三方支付",
            Triple("chat", "微信", Icons.AutoMirrored.Rounded.Chat),
            Triple("qr_code_2", "支付宝", Icons.Rounded.QrCode2),
            Triple("qr_code_scanner", "扫码付", Icons.Rounded.QrCodeScanner),
            Triple("contactless", "闪付", Icons.Rounded.Contactless),
            Triple("nfc", "NFC", Icons.Rounded.Nfc),
            Triple("tap_and_play", "碰一碰", Icons.Rounded.TapAndPlay),
            Triple("smartphone", "云钱包", Icons.Rounded.Smartphone),
            Triple("point_of_sale", "收款码", Icons.Rounded.PointOfSale)
        ),
        iconGroup(
            "储蓄理财股票基金",
            Triple("savings", "储蓄", Icons.Rounded.Savings),
            Triple("trending_up", "理财", Icons.AutoMirrored.Rounded.TrendingUp),
            Triple("candlestick_chart", "股票", Icons.Rounded.CandlestickChart),
            Triple("show_chart", "基金", Icons.AutoMirrored.Rounded.ShowChart),
            Triple("pie_chart", "资产包", Icons.Rounded.PieChart),
            Triple("bar_chart", "定投", Icons.Rounded.BarChart),
            Triple("multiline_chart", "债券", Icons.AutoMirrored.Rounded.MultilineChart),
            Triple("currency_bitcoin", "数字币", Icons.Rounded.CurrencyBitcoin)
        ),
        iconGroup(
            "生活卡券出行会员与借贷",
            Triple("directions_bus", "公交卡", Icons.Rounded.DirectionsBus),
            Triple("subway", "地铁卡", Icons.Rounded.Subway),
            Triple("card_membership", "会员卡", Icons.Rounded.CardMembership),
            Triple("card_giftcard", "礼品卡", Icons.Rounded.CardGiftcard),
            Triple("confirmation_number", "储值券", Icons.Rounded.ConfirmationNumber),
            Triple("loyalty", "积分卡", Icons.Rounded.Loyalty),
            Triple("handshake", "借出款", Icons.Rounded.Handshake),
            Triple("request_quote", "借入款", Icons.Rounded.RequestQuote)
        )
    )

    val ledgerIconGroups: List<IconGroupSpec> = listOf(
        iconGroup(
            "日常与家庭",
            Triple("menu_book", "账本", Icons.AutoMirrored.Rounded.MenuBook),
            Triple("account_balance_wallet", "日常", Icons.Rounded.AccountBalanceWallet),
            Triple("home", "家庭", Icons.Rounded.Home),
            Triple("cottage", "小家", Icons.Rounded.Cottage),
            Triple("shopping_bag", "购物", Icons.Rounded.ShoppingBag),
            Triple("local_grocery_store", "菜篮", Icons.Rounded.LocalGroceryStore),
            Triple("restaurant", "三餐", Icons.Rounded.Restaurant),
            Triple("family_restroom", "亲情", Icons.Rounded.FamilyRestroom)
        ),
        iconGroup(
            "旅行与出行",
            Triple("flight", "旅行", Icons.Rounded.Flight),
            Triple("luggage", "出游", Icons.Rounded.Luggage),
            Triple("beach_access", "度假", Icons.Rounded.BeachAccess),
            Triple("explore", "探索", Icons.Rounded.Explore),
            Triple("map", "自驾", Icons.Rounded.Map),
            Triple("directions_car", "养车", Icons.Rounded.DirectionsCar),
            Triple("train", "高铁", Icons.Rounded.Train),
            Triple("hotel", "住宿", Icons.Rounded.Hotel)
        ),
        iconGroup(
            "工作与报销",
            Triple("work", "工作", Icons.Rounded.Work),
            Triple("business_center", "差旅", Icons.Rounded.BusinessCenter),
            Triple("receipt_long", "报销", Icons.AutoMirrored.Rounded.ReceiptLong),
            Triple("payments", "薪资", Icons.Rounded.Payments),
            Triple("laptop_mac", "项目", Icons.Rounded.LaptopMac),
            Triple("corporate_fare", "公司", Icons.Rounded.CorporateFare),
            Triple("calculate", "税费", Icons.Rounded.Calculate),
            Triple("assignment", "公账", Icons.AutoMirrored.Rounded.Assignment)
        ),
        iconGroup(
            "学习兴趣与宠物",
            Triple("school", "学习", Icons.Rounded.School),
            Triple("book", "读书", Icons.Rounded.Book),
            Triple("pets", "宠物", Icons.Rounded.Pets),
            Triple("sports_esports", "游戏", Icons.Rounded.SportsEsports),
            Triple("music_note", "音乐", Icons.Rounded.MusicNote),
            Triple("camera_alt", "摄影", Icons.Rounded.CameraAlt),
            Triple("fitness_center", "健身", Icons.Rounded.FitnessCenter),
            Triple("palette", "手账", Icons.Rounded.Palette)
        ),
        iconGroup(
            "专项储蓄与大事",
            Triple("savings", "储蓄", Icons.Rounded.Savings),
            Triple("favorite", "专项", Icons.Rounded.Favorite),
            Triple("handyman", "装修", Icons.Rounded.Handyman),
            Triple("celebration", "婚礼", Icons.Rounded.Celebration),
            Triple("child_care", "育儿", Icons.Rounded.ChildCare),
            Triple("storefront", "副业", Icons.Rounded.Storefront),
            Triple("auto_awesome", "心愿", Icons.Rounded.AutoAwesome),
            Triple("trending_up", "投资", Icons.AutoMirrored.Rounded.TrendingUp)
        )
    )

    val categoryIconGroups: List<IconGroupSpec> = listOf(
        iconGroup(
            "餐饮美食",
            Triple("restaurant", "餐饮", Icons.Rounded.Restaurant),
            Triple("lunch_dining", "正餐", Icons.Rounded.LunchDining),
            Triple("delivery_dining", "外卖", Icons.Rounded.DeliveryDining),
            Triple("fastfood", "快餐", Icons.Rounded.Fastfood),
            Triple("ramen_dining", "面食", Icons.Rounded.RamenDining),
            Triple("local_pizza", "披萨", Icons.Rounded.LocalPizza),
            Triple("local_grocery_store", "生鲜", Icons.Rounded.LocalGroceryStore),
            Triple("shopping_basket", "买菜", Icons.Rounded.ShoppingBasket),
            Triple("kitchen", "做饭", Icons.Rounded.Kitchen),
            Triple("breakfast_dining", "早餐", Icons.Rounded.BreakfastDining),
            Triple("brunch_dining", "早午餐", Icons.Rounded.BrunchDining),
            Triple("dinner_dining", "晚餐", Icons.Rounded.DinnerDining),
            Triple("rice_bowl", "米饭", Icons.Rounded.RiceBowl),
            Triple("soup_kitchen", "汤锅", Icons.Rounded.SoupKitchen),
            Triple("set_meal", "套餐", Icons.Rounded.SetMeal),
            Triple("bento", "便当", Icons.Rounded.Bento),
            Triple("outdoor_grill", "烧烤", Icons.Rounded.OutdoorGrill),
            Triple("takeout_dining", "打包", Icons.Rounded.TakeoutDining)
        ),
        iconGroup(
            "饮品零食",
            Triple("local_cafe", "咖啡", Icons.Rounded.LocalCafe),
            Triple("coffee", "茶饮", Icons.Rounded.Coffee),
            Triple("bakery_dining", "甜品", Icons.Rounded.BakeryDining),
            Triple("icecream", "冷饮", Icons.Rounded.Icecream),
            Triple("cake", "蛋糕", Icons.Rounded.Cake),
            Triple("local_bar", "聚餐", Icons.Rounded.LocalBar),
            Triple("wine_bar", "酒水", Icons.Rounded.WineBar),
            Triple("emoji_food_beverage", "热茶", Icons.Rounded.EmojiFoodBeverage),
            Triple("free_breakfast", "下午茶", Icons.Rounded.FreeBreakfast),
            Triple("cookie", "饼干", Icons.Rounded.Cookie),
            Triple("egg_alt", "轻食", Icons.Rounded.EggAlt),
            Triple("tapas", "小吃", Icons.Rounded.Tapas),
            Triple("sports_bar", "夜宵", Icons.Rounded.SportsBar),
            Triple("liquor", "洋酒", Icons.Rounded.Liquor)
        ),
        iconGroup(
            "日常出行",
            Triple("directions_car", "汽车", Icons.Rounded.DirectionsCar),
            Triple("electric_car", "电车", Icons.Rounded.ElectricCar),
            Triple("subway", "地铁", Icons.Rounded.Subway),
            Triple("directions_bus", "公交", Icons.Rounded.DirectionsBus),
            Triple("local_taxi", "打车", Icons.Rounded.LocalTaxi),
            Triple("pedal_bike", "单车", Icons.Rounded.PedalBike),
            Triple("two_wheeler", "骑行", Icons.Rounded.TwoWheeler),
            Triple("train", "高铁", Icons.Rounded.Train),
            Triple("directions_boat", "轮渡", Icons.Rounded.DirectionsBoat),
            Triple("local_gas_station", "加油", Icons.Rounded.LocalGasStation),
            Triple("local_parking", "停车", Icons.Rounded.LocalParking),
            Triple("build", "维修", Icons.Rounded.Build),
            Triple("flight", "差旅", Icons.Rounded.Flight),
            Triple("hotel", "住宿", Icons.Rounded.Hotel),
            Triple("luggage", "旅游", Icons.Rounded.Luggage),
            Triple("ev_station", "充电", Icons.Rounded.EvStation),
            Triple("local_car_wash", "洗车", Icons.Rounded.LocalCarWash),
            Triple("toll", "高速费", Icons.Rounded.Toll),
            Triple("tram", "轻轨", Icons.Rounded.Tram),
            Triple("electric_scooter", "滑板车", Icons.Rounded.ElectricScooter),
            Triple("airplane_ticket", "机票", Icons.AutoMirrored.Rounded.AirplaneTicket)
        ),
        iconGroup(
            "购物百货",
            Triple("shopping_bag", "购物", Icons.Rounded.ShoppingBag),
            Triple("local_mall", "百货", Icons.Rounded.LocalMall),
            Triple("shopping_cart", "超市", Icons.Rounded.ShoppingCart),
            Triple("store", "日用", Icons.Rounded.Store),
            Triple("storefront", "门店", Icons.Rounded.Storefront),
            Triple("local_shipping", "快递", Icons.Rounded.LocalShipping),
            Triple("local_offer", "补贴", Icons.Rounded.LocalOffer),
            Triple("loyalty", "会员", Icons.Rounded.Loyalty),
            Triple("sell", "闲置", Icons.Rounded.Sell),
            Triple("add_shopping_cart", "囤货", Icons.Rounded.AddShoppingCart),
            Triple("local_convenience_store", "便利店", Icons.Rounded.LocalConvenienceStore),
            Triple("inventory_2", "包裹", Icons.Rounded.Inventory2),
            Triple("backpack", "箱包", Icons.Rounded.Backpack),
            Triple("qr_code_scanner", "扫码购", Icons.Rounded.QrCodeScanner),
            Triple("local_post_office", "邮寄", Icons.Rounded.LocalPostOffice)
        ),
        iconGroup(
            "服饰美妆",
            Triple("checkroom", "服饰", Icons.Rounded.Checkroom),
            Triple("face_retouching_natural", "护肤", Icons.Rounded.FaceRetouchingNatural),
            Triple("spa", "美容", Icons.Rounded.Spa),
            Triple("content_cut", "理发", Icons.Rounded.ContentCut),
            Triple("diamond", "首饰", Icons.Rounded.Diamond),
            Triple("watch", "腕表", Icons.Rounded.Watch),
            Triple("dry_cleaning", "干洗", Icons.Rounded.DryCleaning),
            Triple("iron", "熨烫", Icons.Rounded.Iron),
            Triple("style", "穿搭", Icons.Rounded.Style),
            Triple("auto_fix_high", "彩妆", Icons.Rounded.AutoFixHigh),
            Triple("filter_vintage", "香氛", Icons.Rounded.FilterVintage),
            Triple("brush", "美甲", Icons.Rounded.Brush)
        ),
        iconGroup(
            "居家生活",
            Triple("home", "居家", Icons.Rounded.Home),
            Triple("chair", "家具", Icons.Rounded.Chair),
            Triple("bed", "床品", Icons.Rounded.Bed),
            Triple("bolt", "水电", Icons.Rounded.Bolt),
            Triple("water_drop", "水费", Icons.Rounded.WaterDrop),
            Triple("lightbulb", "照明", Icons.Rounded.Lightbulb),
            Triple("wifi", "网络", Icons.Rounded.Wifi),
            Triple("cleaning_services", "家政", Icons.Rounded.CleaningServices),
            Triple("local_laundry_service", "洗护", Icons.Rounded.LocalLaundryService),
            Triple("yard", "园艺", Icons.Rounded.Yard),
            Triple("pets", "宠物", Icons.Rounded.Pets),
            Triple("child_care", "育儿", Icons.Rounded.ChildCare),
            Triple("stroller", "童车", Icons.Rounded.Stroller),
            Triple("toys", "玩具", Icons.Rounded.Toys),
            Triple("weekend", "沙发", Icons.Rounded.Weekend),
            Triple("bathtub", "卫浴", Icons.Rounded.Bathtub),
            Triple("ac_unit", "空调", Icons.Rounded.AcUnit),
            Triple("propane_tank", "燃气", Icons.Rounded.PropaneTank),
            Triple("handyman", "装修", Icons.Rounded.Handyman),
            Triple("local_florist", "绿植", Icons.Rounded.LocalFlorist)
        ),
        iconGroup(
            "数码通信",
            Triple("devices", "数码", Icons.Rounded.Devices),
            Triple("laptop_mac", "电脑", Icons.Rounded.LaptopMac),
            Triple("phone_android", "话费", Icons.Rounded.PhoneAndroid),
            Triple("headphones", "配件", Icons.Rounded.Headphones),
            Triple("tv", "家电", Icons.Rounded.Tv),
            Triple("camera_alt", "摄影", Icons.Rounded.CameraAlt),
            Triple("subscriptions", "订阅", Icons.Rounded.Subscriptions),
            Triple("cloud_done", "云服务", Icons.Rounded.CloudDone),
            Triple("smart_toy", "AI", Icons.Rounded.SmartToy),
            Triple("tablet_mac", "平板", Icons.Rounded.TabletMac),
            Triple("desktop_windows", "台式机", Icons.Rounded.DesktopWindows),
            Triple("keyboard", "外设", Icons.Rounded.Keyboard),
            Triple("router", "宽带", Icons.Rounded.Router),
            Triple("sim_card", "流量卡", Icons.Rounded.SimCard),
            Triple("speaker", "音响", Icons.Rounded.Speaker),
            Triple("print", "打印", Icons.Rounded.Print)
        ),
        iconGroup(
            "娱乐休闲",
            Triple("music_note", "音乐", Icons.Rounded.MusicNote),
            Triple("sports_esports", "游戏", Icons.Rounded.SportsEsports),
            Triple("movie", "电影", Icons.Rounded.Movie),
            Triple("celebration", "聚会", Icons.Rounded.Celebration),
            Triple("confirmation_number", "门票", Icons.Rounded.ConfirmationNumber),
            Triple("fitness_center", "健身", Icons.Rounded.FitnessCenter),
            Triple("sports_soccer", "运动", Icons.Rounded.SportsSoccer),
            Triple("self_improvement", "瑜伽", Icons.Rounded.SelfImprovement),
            Triple("forest", "露营", Icons.Rounded.Forest),
            Triple("beach_access", "度假", Icons.Rounded.BeachAccess),
            Triple("theater_comedy", "演出", Icons.Rounded.TheaterComedy),
            Triple("mic", "K歌", Icons.Rounded.Mic),
            Triple("sports_basketball", "球类", Icons.Rounded.SportsBasketball),
            Triple("sports_tennis", "网球", Icons.Rounded.SportsTennis),
            Triple("pool", "游泳", Icons.Rounded.Pool),
            Triple("hiking", "徒步", Icons.Rounded.Hiking),
            Triple("park", "公园", Icons.Rounded.Park),
            Triple("nightlife", "夜生活", Icons.Rounded.Nightlife)
        ),
        iconGroup(
            "医疗健康",
            Triple("medical_services", "医疗", Icons.Rounded.MedicalServices),
            Triple("local_hospital", "挂号", Icons.Rounded.LocalHospital),
            Triple("medication", "买药", Icons.Rounded.Medication),
            Triple("health_and_safety", "保险", Icons.Rounded.HealthAndSafety),
            Triple("local_pharmacy", "药房", Icons.Rounded.LocalPharmacy),
            Triple("vaccines", "体检", Icons.Rounded.Vaccines),
            Triple("monitor_heart", "健康", Icons.Rounded.MonitorHeart),
            Triple("healing", "康复", Icons.Rounded.Healing),
            Triple("bloodtype", "化验", Icons.Rounded.Bloodtype),
            Triple("psychology", "心理", Icons.Rounded.Psychology),
            Triple("visibility", "眼科", Icons.Rounded.Visibility),
            Triple("elderly", "养老", Icons.Rounded.Elderly)
        ),
        iconGroup(
            "教育金融与人情",
            Triple("school", "学习", Icons.Rounded.School),
            Triple("book", "书籍", Icons.Rounded.Book),
            Triple("menu_book", "文具", Icons.AutoMirrored.Rounded.MenuBook),
            Triple("edit_note", "考证", Icons.Rounded.EditNote),
            Triple("calculate", "账务", Icons.Rounded.Calculate),
            Triple("palette", "艺术", Icons.Rounded.Palette),
            Triple("favorite", "人情", Icons.Rounded.Favorite),
            Triple("card_giftcard", "礼金", Icons.Rounded.CardGiftcard),
            Triple("redeem", "礼物", Icons.Rounded.Redeem),
            Triple("volunteer_activism", "捐赠", Icons.Rounded.VolunteerActivism),
            Triple("handshake", "往来", Icons.Rounded.Handshake),
            Triple("groups", "社交", Icons.Rounded.Groups),
            Triple("work", "工资", Icons.Rounded.Work),
            Triple("payments", "薪酬", Icons.Rounded.Payments),
            Triple("account_balance_wallet", "钱包", Icons.Rounded.AccountBalanceWallet),
            Triple("account_balance", "银行", Icons.Rounded.AccountBalance),
            Triple("credit_card", "刷卡", Icons.Rounded.CreditCard),
            Triple("savings", "储蓄", Icons.Rounded.Savings),
            Triple("trending_up", "理财", Icons.AutoMirrored.Rounded.TrendingUp),
            Triple("trending_down", "亏损", Icons.AutoMirrored.Rounded.TrendingDown),
            Triple("monetization_on", "收益", Icons.Rounded.MonetizationOn),
            Triple("attach_money", "外币", Icons.Rounded.AttachMoney),
            Triple("currency_exchange", "汇兑", Icons.Rounded.CurrencyExchange),
            Triple("percent", "利息", Icons.Rounded.Percent),
            Triple("receipt_long", "报销", Icons.AutoMirrored.Rounded.ReceiptLong),
            Triple("replay", "退款", Icons.Rounded.Replay),
            Triple("label", "标签", Icons.AutoMirrored.Rounded.Label),
            Triple("category", "分类", Icons.Rounded.Category),
            Triple("more_horiz", "其他", Icons.Rounded.MoreHoriz),
            Triple("science", "科研", Icons.Rounded.Science),
            Triple("translate", "外语", Icons.Rounded.Translate),
            Triple("business_center", "商务", Icons.Rounded.BusinessCenter),
            Triple("emoji_events", "奖金", Icons.Rounded.EmojiEvents)
        )
    )

    val tagIconGroups: List<IconGroupSpec> = listOf(
        iconGroup(
            "报销与账务",
            Triple("receipt_long", "报销", Icons.AutoMirrored.Rounded.ReceiptLong),
            Triple("calculate", "计税", Icons.Rounded.Calculate),
            Triple("credit_card", "分期", Icons.Rounded.CreditCard),
            Triple("subscriptions", "订阅", Icons.Rounded.Subscriptions),
            Triple("local_offer", "优惠", Icons.Rounded.LocalOffer),
            Triple("loyalty", "折扣", Icons.Rounded.Loyalty),
            Triple("diamond", "大额", Icons.Rounded.Diamond),
            Triple("savings", "攒钱", Icons.Rounded.Savings),
            Triple("replay", "退款", Icons.Rounded.Replay),
            Triple("request_quote", "账单", Icons.Rounded.RequestQuote)
        ),
        iconGroup(
            "消费场景",
            Triple("delivery_dining", "外卖", Icons.Rounded.DeliveryDining),
            Triple("shopping_cart", "网购", Icons.Rounded.ShoppingCart),
            Triple("shopping_bag", "逛街", Icons.Rounded.ShoppingBag),
            Triple("shopping_basket", "囤货", Icons.Rounded.ShoppingBasket),
            Triple("local_shipping", "快递", Icons.Rounded.LocalShipping),
            Triple("fastfood", "快餐", Icons.Rounded.Fastfood),
            Triple("storefront", "线下", Icons.Rounded.Storefront),
            Triple("public", "跨境", Icons.Rounded.Public),
            Triple("celebration", "聚餐", Icons.Rounded.Celebration),
            Triple("build", "装修", Icons.Rounded.Build)
        ),
        iconGroup(
            "时间与出行",
            Triple("subway", "通勤", Icons.Rounded.Subway),
            Triple("luggage", "差旅", Icons.Rounded.Luggage),
            Triple("flight", "旅行", Icons.Rounded.Flight),
            Triple("directions_car", "自驾", Icons.Rounded.DirectionsCar),
            Triple("local_taxi", "打车", Icons.Rounded.LocalTaxi),
            Triple("hotel", "住宿", Icons.Rounded.Hotel),
            Triple("schedule", "日常", Icons.Rounded.Schedule),
            Triple("today", "周末", Icons.Rounded.Today),
            Triple("event_repeat", "周期", Icons.Rounded.EventRepeat),
            Triple("beach_access", "假日", Icons.Rounded.BeachAccess)
        ),
        iconGroup(
            "人物与关系",
            Triple("person", "自己", Icons.Rounded.Person),
            Triple("home", "家人", Icons.Rounded.Home),
            Triple("favorite", "伴侣", Icons.Rounded.Favorite),
            Triple("pets", "宠物", Icons.Rounded.Pets),
            Triple("redeem", "请客", Icons.Rounded.Redeem),
            Triple("groups", "AA", Icons.Rounded.Groups),
            Triple("child_care", "孩子", Icons.Rounded.ChildCare),
            Triple("elderly", "长辈", Icons.Rounded.Elderly),
            Triple("handshake", "同事", Icons.Rounded.Handshake),
            Triple("card_giftcard", "送礼", Icons.Rounded.CardGiftcard)
        ),
        iconGroup(
            "状态与标记",
            Triple("verified", "刚需", Icons.Rounded.Verified),
            Triple("bolt", "冲动", Icons.Rounded.Bolt),
            Triple("star", "精选", Icons.Rounded.Star),
            Triple("flag", "重点", Icons.Rounded.Flag),
            Triple("bookmark", "待查", Icons.Rounded.Bookmark),
            Triple("push_pin", "置顶", Icons.Rounded.PushPin),
            Triple("priority_high", "注意", Icons.Rounded.PriorityHigh),
            Triple("check_circle", "已核", Icons.Rounded.CheckCircle),
            Triple("sell", "标记", Icons.Rounded.Sell),
            Triple("auto_awesome", "心愿", Icons.Rounded.AutoAwesome)
        )
    )

    val selectableCategoryIcons: List<Pair<String, String>> =
        categoryIconGroups.flatMap { group -> group.items.map { it.key to it.label } }

    val selectableAccountIcons: List<Pair<String, String>> =
        accountIconGroups.flatMap { group -> group.items.map { it.key to it.label } }

    val selectableLedgerIcons: List<Pair<String, String>> =
        ledgerIconGroups.flatMap { group -> group.items.map { it.key to it.label } }

    val ledgerIconChoices: List<Pair<String, String>>
        get() = selectableLedgerIcons

    val selectableTagIcons: List<Pair<String, String>> =
        tagIconGroups.flatMap { group -> group.items.map { it.key to it.label } }

    private val categoryIconMap: Map<String, ImageVector> = buildMap {
        categoryIconGroups.forEach { group ->
            group.items.forEach { put(it.key, it.vector) }
        }
    }

    private val accountIconMap: Map<String, ImageVector> = buildMap {
        accountIconGroups.forEach { group ->
            group.items.forEach { put(it.key, it.vector) }
        }
        put("wallet", Icons.Rounded.AccountBalanceWallet)
    }

    private val ledgerIconMap: Map<String, ImageVector> = buildMap {
        ledgerIconGroups.forEach { group ->
            group.items.forEach { put(it.key, it.vector) }
        }
    }

    private val tagIconMap: Map<String, ImageVector> = buildMap {
        tagIconGroups.forEach { group ->
            group.items.forEach { put(it.key, it.vector) }
        }
    }

    private val allIconMap: Map<String, ImageVector> = buildMap {
        putAll(categoryIconMap)
        putAll(accountIconMap)
        putAll(ledgerIconMap)
        putAll(tagIconMap)
        put("swap_horiz", Icons.Rounded.SwapHoriz)
        put("tune", Icons.Rounded.Tune)
    }

    fun iconFor(key: String): ImageVector =
        allIconMap[key.trim().lowercase(Locale.ROOT)] ?: Icons.Rounded.Category

    fun categoryIcon(key: String): ImageVector {
        val normalized = key.trim().lowercase(Locale.ROOT)
        return categoryIconMap[normalized] ?: allIconMap[normalized] ?: Icons.Rounded.Category
    }

    fun accountIcon(key: String): ImageVector {
        val normalized = key.trim().lowercase(Locale.ROOT)
        return accountIconMap[normalized] ?: allIconMap[normalized] ?: Icons.Rounded.AccountBalanceWallet
    }

    fun ledgerIcon(key: String): ImageVector {
        val normalized = key.trim().lowercase(Locale.ROOT)
        return ledgerIconMap[normalized] ?: allIconMap[normalized] ?: Icons.AutoMirrored.Rounded.MenuBook
    }

    fun tagIcon(key: String): ImageVector? {
        val normalized = key.trim().lowercase(Locale.ROOT)
        if (normalized.isEmpty()) return null
        return tagIconMap[normalized]
            ?: categoryIconMap[normalized]
            ?: allIconMap[normalized]
            ?: Icons.Rounded.Sell
    }

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
        0xFF475569L,
        0xFF5E8B7EL,
        0xFF6B7D9EL,
        0xFF9E7B9BL,
        0xFFB07D62L
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
    private val noteSegmentSplitRegex = Regex("""\s+·\s+|\s*\|\s*|\r?\n|；""")
    private val structuredNoteMetadataRegex = Regex(
        """^(?:地址|创建用户|创建人|记账人|优惠|优惠金额|报销账户|报销金额|报销明细|报销说明|报销状态|多币种|退款|退款金额|退款明细|退款说明|其他|其它|附件\d*|交易单号|商户单号|流水号|交易订单号|商家订单号)\s*[:：].*$"""
    )
    private val importerDiscountNoteRegex = Regex(
        """^优惠\s+[¥￥$€£+\-]?\s*\d+(?:[.,]\d+)?(?:\s*(?:元|折|[A-Za-z]{3}))?$"""
    )
    private val notePlaceholderValues = setOf("/", "-", "--", "无")

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

    /**
     * 仅用于列表行展示的备注清洗：过滤账单导入拼接的结构化元数据片段，绝不改写数据库原值。
     */
    fun cleanListRowNote(rawNote: String): String {
        if (rawNote.isBlank()) return ""
        val kept = noteSegmentSplitRegex.split(rawNote)
            .asSequence()
            .map { it.trim().trim('·').trim() }
            .filter { segment ->
                segment.isNotEmpty() &&
                    segment !in notePlaceholderValues &&
                    !structuredNoteMetadataRegex.matches(segment) &&
                    !importerDiscountNoteRegex.matches(segment)
            }
            .toList()
        return if (kept.isEmpty()) "" else kept.joinToString(" · ")
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
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(10.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    lineHeight = 13.sp
                ),
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
    subtitle: String = "",
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            if (subtitle.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
            if (actionLabel != null && onAction != null) {
                Spacer(modifier = Modifier.height(12.dp))
                FilledTonalButton(
                    onClick = onAction,
                    shape = CircleShape,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Text(
                        text = actionLabel,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

/**
 * 通用精简开关行组件，供设置页与配置面板复用。
 */
@Composable
fun CompactSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    detail: String? = null,
    enabled: Boolean = true
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                }
            )
            if (!detail.isNullOrBlank()) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (enabled) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                    }
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
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
 * 余白风格统一轻量圆角卡片容器。
 */
@Composable
fun YimuCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLowest,
    cornerRadius: Dp = 14.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    val baseModifier = modifier.fillMaxWidth()
    val border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.32f))
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
 * 分类与通用彩色卡通图标徽章：默认展示高清晰多色扁平卡通图，选中态叠加柔彩光环描边。
 */
@Composable
fun CategoryIconBadge(
    iconKey: String,
    colorHex: Long,
    size: Dp = 34.dp,
    iconSize: Dp = 17.dp,
    selected: Boolean = false,
    modifier: Modifier = Modifier
) {
    val accentColor = Color(colorHex)
    val resolvedIconSize = if (selected) {
        (size * 0.76f).coerceAtLeast(iconSize)
    } else {
        (size * 0.86f).coerceAtLeast(iconSize)
    }
    if (selected) {
        Surface(
            modifier = modifier.size(size),
            shape = CircleShape,
            color = accentColor.copy(alpha = 0.14f),
            border = BorderStroke(1.5.dp, accentColor)
        ) {
            Box(contentAlignment = Alignment.Center) {
                CartoonCategoryIcon(
                    iconKey = iconKey,
                    size = resolvedIconSize,
                    fallbackColorHex = colorHex
                )
            }
        }
    } else {
        Box(
            modifier = modifier.size(size),
            contentAlignment = Alignment.Center
        ) {
            CartoonCategoryIcon(
                iconKey = iconKey,
                size = resolvedIconSize,
                fallbackColorHex = colorHex
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
        com.yubrae.app.core.model.MoneyAmount.format(
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
 * 彩色标签胶囊组件（支持展示态、可选前置小图标与多选交互态）。
 */
@Composable
fun TagBadgeChip(
    name: String,
    colorHex: Long,
    selected: Boolean = false,
    compact: Boolean = false,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    iconKey: String = ""
) {
    val tagColor = Color(colorHex)
    val resolvedIcon = KairoIcons.tagIcon(iconKey)
    val shape = RoundedCornerShape(6.dp)
    val bgColor = when {
        selected -> tagColor.copy(alpha = 0.16f)
        onClick == null -> tagColor.copy(alpha = 0.10f)
        else -> MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f)
    }
    val fgColor = when {
        selected -> tagColor
        onClick == null -> tagColor
        else -> MaterialTheme.colorScheme.onSurface
    }
    val border = when {
        selected -> BorderStroke(1.dp, tagColor.copy(alpha = 0.85f))
        onClick == null -> BorderStroke(0.5.dp, tagColor.copy(alpha = 0.22f))
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
                horizontal = if (compact) 5.dp else 8.dp,
                vertical = if (compact) 1.5.dp else 4.dp
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (compact) 3.5.dp else 4.5.dp)
        ) {
            if (resolvedIcon != null) {
                Icon(
                    imageVector = resolvedIcon,
                    contentDescription = null,
                    tint = tagColor,
                    modifier = Modifier.size(11.dp)
                )
            } else {
                Surface(
                    shape = CircleShape,
                    color = tagColor,
                    modifier = Modifier.size(if (compact) 5.dp else 6.dp)
                ) {}
            }
            Text(
                text = name,
                style = if (compact) {
                    MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 13.sp)
                } else {
                    MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp, lineHeight = 14.sp)
                },
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
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}

/**
 * 共享分组图标选择器：内置紧凑搜索框、横向分组筛选胶囊与 6 列有限高紧凑网格。
 * 网格高度受限于 [gridMaxHeight]，可安全嵌入 ModalBottomSheet、AlertDialog 或垂直滚动页面。
 */
@Composable
fun GroupedIconPicker(
    groups: List<IconGroupSpec>,
    selectedKey: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    allowClear: Boolean = false,
    clearLabel: String = "无图标",
    gridMaxHeight: Dp = 240.dp,
    searchPlaceholder: String = "搜索图标名称或分组",
    useCartoonCategoryIcons: Boolean = false,
    useCartoonAccountIcons: Boolean = false
) {
    var searchQuery by remember(groups) { mutableStateOf("") }
    var selectedGroup by remember(groups) { mutableStateOf<String?>(null) }

    val normalizedQuery = searchQuery.trim().lowercase(Locale.ROOT)
    val filteredItems = remember(groups, selectedGroup, normalizedQuery) {
        val baseItems = if (selectedGroup == null) {
            groups.flatMap { it.items }
        } else {
            groups.firstOrNull { it.group == selectedGroup }?.items.orEmpty()
        }
        if (normalizedQuery.isEmpty()) {
            baseItems
        } else {
            val tokens = normalizedQuery.split(Regex("\\s+")).filter { it.isNotEmpty() }
            baseItems.filter { item ->
                val labelLower = item.label.lowercase(Locale.ROOT)
                val groupLower = item.group.lowercase(Locale.ROOT)
                val keyLower = item.key.lowercase(Locale.ROOT)
                tokens.all { token ->
                    labelLower.contains(token) ||
                        groupLower.contains(token) ||
                        keyLower.contains(token)
                }
            }
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.65f),
            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (searchQuery.isEmpty()) {
                        Text(
                            text = searchPlaceholder,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        cursorBrush = SolidColor(accentColor),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (searchQuery.isNotEmpty()) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "清空搜索",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .clickable { searchQuery = "" }
                    )
                }
            }
        }

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (allowClear) {
                item(key = "__clear__") {
                    PickerFilterPill(
                        label = clearLabel,
                        selected = selectedKey.isBlank(),
                        accentColor = accentColor,
                        onClick = { onSelect("") }
                    )
                }
            }
            item(key = "__all__") {
                PickerFilterPill(
                    label = "全部",
                    selected = selectedGroup == null,
                    accentColor = accentColor,
                    onClick = { selectedGroup = null }
                )
            }
            items(groups, key = { it.group }) { groupSpec ->
                PickerFilterPill(
                    label = groupSpec.group,
                    selected = selectedGroup == groupSpec.group,
                    accentColor = accentColor,
                    onClick = {
                        selectedGroup = if (selectedGroup == groupSpec.group) null else groupSpec.group
                    }
                )
            }
        }

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.55f),
            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.30f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (filteredItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "未找到匹配的图标",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(6),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 140.dp, max = gridMaxHeight.coerceAtLeast(140.dp)),
                    contentPadding = PaddingValues(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(filteredItems, key = { it.key }) { item ->
                        val isSelected = selectedKey.trim().equals(item.key, ignoreCase = true)
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onSelect(item.key) },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) accentColor.copy(alpha = 0.13f) else Color.Transparent,
                            border = if (isSelected) BorderStroke(1.2.dp, accentColor) else null
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 2.dp, vertical = 5.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Box(
                                    modifier = Modifier.size(30.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    when {
                                        useCartoonCategoryIcons -> CartoonCategoryIcon(
                                            iconKey = item.key,
                                            size = 26.dp,
                                            fallbackColorHex = KairoIcons.fallbackTagColorHex(item.group)
                                        )
                                        useCartoonAccountIcons -> CartoonAccountIcon(
                                            iconKey = item.key,
                                            accountName = item.label,
                                            colorHex = KairoIcons.fallbackTagColorHex(item.group),
                                            size = 28.dp
                                        )
                                        else -> Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (isSelected) accentColor else accentColor.copy(alpha = 0.10f)
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = item.vector,
                                                contentDescription = item.label,
                                                tint = if (isSelected) Color.White else accentColor,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = item.label,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 9.5.sp,
                                        lineHeight = 12.sp
                                    ),
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (isSelected) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PickerFilterPill(
    label: String,
    selected: Boolean,
    accentColor: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = if (selected) {
            accentColor.copy(alpha = 0.14f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.65f)
        },
        border = if (selected) {
            BorderStroke(1.dp, accentColor.copy(alpha = 0.75f))
        } else {
            null
        }
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.sp,
                lineHeight = 14.sp
            ),
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}

/**
 * 「余白 · YUBRAE」品牌徽标：直接展示方案 A1 桌面同源图标资产 `ic_yubrae_mark`。
 */
@Composable
fun YubraeBrandMark(
    size: Dp = 36.dp,
    modifier: Modifier = Modifier
) {
    Image(
        painter = painterResource(com.yubrae.app.R.drawable.ic_yubrae_mark),
        contentDescription = "余白",
        contentScale = ContentScale.Crop,
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.24f))
    )
}

/**
 * 0.5dp 轻盈极细列表分隔线。
 */
@Composable
fun CompactHairlineDivider(
    modifier: Modifier = Modifier,
    startIndent: Dp = 56.dp
) {
    HorizontalDivider(
        modifier = modifier.padding(start = startIndent),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    )
}
