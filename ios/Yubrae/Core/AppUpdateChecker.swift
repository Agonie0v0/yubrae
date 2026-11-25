import Foundation

public struct GitHubReleaseAssetDto: Codable, Equatable, Sendable {
    public let name: String
    public let size: Int64
    public let browserDownloadUrl: String

    enum CodingKeys: String, CodingKey {
        case name
        case size
        case browserDownloadUrl = "browser_download_url"
    }

    public init(name: String = "", size: Int64 = 0, browserDownloadUrl: String = "") {
        self.name = name
        self.size = size
        self.browserDownloadUrl = browserDownloadUrl
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        self.name = try c.decodeIfPresent(String.self, forKey: .name) ?? ""
        self.size = try c.decodeIfPresent(Int64.self, forKey: .size) ?? 0
        self.browserDownloadUrl = try c.decodeIfPresent(String.self, forKey: .browserDownloadUrl) ?? ""
    }
}

public struct GitHubReleaseDto: Codable, Equatable, Sendable {
    public let tagName: String
    public let name: String
    public let body: String
    public let draft: Bool
    public let prerelease: Bool
    public let publishedAt: String
    public let htmlUrl: String
    public let assets: [GitHubReleaseAssetDto]

    enum CodingKeys: String, CodingKey {
        case tagName = "tag_name"
        case name
        case body
        case draft
        case prerelease
        case publishedAt = "published_at"
        case htmlUrl = "html_url"
        case assets
    }

    public init(
        tagName: String = "",
        name: String = "",
        body: String = "",
        draft: Bool = false,
        prerelease: Bool = false,
        publishedAt: String = "",
        htmlUrl: String = "",
        assets: [GitHubReleaseAssetDto] = []
    ) {
        self.tagName = tagName
        self.name = name
        self.body = body
        self.draft = draft
        self.prerelease = prerelease
        self.publishedAt = publishedAt
        self.htmlUrl = htmlUrl
        self.assets = assets
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        self.tagName = try c.decodeIfPresent(String.self, forKey: .tagName) ?? ""
        self.name = try c.decodeIfPresent(String.self, forKey: .name) ?? ""
        self.body = try c.decodeIfPresent(String.self, forKey: .body) ?? ""
        self.draft = try c.decodeIfPresent(Bool.self, forKey: .draft) ?? false
        self.prerelease = try c.decodeIfPresent(Bool.self, forKey: .prerelease) ?? false
        self.publishedAt = try c.decodeIfPresent(String.self, forKey: .publishedAt) ?? ""
        self.htmlUrl = try c.decodeIfPresent(String.self, forKey: .htmlUrl) ?? ""
        self.assets = try c.decodeIfPresent([GitHubReleaseAssetDto].self, forKey: .assets) ?? []
    }
}

public struct AppUpdateInfo: Equatable, Sendable {
    public let tagName: String
    public let versionName: String
    public let releaseTitle: String
    public let releaseNotes: String
    public let publishedAt: String
    public let htmlUrl: String
    public let ipaAssetName: String?
    public let ipaDownloadUrl: String?
    public let ipaAssetSizeBytes: Int64
    public let sha256AssetUrl: String?

    public init(
        tagName: String,
        versionName: String,
        releaseTitle: String,
        releaseNotes: String,
        publishedAt: String,
        htmlUrl: String,
        ipaAssetName: String? = nil,
        ipaDownloadUrl: String? = nil,
        ipaAssetSizeBytes: Int64 = 0,
        sha256AssetUrl: String? = nil
    ) {
        self.tagName = tagName
        self.versionName = versionName
        self.releaseTitle = releaseTitle
        self.releaseNotes = releaseNotes
        self.publishedAt = publishedAt
        self.htmlUrl = htmlUrl
        self.ipaAssetName = ipaAssetName
        self.ipaDownloadUrl = ipaDownloadUrl
        self.ipaAssetSizeBytes = ipaAssetSizeBytes
        self.sha256AssetUrl = sha256AssetUrl
    }
}

public enum UpdateCheckResult: Equatable, Sendable {
    case updateAvailable(AppUpdateInfo)
    case upToDate(currentVersion: String, latestTag: String)
    case failed(reason: String, fallbackUrl: String)
}

public enum MirrorPrefixValidation: Equatable, Sendable {
    case valid(normalizedPrefix: String)
    case invalid(reason: String)
}

