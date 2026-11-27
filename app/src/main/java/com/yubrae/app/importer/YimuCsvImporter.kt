package com.yubrae.app.importer

import com.yubrae.app.core.contract.BillImporter
import com.yubrae.app.core.contract.ExchangeRateProvider
import com.yubrae.app.core.contract.ImportPreview
import com.yubrae.app.core.contract.ImportSummary
import com.yubrae.app.core.contract.LedgerService
import com.yubrae.app.core.contract.RecordTransactionRequest
import com.yubrae.app.core.model.CurrencyCatalog
import com.yubrae.app.core.model.MoneyAmount
import com.yubrae.app.data.db.AccountDao
import com.yubrae.app.data.db.AccountEntity
import com.yubrae.app.data.db.AccountGroup
import com.yubrae.app.data.db.CaptureChannel
import com.yubrae.app.data.db.CategoryDao
import com.yubrae.app.data.db.CategoryEntity
import com.yubrae.app.data.db.DefaultSeedData
import com.yubrae.app.data.db.KairoDatabase
import com.yubrae.app.data.db.LedgerDao
import com.yubrae.app.data.db.LedgerEntity
import com.yubrae.app.data.db.ReimburseStatus
import com.yubrae.app.data.db.TagDao
import com.yubrae.app.data.db.TagEntity
import com.yubrae.app.data.db.TagTextCodec
import com.yubrae.app.data.db.TransactionDao
import com.yubrae.app.data.db.TransactionStatus
import com.yubrae.app.data.db.TransactionType
import com.yubrae.app.data.prefs.AppSettings
import com.yubrae.app.data.prefs.SettingsStore
import com.yubrae.app.domain.RecordUsageRanker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

/**
 * 兼容一木记账（24 列全量导出）/ 通用记账表格 / 微信账单 / 支付宝账单的多格式导入引擎。
 *
 * 支持 `.xlsx` / `.xls` / `.csv` / `.tsv` / `.zip`（含内嵌图片附件），
 * UTF-8 / UTF-16 / GB18030(GBK) 与分隔符自动探测、缺失账本/账户/一二级分类/标签自动创建、
 * 报销/不计收支/不计预算/单列 A->B 转账/多币种解析、方案 A 关联退款自动生成（refundOfTxId）、
 * 历史日期汇率换算，以及跨账本严格去重。
 */
