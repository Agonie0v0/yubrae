package com.yubrae.app.sync

import com.yubrae.app.data.db.AccountAliasDao
import com.yubrae.app.data.db.AccountAliasEntity
import com.yubrae.app.data.db.AccountDao
import com.yubrae.app.data.db.AccountEntity
import com.yubrae.app.data.db.BudgetDao
import com.yubrae.app.data.db.BudgetEntity
import com.yubrae.app.data.db.CategoryDao
import com.yubrae.app.data.db.CategoryEntity
import com.yubrae.app.data.db.ExchangeRateDao
import com.yubrae.app.data.db.ExchangeRateEntity
import com.yubrae.app.data.db.ParseRuleDao
import com.yubrae.app.data.db.ParseRuleEntity
import com.yubrae.app.data.db.RawCaptureLogDao
import com.yubrae.app.data.db.RawCaptureLogEntity
import com.yubrae.app.data.db.RecurringRuleDao
import com.yubrae.app.data.db.RecurringRuleEntity
import com.yubrae.app.data.db.TagDao
import com.yubrae.app.data.db.TagEntity
import com.yubrae.app.data.db.TransactionDao
import com.yubrae.app.data.db.TransactionEntity
import com.yubrae.app.data.prefs.AppSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

internal class InMemorySyncDatabase {
    val accountDao = MemAccountDao()
    val transactionDao = MemTransactionDao()
    val categoryDao = MemCategoryDao()
    val exchangeRateDao = MemExchangeRateDao()
    val accountAliasDao = MemAccountAliasDao()
    val parseRuleDao = MemParseRuleDao()
    val rawCaptureLogDao = MemRawCaptureLogDao()
    val budgetDao = MemBudgetDao()
    val recurringRuleDao = MemRecurringRuleDao()
    val tagDao = MemTagDao()

    var settings = AppSettings()

    fun createSerializer(): BackupSerializer = BackupSerializer(
        accountDao = accountDao,
        transactionDao = transactionDao,
        categoryDao = categoryDao,
        exchangeRateDao = exchangeRateDao,
        accountAliasDao = accountAliasDao,
        parseRuleDao = parseRuleDao,
        rawCaptureLogDao = rawCaptureLogDao,
        budgetDao = budgetDao,
        recurringRuleDao = recurringRuleDao,
        readSettings = { settings },
        writeSettings = { settings = it },
        runInTransaction = { block -> block() },
        tagDao = tagDao
    )

    suspend fun clearAllTables() {
        transactionDao.clearAll()
        rawCaptureLogDao.clearAll()
        recurringRuleDao.clearAll()
        budgetDao.clearAll()
        accountAliasDao.clearAll()
        parseRuleDao.clearAll()
        exchangeRateDao.clearAll()
        categoryDao.clearAll()
        accountDao.clearAll()
        tagDao.clearAll()
    }

    class MemAccountDao : AccountDao {
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

    class MemTransactionDao : TransactionDao {
        private val items = LinkedHashMap<Long, TransactionEntity>()
        private val flow = MutableStateFlow<List<TransactionEntity>>(emptyList())
        private var nextId = 1L

        private fun publish() { flow.value = items.values.toList() }

        override fun observeAll(): Flow<List<TransactionEntity>> = flow
        override fun observeBetween(startMillis: Long, endMillis: Long): Flow<List<TransactionEntity>> =
            flow.map { list -> list.filter { it.timestamp in startMillis..endMillis } }
        override fun observePending(): Flow<List<TransactionEntity>> =
            flow.map { list -> list.filter { it.status == "PENDING" } }
        override fun observePendingCount(): Flow<Int> = observePending().map { it.size }
        override suspend fun getById(id: Long): TransactionEntity? = items[id]
        override suspend fun getAll(): List<TransactionEntity> = items.values.toList()
        override suspend fun getBetween(startMillis: Long, endMillis: Long): List<TransactionEntity> =
            items.values.filter { it.timestamp in startMillis..endMillis }

        override suspend fun getByLedgerId(ledgerId: Long): List<TransactionEntity> =
            items.values.filter { it.ledgerId == ledgerId }

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

        override suspend fun updateCategoryForIds(ids: List<Long>, categoryId: Long?, updatedAt: Long) {
            val idSet = ids.toHashSet()
            items.replaceAll { _, tx ->
                if (tx.id in idSet) tx.copy(categoryId = categoryId, updatedAt = updatedAt) else tx
            }
            publish()
        }

        override suspend fun markReimbursed(ids: List<Long>, reimbursedTxId: Long, updatedAt: Long) {
            val idSet = ids.toHashSet()
            items.replaceAll { _, tx ->
                if (tx.id in idSet) tx.copy(reimburseStatus = "REIMBURSED", reimbursedTxId = reimbursedTxId, updatedAt = updatedAt) else tx
            }
            publish()
        }

