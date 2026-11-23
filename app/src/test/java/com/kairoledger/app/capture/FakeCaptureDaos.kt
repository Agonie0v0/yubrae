package com.kairoledger.app.capture

import com.kairoledger.app.core.contract.BudgetProgress
import com.kairoledger.app.core.contract.CategoryStat
import com.kairoledger.app.core.contract.ConversionPreview
import com.kairoledger.app.core.contract.LedgerService
import com.kairoledger.app.core.contract.MonthlyPoint
import com.kairoledger.app.core.contract.NetWorthSummary
import com.kairoledger.app.core.contract.RecordTransactionRequest
import com.kairoledger.app.core.model.CurrencyCatalog
import com.kairoledger.app.core.model.MoneyAmount
import com.kairoledger.app.data.db.AccountAliasDao
import com.kairoledger.app.data.db.AccountAliasEntity
import com.kairoledger.app.data.db.AccountDao
import com.kairoledger.app.data.db.AccountEntity
import com.kairoledger.app.data.db.AccountGroup
import com.kairoledger.app.data.db.AliasMatchType
import com.kairoledger.app.data.db.BudgetEntity
import com.kairoledger.app.data.db.CategoryDao
import com.kairoledger.app.data.db.CategoryEntity
import com.kairoledger.app.data.db.ParseRuleDao
import com.kairoledger.app.data.db.ParseRuleEntity
import com.kairoledger.app.data.db.RawCaptureLogDao
import com.kairoledger.app.data.db.RawCaptureLogEntity
import com.kairoledger.app.data.db.RecurringRuleEntity
import com.kairoledger.app.data.db.TagEntity
import com.kairoledger.app.data.db.TransactionDao
import com.kairoledger.app.data.db.TransactionEntity
import com.kairoledger.app.data.db.TransactionStatus
import com.kairoledger.app.data.db.TransactionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

internal class FakeAccountDao : AccountDao {
    private val items = mutableListOf<AccountEntity>()
    private val flow = MutableStateFlow<List<AccountEntity>>(emptyList())
    private var nextId = 1L

    private fun emit() {
        flow.value = items.sortedWith(compareBy<AccountEntity> { it.sortOrder }.thenBy { it.id })
    }

    override fun observeActive(): Flow<List<AccountEntity>> =
        flow.map { list -> list.filter { !it.archived } }

    override fun observeAll(): Flow<List<AccountEntity>> = flow

    override suspend fun getAll(): List<AccountEntity> = flow.value

    override suspend fun getById(id: Long): AccountEntity? = items.firstOrNull { it.id == id }

    override suspend fun getByName(name: String): AccountEntity? = items.firstOrNull { it.name == name }

    override suspend fun insert(account: AccountEntity): Long {
        val id = if (account.id > 0L) account.id else nextId++
        if (id >= nextId) nextId = id + 1
        items.removeAll { it.id == id }
        items.add(account.copy(id = id))
        emit()
        return id
    }

    override suspend fun insertAll(accounts: List<AccountEntity>) {
        accounts.forEach { insert(it) }
    }

    override suspend fun update(account: AccountEntity) {
        val idx = items.indexOfFirst { it.id == account.id }
        if (idx >= 0) {
            items[idx] = account
            emit()
        }
    }

    override suspend fun adjustBalance(accountId: Long, deltaMicros: Long, updatedAt: Long) {
        val idx = items.indexOfFirst { it.id == accountId }
        if (idx >= 0) {
            val old = items[idx]
            items[idx] = old.copy(balanceMicros = old.balanceMicros + deltaMicros, updatedAt = updatedAt)
            emit()
        }
    }

    override suspend fun deleteById(id: Long) {
        items.removeAll { it.id == id }
        emit()
    }

    override suspend fun clearAll() {
        items.clear()
        emit()
    }
}

internal class FakeTransactionDao : TransactionDao {
    private val items = mutableListOf<TransactionEntity>()
    private val flow = MutableStateFlow<List<TransactionEntity>>(emptyList())
    private var nextId = 1L

    private fun emit() {
        flow.value = items.sortedWith(compareByDescending<TransactionEntity> { it.timestamp }.thenByDescending { it.id })
    }

