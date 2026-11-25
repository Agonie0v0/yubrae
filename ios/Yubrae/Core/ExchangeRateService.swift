import Foundation

public struct FetchedRateTable: Equatable, Sendable {
    public let baseCurrency: String
    public let date: String
    public let ratesMicros: [String: Int64]
    public let sourcesByCurrency: [String: String]

    public init(
        baseCurrency: String,
        date: String,
        ratesMicros: [String: Int64],
        sourcesByCurrency: [String: String]
    ) {
        self.baseCurrency = baseCurrency
        self.date = date
        self.ratesMicros = ratesMicros
        self.sourcesByCurrency = sourcesByCurrency
    }
}

public enum ExchangeRateError: LocalizedError {
    case invalidURL(String)
    case httpError(Int, String)
    case emptyResponse
    case invalidJSON(String)
    case allSourcesFailed(primary: String, fallback: String)

    public var errorDescription: String? {
        switch self {
        case .invalidURL(let url):
            return "汇率接口地址无效：\(url)"
        case .httpError(let code, let url):
            return "HTTP \(code) from \(url)"
        case .emptyResponse:
            return "汇率接口返回空内容"
        case .invalidJSON(let reason):
            return "解析汇率响应失败：\(reason)"
        case .allSourcesFailed(let primary, let fallback):
            return "汇率拉取失败：主源(\(primary))，备源(\(fallback))"
        }
    }
}

/// 双源公开汇率拉取服务（与 Android `ExchangeRateApi.kt` 完全一致）：
/// - 主源：`https://api.frankfurter.app/latest?from=<base>`
/// - 备源：`https://open.er-api.com/v6/latest/<base>`（主源缺失币种如 TWD 时自动补全，失败时全量兜底）
public final class ExchangeRateService: Sendable {
    public static let frankfurterBaseUrl = "https://api.frankfurter.app"
    public static let openErBaseUrl = "https://open.er-api.com/v6"
    public static let sourceFrankfurter = "FRANKFURTER"
    public static let sourceOpenErApi = "OPEN_ER_API"

    private let session: URLSession
    private let primaryBaseUrl: String
    private let fallbackBaseUrl: String

    public init(
        session: URLSession = .shared,
        primaryBaseUrl: String = ExchangeRateService.frankfurterBaseUrl,
        fallbackBaseUrl: String = ExchangeRateService.openErBaseUrl
    ) {
        self.session = session
        self.primaryBaseUrl = primaryBaseUrl
        self.fallbackBaseUrl = fallbackBaseUrl
    }

    public func fetchLatestRates(
        baseCurrency: String = CurrencyCatalog.defaultBaseCurrency,
        requiredTargets: [String] = CurrencyCatalog.all.map(\.code)
    ) async throws -> FetchedRateTable {
        let normalizedBase = CurrencyCatalog.normalize(baseCurrency)
        let encodedBase = normalizedBase.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? normalizedBase
        let normalizedTargets = Array(Set(
            requiredTargets
                .map { CurrencyCatalog.normalize($0) }
                .filter { !$0.isEmpty && $0 != normalizedBase }
        ))

        let primaryUrl = "\(primaryBaseUrl.trimmingCharacters(in: CharacterSet(charactersIn: "/")))/latest?from=\(encodedBase)"
        var primaryErrorMessage = "unknown"

        do {
            let data = try await executeGet(urlString: primaryUrl)
            let primary = try Self.parseFrankfurterResponse(data, expectedBase: normalizedBase)

            let missingRequired = normalizedTargets.contains { primary.ratesMicros[$0] == nil }
            if !missingRequired {
                return FetchedRateTable(
                    baseCurrency: normalizedBase,
                    date: primary.date,
                    ratesMicros: primary.ratesMicros,
                    sourcesByCurrency: primary.ratesMicros.mapValues { _ in Self.sourceFrankfurter }
                )
            }

            let fallbackUrl = "\(fallbackBaseUrl.trimmingCharacters(in: CharacterSet(charactersIn: "/")))/latest/\(encodedBase)"
            if let fbData = try? await executeGet(urlString: fallbackUrl),
               let fallback = try? Self.parseOpenErResponse(fbData, expectedBase: normalizedBase) {
                var mergedRates = fallback.ratesMicros
                var mergedSources = fallback.ratesMicros.mapValues { _ in Self.sourceOpenErApi }
                for (code, rate) in primary.ratesMicros {
                    mergedRates[code] = rate
                    mergedSources[code] = Self.sourceFrankfurter
                }
                return FetchedRateTable(
                    baseCurrency: normalizedBase,
                    date: primary.date,
                    ratesMicros: mergedRates,
                    sourcesByCurrency: mergedSources
                )
            }

            return FetchedRateTable(
                baseCurrency: normalizedBase,
                date: primary.date,
                ratesMicros: primary.ratesMicros,
                sourcesByCurrency: primary.ratesMicros.mapValues { _ in Self.sourceFrankfurter }
            )
        } catch {
            primaryErrorMessage = error.localizedDescription
        }

        let fallbackUrl = "\(fallbackBaseUrl.trimmingCharacters(in: CharacterSet(charactersIn: "/")))/latest/\(encodedBase)"
        do {
            let data = try await executeGet(urlString: fallbackUrl)
            let fallback = try Self.parseOpenErResponse(data, expectedBase: normalizedBase)
            return FetchedRateTable(
                baseCurrency: normalizedBase,
                date: fallback.date,
                ratesMicros: fallback.ratesMicros,
                sourcesByCurrency: fallback.ratesMicros.mapValues { _ in Self.sourceOpenErApi }
            )
        } catch {
            throw ExchangeRateError.allSourcesFailed(
                primary: primaryErrorMessage,
                fallback: error.localizedDescription
            )
        }
    }

