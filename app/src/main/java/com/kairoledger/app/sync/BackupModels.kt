package com.kairoledger.app.sync

import com.kairoledger.app.core.model.CurrencyCatalog
import com.kairoledger.app.data.db.AccountAliasEntity
import com.kairoledger.app.data.db.AccountEntity
import com.kairoledger.app.data.db.BudgetEntity
import com.kairoledger.app.data.db.CaptureChannel
import com.kairoledger.app.data.db.CategoryEntity
import com.kairoledger.app.data.db.ExchangeRateEntity
import com.kairoledger.app.data.db.ParseRuleEntity
import com.kairoledger.app.data.db.RawCaptureLogEntity
import com.kairoledger.app.data.db.RecurringRuleEntity
import com.kairoledger.app.data.db.ReimburseStatus
import com.kairoledger.app.data.db.TransactionEntity
import com.kairoledger.app.data.db.TransactionStatus
import com.kairoledger.app.data.db.TransactionType
import com.kairoledger.app.data.prefs.AppSettings
import com.kairoledger.app.data.prefs.ThemeMode
import kotlinx.serialization.Serializable

/**
 * 完整备份快照 DTO，覆盖全部 9 张 Room 数据表及关键 AppSettings。
 */
@Serializable
data class KairoBackupSnapshot(
    val formatVersion: Int = CURRENT_FORMAT_VERSION,
    val exportedAtMillis: Long = System.currentTimeMillis(),
    val appName: String = APP_NAME,
    val settings: SettingsSnapshotDto = SettingsSnapshotDto(),
    val accounts: List<AccountDto> = emptyList(),
    val categories: List<CategoryDto> = emptyList(),
    val transactions: List<TransactionDto> = emptyList(),
    val exchangeRates: List<ExchangeRateDto> = emptyList(),
    val accountAliases: List<AccountAliasDto> = emptyList(),
    val parseRules: List<ParseRuleDto> = emptyList(),
    val rawCaptureLogs: List<RawCaptureLogDto> = emptyList(),
    val budgets: List<BudgetDto> = emptyList(),
    val recurringRules: List<RecurringRuleDto> = emptyList()
) {
    companion object {
        const val CURRENT_FORMAT_VERSION = 1
        const val APP_NAME = "KairoLedger"
    }
}

@Serializable
data class SettingsSnapshotDto(
    val baseCurrency: String = CurrencyCatalog.DEFAULT_BASE_CURRENCY,
    val pinnedCurrencies: List<String> = CurrencyCatalog.PINNED_CURRENCIES,
    val themeMode: String = ThemeMode.SYSTEM.name,
    val dynamicColor: Boolean = true,
    val notificationCaptureEnabled: Boolean = true,
    val smsCaptureEnabled: Boolean = true,
    val autoAccountingBridgeEnabled: Boolean = true,
    val floatingConfirmEnabled: Boolean = true,
    val floatingAutoConfirmSeconds: Int = 8,
    val defaultPendingWhenUncertain: Boolean = false,
    val llmEnabled: Boolean = false,
    val llmBaseUrl: String = "https://api.openai.com/v1",
    val llmModel: String = "gpt-4o-mini",
    val webDavRemoteDir: String = "/KairoLedger",
    val autoBackupEnabled: Boolean = false
)

