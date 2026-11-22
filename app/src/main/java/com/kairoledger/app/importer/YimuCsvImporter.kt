package com.kairoledger.app.importer

import com.kairoledger.app.core.contract.BillImporter
import com.kairoledger.app.core.contract.ExchangeRateProvider
import com.kairoledger.app.core.contract.ImportPreview
import com.kairoledger.app.core.contract.ImportSummary
import com.kairoledger.app.core.contract.LedgerService
import com.kairoledger.app.core.contract.RecordTransactionRequest
import com.kairoledger.app.core.model.CurrencyCatalog
import com.kairoledger.app.core.model.MoneyAmount
import com.kairoledger.app.data.db.AccountDao
import com.kairoledger.app.data.db.AccountEntity
import com.kairoledger.app.data.db.AccountGroup
import com.kairoledger.app.data.db.CaptureChannel
import com.kairoledger.app.data.db.CategoryDao
import com.kairoledger.app.data.db.CategoryEntity
import com.kairoledger.app.data.db.KairoDatabase
import com.kairoledger.app.data.db.TransactionDao
import com.kairoledger.app.data.db.TransactionStatus
import com.kairoledger.app.data.db.TransactionType
import com.kairoledger.app.data.prefs.AppSettings
import com.kairoledger.app.data.prefs.SettingsStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

/**
 * 兼容一木记账 / 微信账单 / 支付宝账单的 CSV 导入器。
 *
 * 支持 UTF-8 BOM / UTF-8 / GB18030(GBK) 自动探测、RFC 4180 完整解析、
 * 表头智能定位、缺失账户与一二级分类自动创建、历史日期汇率换算，
 * 以及基于 (timestamp, type, origAmountMicros, origCurrency, accountId) 的严格去重。
 */
