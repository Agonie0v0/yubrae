package com.kairoledger.app.data.network

import com.kairoledger.app.core.model.CurrencyCatalog
import com.kairoledger.app.core.model.MoneyAmount
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.coroutines.resume

data class FetchedRateTable(
    val baseCurrency: String,
    val date: String,
    val ratesMicros: Map<String, Long>,
    val sourcesByCurrency: Map<String, String>
)

internal data class ParsedSourceRates(
    val date: String,
    val ratesMicros: Map<String, Long>,
    val source: String
)

/**
 * 双源汇率网络拉取客户端：
 * - 主源：https://api.frankfurter.app/latest?from=<base>
 * - 备源：https://open.er-api.com/v6/latest/<base>（主源失败或缺失币种时自动兜底）
 */
class ExchangeRateApi(
    private val okHttpClient: OkHttpClient,
    private val primaryBaseUrl: String,
    private val fallbackBaseUrl: String
) {
    constructor(okHttpClient: OkHttpClient) : this(
        okHttpClient = okHttpClient,
        primaryBaseUrl = FRANKFURTER_BASE_URL,
        fallbackBaseUrl = OPEN_ER_BASE_URL
    )

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetchLatestRates(
        baseCurrency: String = CurrencyCatalog.DEFAULT_BASE_CURRENCY,
        requiredTargets: Collection<String> = CurrencyCatalog.all.map { it.code }
    ): Result<FetchedRateTable> {
        val normalizedBase = baseCurrency.trim().uppercase().ifEmpty { CurrencyCatalog.DEFAULT_BASE_CURRENCY }
        val encodedBase = URLEncoder.encode(normalizedBase, StandardCharsets.UTF_8.name())
        val normalizedTargets = requiredTargets
            .map { it.trim().uppercase() }
            .filter { it.isNotEmpty() && it != normalizedBase }
            .distinct()

        val primaryUrl = "${primaryBaseUrl.trimEnd('/')}/latest?from=$encodedBase"
        val primaryResult = fetchAndParse(primaryUrl) { body ->
            parseFrankfurterResponse(body, normalizedBase)
        }

        val primary = primaryResult.getOrNull()
        if (primary != null) {
            val missingRequired = normalizedTargets.any { it !in primary.ratesMicros }
            if (!missingRequired) {
                return Result.success(
                    FetchedRateTable(
                        baseCurrency = normalizedBase,
                        date = primary.date,
                        ratesMicros = primary.ratesMicros,
                        sourcesByCurrency = primary.ratesMicros.mapValues { SOURCE_FRANKFURTER }
                    )
                )
            }

            // 主源缺少部分币种（如 TWD 等），尝试从备源补全缺失币种
            val fallbackUrl = "${fallbackBaseUrl.trimEnd('/')}/latest/$encodedBase"
            val fallback = fetchAndParse(fallbackUrl) { body ->
                parseOpenErResponse(body, normalizedBase)
            }.getOrNull()

            if (fallback == null) {
                return Result.success(
                    FetchedRateTable(
                        baseCurrency = normalizedBase,
                        date = primary.date,
                        ratesMicros = primary.ratesMicros,
                        sourcesByCurrency = primary.ratesMicros.mapValues { SOURCE_FRANKFURTER }
                    )
                )
            }

            val mergedRates = LinkedHashMap<String, Long>(fallback.ratesMicros.size + primary.ratesMicros.size)
            val mergedSources = LinkedHashMap<String, String>(fallback.ratesMicros.size + primary.ratesMicros.size)
            for ((code, rate) in fallback.ratesMicros) {
                mergedRates[code] = rate
                mergedSources[code] = SOURCE_OPEN_ER_API
            }
            for ((code, rate) in primary.ratesMicros) {
                mergedRates[code] = rate
                mergedSources[code] = SOURCE_FRANKFURTER
            }
            return Result.success(
                FetchedRateTable(
                    baseCurrency = normalizedBase,
                    date = primary.date,
                    ratesMicros = mergedRates,
                    sourcesByCurrency = mergedSources
                )
            )
        }

        val fallbackUrl = "${fallbackBaseUrl.trimEnd('/')}/latest/$encodedBase"
        val fallbackResult = fetchAndParse(fallbackUrl) { body ->
            parseOpenErResponse(body, normalizedBase)
        }
        return fallbackResult.fold(
            onSuccess = { fallback ->
                Result.success(
                    FetchedRateTable(
                        baseCurrency = normalizedBase,
                        date = fallback.date,
                        ratesMicros = fallback.ratesMicros,
                        sourcesByCurrency = fallback.ratesMicros.mapValues { SOURCE_OPEN_ER_API }
                    )
                )
            },
            onFailure = { fallbackError ->
                val primaryMessage = primaryResult.exceptionOrNull()?.message ?: "unknown"
                val fallbackMessage = fallbackError.message ?: "unknown"
                Result.failure(
                    IOException(
                        "汇率拉取失败：主源($primaryMessage)，备源($fallbackMessage)",
                        fallbackError
                    )
                )
            }
        )
    }

    internal fun parseFrankfurterResponse(rawJson: String, expectedBase: String): ParsedSourceRates {
        val root = json.parseToJsonElement(rawJson).jsonObject
        val date = root["date"]?.jsonPrimitive?.contentOrNull
            ?.trim()
            ?.takeIf { DATE_REGEX.matches(it) }
            ?: LocalDate.now(ZoneOffset.UTC).toString()
        val ratesObj = root["rates"]?.jsonObject
            ?: throw IllegalStateException("Frankfurter 响应缺少 rates 字段")
        val rates = extractRatesMicros(ratesObj, expectedBase)
        if (rates.isEmpty()) {
            throw IllegalStateException("Frankfurter 响应未包含有效汇率")
        }
        return ParsedSourceRates(
            date = date,
            ratesMicros = rates,
            source = SOURCE_FRANKFURTER
        )
    }

    internal fun parseOpenErResponse(rawJson: String, expectedBase: String): ParsedSourceRates {
        val root = json.parseToJsonElement(rawJson).jsonObject
        val status = root["result"]?.jsonPrimitive?.contentOrNull
        if (status != null && !status.equals("success", ignoreCase = true)) {
            val errorType = root["error-type"]?.jsonPrimitive?.contentOrNull ?: "unknown"
            throw IllegalStateException("open.er-api 返回错误状态: $errorType")
        }
        val unixSeconds = root["time_last_update_unix"]?.jsonPrimitive?.longOrNull
        val date = if (unixSeconds != null && unixSeconds > 0L) {
            Instant.ofEpochSecond(unixSeconds).atZone(ZoneOffset.UTC).toLocalDate().toString()
        } else {
            LocalDate.now(ZoneOffset.UTC).toString()
        }
        val ratesObj = root["rates"]?.jsonObject
            ?: throw IllegalStateException("open.er-api 响应缺少 rates 字段")
        val rates = extractRatesMicros(ratesObj, expectedBase)
        if (rates.isEmpty()) {
            throw IllegalStateException("open.er-api 响应未包含有效汇率")
        }
        return ParsedSourceRates(
            date = date,
            ratesMicros = rates,
            source = SOURCE_OPEN_ER_API
        )
    }

    private fun extractRatesMicros(ratesObj: JsonObject, baseCurrency: String): Map<String, Long> {
        val result = LinkedHashMap<String, Long>()
        for ((rawCode, element) in ratesObj) {
            val code = rawCode.trim().uppercase()
            if (code.isEmpty() || code == baseCurrency) continue
            val primitive = element as? JsonPrimitive ?: continue
            val micros = MoneyAmount.parseRateToMicros(primitive.content) ?: continue
            if (micros > 0L) {
                result[code] = micros
            }
        }
        return result
    }

    private suspend fun fetchAndParse(
        url: String,
        parser: (String) -> ParsedSourceRates
    ): Result<ParsedSourceRates> {
        val bodyResult = executeGet(url)
        val body = bodyResult.getOrElse { return Result.failure(it) }
        return try {
            Result.success(parser(body))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(IOException("解析汇率响应失败 ($url): ${e.message}", e))
        }
    }

    private suspend fun executeGet(url: String): Result<String> =
        suspendCancellableCoroutine { cont ->
            val request = Request.Builder()
                .url(url)
                .get()
                .header("Accept", "application/json")
                .build()
            val call = okHttpClient.newCall(request)
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (cont.isActive) {
                        cont.resume(Result.failure(e))
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use { res ->
                        if (!res.isSuccessful) {
                            if (cont.isActive) {
                                cont.resume(Result.failure(IOException("HTTP ${res.code} from $url")))
                            }
                            return
                        }
                        val body = try {
                            res.body?.string().orEmpty()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            if (cont.isActive) {
                                cont.resume(Result.failure(IOException("Failed reading response from $url", e)))
                            }
                            return
                        }
                        if (body.isBlank()) {
                            if (cont.isActive) {
                                cont.resume(Result.failure(IOException("Empty response body from $url")))
                            }
                            return
                        }
                        if (cont.isActive) {
                            cont.resume(Result.success(body))
                        }
                    }
                }
            })
        }

    companion object {
        const val FRANKFURTER_BASE_URL = "https://api.frankfurter.app"
        const val OPEN_ER_BASE_URL = "https://open.er-api.com/v6"
        const val SOURCE_FRANKFURTER = "FRANKFURTER"
        const val SOURCE_OPEN_ER_API = "OPEN_ER_API"
        private val DATE_REGEX = Regex("\\d{4}-\\d{2}-\\d{2}")
    }
}
