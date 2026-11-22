package com.kairoledger.app.domain

import com.kairoledger.app.core.model.CurrencyCatalog
import com.kairoledger.app.core.model.MoneyAmount
import com.kairoledger.app.data.db.ExchangeRateEntity
import com.kairoledger.app.data.network.ExchangeRateApi
import com.kairoledger.app.data.repository.ExchangeRateRepositoryImpl
import com.kairoledger.app.testutil.TestHttpServer
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicInteger

class ExchangeRateRepositoryTest {

    private lateinit var server: TestHttpServer
    private lateinit var baseUrl: String
    private lateinit var okHttpClient: OkHttpClient
    private lateinit var dao: FakeExchangeRateDao

    @Volatile
    private var primaryHandler: (String) -> Pair<Int, String> = {
        200 to """{"amount":1.0,"base":"CNY","date":"2026-11-20","rates":{"USD":0.1380,"EUR":0.1300,"GBP":0.1090,"TWD":4.4800}}"""
    }

    @Volatile
    private var fallbackHandler: (String) -> Pair<Int, String> = {
        200 to """{"result":"success","time_last_update_unix":1795132800,"base_code":"CNY","rates":{"CNY":1,"USD":0.1385,"EUR":0.1310,"GBP":0.1095,"TWD":4.5000}}"""
    }

    private val primaryHits = AtomicInteger(0)
    private val fallbackHits = AtomicInteger(0)