@Serializable
data class AccountDto(
    val id: Long = 0L,
    val name: String,
    val groupType: String,
    val currencyCode: String,
    val balanceMicros: Long = 0L,
    val creditLimitMicros: Long = 0L,
    val billingDay: Int? = null,
    val repaymentDay: Int? = null,
    val icon: String = "wallet",
    val colorHex: Long = 0xFF0B57D0L,
    val includeInNetWorth: Boolean = true,
    val sortOrder: Int = 0,
    val archived: Boolean = false,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

@Serializable
data class TransactionDto(
    val id: Long = 0L,
    val type: String,
    val timestamp: Long,
    val origAmountMicros: Long,
    val origCurrency: String,
    val accountId: Long,
    val accountAmountMicros: Long,
    val accountCurrency: String,
    val toAccountId: Long? = null,
    val toAmountMicros: Long? = null,
    val toCurrency: String? = null,
    val rateToAccountMicros: Long = 1_000_000L,
    val rateToBaseMicros: Long = 1_000_000L,
    val baseCurrency: String = "CNY",
    val categoryId: Long? = null,
    val merchant: String = "",
    val note: String = "",
    val tags: String = "",
    val reimburseStatus: String = ReimburseStatus.NONE,
    val sourceType: String = CaptureChannel.MANUAL,
    val rawEventId: Long? = null,
    val status: String = TransactionStatus.CONFIRMED,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

@Serializable
data class CategoryDto(
    val id: Long = 0L,
    val parentId: Long? = null,
    val name: String,
    val type: String,
    val icon: String = "category",
    val colorHex: Long = 0xFF0B57D0L,
    val sortOrder: Int = 0,
    val isSystem: Boolean = true
)

@Serializable
data class ExchangeRateDto(
    val id: Long = 0L,
    val baseCurrency: String,
    val targetCurrency: String,
    val rateMicros: Long,
    val date: String,
    val source: String,
    val updatedAt: Long = 0L
)

@Serializable
data class AccountAliasDto(
    val id: Long = 0L,
    val accountId: Long,
    val matchType: String,
    val pattern: String,
    val priority: Int = 50,
    val enabled: Boolean = true
)

@Serializable
data class ParseRuleDto(
    val id: Long = 0L,
    val name: String,
    val sourcePackage: String = "*",
    val channelType: String = "ANY",
    val regexPattern: String,
    val transactionType: String = TransactionType.EXPENSE,
    val amountGroup: Int = 1,
    val currencyGroup: Int = 0,
    val defaultCurrency: String = "CNY",
    val accountHintGroup: Int = 0,
    val merchantGroup: Int = 0,
    val defaultCategoryName: String = "其他支出",
    val priority: Int = 50,
    val enabled: Boolean = true,
    val isBuiltIn: Boolean = false
)

@Serializable
data class RawCaptureLogDto(
    val id: Long = 0L,
    val channel: String,
    val sourcePackage: String,
    val title: String,
    val content: String,
    val capturedAt: Long = 0L,
    val parseStatus: String,
    val parserUsed: String = "",
    val extractedJson: String = "",
    val linkedTransactionId: Long? = null,
    val errorMessage: String = ""
)

@Serializable
data class BudgetDto(
    val id: Long = 0L,
    val name: String,
    val categoryId: Long? = null,
    val period: String = "MONTHLY",
    val limitAmountMicros: Long,
    val currencyCode: String = "CNY",
    val enabled: Boolean = true
)

@Serializable
data class RecurringRuleDto(
    val id: Long = 0L,
    val name: String,
    val type: String = TransactionType.EXPENSE,
    val amountMicros: Long,
    val currencyCode: String,
    val accountId: Long,
    val toAccountId: Long? = null,
    val categoryId: Long? = null,
    val merchant: String = "",
    val note: String = "",
    val cycleType: String = "MONTHLY",
    val dayOfCycle: Int = 1,
    val nextRunEpochMillis: Long,
    val enabled: Boolean = true
)

internal fun AppSettings.toSnapshotDto(): SettingsSnapshotDto = SettingsSnapshotDto(
    baseCurrency = baseCurrency,
    pinnedCurrencies = pinnedCurrencies,
    themeMode = themeMode.name,
    dynamicColor = dynamicColor,
    notificationCaptureEnabled = notificationCaptureEnabled,
    smsCaptureEnabled = smsCaptureEnabled,
    autoAccountingBridgeEnabled = autoAccountingBridgeEnabled,
    floatingConfirmEnabled = floatingConfirmEnabled,
    floatingAutoConfirmSeconds = floatingAutoConfirmSeconds,
    defaultPendingWhenUncertain = defaultPendingWhenUncertain,
    llmEnabled = llmEnabled,
    llmBaseUrl = llmBaseUrl,
    llmModel = llmModel,
    webDavRemoteDir = webDavRemoteDir,
    autoBackupEnabled = autoBackupEnabled
)

internal fun SettingsSnapshotDto.applyOnto(current: AppSettings): AppSettings = current.copy(
    baseCurrency = baseCurrency.ifBlank { CurrencyCatalog.DEFAULT_BASE_CURRENCY },
    pinnedCurrencies = pinnedCurrencies.ifEmpty { CurrencyCatalog.PINNED_CURRENCIES },
    themeMode = runCatching { ThemeMode.valueOf(themeMode) }.getOrDefault(ThemeMode.SYSTEM),
    dynamicColor = dynamicColor,
    notificationCaptureEnabled = notificationCaptureEnabled,
    smsCaptureEnabled = smsCaptureEnabled,
    autoAccountingBridgeEnabled = autoAccountingBridgeEnabled,
    floatingConfirmEnabled = floatingConfirmEnabled,
    floatingAutoConfirmSeconds = floatingAutoConfirmSeconds,
    defaultPendingWhenUncertain = defaultPendingWhenUncertain,
    llmEnabled = llmEnabled,
    llmBaseUrl = llmBaseUrl.ifBlank { current.llmBaseUrl },
    llmModel = llmModel.ifBlank { current.llmModel },
    webDavRemoteDir = webDavRemoteDir.ifBlank { current.webDavRemoteDir },
    autoBackupEnabled = autoBackupEnabled
)

internal fun AccountEntity.toDto() = AccountDto(
    id, name, groupType, currencyCode, balanceMicros, creditLimitMicros,
    billingDay, repaymentDay, icon, colorHex, includeInNetWorth, sortOrder,
    archived, createdAt, updatedAt
)

internal fun AccountDto.toEntity() = AccountEntity(
    id, name, groupType, currencyCode, balanceMicros, creditLimitMicros,
    billingDay, repaymentDay, icon, colorHex, includeInNetWorth, sortOrder,
    archived, createdAt, updatedAt
)

internal fun TransactionEntity.toDto() = TransactionDto(
    id, type, timestamp, origAmountMicros, origCurrency, accountId,
    accountAmountMicros, accountCurrency, toAccountId, toAmountMicros, toCurrency,
    rateToAccountMicros, rateToBaseMicros, baseCurrency, categoryId, merchant,
    note, tags, reimburseStatus, sourceType, rawEventId, status, createdAt, updatedAt
)

internal fun TransactionDto.toEntity() = TransactionEntity(
    id, type, timestamp, origAmountMicros, origCurrency, accountId,
    accountAmountMicros, accountCurrency, toAccountId, toAmountMicros, toCurrency,
    rateToAccountMicros, rateToBaseMicros, baseCurrency, categoryId, merchant,
    note, tags, reimburseStatus, sourceType, rawEventId, status, createdAt, updatedAt
)

internal fun CategoryEntity.toDto() = CategoryDto(
    id, parentId, name, type, icon, colorHex, sortOrder, isSystem
)

internal fun CategoryDto.toEntity() = CategoryEntity(
    id, parentId, name, type, icon, colorHex, sortOrder, isSystem
)

internal fun ExchangeRateEntity.toDto() = ExchangeRateDto(
    id, baseCurrency, targetCurrency, rateMicros, date, source, updatedAt
)

internal fun ExchangeRateDto.toEntity() = ExchangeRateEntity(
    id, baseCurrency, targetCurrency, rateMicros, date, source, updatedAt
)

internal fun AccountAliasEntity.toDto() = AccountAliasDto(
    id, accountId, matchType, pattern, priority, enabled
)

internal fun AccountAliasDto.toEntity() = AccountAliasEntity(
    id, accountId, matchType, pattern, priority, enabled
)

internal fun ParseRuleEntity.toDto() = ParseRuleDto(
    id, name, sourcePackage, channelType, regexPattern, transactionType,
    amountGroup, currencyGroup, defaultCurrency, accountHintGroup,
    merchantGroup, defaultCategoryName, priority, enabled, isBuiltIn
)

internal fun ParseRuleDto.toEntity() = ParseRuleEntity(
    id, name, sourcePackage, channelType, regexPattern, transactionType,
    amountGroup, currencyGroup, defaultCurrency, accountHintGroup,
    merchantGroup, defaultCategoryName, priority, enabled, isBuiltIn
)

internal fun RawCaptureLogEntity.toDto() = RawCaptureLogDto(
    id, channel, sourcePackage, title, content, capturedAt,
    parseStatus, parserUsed, extractedJson, linkedTransactionId, errorMessage
)

internal fun RawCaptureLogDto.toEntity() = RawCaptureLogEntity(
    id, channel, sourcePackage, title, content, capturedAt,
    parseStatus, parserUsed, extractedJson, linkedTransactionId, errorMessage
)

internal fun BudgetEntity.toDto() = BudgetDto(
    id, name, categoryId, period, limitAmountMicros, currencyCode, enabled
)

internal fun BudgetDto.toEntity() = BudgetEntity(
    id, name, categoryId, period, limitAmountMicros, currencyCode, enabled
)

internal fun RecurringRuleEntity.toDto() = RecurringRuleDto(
    id, name, type, amountMicros, currencyCode, accountId, toAccountId,
    categoryId, merchant, note, cycleType, dayOfCycle, nextRunEpochMillis, enabled
)

internal fun RecurringRuleDto.toEntity() = RecurringRuleEntity(
    id, name, type, amountMicros, currencyCode, accountId, toAccountId,
    categoryId, merchant, note, cycleType, dayOfCycle, nextRunEpochMillis, enabled
)