    override fun observeAll(): Flow<List<TransactionEntity>> = flow

    override fun observeBetween(startMillis: Long, endMillis: Long): Flow<List<TransactionEntity>> =
        flow.map { list -> list.filter { it.timestamp in startMillis..endMillis } }

    override fun observePending(): Flow<List<TransactionEntity>> =
        flow.map { list -> list.filter { it.status == TransactionStatus.PENDING } }

    override fun observePendingCount(): Flow<Int> =
        flow.map { list -> list.count { it.status == TransactionStatus.PENDING } }

    override suspend fun getById(id: Long): TransactionEntity? = items.firstOrNull { it.id == id }

    override suspend fun getAll(): List<TransactionEntity> = flow.value

    override suspend fun getBetween(startMillis: Long, endMillis: Long): List<TransactionEntity> =
        flow.value.filter { it.timestamp in startMillis..endMillis }

    override suspend fun insert(tx: TransactionEntity): Long {
        val id = if (tx.id > 0L) tx.id else nextId++
        if (id >= nextId) nextId = id + 1
        items.removeAll { it.id == id }
        items.add(tx.copy(id = id))
        emit()
        return id
    }

    override suspend fun insertAll(txs: List<TransactionEntity>) {
        txs.forEach { insert(it) }
    }

    override suspend fun update(tx: TransactionEntity) {
        val idx = items.indexOfFirst { it.id == tx.id }
        if (idx >= 0) {
            items[idx] = tx
            emit()
        }
    }

    override suspend fun confirmById(id: Long, updatedAt: Long) {
        val idx = items.indexOfFirst { it.id == id }
        if (idx >= 0) {
            items[idx] = items[idx].copy(status = TransactionStatus.CONFIRMED, updatedAt = updatedAt)
            emit()
        }
    }

    override suspend fun confirmAllPending(updatedAt: Long) {
        for (i in items.indices) {
            if (items[i].status == TransactionStatus.PENDING) {
                items[i] = items[i].copy(status = TransactionStatus.CONFIRMED, updatedAt = updatedAt)
            }
        }
        emit()
    }

    override suspend fun updateCategoryForIds(ids: List<Long>, categoryId: Long?, updatedAt: Long) {
        val idSet = ids.toHashSet()
        for (i in items.indices) {
            if (items[i].id in idSet) {
                items[i] = items[i].copy(categoryId = categoryId, updatedAt = updatedAt)
            }
        }
        emit()
    }

    override suspend fun markReimbursed(ids: List<Long>, reimbursedTxId: Long, updatedAt: Long) {
        val idSet = ids.toHashSet()
        for (i in items.indices) {
            if (items[i].id in idSet) {
                items[i] = items[i].copy(
                    reimburseStatus = com.kairoledger.app.data.db.ReimburseStatus.REIMBURSED,
                    reimbursedTxId = reimbursedTxId,
                    updatedAt = updatedAt
                )
            }
        }
        emit()
    }

    override suspend fun deleteById(id: Long) {
        items.removeAll { it.id == id }
        emit()
    }

    override suspend fun clearAll() {
        items.clear()
        emit()
    }
}

internal class FakeCategoryDao : CategoryDao {
    private val items = mutableListOf<CategoryEntity>()
    private val flow = MutableStateFlow<List<CategoryEntity>>(emptyList())
    private var nextId = 1L

    private fun emit() {
        flow.value = items.sortedWith(compareBy<CategoryEntity> { it.sortOrder }.thenBy { it.id })
    }

    override fun observeAll(): Flow<List<CategoryEntity>> = flow

    override suspend fun getAll(): List<CategoryEntity> = flow.value

    override suspend fun getById(id: Long): CategoryEntity? = items.firstOrNull { it.id == id }

    override suspend fun findByNameAndType(name: String, type: String): CategoryEntity? =
        items.firstOrNull { it.name == name && it.type == type }

    override suspend fun insert(category: CategoryEntity): Long {
        val id = if (category.id > 0L) category.id else nextId++
        if (id >= nextId) nextId = id + 1
        items.removeAll { it.id == id }
        items.add(category.copy(id = id))
        emit()
        return id
    }

    override suspend fun insertAll(categories: List<CategoryEntity>) {
        categories.forEach { insert(it) }
    }