/// GitHub Releases 版本检查器（`Agonie0v0/yubrae`），支持 `https://` 镜像加速前缀、语义化版本对比与 `.ipa` 资产匹配。
public final class AppUpdateChecker: Sendable {
    public static let githubOwner = "Agonie0v0"
    public static let githubRepo = "yubrae"
    public static let defaultLatestApiUrl = "https://api.github.com/repos/\(githubOwner)/\(githubRepo)/releases/latest"
    public static let defaultReleasePageUrl = "https://github.com/\(githubOwner)/\(githubRepo)/releases"

    private static let sha256AssetName = "SHA256SUMS.txt"
    private static let variantSuffixRegex = try! NSRegularExpression(pattern: #"-(perf|debug)$"#, options: .caseInsensitive)

    public static var currentAppVersion: String {
        (Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String)?
            .trimmingCharacters(in: .whitespacesAndNewlines) ?? "1.2.2"
    }

    private let session: URLSession
    private let latestReleaseApiUrl: String
    private let releasePageUrl: String

    public init(
        session: URLSession = .shared,
        latestReleaseApiUrl: String = AppUpdateChecker.defaultLatestApiUrl,
        releasePageUrl: String = AppUpdateChecker.defaultReleasePageUrl
    ) {
        self.session = session
        self.latestReleaseApiUrl = latestReleaseApiUrl
        self.releasePageUrl = releasePageUrl
    }

    public func checkLatestRelease(
        currentVersion: String = AppUpdateChecker.currentAppVersion,
        mirrorPrefix: String = ""
    ) async -> UpdateCheckResult {
        let candidateUrls = Self.buildCandidateUrls(
            rawUrl: latestReleaseApiUrl,
            mirrorPrefix: mirrorPrefix,
            preferMirrorFirst: false
        )

        var lastError: String?
        var fetchedRelease: GitHubReleaseDto?

        for urlStr in candidateUrls {
            guard let url = URL(string: urlStr) else { continue }
            do {
                var request = URLRequest(url: url)
                request.httpMethod = "GET"
                request.timeoutInterval = 15
                request.setValue("application/vnd.github+json", forHTTPHeaderField: "Accept")
                request.setValue("Yubrae-iOS/\(currentVersion)", forHTTPHeaderField: "User-Agent")

                let (data, response) = try await session.data(for: request)
                guard let http = response as? HTTPURLResponse else {
                    lastError = "无效的服务器响应"
                    continue
                }
                guard (200...299).contains(http.statusCode) else {
                    lastError = "HTTP \(http.statusCode)"
                    continue
                }
                let contentType = (http.value(forHTTPHeaderField: "Content-Type") ?? "").lowercased()
                if contentType.contains("text/html") || contentType.contains("application/xhtml") {
                    lastError = "接口返回了 HTML 网页而非 JSON"
                    continue
                }
                fetchedRelease = try JSONDecoder().decode(GitHubReleaseDto.self, from: data)
                break
            } catch {
                lastError = error.localizedDescription
            }
        }

        guard let release = fetchedRelease else {
            return .failed(
                reason: "检查更新失败：\(lastError ?? "网络连接超时")",
                fallbackUrl: releasePageUrl
            )
        }

        return Self.evaluateRelease(
            release,
            currentVersion: currentVersion,
            mirrorPrefix: mirrorPrefix,
            fallbackReleasePageUrl: releasePageUrl
        )
    }

    /// 纯函数评估 `GitHubReleaseDto`，便于单元测试覆盖各种版本号与资产组合。
    public static func evaluateRelease(
        _ release: GitHubReleaseDto,
        currentVersion: String,
        mirrorPrefix: String = "",
        fallbackReleasePageUrl: String = defaultReleasePageUrl
    ) -> UpdateCheckResult {
        if release.draft {
            return .upToDate(currentVersion: currentVersion, latestTag: currentVersion)
        }

        let safeHtmlUrl = isAllowedBrowserUrl(release.htmlUrl)
            ? release.htmlUrl.trimmingCharacters(in: .whitespacesAndNewlines)
            : fallbackReleasePageUrl

        let latestVersion = normalizeVersion(release.tagName)
        if latestVersion.isEmpty {
            return .failed(reason: "无法解析最新版本标签：\(release.tagName)", fallbackUrl: safeHtmlUrl)
        }

        if compareVersions(latestVersion, currentVersion) <= 0 {
            return .upToDate(currentVersion: currentVersion, latestTag: release.tagName)
        }

        let ipaAsset = selectBestIpaAsset(from: release.assets)
        let shaAsset = release.assets.first {
            $0.name.caseInsensitiveCompare(sha256AssetName) == .orderedSame &&
                !$0.browserDownloadUrl.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        }

        // 保留原始 GitHub 直链，由展示/下载处结合用户当前镜像设置统一调用 applyMirrorPrefix，避免切换镜像时双重拼接
        let rawIpaUrl = ipaAsset?.browserDownloadUrl.trimmingCharacters(in: .whitespacesAndNewlines)

        return .updateAvailable(
            AppUpdateInfo(
                tagName: release.tagName,
                versionName: latestVersion,
                releaseTitle: release.name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? release.tagName : release.name,
                releaseNotes: release.body.trimmingCharacters(in: .whitespacesAndNewlines),
                publishedAt: release.publishedAt,
                htmlUrl: safeHtmlUrl,
                ipaAssetName: ipaAsset?.name,
                ipaDownloadUrl: rawIpaUrl,
                ipaAssetSizeBytes: ipaAsset?.size ?? 0,
                sha256AssetUrl: shaAsset?.browserDownloadUrl
            )
        )
    }

    // MARK: - Semver, Asset Selection & Mirror Helpers

    public static func normalizeVersion(_ raw: String) -> String {
        var s = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        if s.hasPrefix("v") || s.hasPrefix("V") {
            s.removeFirst()
        }
        return s.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private static func stripBuildVariantSuffix(_ ver: String) -> String {
        var result = ver.trimmingCharacters(in: .whitespacesAndNewlines)
        while true {
            let range = NSRange(result.startIndex..., in: result)
            guard let match = variantSuffixRegex.firstMatch(in: result, range: range),
                  let swiftRange = Range(match.range, in: result) else {
                break
            }
            result.removeSubrange(swiftRange)
        }
        return result
    }

    /// 语义化版本比对（与 Android `AppUpdateManager.compareVersions` 100% 一致）：
    /// 返回 `1` 表示 candidate > current，`0` 相等，`-1` 表示 candidate < current。
    public static func compareVersions(_ candidateRaw: String, _ currentRaw: String) -> Int {
        let cand = stripBuildVariantSuffix(normalizeVersion(candidateRaw))
        let curr = stripBuildVariantSuffix(normalizeVersion(currentRaw))
        let (candCore, candSuffix) = splitCoreAndSuffix(cand)
        let (currCore, currSuffix) = splitCoreAndSuffix(curr)

        let candParts = candCore.split(separator: ".").map { Int($0) ?? 0 }
        let currParts = currCore.split(separator: ".").map { Int($0) ?? 0 }
        let maxLen = max(candParts.count, currParts.count)
        for i in 0..<maxLen {
            let a = i < candParts.count ? candParts[i] : 0
            let b = i < currParts.count ? currParts[i] : 0
            if a != b {
                return a < b ? -1 : 1
            }
        }

        if candSuffix.isEmpty && !currSuffix.isEmpty { return 1 }
        if !candSuffix.isEmpty && currSuffix.isEmpty { return -1 }
        let cmp = candSuffix.caseInsensitiveCompare(currSuffix)
        switch cmp {
        case .orderedAscending: return -1
        case .orderedDescending: return 1
        case .orderedSame: return 0
        }
    }

    private static func splitCoreAndSuffix(_ ver: String) -> (String, String) {
        if let idx = ver.firstIndex(of: "-") {
            return (String(ver[..<idx]), String(ver[ver.index(after: idx)...]))
        }
        return (ver, "")
    }

    /// 从 Release 资产中匹配最佳 `.ipa` 安装包（优先非 simulator / 非 debug 且包含 yubrae）。
    public static func selectBestIpaAsset(from assets: [GitHubReleaseAssetDto]) -> GitHubReleaseAssetDto? {
        let ipas = assets.filter {
            $0.name.lowercased().hasSuffix(".ipa") &&
                !$0.browserDownloadUrl.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        }
        guard !ipas.isEmpty else { return nil }

        if let preferred = ipas.first(where: {
            let lower = $0.name.lowercased()
            return lower.contains("yubrae") && !lower.contains("simulator") && !lower.contains("debug")
        }) {
            return preferred
        }
        if let nonSim = ipas.first(where: { !$0.name.lowercased().contains("simulator") }) {
            return nonSim
        }
        return ipas.first
    }

    public static func validateMirrorPrefix(_ rawPrefix: String) -> MirrorPrefixValidation {
        let cleaned = rawPrefix.trimmingCharacters(in: .whitespacesAndNewlines)
        if cleaned.isEmpty {
            return .valid(normalizedPrefix: "")
        }
        if cleaned.unicodeScalars.contains(where: { CharacterSet.whitespacesAndNewlines.contains($0) }) {
            return .invalid(reason: "镜像地址不能包含空格，请检查后重试")
        }
        guard let comps = URLComponents(string: cleaned),
              let scheme = comps.scheme?.lowercased(),
              let host = comps.host?.lowercased(),
              !host.isEmpty,
              !host.hasPrefix("."),
              !host.hasSuffix(".") else {
            return .invalid(reason: "镜像地址格式无效，请填写完整 URL（如 https://gh-proxy.com/）")
        }
        let isLocal = host == "127.0.0.1" || host == "localhost"
        if scheme != "https" && !(scheme == "http" && isLocal) {
            return .invalid(reason: "镜像地址必须使用 https:// 协议（如 https://gh-proxy.com/）")
        }
        if comps.query != nil || comps.fragment != nil {
            return .invalid(reason: "镜像前缀不能包含查询参数或锚点，请填写路径前缀（如 https://gh-proxy.com/）")
        }
        let normalized = cleaned.hasSuffix("/") ? cleaned : "\(cleaned)/"
        return .valid(normalizedPrefix: normalized)
    }

    public static func applyMirrorPrefix(rawUrl: String, mirrorPrefix: String) -> String {
        let cleanRaw = rawUrl.trimmingCharacters(in: .whitespacesAndNewlines)
        guard case .valid(let prefix) = validateMirrorPrefix(mirrorPrefix), !prefix.isEmpty else {
            return cleanRaw
        }
        if cleanRaw.lowercased().hasPrefix(prefix.lowercased()) {
            return cleanRaw
        }
        let candidate = prefix + cleanRaw
        return URL(string: candidate) != nil ? candidate : cleanRaw
    }

    public static func buildCandidateUrls(
        rawUrl: String,
        mirrorPrefix: String,
        preferMirrorFirst: Bool = false
    ) -> [String] {
        let cleanRaw = rawUrl.trimmingCharacters(in: .whitespacesAndNewlines)
        let mirrored = applyMirrorPrefix(rawUrl: cleanRaw, mirrorPrefix: mirrorPrefix)
        if mirrored == cleanRaw {
            return [cleanRaw]
        }
        return preferMirrorFirst ? [mirrored, cleanRaw] : [cleanRaw, mirrored]
    }

    public static func isAllowedBrowserUrl(_ url: String) -> Bool {
        let trimmed = url.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty,
              !trimmed.unicodeScalars.contains(where: { CharacterSet.whitespacesAndNewlines.contains($0) }),
              let parsed = URL(string: trimmed),
              let scheme = parsed.scheme?.lowercased(),
              let host = parsed.host,
              !host.isEmpty else {
            return false
        }
        return scheme == "https" || scheme == "http"
    }

    public static func parseSha256(forAsset assetName: String, from sumsText: String) -> String? {
        let target = assetName.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !target.isEmpty else { return nil }
        guard let regex = try? NSRegularExpression(pattern: #"^([a-fA-F0-9]{64})\s+[* ]?(.+)$"#) else {
            return nil
        }
        for rawLine in sumsText.components(separatedBy: .newlines) {
            let line = rawLine.trimmingCharacters(in: .whitespacesAndNewlines)
            if line.isEmpty { continue }
            let range = NSRange(line.startIndex..., in: line)
            guard let match = regex.firstMatch(in: line, range: range),
                  match.numberOfRanges >= 3,
                  let hashRange = Range(match.range(at: 1), in: line),
                  let fileRange = Range(match.range(at: 2), in: line) else {
                continue
            }
            let hash = String(line[hashRange]).lowercased()
            let fileRef = String(line[fileRange]).trimmingCharacters(in: .whitespacesAndNewlines)
                .split(separator: "/").last.map(String.init) ?? ""
            if fileRef.caseInsensitiveCompare(target) == .orderedSame {
                return hash
            }
        }
        return nil
    }
}
