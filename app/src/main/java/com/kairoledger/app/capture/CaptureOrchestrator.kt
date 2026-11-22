package com.kairoledger.app.capture

import android.content.Context
import com.kairoledger.app.core.contract.CaptureEngine
import com.kairoledger.app.core.contract.CaptureHandleResult
import com.kairoledger.app.core.contract.CaptureInput
import com.kairoledger.app.core.contract.DevicePermissionReport
import com.kairoledger.app.core.contract.LedgerService
import com.kairoledger.app.core.contract.ParsedBillDraft
import com.kairoledger.app.core.contract.RecordTransactionRequest
import com.kairoledger.app.core.model.CurrencyCatalog
import com.kairoledger.app.data.db.AccountAliasDao
import com.kairoledger.app.data.db.AccountAliasEntity
import com.kairoledger.app.data.db.AccountDao
import com.kairoledger.app.data.db.AccountEntity
import com.kairoledger.app.data.db.AliasMatchType
import com.kairoledger.app.data.db.CaptureChannel
import com.kairoledger.app.data.db.CategoryDao
import com.kairoledger.app.data.db.CategoryEntity
import com.kairoledger.app.data.db.KairoDatabase
import com.kairoledger.app.data.db.ParseRuleDao
import com.kairoledger.app.data.db.ParseRuleEntity
import com.kairoledger.app.data.db.ParseStatus
import com.kairoledger.app.data.db.RawCaptureLogDao
import com.kairoledger.app.data.db.RawCaptureLogEntity
import com.kairoledger.app.data.db.TransactionDao
import com.kairoledger.app.data.db.TransactionStatus
import com.kairoledger.app.data.db.TransactionType
import com.kairoledger.app.data.prefs.AppSettings
import com.kairoledger.app.data.prefs.SettingsStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import java.util.Locale

/**
 * 自动记账核心编排器：
 * 原始报文先入库 -> 噪声过滤 -> 本地规则 -> AI兜底 -> 60s跨渠道去重融合 -> LedgerService 事务落账 -> 悬浮确认弹窗/通知降级。
 * 主构造函数支持纯 JVM + Fake DAO 单元测试；次构造函数供 AppContainer 注入生产依赖。
 */