    override suspend fun update(category: CategoryEntity) {
        val idx = items.indexOfFirst { it.id == category.id }
        if (idx >= 0) {
            items[idx] = category
            emit()
        }
    }

    override suspend fun deleteById(id: Long) {
        items.removeAll { it.id == id }
        emit()
    }

    override suspend fun clearAll() {
        items.clear()
        emit()
    }
}

internal class FakeAccountAliasDao : AccountAliasDao {
    private val items = mutableListOf<AccountAliasEntity>()
    private val flow = MutableStateFlow<List<AccountAliasEntity>>(emptyList())
    private var nextId = 1L

    private fun emit() {
        flow.value = items.sortedWith(compareByDescending<AccountAliasEntity> { it.priority }.thenBy { it.id })
    }

    override fun observeAll(): Flow<List<AccountAliasEntity>> = flow

    override suspend fun getEnabled(): List<AccountAliasEntity> = flow.value.filter { it.enabled }

    override suspend fun getAll(): List<AccountAliasEntity> = flow.value

    override suspend fun insert(alias: AccountAliasEntity): Long {
        val id = if (alias.id > 0L) alias.id else nextId++
        if (id >= nextId) nextId = id + 1
        items.removeAll { it.id == id }
        items.add(alias.copy(id = id))
        emit()
        return id
    }

    override suspend fun insertAll(aliases: List<AccountAliasEntity>) {
        aliases.forEach { insert(it) }
    }

    override suspend fun update(alias: AccountAliasEntity) {
        val idx = items.indexOfFirst { it.id == alias.id }
        if (idx >= 0) {
            items[idx] = alias
            emit()
        }
    }

    override suspend fun deleteById(id: Long) {
        items.removeAll { it.id == id }
        emit()
    }

    override suspend fun clearAll() {
        items.clear()
        emit()
    }
}

internal class FakeParseRuleDao : ParseRuleDao {
    private val items = mutableListOf<ParseRuleEntity>()
    private val flow = MutableStateFlow<List<ParseRuleEntity>>(emptyList())
    private var nextId = 1L

    private fun emit() {
        flow.value = items.sortedWith(compareByDescending<ParseRuleEntity> { it.priority }.thenBy { it.id })
    }

    override fun observeAll(): Flow<List<ParseRuleEntity>> = flow

    override suspend fun getEnabled(): List<ParseRuleEntity> = flow.value.filter { it.enabled }

    override suspend fun getAll(): List<ParseRuleEntity> = flow.value

    override suspend fun insert(rule: ParseRuleEntity): Long {
        val id = if (rule.id > 0L) rule.id else nextId++
        if (id >= nextId) nextId = id + 1
        items.removeAll { it.id == id }
        items.add(rule.copy(id = id))
        emit()
        return id
    }

    override suspend fun insertAll(rules: List<ParseRuleEntity>) {
        rules.forEach { insert(it) }
    }

    override suspend fun update(rule: ParseRuleEntity) {
        val idx = items.indexOfFirst { it.id == rule.id }
        if (idx >= 0) {
            items[idx] = rule
            emit()
        }
    }

    override suspend fun deleteById(id: Long) {
        items.removeAll { it.id == id }
        emit()
    }

    override suspend fun clearAll() {
        items.clear()
        emit()
    }
}

internal class FakeRawCaptureLogDao : RawCaptureLogDao {
    private val items = mutableListOf<RawCaptureLogEntity>()
    private val flow = MutableStateFlow<List<RawCaptureLogEntity>>(emptyList())
    private var nextId = 1L

    private fun emit() {
        flow.value = items.sortedWith(compareByDescending<RawCaptureLogEntity> { it.capturedAt }.thenByDescending { it.id })
    }

    override fun observeRecent(limit: Int): Flow<List<RawCaptureLogEntity>> =
        flow.map { it.take(limit) }

    override suspend fun getAll(): List<RawCaptureLogEntity> = flow.value

    override suspend fun getById(id: Long): RawCaptureLogEntity? = items.firstOrNull { it.id == id }

    override suspend fun getSince(sinceMillis: Long): List<RawCaptureLogEntity> =
        flow.value.filter { it.capturedAt >= sinceMillis }

