package com.kairoledger.app.domain

import com.kairoledger.app.core.contract.RecordTransactionRequest
import com.kairoledger.app.core.model.MoneyAmount
import com.kairoledger.app.data.db.AccountEntity
import com.kairoledger.app.data.db.AccountGroup
import com.kairoledger.app.data.db.BudgetEntity
import com.kairoledger.app.data.db.CaptureChannel
import com.kairoledger.app.data.db.CategoryEntity
import com.kairoledger.app.data.db.ExchangeRateEntity
import com.kairoledger.app.data.db.RecurringRuleEntity
import com.kairoledger.app.data.db.TransactionStatus
import com.kairoledger.app.data.db.TransactionType
import com.kairoledger.app.data.network.ExchangeRateApi
import com.kairoledger.app.data.prefs.AppSettings
import com.kairoledger.app.data.repository.ExchangeRateRepositoryImpl
import com.kairoledger.app.data.repository.LedgerRepositoryImpl
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class LedgerRepositoryTest {

    private val zoneId: ZoneId = ZoneId.of("Asia/Shanghai")
    private var nowMillis: Long = millisOf(2026, 11, 22, 10, 0)
    private var baseCurrencySetting: String = "CNY"

    private lateinit var accountDao: FakeAccountDao
    private lateinit var transactionDao: FakeTransactionDao
    private lateinit var categoryDao: FakeCategoryDao
    private lateinit var budgetDao: FakeBudgetDao
    private lateinit var recurringRuleDao: FakeRecurringRuleDao
    private lateinit var exchangeRateDao: FakeExchangeRateDao
    private lateinit var rateProvider: ExchangeRateRepositoryImpl
    private lateinit var ledger: LedgerRepositoryImpl

    private fun millisOf(year: Int, month: Int, day: Int, hour: Int = 12, minute: Int = 0): Long =
        LocalDateTime.of(year, month, day, hour, minute).atZone(zoneId).toInstant().toEpochMilli()

    @Before
    fun setUp() {
        accountDao = FakeAccountDao()
        transactionDao = FakeTransactionDao()
        categoryDao = FakeCategoryDao()
        budgetDao = FakeBudgetDao()
        recurringRuleDao = FakeRecurringRuleDao()
        exchangeRateDao = FakeExchangeRateDao()

        rateProvider = ExchangeRateRepositoryImpl(
            exchangeRateDao = exchangeRateDao,
            exchangeRateApi = ExchangeRateApi(OkHttpClient()),
            currentDateProvider = { LocalDate.of(2026, 11, 22) },
            clockMillisProvider = { nowMillis }
        )

        ledger = LedgerRepositoryImpl(
            accountDao = accountDao,
            transactionDao = transactionDao,
            categoryDao = categoryDao,
            budgetDao = budgetDao,
            recurringRuleDao = recurringRuleDao,
            exchangeRateProvider = rateProvider,
            getSettings = { AppSettings(baseCurrency = baseCurrencySetting) },
            zoneId = zoneId,
            clockMillisProvider = { nowMillis }
        )
    }

    @Test
    fun previewConversion_handlesAutoManualAmountAndManualRate() = runTest {
        val cnyAccId = accountDao.insert(
            AccountEntity(name = "储蓄卡", groupType = AccountGroup.DEBIT, currencyCode = "CNY", balanceMicros = 10_000_000L)
        )
        val eurAccId = accountDao.insert(
            AccountEntity(name = "欧元卡", groupType = AccountGroup.FOREIGN, currencyCode = "EUR", balanceMicros = 5_000_000L)
        )
        exchangeRateDao.upsertAll(
            listOf(
                ExchangeRateEntity(baseCurrency = "USD", targetCurrency = "CNY", rateMicros = 7_245_000L, date = "2026-11-22", source = "FRANKFURTER"),
                ExchangeRateEntity(baseCurrency = "USD", targetCurrency = "EUR", rateMicros = 950_000L, date = "2026-11-22", source = "FRANKFURTER")
            )
        )

        // 1. Live conversion 15.00 USD -> CNY account
        val orig15Usd = MoneyAmount.parseToMicros("15.00")!!
        val autoPreview = ledger.previewConversion(orig15Usd, "USD", cnyAccId)
        assertEquals(7_245_000L, autoPreview.rateToAccountMicros)
        assertEquals(1_086_750L, autoPreview.accountAmountMicros)
        assertEquals(1_086_750L, autoPreview.baseAmountMicros)
        assertEquals("FRANKFURTER", autoPreview.rateSource)

        // 2. Manual bank settlement amount 109.20 CNY -> back-calculates rate 7.2800
        val actual10920Cny = MoneyAmount.parseToMicros("109.20")!!
        val manualAmountPreview = ledger.previewConversion(
            origAmountMicros = orig15Usd,
            origCurrency = "USD",
            accountId = cnyAccId,
            manualAccountAmountMicros = actual10920Cny
        )
        assertEquals(7_280_000L, manualAmountPreview.rateToAccountMicros)
        assertEquals(7_280_000L, manualAmountPreview.rateToBaseMicros)
        assertEquals(actual10920Cny, manualAmountPreview.accountAmountMicros)
        assertEquals(actual10920Cny, manualAmountPreview.baseAmountMicros)

        // 3. Explicit rate for USD -> EUR account while base is CNY
        val manualRatePreview = ledger.previewConversion(
            origAmountMicros = MoneyAmount.parseToMicros("100.00")!!,
            origCurrency = "USD",
            accountId = eurAccId,
            manualRateToAccountMicros = 960_000L
        )
        assertEquals(MoneyAmount.parseToMicros("96.00")!!, manualRatePreview.accountAmountMicros)
        assertEquals(960_000L, manualRatePreview.rateToAccountMicros)
        assertEquals(7_245_000L, manualRatePreview.rateToBaseMicros)
        assertEquals(MoneyAmount.parseToMicros("724.50")!!, manualRatePreview.baseAmountMicros)
    }

    @Test
    fun saveEditAndDeleteTransaction_rollsBackAndAppliesBalancesAtomically() = runTest {
        val cnyId = accountDao.insert(
            AccountEntity(name = "微信", groupType = AccountGroup.WALLET, currencyCode = "CNY", balanceMicros = 10_000_000L) // 1000.00
        )
        val usdId = accountDao.insert(
            AccountEntity(name = "美元账户", groupType = AccountGroup.FOREIGN, currencyCode = "USD", balanceMicros = 2_000_000L) // 200.00
        )
        exchangeRateDao.upsert(
            ExchangeRateEntity(baseCurrency = "USD", targetCurrency = "CNY", rateMicros = 7_200_000L, date = "2026-11-22", source = "FRANKFURTER")
        )

        // Expense: $10.00 USD paid from CNY wallet using manual settled amount 72.50 CNY
        val expResult = ledger.saveTransaction(
            RecordTransactionRequest(
                type = TransactionType.EXPENSE,
                timestamp = nowMillis,
                origAmountMicros = MoneyAmount.parseToMicros("10.00")!!,
                origCurrency = "USD",
                accountId = cnyId,
                manualAccountAmountMicros = MoneyAmount.parseToMicros("72.50")!!,
                merchant = "OpenAI"
            )
        )
        assertTrue(expResult.isSuccess)
        val expTx = expResult.getOrThrow()
        assertEquals(7_250_000L, expTx.rateToAccountMicros)
        assertEquals(7_250_000L, expTx.rateToBaseMicros)
        assertEquals(MoneyAmount.parseToMicros("927.50")!!, accountDao.getById(cnyId)!!.balanceMicros)

        // Edit existing expense: move to USD account, amount $20.00 USD -> CNY restored to 1000.00, USD becomes 180.00
        val edited = ledger.saveTransaction(
            RecordTransactionRequest(
                existingId = expTx.id,
                type = TransactionType.EXPENSE,
                timestamp = nowMillis,
                origAmountMicros = MoneyAmount.parseToMicros("20.00")!!,
                origCurrency = "USD",
                accountId = usdId
            )
        )
        assertTrue(edited.isSuccess)
        assertEquals(MoneyAmount.parseToMicros("1000.00")!!, accountDao.getById(cnyId)!!.balanceMicros)
        assertEquals(MoneyAmount.parseToMicros("180.00")!!, accountDao.getById(usdId)!!.balanceMicros)

        // Transfer cross-currency: 720.00 CNY -> USD account with manualToAmountMicros = $99.50
        val transfer = ledger.saveTransaction(
            RecordTransactionRequest(
                type = TransactionType.TRANSFER,
                timestamp = nowMillis,
                origAmountMicros = MoneyAmount.parseToMicros("720.00")!!,
                origCurrency = "CNY",
                accountId = cnyId,
                toAccountId = usdId,
                manualToAmountMicros = MoneyAmount.parseToMicros("99.50")!!
            )
        ).getOrThrow()
        assertEquals(MoneyAmount.parseToMicros("280.00")!!, accountDao.getById(cnyId)!!.balanceMicros)
        assertEquals(MoneyAmount.parseToMicros("279.50")!!, accountDao.getById(usdId)!!.balanceMicros)

        // Delete transfer -> restores both accounts
        assertTrue(ledger.deleteTransaction(transfer.id).isSuccess)
        assertEquals(MoneyAmount.parseToMicros("1000.00")!!, accountDao.getById(cnyId)!!.balanceMicros)
        assertEquals(MoneyAmount.parseToMicros("180.00")!!, accountDao.getById(usdId)!!.balanceMicros)

        // Failed transfer (same account) does not alter balances
        val badTransfer = ledger.saveTransaction(
            RecordTransactionRequest(
                type = TransactionType.TRANSFER,
                origAmountMicros = 10_000L,
                origCurrency = "CNY",
                accountId = cnyId,
                toAccountId = cnyId
            )
        )
        assertTrue(badTransfer.isFailure)
        assertEquals(MoneyAmount.parseToMicros("1000.00")!!, accountDao.getById(cnyId)!!.balanceMicros)
    }

    @Test
    fun calibrateAccountBalance_createsAdjustTxAndSupportsDeleteRollback() = runTest {
        val accId = accountDao.insert(
            AccountEntity(name = "现金", groupType = AccountGroup.WALLET, currencyCode = "CNY", balanceMicros = 500_000L) // 50.00
        )

        val upTx = ledger.calibrateAccountBalance(accId, MoneyAmount.parseToMicros("125.50")!!).getOrThrow()
        assertEquals(TransactionType.ADJUST, upTx.type)
        assertEquals(MoneyAmount.parseToMicros("75.50")!!, upTx.origAmountMicros)
        assertEquals(MoneyAmount.parseToMicros("125.50")!!, accountDao.getById(accId)!!.balanceMicros)

        val downTx = ledger.calibrateAccountBalance(accId, MoneyAmount.parseToMicros("100.00")!!).getOrThrow()
        assertEquals(MoneyAmount.parseToMicros("-25.50")!!, downTx.origAmountMicros)
        assertEquals(MoneyAmount.parseToMicros("100.00")!!, accountDao.getById(accId)!!.balanceMicros)

        // Delete downward adjust restores 125.50
        ledger.deleteTransaction(downTx.id)
        assertEquals(MoneyAmount.parseToMicros("125.50")!!, accountDao.getById(accId)!!.balanceMicros)
    }

    @Test
    fun computeNetWorth_aggregatesAssetsLiabilitiesAndIgnoresExcludedOrArchived() = runTest {
        exchangeRateDao.upsert(
            ExchangeRateEntity(baseCurrency = "USD", targetCurrency = "CNY", rateMicros = 7_000_000L, date = "2026-11-22", source = "CUSTOM")
        )
        accountDao.insert(
            AccountEntity(name = "储蓄卡", groupType = AccountGroup.DEBIT, currencyCode = "CNY", balanceMicros = MoneyAmount.parseToMicros("1000.00")!!)
        )
        accountDao.insert(
            AccountEntity(name = "美元卡", groupType = AccountGroup.FOREIGN, currencyCode = "USD", balanceMicros = MoneyAmount.parseToMicros("100.00")!!) // 700 CNY
        )
        accountDao.insert(
            AccountEntity(name = "信用卡", groupType = AccountGroup.CREDIT, currencyCode = "CNY", balanceMicros = MoneyAmount.parseToMicros("-300.00")!!) // -300 CNY debt
        )
        accountDao.insert(
            AccountEntity(name = "不计入净资产", groupType = AccountGroup.DEBIT, currencyCode = "CNY", balanceMicros = MoneyAmount.parseToMicros("5000.00")!!, includeInNetWorth = false)
        )
        accountDao.insert(
            AccountEntity(name = "已归档", groupType = AccountGroup.DEBIT, currencyCode = "CNY", balanceMicros = MoneyAmount.parseToMicros("9000.00")!!, archived = true)
        )

        val summaryCny = ledger.computeNetWorth("CNY")
        assertEquals("CNY", summaryCny.displayCurrency)
        assertEquals(MoneyAmount.parseToMicros("1700.00")!!, summaryCny.totalAssetsMicros)
        assertEquals(MoneyAmount.parseToMicros("300.00")!!, summaryCny.totalLiabilitiesMicros)
        assertEquals(MoneyAmount.parseToMicros("1400.00")!!, summaryCny.netWorthMicros)

        val summaryUsd = ledger.computeNetWorth("USD")
        assertEquals("USD", summaryUsd.displayCurrency)
        // 1000 CNY -> USD = 142.8571 -> 142.86 USD + 100.00 USD = 242.86 USD
        val expected1000CnyInUsd = MoneyAmount.convert(MoneyAmount.parseToMicros("1000.00")!!, MoneyAmount.invertRate(7_000_000L))
        val expected300CnyInUsd = MoneyAmount.convert(MoneyAmount.parseToMicros("300.00")!!, MoneyAmount.invertRate(7_000_000L))
        assertEquals(expected1000CnyInUsd + MoneyAmount.parseToMicros("100.00")!!, summaryUsd.totalAssetsMicros)
        assertEquals(expected300CnyInUsd, summaryUsd.totalLiabilitiesMicros)
        assertEquals(summaryUsd.totalAssetsMicros - summaryUsd.totalLiabilitiesMicros, summaryUsd.netWorthMicros)
    }

    @Test
    fun historicalReports_freezeBaseCurrencyRateAndExcludePendingUntilConfirmed() = runTest {
        val foodParentId = categoryDao.insert(
            CategoryEntity(name = "餐饮美食", type = TransactionType.EXPENSE, colorHex = 0xFFEA580CL)
        )
        val coffeeId = categoryDao.insert(
            CategoryEntity(parentId = foodParentId, name = "咖啡茶饮", type = TransactionType.EXPENSE, colorHex = 0xFFC2410CL)
        )
        val salaryId = categoryDao.insert(
            CategoryEntity(name = "工资", type = TransactionType.INCOME, colorHex = 0xFF16A34AL)
        )
        val usdId = accountDao.insert(
            AccountEntity(name = "美元账户", groupType = AccountGroup.FOREIGN, currencyCode = "USD", balanceMicros = MoneyAmount.parseToMicros("1000.00")!!)
        )
        val cnyId = accountDao.insert(
            AccountEntity(name = "工资卡", groupType = AccountGroup.DEBIT, currencyCode = "CNY", balanceMicros = MoneyAmount.parseToMicros("0.00")!!)
        )

        // Record $100.00 USD expense in October when USD/CNY = 7.10
        val octTime = millisOf(2026, 10, 15, 14, 0)
        exchangeRateDao.upsert(
            ExchangeRateEntity(baseCurrency = "USD", targetCurrency = "CNY", rateMicros = 7_100_000L, date = "2026-10-15", source = "FRANKFURTER")
        )
        ledger.saveTransaction(
            RecordTransactionRequest(
                type = TransactionType.EXPENSE,
                timestamp = octTime,
                origAmountMicros = MoneyAmount.parseToMicros("100.00")!!,
                origCurrency = "USD",
                accountId = usdId,
                categoryId = coffeeId,
                status = TransactionStatus.CONFIRMED
            )
        ).getOrThrow()

        // Rate shifts dramatically in November to USD/CNY = 7.50!
        exchangeRateDao.upsert(
            ExchangeRateEntity(baseCurrency = "USD", targetCurrency = "CNY", rateMicros = 7_500_000L, date = "2026-11-22", source = "FRANKFURTER")
        )

        // Add one CONFIRMED CNY expense (290.00) and one PENDING CNY expense (500.00) in Nov
        val novTime = millisOf(2026, 11, 10, 9, 0)
        ledger.saveTransaction(
            RecordTransactionRequest(
                type = TransactionType.EXPENSE,
                timestamp = novTime,
                origAmountMicros = MoneyAmount.parseToMicros("290.00")!!,
                origCurrency = "CNY",
                accountId = cnyId,
                categoryId = coffeeId,
                status = TransactionStatus.CONFIRMED
            )
        ).getOrThrow()
        ledger.saveTransaction(
            RecordTransactionRequest(
                type = TransactionType.EXPENSE,
                timestamp = novTime,
                origAmountMicros = MoneyAmount.parseToMicros("500.00")!!,
                origCurrency = "CNY",
                accountId = cnyId,
                categoryId = coffeeId,
                status = TransactionStatus.PENDING
            )
        ).getOrThrow()
        ledger.saveTransaction(
            RecordTransactionRequest(
                type = TransactionType.INCOME,
                timestamp = novTime,
                origAmountMicros = MoneyAmount.parseToMicros("5000.00")!!,
                origCurrency = "CNY",
                accountId = cnyId,
                categoryId = salaryId,
                status = TransactionStatus.CONFIRMED
            )
        ).getOrThrow()

        // Monthly trend in CNY: October MUST remain 710.00 CNY (not 750.00 CNY!), November excludes PENDING 500.00
        val trend = ledger.computeMonthlyTrend(monthsBack = 2, displayCurrency = "CNY")
        assertEquals(2, trend.size)
        assertEquals(10, trend[0].month)
        assertEquals(MoneyAmount.parseToMicros("710.00")!!, trend[0].expenseBaseMicros)
        assertEquals(11, trend[1].month)
        assertEquals(MoneyAmount.parseToMicros("290.00")!!, trend[1].expenseBaseMicros)
        assertEquals(MoneyAmount.parseToMicros("5000.00")!!, trend[1].incomeBaseMicros)

        // Category stats across Oct+Nov: 710 + 290 = 1000.00 CNY (100% in coffeeId, ratio 1.0f)
        val catStats = ledger.computeCategoryStats(
            startMillis = millisOf(2026, 10, 1, 0, 0),
            endMillis = millisOf(2026, 11, 30, 23, 59),
            type = TransactionType.EXPENSE,
            displayCurrency = "CNY"
        )
        assertEquals(1, catStats.size)
        assertEquals(MoneyAmount.parseToMicros("1000.00")!!, catStats[0].totalBaseMicros)
        assertEquals(2, catStats[0].count)
        assertEquals(1.0f, catStats[0].percentage, 0.0001f)

        // Budget on parent category 餐饮美食 (limit 250.00 CNY) aggregates child category 咖啡茶饮 (290.00 CNY in Nov -> overBudget!)
        budgetDao.insert(
            BudgetEntity(
                name = "餐饮预算",
                categoryId = foodParentId,
                period = "MONTHLY",
                limitAmountMicros = MoneyAmount.parseToMicros("250.00")!!,
                currencyCode = "CNY",
                enabled = true
            )
        )
        val budgetBeforeConfirm = ledger.computeBudgetProgress(2026, 11)
        assertEquals(1, budgetBeforeConfirm.size)
        assertEquals(MoneyAmount.parseToMicros("290.00")!!, budgetBeforeConfirm[0].spentInBudgetCurrencyMicros)
        assertTrue(budgetBeforeConfirm[0].overBudget)
        assertEquals(1.16f, budgetBeforeConfirm[0].progressRatio, 0.001f)

        // Confirm all pending -> count 1, Nov expense becomes 790.00
        val confirmedCount = ledger.confirmAllPending().getOrThrow()
        assertEquals(1, confirmedCount)
        val budgetAfterConfirm = ledger.computeBudgetProgress(2026, 11)
        assertEquals(MoneyAmount.parseToMicros("790.00")!!, budgetAfterConfirm[0].spentInBudgetCurrencyMicros)

        // When displayCurrency != tx.baseCurrency ("USD" vs "CNY"), frozen base amount is converted once to USD
        val trendInUsd = ledger.computeMonthlyTrend(monthsBack = 2, displayCurrency = "USD")
        val cnyToUsdRate = rateProvider.getRate("CNY", "USD").rateMicros
        assertEquals(MoneyAmount.convert(MoneyAmount.parseToMicros("710.00")!!, cnyToUsdRate), trendInUsd[0].expenseBaseMicros)
        assertFalse(trendInUsd[0].expenseBaseMicros == MoneyAmount.parseToMicros("100.00")!!)
    }

    @Test
    fun processDueRecurringRules_handlesMonthlyEndClampingWeeklyAndYearlyLeapDay() = runTest {
        val accId = accountDao.insert(
            AccountEntity(name = "银行卡", groupType = AccountGroup.DEBIT, currencyCode = "CNY", balanceMicros = MoneyAmount.parseToMicros("1000.00")!!)
        )
        val jan31 = millisOf(2025, 1, 31, 9, 0)
        val monthlyRuleId = recurringRuleDao.insert(
            RecurringRuleEntity(
                name = "月末会员",
                type = TransactionType.EXPENSE,
                amountMicros = MoneyAmount.parseToMicros("30.00")!!,
                currencyCode = "CNY",
                accountId = accId,
                cycleType = "MONTHLY",
                dayOfCycle = 31,
                nextRunEpochMillis = jan31,
                enabled = true
            )
        )

        // Trigger on Jan 31 -> 1 tx, nextRun = Feb 28
        assertEquals(1, ledger.processDueRecurringRules(jan31))
        val afterJan = recurringRuleDao.getAll().first { it.id == monthlyRuleId }
        assertEquals(millisOf(2025, 2, 28, 9, 0), afterJan.nextRunEpochMillis)

        // Trigger on Feb 28 -> 1 tx, nextRun MUST restore to Mar 31 (not Mar 28!)
        assertEquals(1, ledger.processDueRecurringRules(millisOf(2025, 2, 28, 12, 0)))
        val afterFeb = recurringRuleDao.getAll().first { it.id == monthlyRuleId }
        assertEquals(millisOf(2025, 3, 31, 9, 0), afterFeb.nextRunEpochMillis)

        // Trigger on Mar 31 -> 1 tx, nextRun clamps to Apr 30
        assertEquals(1, ledger.processDueRecurringRules(millisOf(2025, 3, 31, 12, 0)))
        val afterMar = recurringRuleDao.getAll().first { it.id == monthlyRuleId }
        assertEquals(millisOf(2025, 4, 30, 9, 0), afterMar.nextRunEpochMillis)

        assertEquals(MoneyAmount.parseToMicros("910.00")!!, accountDao.getById(accId)!!.balanceMicros)

        // Verify Weekly and Yearly leap-day schedule calculator
        val weeklyRule = RecurringRuleEntity(
            name = "周费",
            amountMicros = 10_000L,
            currencyCode = "CNY",
            accountId = accId,
            cycleType = "WEEKLY",
            dayOfCycle = 1,
            nextRunEpochMillis = millisOf(2026, 11, 1, 8, 30)
        )
        assertEquals(
            millisOf(2026, 11, 8, 8, 30),
            RecurringScheduleCalculator.nextRunEpochMillis(weeklyRule, zoneId = zoneId)
        )

        val leapYearlyRule = RecurringRuleEntity(
            name = "闰年年费",
            amountMicros = 10_000L,
            currencyCode = "CNY",
            accountId = accId,
            cycleType = "YEARLY",
            dayOfCycle = 29,
            nextRunEpochMillis = millisOf(2024, 2, 29, 8, 0)
        )
        val nextYear2025 = RecurringScheduleCalculator.nextRunEpochMillis(leapYearlyRule, zoneId = zoneId)
        assertEquals(millisOf(2025, 2, 28, 8, 0), nextYear2025)
        // Disabled rule must not execute
        recurringRuleDao.insert(
            RecurringRuleEntity(
                name = "停用规则",
                amountMicros = 100_000L,
                currencyCode = "CNY",
                accountId = accId,
                cycleType = "MONTHLY",
                dayOfCycle = 1,
                nextRunEpochMillis = millisOf(2025, 1, 1, 8, 0),
                enabled = false
            )
        )
        assertEquals(0, ledger.processDueRecurringRules(millisOf(2025, 4, 15, 0, 0)))
    }
}
