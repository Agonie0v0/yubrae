package com.kairoledger.app.importer

import com.kairoledger.app.core.contract.BudgetProgress
import com.kairoledger.app.core.contract.CategoryStat
import com.kairoledger.app.core.contract.ConversionPreview
import com.kairoledger.app.core.contract.ExchangeRateProvider
import com.kairoledger.app.core.contract.LedgerService
import com.kairoledger.app.core.contract.MonthlyPoint
import com.kairoledger.app.core.contract.NetWorthSummary
import com.kairoledger.app.core.contract.RateQuote
import com.kairoledger.app.core.contract.RecordTransactionRequest
import com.kairoledger.app.core.model.MoneyAmount
import com.kairoledger.app.data.db.AccountDao
import com.kairoledger.app.data.db.AccountEntity
import com.kairoledger.app.data.db.AccountGroup
import com.kairoledger.app.data.db.BudgetEntity
import com.kairoledger.app.data.db.CaptureChannel
import com.kairoledger.app.data.db.CategoryDao
import com.kairoledger.app.data.db.CategoryEntity
import com.kairoledger.app.data.db.ExchangeRateEntity
import com.kairoledger.app.data.db.RecurringRuleEntity
import com.kairoledger.app.data.db.TransactionDao
import com.kairoledger.app.data.db.TransactionEntity
import com.kairoledger.app.data.db.TransactionType
import com.kairoledger.app.data.prefs.AppSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.charset.Charset
import java.time.LocalDateTime
import java.time.ZoneId

class YimuCsvImporterTest {

    private val testZone: ZoneId = ZoneId.of("Asia/Shanghai")

    @Test
    fun rfc4180Parser_handlesQuotedCommasNewlinesAndEscapedQuotes() {
        val csv = buildString {
            appendLine("col1,col2,col3,col4")
            append("a,\"1,250.50\",\"line1\nline2\",\"\"\"Pro\"\" Plan\"\r\n")
            append("b,plain,x,\n")
        }

        val rows = Rfc4180CsvParser.parse(csv)
        assertEquals(3, rows.size)
        assertEquals(listOf("col1", "col2", "col3", "col4"), rows[0])
        assertEquals(listOf("a", "1,250.50", "line1\nline2", "\"Pro\" Plan"), rows[1])
        assertEquals(listOf("b", "plain", "x", ""), rows[2])
    }

    @Test
    fun previewAndImportYimuMultiCurrencyCsv_createsMissingEntitiesAndImportsChronologically() = runTest {
        val env = TestEnv()
        env.accountDao.insert(
            AccountEntity(
                id = 1L,
                name = "微信零钱",
                groupType = AccountGroup.WALLET,
                currencyCode = "CNY"
            )
        )
        val foodParentId = env.categoryDao.insert(
            CategoryEntity(name = "餐饮美食", type = TransactionType.EXPENSE)
        )

        // Newer row placed before older row to verify chronological sorting oldest -> newest
        val csvContent = """
            日期,收支类型,金额,币种,一级分类,二级分类,账户,转入账户,商品,备注,标签
            2026-03-15 18:00:00,支出,"20.00",USD,数字订阅,AI服务,美元信用卡,,OpenAI,"ChatGPT ""Plus"", monthly",AI
            2026-03-10 09:30:00,支出,35.50,CNY,餐饮美食,咖啡茶饮,微信零钱,,星巴克,拿铁,日常
            2026-03-16 10:00:00,转账,100.00,USD,账户互转,,美元信用卡,欧元储蓄卡,,换汇划转,
        """.trimIndent()
        val utf8BomBytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) +
            csvContent.toByteArray(Charsets.UTF_8)

        val preview = env.importer.previewCsv(ByteArrayInputStream(utf8BomBytes)).getOrThrow()
        assertEquals("一木记账 CSV", preview.formatName)
        assertEquals(3, preview.totalRows)
        assertEquals(3, preview.validRows)
        assertEquals(0, preview.skippedDuplicateRows)
        assertEquals(listOf("美元信用卡", "欧元储蓄卡"), preview.newAccountNames)
        assertEquals(listOf("数字订阅", "AI服务", "咖啡茶饮"), preview.newCategoryNames)
        assertTrue(preview.detectedCurrencies.containsAll(listOf("USD", "CNY")))

