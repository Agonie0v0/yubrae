package com.kairoledger.app.domain

import com.kairoledger.app.data.db.AccountDao
import com.kairoledger.app.data.db.AccountEntity
import com.kairoledger.app.data.db.BudgetDao
import com.kairoledger.app.data.db.BudgetEntity
import com.kairoledger.app.data.db.CategoryDao
import com.kairoledger.app.data.db.CategoryEntity
import com.kairoledger.app.data.db.ExchangeRateDao
import com.kairoledger.app.data.db.ExchangeRateEntity
import com.kairoledger.app.data.db.RecurringRuleDao
import com.kairoledger.app.data.db.RecurringRuleEntity
import com.kairoledger.app.data.db.TransactionDao
import com.kairoledger.app.data.db.TransactionEntity
import com.kairoledger.app.data.db.TransactionStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeExchangeRateDao : ExchangeRateDao {
    private val items = mutableListOf<ExchangeRateEntity>()
    private val flow = MutableStateFlow<List<ExchangeRateEntity>>(emptyList())
    private var nextId = 1L

    private fun emit() {
        flow.value = items.sortedByDescending { it.updatedAt }
    }

    override fun observeAll(): Flow<List<ExchangeRateEntity>> = flow

    override suspend fun getAll(): List<ExchangeRateEntity> =
        items.sortedByDescending { it.updatedAt }

    override suspend fun getLatest(base: String, target: String): ExchangeRateEntity? =
        items
            .filter { it.baseCurrency == base && it.targetCurrency == target }
            .sortedWith(compareByDescending<ExchangeRateEntity> { it.date }.thenByDescending { it.updatedAt })
            .firstOrNull()

    override suspend fun getClosestOnOrBefore(base: String, target: String, date: String): ExchangeRateEntity? =
        items
            .filter { it.baseCurrency == base && it.targetCurrency == target && it.date <= date }
            .sortedWith(compareByDescending<ExchangeRateEntity> { it.date }.thenByDescending { it.updatedAt })
            .firstOrNull()

    override suspend fun upsertAll(rates: List<ExchangeRateEntity>) {
        for (rate in rates) {
            upsertInternal(rate)
        }
        emit()
    }

    override suspend fun upsert(rate: ExchangeRateEntity): Long {
        val id = upsertInternal(rate)
        emit()
        return id
    }

    private fun upsertInternal(rate: ExchangeRateEntity): Long {
        items.removeAll {
            it.baseCurrency == rate.baseCurrency &&
                it.targetCurrency == rate.targetCurrency &&
                it.date == rate.date
        }
        val assignedId = if (rate.id > 0L) rate.id else nextId++
        if (assignedId >= nextId) nextId = assignedId + 1
        items.add(rate.copy(id = assignedId))
        return assignedId
    }

    override suspend fun clearAll() {
        items.clear()
        emit()
    }
}

class FakeAccountDao : AccountDao {
    private val items = mutableListOf<AccountEntity>()
    private val flow = MutableStateFlow<List<AccountEntity>>(emptyList())
    private var nextId = 1L

    private fun sortedItems(): List<AccountEntity> =
        items.sortedWith(compareBy<AccountEntity> { it.sortOrder }.thenBy { it.id })

    private fun emit() {
        flow.value = sortedItems()
    }

    override fun observeActive(): Flow<List<AccountEntity>> =
        flow.map { list -> list.filter { !it.archived } }

    override fun observeAll(): Flow<List<AccountEntity>> = flow

    override suspend fun getAll(): List<AccountEntity> = sortedItems()

    override suspend fun getById(id: Long): AccountEntity? = items.firstOrNull { it.id == id }

    override suspend fun getByName(name: String): AccountEntity? = items.firstOrNull { it.name == name }

    override suspend fun insert(account: AccountEntity): Long {
        val assignedId = if (account.id > 0L) account.id else nextId++
        if (assignedId >= nextId) nextId = assignedId + 1
        items.removeAll { it.id == assignedId }
        items.add(account.copy(id = assignedId))
        emit()
        return assignedId
    }

