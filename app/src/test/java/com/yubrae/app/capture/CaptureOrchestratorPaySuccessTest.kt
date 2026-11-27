package com.yubrae.app.capture

import com.yubrae.app.capture.screen.ScreenBillParser
import com.yubrae.app.capture.screen.ScreenPageKind
import com.yubrae.app.capture.screen.ScreenParseOutcome
import com.yubrae.app.capture.screen.ScreenSnapshot
import com.yubrae.app.core.contract.ParsedBillDraft
import com.yubrae.app.core.model.MoneyAmount
import com.yubrae.app.data.db.AccountAliasEntity
import com.yubrae.app.data.db.AccountEntity
import com.yubrae.app.data.db.AccountGroup
import com.yubrae.app.data.db.AliasMatchType
import com.yubrae.app.data.db.CaptureChannel
import com.yubrae.app.data.db.DefaultSeedData
import com.yubrae.app.data.db.ParseStatus
import com.yubrae.app.data.db.TransactionEntity
import com.yubrae.app.data.db.TransactionStatus
import com.yubrae.app.data.db.TransactionType
import com.yubrae.app.data.prefs.AppSettings
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class CaptureOrchestratorPaySuccessTest {

    private lateinit var accountDao: FakeAccountDao
    private lateinit var aliasDao: FakeAccountAliasDao
    private lateinit var categoryDao: FakeCategoryDao
    private lateinit var ruleDao: FakeParseRuleDao
    private lateinit var transactionDao: FakeTransactionDao
    private lateinit var rawLogDao: FakeRawCaptureLogDao
    private lateinit var ledgerService: FakeLedgerService

    private var promptTriggerCount = 0
    private var lastPromptDraft: ParsedBillDraft? = null
    private var currentSettings = AppSettings(floatingConfirmEnabled = true)

    @Before
    fun setup() = runTest {
        accountDao = FakeAccountDao()
        aliasDao = FakeAccountAliasDao()
        categoryDao = FakeCategoryDao()
        ruleDao = FakeParseRuleDao()
        transactionDao = FakeTransactionDao()
        rawLogDao = FakeRawCaptureLogDao()
        ledgerService = FakeLedgerService(accountDao, transactionDao, categoryDao)
        promptTriggerCount = 0
        lastPromptDraft = null
        currentSettings = AppSettings(floatingConfirmEnabled = true)

        CaptureTestFixtures.seedFixtures(accountDao, aliasDao, categoryDao, ruleDao)
        DefaultSeedData.ensurePresetCategories(categoryDao)
    }

    private fun buildOrchestrator(): CaptureOrchestrator {
        return CaptureOrchestrator(
            rawCaptureLogDao = rawLogDao,
            accountAliasDao = aliasDao,
            parseRuleDao = ruleDao,
            accountDao = accountDao,
            categoryDao = categoryDao,
            transactionDao = transactionDao,
            ledgerService = ledgerService,
            localRuleParser = LocalRuleParser(),
            llmBillParser = LlmBillParser(OkHttpClient()),
            deduplicator = CaptureDeduplicator(),
            settingsProvider = { currentSettings },
            floatingPromptLauncher = { _, draft, _, _ ->
                promptTriggerCount++
                lastPromptDraft = draft
            }
        )
    }

    @Test
    fun deduplicatesWeChatPaySuccessAdRefreshAfter17SecondsAndEnrichesFromBillDetail() = runTest {
        val orchestrator = buildOrchestrator()
        val baseTime = 1_732_500_050_370L // 模拟跨分钟边界（:50.370 -> +17.28s 后进入下一分钟 :07.651）
        val wechatWallet = accountDao.getByName("微信零钱")!!
        val threeMealsCategory = categoryDao.findByNameAndType("三餐外食", TransactionType.EXPENSE)!!
        val onlineShoppingCategory = categoryDao.findByNameAndType("网购百货", TransactionType.EXPENSE)!!
        val amountMicros = MoneyAmount.parseToMicros("5.50")!!

        // 1. 首次捕获微信支付成功页（逍遥优质胡辣汤（**华） / ¥5.50）
        val firstSnapshot = ScreenSnapshot(
            packageName = "com.tencent.mm",
            className = "com.tencent.mm.plugin.wallet_core.ui.WalletPaidSuccessUI",
            texts = listOf("支付成功", "逍遥优质胡辣汤（**华）", "¥5.50", "稍等一下...", "完成"),
            capturedAtMillis = baseTime
        )
        val firstOutcome = ScreenParseOutcome(
            pageKind = ScreenPageKind.PAY_SUCCESS,
            draft = ParsedBillDraft(
                type = TransactionType.EXPENSE,
                origAmountMicros = amountMicros,
                origCurrency = "CNY",
                accountId = wechatWallet.id,
                categoryId = threeMealsCategory.id,
                categoryName = threeMealsCategory.name,
                merchant = "逍遥优质胡辣汤（**华）",
                note = "零钱",
                timestamp = baseTime,
                confidence = 0.92f,
                parserUsed = "SCREEN_PAY_SUCCESS",
                tags = ""
            ),
            dedupFingerprint = "SCREEN:com.tencent.mm:EXPENSE:CNY:$amountMicros:${baseTime / 60_000L}:逍遥优质胡辣汤（**华）",
            summaryText = "支付成功\n逍遥优质胡辣汤（**华）\n¥5.50"
        )

        val r1 = orchestrator.handleScreenCapture(firstSnapshot, firstOutcome)
        assertEquals(ParseStatus.MATCHED_LOCAL, r1.status)
        assertFalse(r1.mergedIntoExisting)
        assertNotNull(r1.transactionId)
        assertEquals(1, promptTriggerCount)
        assertEquals(1, transactionDao.getAll().size)

        // 2. 17.28 秒后底部刷新“摇一摇有优惠 / 淘宝闪购可叠加立减”，即便带入误判标签/分类也不新建、不弹窗、不污染
        val adRefreshTime = baseTime + 17_281L
        val adSnapshot = firstSnapshot.copy(
            texts = listOf(
                "支付成功",
                "逍遥优质胡辣汤（**华）",
                "¥5.50",
                "摇一摇有优惠",
                "淘宝闪购可叠加立减",
                "摇一摇有优惠,淘宝闪购可叠加立减,查看",
                "完成"
            ),
            capturedAtMillis = adRefreshTime
        )
        val adOutcome = ScreenParseOutcome(
            pageKind = ScreenPageKind.PAY_SUCCESS,
            draft = ParsedBillDraft(
                type = TransactionType.EXPENSE,
                origAmountMicros = amountMicros,
                origCurrency = "CNY",
                accountId = wechatWallet.id,
                categoryId = onlineShoppingCategory.id,
                categoryName = onlineShoppingCategory.name,
                merchant = "逍遥优质胡辣汤（**华）",
                note = "摇一摇有优惠",
                timestamp = adRefreshTime,
                confidence = 0.92f,
                parserUsed = "SCREEN_PAY_SUCCESS",
                tags = "淘宝,网购"
            ),
            dedupFingerprint = "SCREEN:com.tencent.mm:EXPENSE:CNY:$amountMicros:${adRefreshTime / 60_000L}:逍遥优质胡辣汤（**华）",
            summaryText = "支付成功\n逍遥优质胡辣汤（**华）\n¥5.50\n摇一摇有优惠\n淘宝闪购可叠加立减"
        )

        val r2 = orchestrator.handleScreenCapture(adSnapshot, adOutcome)
        assertEquals(ParseStatus.MERGED_DEDUP, r2.status)
        assertTrue(r2.mergedIntoExisting)
        assertEquals(r1.transactionId, r2.transactionId)
        assertEquals(1, promptTriggerCount)
        assertEquals(1, transactionDao.getAll().size)

        val txAfterRefresh = transactionDao.getById(r1.transactionId!!)!!
        assertEquals(threeMealsCategory.id, txAfterRefresh.categoryId)
        assertEquals("", txAfterRefresh.tags)
        assertEquals("逍遥优质胡辣汤（**华）", txAfterRefresh.merchant)

        // 3. 35 秒后用户点开微信账单详情页（同店去脱敏 + 完整交易单号），合并到同一笔且补全单号与干净店名，不重复弹窗
        val detailTime = baseTime + 35_000L
        val detailOrderNo = "42000021562026112599887766"
        val detailSnapshot = ScreenSnapshot(
            packageName = "com.tencent.mm",
            className = "com.tencent.mm.plugin.webview.ui.tools.MMWebViewUI",
            texts = listOf(
                "账单详情",
                "逍遥优质胡辣汤",
                "-5.50",
                "当前状态", "支付成功",
                "支付方式", "零钱",
                "交易单号", detailOrderNo
            ),
            capturedAtMillis = detailTime
        )
        val detailOutcome = ScreenParseOutcome(
            pageKind = ScreenPageKind.BILL_DETAIL,
            orderNo = detailOrderNo,
            dedupFingerprint = "ORDER:$detailOrderNo",
            summaryText = "账单详情\n逍遥优质胡辣汤\n-5.50\n交易单号 $detailOrderNo",
            draft = ParsedBillDraft(
                type = TransactionType.EXPENSE,
                origAmountMicros = amountMicros,
                origCurrency = "CNY",
                accountId = wechatWallet.id,
                categoryId = threeMealsCategory.id,
                categoryName = threeMealsCategory.name,
                merchant = "逍遥优质胡辣汤",
                note = "零钱 · 单号:$detailOrderNo",
                timestamp = baseTime,
                confidence = 0.95f,
                parserUsed = "SCREEN_BILL_DETAIL",
                externalOrderNo = detailOrderNo
            )
        )

        val r3 = orchestrator.handleScreenCapture(detailSnapshot, detailOutcome)
        assertEquals(ParseStatus.MERGED_DEDUP, r3.status)
        assertTrue(r3.mergedIntoExisting)
        assertEquals(r1.transactionId, r3.transactionId)
        assertEquals(1, promptTriggerCount)
        assertEquals(1, transactionDao.getAll().size)

        val txAfterDetail = transactionDao.getById(r1.transactionId!!)!!
        assertEquals("逍遥优质胡辣汤", txAfterDetail.merchant)
        assertTrue(
            "Expected note to contain orderNo, got: ${txAfterDetail.note}",
            txAfterDetail.note.contains("单号:$detailOrderNo")
        )
        assertEquals(threeMealsCategory.id, txAfterDetail.categoryId)
        assertEquals("", txAfterDetail.tags)

        // 4. 再次进入同单号账单详情页，由强单号防重直接拦截
        val r4 = orchestrator.handleScreenCapture(
            detailSnapshot.copy(capturedAtMillis = detailTime + 15_000L),
            detailOutcome
        )
        assertEquals(ParseStatus.MERGED_DEDUP, r4.status)
        assertEquals(r1.transactionId, r4.transactionId)
        assertEquals(1, promptTriggerCount)
        assertEquals(1, transactionDao.getAll().size)
    }

    @Test
    fun doesNotMergePaySuccessOrBillDetailWithDifferentNonEmptyOrderNumbers() = runTest {
        val orchestrator = buildOrchestrator()
        val baseTime = 1_732_510_000_000L
        val wechatWallet = accountDao.getByName("微信零钱")!!
        val threeMealsCategory = categoryDao.findByNameAndType("三餐外食", TransactionType.EXPENSE)!!
        val amountMicros = MoneyAmount.parseToMicros("5.50")!!

        val firstSnapshot = ScreenSnapshot(
            packageName = "com.tencent.mm",
            className = "WalletOrderInfoNewUI",
            texts = listOf("账单详情", "逍遥优质胡辣汤", "-5.50", "交易单号", "4200001111111111"),
            capturedAtMillis = baseTime
        )
        val firstOutcome = ScreenParseOutcome(
            pageKind = ScreenPageKind.BILL_DETAIL,
            orderNo = "4200001111111111",
            dedupFingerprint = "ORDER:4200001111111111",
            draft = ParsedBillDraft(
                type = TransactionType.EXPENSE,
                origAmountMicros = amountMicros,
                origCurrency = "CNY",
                accountId = wechatWallet.id,
                categoryId = threeMealsCategory.id,
                categoryName = threeMealsCategory.name,
                merchant = "逍遥优质胡辣汤",
                note = "零钱 · 单号:4200001111111111",
                timestamp = baseTime,
                parserUsed = "SCREEN_BILL_DETAIL",
                externalOrderNo = "4200001111111111"
            )
        )
        val r1 = orchestrator.handleScreenCapture(firstSnapshot, firstOutcome)
        assertEquals(ParseStatus.MATCHED_LOCAL, r1.status)

        val secondSnapshot = ScreenSnapshot(
            packageName = "com.tencent.mm",
            className = "WalletPaidSuccessUI",
            texts = listOf("支付成功", "逍遥优质胡辣汤（**华）", "¥5.50", "交易单号", "4200002222222222"),
            capturedAtMillis = baseTime + 18_000L
        )
        val secondOutcome = ScreenParseOutcome(
            pageKind = ScreenPageKind.PAY_SUCCESS,
            orderNo = "4200002222222222",
            dedupFingerprint = "ORDER:4200002222222222",
            draft = ParsedBillDraft(
                type = TransactionType.EXPENSE,
                origAmountMicros = amountMicros,
                origCurrency = "CNY",
                accountId = wechatWallet.id,
                categoryId = threeMealsCategory.id,
                categoryName = threeMealsCategory.name,
                merchant = "逍遥优质胡辣汤（**华）",
                note = "零钱 · 单号:4200002222222222",
                timestamp = baseTime + 18_000L,
                parserUsed = "SCREEN_PAY_SUCCESS",
                externalOrderNo = "4200002222222222"
            )
        )
        val r2 = orchestrator.handleScreenCapture(secondSnapshot, secondOutcome)
        assertEquals(ParseStatus.MATCHED_LOCAL, r2.status)
        assertFalse(r2.mergedIntoExisting)
        assertNotEquals(r1.transactionId, r2.transactionId)
        assertEquals(2, transactionDao.getAll().size)
        assertEquals(2, promptTriggerCount)
    }

    @Test
    fun inheritsHistoricalConfirmedCategoryForSameNormalizedMerchantWhenCurrentIsFallback() = runTest {
        val orchestrator = buildOrchestrator()
        val baseTime = 1_732_520_000_000L
        val wechatWallet = accountDao.getByName("微信零钱")!!
        val threeMealsCategory = categoryDao.findByNameAndType("三餐外食", TransactionType.EXPENSE)!!
        val onlineShoppingCategory = categoryDao.findByNameAndType("网购百货", TransactionType.EXPENSE)!!
        val otherExpenseCategory = categoryDao.findByNameAndType("其他支出", TransactionType.EXPENSE)!!

        // 历史已确认交易：阿芳小店 -> 三餐外食；同时存在一条更新但未确认(PENDING)的错误分类记录应被忽略
        transactionDao.insert(
            TransactionEntity(
                type = TransactionType.EXPENSE,
                timestamp = baseTime - 86_400_000L,
                origAmountMicros = 1_200_000L,
                origCurrency = "CNY",
                accountId = wechatWallet.id,
                accountAmountMicros = 1_200_000L,
                accountCurrency = "CNY",
                categoryId = threeMealsCategory.id,
                merchant = "阿芳小店",
                sourceType = CaptureChannel.SCREEN,
                status = TransactionStatus.CONFIRMED
            )
        )
        transactionDao.insert(
            TransactionEntity(
                type = TransactionType.EXPENSE,
                timestamp = baseTime - 3_600_000L,
                origAmountMicros = 1_200_000L,
                origCurrency = "CNY",
                accountId = wechatWallet.id,
                accountAmountMicros = 1_200_000L,
                accountCurrency = "CNY",
                categoryId = onlineShoppingCategory.id,
                merchant = "阿芳小店",
                sourceType = CaptureChannel.SCREEN,
                status = TransactionStatus.PENDING
            )
        )
        // 为泛平台商户插一条已确认历史记录，验证不会触发错误记忆
        transactionDao.insert(
            TransactionEntity(
                type = TransactionType.EXPENSE,
                timestamp = baseTime - 7_200_000L,
                origAmountMicros = 2_000_000L,
                origCurrency = "CNY",
                accountId = wechatWallet.id,
                accountAmountMicros = 2_000_000L,
                accountCurrency = "CNY",
                categoryId = threeMealsCategory.id,
                merchant = "淘宝",
                sourceType = CaptureChannel.SCREEN,
                status = TransactionStatus.CONFIRMED
            )
        )

        // 1. 新捕获“阿芳小店（**芳）”，默认命中兜底分类“其他支出” -> 自动继承“三餐外食”
        val snapshot = ScreenSnapshot(
            packageName = "com.tencent.mm",
            className = "WalletPaidSuccessUI",
            texts = listOf("支付成功", "阿芳小店（**芳）", "¥8.00", "完成"),
            capturedAtMillis = baseTime
        )
        val outcomeWithFallback = ScreenParseOutcome(
            pageKind = ScreenPageKind.PAY_SUCCESS,
            draft = ParsedBillDraft(
                type = TransactionType.EXPENSE,
                origAmountMicros = 8_000_000L,
                origCurrency = "CNY",
                accountId = wechatWallet.id,
                categoryId = otherExpenseCategory.id,
                categoryName = otherExpenseCategory.name,
                merchant = "阿芳小店（**芳）",
                timestamp = baseTime,
                parserUsed = "SCREEN_PAY_SUCCESS"
            )
        )
        val result = orchestrator.handleScreenCapture(snapshot, outcomeWithFallback)
        assertEquals(ParseStatus.MATCHED_LOCAL, result.status)
        assertNotNull(result.transactionId)
        assertEquals(threeMealsCategory.id, result.draft?.categoryId)
        assertEquals("三餐外食", result.draft?.categoryName)
        assertEquals("三餐外食", lastPromptDraft?.categoryName)

        val savedTx = transactionDao.getById(result.transactionId!!)!!
        assertEquals(threeMealsCategory.id, savedTx.categoryId)
        val savedLog = rawLogDao.getById(result.logId)!!
        assertTrue(savedLog.extractedJson.contains("三餐外食"))

        // 2. 新捕获“阿芳小店（**芳）”且分类为 null -> 同样自动继承“三餐外食”
        val outcomeWithNullCategory = ScreenParseOutcome(
            pageKind = ScreenPageKind.PAY_SUCCESS,
            draft = ParsedBillDraft(
                type = TransactionType.EXPENSE,
                origAmountMicros = 9_000_000L,
                origCurrency = "CNY",
                accountId = wechatWallet.id,
                categoryId = null,
                categoryName = "",
                merchant = "阿芳小店（**芳）",
                timestamp = baseTime + 150_000L,
                parserUsed = "SCREEN_PAY_SUCCESS"
            )
        )
        val nullCategoryResult = orchestrator.handleScreenCapture(
            snapshot.copy(
                texts = listOf("支付成功", "阿芳小店（**芳）", "¥9.00", "完成"),
                capturedAtMillis = baseTime + 150_000L
            ),
            outcomeWithNullCategory
        )
        assertEquals(threeMealsCategory.id, nullCategoryResult.draft?.categoryId)
        assertEquals("三餐外食", nullCategoryResult.draft?.categoryName)
        assertEquals(threeMealsCategory.id, transactionDao.getById(nullCategoryResult.transactionId!!)!!.categoryId)

        // 3. 新捕获泛平台商户“淘宝”且分类为“其他支出” -> 不继承历史“三餐外食”
        val genericOutcome = ScreenParseOutcome(
            pageKind = ScreenPageKind.PAY_SUCCESS,
            draft = ParsedBillDraft(
                type = TransactionType.EXPENSE,
                origAmountMicros = 19_900_000L,
                origCurrency = "CNY",
                accountId = wechatWallet.id,
                categoryId = otherExpenseCategory.id,
                categoryName = otherExpenseCategory.name,
                merchant = "淘宝",
                timestamp = baseTime + 200_000L,
                parserUsed = "SCREEN_PAY_SUCCESS"
            )
        )
        val genericResult = orchestrator.handleScreenCapture(
            snapshot.copy(
                texts = listOf("支付成功", "淘宝", "¥19.90", "完成"),
                capturedAtMillis = baseTime + 200_000L
            ),
            genericOutcome
        )
        assertEquals(otherExpenseCategory.id, genericResult.draft?.categoryId)
        assertEquals(otherExpenseCategory.id, transactionDao.getById(genericResult.transactionId!!)!!.categoryId)
    }

    @Test
    fun mergesTaobaoFlashSaleOrderWithAlipayBillDetailPreservingStoreTakeoutAndUpgradingAccountAndOrderNo() = runTest {
        val mybankAccountId = accountDao.insert(
            AccountEntity(
                name = "支付宝·网商银行",
                groupType = AccountGroup.DEBIT,
                currencyCode = "CNY"
            )
        )
        aliasDao.insertAll(
            listOf(
                AccountAliasEntity(
                    accountId = mybankAccountId,
                    matchType = AliasMatchType.CARD_LAST4,
                    pattern = "6978",
                    priority = 95
                ),
                AccountAliasEntity(
                    accountId = mybankAccountId,
                    matchType = AliasMatchType.KEYWORD,
                    pattern = "网商银行",
                    priority = 110
                )
            )
        )

        val orchestrator = buildOrchestrator()
        val testZone = ZoneId.of("Asia/Shanghai")
        val screenParser = ScreenBillParser(zoneId = testZone)
        val accounts = accountDao.getAll()
        val aliases = aliasDao.getEnabled()
        val categories = categoryDao.getAll()
        val takeoutCategory = categoryDao.findByNameAndType("外卖", TransactionType.EXPENSE)!!

        val payTimeMillis = LocalDateTime.of(2026, 11, 27, 21, 5, 0)
            .atZone(testZone)
            .toInstant()
            .toEpochMilli()

        // 1. 先捕获淘宝闪购外卖进度页（真实门店 书亦烧仙草(蔡家店) + 外卖分类 + 无支付账户/单号）
        val taobaoOrderSnapshot = ScreenSnapshot(
            packageName = "com.taobao.taobao",
            className = "com.taobao.order.detail.OrderDetailActivity",
            texts = listOf(
                "淘宝闪购",
                "等待商家接单",
                "预计21:28送达",
                "书亦烧仙草(蔡家店) >",
                "烧仙草大杯 x1",
                "¥15.00",
                "打包费 ¥1.00",
                "实付 ¥12.66",
                "催单",
                "联系商家",
                "猜你喜欢",
                "蜜雪冰城(观音桥店)",
                "¥39.90",
                "加入购物车"
            ),
            capturedAtMillis = payTimeMillis
        )
        val taobaoOutcome = screenParser.parse(taobaoOrderSnapshot, aliases, accounts, categories)
        assertEquals(ScreenPageKind.ORDER_DETAIL, taobaoOutcome.pageKind)
        assertNotNull(taobaoOutcome.draft)
        assertEquals("书亦烧仙草(蔡家店)", taobaoOutcome.draft!!.merchant)
        assertEquals("外卖", taobaoOutcome.draft!!.categoryName)
        assertEquals(MoneyAmount.parseToMicros("12.66"), taobaoOutcome.draft!!.origAmountMicros)

        val r1 = orchestrator.handleScreenCapture(taobaoOrderSnapshot, taobaoOutcome)
        assertEquals(ParseStatus.MATCHED_LOCAL, r1.status)
        assertFalse(r1.mergedIntoExisting)
        assertNotNull(r1.transactionId)
        assertEquals(1, promptTriggerCount)
        assertEquals(1, transactionDao.getAll().size)

        // 2. 35 秒后用户点开支付宝账单详情（商户 淘宝闪购 + 支付宝·网商银行 + 单号）-> 自动融合升级不重复记账
        val alipayOrderNo = "202611272200148899001122"
        val alipayDetailSnapshot = ScreenSnapshot(
            packageName = "com.eg.android.AlipayGphone",
            className = "com.alipay.mobile.bill.detail.ui.BillDetailActivity",
            texts = listOf(
                "账单详情",
                "淘宝闪购",
                "支出12.66元",
                "交易成功",
                "支付时间", "2026-11-27 21:05:00",
                "付款方式", "网商银行储蓄卡(6978)",
                "订单号", alipayOrderNo
            ),
            capturedAtMillis = payTimeMillis + 35_000L
        )
        val alipayOutcome = screenParser.parse(alipayDetailSnapshot, aliases, accounts, categories)
        assertEquals(ScreenPageKind.BILL_DETAIL, alipayOutcome.pageKind)
        assertEquals("淘宝闪购", alipayOutcome.draft!!.merchant)
        assertEquals(mybankAccountId, alipayOutcome.draft!!.accountId)
        assertEquals(alipayOrderNo, alipayOutcome.orderNo)

        val r2 = orchestrator.handleScreenCapture(alipayDetailSnapshot, alipayOutcome)
        assertEquals(ParseStatus.MERGED_DEDUP, r2.status)
        assertTrue(r2.mergedIntoExisting)
        assertEquals(r1.transactionId, r2.transactionId)
        assertEquals(1, promptTriggerCount)
        assertEquals(1, transactionDao.getAll().size)

        val mergedTx = transactionDao.getById(r1.transactionId!!)!!
        assertEquals("书亦烧仙草(蔡家店)", mergedTx.merchant)
        assertEquals(takeoutCategory.id, mergedTx.categoryId)
        assertEquals(mybankAccountId, mergedTx.accountId)
        assertTrue("Expected note to retain orderNo, got: ${mergedTx.note}", mergedTx.note.contains("单号:$alipayOrderNo"))
        assertEquals(0L, accountDao.getByName("微信零钱")!!.balanceMicros)
        assertEquals(-MoneyAmount.parseToMicros("12.66")!!, accountDao.getById(mybankAccountId)!!.balanceMicros)

        // 3. 超过 2 分钟后（+5 分钟历史视角）再点开另一笔同流程的支付宝账单详情（付款方式无尾号仅“网商银行”），同样融合升级账户与单号
        val secondPayTime = LocalDateTime.of(2026, 11, 27, 21, 30, 0)
            .atZone(testZone)
            .toInstant()
            .toEpochMilli()
        val secondTaobaoSnapshot = taobaoOrderSnapshot.copy(
            texts = listOf(
                "淘宝闪购",
                "骑士到店",
                "预计21:55送达",
                "霸王茶姬(光环店)",
                "伯牙绝弦 x1",
                "实付 ¥18.80",
                "联系骑士",
                "猜你喜欢",
                "¥59.90"
            ),
            capturedAtMillis = secondPayTime
        )
        val secondTaobaoOutcome = screenParser.parse(secondTaobaoSnapshot, aliases, accounts, categories)
        val r3 = orchestrator.handleScreenCapture(secondTaobaoSnapshot, secondTaobaoOutcome)
        assertEquals(ParseStatus.MATCHED_LOCAL, r3.status)
        assertEquals(2, transactionDao.getAll().size)

        val secondAlipayOrderNo = "202611272200148899002233"
        val secondAlipayHistorical = ScreenSnapshot(
            packageName = "com.eg.android.AlipayGphone",
            className = "com.alipay.mobile.bill.detail.ui.BillDetailActivity",
            texts = listOf(
                "账单详情",
                "淘宝闪购",
                "支出18.80元",
                "交易成功",
                "支付时间", "2026-11-27 21:30:00",
                "付款方式", "网商银行",
                "订单号", secondAlipayOrderNo
            ),
            capturedAtMillis = secondPayTime + 300_000L
        )
        val secondAlipayOutcome = screenParser.parse(secondAlipayHistorical, aliases, accounts, categories)
        val r4 = orchestrator.handleScreenCapture(secondAlipayHistorical, secondAlipayOutcome)
        assertEquals(ParseStatus.MERGED_DEDUP, r4.status)
        assertTrue(r4.mergedIntoExisting)
        assertEquals(r3.transactionId, r4.transactionId)
        assertEquals(2, transactionDao.getAll().size)

        val secondMergedTx = transactionDao.getById(r3.transactionId!!)!!
        assertEquals("霸王茶姬(光环店)", secondMergedTx.merchant)
        assertEquals(takeoutCategory.id, secondMergedTx.categoryId)
        assertEquals(mybankAccountId, secondMergedTx.accountId)
        assertTrue(secondMergedTx.note.contains("单号:$secondAlipayOrderNo"))
    }

    @Test
    fun mergesAlipayTransientPaySuccessThenTaobaoFlashSaleAndAlipayBillDetailIntoSingleRecord() = runTest {
        val mybankAccountId = accountDao.insert(
            AccountEntity(
                name = "支付宝·网商银行",
                groupType = AccountGroup.DEBIT,
                currencyCode = "CNY"
            )
        )
        aliasDao.insert(
            AccountAliasEntity(
                accountId = mybankAccountId,
                matchType = AliasMatchType.CARD_LAST4,
                pattern = "6978",
                priority = 95
            )
        )

        val orchestrator = buildOrchestrator()
        val testZone = ZoneId.of("Asia/Shanghai")
        val screenParser = ScreenBillParser(zoneId = testZone)
        val accounts = accountDao.getAll()
        val aliases = aliasDao.getEnabled()
        val categories = categoryDao.getAll()
        val takeoutCategory = categoryDao.findByNameAndType("外卖", TransactionType.EXPENSE)!!

        val payTimeMillis = LocalDateTime.of(2026, 11, 27, 21, 5, 0)
            .atZone(testZone)
            .toInstant()
            .toEpochMilli()

        // 1. 先捕获支付宝瞬态付款成功弹层（无“完成”按钮，仅“付款成功 + ¥12.66 + 淘宝闪购 + 网商银行”）
        val alipayTransientSnapshot = ScreenSnapshot(
            packageName = "com.eg.android.AlipayGphone",
            className = "com.alipay.android.msp.ui.views.MspContainerActivity",
            texts = listOf("付款成功", "¥12.66", "淘宝闪购", "网商银行"),
            capturedAtMillis = payTimeMillis
        )
        val transientOutcome = screenParser.parse(alipayTransientSnapshot, aliases, accounts, categories)
        assertEquals(ScreenPageKind.PAY_SUCCESS, transientOutcome.pageKind)
        assertEquals("淘宝闪购", transientOutcome.draft!!.merchant)
        assertEquals(mybankAccountId, transientOutcome.draft!!.accountId)

        val r1 = orchestrator.handleScreenCapture(alipayTransientSnapshot, transientOutcome)
        assertEquals(ParseStatus.MATCHED_LOCAL, r1.status)
        assertFalse(r1.mergedIntoExisting)
        assertEquals(1, promptTriggerCount)
        assertEquals(1, transactionDao.getAll().size)

        // 2. 1 秒后跳回淘宝闪购外卖进度页 -> 自动融合升级真实门店“书亦烧仙草(蔡家店)”与“外卖”分类，保留“支付宝·网商银行”
        val taobaoProgressSnapshot = ScreenSnapshot(
            packageName = "com.taobao.taobao",
            className = "com.taobao.order.detail.OrderDetailActivity",
            texts = listOf(
                "淘宝闪购",
                "等待商家接单",
                "预计21:28送达",
                "书亦烧仙草(蔡家店)",
                "烧仙草大杯 x1",
                "实付 ¥12.66",
                "催单",
                "猜你喜欢",
                "¥39.90",
                "加入购物车"
            ),
            capturedAtMillis = payTimeMillis + 1_000L
        )
        val progressOutcome = screenParser.parse(taobaoProgressSnapshot, aliases, accounts, categories)
        assertEquals(ScreenPageKind.ORDER_DETAIL, progressOutcome.pageKind)

        val r2 = orchestrator.handleScreenCapture(taobaoProgressSnapshot, progressOutcome)
        assertEquals(ParseStatus.MERGED_DEDUP, r2.status)
        assertTrue(r2.mergedIntoExisting)
        assertEquals(r1.transactionId, r2.transactionId)
        assertEquals(1, promptTriggerCount)
        assertEquals(1, transactionDao.getAll().size)

        val txAfterProgress = transactionDao.getById(r1.transactionId!!)!!
        assertEquals("书亦烧仙草(蔡家店)", txAfterProgress.merchant)
        assertEquals(takeoutCategory.id, txAfterProgress.categoryId)
        assertEquals(mybankAccountId, txAfterProgress.accountId)

        // 3. 30 秒后再点开支付宝账单详情 -> 依然合并到同一笔，补全单号且不丢失门店/外卖分类
        val alipayOrderNo = "202611272200148899009988"
        val alipayDetailSnapshot = ScreenSnapshot(
            packageName = "com.eg.android.AlipayGphone",
            className = "com.alipay.mobile.bill.detail.ui.BillDetailActivity",
            texts = listOf(
                "账单详情",
                "淘宝闪购",
                "支出12.66元",
                "交易成功",
                "支付时间", "2026-11-27 21:05:00",
                "付款方式", "网商银行储蓄卡(6978)",
                "订单号", alipayOrderNo
            ),
            capturedAtMillis = payTimeMillis + 30_000L
        )
        val detailOutcome = screenParser.parse(alipayDetailSnapshot, aliases, accounts, categories)
        val r3 = orchestrator.handleScreenCapture(alipayDetailSnapshot, detailOutcome)
        assertEquals(ParseStatus.MERGED_DEDUP, r3.status)
        assertTrue(r3.mergedIntoExisting)
        assertEquals(r1.transactionId, r3.transactionId)
        assertEquals(1, promptTriggerCount)
        assertEquals(1, transactionDao.getAll().size)

        val finalTx = transactionDao.getById(r1.transactionId!!)!!
        assertEquals("书亦烧仙草(蔡家店)", finalTx.merchant)
        assertEquals(takeoutCategory.id, finalTx.categoryId)
        assertEquals(mybankAccountId, finalTx.accountId)
        assertTrue(finalTx.note.contains("单号:$alipayOrderNo"))
    }

    @Test
    fun mergesSamePackageTaobaoPaySuccessIntoTaobaoFlashSaleOrderDetail() = runTest {
        val orchestrator = buildOrchestrator()
        val screenParser = ScreenBillParser(zoneId = ZoneId.of("Asia/Shanghai"))
        val accounts = accountDao.getAll()
        val aliases = aliasDao.getEnabled()
        val categories = categoryDao.getAll()
        val takeoutCategory = categoryDao.findByNameAndType("外卖", TransactionType.EXPENSE)!!
        val baseTime = 1_732_712_700_000L

        val taobaoPaySuccess = ScreenSnapshot(
            packageName = "com.taobao.taobao",
            className = "com.taobao.pay.PaySuccessActivity",
            texts = listOf(
                "支付成功",
                "¥12.66",
                "查看订单",
                "返回首页",
                "猜你喜欢",
                "热门零食大礼包",
                "¥39.90",
                "加入购物车"
            ),
            capturedAtMillis = baseTime
        )
        val payOutcome = screenParser.parse(taobaoPaySuccess, aliases, accounts, categories)
        assertEquals(ScreenPageKind.PAY_SUCCESS, payOutcome.pageKind)
        val r1 = orchestrator.handleScreenCapture(taobaoPaySuccess, payOutcome)
        assertEquals(ParseStatus.MATCHED_LOCAL, r1.status)
        assertEquals(1, promptTriggerCount)

        val taobaoOrderDetail = ScreenSnapshot(
            packageName = "com.taobao.taobao",
            className = "com.taobao.order.detail.OrderDetailActivity",
            texts = listOf(
                "淘宝闪购",
                "骑士到店",
                "预计21:28送达",
                "书亦烧仙草(蔡家店)",
                "烧仙草大杯 x1",
                "实付 ¥12.66",
                "猜你喜欢",
                "¥39.90"
            ),
            capturedAtMillis = baseTime + 2_000L
        )
        val orderOutcome = screenParser.parse(taobaoOrderDetail, aliases, accounts, categories)
        assertEquals(ScreenPageKind.ORDER_DETAIL, orderOutcome.pageKind)
        val r2 = orchestrator.handleScreenCapture(taobaoOrderDetail, orderOutcome)
        assertEquals(ParseStatus.MERGED_DEDUP, r2.status)
        assertTrue(r2.mergedIntoExisting)
        assertEquals(r1.transactionId, r2.transactionId)
        assertEquals(1, promptTriggerCount)
        assertEquals(1, transactionDao.getAll().size)

        val upgraded = transactionDao.getById(r1.transactionId!!)!!
        assertEquals("书亦烧仙草(蔡家店)", upgraded.merchant)
        assertEquals(takeoutCategory.id, upgraded.categoryId)
    }
}