class CaptureOrchestrator(
    private val rawCaptureLogDao: RawCaptureLogDao,
    private val accountAliasDao: AccountAliasDao,
    private val parseRuleDao: ParseRuleDao,
    private val accountDao: AccountDao,
    private val categoryDao: CategoryDao,
    private val transactionDao: TransactionDao,
    private val ledgerService: LedgerService,
    private val localRuleParser: LocalRuleParser = LocalRuleParser(),
    private val llmBillParser: LlmBillParser = LlmBillParser(OkHttpClient()),
    private val deduplicator: CaptureDeduplicator = CaptureDeduplicator(),
    private val settingsProvider: suspend () -> AppSettings = { AppSettings() },
    private val floatingPromptLauncher: suspend (
        transactionId: Long,
        draft: ParsedBillDraft,
        accountName: String,
        settings: AppSettings
    ) -> Unit = { _, _, _, _ -> },
    private val permissionInspector: suspend () -> DevicePermissionReport = { defaultUnsupportedPermissionReport() },
    private val rootGranter: suspend () -> Result<String> = {
        Result.failure(UnsupportedOperationException("仅支持在 Android 设备执行 Root 授权"))
    }
) : CaptureEngine {

    constructor(
        context: Context,
        db: KairoDatabase,
        settingsStore: SettingsStore,
        okHttpClient: OkHttpClient,
        ledgerService: LedgerService
    ) : this(
        rawCaptureLogDao = db.rawCaptureLogDao(),
        accountAliasDao = db.accountAliasDao(),
        parseRuleDao = db.parseRuleDao(),
        accountDao = db.accountDao(),
        categoryDao = db.categoryDao(),
        transactionDao = db.transactionDao(),
        ledgerService = ledgerService,
        localRuleParser = LocalRuleParser(),
        llmBillParser = LlmBillParser(okHttpClient),
        deduplicator = CaptureDeduplicator(),
        settingsProvider = { settingsStore.getSnapshot() },
        floatingPromptLauncher = { txId, draft, accountName, settings ->
            FloatingConfirmService.showOrNotify(
                context = context.applicationContext,
                transactionId = txId,
                draft = draft,
                accountName = accountName,
                autoConfirmSeconds = settings.floatingAutoConfirmSeconds
            )
        },
        permissionInspector = { AndroidPermissionHelper.inspectPermissions(context.applicationContext) },
        rootGranter = { AndroidPermissionHelper.tryRootGrantPermissions(context.applicationContext) }
    )

    private val captureMutex = Mutex()

    override fun observeCaptureLogs(limit: Int): Flow<List<RawCaptureLogEntity>> =
        rawCaptureLogDao.observeRecent(limit)

    override fun observeAccountAliases(): Flow<List<AccountAliasEntity>> =
        accountAliasDao.observeAll()

    override fun observeParseRules(): Flow<List<ParseRuleEntity>> =
        parseRuleDao.observeAll()

    override suspend fun handleIncomingCapture(
        input: CaptureInput,
        showFloatingPrompt: Boolean
    ): CaptureHandleResult {
        // 1. 原始报文永远先插入 raw_capture_logs
        val initialLog = RawCaptureLogEntity(
            channel = input.channel,
            sourcePackage = input.sourcePackage,
            title = input.title,
            content = input.content,
            capturedAt = input.timestamp,
            parseStatus = ParseStatus.FAILED
        )
        val logId = rawCaptureLogDao.insert(initialLog)

        val settings = runCatching { settingsProvider() }.getOrDefault(AppSettings())

        if (!isChannelEnabled(input.channel, settings)) {
            val msg = "采集通道 ${input.channel} 已关闭"
            rawCaptureLogDao.update(
                initialLog.copy(
                    id = logId,
                    parseStatus = ParseStatus.IGNORED,
                    errorMessage = msg
                )
            )
            return CaptureHandleResult(logId = logId, status = ParseStatus.IGNORED, message = msg)
        }

        // 2. 噪声过滤
        if (localRuleParser.isNoise(input.title, input.content)) {
            val msg = "非动账噪声通知已过滤"
            rawCaptureLogDao.update(
                initialLog.copy(
                    id = logId,
                    parseStatus = ParseStatus.IGNORED,
                    errorMessage = msg
                )
            )
            return CaptureHandleResult(logId = logId, status = ParseStatus.IGNORED, message = msg)
        }

        // 3. 本地规则 + 启发式兜底解析
        val rules = parseRuleDao.getEnabled()
        val aliases = accountAliasDao.getEnabled()
        val allAccounts = accountDao.getAll()
        val activeAccounts = allAccounts.filter { !it.archived }.ifEmpty { allAccounts }
        val categories = categoryDao.getAll()

        var draft = localRuleParser.parse(
            input = input,
            rules = rules,
            aliases = aliases,
            accounts = activeAccounts,
            categories = categories
        )
        var matchedStatus = ParseStatus.MATCHED_LOCAL
        var llmErrorMessage: String? = null

        // 4. LLM 兜底解析
        if (draft == null && settings.llmEnabled && settings.llmApiKey.isNotBlank()) {
            val llmResult = llmBillParser.parse(input, settings, activeAccounts, categories)
            llmResult.fold(
                onSuccess = { aiDraft ->
                    if (aiDraft != null) {
                        draft = aiDraft
                        matchedStatus = ParseStatus.MATCHED_AI
                    } else {
                        llmErrorMessage = "AI 判定非资金动账"
                    }
                },
                onFailure = { err ->
                    llmErrorMessage = "AI 解析失败: ${err.message ?: "未知错误"}"
                }
            )
        }

        val resolvedDraft = draft
        if (resolvedDraft == null) {
            val finalStatus = if (llmErrorMessage == "AI 判定非资金动账") ParseStatus.IGNORED else ParseStatus.FAILED
            val errorMsg = llmErrorMessage ?: "未命中本地解析规则"
            rawCaptureLogDao.update(
                initialLog.copy(
                    id = logId,
                    parseStatus = finalStatus,
                    errorMessage = errorMsg
                )
            )
            return CaptureHandleResult(logId = logId, status = finalStatus, message = errorMsg)
        }

        return persistDraftWithDeduplication(
            logId = logId,
            initialLog = initialLog,
            draft = resolvedDraft,
            matchedStatus = matchedStatus,
            channel = input.channel,
            sourcePackage = input.sourcePackage,
            rawText = "${input.title}\n${input.content}".trim(),
            activeAccounts = activeAccounts,
            categories = categories,
            settings = settings,
            showFloatingPrompt = showFloatingPrompt
        )
    }

    /**
     * 供 ExternalBillReceiver / AutoAccountingSchemeActivity 调用的结构化外部记账入口。
     */
    suspend fun handleExternalBillRecord(
        channel: String = CaptureChannel.AUTO_ACCOUNTING,
        sourcePackage: String = "external",
        type: String,
        origAmountMicros: Long,
        origCurrency: String?,
        accountNameOrHint: String?,
        categoryName: String?,
        merchant: String,
        note: String,
        timestamp: Long,
        showFloatingPrompt: Boolean = true
    ): CaptureHandleResult {
        val rawSummary = "type=$type amount=$origAmountMicros cur=${origCurrency.orEmpty()} acc=${accountNameOrHint.orEmpty()} cat=${categoryName.orEmpty()} remark=$note"
        val initialLog = RawCaptureLogEntity(
            channel = channel,
            sourcePackage = sourcePackage,
            title = "外部记账协议",
            content = rawSummary,
            capturedAt = timestamp,
            parseStatus = ParseStatus.FAILED
        )
        val logId = rawCaptureLogDao.insert(initialLog)
        val settings = runCatching { settingsProvider() }.getOrDefault(AppSettings())

        if (channel == CaptureChannel.AUTO_ACCOUNTING && !settings.autoAccountingBridgeEnabled) {
            val msg = "AutoAccounting 外部协议接收开关已关闭"
            rawCaptureLogDao.update(initialLog.copy(id = logId, parseStatus = ParseStatus.IGNORED, errorMessage = msg))
            return CaptureHandleResult(logId = logId, status = ParseStatus.IGNORED, message = msg)
        }

        if (origAmountMicros <= 0L) {
            val msg = "外部记账金额必须大于 0"
            rawCaptureLogDao.update(initialLog.copy(id = logId, parseStatus = ParseStatus.FAILED, errorMessage = msg))
            return CaptureHandleResult(logId = logId, status = ParseStatus.FAILED, message = msg)
        }

        val allAccounts = accountDao.getAll()
        val activeAccounts = allAccounts.filter { !it.archived }.ifEmpty { allAccounts }
        val aliases = accountAliasDao.getEnabled()
        val categories = categoryDao.getAll()

        val resolvedAccount = resolveExternalAccount(accountNameOrHint, origCurrency, activeAccounts, aliases)
        val finalCurrency = when {
            !origCurrency.isNullOrBlank() -> CurrencyCatalog.find(origCurrency).code
            resolvedAccount != null -> resolvedAccount.currencyCode
            else -> settings.baseCurrency
        }
        val typedCategories = categories.filter { it.type.equals(type, ignoreCase = true) }.ifEmpty { categories }
        val explicitCategory = categoryName?.takeIf { it.isNotBlank() }?.let { target ->
            typedCategories.firstOrNull { it.name.equals(target, ignoreCase = true) }
                ?: typedCategories.firstOrNull { it.name.contains(target, ignoreCase = true) }
        }
        val resolvedCategory = explicitCategory ?: localRuleParser.resolveCategory(
            type = type,
            merchant = merchant,
            fullText = "${categoryName.orEmpty()} $merchant $note",
            ruleDefaultCategoryName = categoryName.orEmpty(),
            categories = categories
        )

        val draft = ParsedBillDraft(
            type = type,
            origAmountMicros = origAmountMicros,
            origCurrency = finalCurrency,
            accountId = resolvedAccount?.id,
            accountHint = accountNameOrHint.orEmpty(),
            categoryId = resolvedCategory?.id,
            categoryName = resolvedCategory?.name ?: categoryName.orEmpty(),
            merchant = merchant.ifBlank { note.take(24) },
            note = note,
            timestamp = timestamp,
            confidence = 0.98f,
            parserUsed = "EXTERNAL_PROTOCOL"
        )

        return persistDraftWithDeduplication(
            logId = logId,
            initialLog = initialLog,
            draft = draft,
            matchedStatus = ParseStatus.MATCHED_LOCAL,
            channel = channel,
            sourcePackage = sourcePackage,
            rawText = rawSummary,
            activeAccounts = activeAccounts,
            categories = categories,
            settings = settings,
            showFloatingPrompt = showFloatingPrompt
        )
    }

    private suspend fun persistDraftWithDeduplication(
        logId: Long,
        initialLog: RawCaptureLogEntity,
        draft: ParsedBillDraft,
        matchedStatus: String,
        channel: String,
        sourcePackage: String,
        rawText: String,
        activeAccounts: List<AccountEntity>,
        categories: List<CategoryEntity>,
        settings: AppSettings,
        showFloatingPrompt: Boolean
    ): CaptureHandleResult {
        val accountsById = activeAccounts.associateBy { it.id }
        val categoriesById = categories.associateBy { it.id }

        val resolvedAccountId = draft.accountId?.takeIf { it in accountsById }
            ?: activeAccounts.firstOrNull { it.currencyCode.equals(draft.origCurrency, ignoreCase = true) }?.id
            ?: activeAccounts.firstOrNull()?.id

        if (resolvedAccountId == null) {
            val msg = "未找到可用记账账户"
            rawCaptureLogDao.update(
                initialLog.copy(
                    id = logId,
                    parseStatus = ParseStatus.FAILED,
                    parserUsed = draft.parserUsed,
                    extractedJson = draftToJson(draft),
                    errorMessage = msg
                )
            )
            return CaptureHandleResult(logId = logId, status = ParseStatus.FAILED, draft = draft, message = msg)
        }

        val finalDraft = draft.copy(accountId = resolvedAccountId)
        val draftJson = draftToJson(finalDraft)

        return captureMutex.withLock {
            val windowStart = finalDraft.timestamp - CaptureDeduplicator.DEFAULT_WINDOW_MILLIS
            val windowEnd = finalDraft.timestamp + CaptureDeduplicator.DEFAULT_WINDOW_MILLIS
            val recentTxs = transactionDao.getBetween(windowStart, windowEnd)
            val recentLogs = rawCaptureLogDao.getSince(windowStart - 60_000L)
            val logsByTxId = recentLogs
                .filter { it.linkedTransactionId != null && it.id != logId }
                .associateBy { it.linkedTransactionId!! }

            val dedupMatch = deduplicator.findMatchAndMerge(
                incomingDraft = finalDraft,
                incomingChannel = channel,
                incomingSourcePackage = sourcePackage,
                incomingRawText = rawText,
                recentTransactions = recentTxs,
                recentLogsByTxId = logsByTxId,
                accountsById = accountsById,
                categoriesById = categoriesById
            )

            if (dedupMatch != null) {
                val mergeResult = ledgerService.saveTransaction(dedupMatch.mergedRequest)
                return@withLock mergeResult.fold(
                    onSuccess = { updatedTx ->
                        rawCaptureLogDao.update(
                            initialLog.copy(
                                id = logId,
                                parseStatus = ParseStatus.MERGED_DEDUP,
                                parserUsed = finalDraft.parserUsed,
                                extractedJson = draftJson,
                                linkedTransactionId = updatedTx.id,
                                errorMessage = ""
                            )
                        )
                        CaptureHandleResult(
                            logId = logId,
                            status = ParseStatus.MERGED_DEDUP,
                            transactionId = updatedTx.id,
                            mergedIntoExisting = true,
                            draft = finalDraft,
                            message = dedupMatch.reason
                        )
                    },
                    onFailure = { err ->
                        val errMsg = err.message ?: "融合更新交易失败"
                        rawCaptureLogDao.update(
                            initialLog.copy(
                                id = logId,
                                parseStatus = ParseStatus.FAILED,
                                parserUsed = finalDraft.parserUsed,
                                extractedJson = draftJson,
                                errorMessage = errMsg
                            )
                        )
                        CaptureHandleResult(
                            logId = logId,
                            status = ParseStatus.FAILED,
                            draft = finalDraft,
                            message = errMsg
                        )
                    }
                )
            }

            val shouldPromptFloating = showFloatingPrompt && settings.floatingConfirmEnabled
            val initialTxStatus = if (shouldPromptFloating ||
                (settings.defaultPendingWhenUncertain && finalDraft.confidence < 0.8f)
            ) {
                TransactionStatus.PENDING
            } else {
                TransactionStatus.CONFIRMED
            }

            val saveReq = RecordTransactionRequest(
                type = finalDraft.type,
                timestamp = finalDraft.timestamp,
                origAmountMicros = finalDraft.origAmountMicros,
                origCurrency = finalDraft.origCurrency,
                accountId = resolvedAccountId,
                categoryId = finalDraft.categoryId,
                merchant = finalDraft.merchant,
                note = finalDraft.note,
                sourceType = channel,
                rawEventId = logId,
                status = initialTxStatus
            )

            val saveResult = ledgerService.saveTransaction(saveReq)
            saveResult.fold(
                onSuccess = { savedTx ->
                    rawCaptureLogDao.update(
                        initialLog.copy(
                            id = logId,
                            parseStatus = matchedStatus,
                            parserUsed = finalDraft.parserUsed,
                            extractedJson = draftJson,
                            linkedTransactionId = savedTx.id,
                            errorMessage = ""
                        )
                    )
                    if (shouldPromptFloating) {
                        val accountName = accountsById[resolvedAccountId]?.name.orEmpty()
                        runCatching {
                            floatingPromptLauncher(savedTx.id, finalDraft, accountName, settings)
                        }
                    }
                    CaptureHandleResult(
                        logId = logId,
                        status = matchedStatus,
                        transactionId = savedTx.id,
                        mergedIntoExisting = false,
                        draft = finalDraft,
                        message = "自动记账成功"
                    )
                },
                onFailure = { err ->
                    val errMsg = err.message ?: "写入账单失败"
                    rawCaptureLogDao.update(
                        initialLog.copy(
                            id = logId,
                            parseStatus = ParseStatus.FAILED,
                            parserUsed = finalDraft.parserUsed,
                            extractedJson = draftJson,
                            errorMessage = errMsg
                        )
                    )
                    CaptureHandleResult(
                        logId = logId,
                        status = ParseStatus.FAILED,
                        draft = finalDraft,
                        message = errMsg
                    )
                }
            )
        }
    }

    override suspend fun testParseText(
        title: String,
        content: String,
        sourcePackage: String,
        allowLlm: Boolean
    ): Result<ParsedBillDraft?> {
        if (localRuleParser.isNoise(title, content)) {
            return Result.success(null)
        }

        val rules = parseRuleDao.getEnabled()
        val aliases = accountAliasDao.getEnabled()
        val allAccounts = accountDao.getAll()
        val activeAccounts = allAccounts.filter { !it.archived }.ifEmpty { allAccounts }
        val categories = categoryDao.getAll()

        val input = CaptureInput(
            channel = CaptureChannel.NOTIFICATION,
            sourcePackage = sourcePackage,
            title = title,
            content = content,
            timestamp = System.currentTimeMillis()
        )

        val localDraft = localRuleParser.parse(
            input = input,
            rules = rules,
            aliases = aliases,
            accounts = activeAccounts,
            categories = categories
        )
        if (localDraft != null) {
            return Result.success(localDraft)
        }

        if (!allowLlm) {
            return Result.success(null)
        }

        val settings = settingsProvider()
        return llmBillParser.parse(input, settings, activeAccounts, categories)
    }

    override suspend fun testLlmConnection(): Result<String> {
        val settings = settingsProvider()
        return llmBillParser.testConnection(settings)
    }

    override suspend fun promoteLogToAliasOrRule(
        logId: Long,
        accountId: Long,
        matchPattern: String,
        matchType: String
    ): Result<Unit> = runCatching {
        val log = rawCaptureLogDao.getById(logId)
            ?: throw IllegalArgumentException("未找到捕获日志 #$logId")
        accountDao.getById(accountId)
            ?: throw IllegalArgumentException("未找到目标账户 #$accountId")

        val normalizedType = matchType.trim().uppercase(Locale.ROOT)
        require(normalizedType in VALID_ALIAS_TYPES) {
            "不支持的匹配类型: $matchType"
        }

        val effectivePattern = matchPattern.trim().ifBlank {
            if (normalizedType == AliasMatchType.PACKAGE) log.sourcePackage.trim() else ""
        }
        require(effectivePattern.isNotBlank()) { "匹配模式不能为空" }

        accountAliasDao.insert(
            AccountAliasEntity(
                accountId = accountId,
                matchType = normalizedType,
                pattern = effectivePattern,
                priority = 110,
                enabled = true
            )
        )
        Unit
    }

    override suspend fun saveAlias(alias: AccountAliasEntity): Long {
        return if (alias.id == 0L) {
            accountAliasDao.insert(alias)
        } else {
            accountAliasDao.update(alias)
            alias.id
        }
    }

    override suspend fun deleteAlias(aliasId: Long) {
        accountAliasDao.deleteById(aliasId)
    }

    override suspend fun saveRule(rule: ParseRuleEntity): Long {
        return if (rule.id == 0L) {
            parseRuleDao.insert(rule)
        } else {
            parseRuleDao.update(rule)
            rule.id
        }
    }

    override suspend fun deleteRule(ruleId: Long) {
        parseRuleDao.deleteById(ruleId)
    }

    override suspend fun inspectPermissions(): DevicePermissionReport = permissionInspector()

    override suspend fun tryRootGrantPermissions(): Result<String> = rootGranter()

    private fun isChannelEnabled(channel: String, settings: AppSettings): Boolean = when (channel) {
        CaptureChannel.NOTIFICATION -> settings.notificationCaptureEnabled
        CaptureChannel.SMS -> settings.smsCaptureEnabled
        CaptureChannel.AUTO_ACCOUNTING -> settings.autoAccountingBridgeEnabled
        else -> true
    }

    private fun resolveExternalAccount(
        accountNameOrHint: String?,
        origCurrency: String?,
        accounts: List<AccountEntity>,
        aliases: List<AccountAliasEntity>
    ): AccountEntity? {
        if (accounts.isEmpty()) return null
        val hint = accountNameOrHint?.trim().orEmpty()
        if (hint.isNotEmpty()) {
            accounts.firstOrNull { it.name.equals(hint, ignoreCase = true) }?.let { return it }
            accounts.firstOrNull { it.name.contains(hint, ignoreCase = true) }?.let { return it }

            val activeIds = accounts.map { it.id }.toSet()
            val matchedAlias = aliases
                .filter { it.enabled && it.accountId in activeIds }
                .sortedByDescending { it.priority }
                .firstOrNull { alias ->
                    when (alias.matchType.uppercase(Locale.ROOT)) {
                        AliasMatchType.REGEX -> runCatching {
                            Regex(alias.pattern, RegexOption.IGNORE_CASE).containsMatchIn(hint)
                        }.getOrDefault(false)
                        else -> hint.contains(alias.pattern, ignoreCase = true) ||
                            alias.pattern.equals(hint, ignoreCase = true)
                    }
                }
            if (matchedAlias != null) {
                accounts.firstOrNull { it.id == matchedAlias.accountId }?.let { return it }
            }
        }

        if (!origCurrency.isNullOrBlank()) {
            accounts.firstOrNull { it.currencyCode.equals(origCurrency, ignoreCase = true) }?.let { return it }
        }
        return accounts.firstOrNull()
    }

    private fun draftToJson(draft: ParsedBillDraft): String = buildJsonObject {
        put("type", draft.type)
        put("origAmountMicros", draft.origAmountMicros)
        put("origCurrency", draft.origCurrency)
        put("accountId", draft.accountId)
        put("accountHint", draft.accountHint)
        put("categoryId", draft.categoryId)
        put("categoryName", draft.categoryName)
        put("merchant", draft.merchant)
        put("note", draft.note)
        put("timestamp", draft.timestamp)
        put("confidence", draft.confidence)
        put("parserUsed", draft.parserUsed)
    }.toString()

    companion object {
        private val VALID_ALIAS_TYPES = setOf(
            AliasMatchType.PACKAGE,
            AliasMatchType.CARD_LAST4,
            AliasMatchType.KEYWORD,
            AliasMatchType.REGEX
        )

        fun defaultUnsupportedPermissionReport(): DevicePermissionReport = DevicePermissionReport(
            isRooted = false,
            hasLsposedHint = false,
            notificationListenerEnabled = false,
            smsPermissionGranted = false,
            overlayPermissionGranted = false,
            ignoringBatteryOptimizations = false,
            postNotificationGranted = false,
            manufacturer = "JVM",
            osDisplay = "JVM Test Environment"
        )
    }
}