    override suspend fun insert(log: RawCaptureLogEntity): Long {
        val id = if (log.id > 0L) log.id else nextId++
        if (id >= nextId) nextId = id + 1
        items.removeAll { it.id == id }
        items.add(log.copy(id = id))
        emit()
        return id
    }

    override suspend fun insertAll(logs: List<RawCaptureLogEntity>) {
        logs.forEach { insert(it) }
    }

    override suspend fun update(log: RawCaptureLogEntity) {
        val idx = items.indexOfFirst { it.id == log.id }
        if (idx >= 0) {
            items[idx] = log
            emit()
        }
    }

    override suspend fun deleteById(id: Long) {
        items.removeAll { it.id == id }
        emit()
    }

    override suspend fun clearAll() {
        items.clear()
        emit()
    }
}

internal class FakeLedgerService(
    private val accountDao: AccountDao,
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao
) : LedgerService {

    override fun observeActiveAccounts(): Flow<List<AccountEntity>> = accountDao.observeActive()
    override fun observeAllAccounts(): Flow<List<AccountEntity>> = accountDao.observeAll()
    override fun observeTransactions(): Flow<List<TransactionEntity>> = transactionDao.observeAll()
    override fun observePendingTransactions(): Flow<List<TransactionEntity>> = transactionDao.observePending()
    override fun observePendingCount(): Flow<Int> = transactionDao.observePendingCount()
    override fun observeCategories(): Flow<List<CategoryEntity>> = categoryDao.observeAll()
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
        val acc = accountDao.getById(accountId) ?: error("Account $accountId not found")
        val targetBase = baseCurrency ?: CurrencyCatalog.DEFAULT_BASE_CURRENCY
        val rateToAccount = when {
            manualAccountAmountMicros != null -> MoneyAmount.computeRate(origAmountMicros, manualAccountAmountMicros)
            manualRateToAccountMicros != null -> manualRateToAccountMicros
            else -> CaptureDeduplicator.fallbackCrossRateMicros(origCurrency, acc.currencyCode)
        }
        val accountAmount = manualAccountAmountMicros ?: MoneyAmount.convert(origAmountMicros, rateToAccount)
        val rateToBase = CaptureDeduplicator.fallbackCrossRateMicros(origCurrency, targetBase)
        val baseAmount = MoneyAmount.convert(origAmountMicros, rateToBase)
        return ConversionPreview(
            origAmountMicros = origAmountMicros,
            origCurrency = origCurrency,
            accountAmountMicros = accountAmount,
            accountCurrency = acc.currencyCode,
            rateToAccountMicros = rateToAccount,
            baseAmountMicros = baseAmount,
            baseCurrency = targetBase,
            rateToBaseMicros = rateToBase,
            rateSource = "TEST"
        )
    }

    override suspend fun saveTransaction(request: RecordTransactionRequest): Result<TransactionEntity> = runCatching {
        request.existingId?.let { oldId ->
            transactionDao.getById(oldId)?.let { old ->
                when (old.type) {
                    TransactionType.EXPENSE -> accountDao.adjustBalance(old.accountId, old.accountAmountMicros)
                    TransactionType.INCOME -> accountDao.adjustBalance(old.accountId, -old.accountAmountMicros)
                    TransactionType.TRANSFER -> {
                        accountDao.adjustBalance(old.accountId, old.accountAmountMicros)
                        old.toAccountId?.let { toId ->
                            accountDao.adjustBalance(toId, -(old.toAmountMicros ?: old.accountAmountMicros))
                        }
                    }
                }
            }
        }

        val preview = previewConversion(
            origAmountMicros = request.origAmountMicros,
            origCurrency = request.origCurrency,
            accountId = request.accountId,
            manualAccountAmountMicros = request.manualAccountAmountMicros,
            manualRateToAccountMicros = request.manualRateToAccountMicros,
            baseCurrency = CurrencyCatalog.DEFAULT_BASE_CURRENCY
        )

        when (request.type) {
            TransactionType.EXPENSE -> accountDao.adjustBalance(request.accountId, -preview.accountAmountMicros)
            TransactionType.INCOME -> accountDao.adjustBalance(request.accountId, preview.accountAmountMicros)
        }

        val entity = TransactionEntity(
            id = request.existingId ?: 0L,
            type = request.type,
            timestamp = request.timestamp,
            origAmountMicros = request.origAmountMicros,
            origCurrency = request.origCurrency,
            accountId = request.accountId,
            accountAmountMicros = preview.accountAmountMicros,
            accountCurrency = preview.accountCurrency,
            toAccountId = request.toAccountId,
            toAmountMicros = request.manualToAmountMicros,
            rateToAccountMicros = preview.rateToAccountMicros,
            rateToBaseMicros = preview.rateToBaseMicros,
            baseCurrency = preview.baseCurrency,
            categoryId = request.categoryId,
            merchant = request.merchant,
            note = request.note,
            tags = request.tags,
            reimburseStatus = request.reimburseStatus,
            sourceType = request.sourceType,
            rawEventId = request.rawEventId,
            status = request.status
        )
        val savedId = if (request.existingId != null) {
            transactionDao.update(entity)
            request.existingId
        } else {
            transactionDao.insert(entity)
        }
        transactionDao.getById(savedId)!!
    }

    override suspend fun deleteTransaction(transactionId: Long): Result<Unit> = runCatching {
        val old = transactionDao.getById(transactionId) ?: return@runCatching
        when (old.type) {
            TransactionType.EXPENSE -> accountDao.adjustBalance(old.accountId, old.accountAmountMicros)
            TransactionType.INCOME -> accountDao.adjustBalance(old.accountId, -old.accountAmountMicros)
        }
        transactionDao.deleteById(transactionId)
    }

    override suspend fun confirmTransaction(transactionId: Long): Result<Unit> = runCatching {
        transactionDao.confirmById(transactionId)
    }

    override suspend fun confirmAllPending(): Result<Int> = runCatching {
        val count = transactionDao.getAll().count { it.status == TransactionStatus.PENDING }
        transactionDao.confirmAllPending()
        count
    }

    override suspend fun saveAccount(account: AccountEntity): Long = accountDao.insert(account)
    override suspend fun archiveAccount(accountId: Long, archived: Boolean) {}
    override suspend fun calibrateAccountBalance(accountId: Long, targetBalanceMicros: Long, note: String): Result<TransactionEntity> =
        Result.failure(UnsupportedOperationException())
    override suspend fun saveCategory(category: CategoryEntity): Long = categoryDao.insert(category)
    override suspend fun deleteCategory(categoryId: Long) = categoryDao.deleteById(categoryId)
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

    override fun observeLedgers(): Flow<List<com.kairoledger.app.data.db.LedgerEntity>> = MutableStateFlow(emptyList())
    override suspend fun saveLedger(ledger: com.kairoledger.app.data.db.LedgerEntity): Long = 1L
    override suspend fun archiveLedger(ledgerId: Long, archived: Boolean) {}
    override suspend fun batchUpdateCategory(txIds: List<Long>, categoryId: Long?): Result<Int> = Result.success(0)
    override suspend fun batchDeleteTransactions(txIds: List<Long>): Result<Int> = Result.success(0)
    override suspend fun settleReimbursements(
        txIds: List<Long>,
        targetAccountId: Long,
        timestamp: Long,
        note: String
    ): Result<TransactionEntity> = Result.failure(UnsupportedOperationException())
    override fun observeDebts(): Flow<List<com.kairoledger.app.data.db.DebtRecordEntity>> = MutableStateFlow(emptyList())
    override suspend fun saveDebt(debt: com.kairoledger.app.data.db.DebtRecordEntity, syncAccountBalance: Boolean): Long = 0L
    override suspend fun settleDebt(debtId: Long, settleAmountMicros: Long, accountId: Long?, note: String): Result<com.kairoledger.app.data.db.DebtRecordEntity> =
        Result.failure(UnsupportedOperationException())
    override suspend fun deleteDebt(debtId: Long) {}
    override fun observeInstallments(): Flow<List<com.kairoledger.app.data.db.InstallmentPlanEntity>> = MutableStateFlow(emptyList())
    override suspend fun saveInstallment(plan: com.kairoledger.app.data.db.InstallmentPlanEntity): Long = 0L
    override suspend fun recordInstallmentPeriod(planId: Long): Result<TransactionEntity> = Result.failure(UnsupportedOperationException())
    override suspend fun deleteInstallment(planId: Long) {}
    override suspend fun computeNetWorthHistory(monthsBack: Int, displayCurrency: String): List<com.kairoledger.app.core.contract.NetWorthPoint> = emptyList()
    override suspend fun computeTrendBuckets(
        granularity: String,
        anchorDateMillis: Long,
        customStartMillis: Long?,
        customEndMillis: Long?,
        ledgerId: Long,
        displayCurrency: String
    ): List<com.kairoledger.app.core.contract.TrendBucketPoint> = emptyList()
    override suspend fun computeCategoryStatsForLedger(
        startMillis: Long,
        endMillis: Long,
        type: String,
        ledgerId: Long,
        displayCurrency: String
    ): List<CategoryStat> = emptyList()
    override suspend fun computeBudgetProgressForLedger(year: Int, month: Int, ledgerId: Long): List<BudgetProgress> = emptyList()
    override fun observeTags(): Flow<List<TagEntity>> = MutableStateFlow(emptyList())
    override suspend fun saveTag(tag: TagEntity): Long = 0L
    override suspend fun deleteTag(tagId: Long) {}
    override suspend fun ensurePresetCatalogSeeded(): Pair<Int, Int> = 0 to 0
}

