package com.yubrae.app.capture

import com.yubrae.app.core.model.MoneyAmount
import com.yubrae.app.data.db.AccountAliasEntity
import com.yubrae.app.data.db.AccountEntity
import com.yubrae.app.data.db.CategoryEntity
import com.yubrae.app.data.db.TransactionType
import com.yubrae.app.data.prefs.AppSettings
import com.yubrae.app.testutil.TestHttpServer
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class SmartInputParserTest {

    private val testZone = ZoneId.of("Asia/Shanghai")
    private lateinit var smartParser: SmartInputParser
    private lateinit var accounts: List<AccountEntity>
    private lateinit var aliases: List<AccountAliasEntity>
    private lateinit var categories: List<CategoryEntity>

    private lateinit var server: TestHttpServer
    private var nextStatusCode: Int = 200
    private var nextResponseBody: String = ""
    private var lastRequestBody: String = ""

    @Before
    fun setup() = runTest {
        val accountDao = FakeAccountDao()
        val aliasDao = FakeAccountAliasDao()
        val categoryDao = FakeCategoryDao()
        val ruleDao = FakeParseRuleDao()
        CaptureTestFixtures.seedFixtures(accountDao, aliasDao, categoryDao, ruleDao)

        accounts = accountDao.getAll()
        aliases = aliasDao.getEnabled()
        categories = categoryDao.getAll()

        server = TestHttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            lastRequestBody = exchange.requestBody.readBytes().toString(StandardCharsets.UTF_8)
            val bytes = nextResponseBody.toByteArray(StandardCharsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "application/json; charset=utf-8")
            exchange.sendResponseHeaders(nextStatusCode, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()

        smartParser = SmartInputParser(
            llmBillParser = LlmBillParser(OkHttpClient()),
            zoneId = testZone
        )
    }

    @After
    fun teardown() {
        server.stop(0)
    }

    private fun wrapAssistantReply(content: String): String = buildJsonObject {
        put("id", "chatcmpl-smart-test")
        put("choices", buildJsonArray {
            add(buildJsonObject {
                put("index", 0)
                put("message", buildJsonObject {
                    put("role", "assistant")
                    put("content", content)
                })
            })
        })
    }.toString()

    @Test
    fun parsesSingleEntryAcrossMultipleForeignAndDomesticCurrencies() {
        val usdDraft = smartParser.parseLocalBatch("Amazon 买书 USD 19.99", accounts, categories, aliases).single()
        assertEquals(TransactionType.EXPENSE, usdDraft.type)
        assertEquals("USD", usdDraft.origCurrency)
        assertEquals(MoneyAmount.parseToMicros("19.99"), usdDraft.origAmountMicros)
        assertEquals("美元账户 (USD)", accounts.first { it.id == usdDraft.accountId }.name)
        assertEquals("日用购物", usdDraft.categoryName)

        val eurDraft = smartParser.parseLocalBatch("买咖啡 €12.50", accounts, categories, aliases).single()
        assertEquals("EUR", eurDraft.origCurrency)
        assertEquals(MoneyAmount.parseToMicros("12.50"), eurDraft.origAmountMicros)
        assertEquals("欧元账户 (EUR)", accounts.first { it.id == eurDraft.accountId }.name)
        assertEquals("餐饮美食", eurDraft.categoryName)

        val gbpDraft = smartParser.parseLocalBatch("超市购物 £8.40", accounts, categories, aliases).single()
        assertEquals("GBP", gbpDraft.origCurrency)
        assertEquals(MoneyAmount.parseToMicros("8.40"), gbpDraft.origAmountMicros)
        assertEquals("英镑账户 (GBP)", accounts.first { it.id == gbpDraft.accountId }.name)

        val jpyDraft = smartParser.parseLocalBatch("便利店买水 1500日元", accounts, categories, aliases).single()
        assertEquals("JPY", jpyDraft.origCurrency)
        assertEquals(MoneyAmount.parseToMicros("1500"), jpyDraft.origAmountMicros)

        val hkdDraft = smartParser.parseLocalBatch("买奶茶 HK$38", accounts, categories, aliases).single()
        assertEquals("HKD", hkdDraft.origCurrency)
        assertEquals(MoneyAmount.parseToMicros("38"), hkdDraft.origAmountMicros)
        assertEquals("餐饮美食", hkdDraft.categoryName)

        val rmbColloquial = smartParser.parseLocalBatch("吃拉面 18块5", accounts, categories, aliases).single()
        assertEquals("CNY", rmbColloquial.origCurrency)
        assertEquals(MoneyAmount.parseToMicros("18.5"), rmbColloquial.origAmountMicros)
        assertEquals("餐饮美食", rmbColloquial.categoryName)
    }

    @Test
    fun splitsCommaSeparatedMultiEntryMixedExpenseAndIncome() {
        val sample = "早上买咖啡 28.5 元微信付款，打车 35，Amazon 买书 USD 19.99 信用卡"
        val drafts = smartParser.parseLocalBatch(sample, accounts, categories, aliases)
        assertEquals(3, drafts.size)

        assertEquals(TransactionType.EXPENSE, drafts[0].type)
        assertEquals(MoneyAmount.parseToMicros("28.50"), drafts[0].origAmountMicros)
        assertEquals("CNY", drafts[0].origCurrency)
        assertEquals("微信零钱", accounts.first { it.id == drafts[0].accountId }.name)
        assertEquals("餐饮美食", drafts[0].categoryName)

        assertEquals(TransactionType.EXPENSE, drafts[1].type)
        assertEquals(MoneyAmount.parseToMicros("35"), drafts[1].origAmountMicros)
        assertEquals("CNY", drafts[1].origCurrency)
        assertEquals("交通出行", drafts[1].categoryName)

        assertEquals(TransactionType.EXPENSE, drafts[2].type)
        assertEquals(MoneyAmount.parseToMicros("19.99"), drafts[2].origAmountMicros)
        assertEquals("USD", drafts[2].origCurrency)
        assertEquals("招商信用卡6688", accounts.first { it.id == drafts[2].accountId }.name)
        assertEquals("日用购物", drafts[2].categoryName)

        val mixed = "早饭 18 元微信；收到报销 350 元支付宝\n然后发工资 12,500.00 元、淘宝退款 59.9"
        val mixedDrafts = smartParser.parseLocalBatch(mixed, accounts, categories, aliases)
        assertEquals(4, mixedDrafts.size)

        assertEquals(TransactionType.EXPENSE, mixedDrafts[0].type)
        assertEquals(MoneyAmount.parseToMicros("18.00"), mixedDrafts[0].origAmountMicros)
        assertEquals("餐饮美食", mixedDrafts[0].categoryName)

        assertEquals(TransactionType.INCOME, mixedDrafts[1].type)
        assertEquals(MoneyAmount.parseToMicros("350.00"), mixedDrafts[1].origAmountMicros)
        assertEquals("支付宝", accounts.first { it.id == mixedDrafts[1].accountId }.name)
        assertEquals("报销退款", mixedDrafts[1].categoryName)

        assertEquals(TransactionType.INCOME, mixedDrafts[2].type)
        assertEquals(MoneyAmount.parseToMicros("12500.00"), mixedDrafts[2].origAmountMicros)
        assertEquals("工资薪金", mixedDrafts[2].categoryName)

        assertEquals(TransactionType.INCOME, mixedDrafts[3].type)
        assertEquals(MoneyAmount.parseToMicros("59.90"), mixedDrafts[3].origAmountMicros)
        assertEquals("报销退款", mixedDrafts[3].categoryName)
    }

    @Test
    fun identifiesTransfersAndDistinguishesReceivedTransferAsIncome() {
        val giveDraft = smartParser.parseLocalBatch("转账给张三 200 元微信", accounts, categories, aliases).single()
        assertEquals(TransactionType.TRANSFER, giveDraft.type)
        assertEquals(MoneyAmount.parseToMicros("200"), giveDraft.origAmountMicros)
        assertEquals("微信零钱", accounts.first { it.id == giveDraft.accountId }.name)
        assertEquals("张三", giveDraft.merchant)

        val fromToDraft = smartParser.parseLocalBatch("从微信转到支付宝 500 元", accounts, categories, aliases).single()
        assertEquals(TransactionType.TRANSFER, fromToDraft.type)
        assertEquals(MoneyAmount.parseToMicros("500"), fromToDraft.origAmountMicros)
        assertEquals("微信零钱", accounts.first { it.id == fromToDraft.accountId }.name)
        assertTrue(fromToDraft.accountHint.contains("支付宝"))

        val repayDraft = smartParser.parseLocalBatch("还信用卡 3000 元", accounts, categories, aliases).single()
        assertEquals(TransactionType.TRANSFER, repayDraft.type)
        assertEquals(MoneyAmount.parseToMicros("3000"), repayDraft.origAmountMicros)

        val receivedDraft = smartParser.parseLocalBatch("转账收到 800 元支付宝", accounts, categories, aliases).single()
        assertEquals(TransactionType.INCOME, receivedDraft.type)
        assertEquals(MoneyAmount.parseToMicros("800"), receivedDraft.origAmountMicros)
        assertEquals("支付宝", accounts.first { it.id == receivedDraft.accountId }.name)
    }

    @Test
    fun resolvesRelativeYesterdayDayBeforeYesterdayAndTodayDates() {
        val nowMillis = LocalDateTime.of(2026, 11, 22, 14, 0, 0)
            .atZone(testZone)
            .toInstant()
            .toEpochMilli()

        val multiDayDrafts = smartParser.parseLocalBatch(
            text = "昨天买咖啡 28 元，打车 35，今天午饭 25 元",
            accounts = accounts,
            categories = categories,
            aliases = aliases,
            nowMillis = nowMillis
        )
        assertEquals(3, multiDayDrafts.size)

        val day0 = Instant.ofEpochMilli(multiDayDrafts[0].timestamp).atZone(testZone).toLocalDate()
        val day1 = Instant.ofEpochMilli(multiDayDrafts[1].timestamp).atZone(testZone).toLocalDate()
        val day2 = Instant.ofEpochMilli(multiDayDrafts[2].timestamp).atZone(testZone).toLocalDate()
        assertEquals(LocalDate.of(2026, 11, 21), day0)
        assertEquals(LocalDate.of(2026, 11, 21), day1)
        assertEquals(LocalDate.of(2026, 11, 22), day2)

        val earlierDrafts = smartParser.parseLocalBatch(
            text = "前天早上吃早餐 12 元，昨晚打车 42 元",
            accounts = accounts,
            categories = categories,
            aliases = aliases,
            nowMillis = nowMillis
        )
        assertEquals(2, earlierDrafts.size)
        val dt0 = Instant.ofEpochMilli(earlierDrafts[0].timestamp).atZone(testZone)
        val dt1 = Instant.ofEpochMilli(earlierDrafts[1].timestamp).atZone(testZone)
        assertEquals(LocalDate.of(2026, 11, 20), dt0.toLocalDate())
        assertEquals(8, dt0.hour)
        assertEquals(LocalDate.of(2026, 11, 21), dt1.toLocalDate())
        assertEquals(20, dt1.hour)
    }

    @Test
    fun matchesAccountNamesCardLast4AndCategoriesWithoutConfusingCardDigitsAsAmount() {
        val draft1 = smartParser.parseLocalBatch(
            "OpenAI 订阅 $20 招商信用卡6688",
            accounts,
            categories,
            aliases
        ).single()
        assertEquals(MoneyAmount.parseToMicros("20"), draft1.origAmountMicros)
        assertEquals("USD", draft1.origCurrency)
        assertEquals("招商信用卡6688", accounts.first { it.id == draft1.accountId }.name)
        assertEquals("数字订阅", draft1.categoryName)

        // 即使没有显式 "元" 单位且末尾含 4 位卡号，也不能把 6688 误当金额
        val draft2 = smartParser.parseLocalBatch(
            "买咖啡 28.5 招商信用卡6688",
            accounts,
            categories,
            aliases
        ).single()
        assertEquals(MoneyAmount.parseToMicros("28.5"), draft2.origAmountMicros)
        assertEquals("CNY", draft2.origCurrency)
        assertEquals("招商信用卡6688", accounts.first { it.id == draft2.accountId }.name)
        assertEquals("餐饮美食", draft2.categoryName)

        val draft3 = smartParser.parseLocalBatch(
            "Sainsbury 买菜 £15",
            accounts,
            categories,
            aliases
        ).single()
        assertEquals("GBP", draft3.origCurrency)
        assertEquals(MoneyAmount.parseToMicros("15"), draft3.origAmountMicros)
        assertEquals("英镑账户 (GBP)", accounts.first { it.id == draft3.accountId }.name)
        assertEquals("餐饮美食", draft3.categoryName)
    }

    @Test
    fun llmNaturalBatchAndReceiptVisionAndSmartInputFallbackWorkEndToEnd() = runTest {
        val port = server.address.port
        val settings = AppSettings(
            llmEnabled = true,
            llmBaseUrl = "http://127.0.0.1:$port/v1",
            llmApiKey = "sk-test",
            llmModel = "gpt-4o-mini"
        )
        val llmParser = LlmBillParser(OkHttpClient())
        val nowMillis = 1_763_800_000_000L

        // 1. 验证 LLM 多笔自然语言拆单 + 相对日期偏移
        nextStatusCode = 200
        nextResponseBody = wrapAssistantReply(
            """
            ```json
            [
              {
                "type": "EXPENSE",
                "amount": "28.50",
                "currency": "CNY",
                "merchant": "瑞幸咖啡",
                "categoryName": "餐饮美食",
                "accountName": "微信零钱",
                "note": "拿铁",
                "timestampOffsetDays": 0
              },
              {
                "type": "INCOME",
                "amount": "500.00",
                "currency": "CNY",
                "merchant": "公司报销",
                "categoryName": "报销退款",
                "accountName": "支付宝",
                "note": "差旅报销",
                "timestampOffsetDays": -1
              }
            ]
            ```
            """.trimIndent()
        )

        val batchResult = llmParser.parseNaturalLanguageBatch(
            text = "今天买瑞幸 28.5 微信，昨天收到差旅报销 500 支付宝",
            settings = settings,
            accounts = accounts,
            categories = categories,
            nowMillis = nowMillis
        )
        assertTrue(batchResult.isSuccess)
        val batchDrafts = batchResult.getOrThrow()
        assertEquals(2, batchDrafts.size)
        assertEquals(MoneyAmount.parseToMicros("28.50"), batchDrafts[0].origAmountMicros)
        assertEquals("微信零钱", accounts.first { it.id == batchDrafts[0].accountId }.name)
        assertEquals(nowMillis, batchDrafts[0].timestamp)
        assertEquals(TransactionType.INCOME, batchDrafts[1].type)
        assertEquals(
            Instant.ofEpochMilli(nowMillis).atZone(ZoneId.systemDefault()).toLocalDate().minusDays(1),
            Instant.ofEpochMilli(batchDrafts[1].timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
        )

        // 2. 验证 OpenAI Vision 多模态请求体格式与解析
        nextResponseBody = wrapAssistantReply(
            """
            {"isTransaction":true,"type":"EXPENSE","amount":"68.00","currency":"CNY","accountName":"招商信用卡6688","categoryName":"餐饮美食","merchant":"海底捞","note":"小票识别","confidence":0.94}
            """.trimIndent()
        )
        val visionResult = llmParser.parseReceiptImage(
            imageBase64 = "AAAA",
            mimeType = "image/jpeg",
            settings = settings,
            accounts = accounts,
            categories = categories,
            nowMillis = nowMillis
        )
        assertTrue(visionResult.isSuccess)
        val visionDraft = visionResult.getOrNull()
        assertNotNull(visionDraft)
        assertEquals("LLM_VISION", visionDraft!!.parserUsed)
        assertEquals("海底捞", visionDraft.merchant)
        assertEquals(MoneyAmount.parseToMicros("68.00"), visionDraft.origAmountMicros)

        val reqJson = Json.parseToJsonElement(lastRequestBody).jsonObject
        val userContent = reqJson["messages"]!!.jsonArray[1].jsonObject["content"]!!.jsonArray
        assertEquals("text", userContent[0].jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("image_url", userContent[1].jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals(
            "data:image/jpeg;base64,AAAA",
            userContent[1].jsonObject["image_url"]!!.jsonObject["url"]!!.jsonPrimitive.content
        )

        // 3. 验证 SmartInputParser 在 LLM 异常时自动优雅降级到本地批量解析
        nextStatusCode = 500
        nextResponseBody = """{"error":{"message":"Upstream failure"}}"""
        val fallbackDrafts = smartParser.parseSmartInput(
            text = "打车 35，买咖啡 28.5 元微信",
            settings = settings,
            accounts = accounts,
            categories = categories,
            aliases = aliases,
            nowMillis = nowMillis
        )
        assertEquals(2, fallbackDrafts.size)
        assertEquals("LOCAL_SMART_INPUT", fallbackDrafts[0].parserUsed)
        assertEquals(MoneyAmount.parseToMicros("35"), fallbackDrafts[0].origAmountMicros)
        assertEquals(MoneyAmount.parseToMicros("28.5"), fallbackDrafts[1].origAmountMicros)
    }

    @Test
    fun parsesChineseNumeralsAndColloquialMoneyExpressionsAndProtectsNonMoneyTokens() {
        val subwayCn = smartParser.parseLocalBatch("今天地铁花了九块钱", accounts, categories, aliases).single()
        assertEquals(TransactionType.EXPENSE, subwayCn.type)
        assertEquals("CNY", subwayCn.origCurrency)
        assertEquals(MoneyAmount.parseToMicros("9.00"), subwayCn.origAmountMicros)
        assertEquals("地铁", subwayCn.merchant)

        val subwayAr = smartParser.parseLocalBatch("今天地铁花了9块钱", accounts, categories, aliases).single()
        assertEquals(MoneyAmount.parseToMicros("9.00"), subwayAr.origAmountMicros)
        assertEquals("地铁", subwayAr.merchant)

        val coffee = smartParser.parseLocalBatch("瑞幸咖啡花了九块五", accounts, categories, aliases).single()
        assertEquals(MoneyAmount.parseToMicros("9.50"), coffee.origAmountMicros)

        val bag = smartParser.parseLocalBatch("便利店五毛钱", accounts, categories, aliases).single()
        assertEquals(MoneyAmount.parseToMicros("0.50"), bag.origAmountMicros)

        val hundred = smartParser.parseLocalBatch("聚餐两百五十元", accounts, categories, aliases).single()
        assertEquals(MoneyAmount.parseToMicros("250.00"), hundred.origAmountMicros)

        val verbOnly = smartParser.parseLocalBatch("打车花了三十", accounts, categories, aliases).single()
        assertEquals(MoneyAmount.parseToMicros("30.00"), verbOnly.origAmountMicros)

        val fx = smartParser.parseLocalBatch("亚马逊十美元", accounts, categories, aliases).single()
        assertEquals("USD", fx.origCurrency)
        assertEquals(MoneyAmount.parseToMicros("10.00"), fx.origAmountMicros)

        // 保护非金额数字（品牌名“九毛九”、星期几、地铁线路、商品数量、时间时长）
        val brand = smartParser.parseLocalBatch("在九毛九花了八十八", accounts, categories, aliases).single()
        assertEquals(MoneyAmount.parseToMicros("88.00"), brand.origAmountMicros)
        assertTrue(brand.merchant.contains("九毛九"))

        val weekday = smartParser.parseLocalBatch("星期三麦当劳花了三十八块五", accounts, categories, aliases).single()
        assertEquals(MoneyAmount.parseToMicros("38.50"), weekday.origAmountMicros)

        val line = smartParser.parseLocalBatch("二号线地铁花了五块", accounts, categories, aliases).single()
        assertEquals(MoneyAmount.parseToMicros("5.00"), line.origAmountMicros)

        val classifier = smartParser.parseLocalBatch("买了一块面包花了十二块钱", accounts, categories, aliases).single()
        assertEquals(MoneyAmount.parseToMicros("12.00"), classifier.origAmountMicros)

        val nonMoney = smartParser.parseLocalBatch("今天加班花了三个小时", accounts, categories, aliases)
        assertTrue(nonMoney.isEmpty())
    }
}
