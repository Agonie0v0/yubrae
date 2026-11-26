package com.yubrae.app.capture.rules

import com.yubrae.app.capture.LocalRuleParser
import com.yubrae.app.core.contract.CaptureInput
import com.yubrae.app.core.contract.ParsedBillDraft
import com.yubrae.app.core.model.CurrencyCatalog
import com.yubrae.app.core.model.MoneyAmount
import com.yubrae.app.data.db.AccountAliasEntity
import com.yubrae.app.data.db.AccountEntity
import com.yubrae.app.data.db.AccountGroup
import com.yubrae.app.data.db.AliasMatchType
import com.yubrae.app.data.db.CaptureChannel
import com.yubrae.app.data.db.CategoryEntity
import com.yubrae.app.data.db.TransactionType
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.mozilla.javascript.ClassShutter
import org.mozilla.javascript.Context
import org.mozilla.javascript.ContextAction
import org.mozilla.javascript.ContextFactory
import org.mozilla.javascript.Function
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.Undefined
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

class JsTimeoutError(message: String) : Error(message)

/**
 * 安全沙盒化的 Rhino JS 规则执行引擎（纯 JVM 可测，不依赖 Android Framework）。
 * - 独立 ContextFactory 实例（不修改 ContextFactory.initGlobal）
 * - 强制 optimizationLevel = -1（Android ART 必备）与 ClassShutter { false }
 * - initSafeStandardObjects(null, false) 屏蔽 Java/Packages/getClass
 * - 指令计数超时熔断抛出 java.lang.Error 子类 JsTimeoutError，防止规则脚本 try/catch 吞掉死循环
 */
