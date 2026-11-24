package com.yubrae.app.capture

import com.yubrae.app.core.contract.CaptureInput
import com.yubrae.app.core.contract.ParsedBillDraft
import com.yubrae.app.core.model.CurrencyCatalog
import com.yubrae.app.core.model.MoneyAmount
import com.yubrae.app.data.db.AccountEntity
import com.yubrae.app.data.db.CategoryEntity
import com.yubrae.app.data.db.TransactionType
import com.yubrae.app.data.prefs.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * OpenAI 兼容大模型兜底解析器（纯 JVM 可测）。
 */
class LlmBillParser(private val okHttpClient: OkHttpClient) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    suspend fun parse(
        input: CaptureInput,
        settings: AppSettings,
        accounts: List<AccountEntity>,
        categories: List<CategoryEntity>
    ): Result<ParsedBillDraft?> = withContext(Dispatchers.IO) {
        runCatching {
            val endpoint = buildChatCompletionsUrl(settings.llmBaseUrl)
            validateSettings(settings, endpoint)

            val systemPrompt = buildSystemPrompt(accounts, categories)
            val userPrompt = buildString {
                appendLine("请解析以下捕获消息，若非真实资金动账请返回 {\"isTransaction\": false}：")
                appendLine("渠道: ${input.channel}")
                appendLine("来源包名/发件人: ${input.sourcePackage}")
                appendLine("标题: ${input.title}")
                appendLine("正文: ${input.content}")
            }

            val payload = buildJsonObject {
                put("model", settings.llmModel.ifBlank { "gpt-4o-mini" })
                put("temperature", 0.1)
                put("messages", buildJsonArray {
                    add(buildJsonObject {
                        put("role", "system")
                        put("content", systemPrompt)
                    })
                    add(buildJsonObject {
                        put("role", "user")
                        put("content", userPrompt)
                    })
                })
            }.toString()

            val responseText = executeChatRequest(endpoint, settings.llmApiKey, payload)
            val content = extractAssistantContent(responseText)
            extractBillDraftFromContent(
                rawContent = content,
                timestamp = input.timestamp,
                accounts = accounts,
                categories = categories
            )
        }
    }

    suspend fun testConnection(settings: AppSettings): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val endpoint = buildChatCompletionsUrl(settings.llmBaseUrl)
            validateSettings(settings, endpoint)

            val payload = buildJsonObject {
                put("model", settings.llmModel.ifBlank { "gpt-4o-mini" })
                put("temperature", 0.0)
                put("max_tokens", 16)
                put("messages", buildJsonArray {
                    add(buildJsonObject {
                        put("role", "user")
                        put("content", "Reply with OK only.")
                    })
                })
            }.toString()

            val responseText = executeChatRequest(endpoint, settings.llmApiKey, payload)
            val reply = extractAssistantContent(responseText).trim()
            val modelName = settings.llmModel.ifBlank { "default" }
            "连接成功 ($modelName): ${reply.take(60).ifBlank { "OK" }}"
        }
    }

    /**
     * 一句话语音/自然语言多笔拆单解析：支持输出 JSON 数组或 {"items":[...]}，并还原昨日/前天/今天相对日期。
     */
    suspend fun parseNaturalLanguageBatch(
        text: String,
        settings: AppSettings,
        accounts: List<AccountEntity>,
        categories: List<CategoryEntity>,
        nowMillis: Long = System.currentTimeMillis()
    ): Result<List<ParsedBillDraft>> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanText = text.trim()
            if (cleanText.isEmpty()) return@runCatching emptyList()

            val endpoint = buildChatCompletionsUrl(settings.llmBaseUrl)
            validateSettings(settings, endpoint)

            val systemPrompt = buildBatchSystemPrompt(accounts, categories)
            val userPrompt = "请将以下口语或文本拆解为记账明细 JSON 数组：\n$cleanText"

            val payload = buildJsonObject {
                put("model", settings.llmModel.ifBlank { "gpt-4o-mini" })
                put("temperature", 0.1)
                put("messages", buildJsonArray {
                    add(buildJsonObject {
                        put("role", "system")
                        put("content", systemPrompt)
                    })
                    add(buildJsonObject {
                        put("role", "user")
                        put("content", userPrompt)
                    })
                })
            }.toString()

            val responseText = executeChatRequest(endpoint, settings.llmApiKey, payload)
            val content = extractAssistantContent(responseText)
            extractBatchDraftsFromContent(
                rawContent = content,
                nowMillis = nowMillis,
                accounts = accounts,
                categories = categories
            )
        }
    }

    /**
     * 多模态 Vision 小票/账单截图识别：遵循 OpenAI Chat Completions image_url data URI 格式。
     */
    suspend fun parseReceiptImage(
        imageBase64: String,
        mimeType: String,
        settings: AppSettings,
        accounts: List<AccountEntity>,
        categories: List<CategoryEntity>,
        nowMillis: Long = System.currentTimeMillis()
    ): Result<ParsedBillDraft?> = withContext(Dispatchers.IO) {
        runCatching {
            require(imageBase64.isNotBlank()) { "图片数据不能为空" }
            val cleanMime = mimeType.trim().ifBlank { "image/jpeg" }
            val endpoint = buildChatCompletionsUrl(settings.llmBaseUrl)
            validateSettings(settings, endpoint)

            val systemPrompt = buildVisionSystemPrompt(accounts, categories)
            val payload = buildJsonObject {
                put("model", settings.llmModel.ifBlank { "gpt-4o-mini" })
                put("temperature", 0.1)
                put("messages", buildJsonArray {
                    add(buildJsonObject {
                        put("role", "system")
                        put("content", systemPrompt)
                    })
                    add(buildJsonObject {
                        put("role", "user")
                        put("content", buildJsonArray {
                            add(buildJsonObject {
                                put("type", "text")
                                put("text", "请识别这张小票、购物收据或支付凭证图片中的真实交易信息，若无法识别或非账单请返回 {\"isTransaction\": false}：")
                            })
                            add(buildJsonObject {
                                put("type", "image_url")
                                put("image_url", buildJsonObject {
                                    put("url", "data:$cleanMime;base64,$imageBase64")
                                })
                            })
                        })
                    })
                })
            }.toString()

            val responseText = executeChatRequest(endpoint, settings.llmApiKey, payload)
            val content = extractAssistantContent(responseText)
            extractBillDraftFromContent(
                rawContent = content,
                timestamp = nowMillis,
                accounts = accounts,
                categories = categories,
                parserUsed = "LLM_VISION"
            )
        }
    }

    internal fun buildChatCompletionsUrl(baseUrl: String): String {
        val trimmed = baseUrl.trim().trimEnd('/')
        require(trimmed.isNotEmpty()) { "LLM Base URL 不能为空" }
        return if (trimmed.endsWith("/chat/completions", ignoreCase = true)) {
            trimmed
        } else {
            "$trimmed/chat/completions"
        }
    }

    private fun validateSettings(settings: AppSettings, endpoint: String) {
        val isLocal = endpoint.contains("127.0.0.1") || endpoint.contains("localhost")
        if (settings.llmApiKey.isBlank() && !isLocal) {
            throw IllegalStateException("请先配置 LLM API Key")
        }
    }

    private fun executeChatRequest(endpoint: String, apiKey: String, jsonBody: String): String {
        val requestBuilder = Request.Builder()
            .url(endpoint)
            .header("Content-Type", "application/json")
            .post(jsonBody.toRequestBody(JSON_MEDIA_TYPE))

        if (apiKey.isNotBlank()) {
            requestBuilder.header("Authorization", "Bearer ${apiKey.trim()}")
        }

        okHttpClient.newCall(requestBuilder.build()).execute().use { response ->
            val rawBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val detail = extractErrorMessage(rawBody).ifBlank { response.message }
                throw IllegalStateException("HTTP ${response.code}: $detail")
            }
            if (rawBody.isBlank()) {
                throw IllegalStateException("LLM 返回空响应体")
            }
            return rawBody
        }
    }

    private fun extractErrorMessage(rawBody: String): String {
        if (rawBody.isBlank()) return ""
        return runCatching {
            val root = json.parseToJsonElement(rawBody).jsonObject
            root["error"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull
                ?: root["message"]?.jsonPrimitive?.contentOrNull
                ?: rawBody.take(180)
        }.getOrDefault(rawBody.take(180))
    }

    private fun extractAssistantContent(rawResponseBody: String): String {
        val root = json.parseToJsonElement(rawResponseBody).jsonObject
        val choices = root["choices"]?.jsonArray
            ?: throw IllegalStateException("LLM 响应缺少 choices 字段")
        if (choices.isEmpty()) {
            throw IllegalStateException("LLM 响应 choices 为空")
        }
        val messageObj = choices[0].jsonObject["message"]?.jsonObject
            ?: throw IllegalStateException("LLM 响应缺少 message 字段")
        val contentElem = messageObj["content"]
            ?: throw IllegalStateException("LLM 响应内容为空")
        val extracted = when (contentElem) {
            is JsonPrimitive -> contentElem.contentOrNull
            is JsonArray -> contentElem.mapNotNull { part ->
                (part as? JsonObject)?.get("text")?.let { (it as? JsonPrimitive)?.contentOrNull }
            }.joinToString("\n").ifBlank { null }
            else -> null
        }
        return extracted ?: throw IllegalStateException("LLM 响应内容为空")
    }

    internal fun extractBillDraftFromContent(
        rawContent: String,
        timestamp: Long,
        accounts: List<AccountEntity>,
        categories: List<CategoryEntity>,
        parserUsed: String = "LLM"
    ): ParsedBillDraft? {
        val jsonSnippet = extractJsonObject(rawContent)
            ?: throw IllegalStateException("无法从 LLM 输出中提取 JSON 对象: ${rawContent.take(120)}")
        val obj = json.parseToJsonElement(jsonSnippet).jsonObject
        return parseDraftObject(
            obj = obj,
            defaultTimestamp = timestamp,
            accounts = accounts,
            categories = categories,
            parserUsed = parserUsed
        )
    }

    internal fun extractBatchDraftsFromContent(
        rawContent: String,
        nowMillis: Long,
        accounts: List<AccountEntity>,
        categories: List<CategoryEntity>
    ): List<ParsedBillDraft> {
        val jsonSnippet = extractFirstJsonArrayOrObject(rawContent)
            ?: throw IllegalStateException("无法从 LLM 输出中提取 JSON 数组或对象: ${rawContent.take(120)}")
        val element: JsonElement = json.parseToJsonElement(jsonSnippet)
        val itemObjects: List<JsonObject> = when (element) {
            is JsonArray -> element.mapNotNull { it as? JsonObject }
            is JsonObject -> {
                val isTx = (element["isTransaction"] as? JsonPrimitive)?.booleanOrNull
                if (isTx == false) return emptyList()
                val arr = (element["items"] ?: element["bills"] ?: element["transactions"] ?: element["records"]) as? JsonArray
                when {
                    arr != null -> arr.mapNotNull { it as? JsonObject }
                    element.containsKey("amount") -> listOf(element)
                    else -> emptyList()
                }
            }
            else -> emptyList()
        }
        if (itemObjects.isEmpty()) return emptyList()

        val drafts = mutableListOf<ParsedBillDraft>()
        for (item in itemObjects) {
            val offsetDays = primitiveInt(item, "timestampOffsetDays") ?: 0
            val itemTimestamp = resolveRelativeTimestamp(nowMillis, offsetDays)
            val parsed = parseDraftObject(
                obj = item,
                defaultTimestamp = itemTimestamp,
                accounts = accounts,
                categories = categories,
                parserUsed = "LLM_SMART_BATCH"
            )
            if (parsed != null) {
                drafts.add(parsed)
            }
        }
        return drafts
    }

    private fun parseDraftObject(
        obj: JsonObject,
        defaultTimestamp: Long,
        accounts: List<AccountEntity>,
        categories: List<CategoryEntity>,
        parserUsed: String
    ): ParsedBillDraft? {
        val isTx = (obj["isTransaction"] as? JsonPrimitive)?.booleanOrNull ?: true
        if (!isTx) return null

        val rawType = primitiveString(obj, "type")?.uppercase(Locale.ROOT).orEmpty()
        val type = when (rawType) {
            TransactionType.INCOME, "收入" -> TransactionType.INCOME
            TransactionType.TRANSFER, "转账" -> TransactionType.TRANSFER
            else -> TransactionType.EXPENSE
        }

        val amountRaw = primitiveString(obj, "amount")
            ?: throw IllegalStateException("LLM JSON 缺少 amount 字段")
        val origAmountMicros = MoneyAmount.parseToMicros(amountRaw)
            ?.takeIf { it > 0L }
            ?: throw IllegalStateException("LLM 返回非法金额: $amountRaw")

        val rawCurrency = primitiveString(obj, "currency").orEmpty()
        val origCurrency = if (rawCurrency.isNotBlank()) {
            CurrencyCatalog.find(rawCurrency).code
        } else {
            CurrencyCatalog.DEFAULT_BASE_CURRENCY
        }

        val activeAccounts = accounts.filter { !it.archived }.ifEmpty { accounts }
        val accountHint = primitiveString(obj, "accountHint").orEmpty().trim()
        val requestedAccountId = primitiveString(obj, "accountId")?.toLongOrNull()
        val resolvedAccountId = resolveAccountId(
            requestedId = requestedAccountId,
            requestedName = primitiveString(obj, "accountName").orEmpty(),
            accountHint = accountHint,
            currency = origCurrency,
            accounts = activeAccounts
        )

        val typedCategories = categories.filter { it.type.equals(type, ignoreCase = true) }
            .ifEmpty { categories }
        val requestedCategoryId = primitiveString(obj, "categoryId")?.toLongOrNull()
        val requestedCategoryName = primitiveString(obj, "categoryName").orEmpty().trim()
        val resolvedCategory = if (type == TransactionType.TRANSFER && requestedCategoryId == null && requestedCategoryName.isBlank()) {
            null
        } else {
            resolveCategory(
                requestedId = requestedCategoryId,
                requestedName = requestedCategoryName,
                categories = typedCategories
            )
        }

        val merchant = primitiveString(obj, "merchant").orEmpty().trim()
        val note = primitiveString(obj, "note").orEmpty().trim()
        val confidence = primitiveString(obj, "confidence")
            ?.toFloatOrNull()
            ?.coerceIn(0f, 1f)
            ?: 0.85f

        val dateStr = primitiveString(obj, "date")?.trim().orEmpty()
        val finalTimestamp = if (dateStr.isNotEmpty()) {
            parseOptionalDateMillis(dateStr, defaultTimestamp) ?: defaultTimestamp
        } else {
            defaultTimestamp
        }

        return ParsedBillDraft(
            type = type,
            origAmountMicros = origAmountMicros,
            origCurrency = origCurrency,
            accountId = resolvedAccountId,
            accountHint = accountHint,
            categoryId = resolvedCategory?.id,
            categoryName = resolvedCategory?.name ?: requestedCategoryName,
            merchant = merchant,
            note = note,
            timestamp = finalTimestamp,
            confidence = confidence,
            parserUsed = parserUsed
        )
    }

    internal fun extractJsonObject(rawContent: String): String? =
        extractBalancedJson(rawContent, openChar = '{', closeChar = '}')

    internal fun extractFirstJsonArrayOrObject(rawContent: String): String? {
        val stripped = stripMarkdownCodeFence(rawContent)
        val firstBracket = stripped.indexOf('[')
        val firstBrace = stripped.indexOf('{')
        return when {
            firstBracket >= 0 && (firstBrace < 0 || firstBracket < firstBrace) ->
                extractBalancedJson(stripped, openChar = '[', closeChar = ']')
                    ?: extractBalancedJson(stripped, openChar = '{', closeChar = '}')
            firstBrace >= 0 ->
                extractBalancedJson(stripped, openChar = '{', closeChar = '}')
                    ?: extractBalancedJson(stripped, openChar = '[', closeChar = ']')
            else -> null
        }
    }

    private fun stripMarkdownCodeFence(rawContent: String): String {
        var stripped = rawContent.trim()
        val fenceRegex = Regex("```(?:json)?\\s*([\\s\\S]*?)```", RegexOption.IGNORE_CASE)
        fenceRegex.find(stripped)?.let { match ->
            stripped = match.groupValues[1].trim()
        }
        return stripped
    }

    private fun extractBalancedJson(rawContent: String, openChar: Char, closeChar: Char): String? {
        val stripped = stripMarkdownCodeFence(rawContent)
        var startIdx = -1
        var depth = 0
        var inString = false
        var escaped = false

        for (i in stripped.indices) {
            val ch = stripped[i]
            if (escaped) {
                escaped = false
                continue
            }
            if (ch == '\\' && inString) {
                escaped = true
                continue
            }
            if (ch == '"') {
                inString = !inString
                continue
            }
            if (!inString) {
                if (ch == openChar) {
                    if (depth == 0) startIdx = i
                    depth++
                } else if (ch == closeChar && depth > 0) {
                    depth--
                    if (depth == 0 && startIdx >= 0) {
                        return stripped.substring(startIdx, i + 1)
                    }
                }
            }
        }
        return null
    }

    private fun formatAccountsAndCategoriesContext(
        accounts: List<AccountEntity>,
        categories: List<CategoryEntity>
    ): Pair<String, String> {
        val activeAccounts = accounts.filter { !it.archived }.ifEmpty { accounts }
        val accountsDesc = activeAccounts.joinToString("\n") { acc ->
            "- id=${acc.id}, name=\"${acc.name}\", currency=${acc.currencyCode}, group=${acc.groupType}"
        }.ifBlank { "(暂无账户)" }
        val categoriesDesc = categories.joinToString("\n") { cat ->
            "- id=${cat.id}, name=\"${cat.name}\", type=${cat.type}"
        }.ifBlank { "(暂无分类)" }
        return accountsDesc to categoriesDesc
    }

    private fun buildSystemPrompt(
        accounts: List<AccountEntity>,
        categories: List<CategoryEntity>
    ): String {
        val (accountsDesc, categoriesDesc) = formatAccountsAndCategoriesContext(accounts, categories)
        return """
你是「余白 YUBRAE」的专业财务账单提取助手。
请从用户提供的通知、短信或分享文本中提取结构化记账信息，并**严格只输出一个 JSON 对象**（不要包含 Markdown 解释或多余文字）。

可用账户列表:
$accountsDesc

可用分类列表:
$categoriesDesc

输出 JSON 格式要求:
1. 若文本属于验证码、登录提醒、快递物流、促销广告或未实际扣款的账单提醒，输出:
   {"isTransaction": false}
2. 若文本属于真实资金动账（消费/收入/转账），输出:
   {
     "isTransaction": true,
     "type": "EXPENSE 或 INCOME 或 TRANSFER",
     "amount": "纯数字金额字符串如 15.00",
     "currency": "ISO 4217 币种代码如 CNY, USD, EUR, GBP",
     "accountId": 匹配的账户id或null,
     "accountHint": "银行卡尾号4位数字如6688，无则空字符串",
     "categoryId": 匹配的分类id或null,
     "categoryName": "匹配的分类名称",
     "merchant": "商户或交易对方名称",
     "note": "简短中文备注",
     "confidence": 0.90
   }
        """.trimIndent()
    }

    private fun buildBatchSystemPrompt(
        accounts: List<AccountEntity>,
        categories: List<CategoryEntity>
    ): String {
        val (accountsDesc, categoriesDesc) = formatAccountsAndCategoriesContext(accounts, categories)
        return """
你是「余白 YUBRAE」的一句话语音/自然语言智能记账助手。
用户可能在一句话中同时口述 1 到多笔收支或转账（并可能包含今天/昨天/前天等相对时间）。
请严格按以下 JSON 数组格式输出全部识别出的明细（仅输出 JSON 数组，不要包含 Markdown 代码块或多余解释）：
[
  {
    "type": "EXPENSE 或 INCOME 或 TRANSFER",
    "amount": "纯数字金额字符串如 28.50",
    "currency": "ISO 4217 币种代码如 CNY, USD, EUR, GBP, JPY, HKD",
    "merchant": "商户或交易对象名称",
    "categoryName": "最匹配的分类名称",
    "accountName": "最匹配的账户名称",
    "note": "简短中文备注",
    "timestampOffsetDays": 0
  }
]
其中 timestampOffsetDays 为相对今天的天数偏移：今天=0，昨天/昨晚=-1，前天=-2。若整段文本不包含任何有效记账金额，返回 []。

可用账户列表:
$accountsDesc

可用分类列表:
$categoriesDesc
        """.trimIndent()
    }

    private fun buildVisionSystemPrompt(
        accounts: List<AccountEntity>,
        categories: List<CategoryEntity>
    ): String {
        val (accountsDesc, categoriesDesc) = formatAccountsAndCategoriesContext(accounts, categories)
        return """
你是「余白 YUBRAE」的小票、发票与账单截图视觉识图记账助手。
请仔细识别图片中的实付总金额、币种、商户名称、支付方式/银行卡尾号、交易时间与商品明细，并**严格只输出一个 JSON 对象**（不要包含 Markdown 解释或多余文字）：

可用账户列表:
$accountsDesc

可用分类列表:
$categoriesDesc

输出格式:
1. 若图片不是消费小票、收据或账单凭证，输出:
   {"isTransaction": false}
2. 若属于真实交易小票或账单截图，输出:
   {
     "isTransaction": true,
     "type": "EXPENSE 或 INCOME 或 TRANSFER",
     "amount": "实付总金额纯数字字符串如 45.50",
     "currency": "ISO 4217 币种代码如 CNY, USD, EUR, GBP, JPY, HKD",
     "accountId": 匹配的账户id或null,
     "accountName": "匹配的账户名称",
     "accountHint": "图片中银行卡尾号4位数字如6688，无则空字符串",
     "categoryId": 匹配的分类id或null,
     "categoryName": "最匹配的分类名称",
     "merchant": "商户名称",
     "note": "简短商品明细或中文备注",
     "date": "可选交易日期时间 yyyy-MM-dd HH:mm 或 yyyy-MM-dd，无则空字符串",
     "confidence": 0.90
   }
        """.trimIndent()
    }

    private fun resolveAccountId(
        requestedId: Long?,
        requestedName: String,
        accountHint: String,
        currency: String,
        accounts: List<AccountEntity>
    ): Long? {
        if (accounts.isEmpty()) return null
        if (requestedId != null && accounts.any { it.id == requestedId }) {
            return requestedId
        }
        if (requestedName.isNotBlank()) {
            accounts.firstOrNull { it.name.equals(requestedName, ignoreCase = true) }?.let { return it.id }
            accounts.firstOrNull { it.name.contains(requestedName, ignoreCase = true) }?.let { return it.id }
            accounts.firstOrNull { it.name.length >= 2 && requestedName.contains(it.name, ignoreCase = true) }?.let { return it.id }
        }
        if (accountHint.isNotBlank()) {
            accounts.firstOrNull { it.name.contains(accountHint) }?.let { return it.id }
        }
        accounts.firstOrNull { it.currencyCode.equals(currency, ignoreCase = true) }?.let { return it.id }
        return accounts.firstOrNull()?.id
    }

    private fun resolveCategory(
        requestedId: Long?,
        requestedName: String,
        categories: List<CategoryEntity>
    ): CategoryEntity? {
        if (categories.isEmpty()) return null
        if (requestedId != null) {
            categories.firstOrNull { it.id == requestedId }?.let { return it }
        }
        if (requestedName.isNotBlank()) {
            categories.firstOrNull { it.name.equals(requestedName, ignoreCase = true) }?.let { return it }
            categories.firstOrNull {
                it.name.contains(requestedName, ignoreCase = true) ||
                    requestedName.contains(it.name, ignoreCase = true)
            }?.let { return it }
        }
        return categories.firstOrNull()
    }

    private fun primitiveString(obj: JsonObject, key: String): String? {
        val elem = obj[key] ?: return null
        if (elem is JsonNull) return null
        return (elem as? JsonPrimitive)?.contentOrNull
    }

    private fun primitiveInt(obj: JsonObject, key: String): Int? {
        val elem = obj[key] ?: return null
        if (elem is JsonNull) return null
        val primitive = elem as? JsonPrimitive ?: return null
        return primitive.intOrNull ?: primitive.contentOrNull?.trim()?.toIntOrNull()
    }

    private fun resolveRelativeTimestamp(nowMillis: Long, offsetDays: Int): Long {
        if (offsetDays == 0) return nowMillis
        val clamped = offsetDays.coerceIn(-365, 30)
        return Instant.ofEpochMilli(nowMillis)
            .atZone(ZoneId.systemDefault())
            .plusDays(clamped.toLong())
            .toInstant()
            .toEpochMilli()
    }

    private fun parseOptionalDateMillis(raw: String, fallbackMillis: Long): Long? {
        val zone = ZoneId.systemDefault()
        val cleaned = raw.trim()
        runCatching {
            val dt = LocalDateTime.parse(cleaned, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            return dt.atZone(zone).toInstant().toEpochMilli()
        }
        runCatching {
            val dt = LocalDateTime.parse(cleaned, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
            return dt.atZone(zone).toInstant().toEpochMilli()
        }
        runCatching {
            val d = LocalDate.parse(cleaned, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            val time = Instant.ofEpochMilli(fallbackMillis).atZone(zone).toLocalTime()
            return d.atTime(time).atZone(zone).toInstant().toEpochMilli()
        }
        return null
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
