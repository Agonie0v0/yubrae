package com.kairoledger.app.data.repository

import com.kairoledger.app.core.contract.ExchangeRateProvider
import com.kairoledger.app.core.contract.RateQuote
import com.kairoledger.app.core.model.CurrencyCatalog
import com.kairoledger.app.core.model.MoneyAmount
import com.kairoledger.app.data.db.ExchangeRateDao
import com.kairoledger.app.data.db.ExchangeRateEntity
import com.kairoledger.app.data.db.KairoDatabase
import com.kairoledger.app.data.network.ExchangeRateApi
import com.kairoledger.app.data.prefs.SettingsStore
import kotlinx.coroutines.flow.Flow
import okhttp3.OkHttpClient
import java.time.LocalDate
import java.time.ZoneId

/**
 * 汇率仓库实现：
 * - 支持纯 DAO + 回调主构造函数（便于 JVM 单元测试）
 * - 提供便捷次构造函数供 AppContainer / Worker 注入
 */
class ExchangeRateRepositoryImpl(
    private val exchangeRateDao: ExchangeRateDao,
    private val exchangeRateApi: ExchangeRateApi,
    private val onRateSynced: suspend (Long) -> Unit = {},
    private val currentDateProvider: () -> LocalDate = { LocalDate.now(ZoneId.systemDefault()) },
    private val clockMillisProvider: () -> Long = { System.currentTimeMillis() }
) : ExchangeRateProvider {

    constructor(
        exchangeRateDao: ExchangeRateDao,
        okHttpClient: OkHttpClient,
        settingsStore: SettingsStore
    ) : this(
        exchangeRateDao = exchangeRateDao,
        exchangeRateApi = ExchangeRateApi(okHttpClient),
        onRateSynced = { epochMillis -> settingsStore.markRateSynced(epochMillis) }
    )

    constructor(
        db: KairoDatabase,
        okHttpClient: OkHttpClient,
        settingsStore: SettingsStore
    ) : this(
        exchangeRateDao = db.exchangeRateDao(),
        okHttpClient = okHttpClient,
        settingsStore = settingsStore
    )

    override fun observeRates(): Flow<List<ExchangeRateEntity>> = exchangeRateDao.observeAll()

    override suspend fun getRate(
        fromCurrency: String,
        toCurrency: String,
        onDate: String?
    ): RateQuote {
        val from = normalizeCurrency(fromCurrency)
        val to = normalizeCurrency(toCurrency)
        val dateFilter = onDate?.trim()?.takeIf { it.isNotEmpty() }
        val fallbackDate = dateFilter ?: currentDateProvider().toString()

        if (from == to) {
            return RateQuote(
                fromCurrency = from,
                toCurrency = to,
                rateMicros = MoneyAmount.RATE_SCALE,
                date = fallbackDate,
                source = SOURCE_IDENTITY
            )
        }

        // 1. 直接查询 from -> to
        findStoredEntity(from, to, dateFilter)
            ?.takeIf { it.rateMicros > 0L }
            ?.let { entity ->
                return RateQuote(
                    fromCurrency = from,
                    toCurrency = to,
                    rateMicros = entity.rateMicros,
                    date = entity.date,
                    source = entity.source
                )
            }

        // 2. 查询反向 to -> from 并取倒数
        findStoredEntity(to, from, dateFilter)
            ?.takeIf { it.rateMicros > 0L }
            ?.let { entity ->
                return RateQuote(
                    fromCurrency = from,
                    toCurrency = to,
                    rateMicros = MoneyAmount.invertRate(entity.rateMicros),
                    date = entity.date,
                    source = entity.source
                )
            }

        // 3. 通过中间币种 CNY 推导交叉汇率
        if (from != PIVOT_CURRENCY && to != PIVOT_CURRENCY) {
            val fromToCny = findStoredRateToPivot(from, dateFilter)
            val toToCny = findStoredRateToPivot(to, dateFilter)
            if (fromToCny != null && toToCny != null) {
                val cross = MoneyAmount.crossRate(fromToCny.first, toToCny.first)
                val latestDate = maxOf(fromToCny.second, toToCny.second)
                return RateQuote(
                    fromCurrency = from,
                    toCurrency = to,
                    rateMicros = cross,
                    date = latestDate,
                    source = SOURCE_CROSS_CNY
                )
            }
        }

        // 4. 兜底 CurrencyCatalog 内置参考汇率
        val fromCnyRate = CurrencyCatalog.find(from).fallbackRateToCnyMicros
        val toCnyRate = CurrencyCatalog.find(to).fallbackRateToCnyMicros
        val fallbackRate = when {
            to == PIVOT_CURRENCY -> fromCnyRate
            from == PIVOT_CURRENCY -> MoneyAmount.invertRate(toCnyRate)
            else -> MoneyAmount.crossRate(fromCnyRate, toCnyRate)
        }
        return RateQuote(
            fromCurrency = from,
            toCurrency = to,
            rateMicros = fallbackRate,
            date = fallbackDate,
            source = SOURCE_BUILTIN
        )
    }

    override suspend fun refreshFromNetwork(baseCurrency: String): Result<Int> {
        val normalizedBase = normalizeCurrency(baseCurrency)
        val fetchResult = exchangeRateApi.fetchLatestRates(
            baseCurrency = normalizedBase,
            requiredTargets = CurrencyCatalog.all.map { it.code }
        )
        return fetchResult.fold(
            onSuccess = { table ->
                val now = clockMillisProvider()
                val entities = ArrayList<ExchangeRateEntity>(table.ratesMicros.size * 2)
                var syncedPairCount = 0
                for ((rawTarget, rateMicros) in table.ratesMicros) {
                    val target = normalizeCurrency(rawTarget)
                    if (target == normalizedBase || rateMicros <= 0L) continue
                    val source = table.sourcesByCurrency[rawTarget] ?: SOURCE_NETWORK
                    entities += ExchangeRateEntity(
                        baseCurrency = normalizedBase,
                        targetCurrency = target,
                        rateMicros = rateMicros,
                        date = table.date,
                        source = source,
                        updatedAt = now
                    )
                    entities += ExchangeRateEntity(
                        baseCurrency = target,
                        targetCurrency = normalizedBase,
                        rateMicros = MoneyAmount.invertRate(rateMicros),
                        date = table.date,
                        source = source,
                        updatedAt = now
                    )
                    syncedPairCount++
                }
                if (entities.isEmpty()) {
                    return@fold Result.failure(IllegalStateException("未获取到有效汇率数据"))
                }
                exchangeRateDao.upsertAll(entities)
                onRateSynced(now)
                Result.success(syncedPairCount)
            },
            onFailure = { error ->
                Result.failure(error)
            }
        )
    }

    override suspend fun saveCustomRate(
        fromCurrency: String,
        toCurrency: String,
        rateMicros: Long
    ) {
        val from = normalizeCurrency(fromCurrency)
        val to = normalizeCurrency(toCurrency)
        if (from == to || rateMicros <= 0L) return
        val today = currentDateProvider().toString()
        val now = clockMillisProvider()
        exchangeRateDao.upsertAll(
            listOf(
                ExchangeRateEntity(
                    baseCurrency = from,
                    targetCurrency = to,
                    rateMicros = rateMicros,
                    date = today,
                    source = SOURCE_CUSTOM,
                    updatedAt = now
                ),
                ExchangeRateEntity(
                    baseCurrency = to,
                    targetCurrency = from,
                    rateMicros = MoneyAmount.invertRate(rateMicros),
                    date = today,
                    source = SOURCE_CUSTOM,
                    updatedAt = now
                )
            )
        )
    }

    private suspend fun findStoredEntity(
        base: String,
        target: String,
        dateFilter: String?
    ): ExchangeRateEntity? {
        if (dateFilter != null) {
            exchangeRateDao.getClosestOnOrBefore(base, target, dateFilter)?.let { return it }
        }
        return exchangeRateDao.getLatest(base, target)
    }

    private suspend fun findStoredRateToPivot(
        currency: String,
        dateFilter: String?
    ): Pair<Long, String>? {
        findStoredEntity(currency, PIVOT_CURRENCY, dateFilter)
            ?.takeIf { it.rateMicros > 0L }
            ?.let { return it.rateMicros to it.date }
        findStoredEntity(PIVOT_CURRENCY, currency, dateFilter)
            ?.takeIf { it.rateMicros > 0L }
            ?.let { return MoneyAmount.invertRate(it.rateMicros) to it.date }
        return null
    }

    private fun normalizeCurrency(code: String): String =
        code.trim().uppercase().ifEmpty { CurrencyCatalog.DEFAULT_BASE_CURRENCY }

    companion object {
        private const val PIVOT_CURRENCY = CurrencyCatalog.DEFAULT_BASE_CURRENCY
        const val SOURCE_IDENTITY = "IDENTITY"
        const val SOURCE_CROSS_CNY = "CROSS_CNY"
        const val SOURCE_BUILTIN = "BUILTIN"
        const val SOURCE_CUSTOM = "CUSTOM"
        const val SOURCE_NETWORK = "NETWORK"
    }
}