        val summary = env.importer.importCsv(ByteArrayInputStream(utf8BomBytes)).getOrThrow()
        assertEquals(3, summary.importedCount)
        assertEquals(0, summary.skippedDuplicateCount)
        assertEquals(2, summary.createdAccounts)
        assertEquals(3, summary.createdCategories)

        // Verify chronological order of saveTransaction calls
        val saved = env.ledgerService.savedRequests
        assertEquals(3, saved.size)
        val ts0 = LocalDateTime.of(2026, 3, 10, 9, 30, 0).atZone(testZone).toInstant().toEpochMilli()
        val ts1 = LocalDateTime.of(2026, 3, 15, 18, 0, 0).atZone(testZone).toInstant().toEpochMilli()
        val ts2 = LocalDateTime.of(2026, 3, 16, 10, 0, 0).atZone(testZone).toInstant().toEpochMilli()
        assertEquals(ts0, saved[0].timestamp)
        assertEquals(ts1, saved[1].timestamp)
        assertEquals(ts2, saved[2].timestamp)

        // Verify sub-category parent linkage
        val coffeeCat = env.categoryDao.findByNameAndType("咖啡茶饮", TransactionType.EXPENSE)
        assertNotNull(coffeeCat)
        assertEquals(foodParentId, coffeeCat!!.parentId)

        // Verify created USD account has USD currencyCode
        val usdAcc = env.accountDao.getByName("美元信用卡")
        assertNotNull(usdAcc)
        assertEquals("USD", usdAcc!!.currencyCode)
        assertEquals(AccountGroup.CREDIT, usdAcc.groupType)

