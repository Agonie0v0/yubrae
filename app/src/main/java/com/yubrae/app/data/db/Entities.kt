package com.yubrae.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

object AccountGroup {
    const val WALLET = "WALLET"
    const val DEBIT = "DEBIT"
    const val CREDIT = "CREDIT"
    const val FOREIGN = "FOREIGN"
    const val INVEST = "INVEST"
    const val DEBT = "DEBT"

    val all = listOf(WALLET, DEBIT, CREDIT, FOREIGN, INVEST, DEBT)

    fun labelZh(group: String): String = when (group) {
        WALLET -> "电子钱包与现金"
        DEBIT -> "储蓄卡"
        CREDIT -> "信用卡"
        FOREIGN -> "外币账户"
        INVEST -> "理财投资"
        DEBT -> "借贷与报销"
        else -> "其他账户"
    }
}

object TransactionType {
    const val EXPENSE = "EXPENSE"
    const val INCOME = "INCOME"
    const val TRANSFER = "TRANSFER"
    const val ADJUST = "ADJUST"
}

object TransactionStatus {
    const val CONFIRMED = "CONFIRMED"
    const val PENDING = "PENDING"
}

object ReimburseStatus {
    const val NONE = "NONE"
    const val PENDING = "PENDING"
    const val REIMBURSED = "REIMBURSED"
}

object DebtDirection {
    const val LEND = "LEND"
    const val BORROW = "BORROW"
}

object DebtStatus {
    const val OPEN = "OPEN"
    const val SETTLED = "SETTLED"
}

object StatsGranularity {
    const val WEEKLY = "WEEKLY"
    const val MONTHLY = "MONTHLY"
    const val YEARLY = "YEARLY"
    const val CUSTOM = "CUSTOM"
}

@Entity(tableName = "ledgers")
data class LedgerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    @ColumnInfo(name = "currency_code") val currencyCode: String = "CNY",
    val icon: String = "menu_book",
    @ColumnInfo(name = "color_hex") val colorHex: Long = 0xFF2AA876L,
    @ColumnInfo(name = "sort_order") val sortOrder: Int = 0,
    val archived: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "debt_records",
    indices = [
        Index("direction"),
        Index("status"),
        Index("person_name")
    ]
)
data class DebtRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val direction: String,
    @ColumnInfo(name = "person_name") val personName: String,
    @ColumnInfo(name = "amount_micros") val amountMicros: Long,
    @ColumnInfo(name = "settled_amount_micros") val settledAmountMicros: Long = 0L,
    @ColumnInfo(name = "currency_code") val currencyCode: String = "CNY",
    @ColumnInfo(name = "account_id") val accountId: Long? = null,
    @ColumnInfo(name = "due_date_millis") val dueDateMillis: Long? = null,
    val note: String = "",
    val status: String = DebtStatus.OPEN,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "installment_plans",
    indices = [Index("account_id")]
)
data class InstallmentPlanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "account_id") val accountId: Long,
    val name: String,
    @ColumnInfo(name = "total_amount_micros") val totalAmountMicros: Long,
    @ColumnInfo(name = "fee_per_period_micros") val feePerPeriodMicros: Long = 0L,
    @ColumnInfo(name = "currency_code") val currencyCode: String = "CNY",
    @ColumnInfo(name = "total_periods") val totalPeriods: Int,
    @ColumnInfo(name = "completed_periods") val completedPeriods: Int = 0,
    @ColumnInfo(name = "billing_day") val billingDay: Int = 5,
    @ColumnInfo(name = "next_due_millis") val nextDueMillis: Long,
    @ColumnInfo(name = "category_id") val categoryId: Long? = null,
    val enabled: Boolean = true,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)

object CaptureChannel {
    const val MANUAL = "MANUAL"
    const val NOTIFICATION = "NOTIFICATION"
    const val SMS = "SMS"
    const val SCREEN = "SCREEN"
    const val AUTO_ACCOUNTING = "AUTO_ACCOUNTING"
    const val SHARE = "SHARE"
    const val AI = "AI"
    const val IMPORT = "IMPORT"
    const val RECURRING = "RECURRING"
}

object ParseStatus {
    const val MATCHED_LOCAL = "MATCHED_LOCAL"
    const val MATCHED_AI = "MATCHED_AI"
    const val MERGED_DEDUP = "MERGED_DEDUP"
    const val IGNORED = "IGNORED"
    const val FAILED = "FAILED"
}