        override suspend fun migrateLedger(fromLedgerId: Long, toLedgerId: Long, updatedAt: Long) {
            items.replaceAll { _, tx ->
                if (tx.ledgerId == fromLedgerId) tx.copy(ledgerId = toLedgerId, updatedAt = updatedAt) else tx
            }
            publish()
        }

        override suspend fun clearRefundRefsForLedger(deletedLedgerId: Long, updatedAt: Long) {
            val deletedIds = items.values.asSequence().filter { it.ledgerId == deletedLedgerId }.map { it.id }.toHashSet()
            if (deletedIds.isEmpty()) return
            items.replaceAll { _, tx ->
                if (tx.ledgerId != deletedLedgerId && tx.refundOfTxId in deletedIds) {
                    tx.copy(refundOfTxId = null, updatedAt = updatedAt)
                } else {
                    tx
                }
            }
            publish()
        }

        override suspend fun clearReimbursedRefsForLedger(deletedLedgerId: Long, updatedAt: Long) {
            val deletedIds = items.values.asSequence().filter { it.ledgerId == deletedLedgerId }.map { it.id }.toHashSet()
            if (deletedIds.isEmpty()) return
            items.replaceAll { _, tx ->
                if (tx.ledgerId != deletedLedgerId && tx.reimbursedTxId in deletedIds) {
                    tx.copy(reimbursedTxId = null, updatedAt = updatedAt)
                } else {
                    tx
                }
            }
            publish()
        }

