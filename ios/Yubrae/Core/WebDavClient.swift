import Foundation

public struct WebDavConfig: Equatable, Sendable {
    public var serverUrl: String
    public var username: String
    public var password: String
    public var remoteDir: String

    public init(
        serverUrl: String,
        username: String = "",
        password: String = "",
        remoteDir: String = "/Yubrae"
    ) {
        self.serverUrl = serverUrl
        self.username = username
        self.password = password
        self.remoteDir = remoteDir
    }

    public init(settings: AppSettings) {
        self.serverUrl = settings.webDavUrl
        self.username = settings.webDavUsername
        self.password = settings.webDavPassword
        self.remoteDir = settings.webDavRemoteDir
    }
}

public enum WebDavError: LocalizedError, Equatable {
    case missingServerUrl
    case invalidServerUrl(String)
    case missingRemotePath
    case authenticationFailed(Int)
    case httpError(Int, String)
    case emptyResponse
    case invalidResponse

    public var errorDescription: String? {
        switch self {
        case .missingServerUrl:
            return "请先配置 WebDAV 服务器地址"
        case .invalidServerUrl(let raw):
            return "WebDAV 服务器地址格式无效：\(raw)"
        case .missingRemotePath:
            return "远程备份路径不能为空"
        case .authenticationFailed(let code):
            return "WebDAV 认证失败（HTTP \(code)），请检查用户名与密码"
        case .httpError(let code, let detail):
            return "WebDAV 请求失败（HTTP \(code)）：\(detail)"
        case .emptyResponse:
            return "远程备份响应内容为空"
        case .invalidResponse:
            return "服务器返回了无效的 HTTP 响应"
        }
    }
}

/// 纯 `URLSession` + `XMLParser` 实现的 WebDAV 加密备份客户端（与 Android `WebDavSyncManager.kt` 完全互通）。
///
/// 支持 `PROPFIND (Depth: 0/1)`、`MKCOL` 逐级建目录、`PUT` 上传 `yubrae_backup_*.klbk`、`GET` 拉取还原，
/// 并兼容坚果云 / Nextcloud / 群晖 / Alist 等多命名空间 WebDAV XML 响应。
public final class WebDavClient: Sendable {
    private let session: URLSession

    public static let defaultRemoteDir = "/Yubrae"

    private static let propfindBody = """
    <?xml version="1.0" encoding="utf-8" ?>
    <D:propfind xmlns:D="DAV:">
      <D:prop>
        <D:displayname/>
        <D:getcontentlength/>
        <D:getlastmodified/>
        <D:resourcetype/>
      </D:prop>
    </D:propfind>
    """

    public init(session: URLSession = .shared) {
        self.session = session
    }

    // MARK: - Public Operations

    public func testConnection(config: WebDavConfig) async throws -> String {
        let baseURL = try parseBaseURL(config.serverUrl)
        let authHeader = buildAuthHeader(username: config.username, password: config.password)
        let segments = parseRemoteDirSegments(config.remoteDir)
        let dirURL = buildDirectoryURL(baseURL: baseURL, segments: segments)

        let resp = try await executePropfind(url: dirURL, depth: "0", authHeader: authHeader)
        switch resp.statusCode {
        case 200...299:
            return "WebDAV 连接成功（目录已就绪）"
        case 401, 403:
            throw WebDavError.authenticationFailed(resp.statusCode)
        case 404:
            try await ensureRemoteDirectory(baseURL: baseURL, segments: segments, authHeader: authHeader)
            return "WebDAV 连接成功（已自动创建远程目录）"
        default:
            throw WebDavError.httpError(resp.statusCode, resp.message)
        }
    }

    public func listRemoteBackups(config: WebDavConfig) async throws -> [RemoteBackupFile] {
        let baseURL = try parseBaseURL(config.serverUrl)
        let authHeader = buildAuthHeader(username: config.username, password: config.password)
        let segments = parseRemoteDirSegments(config.remoteDir)
        let dirURL = buildDirectoryURL(baseURL: baseURL, segments: segments)

        let resp = try await executePropfind(url: dirURL, depth: "1", authHeader: authHeader)
        switch resp.statusCode {
        case 404:
            return []
        case 401, 403:
            throw WebDavError.authenticationFailed(resp.statusCode)
        case 200...299:
            return Self.parseMultiStatusXML(resp.bodyText, dirURL: dirURL)
        default:
            throw WebDavError.httpError(resp.statusCode, resp.message)
        }
    }