class YimuCsvImporter(
    private val accountDao: AccountDao,
    private val categoryDao: CategoryDao,
    private val transactionDao: TransactionDao,
    private val exchangeRateProvider: ExchangeRateProvider,
    private val ledgerService: LedgerService,
    private val settingsProvider: suspend () -> AppSettings,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
    private val ledgerDao: LedgerDao? = null,
    private val tagDao: TagDao? = null,
    private val receiptsDir: File? = null
) : BillImporter {

    constructor(
        db: KairoDatabase,
        exchangeRateProvider: ExchangeRateProvider,
        ledgerService: LedgerService,
        settingsStore: SettingsStore,
        receiptsDir: File? = null
    ) : this(
        accountDao = db.accountDao(),
        categoryDao = db.categoryDao(),
        transactionDao = db.transactionDao(),
        exchangeRateProvider = exchangeRateProvider,
        ledgerService = ledgerService,
        settingsProvider = { settingsStore.getSnapshot() },
        ledgerDao = db.ledgerDao(),
        tagDao = db.tagDao(),
        receiptsDir = receiptsDir
    )

    private data class DedupKey(
        val timestamp: Long,
        val type: String,
        val origAmountMicros: Long,
        val origCurrency: String,
        val accountId: Long,
        val ledgerId: Long = 0L
    )

    private data class PreviewDedupKey(
        val timestamp: Long,
        val type: String,
        val origAmountMicros: Long,
        val origCurrency: String,
        val accountNameNormalized: String,
        val ledgerKeyNormalized: String = "default"
    )

    private data class MultiCurrencyInfo(
        val currencyCode: String,
        val amountMicros: Long?
    )

    private data class ColumnMapping(
        val headerRowIndex: Int,
        val formatName: String,
        val dateCol: Int,
        val fallbackDateCol: Int,
        val typeCol: Int,
        val amountCol: Int,
        val currencyCol: Int,
        val multiCurrencyCol: Int,
        val categoryCol: Int,
        val subCategoryCol: Int,
        val accountCol: Int,
        val toAccountCol: Int,
        val toAmountCol: Int,
        val ledgerCol: Int,
        val refundCol: Int,
        val refundDetailCol: Int,
        val discountCol: Int,
        val feeCol: Int,
        val merchantCol: Int,
        val itemCol: Int,
        val noteCol: Int,
        val tagsCol: Int,
        val reimburseAccountCol: Int,
        val reimburseAmountCol: Int,
        val reimburseDetailCol: Int,
        val reimburseStatusCol: Int,
        val addressCol: Int,
        val creatorCol: Int,
        val otherCol: Int,
        val excludeStatsCol: Int,
        val excludeBudgetCol: Int,
        val statusCol: Int,
        val attachmentCols: List<Int>
    )

    private data class ParsedRow(
        val rowNumber: Int,
        val timestamp: Long,
        val dateString: String,
        val type: String,
        val origAmountMicros: Long,
        val origCurrency: String,
        val manualAccountAmountMicros: Long?,
        val manualAccountCurrency: String?,
        val accountName: String,
        val toAccountName: String?,
        val manualToAmountMicros: Long?,
        val categoryName: String,
        val subCategoryName: String?,
        val ledgerName: String,
        val merchant: String,
        val note: String,
        val tags: String,
        val tagList: List<String>,
        val reimburseStatus: String,
        val excludeFromStats: Boolean,
        val excludeFromBudget: Boolean,
        val refundMicros: Long,
        val refundDetail: String,
        val attachmentPayloads: List<Pair<String, ByteArray>>,
        val discountAmountMicros: Long = 0L,
        val feeAmountMicros: Long = 0L
    )

    private data class FileAnalysis(
        val formatName: String,
        val totalRows: Int,
        val validRows: List<ParsedRow>,
        val skippedDuplicateRows: Int,
        val newAccountNames: List<String>,
        val newCategoryNames: List<String>,
        val newLedgerNames: List<String>,
        val newTagNames: List<String>,
        val reimburseCount: Int,
        val excludedStatsCount: Int,
        val refundGeneratedCount: Int,
        val attachedImageCount: Int,
        val detectedCurrencies: List<String>,
        val warnings: List<String>
    )

    override suspend fun previewCsv(inputStream: InputStream): Result<ImportPreview> =
        previewFile("bill.csv", inputStream)

    override suspend fun importCsv(inputStream: InputStream): Result<ImportSummary> =
        importFile("bill.csv", inputStream)

    override suspend fun previewFile(fileName: String, inputStream: InputStream): Result<ImportPreview> {
        return try {
            val rawBytes = withContext(Dispatchers.IO) { inputStream.readBytes() }
            val analysis = analyzeFile(fileName, rawBytes)
            Result.success(
                ImportPreview(
                    formatName = analysis.formatName,
                    totalRows = analysis.totalRows,
                    validRows = analysis.validRows.size,
                    skippedDuplicateRows = analysis.skippedDuplicateRows,
                    newAccountNames = analysis.newAccountNames,
                    newCategoryNames = analysis.newCategoryNames,
                    detectedCurrencies = analysis.detectedCurrencies,
                    warnings = analysis.warnings,
                    newLedgerNames = analysis.newLedgerNames,
                    newTagNames = analysis.newTagNames,
                    reimburseCount = analysis.reimburseCount,
                    excludedStatsCount = analysis.excludedStatsCount,
                    refundGeneratedCount = analysis.refundGeneratedCount,
                    attachedImageCount = analysis.attachedImageCount
                )
            )
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun importFile(fileName: String, inputStream: InputStream): Result<ImportSummary> {
        return try {
            val rawBytes = withContext(Dispatchers.IO) { inputStream.readBytes() }
            val analysis = analyzeFile(fileName, rawBytes)
            val settings = settingsProvider()

            val existingAccounts = accountDao.getAll()
            val accountsByName = existingAccounts.associateByTo(LinkedHashMap()) {
                normalizeNameKey(it.name)
            }
            val existingCategories = categoryDao.getAll()
            val categoriesByKey = existingCategories.associateByTo(LinkedHashMap()) {
                CategoryKey(it.parentId, normalizeNameKey(it.name), it.type)
            }

            val existingLedgers = ledgerDao?.getAll().orEmpty()
                .sortedWith(compareBy<LedgerEntity> { it.sortOrder }.thenBy { it.id })
            val ledgersByName = existingLedgers.associateByTo(LinkedHashMap()) {
                normalizeNameKey(it.name)
            }
            var defaultLedger = findDefaultLedger(existingLedgers, settings.activeLedgerId)
            var nextLedgerSortOrder = (existingLedgers.maxOfOrNull { it.sortOrder } ?: 0) + 10
            val fallbackNoDaoLedgerId = settings.activeLedgerId.takeIf { it > 0L } ?: 0L

            val existingTags = tagDao?.getAll().orEmpty()
            val existingTagNames = existingTags.mapTo(HashSet()) { normalizeNameKey(it.name) }
            var nextTagSortOrder = (existingTags.maxOfOrNull { it.sortOrder } ?: 0) + 10

            val existingTxKeys = transactionDao.getAll().mapTo(HashSet()) { tx ->
                DedupKey(
                    timestamp = tx.timestamp,
                    type = tx.type,
                    origAmountMicros = tx.origAmountMicros,
                    origCurrency = tx.origCurrency.uppercase(Locale.ROOT),
                    accountId = tx.accountId,
                    ledgerId = tx.ledgerId
                )
            }

            // Record the first detected currency for each account so newly created accounts get matching currency
            val accountFirstCurrency = LinkedHashMap<String, String>()
            for (row in analysis.validRows) {
                val key = normalizeNameKey(row.accountName)
                val inferredAccCurrency = row.manualAccountCurrency
                    ?: inferCurrencyFromAccountName(row.accountName)
                    ?: row.origCurrency
                accountFirstCurrency.putIfAbsent(key, inferredAccCurrency)
                if (!row.toAccountName.isNullOrBlank()) {
                    val toKey = normalizeNameKey(row.toAccountName)
                    val inferredToCurrency = inferCurrencyFromAccountName(row.toAccountName)
                        ?: row.origCurrency
                    accountFirstCurrency.putIfAbsent(toKey, inferredToCurrency)
                }
            }

            val warnings = analysis.warnings.toMutableList()
            var importedCount = 0
            var skippedDuplicateCount = analysis.skippedDuplicateRows
            var createdAccounts = 0
            var createdCategories = 0
            var createdLedgers = 0
            var createdTags = 0
            var generatedRefundCount = 0
            var attachedImageCount = 0

            // Sort chronologically from oldest to newest before posting to ledgerService
            val sortedRows = analysis.validRows.sortedBy { it.timestamp }

            for (row in sortedRows) {
                // 1. Resolve target ledgerId (without creating yet if duplicate check skips it)
                val existingLedger = resolveExistingLedger(row.ledgerName, ledgersByName, defaultLedger)
                val candidateLedgerId = if (ledgerDao == null) {
                    fallbackNoDaoLedgerId
                } else {
                    existingLedger?.id
                }

                val accountKey = normalizeNameKey(row.accountName)
                var account = accountsByName[accountKey]
                if (account != null && candidateLedgerId != null) {
                    val dedupKey = DedupKey(
                        timestamp = row.timestamp,
                        type = row.type,
                        origAmountMicros = row.origAmountMicros,
                        origCurrency = row.origCurrency,
                        accountId = account.id,
                        ledgerId = candidateLedgerId
                    )
                    if (dedupKey in existingTxKeys) {
                        skippedDuplicateCount++
                        continue
                    }
                }

                // 2. Create missing ledger if needed
                val activeLedgerDao = ledgerDao
                val resolvedLedgerId: Long = if (activeLedgerDao == null) {
                    fallbackNoDaoLedgerId
                } else if (existingLedger != null) {
                    existingLedger.id
                } else if (isDefaultLedgerAlias(row.ledgerName)) {
                    val resolved = defaultLedger ?: run {
                        val fallbackLedger = LedgerEntity(
                            name = "默认账本",
                            currencyCode = settings.baseCurrency,
                            icon = "menu_book",
                            colorHex = IMPORT_PALETTE[0],
                            sortOrder = nextLedgerSortOrder
                        )
                        nextLedgerSortOrder += 10
                        val insertedId = activeLedgerDao.insert(fallbackLedger)
                        val persisted = fallbackLedger.copy(id = insertedId)
                        ledgersByName[normalizeNameKey(persisted.name)] = persisted
                        defaultLedger = persisted
                        createdLedgers++
                        persisted
                    }
                    resolved.id
                } else {
                    val ledgerNameTrimmed = row.ledgerName.trim()
                    val ledgerNorm = normalizeNameKey(ledgerNameTrimmed)
                    val cached = ledgersByName[ledgerNorm]
                    if (cached != null) {
                        cached.id
                    } else {
                        val newLedger = LedgerEntity(
                            name = ledgerNameTrimmed,
                            currencyCode = settings.baseCurrency,
                            icon = "menu_book",
                            colorHex = IMPORT_PALETTE[(existingLedgers.size + createdLedgers) % IMPORT_PALETTE.size],
                            sortOrder = nextLedgerSortOrder
                        )
                        nextLedgerSortOrder += 10
                        val insertedId = activeLedgerDao.insert(newLedger)
                        val persisted = newLedger.copy(id = insertedId)
                        ledgersByName[ledgerNorm] = persisted
                        if (defaultLedger == null) {
                            defaultLedger = persisted
                        }
                        createdLedgers++
                        insertedId
                    }
                }

                // 3. Create missing source account if needed
                if (account == null) {
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

                // 4. Create missing target account for transfers
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

                // 5. Ensure missing tags in tagDao (never overwrite existing tags)
                if (tagDao != null && row.tagList.isNotEmpty()) {
                    for (tagName in row.tagList) {
                        val tagNorm = normalizeNameKey(tagName)
                        if (existingTagNames.add(tagNorm)) {
                            val newTag = TagEntity(
                                name = tagName,
                                groupName = "导入",
                                colorHex = IMPORT_PALETTE[(existingTags.size + createdTags) % IMPORT_PALETTE.size],
                                sortOrder = nextTagSortOrder,
                                isSystem = false,
                                icon = RecordUsageRanker.suggestTagStyle(tagName).first
                            )
                            nextTagSortOrder += 10
                            tagDao.insert(newTag)
                            createdTags++
                        }
                    }
                }

                // 6. Resolve or create category (for EXPENSE / INCOME)
                val categoryId: Long? = if (row.type == TransactionType.EXPENSE || row.type == TransactionType.INCOME) {
                    val catType = row.type
                    val parentName = row.categoryName.ifBlank { defaultCategoryName(catType) }
                    val parentKey = CategoryKey(null, normalizeNameKey(parentName), catType)
                    var parentCat = resolveExistingParentCategory(categoriesByKey, parentName, catType)
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
                            val siblingIcons = categoriesByKey.values
                                .asSequence()
                                .filter { it.parentId == parentCat.id && it.icon.isNotBlank() }
                                .mapTo(HashSet()) { it.icon }
                            val resolvedSubIcon = DefaultSeedData.pickDistinctImportedCategoryIcon(
                                name = subName,
                                parentIcon = parentCat.icon,
                                siblingUsedIcons = siblingIcons
                            )
                            val toInsert = CategoryEntity(
                                parentId = parentCat.id,
                                name = subName,
                                type = catType,
                                icon = resolvedSubIcon,
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

                // 7. Resolve conversion amounts and historical exchange rates
                val effectiveManualAccountAmount = if (
                    row.manualAccountAmountMicros != null &&
                    account.currencyCode.equals(row.manualAccountCurrency ?: settings.baseCurrency, ignoreCase = true)
                ) {
                    row.manualAccountAmountMicros
                } else {
                    null
                }

                val manualRateToAccountMicros = if (effectiveManualAccountAmount == null && row.origCurrency != account.currencyCode) {
                    exchangeRateProvider.getRate(
                        fromCurrency = row.origCurrency,
                        toCurrency = account.currencyCode,
                        onDate = row.dateString
                    ).rateMicros
                } else {
                    if (effectiveManualAccountAmount == null && row.origCurrency != settings.baseCurrency) {
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
                    val effectiveToOrig = (row.origAmountMicros + row.discountAmountMicros - row.feeAmountMicros)
                        .takeIf { it > 0L } ?: row.origAmountMicros
                    val toRate = exchangeRateProvider.getRate(
                        fromCurrency = row.origCurrency,
                        toCurrency = toAccount.currencyCode,
                        onDate = row.dateString
                    ).rateMicros
                    MoneyAmount.convert(effectiveToOrig, toRate)
                } else {
                    row.manualToAmountMicros
                }

                // 8. Persist attachment images if available
                val writtenAttachmentFiles = persistRowAttachments(
                    dir = receiptsDir,
                    timestamp = row.timestamp,
                    rowNumber = row.rowNumber,
                    payloads = row.attachmentPayloads
                )
                val attachmentPathsString = writtenAttachmentFiles.joinToString(";") { it.absolutePath }

                val request = RecordTransactionRequest(
                    type = row.type,
                    timestamp = row.timestamp,
                    origAmountMicros = row.origAmountMicros,
                    origCurrency = row.origCurrency,
                    accountId = account.id,
                    manualAccountAmountMicros = effectiveManualAccountAmount,
                    manualRateToAccountMicros = manualRateToAccountMicros,
                    toAccountId = toAccount?.id,
                    manualToAmountMicros = manualToAmountMicros,
                    categoryId = categoryId,
                    merchant = row.merchant,
                    note = row.note,
                    tags = row.tags,
                    reimburseStatus = row.reimburseStatus,
                    sourceType = CaptureChannel.IMPORT,
                    status = TransactionStatus.CONFIRMED,
                    ledgerId = resolvedLedgerId,
                    excludeFromStats = row.excludeFromStats,
                    excludeFromBudget = row.excludeFromBudget,
                    attachmentPaths = attachmentPathsString,
                    discountAmountMicros = row.discountAmountMicros,
                    feeAmountMicros = row.feeAmountMicros
                )

                val saveResult = ledgerService.saveTransaction(request)
                val savedExpense = saveResult.getOrNull()
                if (savedExpense != null) {
                    existingTxKeys.add(
                        DedupKey(
                            timestamp = row.timestamp,
                            type = row.type,
                            origAmountMicros = row.origAmountMicros,
                            origCurrency = row.origCurrency,
                            accountId = account.id,
                            ledgerId = resolvedLedgerId
                        )
                    )
                    importedCount++
                    attachedImageCount += writtenAttachmentFiles.size

                    // 9. Plan A: auto-generate linked INCOME refund transaction when 退款 > 0
                    if (row.type == TransactionType.EXPENSE && row.refundMicros > 0L) {
                        val refundTimestamp = row.timestamp + 1000L
                        val refundDedupKey = DedupKey(
                            timestamp = refundTimestamp,
                            type = TransactionType.INCOME,
                            origAmountMicros = row.refundMicros,
                            origCurrency = row.origCurrency,
                            accountId = account.id,
                            ledgerId = resolvedLedgerId
                        )
                        if (refundDedupKey !in existingTxKeys) {
                            val refundManualAccountAmount = if (
                                effectiveManualAccountAmount != null &&
                                row.origAmountMicros > 0L
                            ) {
                                BigDecimal(row.refundMicros)
                                    .multiply(BigDecimal(effectiveManualAccountAmount))
                                    .divide(BigDecimal(row.origAmountMicros), 0, RoundingMode.HALF_UP)
                                    .toLong()
                                    .coerceAtLeast(1L)
                            } else {
                                null
                            }

                            val refundRequest = RecordTransactionRequest(
                                type = TransactionType.INCOME,
                                timestamp = refundTimestamp,
                                origAmountMicros = row.refundMicros,
                                origCurrency = row.origCurrency,
                                accountId = account.id,
                                manualAccountAmountMicros = refundManualAccountAmount,
                                manualRateToAccountMicros = if (refundManualAccountAmount == null) {
                                    manualRateToAccountMicros
                                } else {
                                    null
                                },
                                categoryId = savedExpense.categoryId,
                                merchant = row.merchant,
                                note = row.refundDetail.ifBlank { "退款（导入自一木）" },
                                tags = row.tags,
                                reimburseStatus = ReimburseStatus.NONE,
                                sourceType = CaptureChannel.IMPORT,
                                status = TransactionStatus.CONFIRMED,
                                ledgerId = resolvedLedgerId,
                                excludeFromStats = row.excludeFromStats,
                                excludeFromBudget = row.excludeFromBudget,
                                refundOfTxId = savedExpense.id
                            )
                            val refundSave = ledgerService.saveTransaction(refundRequest)
                            if (refundSave.isSuccess) {
                                existingTxKeys.add(refundDedupKey)
                                importedCount++
                                generatedRefundCount++
                            } else {
                                val errMsg = refundSave.exceptionOrNull()?.message ?: "关联退款入账失败"
                                warnings.add("第 ${row.rowNumber} 行关联退款保存失败：$errMsg")
                            }
                        }
                    }
                } else {
                    deleteFilesQuietly(writtenAttachmentFiles)
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
                    warnings = warnings,
                    createdLedgers = createdLedgers,
                    createdTags = createdTags,
                    generatedRefundCount = generatedRefundCount,
                    attachedImageCount = attachedImageCount
                )
            )
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun analyzeFile(fileName: String, rawBytes: ByteArray): FileAnalysis {
        if (rawBytes.isEmpty()) {
            throw IllegalArgumentException("导入文件内容为空")
        }

        val source = SpreadsheetImportParser.parse(fileName, rawBytes)
        val rows = source.rows
        if (rows.isEmpty()) {
            throw IllegalArgumentException("未在文件中解析到任何有效账单行")
        }

        val settings = settingsProvider()
        val mapping = detectHeaderAndColumns(rows, source.sourceKind)
            ?: throw IllegalArgumentException("未能识别账单表头，请确认文件来自一木记账、微信账单、支付宝账单或通用记账表格")

        val existingAccounts = accountDao.getAll().associateBy { normalizeNameKey(it.name) }
        val existingCategories = categoryDao.getAll()
        val existingCategoryKeys = existingCategories.associateBy {
            CategoryKey(it.parentId, normalizeNameKey(it.name), it.type)
        }

        val existingLedgers = ledgerDao?.getAll().orEmpty()
            .sortedWith(compareBy<LedgerEntity> { it.sortOrder }.thenBy { it.id })
        val existingLedgersByName = existingLedgers.associateBy { normalizeNameKey(it.name) }
        val defaultLedger = findDefaultLedger(existingLedgers, settings.activeLedgerId)
        val fallbackNoDaoLedgerId = settings.activeLedgerId.takeIf { it > 0L } ?: 0L

        val existingTagNorms = tagDao?.getAll()?.mapTo(HashSet()) { normalizeNameKey(it.name) }.orEmpty()

        val existingDbKeys = transactionDao.getAll().mapTo(HashSet()) { tx ->
            DedupKey(
                timestamp = tx.timestamp,
                type = tx.type,
                origAmountMicros = tx.origAmountMicros,
                origCurrency = tx.origCurrency.uppercase(Locale.ROOT),
                accountId = tx.accountId,
                ledgerId = tx.ledgerId
            )
        }
        val seenPreviewKeys = HashSet<PreviewDedupKey>()

        val validRows = mutableListOf<ParsedRow>()
        val warnings = mutableListOf<String>()
        val detectedCurrencies = LinkedHashSet<String>()
        val newAccountNames = LinkedHashSet<String>()
        val newCategoryNames = LinkedHashSet<String>()
        val newLedgerNames = LinkedHashSet<String>()
        val seenNewLedgerNorms = HashSet<String>()
        val newTagNames = LinkedHashSet<String>()
        val seenNewTagNorms = HashSet<String>()

        var totalRows = 0
        var skippedDuplicates = 0
        var reimburseCount = 0
        var excludedStatsCount = 0
        var refundGeneratedCount = 0
        var attachedImageCount = 0

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
            val baseAmountCurrency = resolveCurrency(rawCurrencyText, rawAmountText, settings.baseCurrency)
            val rawDiscount = cleanPlaceholder(cellAt(row, mapping.discountCol))
            val parsedDiscountMicros = if (rawDiscount.isNotBlank()) parseAmountMicros(rawDiscount) else null
            val rawFee = cleanPlaceholder(cellAt(row, mapping.feeCol))
            val parsedFeeMicros = if (rawFee.isNotBlank()) parseAmountMicros(rawFee) else null
            val rowAmountMicros = parseAmountMicros(rawAmountText)
            if (rowAmountMicros == null ||
                (rowAmountMicros <= 0L && (parsedDiscountMicros == null || parsedDiscountMicros <= 0L))
            ) {
                warnings.add("第 $rowNumber 行金额无效（$rawAmountText），已跳过")
                continue
            }
            val wasExplicitPositive = hasExplicitPositiveSign(rawAmountText)

            // Multi-currency column resolution
            val rawMultiCurrency = cleanPlaceholder(cellAt(row, mapping.multiCurrencyCol))
            val multiCurrencyInfo = parseMultiCurrencyInfo(rawMultiCurrency)
            var origCurrency = baseAmountCurrency
            var origAmountMicros = rowAmountMicros
            var manualAccountAmountMicros: Long? = null
            var manualAccountCurrency: String? = null
            if (multiCurrencyInfo != null) {
                val fcy = multiCurrencyInfo.currencyCode
                val fcyMicros = multiCurrencyInfo.amountMicros
                if (fcyMicros != null && fcyMicros > 0L) {
                    if (fcy != settings.baseCurrency && fcyMicros != rowAmountMicros) {
                        origCurrency = fcy
                        origAmountMicros = fcyMicros
                        manualAccountAmountMicros = rowAmountMicros
                        manualAccountCurrency = baseAmountCurrency
                    } else {
                        origCurrency = fcy
                        origAmountMicros = fcyMicros
                    }
                } else {
                    origCurrency = fcy
                }
            }

            val rawTypeText = cellAt(row, mapping.typeCol)
            val rawAccountCell = cleanPlaceholder(cellAt(row, mapping.accountCol))
            val rawToAccountCell = cleanPlaceholder(cellAt(row, mapping.toAccountCol))

            val arrowPair = if (rawToAccountCell.isBlank() && isArrowSplittableType(rawTypeText)) {
                splitArrowAccounts(rawAccountCell)
            } else {
                null
            }
            val effectiveRawAccount = arrowPair?.first ?: rawAccountCell
            val rawToAccount = arrowPair?.second ?: rawToAccountCell

            val typeResolution = resolveTransactionType(
                rawType = rawTypeText,
                rawToAccount = rawToAccount,
                wasExplicitPositive = wasExplicitPositive,
                formatName = mapping.formatName
            )
            if (typeResolution == null) {
                warnings.add("第 $rowNumber 行为「${rawTypeText.ifBlank { "中性交易" }}」，已跳过")
                continue
            }
            val txType = typeResolution.first
            val typeImpliesExcludeStats = typeResolution.second

            val discountAmountMicros = if (txType == TransactionType.INCOME) {
                0L
            } else {
                (parsedDiscountMicros ?: 0L).coerceAtLeast(0L)
            }
            val feeAmountMicros = (parsedFeeMicros ?: 0L).coerceAtLeast(0L)
            if (origAmountMicros <= 0L && (txType == TransactionType.INCOME || discountAmountMicros <= 0L)) {
                warnings.add("第 $rowNumber 行金额无效（$rawAmountText），已跳过")
                continue
            }

            if (txType == TransactionType.TRANSFER && rawToAccount.isBlank()) {
                warnings.add("第 $rowNumber 行转账缺少转入账户，已跳过")
                continue
            }

            val accountName = effectiveRawAccount.ifBlank {
                defaultAccountNameForFormat(mapping.formatName)
            }
            if (txType == TransactionType.TRANSFER && accountName.equals(rawToAccount, ignoreCase = true)) {
                warnings.add("第 $rowNumber 行转出账户与转入账户相同（$accountName），已跳过")
                continue
            }

            // Ledger resolution for dedup & preview
            val rawLedgerName = cleanPlaceholder(cellAt(row, mapping.ledgerCol))
            val existingLedger = resolveExistingLedger(rawLedgerName, existingLedgersByName, defaultLedger)
            val resolvedLedgerIdForDedup: Long? = when {
                ledgerDao == null -> fallbackNoDaoLedgerId
                existingLedger != null -> existingLedger.id
                else -> null
            }
            val previewLedgerKey = when {
                ledgerDao == null -> "no_dao:$fallbackNoDaoLedgerId"
                resolvedLedgerIdForDedup != null -> "id:$resolvedLedgerIdForDedup"
                isDefaultLedgerAlias(rawLedgerName) -> "default"
                else -> "new:${normalizeNameKey(rawLedgerName)}"
            }

            // Dedup check against DB and intra-file
            val existingAcc = existingAccounts[normalizeNameKey(accountName)]
            if (existingAcc != null && resolvedLedgerIdForDedup != null) {
                val dbKey = DedupKey(
                    timestamp = parsedTime.first,
                    type = txType,
                    origAmountMicros = origAmountMicros,
                    origCurrency = origCurrency,
                    accountId = existingAcc.id,
                    ledgerId = resolvedLedgerIdForDedup
                )
                if (dbKey in existingDbKeys) {
                    skippedDuplicates++
                    continue
                }
            }
            val previewKey = PreviewDedupKey(
                timestamp = parsedTime.first,
                type = txType,
                origAmountMicros = origAmountMicros,
                origCurrency = origCurrency,
                accountNameNormalized = normalizeNameKey(accountName),
                ledgerKeyNormalized = previewLedgerKey
            )
            if (!seenPreviewKeys.add(previewKey)) {
                skippedDuplicates++
                continue
            }

            // Reimbursement, exclude flags & refund
            val rawOther = cleanPlaceholder(cellAt(row, mapping.otherCol))
            val rawExcludeStats = cleanPlaceholder(cellAt(row, mapping.excludeStatsCol))
            val rawExcludeBudget = cleanPlaceholder(cellAt(row, mapping.excludeBudgetCol))
            val excludeFromStats = typeImpliesExcludeStats ||
                hasExcludeFlag(rawOther, "不计入收支", "不计收支", "不计入统计") ||
                hasExcludeFlag(rawExcludeStats, "不计入收支", "不计收支", "不计入统计", allowBooleanTokens = true)
            val excludeFromBudget = hasExcludeFlag(rawOther, "不计入预算", "不计预算") ||
                hasExcludeFlag(rawExcludeBudget, "不计入预算", "不计预算", allowBooleanTokens = true)

            val rawReimburseAccount = cleanPlaceholder(cellAt(row, mapping.reimburseAccountCol))
            val rawReimburseAmount = cleanPlaceholder(cellAt(row, mapping.reimburseAmountCol))
            val reimburseAmountMicros = parseAmountMicros(rawReimburseAmount) ?: 0L
            val rawReimburseDetail = cleanPlaceholder(cellAt(row, mapping.reimburseDetailCol))
            val rawReimburseStatus = cleanPlaceholder(cellAt(row, mapping.reimburseStatusCol))
            val reimburseStatus = when {
                reimburseAmountMicros > 0L ||
                    rawReimburseAccount.isNotBlank() ||
                    rawReimburseStatus.contains("已报销") ||
                    rawOther.contains("已报销") -> ReimburseStatus.REIMBURSED
                rawReimburseStatus.contains("待报销") ||
                    rawReimburseStatus.contains("可报销") ||
                    rawReimburseStatus.contains("未报销") ||
                    rawOther.contains("待报销") ||
                    rawOther.contains("可报销") ||
                    rawOther.contains("未报销") -> ReimburseStatus.PENDING
                else -> ReimburseStatus.NONE
            }

            val rawRefund = cleanPlaceholder(cellAt(row, mapping.refundCol))
            val rawRefundDetail = cleanPlaceholder(cellAt(row, mapping.refundDetailCol))
            val refundMicros = if (txType == TransactionType.EXPENSE) {
                parseAmountMicros(rawRefund)?.takeIf { it > 0L } ?: 0L
            } else {
                0L
            }
            if (refundMicros > 0L) {
                seenPreviewKeys.add(
                    PreviewDedupKey(
                        timestamp = parsedTime.first + 1000L,
                        type = TransactionType.INCOME,
                        origAmountMicros = refundMicros,
                        origCurrency = origCurrency,
                        accountNameNormalized = normalizeNameKey(accountName),
                        ledgerKeyNormalized = previewLedgerKey
                    )
                )
            }

            // Category & sub-category
            val rawCategory = cleanPlaceholder(cellAt(row, mapping.categoryCol))
            val rawSubCategory = cleanPlaceholder(cellAt(row, mapping.subCategoryCol))
                .takeIf { it.isNotBlank() }

            val categoryName = when {
                txType == TransactionType.TRANSFER -> ""
                rawCategory.isNotBlank() -> rawCategory
                else -> defaultCategoryName(txType)
            }

            // Merchant, item, note & extra Yimu metadata
            val rawMerchant = cleanPlaceholder(cellAt(row, mapping.merchantCol))
            val rawItem = cleanPlaceholder(cellAt(row, mapping.itemCol))
            val rawNote = cleanPlaceholder(cellAt(row, mapping.noteCol))
            val rawAddress = cleanPlaceholder(cellAt(row, mapping.addressCol))
            val rawCreator = cleanPlaceholder(cellAt(row, mapping.creatorCol))

            val merchant = rawMerchant.ifBlank { rawItem }
            val noteParts = ArrayList<String>()
            fun appendNotePart(part: String, dedupAgainst: String = "") {
                val trimmed = part.trim()
                if (trimmed.isEmpty()) return
                val checkToken = dedupAgainst.ifBlank { trimmed }
                if (noteParts.none { it.contains(checkToken, ignoreCase = true) }) {
                    noteParts.add(trimmed)
                }
            }
            if (rawItem.isNotBlank() && !rawItem.equals(merchant, ignoreCase = true)) {
                appendNotePart(rawItem)
            }
            if (rawNote.isNotBlank()) {
                appendNotePart(rawNote)
            }
            if (rawDiscount.isNotBlank() && parsedDiscountMicros == null) {
                appendNotePart("优惠 $rawDiscount", rawDiscount)
            }
            if (rawFee.isNotBlank() && parsedFeeMicros == null) {
                appendNotePart("手续费 $rawFee", rawFee)
            }
            if (rawRefundDetail.isNotBlank()) {
                appendNotePart("退款明细:$rawRefundDetail", rawRefundDetail)
            }
            if (rawReimburseAccount.isNotBlank()) {
                appendNotePart("报销账户:$rawReimburseAccount", rawReimburseAccount)
            }
            if (reimburseAmountMicros > 0L) {
                appendNotePart("报销金额:$rawReimburseAmount", rawReimburseAmount)
            }
            if (rawReimburseDetail.isNotBlank()) {
                appendNotePart("报销明细:$rawReimburseDetail", rawReimburseDetail)
            }
            if (rawAddress.isNotBlank()) {
                appendNotePart("地址:$rawAddress", rawAddress)
            }
            if (rawCreator.isNotBlank()) {
                appendNotePart("创建用户:$rawCreator", rawCreator)
            }
            val extraOtherText = stripStructuredOtherFlags(rawOther)
            if (extraOtherText.isNotBlank()) {
                appendNotePart("其他:$extraOtherText", extraOtherText)
            }
            val note = noteParts.joinToString(" · ")

            // Tags
            val rawTags = cleanPlaceholder(cellAt(row, mapping.tagsCol))
            val tagList = parseTagNames(rawTags)
            val normalizedTags = TagTextCodec.format(tagList)

            // Attachments matching
            val attachmentRefs = buildList {
                for (colIdx in mapping.attachmentCols) {
                    val cellVal = cleanPlaceholder(cellAt(row, colIdx))
                    if (cellVal.isNotBlank()) {
                        cellVal.split(ATTACHMENT_SPLIT_REGEX)
                            .map { cleanPlaceholder(it) }
                            .filter { it.isNotBlank() }
                            .forEach { add(it) }
                    }
                }
            }.distinct()
            val matchedAttachments = attachmentRefs.mapNotNull { ref ->
                resolveAttachmentPayload(ref, source.attachments)
            }

            val manualToAmountMicros = cellAt(row, mapping.toAmountCol)
                .takeIf { it.isNotBlank() }
                ?.let { parseAmountMicros(it) }
                ?.takeIf { it > 0L }

            // Track preview statistics for non-duplicate valid rows
            detectedCurrencies.add(origCurrency)
            if (existingAcc == null) {
                newAccountNames.add(accountName)
            }
            if (txType == TransactionType.TRANSFER && rawToAccount.isNotBlank()) {
                if (existingAccounts[normalizeNameKey(rawToAccount)] == null) {
                    newAccountNames.add(rawToAccount)
                }
            }
            if (txType == TransactionType.EXPENSE || txType == TransactionType.INCOME) {
                val existingParent = resolveExistingParentCategory(existingCategoryKeys, categoryName, txType)
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

            if (ledgerDao != null && !isDefaultLedgerAlias(rawLedgerName) && existingLedger == null) {
                val normLedger = normalizeNameKey(rawLedgerName)
                if (seenNewLedgerNorms.add(normLedger)) {
                    newLedgerNames.add(rawLedgerName.trim())
                }
            }
            if (tagDao != null && tagList.isNotEmpty()) {
                for (tagName in tagList) {
                    val normTag = normalizeNameKey(tagName)
                    if (normTag !in existingTagNorms && seenNewTagNorms.add(normTag)) {
                        newTagNames.add(tagName)
                    }
                }
            }

            if (reimburseStatus != ReimburseStatus.NONE) reimburseCount++
            if (excludeFromStats) excludedStatsCount++
            if (refundMicros > 0L) refundGeneratedCount++
            attachedImageCount += matchedAttachments.size

            validRows.add(
                ParsedRow(
                    rowNumber = rowNumber,
                    timestamp = parsedTime.first,
                    dateString = parsedTime.second,
                    type = txType,
                    origAmountMicros = origAmountMicros,
                    origCurrency = origCurrency,
                    manualAccountAmountMicros = manualAccountAmountMicros,
                    manualAccountCurrency = manualAccountCurrency,
                    accountName = accountName,
                    toAccountName = rawToAccount.takeIf { it.isNotBlank() },
                    manualToAmountMicros = manualToAmountMicros,
                    categoryName = categoryName,
                    subCategoryName = rawSubCategory,
                    ledgerName = rawLedgerName,
                    merchant = merchant,
                    note = note,
                    tags = normalizedTags,
                    tagList = tagList,
                    reimburseStatus = reimburseStatus,
                    excludeFromStats = excludeFromStats,
                    excludeFromBudget = excludeFromBudget,
                    refundMicros = refundMicros,
                    refundDetail = rawRefundDetail,
                    attachmentPayloads = matchedAttachments,
                    discountAmountMicros = discountAmountMicros,
                    feeAmountMicros = feeAmountMicros
                )
            )
        }

        return FileAnalysis(
            formatName = mapping.formatName,
            totalRows = totalRows,
            validRows = validRows,
            skippedDuplicateRows = skippedDuplicates,
            newAccountNames = newAccountNames.toList(),
            newCategoryNames = newCategoryNames.toList(),
            newLedgerNames = newLedgerNames.toList(),
            newTagNames = newTagNames.toList(),
            reimburseCount = reimburseCount,
            excludedStatsCount = excludedStatsCount,
            refundGeneratedCount = refundGeneratedCount,
            attachedImageCount = attachedImageCount,
            detectedCurrencies = detectedCurrencies.toList(),
            warnings = warnings
        )
    }

    private fun detectHeaderAndColumns(
        rows: List<List<String>>,
        sourceKind: String
    ): ColumnMapping? {
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
            val multiCurrencyCol = findFirstHeader(
                normalized,
                listOf("多币种", "外币", "外币金额", "原币金额")
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
            val ledgerCol = findFirstHeader(
                normalized,
                listOf("账本", "账本名称", "所属账本")
            )
            val refundCol = findFirstHeader(
                normalized,
                listOf("退款", "退款金额")
            )
            val refundDetailCol = findFirstHeader(
                normalized,
                listOf("退款明细", "退款说明")
            )
            val discountCol = findFirstHeader(
                normalized,
                listOf("优惠", "优惠金额")
            )
            val feeCol = findFirstHeader(
                normalized,
                listOf("手续费", "服务费", "转账手续费")
            )
            val merchantCol = findFirstHeader(
                normalized,
                listOf("交易对方", "商户", "商家", "商品", "商品说明", "商品名称")
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
            val reimburseAccountCol = findFirstHeader(
                normalized,
                listOf("报销账户")
            )
            val reimburseAmountCol = findFirstHeader(
                normalized,
                listOf("报销金额")
            )
            val reimburseDetailCol = findFirstHeader(
                normalized,
                listOf("报销明细", "报销说明")
            )
            val reimburseStatusCol = findFirstHeader(
                normalized,
                listOf("报销状态", "报销")
            )
            val addressCol = findFirstHeader(
                normalized,
                listOf("地址", "消费地址", "地点")
            )
            val creatorCol = findFirstHeader(
                normalized,
                listOf("创建用户", "创建人", "记账人")
            )
            val otherCol = findFirstHeader(
                normalized,
                listOf("其他", "其它", "扩展属性")
            )
            val excludeStatsCol = findFirstHeader(
                normalized,
                listOf("不计收支", "不计入收支")
            )
            val excludeBudgetCol = findFirstHeader(
                normalized,
                listOf("不计预算", "不计入预算")
            )
            val statusCol = findFirstHeader(
                normalized,
                listOf("当前状态", "交易状态", "状态")
            )
            val attachmentCols = normalized.mapIndexedNotNull { idx, h ->
                if (h == "附件" || h.matches(ATTACHMENT_HEADER_REGEX)) idx else null
            }

            val matchedCoreCount = listOf(dateCol, typeCol, amountCol, categoryCol, accountCol, merchantCol)
                .count { it >= 0 }

            if (dateCol >= 0 && amountCol >= 0 && matchedCoreCount >= 2) {
                val score = matchedCoreCount +
                    (if (currencyCol >= 0) 1 else 0) +
                    (if (statusCol >= 0) 1 else 0) +
                    (if (noteCol >= 0) 1 else 0) +
                    (if (ledgerCol >= 0) 1 else 0) +
                    (if (multiCurrencyCol >= 0) 1 else 0) +
                    (if (refundCol >= 0) 1 else 0) +
                    (if (feeCol >= 0) 1 else 0) +
                    (if (reimburseAccountCol >= 0) 1 else 0) +
                    (if (attachmentCols.isNotEmpty()) 1 else 0)
                if (score > bestScore) {
                    bestScore = score
                    bestIndex = i
                    val preambleText = rows.take(i).joinToString("\n") { it.joinToString(",") }
                    val formatName = detectFormatName(preambleText, normalized, sourceKind)
                    bestMapping = ColumnMapping(
                        headerRowIndex = i,
                        formatName = formatName,
                        dateCol = dateCol,
                        fallbackDateCol = fallbackDateCol,
                        typeCol = typeCol,
                        amountCol = amountCol,
                        currencyCol = currencyCol,
                        multiCurrencyCol = multiCurrencyCol,
                        categoryCol = categoryCol,
                        subCategoryCol = subCategoryCol,
                        accountCol = accountCol,
                        toAccountCol = toAccountCol,
                        toAmountCol = toAmountCol,
                        ledgerCol = ledgerCol,
                        refundCol = refundCol,
                        refundDetailCol = refundDetailCol,
                        discountCol = discountCol,
                        feeCol = feeCol,
                        merchantCol = merchantCol,
                        itemCol = itemCol,
                        noteCol = noteCol,
                        tagsCol = tagsCol,
                        reimburseAccountCol = reimburseAccountCol,
                        reimburseAmountCol = reimburseAmountCol,
                        reimburseDetailCol = reimburseDetailCol,
                        reimburseStatusCol = reimburseStatusCol,
                        addressCol = addressCol,
                        creatorCol = creatorCol,
                        otherCol = otherCol,
                        excludeStatsCol = excludeStatsCol,
                        excludeBudgetCol = excludeBudgetCol,
                        statusCol = statusCol,
                        attachmentCols = attachmentCols
                    )
                }
            }
        }
        return if (bestIndex >= 0) bestMapping else null
    }

    private fun detectFormatName(
        preamble: String,
        headers: List<String>,
        sourceKind: String
    ): String {
        val headerSet = headers.toSet()
        val kindSuffix = sourceKind.ifBlank { "CSV" }
        return when {
            preamble.contains("微信支付") ||
                ("支付方式" in headerSet && "交易单号" in headerSet) ||
                ("当前状态" in headerSet && "商户单号" in headerSet) -> "微信账单 $kindSuffix"
            preamble.contains("支付宝") ||
                "收/付款方式" in headerSet ||
                "交易订单号" in headerSet ||
                "商家订单号" in headerSet -> "支付宝账单 $kindSuffix"
            "多币种" in headerSet ||
                "报销账户" in headerSet ||
                "退款明细" in headerSet ||
                "附件1" in headerSet ||
                "创建用户" in headerSet ||
                ("账本" in headerSet && "退款" in headerSet) -> "一木记账 $kindSuffix"
            "收支类型" in headerSet ||
                "一级分类" in headerSet ||
                "二级分类" in headerSet ||
                "转入账户" in headerSet -> "通用记账 $kindSuffix"
            else -> "通用账单 $kindSuffix"
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

    private fun isArrowSplittableType(rawType: String): Boolean {
        val t = rawType.trim().lowercase(Locale.ROOT)
        return t.isEmpty() ||
            t == "转账" ||
            t == "transfer" ||
            t == "账户互转" ||
            t == "还款" ||
            t == "不计收支" ||
            t == "不计入收支" ||
            t == "中性交易" ||
            t.contains("转")
    }

    private fun splitArrowAccounts(rawAccount: String): Pair<String, String>? {
        if (rawAccount.isBlank()) return null
        val match = ARROW_ACCOUNT_REGEX.matchEntire(rawAccount.trim()) ?: return null
        val from = cleanPlaceholder(match.groupValues[1])
        val to = cleanPlaceholder(match.groupValues[2])
        return if (from.isNotEmpty() && to.isNotEmpty()) from to to else null
    }

    private fun resolveTransactionType(
        rawType: String,
        rawToAccount: String,
        wasExplicitPositive: Boolean,
        formatName: String
    ): Pair<String, Boolean>? {
        val t = rawType.trim().lowercase(Locale.ROOT)
        return when {
            t.isEmpty() -> TransactionType.EXPENSE to false
            t == "支出" || t == "expense" || t == "-" || t == "消费" || t == "付款" ->
                TransactionType.EXPENSE to false
            t == "收入" || t == "income" || t == "+" || t == "收款" || t.contains("退款") ->
                TransactionType.INCOME to false
            t == "转账" || t == "transfer" || t == "账户互转" ->
                TransactionType.TRANSFER to false
            t == "还款" ->
                (if (rawToAccount.isNotBlank()) TransactionType.TRANSFER else TransactionType.EXPENSE) to false
            t == "不计入收支" || t == "不计收支" -> {
                if (rawToAccount.isNotBlank()) {
                    TransactionType.TRANSFER to true
                } else {
                    val fallbackType = if (wasExplicitPositive) TransactionType.INCOME else TransactionType.EXPENSE
                    fallbackType to true
                }
            }
            t == "中性交易" -> {
                if (rawToAccount.isNotBlank()) {
                    TransactionType.TRANSFER to false
                } else if (formatName.startsWith("微信账单") || formatName.startsWith("支付宝账单")) {
                    null
                } else {
                    val fallbackType = if (wasExplicitPositive) TransactionType.INCOME else TransactionType.EXPENSE
                    fallbackType to true
                }
            }
            t == "/" || t == "其他" -> {
                if (rawToAccount.isNotBlank()) TransactionType.TRANSFER to false else null
            }
            t.contains("支") -> TransactionType.EXPENSE to false
            t.contains("收") -> TransactionType.INCOME to false
            t.contains("转") -> if (rawToAccount.isNotBlank()) TransactionType.TRANSFER to false else null
            else -> null
        }
    }

    private fun hasExcludeFlag(
        rawText: String,
        vararg keywords: String,
        allowBooleanTokens: Boolean = false
    ): Boolean {
        if (rawText.isBlank()) return false
        val lower = rawText.trim().lowercase(Locale.ROOT)
        if (keywords.any { lower.contains(it.lowercase(Locale.ROOT)) }) {
            return true
        }
        return allowBooleanTokens && (lower == "true" || lower == "是" || lower == "1")
    }

    private fun stripStructuredOtherFlags(rawOther: String): String {
        if (rawOther.isBlank()) return ""
        return rawOther
            .split(Regex("[,，;；、\\s]+"))
            .map { it.trim() }
            .filter { token ->
                token.isNotEmpty() && token !in STRUCTURED_OTHER_FLAGS
            }
            .joinToString(" ")
    }

    private fun hasExplicitPositiveSign(rawAmount: String): Boolean {
        val trimmed = rawAmount.trim()
        return trimmed.startsWith("+") || trimmed.contains("+")
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
        return detectCurrencyCodeFromText(rawAmount)
            ?: CurrencyCatalog.detectCurrencyFromText(rawAmount, defaultBaseCurrency)
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
                if (upper.matches(Regex("^[A-Z]{3}$"))) {
                    upper
                } else {
                    detectCurrencyCodeFromText(raw)
                }
            }
        }
    }

    private fun detectCurrencyCodeFromText(raw: String): String? {
        if (raw.isBlank()) return null
        val upper = raw.uppercase(Locale.ROOT)
        return when {
            upper.contains("HK$") || upper.contains("HKD") || upper.contains("港币") || upper.contains("港元") -> "HKD"
            upper.contains("NT$") || upper.contains("TWD") || upper.contains("新台币") || upper.contains("台币") -> "TWD"
            upper.contains("US$") || upper.contains("USD") || upper.contains("美元") || upper.contains("美金") -> "USD"
            upper.contains("JP¥") || upper.contains("JPY") || upper.contains("日元") || upper.contains("日圆") -> "JPY"
            upper.contains("S$") || upper.contains("SGD") || upper.contains("新加坡元") || upper.contains("新元") -> "SGD"
            upper.contains("A$") || upper.contains("AUD") || upper.contains("澳元") -> "AUD"
            upper.contains("C$") || upper.contains("CAD") || upper.contains("加元") -> "CAD"
            upper.contains("EUR") || raw.contains("€") || upper.contains("欧元") -> "EUR"
            upper.contains("GBP") || raw.contains("£") || upper.contains("英镑") -> "GBP"
            upper.contains("CHF") || upper.contains("瑞郎") || upper.contains("瑞士法郎") -> "CHF"
            upper.contains("KRW") || raw.contains("₩") || upper.contains("韩元") -> "KRW"
            upper.contains("CNY") || upper.contains("RMB") || upper.contains("人民币") -> "CNY"
            raw.contains("$") -> "USD"
            raw.contains("¥") || raw.contains("￥") || raw.contains("元") -> "CNY"
            else -> {
                ISO_CODE_IN_TEXT_REGEX.find(upper)?.groupValues?.get(1)
            }
        }
    }

    private fun parseMultiCurrencyInfo(raw: String): MultiCurrencyInfo? {
        if (raw.isBlank()) return null
        val code = detectCurrencyCodeFromText(raw) ?: return null
        val amountMatch = FIRST_NUMBER_REGEX.find(raw)
        val micros = amountMatch?.groupValues?.get(1)?.let { MoneyAmount.parseToMicros(it) }?.let { abs(it) }
        return MultiCurrencyInfo(code, micros)
    }

    private fun parseAmountMicros(raw: String): Long? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        val numericText = trimmed
            .replace(CURRENCY_TOKEN_STRIP_REGEX, "")
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
        return abs(parsed)
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

    private fun findDefaultLedger(
        ledgers: List<LedgerEntity>,
        preferredLedgerId: Long = 0L
    ): LedgerEntity? =
        ledgers.firstOrNull { !it.archived && it.id == preferredLedgerId }
            ?: ledgers.firstOrNull { !it.archived && normalizeNameKey(it.name) == "默认账本" }
            ?: ledgers.firstOrNull { !it.archived }
            ?: ledgers.firstOrNull()

    private fun isDefaultLedgerAlias(rawLedgerName: String): Boolean {
        val trimmed = rawLedgerName.trim()
        return trimmed.isEmpty() || trimmed == "默认账本" || trimmed == "日常账本"
    }

    private fun resolveExistingLedger(
        rawLedgerName: String,
        ledgersByName: Map<String, LedgerEntity>,
        defaultLedger: LedgerEntity?
    ): LedgerEntity? {
        val trimmed = rawLedgerName.trim()
        if (trimmed.isEmpty()) return defaultLedger
        val exact = ledgersByName[normalizeNameKey(trimmed)]
        if (exact != null) return exact
        return if (trimmed == "默认账本" || trimmed == "日常账本") defaultLedger else null
    }

    private fun parseTagNames(raw: String): List<String> {
        if (raw.isBlank()) return emptyList()
        return raw.split(TAG_SPLIT_REGEX)
            .asSequence()
            .map { cleanPlaceholder(it.trim('"', '\'')) }
            .filter { it.isNotEmpty() }
            .distinct()
            .toList()
    }

    private fun resolveAttachmentPayload(
        ref: String,
        attachments: Map<String, ByteArray>
    ): Pair<String, ByteArray>? {
        if (attachments.isEmpty() || ref.isBlank()) return null
        val normalized = ref.replace('\\', '/').trimStart('/').lowercase(Locale.ROOT)
        attachments[normalized]?.let { return normalized to it }
        val pureName = normalized.substringAfterLast('/')
        if (pureName.isNotEmpty()) {
            attachments[pureName]?.let { return pureName to it }
            attachments.entries.firstOrNull { (k, _) -> k.endsWith("/$pureName") }?.let {
                return it.key to it.value
            }
        }
        return null
    }

    private suspend fun persistRowAttachments(
        dir: File?,
        timestamp: Long,
        rowNumber: Int,
        payloads: List<Pair<String, ByteArray>>
    ): List<File> {
        if (dir == null || payloads.isEmpty()) return emptyList()
        return withContext(Dispatchers.IO) {
            if (!dir.exists()) {
                dir.mkdirs()
            }
            payloads.mapIndexedNotNull { idx, (nameKey, bytes) ->
                val ext = nameKey.substringAfterLast('.', "jpg")
                    .lowercase(Locale.ROOT)
                    .takeIf { it in VALID_IMAGE_EXTS }
                    ?: "jpg"
                val outFile = File(dir, "receipt_imp_${timestamp}_${rowNumber}_${idx}.$ext")
                runCatching {
                    outFile.writeBytes(bytes)
                    outFile
                }.getOrNull()
            }
        }
    }

    private fun deleteFilesQuietly(files: List<File>) {
        for (f in files) {
            runCatching { if (f.exists()) f.delete() }
        }
    }

    private fun resolveExistingParentCategory(
        categoriesByKey: Map<CategoryKey, CategoryEntity>,
        parentName: String,
        catType: String
    ): CategoryEntity? {
        val parentKey = CategoryKey(null, normalizeNameKey(parentName), catType)
        categoriesByKey[parentKey]?.let { return it }
        findTopOrAnyCategory(categoriesByKey.values, parentName, catType)?.let { return it }
        val canonicalName = DefaultSeedData.canonicalImportedParentName(parentName, catType) ?: return null
        val canonicalKey = CategoryKey(null, normalizeNameKey(canonicalName), catType)
        return categoriesByKey[canonicalKey]
            ?: findTopOrAnyCategory(categoriesByKey.values, canonicalName, catType)
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

    private fun inferCurrencyFromAccountName(name: String): String? = when {
        name.contains("美元") || name.contains("USD", ignoreCase = true) -> "USD"
        name.contains("欧元") || name.contains("EUR", ignoreCase = true) -> "EUR"
        name.contains("英镑") || name.contains("GBP", ignoreCase = true) -> "GBP"
        name.contains("港币") || name.contains("港元") || name.contains("HKD", ignoreCase = true) -> "HKD"
        name.contains("日元") || name.contains("JPY", ignoreCase = true) -> "JPY"
        else -> null
    }

    private fun defaultAccountNameForFormat(formatName: String): String = when {
        formatName.startsWith("微信账单") -> "微信零钱"
        formatName.startsWith("支付宝账单") -> "支付宝"
        else -> "默认导入账户"
    }

    private fun defaultCategoryName(txType: String): String =
        if (txType == TransactionType.INCOME) "其他收入" else "其他支出"

    private fun defaultCategoryIcon(name: String, txType: String): String {
        val suggested = com.yubrae.app.domain.RecordUsageRanker.suggestCategoryStyle(name).first
        if (suggested != "category") return suggested
        return if (txType == TransactionType.INCOME) "attach_money" else "category"
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
        private val TAG_SPLIT_REGEX = Regex("[,，;；、#\\s]+")
        private val ATTACHMENT_SPLIT_REGEX = Regex("[;,|；]+")
        private val ATTACHMENT_HEADER_REGEX = Regex("^附件\\d*$")
        private val ARROW_ACCOUNT_REGEX = Regex("^(.+?)\\s*(?:->|=>|→|➜|➡|—>)\\s*(.+)$")
        private val ISO_CODE_IN_TEXT_REGEX = Regex("\\b([A-Z]{3})\\b")
        private val FIRST_NUMBER_REGEX = Regex("([0-9]+(?:,[0-9]{3})*(?:\\.[0-9]{1,4})?)")
        private val CURRENCY_TOKEN_STRIP_REGEX = Regex(
            "(?i)(CNY|RMB|USD|EUR|GBP|HKD|JPY|SGD|AUD|CAD|CHF|TWD|KRW|US\\$|HK\\$|NT\\$|S\\$|A\\$|C\\$|JP¥)"
        )
        private val VALID_IMAGE_EXTS = setOf("jpg", "jpeg", "png", "webp", "heic")
        private val STRUCTURED_OTHER_FLAGS = setOf(
            "不计入收支", "不计收支", "不计入统计",
            "不计入预算", "不计预算",
            "已报销", "待报销", "可报销", "未报销"
        )

        private val IMPORT_PALETTE = listOf(
            0xFF2AA876L,
            0xFF0284C7L,
            0xFF7C3AEDL,
            0xFFEA580CL,
            0xFFDB2777L,
            0xFF0D9488L,
            0xFF2563EBL,
            0xFFD97706L,
            0xFFE11D48L,
            0xFF16A34AL,
            0xFF4F46E5L,
            0xFF006874L
        )

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