    @Before
    fun setUp() {
        dao = FakeExchangeRateDao()
        okHttpClient = OkHttpClient()
        primaryHits.set(0)
        fallbackHits.set(0)

        server = TestHttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/frankfurter") { exchange ->
                primaryHits.incrementAndGet()
                val query = exchange.requestURI.rawQuery.orEmpty()
                val (status, body) = primaryHandler(query)
                val bytes = body.toByteArray(StandardCharsets.UTF_8)
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(status, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            createContext("/opener") { exchange ->
                fallbackHits.incrementAndGet()
                val path = exchange.requestURI.path
                val (status, body) = fallbackHandler(path)
                val bytes = body.toByteArray(StandardCharsets.UTF_8)
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(status, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            start()
        }
        baseUrl = "http://127.0.0.1:${server.address.port}"
    }

    @After
    fun tearDown() {
        server.stop(0)
    }

    private fun createApi(): ExchangeRateApi = ExchangeRateApi(
        okHttpClient = okHttpClient,
        primaryBaseUrl = "$baseUrl/frankfurter",
        fallbackBaseUrl = "$baseUrl/opener"
    )

    @Test
    fun refreshFromNetwork_usesPrimaryAndAvoidsFallbackWhenAllRequiredPresent() = runTest {
        primaryHandler = {
            val allRates = CurrencyCatalog.all
                .filter { it.code != "CNY" }
                .joinToString(",") { "\"${it.code}\":0.15" }
            200 to """{"amount":1.0,"base":"CNY","date":"2026-11-20","rates":{$allRates}}"""
        }

        var syncedAt = 0L
        val repo = ExchangeRateRepositoryImpl(
            exchangeRateDao = dao,
            exchangeRateApi = createApi(),
            onRateSynced = { syncedAt = it },
            currentDateProvider = { LocalDate.of(2026, 11, 21) },
            clockMillisProvider = { 1_795_200_000_000L }
        )

        val result = repo.refreshFromNetwork("CNY")
        assertTrue(result.isSuccess)
        assertEquals(1, primaryHits.get())
        assertEquals(0, fallbackHits.get())
        assertEquals(1_795_200_000_000L, syncedAt)

        val cnyToUsd = repo.getRate("CNY", "USD")
        assertEquals(150_000L, cnyToUsd.rateMicros)
        assertEquals(ExchangeRateApi.SOURCE_FRANKFURTER, cnyToUsd.source)

        val usdToCny = repo.getRate("USD", "CNY")
        assertEquals(MoneyAmount.invertRate(150_000L), usdToCny.rateMicros)
        assertEquals("2026-11-20", usdToCny.date)
    }

    @Test
    fun refreshFromNetwork_supplementsMissingCurrenciesFromOpenErApi() = runTest {
        // Frankfurter typically omits TWD; fallback fills TWD while keeping Frankfurter for USD/EUR/GBP
        primaryHandler = {
            200 to """{"amount":1.0,"base":"CNY","date":"2026-11-20","rates":{"USD":0.1380,"EUR":0.1300,"GBP":0.1090}}"""
        }
        fallbackHandler = {
            200 to """{"result":"success","time_last_update_unix":1795132800,"base_code":"CNY","rates":{"CNY":1,"USD":0.1400,"TWD":4.4800}}"""
        }

        val repo = ExchangeRateRepositoryImpl(
            exchangeRateDao = dao,
            exchangeRateApi = createApi(),
            currentDateProvider = { LocalDate.of(2026, 11, 21) }
        )

        val result = repo.refreshFromNetwork("CNY")
        assertTrue(result.isSuccess)
        assertEquals(1, primaryHits.get())
        assertEquals(1, fallbackHits.get())

        val usdQuote = repo.getRate("CNY", "USD")
        assertEquals(138_000L, usdQuote.rateMicros)
        assertEquals(ExchangeRateApi.SOURCE_FRANKFURTER, usdQuote.source)

        val twdQuote = repo.getRate("CNY", "TWD")
        assertEquals(4_480_000L, twdQuote.rateMicros)
        assertEquals(ExchangeRateApi.SOURCE_OPEN_ER_API, twdQuote.source)
    }

    @Test
    fun refreshFromNetwork_fallsBackToOpenErApiOnPrimaryHttpFailure() = runTest {
        primaryHandler = { 503 to """{"message":"unavailable"}""" }
        fallbackHandler = {
            200 to """{"result":"success","time_last_update_unix":1795132800,"base_code":"CNY","rates":{"CNY":1,"USD":0.1385,"GBP":0.1095}}"""
        }

        val repo = ExchangeRateRepositoryImpl(
            exchangeRateDao = dao,
            exchangeRateApi = createApi()
        )

        val result = repo.refreshFromNetwork("CNY")
        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrThrow())
        val quote = repo.getRate("CNY", "USD")
        assertEquals(138_500L, quote.rateMicros)
        assertEquals(ExchangeRateApi.SOURCE_OPEN_ER_API, quote.source)
    }

    @Test
    fun refreshFromNetwork_returnsFailureWhenBothSourcesFail() = runTest {
        primaryHandler = { 500 to "error" }
        fallbackHandler = { 200 to """{"result":"error","error-type":"unsupported-code"}""" }

        var syncedCalled = false
        val repo = ExchangeRateRepositoryImpl(
            exchangeRateDao = dao,
            exchangeRateApi = createApi(),
            onRateSynced = { syncedCalled = true }
        )

        val result = repo.refreshFromNetwork("CNY")
        assertTrue(result.isFailure)
        assertEquals(false, syncedCalled)
    }

    @Test
    fun getRate_resolvesIdentityDirectInverseCrossAndBuiltinFallback() = runTest {
        val repo = ExchangeRateRepositoryImpl(
            exchangeRateDao = dao,
            exchangeRateApi = createApi(),
            currentDateProvider = { LocalDate.of(2026, 11, 21) }
        )

        // 1. Same currency -> 1_000_000L
        val same = repo.getRate("usd", "USD")
        assertEquals(MoneyAmount.RATE_SCALE, same.rateMicros)
        assertEquals("IDENTITY", same.source)

        // 2. Empty DB -> Builtin CurrencyCatalog fallback
        val builtinUsdCny = repo.getRate("USD", "CNY")
        assertEquals(7_240_000L, builtinUsdCny.rateMicros)
        assertEquals(ExchangeRateRepositoryImpl.SOURCE_BUILTIN, builtinUsdCny.source)

        val builtinUsdEur = repo.getRate("USD", "EUR")
        assertEquals(MoneyAmount.crossRate(7_240_000L, 7_620_000L), builtinUsdEur.rateMicros)
        assertEquals(ExchangeRateRepositoryImpl.SOURCE_BUILTIN, builtinUsdEur.source)

        // 3. Only reverse rate stored (CNY -> GBP = 0.110000) -> inverted to GBP -> CNY
        dao.upsert(
            ExchangeRateEntity(
                baseCurrency = "CNY",
                targetCurrency = "GBP",
                rateMicros = 110_000L,
                date = "2026-11-10",
                source = "FRANKFURTER"
            )
        )
        val invertedGbpCny = repo.getRate("GBP", "CNY")
        assertEquals(MoneyAmount.invertRate(110_000L), invertedGbpCny.rateMicros)

        // 4. Cross rate via CNY when USD -> CNY and CNY -> GBP are in DB
        dao.upsert(
            ExchangeRateEntity(
                baseCurrency = "USD",
                targetCurrency = "CNY",
                rateMicros = 7_200_000L,
                date = "2026-11-10",
                source = "FRANKFURTER"
            )
        )
        val crossUsdGbp = repo.getRate("USD", "GBP")
        val expectedGbpToCny = MoneyAmount.invertRate(110_000L)
        assertEquals(MoneyAmount.crossRate(7_200_000L, expectedGbpToCny), crossUsdGbp.rateMicros)
        assertEquals(ExchangeRateRepositoryImpl.SOURCE_CROSS_CNY, crossUsdGbp.source)
    }

    @Test
    fun getRate_respectsHistoricalOnDateAndFallsBackToLatest() = runTest {
        val repo = ExchangeRateRepositoryImpl(
            exchangeRateDao = dao,
            exchangeRateApi = createApi()
        )
        dao.upsertAll(
            listOf(
                ExchangeRateEntity(
                    baseCurrency = "USD",
                    targetCurrency = "CNY",
                    rateMicros = 7_100_000L,
                    date = "2026-10-01",
                    source = "FRANKFURTER"
                ),
                ExchangeRateEntity(
                    baseCurrency = "USD",
                    targetCurrency = "CNY",
                    rateMicros = 7_250_000L,
                    date = "2026-10-15",
                    source = "FRANKFURTER"
                ),
                ExchangeRateEntity(
                    baseCurrency = "USD",
                    targetCurrency = "CNY",
                    rateMicros = 7_320_000L,
                    date = "2026-11-05",
                    source = "FRANKFURTER"
                )
            )
        )

        // On 2026-10-20 -> picks closest on or before (2026-10-15)
        val midOct = repo.getRate("USD", "CNY", onDate = "2026-10-20")
        assertEquals(7_250_000L, midOct.rateMicros)
        assertEquals("2026-10-15", midOct.date)

        // On 2026-09-15 (earlier than any record) -> falls back to getLatest (2026-11-05)
        val beforeFirst = repo.getRate("USD", "CNY", onDate = "2026-09-15")
        assertEquals(7_320_000L, beforeFirst.rateMicros)
        assertEquals("2026-11-05", beforeFirst.date)
    }

    @Test
    fun saveCustomRate_persistsTodayForwardAndInverseAsCustom() = runTest {
        val repo = ExchangeRateRepositoryImpl(
            exchangeRateDao = dao,
            exchangeRateApi = createApi(),
            currentDateProvider = { LocalDate.of(2026, 11, 22) },
            clockMillisProvider = { 1_795_300_000_000L }
        )

        repo.saveCustomRate("USD", "CNY", 7_350_000L)

        val forward = repo.getRate("USD", "CNY")
        assertEquals(7_350_000L, forward.rateMicros)
        assertEquals("CUSTOM", forward.source)
        assertEquals("2026-11-22", forward.date)

        val inverse = repo.getRate("CNY", "USD")
        assertEquals(MoneyAmount.invertRate(7_350_000L), inverse.rateMicros)
        assertEquals("CUSTOM", inverse.source)
    }
}