    public func uploadEncryptedBackup(
        data: Data,
        config: WebDavConfig,
        date: Date = Date()
    ) async throws -> RemoteBackupFile {
        let baseURL = try parseBaseURL(config.serverUrl)
        let authHeader = buildAuthHeader(username: config.username, password: config.password)
        let segments = parseRemoteDirSegments(config.remoteDir)
        let dirURL = buildDirectoryURL(baseURL: baseURL, segments: segments)

        try await ensureRemoteDirectory(baseURL: baseURL, segments: segments, authHeader: authHeader)

        let fileName = Self.makeBackupFileName(date: date)
        let targetURL = dirURL.appendingPathComponent(fileName, isDirectory: false)

        var request = URLRequest(url: targetURL)
        request.httpMethod = "PUT"
        request.timeoutInterval = 45
        request.setValue("application/octet-stream", forHTTPHeaderField: "Content-Type")
        if let authHeader {
            request.setValue(authHeader, forHTTPHeaderField: "Authorization")
        }
        request.httpBody = data

        let (_, response) = try await session.data(for: request)
        guard let http = response as? HTTPURLResponse else {
            throw WebDavError.invalidResponse
        }
        if http.statusCode == 401 || http.statusCode == 403 {
            throw WebDavError.authenticationFailed(http.statusCode)
        }
        guard (200...299).contains(http.statusCode) else {
            throw WebDavError.httpError(http.statusCode, HTTPURLResponse.localizedString(forStatusCode: http.statusCode))
        }

        let nowMillis = Int64((date.timeIntervalSince1970 * 1000.0).rounded())
        let encodedPath = URLComponents(url: targetURL, resolvingAgainstBaseURL: false)?.percentEncodedPath ?? targetURL.path
        return RemoteBackupFile(
            name: fileName,
            path: encodedPath,
            sizeBytes: Int64(data.count),
            modifiedAtMillis: nowMillis
        )
    }

    public func downloadBackup(remotePath: String, config: WebDavConfig) async throws -> Data {
        let trimmedPath = remotePath.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedPath.isEmpty else {
            throw WebDavError.missingRemotePath
        }
        let baseURL = try parseBaseURL(config.serverUrl)
        let authHeader = buildAuthHeader(username: config.username, password: config.password)
        let segments = parseRemoteDirSegments(config.remoteDir)
        let dirURL = buildDirectoryURL(baseURL: baseURL, segments: segments)
        let fileURL = resolveRemoteFileURL(baseURL: baseURL, dirURL: dirURL, remotePath: trimmedPath)

        var request = URLRequest(url: fileURL)
        request.httpMethod = "GET"
        request.timeoutInterval = 45
        if let authHeader {
            request.setValue(authHeader, forHTTPHeaderField: "Authorization")
        }

        let (data, response) = try await session.data(for: request)
        guard let http = response as? HTTPURLResponse else {
            throw WebDavError.invalidResponse
        }
        if http.statusCode == 401 || http.statusCode == 403 {
            throw WebDavError.authenticationFailed(http.statusCode)
        }
        guard (200...299).contains(http.statusCode) else {
            throw WebDavError.httpError(http.statusCode, HTTPURLResponse.localizedString(forStatusCode: http.statusCode))
        }
        guard !data.isEmpty else {
            throw WebDavError.emptyResponse
        }
        return data
    }

    // MARK: - Formatting & XML Parsing