    /// 拉取最新汇率并直接写入 `LedgerStore`，返回同步的货币对数量。
    @discardableResult
    public func refreshRates(in store: LedgerStore, baseCurrency: String? = nil) async throws -> Int {
        let base = await MainActor.run {
            baseCurrency ?? store.settings.baseCurrency
        }
        let table = try await fetchLatestRates(baseCurrency: base)
        return await MainActor.run {
            store.applyFetchedRates(table)
        }
    }

    // MARK: - Parsers (Testable Pure Functions)

    public static func parseFrankfurterResponse(
        _ data: Data,
        expectedBase: String
    ) throws -> (date: String, ratesMicros: [String: Int64], source: String) {
        guard let root = try JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            throw ExchangeRateError.invalidJSON("Frankfurter 响应不是 JSON 对象")
        }
        let rawDate = (root["date"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        let date = isValidDate(rawDate) ? rawDate : utcTodayString()
        guard let ratesObj = root["rates"] as? [String: Any] else {
            throw ExchangeRateError.invalidJSON("Frankfurter 响应缺少 rates 字段")
        }
        let rates = extractRatesMicros(from: ratesObj, baseCurrency: expectedBase)
        guard !rates.isEmpty else {
            throw ExchangeRateError.invalidJSON("Frankfurter 响应未包含有效汇率")
        }
        return (date, rates, sourceFrankfurter)
    }

    public static func parseOpenErResponse(
        _ data: Data,
        expectedBase: String
    ) throws -> (date: String, ratesMicros: [String: Int64], source: String) {
        guard let root = try JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            throw ExchangeRateError.invalidJSON("open.er-api 响应不是 JSON 对象")
        }
        if let status = root["result"] as? String, status.lowercased() != "success" {
            let errType = (root["error-type"] as? String) ?? "unknown"
            throw ExchangeRateError.invalidJSON("open.er-api 返回错误状态: \(errType)")
        }
        let date: String
        if let unixSec = (root["time_last_update_unix"] as? NSNumber)?.int64Value, unixSec > 0 {
            date = DefaultSeedData.formatISODate(epochMillis: unixSec * 1000, timeZone: TimeZone(secondsFromGMT: 0)!)
        } else {
            date = utcTodayString()
        }
        guard let ratesObj = root["rates"] as? [String: Any] else {
            throw ExchangeRateError.invalidJSON("open.er-api 响应缺少 rates 字段")
        }
        let rates = extractRatesMicros(from: ratesObj, baseCurrency: expectedBase)
        guard !rates.isEmpty else {
            throw ExchangeRateError.invalidJSON("open.er-api 响应未包含有效汇率")
        }
        return (date, rates, sourceOpenErApi)
    }

    // MARK: - Private Helpers

    private func executeGet(urlString: String) async throws -> Data {
        guard let url = URL(string: urlString) else {
            throw ExchangeRateError.invalidURL(urlString)
        }
        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        request.timeoutInterval = 15
        request.setValue("application/json", forHTTPHeaderField: "Accept")

        let (data, response) = try await session.data(for: request)
        guard let http = response as? HTTPURLResponse else {
            throw ExchangeRateError.emptyResponse
        }
        guard (200...299).contains(http.statusCode) else {
            throw ExchangeRateError.httpError(http.statusCode, urlString)
        }
        guard !data.isEmpty else {
            throw ExchangeRateError.emptyResponse
        }
        return data
    }

    private static func extractRatesMicros(from dict: [String: Any], baseCurrency: String) -> [String: Int64] {
        let normalizedBase = CurrencyCatalog.normalize(baseCurrency)
        var result: [String: Int64] = [:]
        for (rawCode, value) in dict {
            let code = rawCode.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
            if code.isEmpty || code == normalizedBase { continue }
            if let str = value as? String {
                if let micros = MoneyAmount.parseRateToMicros(str), micros > 0 {
                    result[code] = micros
                }
            } else if let number = value as? NSNumber {
                // 过滤 JSON Bool（NSNumber 包装的布尔值）
                if CFGetTypeID(number) == CFBooleanGetTypeID() { continue }
                let dec = number.decimalValue
                if dec > 0 {
                    result[code] = MoneyAmount.rateFromDecimal(dec)
                }
            }
        }
        return result
    }

    private static func isValidDate(_ s: String) -> Bool {
        guard s.count == 10 else { return false }
        let chars = Array(s)
        return chars[4] == "-" && chars[7] == "-" &&
            chars.enumerated().allSatisfy { idx, ch in
                idx == 4 || idx == 7 || (ch >= "0" && ch <= "9")
            }
    }

    private static func utcTodayString() -> String {
        DefaultSeedData.formatISODate(
            epochMillis: currentEpochMillis(),
            timeZone: TimeZone(secondsFromGMT: 0)!
        )
    }
}
