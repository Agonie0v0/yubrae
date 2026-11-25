package com.yubrae.app.capture

import com.yubrae.app.core.contract.CaptureInput
import com.yubrae.app.core.model.MoneyAmount
import com.yubrae.app.data.db.AccountEntity
import com.yubrae.app.data.db.AccountGroup
import com.yubrae.app.data.db.CaptureChannel
import com.yubrae.app.data.db.CategoryEntity
import com.yubrae.app.data.db.TransactionType
import com.yubrae.app.data.prefs.AppSettings
import com.yubrae.app.testutil.TestHttpExchange
import com.yubrae.app.testutil.TestHttpServer
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
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.Base64

class LlmBillParserTest {

    private data class CapturedHttpRequest(
        val path: String,
        val authHeader: String,
        val contentType: String,
        val rawBytes: ByteArray
    ) {
        val bodyText: String
            get() = rawBytes.toString(StandardCharsets.UTF_8)
    }

    private lateinit var server: TestHttpServer
    private lateinit var parser: LlmBillParser
    private var lastRequestPath: String = ""
    private var lastAuthHeader: String = ""
    private var lastRequestBody: String = ""
    private var nextStatusCode: Int = 200
    private var nextResponseBody: String = ""
    private val capturedRequests = mutableListOf<CapturedHttpRequest>()
    private var routeHandler: ((TestHttpExchange, CapturedHttpRequest) -> Pair<Int, String>)? = null

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
        capturedRequests.clear()
        routeHandler = null
        server = TestHttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val rawBytes = exchange.requestBody.readBytes()
            val captured = CapturedHttpRequest(
                path = exchange.requestURI.path,
                authHeader = exchange.requestHeaders.getFirst("Authorization").orEmpty(),
                contentType = exchange.requestHeaders.getFirst("Content-Type").orEmpty(),
                rawBytes = rawBytes
            )
            capturedRequests.add(captured)
            lastRequestPath = captured.path
            lastAuthHeader = captured.authHeader
            lastRequestBody = captured.bodyText

            val (status, respBody) = routeHandler?.invoke(exchange, captured)
                ?: (nextStatusCode to nextResponseBody)
            val bytes = respBody.toByteArray(StandardCharsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "application/json; charset=utf-8")
            exchange.sendResponseHeaders(status, bytes.size.toLong())
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

    @Test
    fun buildAudioTranscriptionsUrl_normalizesOpenAiGeminiAndChatEndpoints() {
        assertEquals(
            "https://api.openai.com/v1/audio/transcriptions",
            parser.buildAudioTranscriptionsUrl("https://api.openai.com/v1")
        )
        assertEquals(
            "https://api.openai.com/v1/audio/transcriptions",
            parser.buildAudioTranscriptionsUrl("https://api.openai.com/v1/")
        )
        assertEquals(
            "https://api.openai.com/v1/audio/transcriptions",
            parser.buildAudioTranscriptionsUrl("https://api.openai.com/v1/chat/completions")
        )
        assertEquals(
            "https://api.openai.com/v1/audio/transcriptions",
            parser.buildAudioTranscriptionsUrl("https://api.openai.com/v1/chat/completions/")
        )
        assertEquals(
            "https://generativelanguage.googleapis.com/v1beta/openai/audio/transcriptions",
            parser.buildAudioTranscriptionsUrl("https://generativelanguage.googleapis.com/v1beta/openai")
        )
        assertEquals(
            "https://generativelanguage.googleapis.com/v1beta/openai/audio/transcriptions",
            parser.buildAudioTranscriptionsUrl("https://generativelanguage.googleapis.com/v1beta/openai/")
        )
        assertEquals(
            "https://api.deepseek.com/audio/transcriptions",
            parser.buildAudioTranscriptionsUrl("https://api.deepseek.com")
        )
        assertEquals(
            "https://custom.example.com/v1/audio/transcriptions",
            parser.buildAudioTranscriptionsUrl("https://custom.example.com/v1/audio/transcriptions")
        )
    }