    public static func makeBackupFileName(date: Date = Date(), timeZone: TimeZone = .current) -> String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = timeZone
        formatter.dateFormat = "yyyyMMdd_HHmmss_SSS"
        return "yubrae_backup_\(formatter.string(from: date)).klbk"
    }

    public static func parseMultiStatusXML(_ xml: String, dirURL: URL) -> [RemoteBackupFile] {
        let trimmed = xml.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty, let data = trimmed.data(using: .utf8) else {
            return []
        }

        let dirPathNormalized = (URLComponents(url: dirURL, resolvingAgainstBaseURL: false)?.percentEncodedPath ?? dirURL.path)
            .trimmingCharacters(in: CharacterSet(charactersIn: "/"))

        let delegate = MultiStatusParserDelegate()
        let parser = XMLParser(data: data)
        parser.shouldProcessNamespaces = false
        parser.shouldResolveExternalEntities = false
        parser.delegate = delegate
        guard parser.parse() else {
            return []
        }

        var results: [RemoteBackupFile] = []
        for entry in delegate.entries {
            if entry.isCollection { continue }
            let hrefRaw = entry.href.trimmingCharacters(in: .whitespacesAndNewlines)
            if hrefRaw.isEmpty { continue }

            let hrefPath = extractHrefPath(hrefRaw)
            if hrefPath.hasSuffix("/") { continue }
            if hrefPath.trimmingCharacters(in: CharacterSet(charactersIn: "/")) == dirPathNormalized {
                continue
            }

            let rawFileName = hrefPath.split(separator: "/").last.map(String.init) ?? ""
            let decodedName = rawFileName.removingPercentEncoding ?? rawFileName
            guard !decodedName.isEmpty, decodedName.lowercased().hasSuffix(".klbk") else {
                continue
            }

            results.append(
                RemoteBackupFile(
                    name: decodedName,
                    path: hrefRaw,
                    sizeBytes: entry.sizeBytes,
                    modifiedAtMillis: entry.modifiedAtMillis
                )
            )
        }

        return results.sorted { lhs, rhs in
            if lhs.modifiedAtMillis != rhs.modifiedAtMillis {
                return lhs.modifiedAtMillis > rhs.modifiedAtMillis
            }
            return lhs.name > rhs.name
        }
    }

    // MARK: - Private HTTP Helpers

    private struct HttpTextResponse {
        let statusCode: Int
        let message: String
        let bodyText: String
    }

    private func ensureRemoteDirectory(baseURL: URL, segments: [String], authHeader: String?) async throws {
        guard !segments.isEmpty else { return }
        var currentURL = ensureTrailingSlash(baseURL)
        for segment in segments {
            currentURL = ensureTrailingSlash(currentURL.appendingPathComponent(segment, isDirectory: true))
            let probe = try await executePropfind(url: currentURL, depth: "0", authHeader: authHeader)
            switch probe.statusCode {
            case 200...299:
                continue
            case 401, 403:
                throw WebDavError.authenticationFailed(probe.statusCode)
            case 404:
                var mkcol = URLRequest(url: currentURL)
                mkcol.httpMethod = "MKCOL"
                mkcol.timeoutInterval = 20
                if let authHeader {
                    mkcol.setValue(authHeader, forHTTPHeaderField: "Authorization")
                }
                let (_, resp) = try await session.data(for: mkcol)
                guard let http = resp as? HTTPURLResponse else {
                    throw WebDavError.invalidResponse
                }
                if ![200, 201, 204, 405].contains(http.statusCode) {
                    throw WebDavError.httpError(http.statusCode, "创建远程目录失败")
                }
            default:
                throw WebDavError.httpError(probe.statusCode, probe.message)
            }
        }
    }

    private func executePropfind(url: URL, depth: String, authHeader: String?) async throws -> HttpTextResponse {
        var request = URLRequest(url: url)
        request.httpMethod = "PROPFIND"
        request.timeoutInterval = 25
        request.setValue(depth, forHTTPHeaderField: "Depth")
        request.setValue("application/xml; charset=utf-8", forHTTPHeaderField: "Content-Type")
        if let authHeader {
            request.setValue(authHeader, forHTTPHeaderField: "Authorization")
        }
        request.httpBody = Data(Self.propfindBody.utf8)

        let (data, response) = try await session.data(for: request)
        guard let http = response as? HTTPURLResponse else {
            throw WebDavError.invalidResponse
        }
        let bodyText = String(data: data, encoding: .utf8) ?? ""
        return HttpTextResponse(
            statusCode: http.statusCode,
            message: HTTPURLResponse.localizedString(forStatusCode: http.statusCode),
            bodyText: bodyText
        )
    }

    private func parseBaseURL(_ rawUrl: String) throws -> URL {
        let cleaned = rawUrl.trimmingCharacters(in: .whitespacesAndNewlines)
            .trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        guard !cleaned.isEmpty else {
            throw WebDavError.missingServerUrl
        }
        guard let url = URL(string: cleaned),
              let scheme = url.scheme?.lowercased(),
              (scheme == "https" || scheme == "http"),
              let host = url.host,
              !host.isEmpty else {
            throw WebDavError.invalidServerUrl(rawUrl)
        }
        return url
    }

    private func parseRemoteDirSegments(_ remoteDir: String) -> [String] {
        remoteDir.trimmingCharacters(in: .whitespacesAndNewlines)
            .split(separator: "/")
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty && $0 != "." && $0 != ".." }
    }

    private func buildDirectoryURL(baseURL: URL, segments: [String]) -> URL {
        var url = baseURL
        for seg in segments {
            url = url.appendingPathComponent(seg, isDirectory: true)
        }
        return ensureTrailingSlash(url)
    }

    private func ensureTrailingSlash(_ url: URL) -> URL {
        if url.absoluteString.hasSuffix("/") {
            return url
        }
        return URL(string: url.absoluteString + "/") ?? url
    }

    private func resolveRemoteFileURL(baseURL: URL, dirURL: URL, remotePath: String) -> URL {
        if let absolute = URL(string: remotePath),
           let scheme = absolute.scheme?.lowercased(),
           (scheme == "http" || scheme == "https"),
           absolute.host != nil {
            return absolute
        }
        if remotePath.hasPrefix("/") {
            if var comps = URLComponents(url: baseURL, resolvingAgainstBaseURL: false) {
                comps.query = nil
                comps.fragment = nil
                comps.percentEncodedPath = remotePath
                if let resolved = comps.url {
                    return resolved
                }
                comps.path = remotePath
                if let resolved = comps.url {
                    return resolved
                }
            }
        }
        var url = dirURL
        let parts = remotePath.split(separator: "/").map(String.init).filter { !$0.isEmpty }
        for (idx, part) in parts.enumerated() {
            url = url.appendingPathComponent(part, isDirectory: idx < parts.count - 1)
        }
        return url
    }

    private func buildAuthHeader(username: String, password: String) -> String? {
        if username.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty &&
            password.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            return nil
        }
        let raw = "\(username):\(password)"
        let encoded = Data(raw.utf8).base64EncodedString()
        return "Basic \(encoded)"
    }

    private static func extractHrefPath(_ href: String) -> String {
        if let url = URL(string: href),
           let scheme = url.scheme?.lowercased(),
           (scheme == "http" || scheme == "https"),
           let comps = URLComponents(url: url, resolvingAgainstBaseURL: false) {
            return comps.percentEncodedPath
        }
        return href.split(separator: "?", maxSplits: 1).first.map(String.init) ?? href
    }

    fileprivate static func parseWebDavTimestamp(_ raw: String) -> Int64 {
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return 0 }

        let isoFormatter = ISO8601DateFormatter()
        isoFormatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        if let date = isoFormatter.date(from: trimmed) {
            return Int64((date.timeIntervalSince1970 * 1000.0).rounded())
        }
        isoFormatter.formatOptions = [.withInternetDateTime]
        if let date = isoFormatter.date(from: trimmed) {
            return Int64((date.timeIntervalSince1970 * 1000.0).rounded())
        }

        let patterns = [
            "EEE, dd MMM yyyy HH:mm:ss zzz",
            "EEE, dd MMM yyyy HH:mm:ss 'GMT'",
            "EEEE, dd-MMM-yy HH:mm:ss zzz",
            "EEE MMM d HH:mm:ss yyyy"
        ]
        let df = DateFormatter()
        df.locale = Locale(identifier: "en_US_POSIX")
        df.timeZone = TimeZone(secondsFromGMT: 0)
        for pattern in patterns {
            df.dateFormat = pattern
            if let date = df.date(from: trimmed) {
                return Int64((date.timeIntervalSince1970 * 1000.0).rounded())
            }
        }
        return 0
    }
}