class YimuCsvImporter(
    private val accountDao: AccountDao,
    private val categoryDao: CategoryDao,
    private val transactionDao: TransactionDao,
    private val exchangeRateProvider: ExchangeRateProvider,
    private val ledgerService: LedgerService,
    private val settingsProvider: suspend () -> AppSettings,
    private val zoneId: ZoneId = ZoneId.systemDefault()
) : BillImporter {

    constructor(
        db: KairoDatabase,
        exchangeRateProvider: ExchangeRateProvider,
        ledgerService: LedgerService,
        settingsStore: SettingsStore
    ) : this(
        accountDao = db.accountDao(),
        categoryDao = db.categoryDao(),
        transactionDao = db.transactionDao(),
        exchangeRateProvider = exchangeRateProvider,
        ledgerService = ledgerService,
        settingsProvider = { settingsStore.getSnapshot() }
    )

    private data class DedupKey(
        val timestamp: Long,
        val type: String,
        val origAmountMicros: Long,
        val origCurrency: String,
        val accountId: Long
    )

    private data class PreviewDedupKey(
        val timestamp: Long,
        val type: String,
        val origAmountMicros: Long,
        val origCurrency: String,
        val accountNameNormalized: String
    )

    private data class ColumnMapping(
        val headerRowIndex: Int,
        val formatName: String,
        val dateCol: Int,
        val fallbackDateCol: Int,
        val typeCol: Int,
        val amountCol: Int,
        val currencyCol: Int,
        val categoryCol: Int,
        val subCategoryCol: Int,
        val accountCol: Int,
        val toAccountCol: Int,
        val toAmountCol: Int,
        val merchantCol: Int,
        val itemCol: Int,
        val noteCol: Int,
        val tagsCol: Int,
        val statusCol: Int
    )

    private data class ParsedRow(
        val rowNumber: Int,
        val timestamp: Long,
        val dateString: String,
        val type: String,
        val origAmountMicros: Long,
        val origCurrency: String,
        val accountName: String,
        val toAccountName: String?,
        val manualToAmountMicros: Long?,
        val categoryName: String,
        val subCategoryName: String?,
        val merchant: String,
        val note: String,
        val tags: String
    )

    private data class CsvAnalysis(
        val formatName: String,
        val totalRows: Int,
        val validRows: List<ParsedRow>,
        val skippedDuplicateRows: Int,
        val newAccountNames: List<String>,
        val newCategoryNames: List<String>,
        val detectedCurrencies: List<String>,
        val warnings: List<String>
    )

    override suspend fun previewCsv(inputStream: InputStream): Result<ImportPreview> {
        return try {
            val rawBytes = withContext(Dispatchers.IO) { inputStream.readBytes() }
            val analysis = analyzeCsv(rawBytes)
            Result.success(
                ImportPreview(
                    formatName = analysis.formatName,
                    totalRows = analysis.totalRows,
                    validRows = analysis.validRows.size,
                    skippedDuplicateRows = analysis.skippedDuplicateRows,
                    newAccountNames = analysis.newAccountNames,
                    newCategoryNames = analysis.newCategoryNames,
                    detectedCurrencies = analysis.detectedCurrencies,
                    warnings = analysis.warnings
                )
            )
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun importCsv(inputStream: InputStream): Result<ImportSummary> {
        return try {
            val rawBytes = withContext(Dispatchers.IO) { inputStream.readBytes() }
            val analysis = analyzeCsv(rawBytes)
            val settings = settingsProvider()

            val existingAccounts = accountDao.getAll()
            val accountsByName = existingAccounts.associateByTo(LinkedHashMap()) {
                normalizeNameKey(it.name)
            }
            val existingCategories = categoryDao.getAll()
            val categoriesByKey = existingCategories.associateByTo(LinkedHashMap()) {
                CategoryKey(it.parentId, normalizeNameKey(it.name), it.type)
            }
            val existingTxKeys = transactionDao.getAll().mapTo(HashSet()) { tx ->
                DedupKey(
                    timestamp = tx.timestamp,
                    type = tx.type,
                    origAmountMicros = tx.origAmountMicros,
                    origCurrency = tx.origCurrency.uppercase(Locale.ROOT),
                    accountId = tx.accountId
                )
            }

            // Record the first detected currency for each account so newly created accounts get matching currency
            val accountFirstCurrency = LinkedHashMap<String, String>()
            for (row in analysis.validRows) {
                val key = normalizeNameKey(row.accountName)
                accountFirstCurrency.putIfAbsent(key, row.origCurrency)
                if (!row.toAccountName.isNullOrBlank()) {
                    val toKey = normalizeNameKey(row.toAccountName)
                    accountFirstCurrency.putIfAbsent(toKey, row.origCurrency)
                }
            }

            val warnings = analysis.warnings.toMutableList()
            var importedCount = 0
            var skippedDuplicateCount = analysis.skippedDuplicateRows
            var createdAccounts = 0
            var createdCategories = 0

            // Sort chronologically from oldest to newest before posting to ledgerService
            val sortedRows = analysis.validRows.sortedBy { it.timestamp }

            for (row in sortedRows) {
                val accountKey = normalizeNameKey(row.accountName)
                var account = accountsByName[accountKey]
                if (account != null) {
                    val dedupKey = DedupKey(
                        timestamp = row.timestamp,
                        type = row.type,
                        origAmountMicros = row.origAmountMicros,
                        origCurrency = row.origCurrency,
                        accountId = account.id
                    )
                    if (dedupKey in existingTxKeys) {
                        skippedDuplicateCount++
                        continue
                    }
                } else {
                    val accountCurrency = accountFirstCurrency[accountKey] ?: row.origCurrency
                    val newAccount = AccountEntity(
                        name = row.accountName.trim(),
                        groupType = inferAccountGroup(row.accountName, accountCurrency),
                        currencyCode = accountCurrency,
                        balanceMicros = 0L,
                        icon = inferAccountIcon(row.accountName, accountCurrency),
                        colorHex = inferAccountColor(row.accountName)
                    )
                    val newId = accountDao.insert(newAccount)
                    account = newAccount.copy(id = newId)
                    accountsByName[accountKey] = account
                    createdAccounts++
                }

                var toAccount: AccountEntity? = null
                if (row.type == TransactionType.TRANSFER && !row.toAccountName.isNullOrBlank()) {
                    val toKey = normalizeNameKey(row.toAccountName)
                    toAccount = accountsByName[toKey]
                    if (toAccount == null) {
                        val toCurrency = accountFirstCurrency[toKey] ?: row.origCurrency
                        val newToAccount = AccountEntity(
                            name = row.toAccountName.trim(),
                            groupType = inferAccountGroup(row.toAccountName, toCurrency),
                            currencyCode = toCurrency,
                            balanceMicros = 0L,
                            icon = inferAccountIcon(row.toAccountName, toCurrency),
                            colorHex = inferAccountColor(row.toAccountName)
                        )
                        val newToId = accountDao.insert(newToAccount)
                        toAccount = newToAccount.copy(id = newToId)
                        accountsByName[toKey] = toAccount
                        createdAccounts++
                    }
                }

                // Resolve or create category (only for EXPENSE / INCOME, or if explicitly provided)
                val categoryId: Long? = if (row.type == TransactionType.EXPENSE || row.type == TransactionType.INCOME) {
                    val catType = row.type
                    val parentName = row.categoryName.ifBlank { defaultCategoryName(catType) }
                    val parentKey = CategoryKey(null, normalizeNameKey(parentName), catType)
                    var parentCat = categoriesByKey[parentKey]
                        ?: findTopOrAnyCategory(categoriesByKey.values, parentName, catType)
                    if (parentCat == null) {
                        val toInsert = CategoryEntity(
                            parentId = null,
                            name = parentName,
                            type = catType,
                            icon = defaultCategoryIcon(parentName, catType),
                            colorHex = defaultCategoryColor(catType),
                            isSystem = false
                        )
                        val insertedId = categoryDao.insert(toInsert)
                        parentCat = toInsert.copy(id = insertedId)
                        categoriesByKey[parentKey] = parentCat
                        createdCategories++
                    }

                    val subName = row.subCategoryName?.takeIf { it.isNotBlank() }
                    if (subName != null && !subName.equals(parentName, ignoreCase = true)) {
                        val subKey = CategoryKey(parentCat.id, normalizeNameKey(subName), catType)
                        var subCat = categoriesByKey[subKey]
                        if (subCat == null) {
                            val toInsert = CategoryEntity(
                                parentId = parentCat.id,
                                name = subName,
                                type = catType,
                                icon = parentCat.icon,
                                colorHex = parentCat.colorHex,
                                isSystem = false
                            )
                            val insertedId = categoryDao.insert(toInsert)
                            subCat = toInsert.copy(id = insertedId)
                            categoriesByKey[subKey] = subCat
                            createdCategories++
                        }
                        subCat.id
                    } else {
                        parentCat.id
                    }
                } else {
                    null
                }

                // Query exchangeRateProvider by transaction date
                val manualRateToAccountMicros = if (row.origCurrency != account.currencyCode) {
                    exchangeRateProvider.getRate(
                        fromCurrency = row.origCurrency,
                        toCurrency = account.currencyCode,
                        onDate = row.dateString
                    ).rateMicros
                } else {
                    if (row.origCurrency != settings.baseCurrency) {
                        // Touch historical rate lookup for base currency reference
                        exchangeRateProvider.getRate(
                            fromCurrency = row.origCurrency,
                            toCurrency = settings.baseCurrency,
                            onDate = row.dateString
                        )
                    }
                    null
                }

                val manualToAmountMicros = if (
                    row.type == TransactionType.TRANSFER &&
                    toAccount != null &&
                    row.manualToAmountMicros == null &&
                    row.origCurrency != toAccount.currencyCode
                ) {
                    val toRate = exchangeRateProvider.getRate(
                        fromCurrency = row.origCurrency,
                        toCurrency = toAccount.currencyCode,
                        onDate = row.dateString
                    ).rateMicros
                    MoneyAmount.convert(row.origAmountMicros, toRate)
                } else {
                    row.manualToAmountMicros
                }

                val request = RecordTransactionRequest(
                    type = row.type,
                    timestamp = row.timestamp,
                    origAmountMicros = row.origAmountMicros,
                    origCurrency = row.origCurrency,
                    accountId = account.id,
                    manualRateToAccountMicros = manualRateToAccountMicros,
                    toAccountId = toAccount?.id,
                    manualToAmountMicros = manualToAmountMicros,
                    categoryId = categoryId,
                    merchant = row.merchant,
                    note = row.note,
                    tags = row.tags,
                    sourceType = CaptureChannel.IMPORT,
                    status = TransactionStatus.CONFIRMED
                )

                val saveResult = ledgerService.saveTransaction(request)
                if (saveResult.isSuccess) {
                    existingTxKeys.add(
                        DedupKey(
                            timestamp = row.timestamp,
                            type = row.type,
                            origAmountMicros = row.origAmountMicros,
                            origCurrency = row.origCurrency,
                            accountId = account.id
                        )
                    )
                    importedCount++
                } else {
                    val errMsg = saveResult.exceptionOrNull()?.message ?: "入账失败"
                    warnings.add("第 ${row.rowNumber} 行保存失败：$errMsg")
                }
            }

            Result.success(
                ImportSummary(
                    importedCount = importedCount,
                    skippedDuplicateCount = skippedDuplicateCount,
                    createdAccounts = createdAccounts,
                    createdCategories = createdCategories,
                    warnings = warnings
                )
            )
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun analyzeCsv(rawBytes: ByteArray): CsvAnalysis {
        if (rawBytes.isEmpty()) {
            throw IllegalArgumentException("CSV 文件内容为空")
        }

        val decoded = Rfc4180CsvParser.decodeAndParse(rawBytes)
        val rows = decoded.rows
        if (rows.isEmpty()) {
            throw IllegalArgumentException("未在文件中解析到任何有效 CSV 行")
        }

        val settings = settingsProvider()
        val mapping = detectHeaderAndColumns(rows)
            ?: throw IllegalArgumentException("未能识别 CSV 表头，请确认文件来自一木记账、微信账单或支付宝账单")

        val existingAccounts = accountDao.getAll().associateBy { normalizeNameKey(it.name) }
        val existingCategories = categoryDao.getAll()
        val existingCategoryKeys = existingCategories.associateBy {
            CategoryKey(it.parentId, normalizeNameKey(it.name), it.type)
        }

        val existingDbKeys = transactionDao.getAll().mapTo(HashSet()) { tx ->
            DedupKey(
                timestamp = tx.timestamp,
                type = tx.type,
                origAmountMicros = tx.origAmountMicros,
                origCurrency = tx.origCurrency.uppercase(Locale.ROOT),
                accountId = tx.accountId
            )
        }
        val seenPreviewKeys = HashSet<PreviewDedupKey>()

        val validRows = mutableListOf<ParsedRow>()
        val warnings = mutableListOf<String>()
        val detectedCurrencies = LinkedHashSet<String>()
        val newAccountNames = LinkedHashSet<String>()
        val newCategoryNames = LinkedHashSet<String>()

        var totalRows = 0
        var skippedDuplicates = 0

        for (idx in (mapping.headerRowIndex + 1) until rows.size) {
            val row = rows[idx]
            if (isSkippableFooterOrBlankRow(row)) {
                continue
            }
            totalRows++
            val rowNumber = idx + 1

            val statusText = cellAt(row, mapping.statusCol)
            if (isCancelledOrFailedStatus(statusText)) {
                warnings.add("第 $rowNumber 行交易状态为「$statusText」，已跳过")
                continue
            }

            val rawDate = cellAt(row, mapping.dateCol).ifBlank {
                cellAt(row, mapping.fallbackDateCol)
            }
            val parsedTime = parseTimestamp(rawDate)
            if (parsedTime == null) {
                warnings.add("第 $rowNumber 行日期格式无效（$rawDate），已跳过")
                continue
            }

            val rawAmountText = cellAt(row, mapping.amountCol)
            val rawCurrencyText = cellAt(row, mapping.currencyCol)
            val currency = resolveCurrency(rawCurrencyText, rawAmountText, settings.baseCurrency)
            val amountWithSign = parseAmountMicrosWithSign(rawAmountText)
            if (amountWithSign == null || amountWithSign.first <= 0L) {
                warnings.add("第 $rowNumber 行金额无效（$rawAmountText），已跳过")
                continue
            }
            val amountMicros = amountWithSign.first
            val wasNegative = amountWithSign.second

            val rawTypeText = cellAt(row, mapping.typeCol)
            val rawToAccount = cleanPlaceholder(cellAt(row, mapping.toAccountCol))
            val txType = resolveTransactionType(rawTypeText, rawToAccount, wasNegative)
            if (txType == null) {
                warnings.add("第 $rowNumber 行为「${rawTypeText.ifBlank { "中性交易" }}」，已跳过")
                continue
            }

            if (txType == TransactionType.TRANSFER && rawToAccount.isBlank()) {
                warnings.add("第 $rowNumber 行转账缺少转入账户，已跳过")
                continue
            }

            val rawAccount = cleanPlaceholder(cellAt(row, mapping.accountCol))
            val accountName = rawAccount.ifBlank {
                defaultAccountNameForFormat(mapping.formatName)
            }
            if (txType == TransactionType.TRANSFER && accountName.equals(rawToAccount, ignoreCase = true)) {
                warnings.add("第 $rowNumber 行转出账户与转入账户相同（$accountName），已跳过")
                continue
            }

            // Dedup check against DB and intra-CSV
            val existingAcc = existingAccounts[normalizeNameKey(accountName)]
            if (existingAcc != null) {
                val dbKey = DedupKey(
                    timestamp = parsedTime.first,
                    type = txType,
                    origAmountMicros = amountMicros,
                    origCurrency = currency,
                    accountId = existingAcc.id
                )
                if (dbKey in existingDbKeys) {
                    skippedDuplicates++
                    continue
                }
            }
            val previewKey = PreviewDedupKey(
                timestamp = parsedTime.first,
                type = txType,
                origAmountMicros = amountMicros,
                origCurrency = currency,
                accountNameNormalized = normalizeNameKey(accountName)
            )
            if (!seenPreviewKeys.add(previewKey)) {
                skippedDuplicates++
                continue
            }

            val rawCategory = cleanPlaceholder(cellAt(row, mapping.categoryCol))
            val rawSubCategory = cleanPlaceholder(cellAt(row, mapping.subCategoryCol))
                .takeIf { it.isNotBlank() }

            val categoryName = when {
                txType == TransactionType.TRANSFER -> ""
                rawCategory.isNotBlank() -> rawCategory
                else -> defaultCategoryName(txType)
            }

            val rawMerchant = cleanPlaceholder(cellAt(row, mapping.merchantCol))
            val rawItem = cleanPlaceholder(cellAt(row, mapping.itemCol))
            val rawNote = cleanPlaceholder(cellAt(row, mapping.noteCol))
            val rawTags = cleanPlaceholder(cellAt(row, mapping.tagsCol))

            val merchant = rawMerchant.ifBlank { rawItem }
            val noteParts = buildList {
                if (rawItem.isNotBlank() && !rawItem.equals(merchant, ignoreCase = true)) {
                    add(rawItem)
                }
                if (rawNote.isNotBlank()) {
                    add(rawNote)
                }
            }
            val note = noteParts.joinToString(" · ")

            val manualToAmountMicros = cellAt(row, mapping.toAmountCol)
                .takeIf { it.isNotBlank() }
                ?.let { parseAmountMicrosWithSign(it)?.first }
                ?.takeIf { it > 0L }

            // Track preview statistics for non-duplicate valid rows
            detectedCurrencies.add(currency)
            if (existingAcc == null) {
                newAccountNames.add(accountName)
            }
            if (txType == TransactionType.TRANSFER && rawToAccount.isNotBlank()) {
                if (existingAccounts[normalizeNameKey(rawToAccount)] == null) {
                    newAccountNames.add(rawToAccount)
                }
            }
            if (txType == TransactionType.EXPENSE || txType == TransactionType.INCOME) {
                val existingParent = existingCategoryKeys[CategoryKey(null, normalizeNameKey(categoryName), txType)]
                    ?: findTopOrAnyCategory(existingCategories, categoryName, txType)
                if (existingParent == null) {
                    newCategoryNames.add(categoryName)
                }
                if (rawSubCategory != null && !rawSubCategory.equals(categoryName, ignoreCase = true)) {
                    val parentId = existingParent?.id
                    val subExists = parentId != null &&
                        existingCategoryKeys[CategoryKey(parentId, normalizeNameKey(rawSubCategory), txType)] != null
                    if (!subExists) {
                        newCategoryNames.add(rawSubCategory)
                    }
                }
            }

            validRows.add(
                ParsedRow(
                    rowNumber = rowNumber,
                    timestamp = parsedTime.first,
                    dateString = parsedTime.second,
                    type = txType,
                    origAmountMicros = amountMicros,
                    origCurrency = currency,
                    accountName = accountName,
                    toAccountName = rawToAccount.takeIf { it.isNotBlank() },
                    manualToAmountMicros = manualToAmountMicros,
                    categoryName = categoryName,
                    subCategoryName = rawSubCategory,
                    merchant = merchant,
                    note = note,
                    tags = rawTags
                )
            )
        }

        return CsvAnalysis(
            formatName = mapping.formatName,
            totalRows = totalRows,
            validRows = validRows,
            skippedDuplicateRows = skippedDuplicates,
            newAccountNames = newAccountNames.toList(),
            newCategoryNames = newCategoryNames.toList(),
            detectedCurrencies = detectedCurrencies.toList(),
            warnings = warnings
        )
    }

    private fun detectHeaderAndColumns(rows: List<List<String>>): ColumnMapping? {
        val preambleText = rows.take(40).joinToString("\n") { it.joinToString(",") }
        val limit = minOf(rows.size, 80)

        var bestIndex = -1
        var bestScore = -1
        var bestMapping: ColumnMapping? = null

        for (i in 0 until limit) {
            val row = rows[i]
            if (row.size < 3) continue
            val normalized = row.map { normalizeHeader(it) }

            val dateCol = findFirstHeader(
                normalized,
                listOf("交易时间", "日期", "记账时间", "付款时间", "交易创建时间", "创建时间", "时间")
            )
            val fallbackDateCol = findFirstHeader(
                normalized,
                listOf("交易创建时间", "付款时间", "创建时间")
            )
            val typeCol = findFirstHeader(
                normalized,
                listOf("收支类型", "收/支", "收支", "类型")
            )
            val amountCol = findFirstHeader(
                normalized,
                listOf("金额", "金额(元)", "交易金额", "金额(原币种)")
            )
            val currencyCol = findFirstHeader(
                normalized,
                listOf("币种", "货币", "原币种")
            )
            val categoryCol = findFirstHeader(
                normalized,
                listOf("类别", "一级分类", "交易分类", "分类", "交易类型")
            )
            val subCategoryCol = findFirstHeader(
                normalized,
                listOf("二级分类", "子分类")
            )
            val accountCol = findFirstHeader(
                normalized,
                listOf("账户", "支付方式", "收/付款方式", "账户1", "资金账户")
            )
            val toAccountCol = findFirstHeader(
                normalized,
                listOf("转入账户", "目标账户", "账户2")
            )
            val toAmountCol = findFirstHeader(
                normalized,
                listOf("转入金额", "目标金额")
            )
            val merchantCol = findFirstHeader(
                normalized,
                listOf("交易对方", "商户", "商品", "商品说明", "商品名称")
            )
            val itemCol = findFirstHeader(
                normalized,
                listOf("商品", "商品说明", "商品名称")
            )
            val noteCol = findFirstHeader(
                normalized,
                listOf("备注", "说明", "附言")
            )
            val tagsCol = findFirstHeader(
                normalized,
                listOf("标签")
            )
            val statusCol = findFirstHeader(
                normalized,
                listOf("当前状态", "交易状态", "状态")
            )

            val matchedCoreCount = listOf(dateCol, typeCol, amountCol, categoryCol, accountCol, merchantCol)
                .count { it >= 0 }

            if (dateCol >= 0 && amountCol >= 0 && matchedCoreCount >= 2) {
                val score = matchedCoreCount +
                    (if (currencyCol >= 0) 1 else 0) +
                    (if (statusCol >= 0) 1 else 0) +
                    (if (noteCol >= 0) 1 else 0)
                if (score > bestScore) {
                    bestScore = score
                    bestIndex = i
                    val formatName = detectFormatName(preambleText, normalized)
                    bestMapping = ColumnMapping(
                        headerRowIndex = i,
                        formatName = formatName,
                        dateCol = dateCol,
                        fallbackDateCol = fallbackDateCol,
                        typeCol = typeCol,
                        amountCol = amountCol,
                        currencyCol = currencyCol,
                        categoryCol = categoryCol,
                        subCategoryCol = subCategoryCol,
                        accountCol = accountCol,
                        toAccountCol = toAccountCol,
                        toAmountCol = toAmountCol,
                        merchantCol = merchantCol,
                        itemCol = itemCol,
                        noteCol = noteCol,
                        tagsCol = tagsCol,
                        statusCol = statusCol
                    )
                }
            }
        }
        return if (bestIndex >= 0) bestMapping else null
    }

    private fun detectFormatName(preamble: String, headers: List<String>): String {
        val headerSet = headers.toSet()
        return when {
            preamble.contains("微信支付") ||
                ("支付方式" in headerSet && "交易单号" in headerSet) ||
                ("当前状态" in headerSet && "商户单号" in headerSet) -> "微信账单 CSV"
            preamble.contains("支付宝") ||
                "收/付款方式" in headerSet ||
                "交易订单号" in headerSet ||
                "商家订单号" in headerSet -> "支付宝账单 CSV"
            "收支类型" in headerSet ||
                "一级分类" in headerSet ||
                "二级分类" in headerSet ||
                "转入账户" in headerSet -> "一木记账 CSV"
            else -> "通用账单 CSV"
        }
    }

    private fun normalizeHeader(raw: String): String =
        raw.trim()
            .removePrefix("\uFEFF")
            .replace("（", "(")
            .replace("）", ")")
            .replace(Regex("\\s+"), "")
            .lowercase(Locale.ROOT)

    private fun findFirstHeader(normalizedHeaders: List<String>, candidates: List<String>): Int {
        for (candidate in candidates) {
            val target = normalizeHeader(candidate)
            val idx = normalizedHeaders.indexOfFirst { it == target }
            if (idx >= 0) return idx
        }
        return -1
    }

    private fun cellAt(row: List<String>, index: Int): String {
        if (index < 0 || index >= row.size) return ""
        return row[index].trim().removePrefix("'").trim()
    }

    private fun cleanPlaceholder(value: String): String {
        val trimmed = value.trim()
        return if (trimmed == "/" || trimmed == "-" || trimmed == "--" || trimmed == "无") {
            ""
        } else {
            trimmed
        }
    }

    private fun isSkippableFooterOrBlankRow(row: List<String>): Boolean {
        if (row.all { it.isBlank() }) return true
        val first = row.firstOrNull { it.isNotBlank() }?.trim() ?: return true
        if (first.startsWith("---") || first.startsWith("===")) return true
        if (first.startsWith("注：") || first.startsWith("特别提示") || first.startsWith("导出时间")) {
            return true
        }
        if ((first.startsWith("共") && first.contains("笔")) && row.count { it.isNotBlank() } <= 2) {
            return true
        }
        if ((first.startsWith("已支出") || first.startsWith("已收入") || first.startsWith("合计")) &&
            row.count { it.isNotBlank() } <= 2
        ) {
            return true
        }
        return false
    }

    private fun isCancelledOrFailedStatus(status: String): Boolean {
        if (status.isBlank()) return false
        val s = status.trim()
        return s.contains("关闭") ||
            s.contains("失败") ||
            s.contains("撤销") ||
            s.contains("取消") ||
            s.contains("等待付款") ||
            s.contains("未支付")
    }

    private fun resolveTransactionType(
        rawType: String,
        rawToAccount: String,
        wasNegativeAmount: Boolean
    ): String? {
        val t = rawType.trim().lowercase(Locale.ROOT)
        return when {
            t.isEmpty() -> if (wasNegativeAmount) TransactionType.EXPENSE else TransactionType.EXPENSE
            t == "支出" || t == "expense" || t == "-" || t == "消费" || t == "付款" -> TransactionType.EXPENSE
            t == "收入" || t == "income" || t == "+" || t == "收款" || t.contains("退款") -> TransactionType.INCOME
            t == "转账" || t == "transfer" || t == "账户互转" -> TransactionType.TRANSFER
            t == "还款" -> if (rawToAccount.isNotBlank()) TransactionType.TRANSFER else TransactionType.EXPENSE
            t == "不计收支" || t == "中性交易" || t == "/" || t == "其他" -> {
                if (rawToAccount.isNotBlank()) TransactionType.TRANSFER else null
            }
            t.contains("支") -> TransactionType.EXPENSE
            t.contains("收") -> TransactionType.INCOME
            t.contains("转") -> if (rawToAccount.isNotBlank()) TransactionType.TRANSFER else null
            else -> null
        }
    }

    private fun resolveCurrency(
        rawCurrency: String,
        rawAmount: String,
        defaultBaseCurrency: String
    ): String {
        val cleaned = cleanPlaceholder(rawCurrency)
        if (cleaned.isNotBlank()) {
            normalizeCurrencyCode(cleaned)?.let { return it }
        }
        return CurrencyCatalog.detectCurrencyFromText(rawAmount, defaultBaseCurrency)
            .uppercase(Locale.ROOT)
    }

    private fun normalizeCurrencyCode(raw: String): String? {
        val upper = raw.trim().uppercase(Locale.ROOT)
        if (upper.isEmpty()) return null
        return when (upper) {
            "人民币", "RMB", "CNY", "¥", "￥", "元" -> "CNY"
            "美元", "美金", "USD", "US$", "$" -> "USD"
            "欧元", "EUR", "€" -> "EUR"
            "英镑", "GBP", "£" -> "GBP"
            "港币", "港元", "HKD", "HK$" -> "HKD"
            "日元", "日圆", "JPY", "JP¥" -> "JPY"
            "新加坡元", "新元", "SGD", "S$" -> "SGD"
            "澳元", "澳大利亚元", "AUD", "A$" -> "AUD"
            "加元", "加拿大元", "CAD", "C$" -> "CAD"
            "瑞郎", "瑞士法郎", "CHF" -> "CHF"
            "新台币", "台币", "TWD", "NT$" -> "TWD"
            "韩元", "KRW", "₩" -> "KRW"
            else -> {
                if (upper.matches(Regex("^[A-Z]{3}$"))) upper
                else CurrencyCatalog.detectCurrencyFromText(raw, "").takeIf { it.isNotBlank() }
            }
        }
    }

    private fun parseAmountMicrosWithSign(raw: String): Pair<Long, Boolean>? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        val isNegative = trimmed.contains('-')
        val numericText = trimmed
            .replace(Regex("(?i)(CNY|RMB|USD|EUR|GBP|HKD|JPY|SGD|AUD|CAD|CHF|TWD|KRW|US\\$|HK\\$|NT\\$|S\\$|A\\$|C\\$|JP¥)"), "")
            .replace("¥", "")
            .replace("￥", "")
            .replace("$", "")
            .replace("€", "")
            .replace("£", "")
            .replace("₩", "")
            .replace("元", "")
            .replace(",", "")
            .replace("，", "")
            .replace("+", "")
            .replace("-", "")
            .trim()
        val parsed = MoneyAmount.parseToMicros(numericText) ?: return null
        return abs(parsed) to isNegative
    }

    private fun parseTimestamp(raw: String): Pair<Long, String>? {
        val cleaned = raw.trim()
            .removePrefix("'")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (cleaned.isEmpty()) return null

        if (cleaned.matches(Regex("^\\d{13}$"))) {
            val millis = cleaned.toLongOrNull() ?: return null
            val date = Instant.ofEpochMilli(millis).atZone(zoneId).toLocalDate()
            return millis to date.toString()
        }

        for (formatter in DATE_TIME_FORMATTERS) {
            try {
                val ldt = LocalDateTime.parse(cleaned, formatter)
                val instant = ldt.atZone(zoneId).toInstant()
                return instant.toEpochMilli() to ldt.toLocalDate().toString()
            } catch (_: Exception) {
            }
        }

        for (formatter in DATE_ONLY_FORMATTERS) {
            try {
                val ld = LocalDate.parse(cleaned, formatter)
                val instant = ld.atStartOfDay(zoneId).toInstant()
                return instant.toEpochMilli() to ld.toString()
            } catch (_: Exception) {
            }
        }

        return null
    }

    private fun findTopOrAnyCategory(
        categories: Collection<CategoryEntity>,
        name: String,
        type: String
    ): CategoryEntity? {
        val key = normalizeNameKey(name)
        return categories.firstOrNull {
            it.type == type && it.parentId == null && normalizeNameKey(it.name) == key
        } ?: categories.firstOrNull {
            it.type == type && normalizeNameKey(it.name) == key
        }
    }

    private fun normalizeNameKey(name: String): String = name.trim().lowercase(Locale.ROOT)

    private fun defaultAccountNameForFormat(formatName: String): String = when (formatName) {
        "微信账单 CSV" -> "微信零钱"
        "支付宝账单 CSV" -> "支付宝"
        else -> "默认导入账户"
    }

    private fun defaultCategoryName(txType: String): String =
        if (txType == TransactionType.INCOME) "其他收入" else "其他支出"

    private fun defaultCategoryIcon(name: String, txType: String): String = when {
        txType == TransactionType.INCOME -> "attach_money"
        name.contains("餐") || name.contains("食") || name.contains("饮") -> "restaurant"
        name.contains("交通") || name.contains("车") || name.contains("地铁") -> "directions_car"
        name.contains("购物") || name.contains("消费") || name.contains("百货") -> "shopping_bag"
        name.contains("订阅") || name.contains("软件") -> "subscriptions"
        name.contains("医") || name.contains("药") -> "medical_services"
        else -> "category"
    }

    private fun defaultCategoryColor(txType: String): Long =
        if (txType == TransactionType.INCOME) 0xFF16A34AL else 0xFF0B57D0L

    private fun inferAccountGroup(name: String, currencyCode: String): String = when {
        name.contains("信用") || name.contains("花呗") || name.contains("白条") ||
            name.contains("Visa", ignoreCase = true) || name.contains("Master", ignoreCase = true) ->
            AccountGroup.CREDIT
        name.contains("储蓄") || name.contains("借记") || name.contains("银行") ->
            AccountGroup.DEBIT
        currencyCode != "CNY" ->
            AccountGroup.FOREIGN
        else -> AccountGroup.WALLET
    }

    private fun inferAccountIcon(name: String, currencyCode: String): String = when {
        name.contains("微信") -> "chat"
        name.contains("支付宝") || name.contains("余额宝") -> "account_balance_wallet"
        name.contains("信用") || name.contains("花呗") -> "credit_card"
        name.contains("银行") || name.contains("储蓄") -> "account_balance"
        currencyCode == "USD" -> "attach_money"
        currencyCode == "EUR" -> "euro"
        currencyCode == "GBP" -> "currency_pound"
        else -> "wallet"
    }

    private fun inferAccountColor(name: String): Long = when {
        name.contains("微信") -> 0xFF07C160L
        name.contains("支付宝") -> 0xFF1677FFL
        name.contains("信用") -> 0xFFDC2626L
        else -> 0xFF0B57D0L
    }

    private data class CategoryKey(
        val parentId: Long?,
        val nameNormalized: String,
        val type: String
    )

    companion object {
        private val DATE_TIME_FORMATTERS = listOf(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT),
            DateTimeFormatter.ofPattern("yyyy-M-d H:mm:ss", Locale.ROOT),
            DateTimeFormatter.ofPattern("yyyy-M-d H:mm", Locale.ROOT),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss", Locale.ROOT),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm", Locale.ROOT),
            DateTimeFormatter.ofPattern("yyyy/M/d H:mm:ss", Locale.ROOT),
            DateTimeFormatter.ofPattern("yyyy/M/d H:mm", Locale.ROOT),
            DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm:ss", Locale.ROOT),
            DateTimeFormatter.ofPattern("yyyy.M.d H:mm", Locale.ROOT),
            DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH:mm:ss", Locale.ROOT),
            DateTimeFormatter.ofPattern("yyyy年M月d日 H:mm:ss", Locale.ROOT),
            DateTimeFormatter.ofPattern("yyyy年M月d日 H:mm", Locale.ROOT),
            DateTimeFormatter.ISO_LOCAL_DATE_TIME
        )

        private val DATE_ONLY_FORMATTERS = listOf(
            DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ROOT),
            DateTimeFormatter.ofPattern("yyyy-M-d", Locale.ROOT),
            DateTimeFormatter.ofPattern("yyyy/MM/dd", Locale.ROOT),
            DateTimeFormatter.ofPattern("yyyy/M/d", Locale.ROOT),
            DateTimeFormatter.ofPattern("yyyy.MM.dd", Locale.ROOT),
            DateTimeFormatter.ofPattern("yyyy.M.d", Locale.ROOT),
            DateTimeFormatter.ofPattern("yyyy年MM月dd日", Locale.ROOT),
            DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.ROOT)
        )
    }
}