        override suspend fun deleteByLedgerId(ledgerId: Long) {
            items.entries.removeAll { it.value.ledgerId == ledgerId }
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

    class MemCategoryDao : CategoryDao {
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

    class MemExchangeRateDao : ExchangeRateDao {
        private val items = LinkedHashMap<Long, ExchangeRateEntity>()
        private val flow = MutableStateFlow<List<ExchangeRateEntity>>(emptyList())
        private var nextId = 1L

        private fun publish() { flow.value = items.values.toList() }

        override fun observeAll(): Flow<List<ExchangeRateEntity>> = flow
        override suspend fun getAll(): List<ExchangeRateEntity> = items.values.toList()
        override suspend fun getLatest(base: String, target: String): ExchangeRateEntity? =
            items.values.filter { it.baseCurrency == base && it.targetCurrency == target }
                .maxByOrNull { it.date }
        override suspend fun getClosestOnOrBefore(base: String, target: String, date: String): ExchangeRateEntity? =
            items.values.filter { it.baseCurrency == base && it.targetCurrency == target && it.date <= date }
                .maxByOrNull { it.date }

        override suspend fun upsertAll(rates: List<ExchangeRateEntity>) {
            rates.forEach { upsert(it) }
        }

        override suspend fun upsert(rate: ExchangeRateEntity): Long {
            val id = if (rate.id > 0L) rate.id else nextId++
            if (id >= nextId) nextId = id + 1
            items[id] = rate.copy(id = id)
            publish()
            return id
        }

        override suspend fun clearAll() {
            items.clear()
            publish()
        }
    }

    class MemAccountAliasDao : AccountAliasDao {
        private val items = LinkedHashMap<Long, AccountAliasEntity>()
        private val flow = MutableStateFlow<List<AccountAliasEntity>>(emptyList())
        private var nextId = 1L

        private fun publish() { flow.value = items.values.toList() }

        override fun observeAll(): Flow<List<AccountAliasEntity>> = flow
        override suspend fun getEnabled(): List<AccountAliasEntity> = items.values.filter { it.enabled }
        override suspend fun getAll(): List<AccountAliasEntity> = items.values.toList()

        override suspend fun insert(alias: AccountAliasEntity): Long {
            val id = if (alias.id > 0L) alias.id else nextId++
            if (id >= nextId) nextId = id + 1
            items[id] = alias.copy(id = id)
            publish()
            return id
        }

        override suspend fun insertAll(aliases: List<AccountAliasEntity>) {
            aliases.forEach { insert(it) }
        }

        override suspend fun update(alias: AccountAliasEntity) {
            items[alias.id] = alias
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

    class MemParseRuleDao : ParseRuleDao {
        private val items = LinkedHashMap<Long, ParseRuleEntity>()
        private val flow = MutableStateFlow<List<ParseRuleEntity>>(emptyList())
        private var nextId = 1L

        private fun publish() { flow.value = items.values.toList() }

        override fun observeAll(): Flow<List<ParseRuleEntity>> = flow
        override suspend fun getEnabled(): List<ParseRuleEntity> = items.values.filter { it.enabled }
        override suspend fun getAll(): List<ParseRuleEntity> = items.values.toList()

        override suspend fun insert(rule: ParseRuleEntity): Long {
            val id = if (rule.id > 0L) rule.id else nextId++
            if (id >= nextId) nextId = id + 1
            items[id] = rule.copy(id = id)
            publish()
            return id
        }

        override suspend fun insertAll(rules: List<ParseRuleEntity>) {
            rules.forEach { insert(it) }
        }

        override suspend fun update(rule: ParseRuleEntity) {
            items[rule.id] = rule
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

    class MemRawCaptureLogDao : RawCaptureLogDao {
        private val items = LinkedHashMap<Long, RawCaptureLogEntity>()
        private val flow = MutableStateFlow<List<RawCaptureLogEntity>>(emptyList())
        private var nextId = 1L

        private fun publish() { flow.value = items.values.toList() }

        override fun observeRecent(limit: Int): Flow<List<RawCaptureLogEntity>> = flow.map { it.take(limit) }
        override suspend fun getAll(): List<RawCaptureLogEntity> = items.values.toList()
        override suspend fun getById(id: Long): RawCaptureLogEntity? = items[id]
        override suspend fun getSince(sinceMillis: Long): List<RawCaptureLogEntity> =
            items.values.filter { it.capturedAt >= sinceMillis }

        override suspend fun insert(log: RawCaptureLogEntity): Long {
            val id = if (log.id > 0L) log.id else nextId++
            if (id >= nextId) nextId = id + 1
            items[id] = log.copy(id = id)
            publish()
            return id
        }

        override suspend fun insertAll(logs: List<RawCaptureLogEntity>) {
            logs.forEach { insert(it) }
        }

        override suspend fun update(log: RawCaptureLogEntity) {
            items[log.id] = log
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

    class MemBudgetDao : BudgetDao {
        private val items = LinkedHashMap<Long, BudgetEntity>()
        private val flow = MutableStateFlow<List<BudgetEntity>>(emptyList())
        private var nextId = 1L

        private fun publish() { flow.value = items.values.toList() }

        override fun observeAll(): Flow<List<BudgetEntity>> = flow
        override suspend fun getAll(): List<BudgetEntity> = items.values.toList()

        override suspend fun getByLedgerId(ledgerId: Long): List<BudgetEntity> =
            items.values.filter { it.ledgerId == ledgerId }

        override suspend fun insert(budget: BudgetEntity): Long {
            val id = if (budget.id > 0L) budget.id else nextId++
            if (id >= nextId) nextId = id + 1
            items[id] = budget.copy(id = id)
            publish()
            return id
        }

        override suspend fun insertAll(budgets: List<BudgetEntity>) {
            budgets.forEach { insert(it) }
        }

        override suspend fun update(budget: BudgetEntity) {
            items[budget.id] = budget
            publish()
        }

        override suspend fun deleteByLedgerId(ledgerId: Long) {
            items.entries.removeAll { it.value.ledgerId == ledgerId }
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

    class MemRecurringRuleDao : RecurringRuleDao {
        private val items = LinkedHashMap<Long, RecurringRuleEntity>()
        private val flow = MutableStateFlow<List<RecurringRuleEntity>>(emptyList())
        private var nextId = 1L

        private fun publish() { flow.value = items.values.toList() }

        override fun observeAll(): Flow<List<RecurringRuleEntity>> = flow
        override suspend fun getDueRules(nowMillis: Long): List<RecurringRuleEntity> =
            items.values.filter { it.enabled && it.nextRunEpochMillis <= nowMillis }
        override suspend fun getAll(): List<RecurringRuleEntity> = items.values.toList()

        override suspend fun insert(rule: RecurringRuleEntity): Long {
            val id = if (rule.id > 0L) rule.id else nextId++
            if (id >= nextId) nextId = id + 1
            items[id] = rule.copy(id = id)
            publish()
            return id
        }

        override suspend fun insertAll(rules: List<RecurringRuleEntity>) {
            rules.forEach { insert(it) }
        }

        override suspend fun update(rule: RecurringRuleEntity) {
            items[rule.id] = rule
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

    class MemTagDao : TagDao {
        private val items = LinkedHashMap<Long, TagEntity>()
        private val flow = MutableStateFlow<List<TagEntity>>(emptyList())
        private var nextId = 1L

        private fun sortedValues(): List<TagEntity> =
            items.values.sortedWith(compareBy<TagEntity> { it.sortOrder }.thenBy { it.id })

        private fun publish() { flow.value = sortedValues() }

        override fun observeAll(): Flow<List<TagEntity>> = flow
        override suspend fun getAll(): List<TagEntity> = sortedValues()
        override suspend fun getById(id: Long): TagEntity? = items[id]
        override suspend fun findByName(name: String): TagEntity? = items.values.firstOrNull { it.name == name }

        override suspend fun insert(tag: TagEntity): Long {
            val id = if (tag.id > 0L) tag.id else nextId++
            if (id >= nextId) nextId = id + 1
            val duplicateNameIds = items.values.filter { it.name == tag.name && it.id != id }.map { it.id }
            duplicateNameIds.forEach { items.remove(it) }
            items[id] = tag.copy(id = id)
            publish()
            return id
        }

        override suspend fun insertAll(tags: List<TagEntity>) {
            tags.forEach { insert(it) }
        }

        override suspend fun update(tag: TagEntity) {
            items[tag.id] = tag
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