// MARK: - XMLParserDelegate for Multi-Status

private final class MultiStatusParserDelegate: NSObject, XMLParserDelegate {
    struct Entry {
        var href: String = ""
        var isCollection: Bool = false
        var sizeBytes: Int64 = 0
        var modifiedAtMillis: Int64 = 0
    }

    var entries: [Entry] = []

    private var currentEntry: Entry?
    private var elementStack: [String] = []
    private var currentText: String = ""

    private static func localName(of elementName: String) -> String {
        if let colonIndex = elementName.lastIndex(of: ":") {
            return String(elementName[elementName.index(after: colonIndex)...]).lowercased()
        }
        return elementName.lowercased()
    }

    func parser(
        _ parser: XMLParser,
        didStartElement elementName: String,
        namespaceURI: String?,
        qualifiedName qName: String?,
        attributes attributeDict: [String: String] = [:]
    ) {
        let local = Self.localName(of: elementName)
        elementStack.append(local)
        currentText = ""

        if local == "response" {
            currentEntry = Entry()
        } else if local == "collection", elementStack.contains("resourcetype") {
            currentEntry?.isCollection = true
        }
    }

    func parser(_ parser: XMLParser, foundCharacters string: String) {
        currentText.append(string)
    }

    func parser(
        _ parser: XMLParser,
        didEndElement elementName: String,
        namespaceURI: String?,
        qualifiedName qName: String?
    ) {
        let local = Self.localName(of: elementName)
        let text = currentText.trimmingCharacters(in: .whitespacesAndNewlines)

        if var entry = currentEntry {
            switch local {
            case "href":
                if entry.href.isEmpty, !text.isEmpty {
                    entry.href = text
                }
                currentEntry = entry
            case "getcontentlength":
                if entry.sizeBytes == 0, let parsed = Int64(text) {
                    entry.sizeBytes = parsed
                }
                currentEntry = entry
            case "getlastmodified":
                if entry.modifiedAtMillis == 0, !text.isEmpty {
                    entry.modifiedAtMillis = WebDavClient.parseWebDavTimestamp(text)
                }
                currentEntry = entry
            case "response":
                entries.append(entry)
                currentEntry = nil
            default:
                break
            }
        }

        if !elementStack.isEmpty {
            elementStack.removeLast()
        }
        currentText = ""
    }
}
