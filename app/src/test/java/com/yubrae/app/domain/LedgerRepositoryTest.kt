package com.yubrae.app.domain

import com.yubrae.app.core.contract.RecordTransactionRequest
import com.yubrae.app.core.model.MoneyAmount
import com.yubrae.app.data.db.AccountEntity
import com.yubrae.app.data.db.AccountGroup
import com.yubrae.app.data.db.BudgetEntity
import com.yubrae.app.data.db.CaptureChannel
import com.yubrae.app.data.db.CategoryEntity
import com.yubrae.app.data.db.DebtDirection
import com.yubrae.app.data.db.DebtRecordEntity
import com.yubrae.app.data.db.DebtStatus
import com.yubrae.app.data.db.ExchangeRateEntity
import com.yubrae.app.data.db.InstallmentPlanEntity
import com.yubrae.app.data.db.LedgerEntity
import com.yubrae.app.data.db.RecurringRuleEntity
import com.yubrae.app.data.db.ReimburseStatus
import com.yubrae.app.data.db.StatsGranularity
import com.yubrae.app.data.db.TagEntity
import com.yubrae.app.data.db.TransactionEntity
import com.yubrae.app.data.db.TransactionStatus
import com.yubrae.app.data.db.TransactionType
import com.yubrae.app.data.network.ExchangeRateApi
import com.yubrae.app.data.prefs.AppSettings
import com.yubrae.app.data.repository.ExchangeRateRepositoryImpl
import com.yubrae.app.data.repository.LedgerRepositoryImpl
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
    private var activeLedgerIdSetting: Long = 0L

    private lateinit var accountDao: FakeAccountDao
    private lateinit var transactionDao: FakeTransactionDao
    private lateinit var categoryDao: FakeCategoryDao
    private lateinit var budgetDao: FakeBudgetDao
    private lateinit var recurringRuleDao: FakeRecurringRuleDao
    private lateinit var exchangeRateDao: FakeExchangeRateDao
    private lateinit var ledgerDao: FakeLedgerDao
    private lateinit var debtRecordDao: FakeDebtRecordDao
    private lateinit var installmentPlanDao: FakeInstallmentPlanDao
    private lateinit var tagDao: FakeTagDao
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
        ledgerDao = FakeLedgerDao()
        debtRecordDao = FakeDebtRecordDao()
        installmentPlanDao = FakeInstallmentPlanDao()
        tagDao = FakeTagDao()
        activeLedgerIdSetting = 0L

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
            ledgerDao = ledgerDao,
            debtRecordDao = debtRecordDao,
            installmentPlanDao = installmentPlanDao,
            tagDao = tagDao,
            getSettings = {
                AppSettings(
                    baseCurrency = baseCurrencySetting,
                    activeLedgerId = activeLedgerIdSetting
                )
            },
            updateSettings = { transform ->
                val updated = transform(
                    AppSettings(
                        baseCurrency = baseCurrencySetting,
                        activeLedgerId = activeLedgerIdSetting
                    )
                )
                baseCurrencySetting = updated.baseCurrency
                activeLedgerIdSetting = updated.activeLedgerId
            },
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

        val upTx = ledger.calibrateAccountBalance(accId, MoneyAmount.parseToMicros("125.50")!!).getOrThrow()!!
        assertEquals(TransactionType.ADJUST, upTx.type)
        assertEquals(MoneyAmount.parseToMicros("75.50")!!, upTx.origAmountMicros)
        assertEquals(MoneyAmount.parseToMicros("125.50")!!, accountDao.getById(accId)!!.balanceMicros)

        val downTx = ledger.calibrateAccountBalance(accId, MoneyAmount.parseToMicros("100.00")!!).getOrThrow()!!
        assertEquals(MoneyAmount.parseToMicros("-25.50")!!, downTx.origAmountMicros)
        assertEquals(MoneyAmount.parseToMicros("100.00")!!, accountDao.getById(accId)!!.balanceMicros)

        // Delete downward adjust restores 125.50
        ledger.deleteTransaction(downTx.id)
        assertEquals(MoneyAmount.parseToMicros("125.50")!!, accountDao.getById(accId)!!.balanceMicros)
    }

    @Test
    fun calibrateAccountBalance_withoutRecordUpdatesBalanceOnly() = runTest {
        val accId = accountDao.insert(
            AccountEntity(name = "现金", groupType = AccountGroup.WALLET, currencyCode = "CNY", balanceMicros = 500_000L) // 50.00
        )
        val initialTxCount = transactionDao.getAll().size

        val result = ledger.calibrateAccountBalance(
            accountId = accId,
            targetBalanceMicros = MoneyAmount.parseToMicros("288.88")!!,
            note = "直接修改",
            createRecord = false
        ).getOrThrow()

        assertEquals(null, result)
        assertEquals(MoneyAmount.parseToMicros("288.88")!!, accountDao.getById(accId)!!.balanceMicros)
        assertEquals(initialTxCount, transactionDao.getAll().size)
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

    @Test
    fun multiLedger_and_excludeFlags_isolateStats() = runTest {
        val accId = accountDao.insert(
            AccountEntity(name = "支付宝", groupType = AccountGroup.WALLET, currencyCode = "CNY", balanceMicros = MoneyAmount.parseToMicros("2000.00")!!)
        )
        val foodCatId = categoryDao.insert(CategoryEntity(name = "餐饮", type = TransactionType.EXPENSE))
        ledgerDao.insert(LedgerEntity(id = 1L, name = "日常账本"))
        val travelLedgerId = ledger.saveLedger(LedgerEntity(name = "日本旅行", currencyCode = "CNY"))

        val ts1 = millisOf(2026, 11, 10, 12, 0)
        val ts2 = millisOf(2026, 11, 11, 12, 0)
        // 日常账本正常餐饮 50 元
        ledger.saveTransaction(
            RecordTransactionRequest(
                type = TransactionType.EXPENSE,
                timestamp = ts1,
                origAmountMicros = MoneyAmount.parseToMicros("50.00")!!,
                origCurrency = "CNY",
                accountId = accId,
                categoryId = foodCatId,
                ledgerId = 1L
            )
        )
        // 日常账本不计入统计 200 元
        ledger.saveTransaction(
            RecordTransactionRequest(
                type = TransactionType.EXPENSE,
                timestamp = ts1,
                origAmountMicros = MoneyAmount.parseToMicros("200.00")!!,
                origCurrency = "CNY",
                accountId = accId,
                categoryId = foodCatId,
                ledgerId = 1L,
                excludeFromStats = true
            )
        )
        // 旅行账本餐饮 120 元
        ledger.saveTransaction(
            RecordTransactionRequest(
                type = TransactionType.EXPENSE,
                timestamp = ts2,
                origAmountMicros = MoneyAmount.parseToMicros("120.00")!!,
                origCurrency = "CNY",
                accountId = accId,
                categoryId = foodCatId,
                ledgerId = travelLedgerId
            )
        )

        val monthStart = millisOf(2026, 11, 1, 0, 0)
        val monthEnd = millisOf(2026, 11, 30, 23, 59)

        val ledger1Stats = ledger.computeCategoryStatsForLedger(monthStart, monthEnd, TransactionType.EXPENSE, 1L, "CNY")
        assertEquals(MoneyAmount.parseToMicros("50.00")!!, ledger1Stats.sumOf { it.totalBaseMicros })

        val travelStats = ledger.computeCategoryStatsForLedger(monthStart, monthEnd, TransactionType.EXPENSE, travelLedgerId, "CNY")
        assertEquals(MoneyAmount.parseToMicros("120.00")!!, travelStats.sumOf { it.totalBaseMicros })

        val allStats = ledger.computeCategoryStatsForLedger(monthStart, monthEnd, TransactionType.EXPENSE, 0L, "CNY")
        assertEquals(MoneyAmount.parseToMicros("170.00")!!, allStats.sumOf { it.totalBaseMicros })

        val trend = ledger.computeTrendBuckets(
            granularity = StatsGranularity.MONTHLY,
            anchorDateMillis = ts1,
            customStartMillis = null,
            customEndMillis = null,
            ledgerId = travelLedgerId,
            displayCurrency = "CNY"
        )
        assertEquals(MoneyAmount.parseToMicros("120.00")!!, trend.sumOf { it.expenseBaseMicros })
    }

    @Test
    fun refundTransaction_deductsCategoryExpenseWithoutInflatingIncome() = runTest {
        val accId = accountDao.insert(
            AccountEntity(name = "微信", groupType = AccountGroup.WALLET, currencyCode = "CNY", balanceMicros = MoneyAmount.parseToMicros("1000.00")!!)
        )
        val shopCatId = categoryDao.insert(CategoryEntity(name = "购物", type = TransactionType.EXPENSE))
        val ts = millisOf(2026, 11, 15, 14, 0)

        val origTx = ledger.saveTransaction(
            RecordTransactionRequest(
                type = TransactionType.EXPENSE,
                timestamp = ts,
                origAmountMicros = MoneyAmount.parseToMicros("100.00")!!,
                origCurrency = "CNY",
                accountId = accId,
                categoryId = shopCatId,
                merchant = "淘宝"
            )
        ).getOrThrow()

        // 退款 30 元关联原支出
        ledger.saveTransaction(
            RecordTransactionRequest(
                type = TransactionType.INCOME,
                timestamp = ts + 3_600_000L,
                origAmountMicros = MoneyAmount.parseToMicros("30.00")!!,
                origCurrency = "CNY",
                accountId = accId,
                categoryId = shopCatId,
                merchant = "淘宝退款",
                refundOfTxId = origTx.id
            )
        ).getOrThrow()

        // 账户余额净支出 70 元
        assertEquals(MoneyAmount.parseToMicros("930.00")!!, accountDao.getById(accId)!!.balanceMicros)

        val monthStart = millisOf(2026, 11, 1, 0, 0)
        val monthEnd = millisOf(2026, 11, 30, 23, 59)
        val expenseStats = ledger.computeCategoryStatsForLedger(monthStart, monthEnd, TransactionType.EXPENSE, 1L, "CNY")
        val incomeStats = ledger.computeCategoryStatsForLedger(monthStart, monthEnd, TransactionType.INCOME, 1L, "CNY")

        // 退款冲减支出为 70，且不计入收入统计
        assertEquals(MoneyAmount.parseToMicros("70.00")!!, expenseStats.sumOf { it.totalBaseMicros })
        assertTrue(incomeStats.isEmpty())
    }

    @Test
    fun settleReimbursements_convertsCrossCurrency_marksReimbursed_andExcludesFromStats() = runTest {
        val usdCardId = accountDao.insert(
            AccountEntity(name = "美元卡", groupType = AccountGroup.FOREIGN, currencyCode = "USD", balanceMicros = MoneyAmount.parseToMicros("500.00")!!)
        )
        val cnyBankId = accountDao.insert(
            AccountEntity(name = "工资卡", groupType = AccountGroup.DEBIT, currencyCode = "CNY", balanceMicros = MoneyAmount.parseToMicros("1000.00")!!)
        )
        exchangeRateDao.upsertAll(
            listOf(
                ExchangeRateEntity(baseCurrency = "USD", targetCurrency = "CNY", rateMicros = 7_200_000L, date = "2026-11-22", source = "CUSTOM")
            )
        )
        val travelCatId = categoryDao.insert(CategoryEntity(name = "差旅", type = TransactionType.EXPENSE))

        val tx1 = ledger.saveTransaction(
            RecordTransactionRequest(
                type = TransactionType.EXPENSE,
                timestamp = millisOf(2026, 11, 20, 10, 0),
                origAmountMicros = MoneyAmount.parseToMicros("10.00")!!,
                origCurrency = "USD",
                accountId = usdCardId,
                categoryId = travelCatId,
                reimburseStatus = ReimburseStatus.PENDING
            )
        ).getOrThrow()
        val tx2 = ledger.saveTransaction(
            RecordTransactionRequest(
                type = TransactionType.EXPENSE,
                timestamp = millisOf(2026, 11, 20, 12, 0),
                origAmountMicros = MoneyAmount.parseToMicros("28.00")!!,
                origCurrency = "CNY",
                accountId = cnyBankId,
                categoryId = travelCatId,
                reimburseStatus = ReimburseStatus.PENDING
            )
        ).getOrThrow()

        // 10 USD * 7.2 + 28 CNY = 100 CNY
        val settleTx = ledger.settleReimbursements(
            txIds = listOf(tx1.id, tx2.id),
            targetAccountId = cnyBankId,
            timestamp = millisOf(2026, 11, 22, 15, 0),
            note = "十一月出差核销"
        ).getOrThrow()

        assertEquals(MoneyAmount.parseToMicros("100.00")!!, settleTx.origAmountMicros)
        assertTrue(settleTx.excludeFromStats)
        assertEquals(MoneyAmount.parseToMicros("1072.00")!!, accountDao.getById(cnyBankId)!!.balanceMicros)

        val updatedTx1 = transactionDao.getById(tx1.id)!!
        val updatedTx2 = transactionDao.getById(tx2.id)!!
        assertEquals(ReimburseStatus.REIMBURSED, updatedTx1.reimburseStatus)
        assertEquals(settleTx.id, updatedTx1.reimbursedTxId)
        assertEquals(ReimburseStatus.REIMBURSED, updatedTx2.reimburseStatus)
    }

    @Test
    fun debtLifecycle_handlesPartialAndFullSettlementWithAccountSync() = runTest {
        val bankId = accountDao.insert(
            AccountEntity(name = "招商储蓄卡", groupType = AccountGroup.DEBIT, currencyCode = "CNY", balanceMicros = MoneyAmount.parseToMicros("1000.00")!!)
        )

        val debtId = ledger.saveDebt(
            DebtRecordEntity(
                direction = DebtDirection.LEND,
                personName = "张三",
                amountMicros = MoneyAmount.parseToMicros("300.00")!!,
                currencyCode = "CNY",
                accountId = bankId,
                note = "垫付聚餐"
            ),
            syncAccountBalance = true
        )
        assertEquals(MoneyAmount.parseToMicros("700.00")!!, accountDao.getById(bankId)!!.balanceMicros)

        // 部分归还 100 元
        val afterPartial = ledger.settleDebt(
            debtId = debtId,
            settleAmountMicros = MoneyAmount.parseToMicros("100.00")!!,
            accountId = bankId,
            note = "先还一百"
        ).getOrThrow()
        assertEquals(DebtStatus.OPEN, afterPartial.status)
        assertEquals(MoneyAmount.parseToMicros("100.00")!!, afterPartial.settledAmountMicros)
        assertEquals(MoneyAmount.parseToMicros("800.00")!!, accountDao.getById(bankId)!!.balanceMicros)

        // 归还剩余（传入超额金额应截断为剩余 200 元）
        val afterFull = ledger.settleDebt(
            debtId = debtId,
            settleAmountMicros = MoneyAmount.parseToMicros("500.00")!!,
            accountId = bankId,
            note = "全部结清"
        ).getOrThrow()
        assertEquals(DebtStatus.SETTLED, afterFull.status)
        assertEquals(MoneyAmount.parseToMicros("300.00")!!, afterFull.settledAmountMicros)
        assertEquals(MoneyAmount.parseToMicros("1000.00")!!, accountDao.getById(bankId)!!.balanceMicros)
    }

    @Test
    fun installmentPlan_recordsPeriodsAndDisablesOnCompletion() = runTest {
        val creditId = accountDao.insert(
            AccountEntity(name = "花呗分期", groupType = AccountGroup.CREDIT, currencyCode = "CNY", balanceMicros = 0L)
        )
        val planId = ledger.saveInstallment(
            InstallmentPlanEntity(
                accountId = creditId,
                name = "iPhone 17 分期",
                totalAmountMicros = MoneyAmount.parseToMicros("2000.00")!!,
                feePerPeriodMicros = MoneyAmount.parseToMicros("10.00")!!,
                currencyCode = "CNY",
                totalPeriods = 2,
                completedPeriods = 0,
                billingDay = 15,
                nextDueMillis = millisOf(2026, 11, 15, 9, 0)
            )
        )

        // 第 1 期：本金 1000 + 手续费 10 = 1010
        val tx1 = ledger.recordInstallmentPeriod(planId).getOrThrow()
        assertEquals(MoneyAmount.parseToMicros("1010.00")!!, tx1.origAmountMicros)
        val planAfter1 = installmentPlanDao.getById(planId)!!
        assertEquals(1, planAfter1.completedPeriods)
        assertTrue(planAfter1.enabled)

        // 第 2 期：最后一期 -> completedPeriods = 2, enabled = false
        ledger.recordInstallmentPeriod(planId).getOrThrow()
        val planAfter2 = installmentPlanDao.getById(planId)!!
        assertEquals(2, planAfter2.completedPeriods)
        assertFalse(planAfter2.enabled)
        assertEquals(MoneyAmount.parseToMicros("-2020.00")!!, accountDao.getById(creditId)!!.balanceMicros)
    }

    @Test
    fun netWorthHistory_and_budgetRollover_computeExpectedValues() = runTest {
        nowMillis = millisOf(2026, 11, 22, 10, 0)
        val bankId = accountDao.insert(
            AccountEntity(name = "现金账户", groupType = AccountGroup.DEBIT, currencyCode = "CNY", balanceMicros = MoneyAmount.parseToMicros("1000.00")!!)
        )
        val foodCatId = categoryDao.insert(CategoryEntity(name = "餐饮", type = TransactionType.EXPENSE))

        // 10 月支出 200 元；11 月收入 500 元、支出 100 元 -> 当前余额已在上面设为 1000
        // 往回反推：10 月末余额 = 1000 - (500 - 100) = 600
        ledger.saveTransaction(
            RecordTransactionRequest(
                type = TransactionType.EXPENSE,
                timestamp = millisOf(2026, 10, 10, 12, 0),
                origAmountMicros = MoneyAmount.parseToMicros("200.00")!!,
                origCurrency = "CNY",
                accountId = bankId,
                categoryId = foodCatId
            )
        )
        ledger.saveTransaction(
            RecordTransactionRequest(
                type = TransactionType.INCOME,
                timestamp = millisOf(2026, 11, 5, 12, 0),
                origAmountMicros = MoneyAmount.parseToMicros("500.00")!!,
                origCurrency = "CNY",
                accountId = bankId
            )
        )
        ledger.saveTransaction(
            RecordTransactionRequest(
                type = TransactionType.EXPENSE,
                timestamp = millisOf(2026, 11, 18, 12, 0),
                origAmountMicros = MoneyAmount.parseToMicros("100.00")!!,
                origCurrency = "CNY",
                accountId = bankId,
                categoryId = foodCatId
            )
        )

        val history = ledger.computeNetWorthHistory(monthsBack = 3, displayCurrency = "CNY")
        assertEquals(3, history.size)
        val octPoint = history.firstOrNull { it.label.contains("10") }
        val novPoint = history.last()
        assertNotNull(octPoint)
        assertEquals(MoneyAmount.parseToMicros("1200.00")!!, novPoint.netWorthMicros)
        assertEquals(MoneyAmount.parseToMicros("800.00")!!, octPoint!!.netWorthMicros)

        // 预算结转：月预算 500 元、启用 rollover，10 月花了 200 元 -> 结转 300 元 -> 11 月有效预算 = 800 元
        budgetDao.insert(
            BudgetEntity(
                name = "餐饮月预算",
                categoryId = foodCatId,
                limitAmountMicros = MoneyAmount.parseToMicros("500.00")!!,
                currencyCode = "CNY",
                ledgerId = 1L,
                rolloverEnabled = true
            )
        )
        val progress = ledger.computeBudgetProgressForLedger(2026, 11, 1L)
        assertEquals(1, progress.size)
        val item = progress.first()
        assertEquals(MoneyAmount.parseToMicros("300.00")!!, item.rolloverMicros)
        assertEquals(MoneyAmount.parseToMicros("800.00")!!, item.limitInBudgetCurrencyMicros)
        assertEquals(MoneyAmount.parseToMicros("100.00")!!, item.spentInBudgetCurrencyMicros)
        assertEquals(9, item.remainingDays)
        // 11月22日..30日共 9 天，可用余额 700 元，日均约 77.7777 元
        assertEquals(MoneyAmount.parseToMicros("700.00")!! / 9L, item.dailyAvailableMicros)
    }

    @Test
    fun deleteLedger_rejectsWhenOnlyOneActiveLedgerRemains() = runTest {
        ledgerDao.insert(LedgerEntity(id = 1L, name = "默认账本", sortOrder = 0))
        val onlyActiveResult = ledger.deleteLedger(1L)
        assertTrue(onlyActiveResult.isFailure)
        assertNotNull(ledgerDao.getById(1L))

        // 即使存在已归档账本，也不能删除唯一剩余的活跃账本；但可以删除已归档账本
        ledgerDao.insert(LedgerEntity(id = 2L, name = "归档账本", sortOrder = 10, archived = true))
        val deleteLastActiveResult = ledger.deleteLedger(1L)
        assertTrue(deleteLastActiveResult.isFailure)
        assertTrue(ledger.deleteLedger(1L, migrateToLedgerId = 2L).isFailure)
        assertTrue(ledger.deleteLedger(1L, migrateToLedgerId = 1L).isFailure)
        assertTrue(ledger.deleteLedger(999L).isFailure)

        val deleteArchivedResult = ledger.deleteLedger(2L)
        assertTrue(deleteArchivedResult.isSuccess)
        assertNull(ledgerDao.getById(2L))
        assertNotNull(ledgerDao.getById(1L))
    }

    @Test
    fun deleteLedger_allowsDeletingDefaultLedgerId1_withMigrationAndBudgetConflictHandling() = runTest {
        ledgerDao.insert(LedgerEntity(id = 1L, name = "默认账本", sortOrder = 0))
        ledgerDao.insert(LedgerEntity(id = 2L, name = "家庭账本", sortOrder = 10))
        activeLedgerIdSetting = 1L

        val accId = accountDao.insert(
            AccountEntity(
                name = "储蓄卡",
                groupType = AccountGroup.DEBIT,
                currencyCode = "CNY",
                balanceMicros = MoneyAmount.parseToMicros("1000.00")!!
            )
        )
        val foodCatId = categoryDao.insert(CategoryEntity(name = "餐饮", type = TransactionType.EXPENSE))
        val travelCatId = categoryDao.insert(CategoryEntity(name = "旅行", type = TransactionType.EXPENSE))

        val tx1 = ledger.saveTransaction(
            RecordTransactionRequest(
                type = TransactionType.EXPENSE,
                timestamp = millisOf(2026, 11, 20, 12, 0),
                origAmountMicros = MoneyAmount.parseToMicros("200.00")!!,
                origCurrency = "CNY",
                accountId = accId,
                categoryId = foodCatId,
                ledgerId = 1L
            )
        ).getOrThrow()
        val tx2 = ledger.saveTransaction(
            RecordTransactionRequest(
                type = TransactionType.EXPENSE,
                timestamp = millisOf(2026, 11, 21, 12, 0),
                origAmountMicros = MoneyAmount.parseToMicros("100.00")!!,
                origCurrency = "CNY",
                accountId = accId,
                categoryId = foodCatId,
                ledgerId = 2L
            )
        ).getOrThrow()

        // 源账本在 foodCatId 上存在同周期预算（与目标账本冲突，应保留目标），travelCatId 无冲突应迁移
        budgetDao.insert(
            BudgetEntity(
                name = "默认餐饮预算",
                categoryId = foodCatId,
                limitAmountMicros = MoneyAmount.parseToMicros("500.00")!!,
                currencyCode = "CNY",
                ledgerId = 1L
            )
        )
        budgetDao.insert(
            BudgetEntity(
                name = "默认旅行预算",
                categoryId = travelCatId,
                limitAmountMicros = MoneyAmount.parseToMicros("1200.00")!!,
                currencyCode = "CNY",
                ledgerId = 1L
            )
        )
        budgetDao.insert(
            BudgetEntity(
                name = "家庭餐饮预算",
                categoryId = foodCatId,
                limitAmountMicros = MoneyAmount.parseToMicros("800.00")!!,
                currencyCode = "CNY",
                ledgerId = 2L
            )
        )

        val result = ledger.deleteLedger(ledgerId = 1L, migrateToLedgerId = 2L)
        assertTrue(result.isSuccess)
        assertNull(ledgerDao.getById(1L))
        assertEquals(listOf(2L), ledgerDao.getAll().map { it.id })
        assertEquals(2L, activeLedgerIdSetting)

        assertEquals(2L, transactionDao.getById(tx1.id)!!.ledgerId)
        assertEquals(2L, transactionDao.getById(tx2.id)!!.ledgerId)
        assertEquals(MoneyAmount.parseToMicros("700.00")!!, accountDao.getById(accId)!!.balanceMicros)

        assertTrue(budgetDao.getByLedgerId(1L).isEmpty())
        val migratedBudgets = budgetDao.getByLedgerId(2L)
        assertEquals(2, migratedBudgets.size)
        assertEquals(
            MoneyAmount.parseToMicros("800.00")!!,
            migratedBudgets.first { it.categoryId == foodCatId }.limitAmountMicros
        )
        assertEquals(
            MoneyAmount.parseToMicros("1200.00")!!,
            migratedBudgets.first { it.categoryId == travelCatId }.limitAmountMicros
        )
    }

    @Test
    fun deleteLedger_allowsDeletingDefaultLedgerId1_cascadeDeletesAndRollsBackBalancesAndRefundRefs() = runTest {
        ledgerDao.insert(LedgerEntity(id = 1L, name = "默认账本", sortOrder = 0))
        ledgerDao.insert(LedgerEntity(id = 2L, name = "旅行账本", sortOrder = 20))
        ledgerDao.insert(LedgerEntity(id = 3L, name = "日常账本", sortOrder = 10))
        activeLedgerIdSetting = 1L

        val cnyAccId = accountDao.insert(
            AccountEntity(
                name = "微信",
                groupType = AccountGroup.WALLET,
                currencyCode = "CNY",
                balanceMicros = MoneyAmount.parseToMicros("1000.00")!!
            )
        )
        val usdAccId = accountDao.insert(
            AccountEntity(
                name = "美元卡",
                groupType = AccountGroup.FOREIGN,
                currencyCode = "USD",
                balanceMicros = MoneyAmount.parseToMicros("500.00")!!
            )
        )
        exchangeRateDao.upsertAll(
            listOf(
                ExchangeRateEntity(
                    baseCurrency = "USD",
                    targetCurrency = "CNY",
                    rateMicros = 7_000_000L,
                    date = "2026-11-22",
                    source = "MANUAL"
                )
            )
        )
        val foodCatId = categoryDao.insert(CategoryEntity(name = "餐饮", type = TransactionType.EXPENSE))

        val exp1 = ledger.saveTransaction(
            RecordTransactionRequest(
                type = TransactionType.EXPENSE,
                timestamp = millisOf(2026, 11, 20, 10, 0),
                origAmountMicros = MoneyAmount.parseToMicros("120.00")!!,
                origCurrency = "CNY",
                accountId = cnyAccId,
                categoryId = foodCatId,
                ledgerId = 1L
            )
        ).getOrThrow()
        ledger.saveTransaction(
            RecordTransactionRequest(
                type = TransactionType.INCOME,
                timestamp = millisOf(2026, 11, 20, 11, 0),
                origAmountMicros = MoneyAmount.parseToMicros("300.00")!!,
                origCurrency = "CNY",
                accountId = cnyAccId,
                ledgerId = 1L
            )
        ).getOrThrow()
        ledger.saveTransaction(
            RecordTransactionRequest(
                type = TransactionType.TRANSFER,
                timestamp = millisOf(2026, 11, 20, 12, 0),
                origAmountMicros = MoneyAmount.parseToMicros("140.00")!!,
                origCurrency = "CNY",
                accountId = cnyAccId,
                toAccountId = usdAccId,
                manualToAmountMicros = MoneyAmount.parseToMicros("20.00")!!,
                ledgerId = 1L
            )
        ).getOrThrow()
        // 在剩余账本 3L 中记录一笔引用默认账本 exp1 的退款
        val crossLedgerRefund = ledger.saveTransaction(
            RecordTransactionRequest(
                type = TransactionType.INCOME,
                timestamp = millisOf(2026, 11, 21, 9, 0),
                origAmountMicros = MoneyAmount.parseToMicros("50.00")!!,
                origCurrency = "CNY",
                accountId = cnyAccId,
                refundOfTxId = exp1.id,
                ledgerId = 3L
            )
        ).getOrThrow()
        budgetDao.insert(
            BudgetEntity(
                name = "默认月预算",
                categoryId = foodCatId,
                limitAmountMicros = MoneyAmount.parseToMicros("500.00")!!,
                currencyCode = "CNY",
                ledgerId = 1L
            )
        )

        assertEquals(MoneyAmount.parseToMicros("1090.00")!!, accountDao.getById(cnyAccId)!!.balanceMicros)
        assertEquals(MoneyAmount.parseToMicros("520.00")!!, accountDao.getById(usdAccId)!!.balanceMicros)

        val result = ledger.deleteLedger(ledgerId = 1L, migrateToLedgerId = null)
        assertTrue(result.isSuccess)
        assertNull(ledgerDao.getById(1L))
        assertEquals(listOf(3L, 2L), ledgerDao.getAll().map { it.id })
        // 应切换至按 sortOrder 排序后的第一个剩余活跃账本 (3L, sortOrder = 10)
        assertEquals(3L, activeLedgerIdSetting)

        assertTrue(transactionDao.getByLedgerId(1L).isEmpty())
        assertTrue(budgetDao.getByLedgerId(1L).isEmpty())
        val survivingRefund = transactionDao.getById(crossLedgerRefund.id)!!
        assertEquals(3L, survivingRefund.ledgerId)
        assertNull(survivingRefund.refundOfTxId)

        // 1L 三笔流水已全部精确回滚，仅保留 3L 的 +50.00
        assertEquals(MoneyAmount.parseToMicros("1050.00")!!, accountDao.getById(cnyAccId)!!.balanceMicros)
        assertEquals(MoneyAmount.parseToMicros("500.00")!!, accountDao.getById(usdAccId)!!.balanceMicros)
    }

    @Test
    fun archiveLedger_allowsArchivingDefaultLedgerId1_whenOtherActiveExists_andRejectsLastActive() = runTest {
        ledgerDao.insert(LedgerEntity(id = 1L, name = "默认账本", sortOrder = 0))
        ledgerDao.insert(LedgerEntity(id = 2L, name = "日常账本", sortOrder = 10))
        activeLedgerIdSetting = 1L

        ledger.archiveLedger(1L, true)
        assertTrue(ledgerDao.getById(1L)!!.archived)
        assertEquals(2L, activeLedgerIdSetting)

        // 尝试归档最后一个活跃账本 2L，应被拦截
        ledger.archiveLedger(2L, true)
        assertFalse(ledgerDao.getById(2L)!!.archived)

        ledger.archiveLedger(1L, false)
        assertFalse(ledgerDao.getById(1L)!!.archived)
    }

    @Test
    fun saveTransaction_resolvesDynamicActiveLedgerWhenUnspecifiedOrStale() = runTest {
        ledgerDao.insert(LedgerEntity(id = 2L, name = "新主账本", sortOrder = 0))
        activeLedgerIdSetting = 1L // 指向已删除的 1L

        val accId = accountDao.insert(
            AccountEntity(name = "现金", groupType = AccountGroup.WALLET, currencyCode = "CNY", balanceMicros = 100_000_000L)
        )
        val saved = ledger.saveTransaction(
            RecordTransactionRequest(
                type = TransactionType.EXPENSE,
                timestamp = millisOf(2026, 11, 22, 10, 0),
                origAmountMicros = MoneyAmount.parseToMicros("10.00")!!,
                origCurrency = "CNY",
                accountId = accId,
                ledgerId = 0L
            )
        ).getOrThrow()

        assertEquals(2L, saved.ledgerId)
    }

    @Test
    fun saveTag_persistsAndTrimsIcon() = runTest {
        val insertedId = ledger.saveTag(
            TagEntity(
                name = "露营",
                groupName = " ",
                colorHex = 0xFF2AA876L,
                sortOrder = 5,
                isSystem = false,
                icon = "  forest  "
            )
        )
        assertTrue(insertedId > 0L)
        val created = tagDao.getById(insertedId)!!
        assertEquals("常用", created.groupName)
        assertEquals("forest", created.icon)

        ledger.saveTag(created.copy(icon = "  luggage "))
        val updated = tagDao.getById(insertedId)!!
        assertEquals("luggage", updated.icon)
    }

    @Test
    fun deleteAccount_cascadesAndRollsBackTransfers_andSupportsMigration() = runTest {
        val accountAliasDao = com.yubrae.app.capture.FakeAccountAliasDao()
        var hiddenIds = setOf<Long>()
        var initialSeededFlag = false
        val repoWithAlias = LedgerRepositoryImpl(
            accountDao = accountDao,
            transactionDao = transactionDao,
            categoryDao = FakeCategoryDao(),
            budgetDao = FakeBudgetDao(),
            recurringRuleDao = FakeRecurringRuleDao(),
            exchangeRateProvider = ExchangeRateRepositoryImpl(
                exchangeRateDao = FakeExchangeRateDao(),
                exchangeRateApi = ExchangeRateApi(OkHttpClient()),
                currentDateProvider = { LocalDate.of(2026, 11, 22) }
            ),
            ledgerDao = ledgerDao,
            getSettings = {
                com.yubrae.app.data.prefs.AppSettings(
                    hiddenAssetAccountIds = hiddenIds,
                    initialAccountsSeeded = initialSeededFlag
                )
            },
            updateSettings = { transform ->
                val updated = transform(
                    com.yubrae.app.data.prefs.AppSettings(
                        hiddenAssetAccountIds = hiddenIds,
                        initialAccountsSeeded = initialSeededFlag
                    )
                )
                hiddenIds = updated.hiddenAssetAccountIds
                initialSeededFlag = updated.initialAccountsSeeded
            },
            accountAliasDao = accountAliasDao
        )

        val accA = accountDao.insert(
            AccountEntity(
                name = "微信零钱",
                groupType = AccountGroup.WALLET,
                currencyCode = "CNY",
                balanceMicros = MoneyAmount.parseToMicros("100.00")!!
            )
        )
        val accB = accountDao.insert(
            AccountEntity(
                name = "银行储蓄卡",
                groupType = AccountGroup.DEBIT,
                currencyCode = "CNY",
                balanceMicros = MoneyAmount.parseToMicros("50.00")!!
            )
        )
        val accC = accountDao.insert(
            AccountEntity(
                name = "现金",
                groupType = AccountGroup.WALLET,
                currencyCode = "CNY",
                balanceMicros = MoneyAmount.parseToMicros("10.00")!!
            )
        )
        hiddenIds = setOf(accA)
        accountAliasDao.insert(
            com.yubrae.app.data.db.AccountAliasEntity(accountId = accA, matchType = "KEYWORD", pattern = "微信")
        )

        // 转账 20 元 A -> B (A=80, B=70)，再用 5 元 B -> A (A=85, B=65)
        repoWithAlias.saveTransaction(
            RecordTransactionRequest(
                type = TransactionType.TRANSFER,
                timestamp = millisOf(2026, 11, 22, 9, 0),
                origAmountMicros = MoneyAmount.parseToMicros("20.00")!!,
                origCurrency = "CNY",
                accountId = accA,
                toAccountId = accB
            )
        ).getOrThrow()
        repoWithAlias.saveTransaction(
            RecordTransactionRequest(
                type = TransactionType.TRANSFER,
                timestamp = millisOf(2026, 11, 22, 10, 0),
                origAmountMicros = MoneyAmount.parseToMicros("5.00")!!,
                origCurrency = "CNY",
                accountId = accB,
                toAccountId = accA
            )
        ).getOrThrow()

        // 级联删除 A：删除关联账单，准确回滚对账户 B 的转账余额影响（B 恢复 50.00 元），并清理别名与隐藏列表
        val delResA = repoWithAlias.deleteAccount(accA, migrateToAccountId = null)
        assertTrue(delResA.isSuccess)
        assertEquals(null, accountDao.getById(accA))
        assertEquals(MoneyAmount.parseToMicros("50.00")!!, accountDao.getById(accB)!!.balanceMicros)
        assertTrue(transactionDao.getAll().isEmpty())
        assertTrue(accountAliasDao.getAll().none { it.accountId == accA })
        assertFalse(accA in hiddenIds)
        assertTrue(initialSeededFlag)

        // 迁移删除 C -> B：先记录一笔 3 元支出（C=7.00），再迁移删除 C 到 B（B=57.00）
        repoWithAlias.saveTransaction(
            RecordTransactionRequest(
                type = TransactionType.EXPENSE,
                timestamp = millisOf(2026, 11, 22, 11, 0),
                origAmountMicros = MoneyAmount.parseToMicros("3.00")!!,
                origCurrency = "CNY",
                accountId = accC
            )
        ).getOrThrow()
        val migrateRes = repoWithAlias.deleteAccount(accC, migrateToAccountId = accB)
        assertTrue(migrateRes.isSuccess)
        assertEquals(null, accountDao.getById(accC))
        assertEquals(MoneyAmount.parseToMicros("57.00")!!, accountDao.getById(accB)!!.balanceMicros)
        assertEquals(accB, transactionDao.getAll().single().accountId)

        // 允许删除最后一个活跃账户
        val delLast = repoWithAlias.deleteAccount(accB, migrateToAccountId = null)
        assertTrue(delLast.isSuccess)
        assertTrue(accountDao.getAll().isEmpty())
    }

    @Test
    fun reorderAccounts_persistsSortOrderAtomicallyInMultiplesOfTen() = runTest {
        val accA = accountDao.insert(
            AccountEntity(name = "微信零钱", groupType = AccountGroup.WALLET, currencyCode = "CNY", sortOrder = 0)
        )
        val accB = accountDao.insert(
            AccountEntity(name = "招行储蓄卡", groupType = AccountGroup.DEBIT, currencyCode = "CNY", sortOrder = 0)
        )
        val accC = accountDao.insert(
            AccountEntity(name = "招行信用卡", groupType = AccountGroup.CREDIT, currencyCode = "CNY", sortOrder = 0)
        )

        val emptyRes = ledger.reorderAccounts(emptyList())
        assertTrue(emptyRes.isSuccess)

        nowMillis = millisOf(2026, 11, 25, 16, 30)
        val res = ledger.reorderAccounts(listOf(accC, accA, accB, accA))
        assertTrue(res.isSuccess)

        val ordered = accountDao.getAll()
        assertEquals(listOf(accC, accA, accB), ordered.map { it.id })
        assertEquals(listOf(0, 10, 20), ordered.map { it.sortOrder })
        assertTrue(ordered.all { it.updatedAt == nowMillis })
    }

    @Test
    fun batchMoveTransactionsAccount_handlesSameCurrencyCrossCurrencyAndTransferSelfGuard() = runTest {
        val cnyA = accountDao.insert(
            AccountEntity(
                name = "招行储蓄卡",
                groupType = AccountGroup.DEBIT,
                currencyCode = "CNY",
                balanceMicros = MoneyAmount.parseToMicros("1000.00")!!
            )
        )
        val cnyB = accountDao.insert(
            AccountEntity(
                name = "微信零钱",
                groupType = AccountGroup.WALLET,
                currencyCode = "CNY",
                balanceMicros = MoneyAmount.parseToMicros("500.00")!!
            )
        )
        val usdC = accountDao.insert(
            AccountEntity(
                name = "美元账户",
                groupType = AccountGroup.FOREIGN,
                currencyCode = "USD",
                balanceMicros = MoneyAmount.parseToMicros("200.00")!!
            )
        )
        val archivedAcc = accountDao.insert(
            AccountEntity(
                name = "归档旧卡",
                groupType = AccountGroup.DEBIT,
                currencyCode = "CNY",
                archived = true
            )
        )
        exchangeRateDao.upsert(
            ExchangeRateEntity(
                baseCurrency = "USD",
                targetCurrency = "CNY",
                rateMicros = 8_000_000L, // 1 USD = 8.00 CNY => 1 CNY = 0.125 USD exact
                date = "2026-11-22",
                source = "CUSTOM"
            )
        )

        val expCny = ledger.saveTransaction(
            RecordTransactionRequest(
                type = TransactionType.EXPENSE,
                timestamp = millisOf(2026, 11, 22, 10, 0),
                origAmountMicros = MoneyAmount.parseToMicros("100.00")!!,
                origCurrency = "CNY",
                accountId = cnyA
            )
        ).getOrThrow()
        val incCny = ledger.saveTransaction(
            RecordTransactionRequest(
                type = TransactionType.INCOME,
                timestamp = millisOf(2026, 11, 22, 11, 0),
                origAmountMicros = MoneyAmount.parseToMicros("50.00")!!,
                origCurrency = "CNY",
                accountId = cnyA
            )
        ).getOrThrow()
        val expAlreadyInB = ledger.saveTransaction(
            RecordTransactionRequest(
                type = TransactionType.EXPENSE,
                timestamp = millisOf(2026, 11, 22, 11, 30),
                origAmountMicros = MoneyAmount.parseToMicros("20.00")!!,
                origCurrency = "CNY",
                accountId = cnyB
            )
        ).getOrThrow()
        val transferAToB = ledger.saveTransaction(
            RecordTransactionRequest(
                type = TransactionType.TRANSFER,
                timestamp = millisOf(2026, 11, 22, 12, 0),
                origAmountMicros = MoneyAmount.parseToMicros("40.00")!!,
                origCurrency = "CNY",
                accountId = cnyA,
                toAccountId = cnyB
            )
        ).getOrThrow()

        // Archived / nonexistent targets must fail without changing balances
        assertTrue(ledger.batchMoveTransactionsAccount(listOf(expCny.id), archivedAcc).isFailure)
        assertTrue(ledger.batchMoveTransactionsAccount(listOf(expCny.id), 99999L).isFailure)
        assertEquals(MoneyAmount.parseToMicros("910.00")!!, accountDao.getById(cnyA)!!.balanceMicros)
        assertEquals(MoneyAmount.parseToMicros("520.00")!!, accountDao.getById(cnyB)!!.balanceMicros)

        // 1. Same-currency batch move to cnyB:
        //    expCny & incCny move; expAlreadyInB skipped; transferAToB skipped (toAccountId == cnyB anti-self-transfer)
        val sameCurMoved = ledger.batchMoveTransactionsAccount(
            txIds = listOf(expCny.id, incCny.id, expAlreadyInB.id, transferAToB.id),
            targetAccountId = cnyB
        ).getOrThrow()
        assertEquals(2, sameCurMoved)
        assertEquals(MoneyAmount.parseToMicros("960.00")!!, accountDao.getById(cnyA)!!.balanceMicros)
        assertEquals(MoneyAmount.parseToMicros("470.00")!!, accountDao.getById(cnyB)!!.balanceMicros)
        assertEquals(cnyB, transactionDao.getById(expCny.id)!!.accountId)
        assertEquals(cnyB, transactionDao.getById(incCny.id)!!.accountId)
        assertEquals(cnyA, transactionDao.getById(transferAToB.id)!!.accountId)
        assertEquals(cnyB, transactionDao.getById(transferAToB.id)!!.toAccountId)

        // 2. Cross-currency move to usdC (1 CNY = 0.125 USD):
        //    expCny (100 CNY -> 12.50 USD) and transferAToB source (40 CNY -> 5.00 USD, toAccountId stays cnyB)
        val crossCurMoved = ledger.batchMoveTransactionsAccount(
            txIds = listOf(expCny.id, transferAToB.id),
            targetAccountId = usdC
        ).getOrThrow()
        assertEquals(2, crossCurMoved)
        assertEquals(MoneyAmount.parseToMicros("1000.00")!!, accountDao.getById(cnyA)!!.balanceMicros)
        assertEquals(MoneyAmount.parseToMicros("570.00")!!, accountDao.getById(cnyB)!!.balanceMicros)
        assertEquals(MoneyAmount.parseToMicros("182.50")!!, accountDao.getById(usdC)!!.balanceMicros)

        val movedExp = transactionDao.getById(expCny.id)!!
        assertEquals(usdC, movedExp.accountId)
        assertEquals("USD", movedExp.accountCurrency)
        assertEquals(MoneyAmount.parseToMicros("12.50")!!, movedExp.accountAmountMicros)
        assertEquals(125_000L, movedExp.rateToAccountMicros)

        val movedTransfer = transactionDao.getById(transferAToB.id)!!
        assertEquals(usdC, movedTransfer.accountId)
        assertEquals(cnyB, movedTransfer.toAccountId)
        assertEquals("USD", movedTransfer.accountCurrency)
        assertEquals(MoneyAmount.parseToMicros("5.00")!!, movedTransfer.accountAmountMicros)
        assertEquals(MoneyAmount.parseToMicros("40.00")!!, movedTransfer.toAmountMicros)

        // 3. Move a USD-originated expense paid from CNY card to USD card -> restores exact origAmountMicros & 1:1 rate
        val usdOrigOnCny = ledger.saveTransaction(
            RecordTransactionRequest(
                type = TransactionType.EXPENSE,
                timestamp = millisOf(2026, 11, 22, 13, 0),
                origAmountMicros = MoneyAmount.parseToMicros("10.00")!!,
                origCurrency = "USD",
                accountId = cnyA,
                manualAccountAmountMicros = MoneyAmount.parseToMicros("79.50")!!
            )
        ).getOrThrow()
        assertEquals(MoneyAmount.parseToMicros("920.50")!!, accountDao.getById(cnyA)!!.balanceMicros)

        val movedToOrigCur = ledger.batchMoveTransactionsAccount(listOf(usdOrigOnCny.id), usdC).getOrThrow()
        assertEquals(1, movedToOrigCur)
        assertEquals(MoneyAmount.parseToMicros("1000.00")!!, accountDao.getById(cnyA)!!.balanceMicros)
        assertEquals(MoneyAmount.parseToMicros("172.50")!!, accountDao.getById(usdC)!!.balanceMicros)
        val updatedUsdTx = transactionDao.getById(usdOrigOnCny.id)!!
        assertEquals(MoneyAmount.parseToMicros("10.00")!!, updatedUsdTx.accountAmountMicros)
        assertEquals(MoneyAmount.RATE_SCALE, updatedUsdTx.rateToAccountMicros)
        assertEquals("USD", updatedUsdTx.accountCurrency)
    }

    @Test
    fun computeCreditBillingCycleSummary_handlesCrossMonthBoundariesLeapMonthAndSpendRepaid() {
        val creditAccount = AccountEntity(
            id = 10L,
            name = "招行信用卡",
            groupType = AccountGroup.CREDIT,
            currencyCode = "CNY",
            balanceMicros = MoneyAmount.parseToMicros("-500.00")!!,
            creditLimitMicros = MoneyAmount.parseToMicros("20000.00")!!,
            billingDay = 10,
            repaymentDay = 28
        )

        val txs = listOf(
            // Out-of-cycle (Nov 10 23:59) -> must be excluded when now = Nov 15
            TransactionEntity(
                id = 1L,
                type = TransactionType.EXPENSE,
                timestamp = millisOf(2026, 11, 10, 23, 59),
                origAmountMicros = MoneyAmount.parseToMicros("500.00")!!,
                origCurrency = "CNY",
                accountId = 10L,
                accountAmountMicros = MoneyAmount.parseToMicros("500.00")!!,
                accountCurrency = "CNY",
                status = TransactionStatus.CONFIRMED
            ),
            // In-cycle EXPENSE 200.00
            TransactionEntity(
                id = 2L,
                type = TransactionType.EXPENSE,
                timestamp = millisOf(2026, 11, 11, 0, 1),
                origAmountMicros = MoneyAmount.parseToMicros("200.00")!!,
                origCurrency = "CNY",
                accountId = 10L,
                accountAmountMicros = MoneyAmount.parseToMicros("200.00")!!,
                accountCurrency = "CNY",
                status = TransactionStatus.CONFIRMED
            ),
            // In-cycle TRANSFER out 50.00
            TransactionEntity(
                id = 3L,
                type = TransactionType.TRANSFER,
                timestamp = millisOf(2026, 11, 14, 12, 0),
                origAmountMicros = MoneyAmount.parseToMicros("50.00")!!,
                origCurrency = "CNY",
                accountId = 10L,
                accountAmountMicros = MoneyAmount.parseToMicros("50.00")!!,
                accountCurrency = "CNY",
                toAccountId = 20L,
                toAmountMicros = MoneyAmount.parseToMicros("50.00")!!,
                status = TransactionStatus.CONFIRMED
            ),
            // In-cycle Refund 30.00 -> offsets spend (200 + 50 - 30 = 220.00), NOT repaid
            TransactionEntity(
                id = 4L,
                type = TransactionType.INCOME,
                timestamp = millisOf(2026, 11, 15, 9, 0),
                origAmountMicros = MoneyAmount.parseToMicros("30.00")!!,
                origCurrency = "CNY",
                accountId = 10L,
                accountAmountMicros = MoneyAmount.parseToMicros("30.00")!!,
                accountCurrency = "CNY",
                refundOfTxId = 2L,
                status = TransactionStatus.CONFIRMED
            ),
            // In-cycle PENDING expense -> ignored
            TransactionEntity(
                id = 5L,
                type = TransactionType.EXPENSE,
                timestamp = millisOf(2026, 11, 15, 10, 0),
                origAmountMicros = MoneyAmount.parseToMicros("999.00")!!,
                origCurrency = "CNY",
                accountId = 10L,
                accountAmountMicros = MoneyAmount.parseToMicros("999.00")!!,
                accountCurrency = "CNY",
                status = TransactionStatus.PENDING
            ),
            // In-cycle Repayment TRANSFER into credit card (uses toAmountMicros = 120.00)
            TransactionEntity(
                id = 6L,
                type = TransactionType.TRANSFER,
                timestamp = millisOf(2026, 11, 20, 18, 0),
                origAmountMicros = MoneyAmount.parseToMicros("15.00")!!,
                origCurrency = "USD",
                accountId = 20L,
                accountAmountMicros = MoneyAmount.parseToMicros("15.00")!!,
                accountCurrency = "USD",
                toAccountId = 10L,
                toAmountMicros = MoneyAmount.parseToMicros("120.00")!!,
                toCurrency = "CNY",
                status = TransactionStatus.CONFIRMED
            ),
            // In-cycle non-refund INCOME cashback 25.00 -> counts toward repaid (120 + 25 = 145.00)
            TransactionEntity(
                id = 7L,
                type = TransactionType.INCOME,
                timestamp = millisOf(2026, 12, 10, 23, 0),
                origAmountMicros = MoneyAmount.parseToMicros("25.00")!!,
                origCurrency = "CNY",
                accountId = 10L,
                accountAmountMicros = MoneyAmount.parseToMicros("25.00")!!,
                accountCurrency = "CNY",
                status = TransactionStatus.CONFIRMED
            ),
            // In-cycle ADJUST -> neither spend nor repaid
            TransactionEntity(
                id = 8L,
                type = TransactionType.ADJUST,
                timestamp = millisOf(2026, 11, 21, 10, 0),
                origAmountMicros = MoneyAmount.parseToMicros("10.00")!!,
                origCurrency = "CNY",
                accountId = 10L,
                accountAmountMicros = MoneyAmount.parseToMicros("10.00")!!,
                accountCurrency = "CNY",
                status = TransactionStatus.CONFIRMED
            )
        )

        // 1. After billing day (Nov 15): cycle is Nov 11 00:00 .. Dec 10 23:59:59.999
        val afterBillSummary = LedgerAnalytics.computeCreditBillingCycleSummary(
            account = creditAccount,
            transactions = txs,
            nowMillis = millisOf(2026, 11, 15, 10, 0),
            zone = zoneId
        )
        assertEquals(millisOf(2026, 11, 11, 0, 0), afterBillSummary.cycleStartMillis)
        assertEquals(millisOf(2026, 12, 11, 0, 0) - 1L, afterBillSummary.cycleEndMillis)
        assertEquals(MoneyAmount.parseToMicros("220.00")!!, afterBillSummary.cycleSpendMicros)
        assertEquals(MoneyAmount.parseToMicros("145.00")!!, afterBillSummary.cycleRepaidMicros)
        assertEquals(10, afterBillSummary.billingDay)
        assertEquals(28, afterBillSummary.repaymentDay)

        // 2. On billing day (Nov 10): cycle is Oct 11 00:00 .. Nov 10 23:59:59.999 -> includes tx #1 (500.00)
        val onBillSummary = LedgerAnalytics.computeCreditBillingCycleSummary(
            account = creditAccount,
            transactions = txs,
            nowMillis = millisOf(2026, 11, 10, 18, 0),
            zone = zoneId
        )
        assertEquals(millisOf(2026, 10, 11, 0, 0), onBillSummary.cycleStartMillis)
        assertEquals(millisOf(2026, 11, 11, 0, 0) - 1L, onBillSummary.cycleEndMillis)
        assertEquals(MoneyAmount.parseToMicros("500.00")!!, onBillSummary.cycleSpendMicros)
        assertEquals(0L, onBillSummary.cycleRepaidMicros)

        // 3. Month-end clamping (billingDay = 31 in non-leap Feb 2025): Feb 1 .. Feb 28
        val endOfMonthCard = creditAccount.copy(billingDay = 31, repaymentDay = 20)
        val febSummary = LedgerAnalytics.computeCreditBillingCycleSummary(
            account = endOfMonthCard,
            transactions = emptyList(),
            nowMillis = millisOf(2025, 2, 20, 12, 0),
            zone = zoneId
        )
        assertEquals(millisOf(2025, 2, 1, 0, 0), febSummary.cycleStartMillis)
        assertEquals(millisOf(2025, 3, 1, 0, 0) - 1L, febSummary.cycleEndMillis)

        // 4. Missing billingDay falls back to calendar month and clamps oversized refund to 0
        val noBillCard = creditAccount.copy(billingDay = null, repaymentDay = null)
        val bigRefundOnly = listOf(
            TransactionEntity(
                id = 9L,
                type = TransactionType.INCOME,
                timestamp = millisOf(2026, 11, 12, 10, 0),
                origAmountMicros = MoneyAmount.parseToMicros("99.00")!!,
                origCurrency = "CNY",
                accountId = 10L,
                accountAmountMicros = MoneyAmount.parseToMicros("99.00")!!,
                accountCurrency = "CNY",
                refundOfTxId = 1L,
                status = TransactionStatus.CONFIRMED
            )
        )
        val fallbackSummary = LedgerAnalytics.computeCreditBillingCycleSummary(
            account = noBillCard,
            transactions = bigRefundOnly,
            nowMillis = millisOf(2026, 11, 15, 10, 0),
            zone = zoneId
        )
        assertEquals(millisOf(2026, 11, 1, 0, 0), fallbackSummary.cycleStartMillis)
        assertEquals(millisOf(2026, 12, 1, 0, 0) - 1L, fallbackSummary.cycleEndMillis)
        assertEquals(0L, fallbackSummary.cycleSpendMicros)
        assertEquals(0L, fallbackSummary.cycleRepaidMicros)
        assertNull(fallbackSummary.billingDay)
        assertNull(fallbackSummary.repaymentDay)
    }

    @Test
    fun computeNetWorth_includesOpenNetWorthDebtsAndReactsToPartialSettleAndEdit() = runTest {
        exchangeRateDao.upsert(
            ExchangeRateEntity(
                baseCurrency = "USD",
                targetCurrency = "CNY",
                rateMicros = 7_000_000L,
                date = "2026-11-22",
                source = "CUSTOM"
            )
        )
        accountDao.insert(
            AccountEntity(
                name = "储蓄卡",
                groupType = AccountGroup.DEBIT,
                currencyCode = "CNY",
                balanceMicros = MoneyAmount.parseToMicros("1000.00")!!
            )
        )
        accountDao.insert(
            AccountEntity(
                name = "信用卡",
                groupType = AccountGroup.CREDIT,
                currencyCode = "CNY",
                balanceMicros = MoneyAmount.parseToMicros("-100.00")!!
            )
        )

        // 1. 借出 100 USD (计入净资产 -> +700 CNY 总资产)
        val lendUsdId = ledger.saveDebt(
            DebtRecordEntity(
                direction = DebtDirection.LEND,
                personName = "Alice",
                amountMicros = MoneyAmount.parseToMicros("100.00")!!,
                currencyCode = "USD",
                note = "代垫机票",
                includeInNetWorth = true,
                createdAt = millisOf(2026, 11, 1, 9, 0)
            ),
            syncAccountBalance = false
        )
        // 2. 借入 200 CNY (计入净资产 -> +200 CNY 总负债)
        val borrowCreatedAt = millisOf(2026, 11, 2, 10, 0)
        val borrowCnyId = ledger.saveDebt(
            DebtRecordEntity(
                direction = DebtDirection.BORROW,
                personName = "Bob",
                amountMicros = MoneyAmount.parseToMicros("200.00")!!,
                currencyCode = "CNY",
                note = "拼单房租",
                includeInNetWorth = true,
                createdAt = borrowCreatedAt
            ),
            syncAccountBalance = false
        )
        // 3. 借出 500 CNY (不计入净资产 -> 忽略)
        ledger.saveDebt(
            DebtRecordEntity(
                direction = DebtDirection.LEND,
                personName = "Charlie",
                amountMicros = MoneyAmount.parseToMicros("500.00")!!,
                currencyCode = "CNY",
                includeInNetWorth = false
            ),
            syncAccountBalance = false
        )
        // 4. 已结清借出 400 CNY (即使 includeInNetWorth = true 也忽略)
        ledger.saveDebt(
            DebtRecordEntity(
                direction = DebtDirection.LEND,
                personName = "Dave",
                amountMicros = MoneyAmount.parseToMicros("400.00")!!,
                settledAmountMicros = MoneyAmount.parseToMicros("400.00")!!,
                currencyCode = "CNY",
                status = DebtStatus.SETTLED,
                includeInNetWorth = true
            ),
            syncAccountBalance = false
        )

        // 初始净资产: 资产 1000 + 700 = 1700, 负债 100 + 200 = 300, 净资产 1400
        val initial = ledger.computeNetWorth("CNY")
        assertEquals(MoneyAmount.parseToMicros("1700.00")!!, initial.totalAssetsMicros)
        assertEquals(MoneyAmount.parseToMicros("300.00")!!, initial.totalLiabilitiesMicros)
        assertEquals(MoneyAmount.parseToMicros("1400.00")!!, initial.netWorthMicros)

        // 部分核销 USD 借出 40 USD -> 剩余 60 USD (420 CNY) -> 资产变为 1420, 净资产 1120
        val afterPartialSettle = ledger.settleDebt(
            debtId = lendUsdId,
            settleAmountMicros = MoneyAmount.parseToMicros("40.00")!!,
            accountId = null,
            note = "先还 40 美元"
        ).getOrThrow()
        assertEquals(DebtStatus.OPEN, afterPartialSettle.status)
        assertEquals(MoneyAmount.parseToMicros("40.00")!!, afterPartialSettle.settledAmountMicros)

        val afterSettleNetWorth = ledger.computeNetWorth("CNY")
        assertEquals(MoneyAmount.parseToMicros("1420.00")!!, afterSettleNetWorth.totalAssetsMicros)
        assertEquals(MoneyAmount.parseToMicros("300.00")!!, afterSettleNetWorth.totalLiabilitiesMicros)
        assertEquals(MoneyAmount.parseToMicros("1120.00")!!, afterSettleNetWorth.netWorthMicros)

        // 编辑已有借入记录：改金额 350、已核销 100、往来人/备注/还款日，传入 createdAt = 0L 必须保留原 createdAt
        nowMillis = millisOf(2026, 11, 25, 18, 0)
        val dueDate = millisOf(2026, 12, 15, 12, 0)
        ledger.saveDebt(
            DebtRecordEntity(
                id = borrowCnyId,
                direction = DebtDirection.BORROW,
                personName = "Bob 室友",
                amountMicros = MoneyAmount.parseToMicros("350.00")!!,
                settledAmountMicros = MoneyAmount.parseToMicros("100.00")!!,
                currencyCode = "CNY",
                dueDateMillis = dueDate,
                note = "合租水电与房租",
                status = DebtStatus.SETTLED, // 应自动校正为 OPEN
                createdAt = 0L,
                includeInNetWorth = true
            ),
            syncAccountBalance = false
        )
        val editedBorrow = debtRecordDao.getById(borrowCnyId)!!
        assertEquals("Bob 室友", editedBorrow.personName)
        assertEquals("合租水电与房租", editedBorrow.note)
        assertEquals(dueDate, editedBorrow.dueDateMillis)
        assertEquals(borrowCreatedAt, editedBorrow.createdAt)
        assertEquals(nowMillis, editedBorrow.updatedAt)
        assertEquals(DebtStatus.OPEN, editedBorrow.status)
        assertEquals(MoneyAmount.parseToMicros("100.00")!!, editedBorrow.settledAmountMicros)
        assertTrue(editedBorrow.includeInNetWorth)

        // 此时未结清借入 = 250 CNY -> 负债 = 100 + 250 = 350, 净资产 = 1420 - 350 = 1070
        val afterEditNetWorth = ledger.computeNetWorth("CNY")
        assertEquals(MoneyAmount.parseToMicros("1420.00")!!, afterEditNetWorth.totalAssetsMicros)
        assertEquals(MoneyAmount.parseToMicros("350.00")!!, afterEditNetWorth.totalLiabilitiesMicros)
        assertEquals(MoneyAmount.parseToMicros("1070.00")!!, afterEditNetWorth.netWorthMicros)

        // 编辑已核销金额超额 -> 钳位到总额并自动标记为 SETTLED -> 负债回落到 100 CNY
        ledger.saveDebt(
            editedBorrow.copy(
                settledAmountMicros = MoneyAmount.parseToMicros("999.00")!!,
                status = DebtStatus.OPEN
            ),
            syncAccountBalance = false
        )
        val clampedSettledBorrow = debtRecordDao.getById(borrowCnyId)!!
        assertEquals(MoneyAmount.parseToMicros("350.00")!!, clampedSettledBorrow.settledAmountMicros)
        assertEquals(DebtStatus.SETTLED, clampedSettledBorrow.status)

        val afterAutoSettled = ledger.computeNetWorth("CNY")
        assertEquals(MoneyAmount.parseToMicros("1420.00")!!, afterAutoSettled.totalAssetsMicros)
        assertEquals(MoneyAmount.parseToMicros("100.00")!!, afterAutoSettled.totalLiabilitiesMicros)
        assertEquals(MoneyAmount.parseToMicros("1320.00")!!, afterAutoSettled.netWorthMicros)

        // 取消 USD 借出的 includeInNetWorth -> 资产恢复 1000, 净资产 900
        val lendEntity = debtRecordDao.getById(lendUsdId)!!
        ledger.saveDebt(lendEntity.copy(includeInNetWorth = false), syncAccountBalance = false)
        val afterToggleOff = ledger.computeNetWorth("CNY")
        assertEquals(MoneyAmount.parseToMicros("1000.00")!!, afterToggleOff.totalAssetsMicros)
        assertEquals(MoneyAmount.parseToMicros("100.00")!!, afterToggleOff.totalLiabilitiesMicros)
        assertEquals(MoneyAmount.parseToMicros("900.00")!!, afterToggleOff.netWorthMicros)
    }
}