    @Test
    fun transcribeAudioWav_validatesEmptyWavAndMissingApiKey() = runTest {
        val validSettings = AppSettings(
            llmEnabled = true,
            llmBaseUrl = baseUrl(trailingSlash = false),
            llmApiKey = "sk-audio"
        )
        val emptyResult = parser.transcribeAudioWav(ByteArray(0), validSettings)
        assertTrue(emptyResult.isFailure)
        assertEquals(0, capturedRequests.size)

        val remoteNoKeySettings = AppSettings(
            llmEnabled = true,
            llmBaseUrl = "https://api.openai.com/v1",
            llmApiKey = ""
        )
        val wav = InAppAudioRecorder.buildWavBytes(byteArrayOf(0x01, 0x02, 0x03, 0x04))
        val noKeyResult = parser.transcribeAudioWav(wav, remoteNoKeySettings)
        assertTrue(noKeyResult.isFailure)
        assertTrue(noKeyResult.exceptionOrNull()!!.message!!.contains("API Key"))
    }

    @Test
    fun transcribeAudioWav_postsMultipartToAudioEndpointAndExtractsJsonOrPlainText() = runTest {
        val sampleWav = InAppAudioRecorder.buildWavBytes(byteArrayOf(0x10, 0x20, 0x30, 0x40))
        nextStatusCode = 200
        nextResponseBody = """{"text": "  午饭花了35元，微信支付  "}"""

        val settings = AppSettings(
            llmEnabled = true,
            llmBaseUrl = baseUrl(trailingSlash = true) + "chat/completions",
            llmApiKey = "sk-whisper-123",
            llmModel = "gpt-4o-mini"
        )

        val jsonResult = parser.transcribeAudioWav(sampleWav, settings)
        assertTrue(jsonResult.isSuccess)
        assertEquals("午饭花了35元，微信支付", jsonResult.getOrThrow())
        assertEquals(1, capturedRequests.size)
        val req = capturedRequests[0]
        assertEquals("/v1/audio/transcriptions", req.path)
        assertEquals("Bearer sk-whisper-123", req.authHeader)
        assertTrue(req.contentType.contains("multipart/form-data"))
        val bodyStr = req.bodyText
        assertTrue(bodyStr.contains("name=\"file\"; filename=\"speech.wav\""))
        assertTrue(bodyStr.contains("Content-Type: audio/wav"))
        assertTrue(bodyStr.contains("name=\"model\""))
        assertTrue(bodyStr.contains("whisper-1"))
        assertTrue(bodyStr.contains("RIFF"))

        // 验证纯文本响应格式兼容
        capturedRequests.clear()
        nextResponseBody = "打车18元 支付宝\n"
        val plainResult = parser.transcribeAudioWav(sampleWav, settings)
        assertTrue(plainResult.isSuccess)
        assertEquals("打车18元 支付宝", plainResult.getOrThrow())
    }

    @Test
    fun transcribeAudioWav_fallsBackToChatCompletionsInputAudioWhenEndpointUnsupported() = runTest {
        val sampleWav = InAppAudioRecorder.buildWavBytes(byteArrayOf(0x05, 0x06, 0x07, 0x08))
        routeHandler = { _, req ->
            when (req.path) {
                "/v1beta/openai/audio/transcriptions" ->
                    404 to """{"error":{"message":"audio/transcriptions not supported"}}"""
                "/v1beta/openai/chat/completions" ->
                    200 to wrapAssistantReply("昨天买咖啡花了28元")
                else ->
                    500 to "unexpected path ${req.path}"
            }
        }

        val port = server.address.port
        val settings = AppSettings(
            llmEnabled = true,
            llmBaseUrl = "http://127.0.0.1:$port/v1beta/openai/",
            llmApiKey = "sk-gemini-test",
            llmModel = "gemini-2.5-flash"
        )

        val result = parser.transcribeAudioWav(sampleWav, settings)
        assertTrue(result.isSuccess)
        assertEquals("昨天买咖啡花了28元", result.getOrThrow())
        assertEquals(2, capturedRequests.size)
        assertEquals("/v1beta/openai/audio/transcriptions", capturedRequests[0].path)
        assertEquals("/v1beta/openai/chat/completions", capturedRequests[1].path)

        val chatJson = Json.parseToJsonElement(capturedRequests[1].bodyText).jsonObject
        assertEquals("gemini-2.5-flash", chatJson["model"]!!.jsonPrimitive.content)
        val messages = chatJson["messages"]!!.jsonArray
        val userContent = messages[1].jsonObject["content"]!!.jsonArray
        val audioPart = userContent[1].jsonObject
        assertEquals("input_audio", audioPart["type"]!!.jsonPrimitive.content)
        val inputAudio = audioPart["input_audio"]!!.jsonObject
        assertEquals("wav", inputAudio["format"]!!.jsonPrimitive.content)
        val decodedWav = Base64.getDecoder().decode(inputAudio["data"]!!.jsonPrimitive.content)
        assertArrayEquals(sampleWav, decodedWav)
    }

