package com.kairoledger.app.capture

import android.content.Context
import com.kairoledger.app.capture.screen.ScreenPageKind
import com.kairoledger.app.capture.screen.ScreenParseOutcome
import com.kairoledger.app.capture.screen.ScreenSnapshot
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
import com.kairoledger.app.data.db.TransactionEntity
import com.kairoledger.app.data.db.TransactionStatus
import com.kairoledger.app.data.db.TransactionType
import com.kairoledger.app.data.prefs.AppSettings
import com.kairoledger.app.data.prefs.SettingsStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import java.util.Locale
import kotlin.math.abs

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
    },
    private val richFloatingPromptLauncher: (suspend (
        transactionId: Long,
        draft: ParsedBillDraft,
        accountName: String,
        accountAmountMicros: Long,
        accountCurrency: String,
        sourceBadge: String,
        settings: AppSettings
    ) -> Unit)? = null
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
        rootGranter = { AndroidPermissionHelper.tryRootGrantPermissions(context.applicationContext) },
        richFloatingPromptLauncher = { txId, draft, accountName, accountAmountMicros, accountCurrency, sourceBadge, settings ->
            FloatingConfirmService.showOrNotify(
                context = context.applicationContext,
                transactionId = txId,
                draft = draft,
                accountName = accountName,
                autoConfirmSeconds = settings.floatingAutoConfirmSeconds,
                accountAmountMicros = accountAmountMicros,
                accountCurrency = accountCurrency,
                sourceBadge = sourceBadge
            )
        }
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

    /**
     * 供 KairoAccessibilityService 调用的屏幕账单页 / 支付成功页识别入口。
     */
    suspend fun handleScreenCapture(
        snapshot: ScreenSnapshot,
        outcome: ScreenParseOutcome
    ): CaptureHandleResult {
        val title = buildScreenLogTitle(snapshot.packageName, outcome.pageKind)
        val rawSummary = outcome.summaryText.trim().ifBlank {
            snapshot.texts.asSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .joinToString("\n")
                .take(1500)
        }
        val cleanOrderNo = outcome.orderNo.trim().ifBlank {
            outcome.draft?.externalOrderNo.orEmpty().trim()
        }
        val logContent = if (cleanOrderNo.isNotBlank() && !rawSummary.contains(cleanOrderNo)) {
            if (rawSummary.isBlank()) "单号: $cleanOrderNo" else "$rawSummary\n单号: $cleanOrderNo"
        } else {
            rawSummary
        }

        val initialLog = RawCaptureLogEntity(
            channel = CaptureChannel.SCREEN,
            sourcePackage = snapshot.packageName,
            title = title,
            content = logContent,
            capturedAt = snapshot.capturedAtMillis,
            parseStatus = ParseStatus.FAILED
        )
        val logId = rawCaptureLogDao.insert(initialLog)
        val settings = runCatching { settingsProvider() }.getOrDefault(AppSettings())

        if (!settings.accessibilityCaptureEnabled) {
            val msg = "采集通道 ${CaptureChannel.SCREEN} 已关闭"
            rawCaptureLogDao.update(initialLog.copy(id = logId, parseStatus = ParseStatus.IGNORED, errorMessage = msg))
            return CaptureHandleResult(logId = logId, status = ParseStatus.IGNORED, message = msg)
        }
        if (outcome.pageKind == ScreenPageKind.PAY_SUCCESS && !settings.accessibilityPaySuccessEnabled) {
            val msg = "支付成功页自动唤起开关已关闭"
            rawCaptureLogDao.update(initialLog.copy(id = logId, parseStatus = ParseStatus.IGNORED, errorMessage = msg))
            return CaptureHandleResult(logId = logId, status = ParseStatus.IGNORED, message = msg)
        }
        if (outcome.pageKind == ScreenPageKind.BILL_DETAIL && !settings.accessibilityBillDetailEnabled) {
            val msg = "历史账单详情页补记开关已关闭"
            rawCaptureLogDao.update(initialLog.copy(id = logId, parseStatus = ParseStatus.IGNORED, errorMessage = msg))
            return CaptureHandleResult(logId = logId, status = ParseStatus.IGNORED, message = msg)
        }
        if (outcome.ignoredReason.isNotBlank()) {
            rawCaptureLogDao.update(
                initialLog.copy(
                    id = logId,
                    parseStatus = ParseStatus.IGNORED,
                    errorMessage = outcome.ignoredReason
                )
            )
            return CaptureHandleResult(
                logId = logId,
                status = ParseStatus.IGNORED,
                message = outcome.ignoredReason
            )
        }

        val allAccounts = accountDao.getAll()
        val activeAccounts = allAccounts.filter { !it.archived }.ifEmpty { allAccounts }
        val categories = categoryDao.getAll()

        var draft = outcome.draft
        var matchedStatus = ParseStatus.MATCHED_LOCAL
        var llmErrorMessage: String? = null

        if (draft == null && outcome.shouldFallbackToLlm && settings.llmEnabled && settings.llmApiKey.isNotBlank()) {
            val screenInput = CaptureInput(
                channel = CaptureChannel.SCREEN,
                sourcePackage = snapshot.packageName,
                title = title,
                content = logContent,
                timestamp = snapshot.capturedAtMillis
            )
            val llmResult = llmBillParser.parse(screenInput, settings, activeAccounts, categories)
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

        val rawDraft = draft
        if (rawDraft == null || rawDraft.origAmountMicros <= 0L) {
            val finalStatus = when {
                llmErrorMessage == "AI 判定非资金动账" -> ParseStatus.IGNORED
                outcome.pageKind == ScreenPageKind.UNKNOWN && rawDraft == null -> ParseStatus.IGNORED
                else -> ParseStatus.FAILED
            }
            val errorMsg = llmErrorMessage ?: when {
                rawDraft != null -> "屏幕识别金额无效"
                outcome.pageKind == ScreenPageKind.UNKNOWN -> "非支付或账单详情页面"
                else -> "未从屏幕页面识别到有效账单"
            }
            rawCaptureLogDao.update(
                initialLog.copy(
                    id = logId,
                    parseStatus = finalStatus,
                    errorMessage = errorMsg
                )
            )
            return CaptureHandleResult(logId = logId, status = finalStatus, message = errorMsg)
        }

        val resolvedDraft = if (cleanOrderNo.isNotBlank() && rawDraft.externalOrderNo.isBlank()) {
            rawDraft.copy(externalOrderNo = cleanOrderNo)
        } else {
            rawDraft
        }
        val sourceBadge = when (outcome.pageKind) {
            ScreenPageKind.PAY_SUCCESS -> "支付页识别"
            ScreenPageKind.BILL_DETAIL -> "账单详情识别"
            ScreenPageKind.UNKNOWN -> "页面识别"
        }

        return persistDraftWithDeduplication(
            logId = logId,
            initialLog = initialLog,
            draft = resolvedDraft,
            matchedStatus = matchedStatus,
            channel = CaptureChannel.SCREEN,
            sourcePackage = snapshot.packageName,
            rawText = "$title\n$logContent".trim(),
            activeAccounts = activeAccounts,
            categories = categories,
            settings = settings,
            showFloatingPrompt = true,
            screenPageKind = outcome.pageKind,
            screenOrderNo = resolvedDraft.externalOrderNo.ifBlank { cleanOrderNo },
            screenDedupFingerprint = outcome.dedupFingerprint,
            capturedAtMillis = snapshot.capturedAtMillis,
            sourceBadge = sourceBadge
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
        showFloatingPrompt: Boolean,
        screenPageKind: ScreenPageKind? = null,
        screenOrderNo: String = "",
        screenDedupFingerprint: String = "",
        capturedAtMillis: Long = initialLog.capturedAt,
        sourceBadge: String = ""
    ): CaptureHandleResult {
        val accountsById = activeAccounts.associateBy { it.id }
        val categoriesById = categories.associateBy { it.id }

        val resolvedAccountId = draft.accountId?.takeIf { it in accountsById }
            ?: if (draft.manualAccountAmountMicros != null &&
                !draft.origCurrency.equals(settings.baseCurrency, ignoreCase = true)
            ) {
                activeAccounts.firstOrNull { it.currencyCode.equals(settings.baseCurrency, ignoreCase = true) }?.id
            } else {
                null
            }
            ?: activeAccounts.firstOrNull { it.currencyCode.equals(draft.origCurrency, ignoreCase = true) }?.id
            ?: activeAccounts.firstOrNull()?.id

        if (resolvedAccountId == null) {
            val msg = "未找到可用记账账户"
            rawCaptureLogDao.update(
                initialLog.copy(
                    id = logId,
                    parseStatus = ParseStatus.FAILED,
                    parserUsed = draft.parserUsed,
                    extractedJson = draftToJson(draft, screenOrderNo, screenDedupFingerprint, screenPageKind),
                    errorMessage = msg
                )
            )
            return CaptureHandleResult(logId = logId, status = ParseStatus.FAILED, draft = draft, message = msg)
        }

        val effectiveOrderNo = screenOrderNo.trim().ifBlank { draft.externalOrderNo.trim() }
        val finalDraft = draft.copy(
            accountId = resolvedAccountId,
            externalOrderNo = effectiveOrderNo
        )
        val draftJson = draftToJson(finalDraft, effectiveOrderNo, screenDedupFingerprint, screenPageKind)
        val finalNote = appendOrderNoIfMissing(finalDraft.note, effectiveOrderNo)

        return captureMutex.withLock {
            // 1. 强单号防重（若关联交易仍存在，绝不重复记账或弹窗；若用户已手工删掉则允许重新补记）
            if (effectiveOrderNo.isNotBlank()) {
                val recentAllLogs = rawCaptureLogDao.getAll().take(ORDER_DEDUP_SCAN_LIMIT)
                for (candidate in recentAllLogs) {
                    if (candidate.id == logId) continue
                    val linkedTxId = candidate.linkedTransactionId ?: continue
                    if (!logMatchesOrderNo(candidate, effectiveOrderNo)) continue
                    val existingTx = transactionDao.getById(linkedTxId)
                    if (existingTx != null) {
                        val dedupMsg = "同单号已记账（交易 #${existingTx.id}），跳过重复记录"
                        rawCaptureLogDao.update(
                            initialLog.copy(
                                id = logId,
                                parseStatus = ParseStatus.MERGED_DEDUP,
                                parserUsed = finalDraft.parserUsed,
                                extractedJson = draftJson,
                                linkedTransactionId = existingTx.id,
                                errorMessage = ""
                            )
                        )
                        return@withLock CaptureHandleResult(
                            logId = logId,
                            status = ParseStatus.MERGED_DEDUP,
                            transactionId = existingTx.id,
                            mergedIntoExisting = true,
                            draft = finalDraft,
                            message = dedupMsg
                        )
                    }
                }
            }

            // 2. 历史账单详情页（距当前捕获超过 2 分钟）：检查前后 120s 内同金额/币种交易防重
            val isHistoricalBillDetail = screenPageKind == ScreenPageKind.BILL_DETAIL &&
                abs(capturedAtMillis - finalDraft.timestamp) > HISTORICAL_BILL_THRESHOLD_MILLIS
            if (isHistoricalBillDetail) {
                val historicalWindowTxs = transactionDao.getBetween(
                    finalDraft.timestamp - HISTORICAL_DEDUP_WINDOW_MILLIS,
                    finalDraft.timestamp + HISTORICAL_DEDUP_WINDOW_MILLIS
                )
                val existingHistoricalTx = historicalWindowTxs.firstOrNull { tx ->
                    tx.type == finalDraft.type &&
                        tx.origCurrency.equals(finalDraft.origCurrency, ignoreCase = true) &&
                        tx.origAmountMicros == finalDraft.origAmountMicros
                }
                if (existingHistoricalTx != null) {
                    val dedupMsg = "历史账单已存在相同金额/币种记录（交易 #${existingHistoricalTx.id}），跳过重复补记"
                    rawCaptureLogDao.update(
                        initialLog.copy(
                            id = logId,
                            parseStatus = ParseStatus.MERGED_DEDUP,
                            parserUsed = finalDraft.parserUsed,
                            extractedJson = draftJson,
                            linkedTransactionId = existingHistoricalTx.id,
                            errorMessage = ""
                        )
                    )
                    return@withLock CaptureHandleResult(
                        logId = logId,
                        status = ParseStatus.MERGED_DEDUP,
                        transactionId = existingHistoricalTx.id,
                        mergedIntoExisting = true,
                        draft = finalDraft,
                        message = dedupMsg
                    )
                }
            }

            val windowStart = finalDraft.timestamp - CaptureDeduplicator.DEFAULT_WINDOW_MILLIS
            val windowEnd = finalDraft.timestamp + CaptureDeduplicator.DEFAULT_WINDOW_MILLIS
            val recentTxs = transactionDao.getBetween(windowStart, windowEnd)
            val recentLogs = rawCaptureLogDao.getSince(windowStart - 60_000L)
            val logsByTxId = recentLogs
                .filter { it.linkedTransactionId != null && it.id != logId }
                .associateBy { it.linkedTransactionId!! }

            // 3. 60s 跨渠道去重与信息融合
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
                val enrichedRequest = enrichMergedRequest(
                    base = dedupMatch.mergedRequest,
                    incomingDraft = finalDraft,
                    orderNo = effectiveOrderNo,
                    accountsById = accountsById
                )
                val mergeResult = ledgerService.saveTransaction(enrichedRequest)
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

            // 3b. 同应用支付成功页刚落账后用户立即点开“账单详情页”（无单号场景的实时去重融合）
            if (screenPageKind == ScreenPageKind.BILL_DETAIL && !isHistoricalBillDetail) {
                val existingPaySuccessTx = findRecentScreenPaySuccessMatch(
                    finalDraft = finalDraft,
                    sourcePackage = sourcePackage,
                    recentTxs = recentTxs,
                    logsByTxId = logsByTxId
                )
                if (existingPaySuccessTx != null) {
                    val enrichedTxId = maybeEnrichPaySuccessTx(
                        existing = existingPaySuccessTx,
                        incomingDraft = finalDraft,
                        orderNo = effectiveOrderNo,
                        accountsById = accountsById
                    )
                    rawCaptureLogDao.update(
                        initialLog.copy(
                            id = logId,
                            parseStatus = ParseStatus.MERGED_DEDUP,
                            parserUsed = finalDraft.parserUsed,
                            extractedJson = draftJson,
                            linkedTransactionId = enrichedTxId,
                            errorMessage = ""
                        )
                    )
                    return@withLock CaptureHandleResult(
                        logId = logId,
                        status = ParseStatus.MERGED_DEDUP,
                        transactionId = enrichedTxId,
                        mergedIntoExisting = true,
                        draft = finalDraft,
                        message = "与刚捕获的支付成功页交易 #$enrichedTxId 自动融合"
                    )
                }
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
                manualAccountAmountMicros = finalDraft.manualAccountAmountMicros,
                categoryId = finalDraft.categoryId,
                merchant = finalDraft.merchant,
                note = finalNote,
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
                            if (richFloatingPromptLauncher != null) {
                                richFloatingPromptLauncher.invoke(
                                    savedTx.id,
                                    finalDraft,
                                    accountName,
                                    savedTx.accountAmountMicros,
                                    savedTx.accountCurrency,
                                    sourceBadge,
                                    settings
                                )
                            } else {
                                floatingPromptLauncher(savedTx.id, finalDraft, accountName, settings)
                            }
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
        CaptureChannel.SCREEN -> settings.accessibilityCaptureEnabled
        CaptureChannel.AUTO_ACCOUNTING -> settings.autoAccountingBridgeEnabled
        else -> true
    }

    private fun buildScreenLogTitle(packageName: String, pageKind: ScreenPageKind): String {
        val appLabel = when {
            packageName == "com.tencent.mm" -> "微信"
            packageName == "com.eg.android.AlipayGphone" -> "支付宝"
            packageName == "com.unionpay" -> "云闪付"
            packageName.contains("cmbchina", ignoreCase = true) -> "招商银行"
            packageName.contains("icbc", ignoreCase = true) -> "工商银行"
            packageName.contains("chinamworld", ignoreCase = true) -> "建设银行"
            packageName.contains("bankabc", ignoreCase = true) -> "农业银行"
            packageName.contains("pingan", ignoreCase = true) -> "平安银行"
            packageName.contains("wise", ignoreCase = true) -> "Wise"
            packageName.contains("paypal", ignoreCase = true) -> "PayPal"
            packageName.contains("revolut", ignoreCase = true) -> "Revolut"
            else -> ""
        }
        val pageLabel = when (pageKind) {
            ScreenPageKind.PAY_SUCCESS -> "支付成功页"
            ScreenPageKind.BILL_DETAIL -> "账单详情页"
            ScreenPageKind.UNKNOWN -> "屏幕页面"
        }
        return if (appLabel.isNotBlank()) "$appLabel$pageLabel" else pageLabel
    }

    private fun appendOrderNoIfMissing(note: String, orderNo: String): String {
        val cleanNote = note.trim()
        val cleanOrder = orderNo.trim()
        if (cleanOrder.isBlank() || cleanNote.contains(cleanOrder)) return cleanNote
        return if (cleanNote.isBlank()) "单号:$cleanOrder" else "$cleanNote | 单号:$cleanOrder"
    }

    private fun logMatchesOrderNo(log: RawCaptureLogEntity, orderNo: String): Boolean {
        val extracted = extractOrderNoFromJson(log.extractedJson)
        if (extracted.isNotBlank() && extracted.equals(orderNo, ignoreCase = true)) {
            return true
        }
        return orderNo.length >= 8 && log.content.contains(orderNo, ignoreCase = true)
    }

    private fun extractOrderNoFromJson(rawJson: String): String {
        if (rawJson.isBlank()) return ""
        return runCatching {
            val obj = LENIENT_JSON.parseToJsonElement(rawJson) as? JsonObject ?: return ""
            val primary = (obj["externalOrderNo"] as? JsonPrimitive)?.contentOrNull.orEmpty().trim()
            if (primary.isNotBlank()) return primary
            (obj["orderNo"] as? JsonPrimitive)?.contentOrNull.orEmpty().trim()
        }.getOrDefault("")
    }

    private fun enrichMergedRequest(
        base: RecordTransactionRequest,
        incomingDraft: ParsedBillDraft,
        orderNo: String,
        accountsById: Map<Long, AccountEntity>
    ): RecordTransactionRequest {
        val targetAccCurrency = accountsById[base.accountId]?.currencyCode.orEmpty()
        val enrichedManualAmount = if (
            base.manualAccountAmountMicros == null &&
            incomingDraft.manualAccountAmountMicros != null &&
            incomingDraft.manualAccountAmountMicros > 0L &&
            targetAccCurrency.isNotBlank() &&
            !targetAccCurrency.equals(base.origCurrency, ignoreCase = true)
        ) {
            incomingDraft.manualAccountAmountMicros
        } else {
            base.manualAccountAmountMicros
        }
        val enrichedNote = appendOrderNoIfMissing(base.note, orderNo)
        return base.copy(
            manualAccountAmountMicros = enrichedManualAmount,
            note = enrichedNote
        )
    }

    private fun findRecentScreenPaySuccessMatch(
        finalDraft: ParsedBillDraft,
        sourcePackage: String,
        recentTxs: List<TransactionEntity>,
        logsByTxId: Map<Long, RawCaptureLogEntity>
    ): TransactionEntity? {
        return recentTxs.firstOrNull { tx ->
            if (tx.sourceType != CaptureChannel.SCREEN) return@firstOrNull false
            if (tx.type != finalDraft.type) return@firstOrNull false
            if (!tx.origCurrency.equals(finalDraft.origCurrency, ignoreCase = true)) return@firstOrNull false
            if (tx.origAmountMicros != finalDraft.origAmountMicros) return@firstOrNull false
            val linkedLog = logsByTxId[tx.id] ?: return@firstOrNull false
            if (!linkedLog.sourcePackage.equals(sourcePackage, ignoreCase = true)) return@firstOrNull false
            if (!linkedLog.title.contains("支付成功")) return@firstOrNull false
            val a = tx.merchant.trim().lowercase(Locale.ROOT)
            val b = finalDraft.merchant.trim().lowercase(Locale.ROOT)
            a.isBlank() || b.isBlank() || a == b || a.contains(b) || b.contains(a)
        }
    }

    private suspend fun maybeEnrichPaySuccessTx(
        existing: TransactionEntity,
        incomingDraft: ParsedBillDraft,
        orderNo: String,
        accountsById: Map<Long, AccountEntity>
    ): Long {
        val updatedNote = appendOrderNoIfMissing(existing.note, orderNo)
        val newAccountId = if (incomingDraft.accountHint.isNotBlank() &&
            incomingDraft.accountId != null &&
            !existing.note.contains(incomingDraft.accountHint)
        ) {
            incomingDraft.accountId
        } else {
            existing.accountId
        }
        val targetAccCurrency = accountsById[newAccountId]?.currencyCode ?: existing.accountCurrency
        val newManualAccountAmount = if (
            incomingDraft.manualAccountAmountMicros != null &&
            incomingDraft.manualAccountAmountMicros > 0L &&
            !targetAccCurrency.equals(existing.origCurrency, ignoreCase = true)
        ) {
            incomingDraft.manualAccountAmountMicros
        } else {
            null
        }
        val betterMerchant = if (existing.merchant.isBlank() && incomingDraft.merchant.isNotBlank()) {
            incomingDraft.merchant
        } else {
            existing.merchant
        }
        val needsUpdate = updatedNote != existing.note ||
            newAccountId != existing.accountId ||
            newManualAccountAmount != null ||
            betterMerchant != existing.merchant
        if (!needsUpdate) return existing.id

        val req = RecordTransactionRequest(
            existingId = existing.id,
            type = existing.type,
            timestamp = minOf(existing.timestamp, incomingDraft.timestamp),
            origAmountMicros = existing.origAmountMicros,
            origCurrency = existing.origCurrency,
            accountId = newAccountId,
            manualAccountAmountMicros = newManualAccountAmount ?: if (
                existing.origCurrency != targetAccCurrency &&
                existing.accountCurrency == targetAccCurrency &&
                existing.accountAmountMicros > 0L
            ) {
                existing.accountAmountMicros
            } else {
                null
            },
            toAccountId = existing.toAccountId,
            manualToAmountMicros = existing.toAmountMicros,
            categoryId = existing.categoryId ?: incomingDraft.categoryId,
            merchant = betterMerchant,
            note = updatedNote,
            tags = existing.tags,
            reimburseStatus = existing.reimburseStatus,
            sourceType = existing.sourceType,
            rawEventId = existing.rawEventId,
            status = existing.status
        )
        return ledgerService.saveTransaction(req).getOrNull()?.id ?: existing.id
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

    private fun draftToJson(
        draft: ParsedBillDraft,
        extraOrderNo: String = "",
        dedupFingerprint: String = "",
        pageKind: ScreenPageKind? = null
    ): String = buildJsonObject {
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
        val effectiveOrderNo = draft.externalOrderNo.ifBlank { extraOrderNo }.trim()
        if (effectiveOrderNo.isNotBlank()) {
            put("externalOrderNo", effectiveOrderNo)
        }
        draft.manualAccountAmountMicros?.let {
            put("manualAccountAmountMicros", it)
        }
        if (dedupFingerprint.isNotBlank()) {
            put("dedupFingerprint", dedupFingerprint)
        }
        if (pageKind != null) {
            put("pageKind", pageKind.name)
        }
    }.toString()

    companion object {
        private const val ORDER_DEDUP_SCAN_LIMIT = 500
        private const val HISTORICAL_BILL_THRESHOLD_MILLIS = 120_000L
        private const val HISTORICAL_DEDUP_WINDOW_MILLIS = 120_000L

        private val LENIENT_JSON = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

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
            osDisplay = "JVM Test Environment",
            accessibilityServiceEnabled = false
        )
    }
}
