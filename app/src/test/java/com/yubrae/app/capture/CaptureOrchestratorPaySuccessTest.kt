package com.yubrae.app.capture

import com.yubrae.app.capture.screen.ScreenPageKind
import com.yubrae.app.capture.screen.ScreenParseOutcome
import com.yubrae.app.capture.screen.ScreenSnapshot
import com.yubrae.app.core.contract.ParsedBillDraft
import com.yubrae.app.core.model.MoneyAmount
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
}