        // Verify historical date was requested from exchangeRateProvider for cross-currency transfer
        assertTrue(env.rateProvider.queriedDates.contains("2026-03-16"))
        assertEquals(TransactionType.TRANSFER, saved[2].type)
        assertEquals(CaptureChannel.IMPORT, saved[1].sourceType)
        assertEquals("ChatGPT \"Plus\", monthly", saved[1].note)
    }

    @Test
    fun importWeChatCsv_skipsPreambleAndNeutralOrFailedRows() = runTest {
        val env = TestEnv()
        val wechatCsv = """
            微信支付账单明细,,,,,,,,
            昵称：[测试用户],,,,,,,,
            起始时间：[2026-03-01 00:00:00] 终止时间：[2026-03-31 23:59:59],,,,,,,,
            ----------------------微信支付账单明细列表--------------------,,,,,,,,
            交易时间,交易类型,交易对方,商品,收/支,金额(元),支付方式,当前状态,交易单号,商户单号,备注
            2026-03-01 12:15:00,商户消费,瑞幸咖啡,生椰拿铁,支出,¥18.50,零钱,支付成功,1001,2001,/
            2026-03-02 09:00:00,微信红包,张三,/,收入,¥50.00,零钱,已存入零钱,1002,2002,新年红包
            2026-03-03 10:00:00,零钱提现,银行卡,提现,中性交易,¥100.00,零钱,已到账,1003,2003,/
            2026-03-04 11:00:00,商户消费,测试商户,订单,支出,¥9.90,零钱,支付失败,1004,2004,/
        """.trimIndent()
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) +
            wechatCsv.toByteArray(Charsets.UTF_8)

        val preview = env.importer.previewCsv(ByteArrayInputStream(bytes)).getOrThrow()
        assertEquals("微信账单 CSV", preview.formatName)
        assertEquals(4, preview.totalRows)
        assertEquals(2, preview.validRows)
        assertEquals(2, preview.warnings.size)

        val summary = env.importer.importCsv(ByteArrayInputStream(bytes)).getOrThrow()
        assertEquals(2, summary.importedCount)
        val txs = env.transactionDao.getAll()
        assertEquals(2, txs.size)
        val expenseTx = txs.first { it.type == TransactionType.EXPENSE }
        assertEquals(185_000L, expenseTx.origAmountMicros)
        assertEquals("CNY", expenseTx.origCurrency)
        assertEquals("瑞幸咖啡", expenseTx.merchant)
        assertEquals("生椰拿铁", expenseTx.note)
    }

    @Test
    fun importAlipayCsvInGb18030_decodesChineseWithoutBomAndSkipsClosedOrders() = runTest {
        val env = TestEnv()
        val alipayCsv = """
            支付宝交易记录明细查询,,,,,,,,,,,,
            账号:[demo@example.com],,,,,,,,,,,,
            ---------------------------------交易记录明细列表------------------------------------,,,,,,,,,,,,
            交易时间,交易分类,交易对方,对方账号,商品说明,收/支,金额,收/付款方式,交易状态,交易订单号,商家订单号,备注
            2026-04-05 08:20:10,餐饮美食,肯德基,kfc@pay,早餐套餐,支出,26.80,花呗,交易成功,A001,M001,/
            2026-04-06 14:10:00,退款,淘宝店铺,tb@pay,退货退款,收入,120.00,余额宝,退款成功,A002,M002,运费险
            2026-04-07 16:00:00,日用购物,某商户,m@pay,未付款订单,支出,88.00,支付宝,交易关闭,A003,M003,/
            ------------------------------------------------------------------------------------,,,,,,,,,,,,
            共3笔记录,,,,,,,,,,,,
        """.trimIndent()
        val gb18030Bytes = alipayCsv.toByteArray(Charset.forName("GB18030"))

        val preview = env.importer.previewCsv(ByteArrayInputStream(gb18030Bytes)).getOrThrow()
        assertEquals("支付宝账单 CSV", preview.formatName)
        assertEquals(3, preview.totalRows)
        assertEquals(2, preview.validRows)
        assertEquals(1, preview.warnings.size)

        val summary = env.importer.importCsv(ByteArrayInputStream(gb18030Bytes)).getOrThrow()
        assertEquals(2, summary.importedCount)
        val huabei = env.accountDao.getByName("花呗")
        assertNotNull(huabei)
        assertEquals(AccountGroup.CREDIT, huabei!!.groupType)
    }

    @Test
    fun reimportSameCsv_deduplicatesStrictlyByTuple() = runTest {
        val env = TestEnv()
        val csv = """
            日期,收支类型,金额,币种,类别,账户,备注
            2026-05-01 12:00:00,支出,45.00,CNY,餐饮美食,现金,午饭
            2026-05-01 12:00:00,支出,45.00,CNY,餐饮美食,现金,午饭
            2026-05-02 18:30:00,收入,300.00,CNY,兼职副业,现金,设计费
        """.trimIndent()
        val bytes = csv.toByteArray(Charsets.UTF_8)

        // First import: 1 intra-CSV duplicate skipped, 2 imported
        val firstSummary = env.importer.importCsv(ByteArrayInputStream(bytes)).getOrThrow()
        assertEquals(2, firstSummary.importedCount)
        assertEquals(1, firstSummary.skippedDuplicateCount)

        // Preview after import: all rows are now duplicates, no new accounts/categories
        val secondPreview = env.importer.previewCsv(ByteArrayInputStream(bytes)).getOrThrow()
        assertEquals(3, secondPreview.totalRows)
        assertEquals(0, secondPreview.validRows)
        assertEquals(3, secondPreview.skippedDuplicateRows)
        assertTrue(secondPreview.newAccountNames.isEmpty())
        assertTrue(secondPreview.newCategoryNames.isEmpty())

        // Append 1 new transaction with different timestamp
        val csvWithOneNew = csv + "\n2026-05-03 09:00:00,支出,12.00,CNY,交通出行,现金,地铁"
        val secondSummary = env.importer.importCsv(
            ByteArrayInputStream(csvWithOneNew.toByteArray(Charsets.UTF_8))
        ).getOrThrow()
        assertEquals(1, secondSummary.importedCount)
        assertEquals(3, secondSummary.skippedDuplicateCount)
        assertEquals(3, env.transactionDao.getAll().size)
    }

    private inner class TestEnv {
        val accountDao = FakeAccountDao()
        val categoryDao = FakeCategoryDao()
        val transactionDao = FakeTransactionDao()
        val rateProvider = FakeExchangeRateProvider()
        val ledgerService = FakeLedgerService(accountDao, transactionDao, rateProvider)
        val importer = YimuCsvImporter(
            accountDao = accountDao,
            categoryDao = categoryDao,
            transactionDao = transactionDao,
            exchangeRateProvider = rateProvider,
            ledgerService = ledgerService,
            settingsProvider = { AppSettings(baseCurrency = "CNY") },
            zoneId = testZone
        )
    }

    private class FakeExchangeRateProvider : ExchangeRateProvider {
        val queriedDates = mutableListOf<String?>()

        override fun observeRates(): Flow<List<ExchangeRateEntity>> = MutableStateFlow(emptyList())

        override suspend fun getRate(fromCurrency: String, toCurrency: String, onDate: String?): RateQuote {
            queriedDates.add(onDate)
            val rate = when {
                fromCurrency == toCurrency -> 1_000_000L
                fromCurrency == "USD" && toCurrency == "CNY" -> 7_250_000L
                fromCurrency == "USD" && toCurrency == "EUR" -> 920_000L
                fromCurrency == "EUR" && toCurrency == "CNY" -> 7_800_000L
                else -> 1_000_000L
            }
            return RateQuote(fromCurrency, toCurrency, rate, onDate ?: "2026-03-16", "MOCK")
        }

        override suspend fun refreshFromNetwork(baseCurrency: String): Result<Int> = Result.success(0)

        override suspend fun saveCustomRate(fromCurrency: String, toCurrency: String, rateMicros: Long) {}
    }

    private class FakeLedgerService(
        private val accountDao: FakeAccountDao,
        private val transactionDao: FakeTransactionDao,
        private val rateProvider: FakeExchangeRateProvider
    ) : LedgerService {
        val savedRequests = mutableListOf<RecordTransactionRequest>()

        override fun observeActiveAccounts(): Flow<List<AccountEntity>> = accountDao.observeActive()
        override fun observeAllAccounts(): Flow<List<AccountEntity>> = accountDao.observeAll()
        override fun observeTransactions(): Flow<List<TransactionEntity>> = transactionDao.observeAll()
        override fun observePendingTransactions(): Flow<List<TransactionEntity>> = transactionDao.observePending()
        override fun observePendingCount(): Flow<Int> = transactionDao.observePendingCount()
        override fun observeCategories(): Flow<List<CategoryEntity>> = MutableStateFlow(emptyList())
        override fun observeBudgets(): Flow<List<BudgetEntity>> = MutableStateFlow(emptyList())
        override fun observeRecurringRules(): Flow<List<RecurringRuleEntity>> = MutableStateFlow(emptyList())

        override suspend fun previewConversion(
            origAmountMicros: Long,
            origCurrency: String,
            accountId: Long,
            manualAccountAmountMicros: Long?,
            manualRateToAccountMicros: Long?,
            baseCurrency: String?
        ): ConversionPreview {
            val acc = accountDao.getById(accountId)!!
            val rateToAcc = manualRateToAccountMicros ?: rateProvider.getRate(origCurrency, acc.currencyCode).rateMicros
            val accAmount = manualAccountAmountMicros ?: MoneyAmount.convert(origAmountMicros, rateToAcc)
            val base = baseCurrency ?: "CNY"
            val rateToBase = rateProvider.getRate(origCurrency, base).rateMicros
            return ConversionPreview(
                origAmountMicros = origAmountMicros,
                origCurrency = origCurrency,
                accountAmountMicros = accAmount,
                accountCurrency = acc.currencyCode,
                rateToAccountMicros = rateToAcc,
                baseAmountMicros = MoneyAmount.convert(origAmountMicros, rateToBase),
                baseCurrency = base,
                rateToBaseMicros = rateToBase,
                rateSource = "MOCK"
            )
        }

        override suspend fun saveTransaction(request: RecordTransactionRequest): Result<TransactionEntity> {
            savedRequests.add(request)
            val acc = accountDao.getById(request.accountId)!!
            val toAcc = request.toAccountId?.let { accountDao.getById(it) }
            val rateToAcc = request.manualRateToAccountMicros
                ?: rateProvider.getRate(request.origCurrency, acc.currencyCode).rateMicros
            val accAmount = request.manualAccountAmountMicros
                ?: MoneyAmount.convert(request.origAmountMicros, rateToAcc)
            val rateToBase = rateProvider.getRate(request.origCurrency, "CNY").rateMicros
            val entity = TransactionEntity(
                type = request.type,
                timestamp = request.timestamp,
                origAmountMicros = request.origAmountMicros,
                origCurrency = request.origCurrency,
                accountId = request.accountId,
                accountAmountMicros = accAmount,
                accountCurrency = acc.currencyCode,
                toAccountId = toAcc?.id,
                toAmountMicros = request.manualToAmountMicros,
                toCurrency = toAcc?.currencyCode,
                rateToAccountMicros = rateToAcc,
                rateToBaseMicros = rateToBase,
                baseCurrency = "CNY",
                categoryId = request.categoryId,
                merchant = request.merchant,
                note = request.note,
                tags = request.tags,
                reimburseStatus = request.reimburseStatus,
                sourceType = request.sourceType,
                rawEventId = request.rawEventId,
                status = request.status
            )
            val id = transactionDao.insert(entity)
            return Result.success(entity.copy(id = id))
        }

        override suspend fun deleteTransaction(transactionId: Long): Result<Unit> = Result.success(Unit)
        override suspend fun confirmTransaction(transactionId: Long): Result<Unit> = Result.success(Unit)
        override suspend fun confirmAllPending(): Result<Int> = Result.success(0)
        override suspend fun saveAccount(account: AccountEntity): Long = accountDao.insert(account)
        override suspend fun archiveAccount(accountId: Long, archived: Boolean) {}
        override suspend fun calibrateAccountBalance(accountId: Long, targetBalanceMicros: Long, note: String): Result<TransactionEntity> =
            Result.failure(UnsupportedOperationException())
        override suspend fun saveCategory(category: CategoryEntity): Long = 0L
        override suspend fun deleteCategory(categoryId: Long) {}
        override suspend fun saveBudget(budget: BudgetEntity): Long = 0L
        override suspend fun deleteBudget(budgetId: Long) {}
        override suspend fun saveRecurringRule(rule: RecurringRuleEntity): Long = 0L
        override suspend fun deleteRecurringRule(ruleId: Long) {}
        override suspend fun processDueRecurringRules(nowMillis: Long): Int = 0
        override suspend fun computeNetWorth(displayCurrency: String): NetWorthSummary =
            NetWorthSummary(displayCurrency, 0L, 0L, 0L, emptyMap())
        override suspend fun computeMonthlyTrend(monthsBack: Int, displayCurrency: String): List<MonthlyPoint> = emptyList()
        override suspend fun computeCategoryStats(startMillis: Long, endMillis: Long, type: String, displayCurrency: String): List<CategoryStat> = emptyList()
        override suspend fun computeBudgetProgress(year: Int, month: Int): List<BudgetProgress> = emptyList()
    }

    private class FakeAccountDao : AccountDao {
        private val items = LinkedHashMap<Long, AccountEntity>()
        private val flow = MutableStateFlow<List<AccountEntity>>(emptyList())
        private var nextId = 1L

        private fun publish() { flow.value = items.values.toList() }

        override fun observeActive(): Flow<List<AccountEntity>> = flow.map { list -> list.filter { !it.archived } }
        override fun observeAll(): Flow<List<AccountEntity>> = flow
        override suspend fun getAll(): List<AccountEntity> = items.values.toList()
        override suspend fun getById(id: Long): AccountEntity? = items[id]
        override suspend fun getByName(name: String): AccountEntity? = items.values.firstOrNull { it.name == name }

        override suspend fun insert(account: AccountEntity): Long {
            val id = if (account.id > 0L) account.id else nextId++
            if (id >= nextId) nextId = id + 1
            items[id] = account.copy(id = id)
            publish()
            return id
        }

        override suspend fun insertAll(accounts: List<AccountEntity>) {
            accounts.forEach { insert(it) }
        }

        override suspend fun update(account: AccountEntity) {
            items[account.id] = account
            publish()
        }

        override suspend fun adjustBalance(accountId: Long, deltaMicros: Long, updatedAt: Long) {
            val cur = items[accountId] ?: return
            items[accountId] = cur.copy(balanceMicros = cur.balanceMicros + deltaMicros, updatedAt = updatedAt)
            publish()
        }

        override suspend fun deleteById(id: Long) {
            items.remove(id)
            publish()
        }

        override suspend fun clearAll() {
            items.clear()
            publish()
        }
    }

    private class FakeCategoryDao : CategoryDao {
        private val items = LinkedHashMap<Long, CategoryEntity>()
        private val flow = MutableStateFlow<List<CategoryEntity>>(emptyList())
        private var nextId = 1L

        private fun publish() { flow.value = items.values.toList() }

        override fun observeAll(): Flow<List<CategoryEntity>> = flow
        override suspend fun getAll(): List<CategoryEntity> = items.values.toList()
        override suspend fun getById(id: Long): CategoryEntity? = items[id]
        override suspend fun findByNameAndType(name: String, type: String): CategoryEntity? =
            items.values.firstOrNull { it.name == name && it.type == type }

        override suspend fun insert(category: CategoryEntity): Long {
            val id = if (category.id > 0L) category.id else nextId++
            if (id >= nextId) nextId = id + 1
            items[id] = category.copy(id = id)
            publish()
            return id
        }

        override suspend fun insertAll(categories: List<CategoryEntity>) {
            categories.forEach { insert(it) }
        }

        override suspend fun update(category: CategoryEntity) {
            items[category.id] = category
            publish()
        }

        override suspend fun deleteById(id: Long) {
            items.remove(id)
            publish()
        }

        override suspend fun clearAll() {
            items.clear()
            publish()
        }
    }

    private class FakeTransactionDao : TransactionDao {
        private val items = LinkedHashMap<Long, TransactionEntity>()
        private val flow = MutableStateFlow<List<TransactionEntity>>(emptyList())
        private var nextId = 1L

        private fun publish() { flow.value = items.values.sortedByDescending { it.timestamp } }

        override fun observeAll(): Flow<List<TransactionEntity>> = flow
        override fun observeBetween(startMillis: Long, endMillis: Long): Flow<List<TransactionEntity>> =
            flow.map { list -> list.filter { it.timestamp in startMillis..endMillis } }
        override fun observePending(): Flow<List<TransactionEntity>> =
            flow.map { list -> list.filter { it.status == "PENDING" } }
        override fun observePendingCount(): Flow<Int> =
            observePending().map { it.size }
        override suspend fun getById(id: Long): TransactionEntity? = items[id]
        override suspend fun getAll(): List<TransactionEntity> = items.values.toList()
        override suspend fun getBetween(startMillis: Long, endMillis: Long): List<TransactionEntity> =
            items.values.filter { it.timestamp in startMillis..endMillis }

        override suspend fun insert(tx: TransactionEntity): Long {
            val id = if (tx.id > 0L) tx.id else nextId++
            if (id >= nextId) nextId = id + 1
            items[id] = tx.copy(id = id)
            publish()
            return id
        }

        override suspend fun insertAll(txs: List<TransactionEntity>) {
            txs.forEach { insert(it) }
        }

        override suspend fun update(tx: TransactionEntity) {
            items[tx.id] = tx
            publish()
        }

        override suspend fun confirmById(id: Long, updatedAt: Long) {
            val cur = items[id] ?: return
            items[id] = cur.copy(status = "CONFIRMED", updatedAt = updatedAt)
            publish()
        }

        override suspend fun confirmAllPending(updatedAt: Long) {
            items.replaceAll { _, tx ->
                if (tx.status == "PENDING") tx.copy(status = "CONFIRMED", updatedAt = updatedAt) else tx
            }
            publish()
        }

        override suspend fun deleteById(id: Long) {
            items.remove(id)
            publish()
        }

        override suspend fun clearAll() {
            items.clear()
            publish()
        }
    }
}
