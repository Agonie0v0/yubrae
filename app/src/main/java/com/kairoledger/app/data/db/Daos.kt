package com.kairoledger.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts WHERE archived = 0 ORDER BY sort_order ASC, id ASC")
    fun observeActive(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts ORDER BY sort_order ASC, id ASC")
    fun observeAll(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts ORDER BY sort_order ASC, id ASC")
    suspend fun getAll(): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): AccountEntity?

    @Query("SELECT * FROM accounts WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): AccountEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(account: AccountEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(accounts: List<AccountEntity>)

    @Update
    suspend fun update(account: AccountEntity)

    @Query("UPDATE accounts SET balance_micros = balance_micros + :deltaMicros, updated_at = :updatedAt WHERE id = :accountId")
    suspend fun adjustBalance(accountId: Long, deltaMicros: Long, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM accounts WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM accounts")
    suspend fun clearAll()
}

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY timestamp DESC, id DESC")
    fun observeAll(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE timestamp BETWEEN :startMillis AND :endMillis ORDER BY timestamp DESC, id DESC")
    fun observeBetween(startMillis: Long, endMillis: Long): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE status = 'PENDING' ORDER BY timestamp DESC, id DESC")
    fun observePending(): Flow<List<TransactionEntity>>

    @Query("SELECT COUNT(*) FROM transactions WHERE status = 'PENDING'")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT * FROM transactions WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): TransactionEntity?

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC, id DESC")
    suspend fun getAll(): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE timestamp BETWEEN :startMillis AND :endMillis ORDER BY timestamp DESC")
    suspend fun getBetween(startMillis: Long, endMillis: Long): List<TransactionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(tx: TransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(txs: List<TransactionEntity>)

    @Update
    suspend fun update(tx: TransactionEntity)

    @Query("UPDATE transactions SET status = 'CONFIRMED', updated_at = :updatedAt WHERE id = :id")
    suspend fun confirmById(id: Long, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE transactions SET status = 'CONFIRMED', updated_at = :updatedAt WHERE status = 'PENDING'")
    suspend fun confirmAllPending(updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE transactions SET category_id = :categoryId, updated_at = :updatedAt WHERE id IN (:ids)")
    suspend fun updateCategoryForIds(ids: List<Long>, categoryId: Long?, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE transactions SET reimburse_status = 'REIMBURSED', reimbursed_tx_id = :reimbursedTxId, updated_at = :updatedAt WHERE id IN (:ids)")
    suspend fun markReimbursed(ids: List<Long>, reimbursedTxId: Long, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM transactions")
    suspend fun clearAll()
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY sort_order ASC, id ASC")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY sort_order ASC, id ASC")
    suspend fun getAll(): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): CategoryEntity?

    @Query("SELECT * FROM categories WHERE name = :name AND type = :type LIMIT 1")
    suspend fun findByNameAndType(name: String, type: String): CategoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(category: CategoryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(categories: List<CategoryEntity>)

    @Update
    suspend fun update(category: CategoryEntity)

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM categories")
    suspend fun clearAll()
}

@Dao
interface ExchangeRateDao {
    @Query("SELECT * FROM exchange_rates ORDER BY updated_at DESC")
    fun observeAll(): Flow<List<ExchangeRateEntity>>

    @Query("SELECT * FROM exchange_rates ORDER BY updated_at DESC")
    suspend fun getAll(): List<ExchangeRateEntity>

    @Query(
        "SELECT * FROM exchange_rates WHERE base_currency = :base AND target_currency = :target " +
            "ORDER BY date DESC, updated_at DESC LIMIT 1"
    )
    suspend fun getLatest(base: String, target: String): ExchangeRateEntity?

    @Query(
        "SELECT * FROM exchange_rates WHERE base_currency = :base AND target_currency = :target AND date <= :date " +
            "ORDER BY date DESC, updated_at DESC LIMIT 1"
    )
    suspend fun getClosestOnOrBefore(base: String, target: String, date: String): ExchangeRateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rates: List<ExchangeRateEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rate: ExchangeRateEntity): Long

    @Query("DELETE FROM exchange_rates")
    suspend fun clearAll()
}

@Dao
interface AccountAliasDao {
    @Query("SELECT * FROM account_aliases ORDER BY priority DESC, id ASC")
    fun observeAll(): Flow<List<AccountAliasEntity>>

    @Query("SELECT * FROM account_aliases WHERE enabled = 1 ORDER BY priority DESC, id ASC")
    suspend fun getEnabled(): List<AccountAliasEntity>

    @Query("SELECT * FROM account_aliases ORDER BY priority DESC, id ASC")
    suspend fun getAll(): List<AccountAliasEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(alias: AccountAliasEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(aliases: List<AccountAliasEntity>)

    @Update
    suspend fun update(alias: AccountAliasEntity)

    @Query("DELETE FROM account_aliases WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM account_aliases")
    suspend fun clearAll()
}

@Dao
interface ParseRuleDao {
    @Query("SELECT * FROM parse_rules ORDER BY priority DESC, id ASC")
    fun observeAll(): Flow<List<ParseRuleEntity>>

    @Query("SELECT * FROM parse_rules WHERE enabled = 1 ORDER BY priority DESC, id ASC")
    suspend fun getEnabled(): List<ParseRuleEntity>

    @Query("SELECT * FROM parse_rules ORDER BY priority DESC, id ASC")
    suspend fun getAll(): List<ParseRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rule: ParseRuleEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rules: List<ParseRuleEntity>)

    @Update
    suspend fun update(rule: ParseRuleEntity)

    @Query("DELETE FROM parse_rules WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM parse_rules")
    suspend fun clearAll()
}

@Dao
interface RawCaptureLogDao {
    @Query("SELECT * FROM raw_capture_logs ORDER BY captured_at DESC, id DESC LIMIT :limit")
    fun observeRecent(limit: Int = 200): Flow<List<RawCaptureLogEntity>>

    @Query("SELECT * FROM raw_capture_logs ORDER BY captured_at DESC, id DESC")
    suspend fun getAll(): List<RawCaptureLogEntity>

    @Query("SELECT * FROM raw_capture_logs WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): RawCaptureLogEntity?

    @Query("SELECT * FROM raw_capture_logs WHERE captured_at >= :sinceMillis ORDER BY captured_at DESC")
    suspend fun getSince(sinceMillis: Long): List<RawCaptureLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: RawCaptureLogEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(logs: List<RawCaptureLogEntity>)

    @Update
    suspend fun update(log: RawCaptureLogEntity)

    @Query("DELETE FROM raw_capture_logs WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM raw_capture_logs")
    suspend fun clearAll()
}

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets ORDER BY id ASC")
    fun observeAll(): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets ORDER BY id ASC")
    suspend fun getAll(): List<BudgetEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(budget: BudgetEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(budgets: List<BudgetEntity>)

    @Update
    suspend fun update(budget: BudgetEntity)

    @Query("DELETE FROM budgets WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM budgets")
    suspend fun clearAll()
}

@Dao
interface RecurringRuleDao {
    @Query("SELECT * FROM recurring_rules ORDER BY next_run_epoch_millis ASC")
    fun observeAll(): Flow<List<RecurringRuleEntity>>

    @Query("SELECT * FROM recurring_rules WHERE enabled = 1 AND next_run_epoch_millis <= :nowMillis")
    suspend fun getDueRules(nowMillis: Long): List<RecurringRuleEntity>

    @Query("SELECT * FROM recurring_rules ORDER BY id ASC")
    suspend fun getAll(): List<RecurringRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rule: RecurringRuleEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rules: List<RecurringRuleEntity>)

    @Update
    suspend fun update(rule: RecurringRuleEntity)

    @Query("DELETE FROM recurring_rules WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM recurring_rules")
    suspend fun clearAll()
}

@Dao
interface LedgerDao {
    @Query("SELECT * FROM ledgers WHERE archived = 0 ORDER BY sort_order ASC, id ASC")
    fun observeActive(): Flow<List<LedgerEntity>>

    @Query("SELECT * FROM ledgers ORDER BY sort_order ASC, id ASC")
    fun observeAll(): Flow<List<LedgerEntity>>

    @Query("SELECT * FROM ledgers ORDER BY sort_order ASC, id ASC")
    suspend fun getAll(): List<LedgerEntity>

    @Query("SELECT * FROM ledgers WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): LedgerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(ledger: LedgerEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(ledgers: List<LedgerEntity>)

    @Update
    suspend fun update(ledger: LedgerEntity)

    @Query("DELETE FROM ledgers WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM ledgers")
    suspend fun clearAll()
}

@Dao
interface DebtRecordDao {
    @Query("SELECT * FROM debt_records ORDER BY status ASC, updated_at DESC, id DESC")
    fun observeAll(): Flow<List<DebtRecordEntity>>

    @Query("SELECT * FROM debt_records ORDER BY status ASC, updated_at DESC, id DESC")
    suspend fun getAll(): List<DebtRecordEntity>

    @Query("SELECT * FROM debt_records WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): DebtRecordEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(debt: DebtRecordEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(debts: List<DebtRecordEntity>)

    @Update
    suspend fun update(debt: DebtRecordEntity)

    @Query("DELETE FROM debt_records WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM debt_records")
    suspend fun clearAll()
}

@Dao
interface InstallmentPlanDao {
    @Query("SELECT * FROM installment_plans ORDER BY next_due_millis ASC, id DESC")
    fun observeAll(): Flow<List<InstallmentPlanEntity>>

    @Query("SELECT * FROM installment_plans ORDER BY next_due_millis ASC, id DESC")
    suspend fun getAll(): List<InstallmentPlanEntity>

    @Query("SELECT * FROM installment_plans WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): InstallmentPlanEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(plan: InstallmentPlanEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(plans: List<InstallmentPlanEntity>)

    @Update
    suspend fun update(plan: InstallmentPlanEntity)

    @Query("DELETE FROM installment_plans WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM installment_plans")
    suspend fun clearAll()
}