    override suspend fun insertAll(accounts: List<AccountEntity>) {
        for (account in accounts) {
            insert(account)
        }
    }

    override suspend fun update(account: AccountEntity) {
        val index = items.indexOfFirst { it.id == account.id }
        if (index >= 0) {
            items[index] = account
            emit()
        }
    }

    override suspend fun adjustBalance(accountId: Long, deltaMicros: Long, updatedAt: Long) {
        val index = items.indexOfFirst { it.id == accountId }
        if (index >= 0) {
            val current = items[index]
            items[index] = current.copy(
                balanceMicros = current.balanceMicros + deltaMicros,
                updatedAt = updatedAt
            )
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

class FakeTransactionDao : TransactionDao {
    private val items = mutableListOf<TransactionEntity>()
    private val flow = MutableStateFlow<List<TransactionEntity>>(emptyList())
    private var nextId = 1L

    private fun sortedItems(): List<TransactionEntity> =
        items.sortedWith(compareByDescending<TransactionEntity> { it.timestamp }.thenByDescending { it.id })

    private fun emit() {
        flow.value = sortedItems()
    }

    override fun observeAll(): Flow<List<TransactionEntity>> = flow

    override fun observeBetween(startMillis: Long, endMillis: Long): Flow<List<TransactionEntity>> =
        flow.map { list -> list.filter { it.timestamp in startMillis..endMillis } }

    override fun observePending(): Flow<List<TransactionEntity>> =
        flow.map { list -> list.filter { it.status == TransactionStatus.PENDING } }

    override fun observePendingCount(): Flow<Int> =
        flow.map { list -> list.count { it.status == TransactionStatus.PENDING } }

    override suspend fun getById(id: Long): TransactionEntity? = items.firstOrNull { it.id == id }

    override suspend fun getAll(): List<TransactionEntity> = sortedItems()

    override suspend fun getBetween(startMillis: Long, endMillis: Long): List<TransactionEntity> =
        sortedItems().filter { it.timestamp in startMillis..endMillis }

    override suspend fun insert(tx: TransactionEntity): Long {
        val assignedId = if (tx.id > 0L) tx.id else nextId++
        if (assignedId >= nextId) nextId = assignedId + 1
        items.removeAll { it.id == assignedId }
        items.add(tx.copy(id = assignedId))
        emit()
        return assignedId
    }

    override suspend fun insertAll(txs: List<TransactionEntity>) {
        for (tx in txs) {
            insert(tx)
        }
    }

    override suspend fun update(tx: TransactionEntity) {
        val index = items.indexOfFirst { it.id == tx.id }
        if (index >= 0) {
            items[index] = tx
            emit()
        }
    }

    override suspend fun confirmById(id: Long, updatedAt: Long) {
        val index = items.indexOfFirst { it.id == id }
        if (index >= 0) {
            items[index] = items[index].copy(status = TransactionStatus.CONFIRMED, updatedAt = updatedAt)
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

class FakeCategoryDao : CategoryDao {
    private val items = mutableListOf<CategoryEntity>()
    private val flow = MutableStateFlow<List<CategoryEntity>>(emptyList())
    private var nextId = 1L

    private fun sortedItems(): List<CategoryEntity> =
        items.sortedWith(compareBy<CategoryEntity> { it.sortOrder }.thenBy { it.id })

    private fun emit() {
        flow.value = sortedItems()
    }

    override fun observeAll(): Flow<List<CategoryEntity>> = flow

    override suspend fun getAll(): List<CategoryEntity> = sortedItems()

    override suspend fun getById(id: Long): CategoryEntity? = items.firstOrNull { it.id == id }

    override suspend fun findByNameAndType(name: String, type: String): CategoryEntity? =
        items.firstOrNull { it.name == name && it.type == type }

    override suspend fun insert(category: CategoryEntity): Long {
        val assignedId = if (category.id > 0L) category.id else nextId++
        if (assignedId >= nextId) nextId = assignedId + 1
        items.removeAll { it.id == assignedId }
        items.add(category.copy(id = assignedId))
        emit()
        return assignedId
    }

    override suspend fun insertAll(categories: List<CategoryEntity>) {
        for (category in categories) {
            insert(category)
        }
    }

    override suspend fun update(category: CategoryEntity) {
        val index = items.indexOfFirst { it.id == category.id }
        if (index >= 0) {
            items[index] = category
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

class FakeBudgetDao : BudgetDao {
    private val items = mutableListOf<BudgetEntity>()
    private val flow = MutableStateFlow<List<BudgetEntity>>(emptyList())
    private var nextId = 1L

    private fun emit() {
        flow.value = items.sortedBy { it.id }
    }

    override fun observeAll(): Flow<List<BudgetEntity>> = flow

    override suspend fun getAll(): List<BudgetEntity> = items.sortedBy { it.id }

    override suspend fun insert(budget: BudgetEntity): Long {
        val assignedId = if (budget.id > 0L) budget.id else nextId++
        if (assignedId >= nextId) nextId = assignedId + 1
        items.removeAll { it.id == assignedId }
        items.add(budget.copy(id = assignedId))
        emit()
        return assignedId
    }

    override suspend fun insertAll(budgets: List<BudgetEntity>) {
        for (budget in budgets) {
            insert(budget)
        }
    }

    override suspend fun update(budget: BudgetEntity) {
        val index = items.indexOfFirst { it.id == budget.id }
        if (index >= 0) {
            items[index] = budget
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

class FakeRecurringRuleDao : RecurringRuleDao {
    private val items = mutableListOf<RecurringRuleEntity>()
    private val flow = MutableStateFlow<List<RecurringRuleEntity>>(emptyList())
    private var nextId = 1L

    private fun emit() {
        flow.value = items.sortedBy { it.nextRunEpochMillis }
    }

    override fun observeAll(): Flow<List<RecurringRuleEntity>> = flow

    override suspend fun getDueRules(nowMillis: Long): List<RecurringRuleEntity> =
        items.filter { it.enabled && it.nextRunEpochMillis <= nowMillis }

    override suspend fun getAll(): List<RecurringRuleEntity> = items.sortedBy { it.id }

    override suspend fun insert(rule: RecurringRuleEntity): Long {
        val assignedId = if (rule.id > 0L) rule.id else nextId++
        if (assignedId >= nextId) nextId = assignedId + 1
        items.removeAll { it.id == assignedId }
        items.add(rule.copy(id = assignedId))
        emit()
        return assignedId
    }

    override suspend fun insertAll(rules: List<RecurringRuleEntity>) {
        for (rule in rules) {
            insert(rule)
        }
    }

    override suspend fun update(rule: RecurringRuleEntity) {
        val index = items.indexOfFirst { it.id == rule.id }
        if (index >= 0) {
            items[index] = rule
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

class FakeLedgerDao : com.kairoledger.app.data.db.LedgerDao {
    private val items = mutableListOf<com.kairoledger.app.data.db.LedgerEntity>()
    private val flow = MutableStateFlow<List<com.kairoledger.app.data.db.LedgerEntity>>(emptyList())
    private var nextId = 1L
    private fun emit() { flow.value = items.sortedWith(compareBy({ it.sortOrder }, { it.id })) }

    override fun observeActive(): Flow<List<com.kairoledger.app.data.db.LedgerEntity>> =
        flow.map { list -> list.filter { !it.archived } }
    override fun observeAll(): Flow<List<com.kairoledger.app.data.db.LedgerEntity>> = flow
    override suspend fun getAll(): List<com.kairoledger.app.data.db.LedgerEntity> = flow.value
    override suspend fun getById(id: Long): com.kairoledger.app.data.db.LedgerEntity? = items.firstOrNull { it.id == id }
    override suspend fun insert(ledger: com.kairoledger.app.data.db.LedgerEntity): Long {
        val assigned = if (ledger.id > 0L) ledger.id else nextId++
        if (assigned >= nextId) nextId = assigned + 1
        items.removeAll { it.id == assigned }
        items.add(ledger.copy(id = assigned))
        emit()
        return assigned
    }
    override suspend fun insertAll(ledgers: List<com.kairoledger.app.data.db.LedgerEntity>) { ledgers.forEach { insert(it) } }
    override suspend fun update(ledger: com.kairoledger.app.data.db.LedgerEntity) {
        val idx = items.indexOfFirst { it.id == ledger.id }
        if (idx >= 0) { items[idx] = ledger; emit() }
    }
    override suspend fun deleteById(id: Long) { items.removeAll { it.id == id }; emit() }
    override suspend fun clearAll() { items.clear(); emit() }
}

class FakeDebtRecordDao : com.kairoledger.app.data.db.DebtRecordDao {
    private val items = mutableListOf<com.kairoledger.app.data.db.DebtRecordEntity>()
    private val flow = MutableStateFlow<List<com.kairoledger.app.data.db.DebtRecordEntity>>(emptyList())
    private var nextId = 1L
    private fun emit() { flow.value = items.sortedByDescending { it.updatedAt } }

    override fun observeAll(): Flow<List<com.kairoledger.app.data.db.DebtRecordEntity>> = flow
    override suspend fun getAll(): List<com.kairoledger.app.data.db.DebtRecordEntity> = flow.value
    override suspend fun getById(id: Long): com.kairoledger.app.data.db.DebtRecordEntity? = items.firstOrNull { it.id == id }
    override suspend fun insert(debt: com.kairoledger.app.data.db.DebtRecordEntity): Long {
        val assigned = if (debt.id > 0L) debt.id else nextId++
        if (assigned >= nextId) nextId = assigned + 1
        items.removeAll { it.id == assigned }
        items.add(debt.copy(id = assigned))
        emit()
        return assigned
    }
    override suspend fun insertAll(debts: List<com.kairoledger.app.data.db.DebtRecordEntity>) { debts.forEach { insert(it) } }
    override suspend fun update(debt: com.kairoledger.app.data.db.DebtRecordEntity) {
        val idx = items.indexOfFirst { it.id == debt.id }
        if (idx >= 0) { items[idx] = debt; emit() }
    }
    override suspend fun deleteById(id: Long) { items.removeAll { it.id == id }; emit() }
    override suspend fun clearAll() { items.clear(); emit() }
}

class FakeInstallmentPlanDao : com.kairoledger.app.data.db.InstallmentPlanDao {
    private val items = mutableListOf<com.kairoledger.app.data.db.InstallmentPlanEntity>()
    private val flow = MutableStateFlow<List<com.kairoledger.app.data.db.InstallmentPlanEntity>>(emptyList())
    private var nextId = 1L
    private fun emit() { flow.value = items.sortedBy { it.nextDueMillis } }

    override fun observeAll(): Flow<List<com.kairoledger.app.data.db.InstallmentPlanEntity>> = flow
    override suspend fun getAll(): List<com.kairoledger.app.data.db.InstallmentPlanEntity> = flow.value
    override suspend fun getById(id: Long): com.kairoledger.app.data.db.InstallmentPlanEntity? = items.firstOrNull { it.id == id }
    override suspend fun insert(plan: com.kairoledger.app.data.db.InstallmentPlanEntity): Long {
        val assigned = if (plan.id > 0L) plan.id else nextId++
        if (assigned >= nextId) nextId = assigned + 1
        items.removeAll { it.id == assigned }
        items.add(plan.copy(id = assigned))
        emit()
        return assigned
    }
    override suspend fun insertAll(plans: List<com.kairoledger.app.data.db.InstallmentPlanEntity>) { plans.forEach { insert(it) } }
    override suspend fun update(plan: com.kairoledger.app.data.db.InstallmentPlanEntity) {
        val idx = items.indexOfFirst { it.id == plan.id }
        if (idx >= 0) { items[idx] = plan; emit() }
    }
    override suspend fun deleteById(id: Long) { items.removeAll { it.id == id }; emit() }
    override suspend fun clearAll() { items.clear(); emit() }
}