object AliasMatchType {
    const val PACKAGE = "PACKAGE"
    const val CARD_LAST4 = "CARD_LAST4"
    const val KEYWORD = "KEYWORD"
    const val REGEX = "REGEX"
}

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    @ColumnInfo(name = "group_type") val groupType: String,
    @ColumnInfo(name = "currency_code") val currencyCode: String,
    @ColumnInfo(name = "balance_micros") val balanceMicros: Long = 0L,
    @ColumnInfo(name = "credit_limit_micros") val creditLimitMicros: Long = 0L,
    @ColumnInfo(name = "billing_day") val billingDay: Int? = null,
    @ColumnInfo(name = "repayment_day") val repaymentDay: Int? = null,
    val icon: String = "wallet",
    @ColumnInfo(name = "color_hex") val colorHex: Long = 0xFF0B57D0L,
    @ColumnInfo(name = "include_in_net_worth") val includeInNetWorth: Boolean = true,
    @ColumnInfo(name = "sort_order") val sortOrder: Int = 0,
    val archived: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "transactions",
    indices = [
        Index("timestamp"),
        Index("account_id"),
        Index("to_account_id"),
        Index("category_id"),
        Index("status"),
        Index("ledger_id"),
        Index("refund_of_tx_id")
    ]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val type: String,
    val timestamp: Long,
    @ColumnInfo(name = "orig_amount_micros") val origAmountMicros: Long,
    @ColumnInfo(name = "orig_currency") val origCurrency: String,
    @ColumnInfo(name = "account_id") val accountId: Long,
    @ColumnInfo(name = "account_amount_micros") val accountAmountMicros: Long,
    @ColumnInfo(name = "account_currency") val accountCurrency: String,
    @ColumnInfo(name = "to_account_id") val toAccountId: Long? = null,
    @ColumnInfo(name = "to_amount_micros") val toAmountMicros: Long? = null,
    @ColumnInfo(name = "to_currency") val toCurrency: String? = null,
    /** 原币种 -> 账户币种 成交汇率（百万分位） */
    @ColumnInfo(name = "rate_to_account_micros") val rateToAccountMicros: Long = 1_000_000L,
    /** 原币种 -> 主币种 历史冻结汇率快照（百万分位，防报表漂移） */
    @ColumnInfo(name = "rate_to_base_micros") val rateToBaseMicros: Long = 1_000_000L,
    @ColumnInfo(name = "base_currency") val baseCurrency: String = "CNY",
    @ColumnInfo(name = "category_id") val categoryId: Long? = null,
    val merchant: String = "",
    val note: String = "",
    val tags: String = "",
    @ColumnInfo(name = "reimburse_status") val reimburseStatus: String = ReimburseStatus.NONE,
    @ColumnInfo(name = "source_type") val sourceType: String = CaptureChannel.MANUAL,
    @ColumnInfo(name = "raw_event_id") val rawEventId: Long? = null,
    val status: String = TransactionStatus.CONFIRMED,
    @ColumnInfo(name = "ledger_id") val ledgerId: Long = 1L,
    @ColumnInfo(name = "exclude_from_stats") val excludeFromStats: Boolean = false,
    @ColumnInfo(name = "exclude_from_budget") val excludeFromBudget: Boolean = false,
    @ColumnInfo(name = "refund_of_tx_id") val refundOfTxId: Long? = null,
    @ColumnInfo(name = "attachment_paths") val attachmentPaths: String = "",
    @ColumnInfo(name = "reimbursed_tx_id") val reimbursedTxId: Long? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "parent_id") val parentId: Long? = null,
    val name: String,
    val type: String,
    val icon: String = "category",
    @ColumnInfo(name = "color_hex") val colorHex: Long = 0xFF0B57D0L,
    @ColumnInfo(name = "sort_order") val sortOrder: Int = 0,
    @ColumnInfo(name = "is_system") val isSystem: Boolean = true
)

@Entity(
    tableName = "exchange_rates",
    indices = [
        Index(value = ["base_currency", "target_currency", "date"], unique = true)
    ]
)
data class ExchangeRateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "base_currency") val baseCurrency: String,
    @ColumnInfo(name = "target_currency") val targetCurrency: String,
    @ColumnInfo(name = "rate_micros") val rateMicros: Long,
    val date: String,
    val source: String,
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "account_aliases",
    indices = [Index("account_id")]
)
data class AccountAliasEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "account_id") val accountId: Long,
    @ColumnInfo(name = "match_type") val matchType: String,
    val pattern: String,
    val priority: Int = 50,
    val enabled: Boolean = true
)

