package com.kairoledger.app.capture

import com.kairoledger.app.core.contract.CaptureInput
import com.kairoledger.app.core.contract.ParsedBillDraft
import com.kairoledger.app.core.model.CurrencyCatalog
import com.kairoledger.app.core.model.MoneyAmount
import com.kairoledger.app.data.db.AccountEntity
import com.kairoledger.app.data.db.CategoryEntity
import com.kairoledger.app.data.db.TransactionType
import com.kairoledger.app.data.prefs.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
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
        return messageObj["content"]?.jsonPrimitive?.contentOrNull
            ?: throw IllegalStateException("LLM 响应内容为空")
    }

    internal fun extractBillDraftFromContent(
        rawContent: String,
        timestamp: Long,
        accounts: List<AccountEntity>,
        categories: List<CategoryEntity>
    ): ParsedBillDraft? {
        val jsonSnippet = extractJsonObject(rawContent)
            ?: throw IllegalStateException("无法从 LLM 输出中提取 JSON 对象: ${rawContent.take(120)}")
        val obj = json.parseToJsonElement(jsonSnippet).jsonObject

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
        val resolvedCategory = resolveCategory(
            requestedId = requestedCategoryId,
            requestedName = requestedCategoryName,
            categories = typedCategories
        )

        val merchant = primitiveString(obj, "merchant").orEmpty().trim()
        val note = primitiveString(obj, "note").orEmpty().trim()
        val confidence = primitiveString(obj, "confidence")
            ?.toFloatOrNull()
            ?.coerceIn(0f, 1f)
            ?: 0.85f

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
            timestamp = timestamp,
            confidence = confidence,
            parserUsed = "LLM"
        )
    }

    internal fun extractJsonObject(rawContent: String): String? {
        var stripped = rawContent.trim()
        // 剥离 ```json ... ``` 或 ``` ... ``` 代码块包裹
        val fenceRegex = Regex("```(?:json)?\\s*([\\s\\S]*?)```", RegexOption.IGNORE_CASE)
        fenceRegex.find(stripped)?.let { match ->
            stripped = match.groupValues[1].trim()
        }

        // 在可能存在的前后解释文字中提取第一个完整平衡的 JSON 对象 { ... }
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
                if (ch == '{') {
                    if (depth == 0) startIdx = i
                    depth++
                } else if (ch == '}' && depth > 0) {
                    depth--
                    if (depth == 0 && startIdx >= 0) {
                        return stripped.substring(startIdx, i + 1)
                    }
                }
            }
        }
        return null
    }

    private fun buildSystemPrompt(
        accounts: List<AccountEntity>,
        categories: List<CategoryEntity>
    ): String {
        val activeAccounts = accounts.filter { !it.archived }.ifEmpty { accounts }
        val accountsDesc = activeAccounts.joinToString("\n") { acc ->
            "- id=${acc.id}, name=\"${acc.name}\", currency=${acc.currencyCode}, group=${acc.groupType}"
        }
        val categoriesDesc = categories.joinToString("\n") { cat ->
            "- id=${cat.id}, name=\"${cat.name}\", type=${cat.type}"
        }

        return """
你是「棱账 KairoLedger」的专业财务账单提取助手。
请从用户提供的通知、短信或分享文本中提取结构化记账信息，并**严格只输出一个 JSON 对象**（不要包含 Markdown 解释或多余文字）。

可用账户列表:
${accountsDesc.ifBlank { "(暂无账户)" }}

可用分类列表:
${categoriesDesc.ifBlank { "(暂无分类)" }}

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

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