class SandboxedJsRuleEngine(
    private val localRuleParser: LocalRuleParser = LocalRuleParser(),
    private val ruleTimeoutMillis: Long = 250L
) {
    private val effectiveTimeoutMillis = ruleTimeoutMillis.coerceAtLeast(10L)
    private val sandboxFactory = SandboxedContextFactory(effectiveTimeoutMillis)
    private val engineLock = Any()

    @Volatile
    private var activeBundle: JsRuleBundle? = null
    private var compiledState: CompiledBundleState? = null

    fun updateBundle(bundle: JsRuleBundle?) {
        synchronized(engineLock) {
            activeBundle = bundle
            compiledState = null
        }
    }

    fun currentBundle(): JsRuleBundle? = activeBundle

    fun matchSms(sender: String, body: String, timestampMillis: Long): JsRuleRawResult? {
        if (!body.contains('【') || !body.contains('】')) return null
        val bundle = activeBundle ?: return null
        if (bundle.smsRules.isEmpty()) return null

        val extractedBanks = extractSmsBankTags(body)
        val payload = buildJsonObject {
            put("sender", sender)
            put("body", body)
            put("t", timestampMillis)
        }.toString()

        val triedPaths = HashSet<String>()
        val candidates = buildList {
            for (bank in extractedBanks) {
                val indexed = bundle.smsRulesByBank[bank]
                if (indexed != null) {
                    for (entry in indexed) {
                        if (triedPaths.add(entry.meta.path)) add(entry)
                    }
                }
            }
            for (entry in bundle.unindexedSmsRules) {
                if (triedPaths.add(entry.meta.path)) add(entry)
            }
            for (entry in bundle.smsRules) {
                if (triedPaths.add(entry.meta.path)) add(entry)
            }
        }

        return evaluateCandidateRules(bundle, candidates, payload)
    }

    fun matchNotice(
        packageName: String,
        title: String,
        content: String,
        timestampMillis: Long
    ): JsRuleRawResult? {
        val bundle = activeBundle ?: return null
        val pkg = packageName.trim()
        if (pkg.isEmpty()) return null

        val candidates = if (pkg == "*") {
            bundle.noticeRulesByPackage.values.flatten()
        } else {
            findRulesByPackage(bundle.noticeRulesByPackage, pkg)
        }
        if (candidates.isEmpty()) return null

        val payload = buildJsonObject {
            put("title", title)
            put("text", content)
            put("t", timestampMillis)
        }.toString()

        return evaluateCandidateRules(bundle, candidates, payload)
    }

    fun matchOcr(packageName: String, screenText: String): JsRuleRawResult? {
        if (screenText.isBlank()) return null
        val bundle = activeBundle ?: return null
        val pkg = packageName.trim()
        if (pkg.isEmpty()) return null

        val candidates = if (pkg == "*") {
            bundle.ocrRulesByPackage.values.flatten()
        } else {
            findRulesByPackage(bundle.ocrRulesByPackage, pkg)
        }
        if (candidates.isEmpty()) return null

        return evaluateCandidateRules(bundle, candidates, screenText)
    }

    fun evalCategory(raw: JsRuleRawResult, timestampMillis: Long): String? {
        val bundle = activeBundle ?: return null
        if (bundle.categoryJs.isBlank()) return null

        val billType = when (raw.type.trim().lowercase(Locale.ROOT)) {
            "income" -> "Income"
            "transfer" -> "Transfer"
            else -> "Expend"
        }
        val localTime = Instant.ofEpochMilli(timestampMillis)
            .atZone(ZoneId.systemDefault())
            .toLocalTime()
        val timeHHmm = String.format(Locale.US, "%02d:%02d", localTime.hour, localTime.minute)

        return synchronized(engineLock) {
            val current = activeBundle ?: return@synchronized null
            if (current.categoryJs.isBlank()) return@synchronized null
            sandboxFactory.call(ContextAction { cx ->
                val timedCx = configureContext(cx)
                val state = getOrInitStateLocked(timedCx, current) ?: return@ContextAction null
                val categoryHandle = ensureCategoryCompiledLocked(timedCx, state)
                    ?: return@ContextAction null
                val (catObj, catFn) = categoryHandle
                try {
                    armTimeout(timedCx, effectiveTimeoutMillis)
                    val res = catFn.call(
                        timedCx,
                        state.scope,
                        catObj,
                        arrayOf(
                            raw.money,
                            billType,
                            raw.shopName,
                            raw.shopItem,
                            timeHHmm,
                            ""
                        )
                    )
                    extractCategoryName(res)
                } catch (_: JsTimeoutError) {
                    null
                } catch (_: Throwable) {
                    null
                } finally {
                    disarmTimeout(timedCx)
                }
            })
        }
    }

    fun parseIncoming(
        input: CaptureInput,
        aliases: List<AccountAliasEntity>,
        accounts: List<AccountEntity>,
        categories: List<CategoryEntity>,
        enableCategoryJs: Boolean = true
    ): ParsedBillDraft? {
        val raw = when {
            input.channel.equals(CaptureChannel.SMS, ignoreCase = true) -> {
                val body = input.content.ifBlank { input.title }
                matchSms(sender = input.title, body = body, timestampMillis = input.timestamp)
            }
            input.channel.equals(CaptureChannel.NOTIFICATION, ignoreCase = true) -> {
                matchNotice(
                    packageName = input.sourcePackage,
                    title = input.title,
                    content = input.content,
                    timestampMillis = input.timestamp
                )
            }
            input.channel.equals(CaptureChannel.SCREEN, ignoreCase = true) -> {
                val screenText = if (input.content.isNotBlank()) input.content else input.title
                matchOcr(packageName = input.sourcePackage, screenText = screenText)
            }
            else -> null
        } ?: return null

        val bundleSource = activeBundle?.source ?: JsRuleSource.BUILTIN
        return buildDraftFromRaw(
            raw = raw,
            bundleSource = bundleSource,
            input = input,
            aliases = aliases,
            accounts = accounts,
            categories = categories,
            enableCategoryJs = enableCategoryJs
        )
    }

    fun parseScreenOcr(
        packageName: String,
        screenText: String,
        timestampMillis: Long,
        aliases: List<AccountAliasEntity>,
        accounts: List<AccountEntity>,
        categories: List<CategoryEntity>,
        enableCategoryJs: Boolean = true
    ): ParsedBillDraft? {
        val raw = matchOcr(packageName = packageName, screenText = screenText) ?: return null
        val bundleSource = activeBundle?.source ?: JsRuleSource.BUILTIN
        val input = CaptureInput(
            channel = CaptureChannel.SCREEN,
            sourcePackage = packageName,
            title = "",
            content = screenText,
            timestamp = timestampMillis
        )
        return buildDraftFromRaw(
            raw = raw,
            bundleSource = bundleSource,
            input = input,
            aliases = aliases,
            accounts = accounts,
            categories = categories,
            enableCategoryJs = enableCategoryJs
        )
    }

    private fun buildDraftFromRaw(
        raw: JsRuleRawResult,
        bundleSource: JsRuleSource,
        input: CaptureInput,
        aliases: List<AccountAliasEntity>,
        accounts: List<AccountEntity>,
        categories: List<CategoryEntity>,
        enableCategoryJs: Boolean
    ): ParsedBillDraft? {
        val mappedType = when (raw.type.trim().lowercase(Locale.ROOT)) {
            "expend", "expense" -> TransactionType.EXPENSE
            "income" -> TransactionType.INCOME
            "transfer" -> TransactionType.TRANSFER
            else -> return null
        }
        val origAmountMicros = doubleToMicros(raw.money) ?: return null
        if (origAmountMicros <= 0L) return null

        val currency = localRuleParser.normalizeCurrencyToken(
            raw = raw.currency.ifBlank { CurrencyCatalog.DEFAULT_BASE_CURRENCY },
            defaultCode = CurrencyCatalog.DEFAULT_BASE_CURRENCY
        ).ifBlank { CurrencyCatalog.DEFAULT_BASE_CURRENCY }

        val resolvedTimestamp = resolveTimestamp(raw.time, input.timestamp)
        val activeAccounts = accounts.filter { !it.archived }.ifEmpty { accounts }

        val rawFrom = raw.accountNameFrom.trim()
        val rawTo = raw.accountNameTo.trim()
        val primaryAccountText = if (mappedType == TransactionType.INCOME && rawFrom.isBlank()) {
            rawTo
        } else {
            rawFrom
        }

        val combinedText = listOf(
            input.title,
            input.content,
            raw.shopName,
            raw.shopItem,
            rawFrom,
            rawTo,
            raw.channel
        ).filter { it.isNotBlank() }.joinToString(" ")

        val extractedTail = extractLast4FromHint(primaryAccountText)
            .ifBlank { localRuleParser.extractAccountHint(combinedText) }
        val accountHint = primaryAccountText.ifBlank { extractedTail }

        val accountId = resolveAccountFromHint(
            accountText = primaryAccountText,
            last4 = extractedTail,
            aliases = aliases,
            accounts = activeAccounts
        ) ?: localRuleParser.resolveAccountId(
            input = input,
            fullText = combinedText,
            accountHint = extractedTail.ifBlank { accountHint },
            currency = currency,
            aliases = aliases,
            accounts = activeAccounts
        )

        var toAccountId: Long? = null
        var toAccountHint = ""
        if (mappedType == TransactionType.TRANSFER && rawTo.isNotBlank()) {
            toAccountHint = rawTo
            val toLast4 = extractLast4FromHint(rawTo)
            toAccountId = resolveAccountFromHint(
                accountText = rawTo,
                last4 = toLast4,
                aliases = aliases,
                accounts = activeAccounts
            )
        }

        val merchant = raw.shopName.trim().ifBlank { raw.shopItem.trim().take(40) }
        val categoryHaystack = listOf(raw.shopName, raw.shopItem, input.title, input.content)
            .filter { it.isNotBlank() }
            .joinToString(" ")
        var resolvedCategory = localRuleParser.resolveCategory(
            type = mappedType,
            merchant = merchant,
            fullText = categoryHaystack,
            ruleDefaultCategoryName = "",
            categories = categories
        )

        if (enableCategoryJs && (resolvedCategory == null || resolvedCategory.name in FALLBACK_CATEGORY_NAMES)) {
            val jsExternalCat = evalCategory(raw, resolvedTimestamp)
            if (!jsExternalCat.isNullOrBlank()) {
                val candidates = JsCategoryMapper.mapToYubraeCandidates(jsExternalCat, mappedType)
                val typedCategories = categories.filter { it.type.equals(mappedType, ignoreCase = true) }
                    .ifEmpty { categories }
                val jsMatched = findBestCategoryMatch(candidates, typedCategories)
                if (jsMatched != null && (resolvedCategory == null || jsMatched.name !in FALLBACK_CATEGORY_NAMES)) {
                    resolvedCategory = jsMatched
                }
            }
        }

        val defaultCatName = if (mappedType == TransactionType.INCOME) "其他收入" else "其他支出"
        val resolvedCategoryName = resolvedCategory?.name ?: defaultCatName

        val compactNote = raw.shopItem.trim()
            .ifBlank { raw.channel.trim() }
            .ifBlank { input.content.trim().ifBlank { input.title.trim() } }
            .let { if (it.length <= 80) it else it.take(77) + "..." }

        val autoTags = localRuleParser.inferAutoTags(
            sourcePackage = input.sourcePackage,
            merchant = merchant,
            fullText = categoryHaystack,
            categoryName = resolvedCategoryName,
            currency = currency
        )

        val ruleLabel = raw.matchedRuleChineseName.ifBlank { raw.matchedRuleName }.ifBlank { "JS_RULE" }
        return ParsedBillDraft(
            type = mappedType,
            origAmountMicros = origAmountMicros,
            origCurrency = currency,
            accountId = accountId,
            accountHint = accountHint,
            toAccountId = toAccountId,
            toAccountHint = toAccountHint,
            categoryId = resolvedCategory?.id,
            categoryName = resolvedCategoryName,
            merchant = merchant,
            note = compactNote,
            timestamp = resolvedTimestamp,
            confidence = 0.95f,
            parserUsed = "${bundleSource.prefix}:$ruleLabel",
            tags = autoTags
        )
    }

    private fun resolveAccountFromHint(
        accountText: String,
        last4: String,
        aliases: List<AccountAliasEntity>,
        accounts: List<AccountEntity>
    ): Long? {
        if (accounts.isEmpty() || accountText.isBlank()) return null
        val cleanText = accountText.trim()
        val activeIds = accounts.map { it.id }.toSet()
        val enabledAliases = aliases
            .filter { it.enabled && it.accountId in activeIds }
            .sortedWith(compareByDescending<AccountAliasEntity> { it.priority }.thenBy { it.id })

        // 1. 账户名精确匹配
        accounts.firstOrNull { it.name.equals(cleanText, ignoreCase = true) }?.let { return it.id }

        // 2. 卡号尾号 4 位指纹匹配
        if (last4.isNotBlank()) {
            enabledAliases.firstOrNull { alias ->
                alias.matchType.equals(AliasMatchType.CARD_LAST4, ignoreCase = true) &&
                    alias.pattern.trim().equals(last4, ignoreCase = true)
            }?.let { return it.accountId }

            accounts.firstOrNull { it.name.contains(last4, ignoreCase = true) }?.let { return it.id }
        }

        // 3. REGEX / KEYWORD 别名匹配 accountText（绝不匹配 PACKAGE，避免误路由转账目标账户）
        for (alias in enabledAliases) {
            val matched = when (alias.matchType.uppercase(Locale.ROOT)) {
                AliasMatchType.REGEX -> runCatching {
                    Regex(alias.pattern, RegexOption.IGNORE_CASE).containsMatchIn(cleanText)
                }.getOrDefault(false)
                AliasMatchType.KEYWORD -> alias.pattern.isNotBlank() &&
                    cleanText.contains(alias.pattern.trim(), ignoreCase = true)
                else -> false
            }
            if (matched) return alias.accountId
        }

        // 4. 账户名包含匹配（长名字优先，并结合信用卡/储蓄卡倾向）
        val isCreditHint = cleanText.contains("信用卡") || cleanText.contains("贷记卡")
        val isDebitHint = cleanText.contains("储蓄卡") || cleanText.contains("借记卡") || cleanText.contains("一卡通")
        val nameMatches = accounts
            .filter { acc ->
                val accName = acc.name.trim()
                accName.length >= 2 &&
                    (accName.contains(cleanText, ignoreCase = true) || cleanText.contains(accName, ignoreCase = true))
            }
            .sortedWith(
                compareByDescending<AccountEntity> { acc ->
                    when {
                        isCreditHint && acc.groupType == AccountGroup.CREDIT -> 2
                        isDebitHint && acc.groupType == AccountGroup.DEBIT -> 2
                        !isCreditHint && !isDebitHint && acc.groupType != AccountGroup.CREDIT -> 1
                        else -> 0
                    }
                }.thenByDescending { it.name.length }
            )
        return nameMatches.firstOrNull()?.id
    }

    private fun evaluateCandidateRules(
        bundle: JsRuleBundle,
        candidates: List<JsRuleEntry>,
        payload: String
    ): JsRuleRawResult? = synchronized(engineLock) {
        val current = activeBundle ?: return@synchronized null
        if (current !== bundle) return@synchronized null

        sandboxFactory.call(ContextAction { cx ->
            val timedCx = configureContext(cx)
            val state = getOrInitStateLocked(timedCx, current) ?: return@ContextAction null

            for (entry in candidates) {
                val handle = ensureRuleCompiledLocked(timedCx, state, entry) ?: continue
                val (ruleObj, getFn) = handle
                val jsResult = try {
                    armTimeout(timedCx, effectiveTimeoutMillis)
                    getFn.call(timedCx, state.scope, ruleObj, arrayOf(payload))
                } catch (_: JsTimeoutError) {
                    null
                } catch (_: Throwable) {
                    null
                } finally {
                    disarmTimeout(timedCx)
                }
                if (jsResult != null && jsResult != Undefined.instance && jsResult !is Undefined) {
                    val parsed = parseRawResult(jsResult, entry)
                    if (parsed != null && parsed.money > 0.0) {
                        return@ContextAction parsed
                    }
                }
            }
            null
        })
    }

    private fun getOrInitStateLocked(cx: TimedContext, bundle: JsRuleBundle): CompiledBundleState? {
        val existing = compiledState
        if (existing != null && existing.bundle === bundle) {
            return existing
        }

        val scope = cx.initSafeStandardObjects(null, false)
        return try {
            armTimeout(cx, maxOf(effectiveTimeoutMillis * 4L, 1_000L))
            cx.evaluateString(scope, SANDBOX_PRELUDE_JS, "sandbox_prelude.js", 1, null)
            cx.evaluateString(scope, bundle.commonJs, "common.js", 1, null)
            CompiledBundleState(bundle = bundle, scope = scope).also {
                compiledState = it
            }
        } catch (_: JsTimeoutError) {
            null
        } catch (_: Throwable) {
            null
        } finally {
            disarmTimeout(cx)
        }
    }

    private fun ensureRuleCompiledLocked(
        cx: TimedContext,
        state: CompiledBundleState,
        entry: JsRuleEntry
    ): Pair<Scriptable, Function>? {
        val cacheKey = "${entry.meta.path}#${entry.meta.ruleName}"
        if (cacheKey in state.compiledRules) {
            return state.compiledRules[cacheKey]
        }
        val handle = try {
            armTimeout(cx, effectiveTimeoutMillis)
            cx.evaluateString(state.scope, entry.script, entry.meta.path, 1, null)
            val ruleObj = ScriptableObject.getProperty(state.scope, entry.meta.ruleName) as? Scriptable
            val getFn = ruleObj?.let { ScriptableObject.getProperty(it, "get") as? Function }
            if (ruleObj != null && getFn != null) ruleObj to getFn else null
        } catch (_: JsTimeoutError) {
            null
        } catch (_: Throwable) {
            null
        } finally {
            disarmTimeout(cx)
        }
        state.compiledRules[cacheKey] = handle
        return handle
    }

    private fun ensureCategoryCompiledLocked(
        cx: TimedContext,
        state: CompiledBundleState
    ): Pair<Scriptable, Function>? {
        if (state.categoryInitAttempted) {
            return state.categoryHandle
        }
        state.categoryInitAttempted = true
        val handle = try {
            armTimeout(cx, maxOf(effectiveTimeoutMillis * 2L, 500L))
            cx.evaluateString(state.scope, state.bundle.categoryJs, "category.js", 1, null)
            val catObj = ScriptableObject.getProperty(state.scope, "category") as? Scriptable
            val getFn = catObj?.let { ScriptableObject.getProperty(it, "get") as? Function }
            if (catObj != null && getFn != null) catObj to getFn else null
        } catch (_: JsTimeoutError) {
            null
        } catch (_: Throwable) {
            null
        } finally {
            disarmTimeout(cx)
        }
        state.categoryHandle = handle
        return handle
    }

    private fun configureContext(cx: Context): TimedContext {
        val timed = cx as TimedContext
        timed.optimizationLevel = -1
        timed.languageVersion = Context.VERSION_ES6
        timed.instructionObserverThreshold = INSTRUCTION_THRESHOLD
        if (!timed.shutterConfigured) {
            timed.setClassShutter(DENY_ALL_CLASS_SHUTTER)
            timed.shutterConfigured = true
        }
        return timed
    }

    private fun armTimeout(cx: TimedContext, timeoutMs: Long) {
        cx.currentTimeoutMillis = timeoutMs
        cx.deadlineNanos = System.nanoTime() + timeoutMs * 1_000_000L
    }

    private fun disarmTimeout(cx: TimedContext) {
        cx.deadlineNanos = 0L
    }

    private fun parseRawResult(jsValue: Any, entry: JsRuleEntry): JsRuleRawResult? {
        val obj = jsValue as? Scriptable ?: return null
        val money = propDouble(obj, "money")
        if (money <= 0.0) return null
        return JsRuleRawResult(
            type = propString(obj, "type").ifBlank { "Expend" },
            money = money,
            fee = propDouble(obj, "fee"),
            shopName = propString(obj, "shopName"),
            shopItem = propString(obj, "shopItem"),
            accountNameFrom = propString(obj, "accountNameFrom"),
            accountNameTo = propString(obj, "accountNameTo"),
            currency = propString(obj, "currency").ifBlank { CurrencyCatalog.DEFAULT_BASE_CURRENCY },
            time = propLong(obj, "time"),
            channel = propString(obj, "channel"),
            total = propDouble(obj, "total"),
            matchedRuleName = entry.meta.ruleName,
            matchedRuleChineseName = entry.meta.ruleChineseName,
            ruleType = entry.meta.ruleType
        )
    }

    private fun extractCategoryName(res: Any?): String? {
        if (res == null || res == Undefined.instance || res is Undefined) return null
        return when (res) {
            is String -> res.trim().takeIf { it.isNotEmpty() }
            is Scriptable -> propString(res, "category").takeIf { it.isNotEmpty() }
            else -> Context.toString(res).trim().takeIf { it.isNotEmpty() && it != "undefined" }
        }
    }

    private fun propString(obj: Scriptable, key: String): String {
        val value = ScriptableObject.getProperty(obj, key)
        if (value == null || value == Scriptable.NOT_FOUND || value == Undefined.instance || value is Undefined) {
            return ""
        }
        val str = Context.toString(value).trim()
        return if (str == "undefined" || str == "null") "" else str
    }

    private fun propDouble(obj: Scriptable, key: String): Double {
        val value = ScriptableObject.getProperty(obj, key)
        if (value == null || value == Scriptable.NOT_FOUND || value == Undefined.instance || value is Undefined) {
            return 0.0
        }
        if (value is String) {
            return value.replace(",", "").trim().toDoubleOrNull() ?: 0.0
        }
        val num = Context.toNumber(value)
        return if (num.isNaN() || num.isInfinite()) 0.0 else num
    }

    private fun propLong(obj: Scriptable, key: String): Long {
        val value = ScriptableObject.getProperty(obj, key)
        if (value == null || value == Scriptable.NOT_FOUND || value == Undefined.instance || value is Undefined) {
            return 0L
        }
        if (value is String) {
            return value.replace(",", "").trim().toDoubleOrNull()?.toLong() ?: 0L
        }
        val num = Context.toNumber(value)
        return if (num.isNaN() || num.isInfinite()) 0L else num.toLong()
    }

    private fun extractSmsBankTags(body: String): List<String> {
        val tags = LinkedHashSet<String>()
        var remaining = body
        while (true) {
            val match = LEADING_BANK_TAG_REGEX.find(remaining) ?: break
            val tag = match.groupValues.getOrNull(1)?.trim().orEmpty()
            if (tag.isNotEmpty()) tags.add(tag)
            remaining = remaining.substring(match.range.last + 1)
        }
        remaining = body
        while (true) {
            val match = TRAILING_BANK_TAG_REGEX.find(remaining) ?: break
            val tag = match.groupValues.getOrNull(1)?.trim().orEmpty()
            if (tag.isNotEmpty()) tags.add(tag)
            remaining = remaining.substring(0, match.range.first)
        }
        for (match in ANY_BANK_TAG_REGEX.findAll(body)) {
            val tag = match.groupValues.getOrNull(1)?.trim().orEmpty()
            if (tag.isNotEmpty()) tags.add(tag)
        }
        return tags.toList()
    }

    private fun findRulesByPackage(
        byPkg: Map<String, List<JsRuleEntry>>,
        pkg: String
    ): List<JsRuleEntry> {
        byPkg[pkg]?.let { return it }
        return byPkg.entries.firstOrNull { it.key.equals(pkg, ignoreCase = true) }?.value.orEmpty()
    }

    private fun extractLast4FromHint(accountText: String): String {
        if (accountText.isBlank()) return ""
        val match = HINT_LAST4_REGEX.find(accountText) ?: return ""
        return match.groupValues.drop(1).firstOrNull { it.isNotBlank() }.orEmpty()
    }

    private fun findBestCategoryMatch(
        candidateNames: List<String>,
        categories: List<CategoryEntity>
    ): CategoryEntity? {
        for (candidate in candidateNames) {
            categories.firstOrNull { it.name.equals(candidate, ignoreCase = true) }?.let { return it }
        }
        for (candidate in candidateNames) {
            categories.firstOrNull {
                it.name.contains(candidate, ignoreCase = true) || candidate.contains(it.name, ignoreCase = true)
            }?.let { return it }
        }
        return null
    }

    private fun doubleToMicros(amount: Double): Long? {
        if (amount.isNaN() || amount.isInfinite() || amount <= 0.0) return null
        return runCatching {
            BigDecimal.valueOf(amount)
                .multiply(BD_AMOUNT_SCALE)
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact()
        }.getOrNull()
    }

    private fun resolveTimestamp(rawTime: Long, fallbackMillis: Long): Long = when {
        rawTime >= YEAR_2000_MILLIS -> rawTime
        rawTime in YEAR_2000_SECONDS..YEAR_2100_SECONDS -> rawTime * 1_000L
        else -> fallbackMillis
    }

    private class CompiledBundleState(
        val bundle: JsRuleBundle,
        val scope: Scriptable,
        val compiledRules: MutableMap<String, Pair<Scriptable, Function>?> = HashMap(),
        var categoryInitAttempted: Boolean = false,
        var categoryHandle: Pair<Scriptable, Function>? = null
    )

    private class TimedContext(
        factory: ContextFactory,
        var currentTimeoutMillis: Long
    ) : Context(factory) {
        @Volatile
        var deadlineNanos: Long = 0L
        var shutterConfigured: Boolean = false
    }

    private class SandboxedContextFactory(
        private val defaultTimeoutMillis: Long
    ) : ContextFactory() {

        override fun makeContext(): Context {
            return TimedContext(this, defaultTimeoutMillis).apply {
                optimizationLevel = -1
                languageVersion = Context.VERSION_ES6
                instructionObserverThreshold = INSTRUCTION_THRESHOLD
                setClassShutter(DENY_ALL_CLASS_SHUTTER)
                shutterConfigured = true
            }
        }

        override fun observeInstructionCount(cx: Context?, instructionCount: Int) {
            val timed = cx as? TimedContext ?: return
            val deadline = timed.deadlineNanos
            if (deadline > 0L && System.nanoTime() > deadline) {
                throw JsTimeoutError("JS rule execution timed out after ${timed.currentTimeoutMillis}ms")
            }
        }
    }

    companion object {
        private const val INSTRUCTION_THRESHOLD = 5_000
        private const val YEAR_2000_MILLIS = 946_684_800_000L
        private const val YEAR_2000_SECONDS = 946_684_800L
        private const val YEAR_2100_SECONDS = 4_102_444_800L
        private val BD_AMOUNT_SCALE = BigDecimal(MoneyAmount.AMOUNT_SCALE)
        private val FALLBACK_CATEGORY_NAMES = setOf("其他支出", "其他收入")

        private val LEADING_BANK_TAG_REGEX = Regex("""^【(.*?)】""")
        private val TRAILING_BANK_TAG_REGEX = Regex("""【(.*?)】$""")
        private val ANY_BANK_TAG_REGEX = Regex("""【(.*?)】""")
        private val HINT_LAST4_REGEX = Regex("""(?:[（(](\d{4})[)）]|尾号\s*(\d{4})\b|\b(\d{4})$)""")

        private val DENY_ALL_CLASS_SHUTTER = ClassShutter { false }

        // 注入静默 console 与 Rhino ES6 缺失的现代 JS 方法垫片（纯 JS，无任何 Java 引用）
        private val SANDBOX_PRELUDE_JS = """
            var console = {
                log: function() {},
                warn: function() {},
                error: function() {},
                info: function() {},
                debug: function() {}
            };
            if (!String.prototype.includes) {
                String.prototype.includes = function(search, start) {
                    if (search instanceof RegExp) throw new TypeError("First argument must not be a RegExp");
                    return this.indexOf(String(search), start || 0) !== -1;
                };
            }
            if (!String.prototype.startsWith) {
                String.prototype.startsWith = function(search, pos) {
                    var p = (!pos || pos < 0) ? 0 : pos;
                    return this.substring(p, p + search.length) === search;
                };
            }
            if (!String.prototype.endsWith) {
                String.prototype.endsWith = function(search, thisLen) {
                    var len = (thisLen === undefined || thisLen > this.length) ? this.length : thisLen;
                    return this.substring(len - search.length, len) === search;
                };
            }
            if (!String.prototype.padStart) {
                String.prototype.padStart = function(targetLength, padString) {
                    var target = targetLength >> 0;
                    var pad = String(typeof padString !== "undefined" ? padString : " ");
                    var s = String(this);
                    if (s.length >= target || pad.length === 0) return s;
                    var need = target - s.length;
                    var repeated = "";
                    while (repeated.length < need) repeated += pad;
                    return repeated.slice(0, need) + s;
                };
            }
            if (!String.prototype.matchAll) {
                String.prototype.matchAll = function(re) {
                    var str = String(this);
                    var src = (re instanceof RegExp) ? re.source : String(re);
                    var flags = "";
                    if (re instanceof RegExp) {
                        if (re.ignoreCase) flags += "i";
                        if (re.multiline) flags += "m";
                    }
                    flags += "g";
                    var rx = new RegExp(src, flags);
                    var out = [];
                    var m;
                    while ((m = rx.exec(str)) !== null) {
                        out.push(m);
                        if (m[0].length === 0) rx.lastIndex++;
                    }
                    return out;
                };
            }
            if (!Array.prototype.includes) {
                Array.prototype.includes = function(elem, fromIndex) {
                    var len = this.length >>> 0;
                    var k = Math.max((fromIndex | 0) >= 0 ? (fromIndex | 0) : len + (fromIndex | 0), 0);
                    while (k < len) {
                        if (this[k] === elem || (elem !== elem && this[k] !== this[k])) return true;
                        k++;
                    }
                    return false;
                };
            }
            if (!Array.prototype.find) {
                Array.prototype.find = function(predicate, thisArg) {
                    if (typeof predicate !== "function") throw new TypeError("predicate must be a function");
                    var len = this.length >>> 0;
                    for (var i = 0; i < len; i++) {
                        if (i in this && predicate.call(thisArg, this[i], i, this)) return this[i];
                    }
                    return undefined;
                };
            }
            if (!Array.from) {
                Array.from = function(iterable) {
                    if (iterable == null) throw new TypeError("Array.from requires an array-like object");
                    if (Array.isArray(iterable)) return iterable.slice();
                    var arr = [];
                    if (typeof iterable.forEach === "function") {
                        iterable.forEach(function(v) { arr.push(v); });
                        return arr;
                    }
                    var len = iterable.length >>> 0;
                    for (var i = 0; i < len; i++) arr.push(iterable[i]);
                    return arr;
                };
            }
            if (!Object.assign) {
                Object.assign = function(target) {
                    if (target == null) throw new TypeError("Cannot convert undefined or null to object");
                    var to = Object(target);
                    for (var i = 1; i < arguments.length; i++) {
                        var src = arguments[i];
                        if (src != null) {
                            for (var key in src) {
                                if (Object.prototype.hasOwnProperty.call(src, key)) to[key] = src[key];
                            }
                        }
                    }
                    return to;
                };
            }
            if (!Object.entries) {
                Object.entries = function(obj) {
                    var keys = Object.keys(obj);
                    var res = new Array(keys.length);
                    for (var i = 0; i < keys.length; i++) res[i] = [keys[i], obj[keys[i]]];
                    return res;
                };
            }
            if (!Object.values) {
                Object.values = function(obj) {
                    var keys = Object.keys(obj);
                    var res = new Array(keys.length);
                    for (var i = 0; i < keys.length; i++) res[i] = obj[keys[i]];
                    return res;
                };
            }
        """.trimIndent()
    }
}