@Entity(tableName = "parse_rules")
data class ParseRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    @ColumnInfo(name = "source_package") val sourcePackage: String = "*",
    @ColumnInfo(name = "channel_type") val channelType: String = "ANY",
    @ColumnInfo(name = "regex_pattern") val regexPattern: String,
    @ColumnInfo(name = "transaction_type") val transactionType: String = TransactionType.EXPENSE,
    @ColumnInfo(name = "amount_group") val amountGroup: Int = 1,
    @ColumnInfo(name = "currency_group") val currencyGroup: Int = 0,
    @ColumnInfo(name = "default_currency") val defaultCurrency: String = "CNY",
    @ColumnInfo(name = "account_hint_group") val accountHintGroup: Int = 0,
    @ColumnInfo(name = "merchant_group") val merchantGroup: Int = 0,
    @ColumnInfo(name = "default_category_name") val defaultCategoryName: String = "其他支出",
    val priority: Int = 50,
    val enabled: Boolean = true,
    @ColumnInfo(name = "is_built_in") val isBuiltIn: Boolean = false
)

@Entity(
    tableName = "raw_capture_logs",
    indices = [Index("captured_at")]
)
data class RawCaptureLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val channel: String,
    @ColumnInfo(name = "source_package") val sourcePackage: String,
    val title: String,
    val content: String,
    @ColumnInfo(name = "captured_at") val capturedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "parse_status") val parseStatus: String,
    @ColumnInfo(name = "parser_used") val parserUsed: String = "",
    @ColumnInfo(name = "extracted_json") val extractedJson: String = "",
    @ColumnInfo(name = "linked_transaction_id") val linkedTransactionId: Long? = null,
    @ColumnInfo(name = "error_message") val errorMessage: String = ""
)

@Entity(tableName = "budgets")
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    @ColumnInfo(name = "category_id") val categoryId: Long? = null,
    val period: String = "MONTHLY",
    @ColumnInfo(name = "limit_amount_micros") val limitAmountMicros: Long,
    @ColumnInfo(name = "currency_code") val currencyCode: String = "CNY",
    val enabled: Boolean = true,
    @ColumnInfo(name = "ledger_id") val ledgerId: Long = 1L,
    @ColumnInfo(name = "rollover_enabled") val rolloverEnabled: Boolean = false
)

@Entity(tableName = "recurring_rules")
data class RecurringRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val type: String = TransactionType.EXPENSE,
    @ColumnInfo(name = "amount_micros") val amountMicros: Long,
    @ColumnInfo(name = "currency_code") val currencyCode: String,
    @ColumnInfo(name = "account_id") val accountId: Long,
    @ColumnInfo(name = "to_account_id") val toAccountId: Long? = null,
    @ColumnInfo(name = "category_id") val categoryId: Long? = null,
    val merchant: String = "",
    val note: String = "",
    @ColumnInfo(name = "cycle_type") val cycleType: String = "MONTHLY",
    @ColumnInfo(name = "day_of_cycle") val dayOfCycle: Int = 1,
    @ColumnInfo(name = "next_run_epoch_millis") val nextRunEpochMillis: Long,
    val enabled: Boolean = true
)

@Entity(
    tableName = "tags",
    indices = [Index(value = ["name"], unique = true)]
)
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    @ColumnInfo(name = "group_name") val groupName: String = "常用",
    @ColumnInfo(name = "color_hex") val colorHex: Long = 0xFF2AA876L,
    @ColumnInfo(name = "sort_order") val sortOrder: Int = 0,
    @ColumnInfo(name = "is_system") val isSystem: Boolean = true,
    val icon: String = ""
)

object TagTextCodec {
    private val SEPARATORS = charArrayOf(',', '，', ';', '；', '#')

    fun parse(raw: String): List<String> {
        if (raw.isBlank()) return emptyList()
        return raw.split(*SEPARATORS)
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .toList()
    }

    fun format(tags: Iterable<String>): String =
        tags.asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .joinToString(",")

    fun merge(existing: String, incoming: String): String =
        format(parse(existing) + parse(incoming))
}