    @Test
    fun transcribeAudioWav_returnsFriendlyChineseMessageWhenBothEndpointsFail() = runTest {
        val sampleWav = InAppAudioRecorder.buildWavBytes(byteArrayOf(0x11, 0x22))
        routeHandler = { _, req ->
            when (req.path) {
                "/v1/audio/transcriptions" ->
                    404 to """{"error":{"message":"Not Found"}}"""
                "/v1/chat/completions" ->
                    400 to """{"error":{"message":"unknown variant input_audio, expected text"}}"""
                else ->
                    500 to "unexpected"
            }
        }

        val settings = AppSettings(
            llmEnabled = true,
            llmBaseUrl = baseUrl(trailingSlash = false),
            llmApiKey = "sk-deepseek",
            llmModel = "deepseek-chat"
        )

        val result = parser.transcribeAudioWav(sampleWav, settings)
        assertTrue(result.isFailure)
        val msg = result.exceptionOrNull()!!.message.orEmpty()
        assertTrue("Expected model hint in message: $msg", msg.contains("deepseek-chat"))
        assertTrue("Expected multimodal/provider hint in message: $msg", msg.contains("OpenAI") || msg.contains("Gemini"))
        assertTrue("Expected keyboard voice hint in message: $msg", msg.contains("键盘语音"))
    }

    @Test
    fun transcribeAudioWav_fallsBackOn400_405_415_422_andFailsDirectlyOn401() = runTest {
        val sampleWav = InAppAudioRecorder.buildWavBytes(byteArrayOf(0x01, 0x02, 0x03, 0x04))
        val settings = AppSettings(
            llmEnabled = true,
            llmBaseUrl = baseUrl(trailingSlash = false),
            llmApiKey = "sk-test",
            llmModel = "gpt-4o-audio-preview"
        )

        for (status in listOf(400, 405, 415, 422)) {
            capturedRequests.clear()
            routeHandler = { _, req ->
                when (req.path) {
                    "/v1/audio/transcriptions" -> status to """{"error":{"message":"unsupported status $status"}}"""
                    "/v1/chat/completions" -> 200 to wrapAssistantReply("回退成功$status")
                    else -> 500 to "unexpected"
                }
            }
            val res = parser.transcribeAudioWav(sampleWav, settings)
            assertTrue("Status $status should trigger fallback", res.isSuccess)
            assertEquals("回退成功$status", res.getOrThrow())
            assertEquals(2, capturedRequests.size)
        }

        // 401 鉴权失败不应再请求 /chat/completions
        capturedRequests.clear()
        routeHandler = { _, _ ->
            401 to """{"error":{"message":"Invalid API key"}}"""
        }
        val unauthRes = parser.transcribeAudioWav(sampleWav, settings)
        assertTrue(unauthRes.isFailure)
        assertEquals(1, capturedRequests.size)
        assertTrue(unauthRes.exceptionOrNull()!!.message!!.contains("401"))
    }
}
