package com.kairoledger.app.capture

import com.kairoledger.app.core.contract.CaptureInput
import com.kairoledger.app.core.model.MoneyAmount
import com.kairoledger.app.data.db.AccountEntity
import com.kairoledger.app.data.db.AccountGroup
import com.kairoledger.app.data.db.CaptureChannel
import com.kairoledger.app.data.db.CategoryEntity
import com.kairoledger.app.data.db.TransactionType
import com.kairoledger.app.data.prefs.AppSettings
import com.kairoledger.app.testutil.TestHttpServer
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets

class LlmBillParserTest {

    private lateinit var server: TestHttpServer
    private lateinit var parser: LlmBillParser
    private var lastRequestPath: String = ""
    private var lastAuthHeader: String = ""
    private var lastRequestBody: String = ""
    private var nextStatusCode: Int = 200
    private var nextResponseBody: String = ""

    private val accounts = listOf(
        AccountEntity(id = 1L, name = "微信零钱", groupType = AccountGroup.WALLET, currencyCode = "CNY"),
        AccountEntity(id = 2L, name = "美元账户 (USD)", groupType = AccountGroup.FOREIGN, currencyCode = "USD")
    )

    private val categories = listOf(
        CategoryEntity(id = 10L, name = "餐饮美食", type = TransactionType.EXPENSE),
        CategoryEntity(id = 20L, name = "数字订阅", type = TransactionType.EXPENSE),
        CategoryEntity(id = 30L, name = "其他收入", type = TransactionType.INCOME)
    )

    @Before
    fun setup() {
        parser = LlmBillParser(OkHttpClient())
        server = TestHttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            lastRequestPath = exchange.requestURI.path
            lastAuthHeader = exchange.requestHeaders.getFirst("Authorization").orEmpty()
            lastRequestBody = exchange.requestBody.readBytes().toString(StandardCharsets.UTF_8)

            val bytes = nextResponseBody.toByteArray(StandardCharsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "application/json; charset=utf-8")
            exchange.sendResponseHeaders(nextStatusCode, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
    }

    @After
    fun teardown() {
        server.stop(0)
    }

    private fun baseUrl(trailingSlash: Boolean): String {
        val port = server.address.port
        return if (trailingSlash) "http://127.0.0.1:$port/v1/" else "http://127.0.0.1:$port/v1"
    }

    private fun wrapAssistantReply(content: String): String = buildJsonObject {
        put("id", "chatcmpl-test")
        put("choices", buildJsonArray {
            add(buildJsonObject {
                put("index", 0)
                put("message", buildJsonObject {
                    put("role", "assistant")
                    put("content", content)
                })
            })
        })
    }.toString()

    @Test
    fun buildChatCompletionsUrl_handlesTrailingSlashAndDirectEndpoint() {
        assertEquals(
            "https://api.openai.com/v1/chat/completions",
            parser.buildChatCompletionsUrl("https://api.openai.com/v1/")
        )
        assertEquals(
            "https://api.deepseek.com/chat/completions",
            parser.buildChatCompletionsUrl("https://api.deepseek.com")
        )
        assertEquals(
            "https://custom.example.com/v1/chat/completions",
            parser.buildChatCompletionsUrl("https://custom.example.com/v1/chat/completions")
        )
    }

    @Test
    fun parse_stripsMarkdownFenceAndExplanatoryProseAndMapsAccounts() = runTest {
        nextStatusCode = 200
        nextResponseBody = wrapAssistantReply(
            """
            好的，以下是为您解析的结构化账单 JSON：
            ```json
            {
              "isTransaction": true,
              "type": "EXPENSE",
              "amount": "19.99",
              "currency": "USD",
              "accountId": 2,
              "accountHint": "",
              "categoryId": 20,
              "categoryName": "数字订阅",
              "merchant": "Cursor Pro",
              "note": "AI编程订阅续费",
              "confidence": 0.95
            }
            ```
            希望这能帮到您！
            """.trimIndent()
        )

        val settings = AppSettings(
            llmEnabled = true,
            llmBaseUrl = baseUrl(trailingSlash = true),
            llmApiKey = "sk-test-1234",
            llmModel = "gpt-4o-mini"
        )
        val input = CaptureInput(
            channel = CaptureChannel.NOTIFICATION,
            sourcePackage = "com.cursor.app",
            title = "Receipt",
            content = "Your Cursor Pro subscription of $19.99 has been renewed."
        )

        val result = parser.parse(input, settings, accounts, categories)
        assertTrue(result.isSuccess)
        val draft = result.getOrNull()
        assertNotNull(draft)
        assertEquals("/v1/chat/completions", lastRequestPath)
        assertEquals("Bearer sk-test-1234", lastAuthHeader)

        // 验证 system prompt 包含账户与分类信息
        val reqJson = Json.parseToJsonElement(lastRequestBody).jsonObject
        val systemMsg = reqJson["messages"]!!.jsonArray[0].jsonObject["content"]!!.jsonPrimitive.content
        assertTrue(systemMsg.contains("美元账户 (USD)"))
        assertTrue(systemMsg.contains("数字订阅"))

        assertEquals(TransactionType.EXPENSE, draft!!.type)
        assertEquals(MoneyAmount.parseToMicros("19.99"), draft.origAmountMicros)
        assertEquals("USD", draft.origCurrency)
        assertEquals(2L, draft.accountId)
        assertEquals(20L, draft.categoryId)
        assertEquals("Cursor Pro", draft.merchant)
        assertEquals("LLM", draft.parserUsed)
    }

    @Test
    fun parse_returnsNullWhenModelDeterminesNonTransaction() = runTest {
        nextStatusCode = 200
        nextResponseBody = wrapAssistantReply("""{"isTransaction": false}""")

        val settings = AppSettings(
            llmEnabled = true,
            llmBaseUrl = baseUrl(trailingSlash = false),
            llmApiKey = "sk-test"
        )
        val result = parser.parse(
            CaptureInput(CaptureChannel.SMS, "1069", "通知", "您的会员积分即将到期"),
            settings,
            accounts,
            categories
        )

        assertTrue(result.isSuccess)
        assertNull(result.getOrNull())
    }

    @Test
    fun testConnection_returnsSuccessAndHandlesHttpError() = runTest {
        nextStatusCode = 200
        nextResponseBody = wrapAssistantReply("OK")
        val okSettings = AppSettings(
            llmBaseUrl = baseUrl(trailingSlash = true),
            llmApiKey = "sk-ok",
            llmModel = "deepseek-chat"
        )
        val okResult = parser.testConnection(okSettings)
        assertTrue(okResult.isSuccess)
        assertTrue(okResult.getOrThrow().contains("连接成功"))
        assertTrue(okResult.getOrThrow().contains("deepseek-chat"))

        nextStatusCode = 401
        nextResponseBody = """{"error":{"message":"Invalid API key"}}"""
        val errResult = parser.testConnection(okSettings)
        assertTrue(errResult.isFailure)
        assertTrue(errResult.exceptionOrNull()!!.message!!.contains("401"))
    }
}