internal object CaptureTestFixtures {
    suspend fun seedFixtures(
        accountDao: AccountDao,
        aliasDao: AccountAliasDao,
        categoryDao: CategoryDao,
        ruleDao: ParseRuleDao
    ) {
        val wechatId = accountDao.insert(AccountEntity(name = "微信零钱", groupType = AccountGroup.WALLET, currencyCode = "CNY"))
        val alipayId = accountDao.insert(AccountEntity(name = "支付宝", groupType = AccountGroup.WALLET, currencyCode = "CNY"))
        val cmbCreditId = accountDao.insert(AccountEntity(name = "招商信用卡6688", groupType = AccountGroup.CREDIT, currencyCode = "CNY"))
        val usdId = accountDao.insert(AccountEntity(name = "美元账户 (USD)", groupType = AccountGroup.FOREIGN, currencyCode = "USD"))
        val eurId = accountDao.insert(AccountEntity(name = "欧元账户 (EUR)", groupType = AccountGroup.FOREIGN, currencyCode = "EUR"))
        val gbpId = accountDao.insert(AccountEntity(name = "英镑账户 (GBP)", groupType = AccountGroup.FOREIGN, currencyCode = "GBP"))

        aliasDao.insertAll(
            listOf(
                AccountAliasEntity(accountId = wechatId, matchType = AliasMatchType.PACKAGE, pattern = "com.tencent.mm", priority = 100),
                AccountAliasEntity(accountId = alipayId, matchType = AliasMatchType.PACKAGE, pattern = "com.eg.android.AlipayGphone", priority = 100),
                AccountAliasEntity(accountId = cmbCreditId, matchType = AliasMatchType.CARD_LAST4, pattern = "6688", priority = 50),
                AccountAliasEntity(accountId = cmbCreditId, matchType = AliasMatchType.KEYWORD, pattern = "信用卡", priority = 65),
                AccountAliasEntity(accountId = usdId, matchType = AliasMatchType.KEYWORD, pattern = "Wise", priority = 70),
                AccountAliasEntity(accountId = eurId, matchType = AliasMatchType.REGEX, pattern = "Revolut.*EUR", priority = 75),
                AccountAliasEntity(accountId = gbpId, matchType = AliasMatchType.KEYWORD, pattern = "Sainsbury", priority = 60)
            )
        )

        categoryDao.insertAll(
            listOf(
                CategoryEntity(name = "餐饮美食", type = TransactionType.EXPENSE),
                CategoryEntity(name = "交通出行", type = TransactionType.EXPENSE),
                CategoryEntity(name = "日用购物", type = TransactionType.EXPENSE),
                CategoryEntity(name = "数字订阅", type = TransactionType.EXPENSE),
                CategoryEntity(name = "其他支出", type = TransactionType.EXPENSE),
                CategoryEntity(name = "工资薪金", type = TransactionType.INCOME),
                CategoryEntity(name = "报销退款", type = TransactionType.INCOME),
                CategoryEntity(name = "其他收入", type = TransactionType.INCOME)
            )
        )

        ruleDao.insertAll(
            listOf(
                ParseRuleEntity(
                    name = "微信支付付款通知",
                    sourcePackage = "com.tencent.mm",
                    channelType = "NOTIFICATION",
                    regexPattern = "(?:微信支付|向)(.*?)付款(?:人民币|RMB)?[￥¥]?([0-9]+(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?)元?",
                    transactionType = TransactionType.EXPENSE,
                    amountGroup = 2,
                    currencyGroup = 0,
                    defaultCurrency = "CNY",
                    merchantGroup = 1,
                    defaultCategoryName = "日用购物",
                    priority = 100,
                    isBuiltIn = true
                ),
                ParseRuleEntity(
                    name = "支付宝支付/扣款通知",
                    sourcePackage = "com.eg.android.AlipayGphone",
                    channelType = "NOTIFICATION",
                    regexPattern = "(?:向(.*?)付款|消费|支付|扣款)[￥¥]?([0-9]+(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?)元?",
                    transactionType = TransactionType.EXPENSE,
                    amountGroup = 2,
                    defaultCurrency = "CNY",
                    merchantGroup = 1,
                    defaultCategoryName = "日用购物",
                    priority = 100,
                    isBuiltIn = true
                ),
                ParseRuleEntity(
                    name = "银行外币消费通用规则(USD/EUR/GBP)",
                    sourcePackage = "*",
                    channelType = "ANY",
                    regexPattern = "(?:尾号(\\d{4})).*?(?:消费|支出|扣款|支付|spent|charged)\\s*(USD|EUR|GBP|HKD|JPY|SGD|CNY|RMB|\\$|€|£|¥)\\s*([0-9]+(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?)",
                    transactionType = TransactionType.EXPENSE,
                    amountGroup = 3,
                    currencyGroup = 2,
                    defaultCurrency = "USD",
                    accountHintGroup = 1,
                    defaultCategoryName = "数字订阅",
                    priority = 90,
                    isBuiltIn = true
                ),
                ParseRuleEntity(
                    name = "银行人民币支出通用短信/通知",
                    sourcePackage = "*",
                    channelType = "ANY",
                    regexPattern = "(?:尾号(\\d{4})).*?(?:支出|消费|扣款|付款|转出).*?(?:人民币|RMB|CNY|¥|￥)?\\s*([0-9]+(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?)\\s*元?",
                    transactionType = TransactionType.EXPENSE,
                    amountGroup = 2,
                    defaultCurrency = "CNY",
                    accountHintGroup = 1,
                    defaultCategoryName = "其他支出",
                    priority = 80,
                    isBuiltIn = true
                ),
                ParseRuleEntity(
                    name = "银行人民币收入/入账短信",
                    sourcePackage = "*",
                    channelType = "ANY",
                    regexPattern = "(?:尾号(\\d{4})).*?(?:收入|入账|转入|到账|工资).*?(?:人民币|RMB|CNY|¥|￥)?\\s*([0-9]+(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?)\\s*元?",
                    transactionType = TransactionType.INCOME,
                    amountGroup = 2,
                    defaultCurrency = "CNY",
                    accountHintGroup = 1,
                    defaultCategoryName = "其他收入",
                    priority = 80,
                    isBuiltIn = true
                ),
                ParseRuleEntity(
                    name = "英文境外卡/Wise/Revolut扣款通知",
                    sourcePackage = "*",
                    channelType = "NOTIFICATION",
                    regexPattern = "(?:You spent|Paid|Charged|Payment of)\\s*(\\$|€|£|USD|EUR|GBP|CNY)\\s*([0-9]+(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?)\\s*(?:at|to)\\s+([^.\\n]+)",
                    transactionType = TransactionType.EXPENSE,
                    amountGroup = 2,
                    currencyGroup = 1,
                    defaultCurrency = "USD",
                    merchantGroup = 3,
                    defaultCategoryName = "数字订阅",
                    priority = 88,
                    isBuiltIn = true
                )
            )
        )
    }
}
