package com.kairoledger.app.sync

import com.kairoledger.app.core.contract.CloudSyncService
import com.kairoledger.app.core.contract.RemoteBackupFile
import com.kairoledger.app.data.db.KairoDatabase
import com.kairoledger.app.data.prefs.AppSettings
import com.kairoledger.app.data.prefs.SettingsStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import java.io.IOException
import java.io.StringReader
import java.net.URLDecoder
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

/**
 * 基于 OkHttp 的加密 WebDAV 云同步与本地加密包导入导出管理器。
 *
 * 支持标准 PROPFIND (Depth: 0/1)、MKCOL 自动建目录、PUT 上传 .klbk 加密快照、GET 拉取恢复，
 * 以及多命名空间 XML 解析（<D:response> / <d:response> / <response> 等坚果云与 NAS 格式）。
 */
class WebDavSyncManager(
    private val backupSerializer: BackupSerializer,
    private val okHttpClient: OkHttpClient,
    private val readSettings: suspend () -> AppSettings,
    private val markBackupSynced: suspend (Long) -> Unit
) : CloudSyncService {

    constructor(
        db: KairoDatabase,
        settingsStore: SettingsStore,
        okHttpClient: OkHttpClient
    ) : this(
        backupSerializer = BackupSerializer(db, settingsStore),
        okHttpClient = okHttpClient,
        readSettings = { settingsStore.getSnapshot() },
        markBackupSynced = { epochMillis -> settingsStore.markBackupSynced(epochMillis) }
    )

    override suspend fun testWebDavConnection(): Result<String> {
        return runSafe {
            val settings = readSettings()
            val baseUrl = parseBaseUrl(settings.webDavUrl)
            val authHeader = buildAuthHeader(settings)
            val segments = parseRemoteDirSegments(settings.webDavRemoteDir)
            val dirUrl = buildDirectoryUrl(baseUrl, segments)

            val propfindResult = executePropfind(dirUrl, depth = "0", authHeader = authHeader)
            when {
                propfindResult.code in 200..299 -> {
                    "WebDAV 连接成功（目录已就绪）"
                }
                propfindResult.code == 401 || propfindResult.code == 403 -> {
                    throw IOException("WebDAV 认证失败（HTTP ${propfindResult.code}），请检查用户名与密码")
                }
                propfindResult.code == 404 -> {
                    ensureRemoteDirectory(baseUrl, segments, authHeader)
                    "WebDAV 连接成功（已自动创建远程目录）"
                }
                else -> {
                    throw IOException("WebDAV 连接失败：HTTP ${propfindResult.code} ${propfindResult.message}")
                }
            }
        }
    }

    override suspend fun listRemoteBackups(): Result<List<RemoteBackupFile>> {
        return runSafe {
            val settings = readSettings()
            val baseUrl = parseBaseUrl(settings.webDavUrl)
            val authHeader = buildAuthHeader(settings)
            val segments = parseRemoteDirSegments(settings.webDavRemoteDir)
            val dirUrl = buildDirectoryUrl(baseUrl, segments)

            val response = executePropfind(dirUrl, depth = "1", authHeader = authHeader)
            when {
                response.code == 404 -> emptyList()
                response.code == 401 || response.code == 403 -> {
                    throw IOException("WebDAV 认证失败（HTTP ${response.code}），无法列出备份")
                }
                response.code !in 200..299 -> {
                    throw IOException("WebDAV 获取备份列表失败：HTTP ${response.code} ${response.message}")
                }
                else -> parseMultiStatusXml(response.bodyText, dirUrl)
            }
        }
    }

    override suspend fun uploadEncryptedBackup(): Result<RemoteBackupFile> {
        return runSafe {
            val settings = readSettings()
            val baseUrl = parseBaseUrl(settings.webDavUrl)
            val passphrase = settings.backupPassphrase
            require(passphrase.isNotBlank()) { "请先设置备份加密口令" }

            val authHeader = buildAuthHeader(settings)
            val segments = parseRemoteDirSegments(settings.webDavRemoteDir)
            val dirUrl = buildDirectoryUrl(baseUrl, segments)

            ensureRemoteDirectory(baseUrl, segments, authHeader)

            val json = backupSerializer.exportToJson()
            val encryptedBytes = CryptoHelper.encryptString(json, passphrase)

            val now = System.currentTimeMillis()
            val timestampTag = Instant.ofEpochMilli(now)
                .atZone(ZoneId.systemDefault())
                .format(FILE_TIME_FORMATTER)
            val fileName = "kairo_backup_${timestampTag}.klbk"
            val targetUrl = dirUrl.newBuilder().addPathSegment(fileName).build()

            val request = Request.Builder()
                .url(targetUrl)
                .apply { if (authHeader != null) header("Authorization", authHeader) }
                .put(encryptedBytes.toRequestBody(MEDIA_TYPE_OCTET))
                .build()

            withContext(Dispatchers.IO) {
                okHttpClient.newCall(request).execute().use { resp ->
                    if (resp.code == 401 || resp.code == 403) {
                        throw IOException("WebDAV 上传鉴权失败（HTTP ${resp.code}）")
                    }
                    if (!resp.isSuccessful) {
                        throw IOException("WebDAV 上传失败：HTTP ${resp.code} ${resp.message}")
                    }
                }
            }

            markBackupSynced(now)
            RemoteBackupFile(
                name = fileName,
                path = targetUrl.encodedPath,
                sizeBytes = encryptedBytes.size.toLong(),
                modifiedAtMillis = now
            )
        }
    }

    override suspend fun restoreEncryptedBackup(remotePath: String): Result<String> {
        return runSafe {
            val settings = readSettings()
            val baseUrl = parseBaseUrl(settings.webDavUrl)
            val passphrase = settings.backupPassphrase
            require(passphrase.isNotBlank()) { "请先设置备份加密口令" }
            require(remotePath.isNotBlank()) { "远程备份路径不能为空" }

            val authHeader = buildAuthHeader(settings)
            val segments = parseRemoteDirSegments(settings.webDavRemoteDir)
            val dirUrl = buildDirectoryUrl(baseUrl, segments)
            val fileUrl = resolveRemoteFileUrl(baseUrl, dirUrl, remotePath)

            val request = Request.Builder()
                .url(fileUrl)
                .apply { if (authHeader != null) header("Authorization", authHeader) }
                .get()
                .build()

            val encryptedBytes = withContext(Dispatchers.IO) {
                okHttpClient.newCall(request).execute().use { resp ->
                    if (resp.code == 401 || resp.code == 403) {
                        throw IOException("WebDAV 下载鉴权失败（HTTP ${resp.code}）")
                    }
                    if (!resp.isSuccessful) {
                        throw IOException("WebDAV 下载备份失败：HTTP ${resp.code} ${resp.message}")
                    }
                    resp.body?.bytes() ?: throw IOException("远程备份响应内容为空")
                }
            }

            val json = CryptoHelper.decryptToString(encryptedBytes, passphrase)
            val summary = backupSerializer.restoreFromJson(json)
            markBackupSynced(System.currentTimeMillis())

            "恢复成功：${summary.accountsCount} 个账户，${summary.transactionsCount} 笔交易，${summary.categoriesCount} 个分类"
        }
    }

    override suspend fun exportLocalEncryptedBytes(passphrase: String): Result<ByteArray> {
        return runSafe {
            require(passphrase.isNotBlank()) { "导出加密口令不能为空" }
            val json = backupSerializer.exportToJson()
            CryptoHelper.encryptString(json, passphrase)
        }
    }

    override suspend fun importLocalEncryptedBytes(bytes: ByteArray, passphrase: String): Result<String> {
        return runSafe {
            require(passphrase.isNotBlank()) { "解密口令不能为空" }
            val json = CryptoHelper.decryptToString(bytes, passphrase)
            val summary = backupSerializer.restoreFromJson(json)
            "导入恢复成功：${summary.accountsCount} 个账户，${summary.transactionsCount} 笔交易，${summary.categoriesCount} 个分类"
        }
    }

    private suspend fun ensureRemoteDirectory(
        baseUrl: HttpUrl,
        segments: List<String>,
        authHeader: String?
    ) {
        if (segments.isEmpty()) return

        var currentUrl = baseUrl
        for (segment in segments) {
            currentUrl = currentUrl.newBuilder()
                .addPathSegment(segment)
                .addPathSegment("") // trailing slash for collection URL
                .build()

            val probe = executePropfind(currentUrl, depth = "0", authHeader = authHeader)
            when {
                probe.code in 200..299 -> continue
                probe.code == 401 || probe.code == 403 -> {
                    throw IOException("WebDAV 目录检查鉴权失败（HTTP ${probe.code}）")
                }
                probe.code == 404 -> {
                    val mkcolReq = Request.Builder()
                        .url(currentUrl)
                        .apply { if (authHeader != null) header("Authorization", authHeader) }
                        .method("MKCOL", null)
                        .build()

                    withContext(Dispatchers.IO) {
                        okHttpClient.newCall(mkcolReq).execute().use { resp ->
                            // RFC 4918: 201 Created, 200/204 OK, 405 Method Not Allowed (already exists)
                            if (resp.code !in setOf(200, 201, 204, 405)) {
                                throw IOException("创建远程目录失败（HTTP ${resp.code} ${resp.message}）")
                            }
                        }
                    }
                }
                else -> {
                    throw IOException("检查远程目录失败：HTTP ${probe.code} ${probe.message}")
                }
            }
        }
    }

    private data class HttpTextResponse(
        val code: Int,
        val message: String,
        val bodyText: String
    )

    private suspend fun executePropfind(
        url: HttpUrl,
        depth: String,
        authHeader: String?
    ): HttpTextResponse {
        val request = Request.Builder()
            .url(url)
            .apply { if (authHeader != null) header("Authorization", authHeader) }
            .header("Depth", depth)
            .method("PROPFIND", PROPFIND_BODY.toRequestBody(MEDIA_TYPE_XML))
            .build()

        return withContext(Dispatchers.IO) {
            okHttpClient.newCall(request).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                HttpTextResponse(
                    code = resp.code,
                    message = resp.message,
                    bodyText = text
                )
            }
        }
    }

    internal fun parseMultiStatusXml(xml: String, dirUrl: HttpUrl): List<RemoteBackupFile> {
        if (xml.isBlank()) return emptyList()

        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            runCatching { setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            isExpandEntityReferences = false
        }

        val doc = factory.newDocumentBuilder().parse(InputSource(StringReader(xml)))
        val root = doc.documentElement ?: return emptyList()
        val responseNodes = findDescendantsByLocalName(root, "response")

        val dirPathNormalized = dirUrl.encodedPath.trimEnd('/')
        val results = mutableListOf<RemoteBackupFile>()

        for (responseEl in responseNodes) {
            val hrefRaw = findFirstDirectOrDescendantText(responseEl, "href")?.trim().orEmpty()
            if (hrefRaw.isEmpty()) continue

            val propElements = findDescendantsByLocalName(responseEl, "prop")
            var isCollection = false
            var sizeBytes = 0L
            var modifiedMillis = 0L

            for (propEl in propElements) {
                val resourceTypeEl = findFirstChildByLocalName(propEl, "resourcetype")
                if (resourceTypeEl != null && findDescendantsByLocalName(resourceTypeEl, "collection").isNotEmpty()) {
                    isCollection = true
                }
                if (sizeBytes == 0L) {
                    val lenText = findFirstDirectOrDescendantText(propEl, "getcontentlength")?.trim()
                    sizeBytes = lenText?.toLongOrNull() ?: 0L
                }
                if (modifiedMillis == 0L) {
                    val modText = findFirstDirectOrDescendantText(propEl, "getlastmodified")?.trim()
                    if (!modText.isNullOrBlank()) {
                        modifiedMillis = parseWebDavTimestamp(modText)
                    }
                }
            }

            if (isCollection) continue

            val hrefPath = extractHrefPath(hrefRaw)
            if (hrefPath.endsWith("/")) continue
            if (hrefPath.trimEnd('/') == dirPathNormalized) continue

            val rawFileName = hrefPath.substringAfterLast('/')
            val fileName = runCatching { URLDecoder.decode(rawFileName, "UTF-8") }
                .getOrDefault(rawFileName)

            if (fileName.isBlank() || !fileName.endsWith(".klbk", ignoreCase = true)) {
                continue
            }

            results.add(
                RemoteBackupFile(
                    name = fileName,
                    path = hrefRaw,
                    sizeBytes = sizeBytes,
                    modifiedAtMillis = modifiedMillis
                )
            )
        }

        return results.sortedWith(
            compareByDescending<RemoteBackupFile> { it.modifiedAtMillis }
                .thenByDescending { it.name }
        )
    }

    private fun extractHrefPath(href: String): String {
        val httpUrl = href.toHttpUrlOrNull()
        return httpUrl?.encodedPath ?: href.substringBefore('?')
    }

    private fun parseWebDavTimestamp(raw: String): Long {
        val trimmed = raw.trim()
        runCatching {
            return ZonedDateTime.parse(trimmed, DateTimeFormatter.RFC_1123_DATE_TIME)
                .toInstant()
                .toEpochMilli()
        }
        runCatching {
            return Instant.parse(trimmed).toEpochMilli()
        }
        runCatching {
            return OffsetDateTime.parse(trimmed, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                .toInstant()
                .toEpochMilli()
        }
        runCatching {
            val fmt = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss z", Locale.ENGLISH)
            return ZonedDateTime.parse(trimmed, fmt).toInstant().toEpochMilli()
        }
        return 0L
    }

    private fun findDescendantsByLocalName(root: Element, targetLocalName: String): List<Element> {
        val matches = mutableListOf<Element>()
        fun walk(node: Node) {
            val children = node.childNodes
            for (i in 0 until children.length) {
                val child = children.item(i)
                if (child is Element) {
                    if (localNameOf(child).equals(targetLocalName, ignoreCase = true)) {
                        matches.add(child)
                    }
                    walk(child)
                }
            }
        }
        walk(root)
        return matches
    }

    private fun findFirstChildByLocalName(parent: Element, targetLocalName: String): Element? {
        val children = parent.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child is Element && localNameOf(child).equals(targetLocalName, ignoreCase = true)) {
                return child
            }
        }
        return null
    }

    private fun findFirstDirectOrDescendantText(parent: Element, targetLocalName: String): String? {
        findFirstChildByLocalName(parent, targetLocalName)?.let { return it.textContent }
        return findDescendantsByLocalName(parent, targetLocalName).firstOrNull()?.textContent
    }

    private fun localNameOf(element: Element): String {
        val local = element.localName
        if (!local.isNullOrBlank()) return local
        val full = element.nodeName
        return if (':' in full) full.substringAfter(':') else full
    }

    private fun parseBaseUrl(rawUrl: String): HttpUrl {
        val cleaned = rawUrl.trim().trimEnd('/')
        require(cleaned.isNotBlank()) { "请先配置 WebDAV 服务器地址" }
        return cleaned.toHttpUrlOrNull()
            ?: throw IllegalArgumentException("WebDAV 服务器地址格式无效：$rawUrl")
    }

    private fun parseRemoteDirSegments(remoteDir: String): List<String> =
        remoteDir.trim()
            .split('/')
            .map { it.trim() }
            .filter { it.isNotEmpty() && it != "." && it != ".." }

    private fun buildDirectoryUrl(baseUrl: HttpUrl, segments: List<String>): HttpUrl {
        val builder = baseUrl.newBuilder()
        for (seg in segments) {
            builder.addPathSegment(seg)
        }
        // Ensure trailing slash on directory URL
        val built = builder.build()
        return if (built.encodedPath.endsWith("/")) {
            built
        } else {
            built.newBuilder().addPathSegment("").build()
        }
    }

    private fun resolveRemoteFileUrl(baseUrl: HttpUrl, dirUrl: HttpUrl, remotePath: String): HttpUrl {
        val trimmed = remotePath.trim()
        trimmed.toHttpUrlOrNull()?.let { return it }

        if (trimmed.startsWith("/")) {
            return runCatching {
                baseUrl.newBuilder().encodedPath(trimmed).build()
            }.getOrElse {
                val segs = trimmed.split('/').filter { s -> s.isNotEmpty() }
                val b = baseUrl.newBuilder().encodedPath("/")
                segs.forEach { s -> b.addPathSegment(s) }
                b.build()
            }
        }

        val builder = dirUrl.newBuilder()
        trimmed.split('/').filter { it.isNotEmpty() }.forEach { seg ->
            builder.addPathSegment(seg)
        }
        return builder.build()
    }

    private fun buildAuthHeader(settings: AppSettings): String? {
        if (settings.webDavUsername.isBlank() && settings.webDavPassword.isBlank()) {
            return null
        }
        return Credentials.basic(settings.webDavUsername, settings.webDavPassword, Charsets.UTF_8)
    }

    private suspend inline fun <T> runSafe(crossinline block: suspend () -> T): Result<T> {
        return try {
            Result.success(block())
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        private val MEDIA_TYPE_XML = "application/xml; charset=utf-8".toMediaType()
        private val MEDIA_TYPE_OCTET = "application/octet-stream".toMediaType()
        private val FILE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS", Locale.ROOT)

        private const val PROPFIND_BODY = """<?xml version="1.0" encoding="utf-8" ?>
<D:propfind xmlns:D="DAV:">
  <D:prop>
    <D:displayname/>
    <D:getcontentlength/>
    <D:getlastmodified/>
    <D:resourcetype/>
  </D:prop>
</D:propfind>"""
    }
}
