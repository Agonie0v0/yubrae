package com.yubrae.app.capture.rules

import android.content.Context
import com.yubrae.app.BuildConfig
import com.yubrae.app.capture.LocalRuleParser
import com.yubrae.app.update.AppUpdateManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.time.Instant
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.coroutineContext

/**
 * JS 规则包本地基线加载、本地导入与云端热更新仓库（纯 JVM 可测）。
 */
class CloudRuleRepository private constructor(
    private val storageDir: File,
    baseHttpClient: OkHttpClient,
    private val builtinZipProvider: () -> ByteArray?,
    private val builtinManifestProvider: () -> String?,
    localRuleParser: LocalRuleParser,
    private val defaultManifestUrl: String,
    @Suppress("UNUSED_PARAMETER") marker: Unit
) {
    constructor(
        context: Context,
        okHttpClient: OkHttpClient,
        localRuleParser: LocalRuleParser = LocalRuleParser()
    ) : this(
        storageDir = File(context.applicationContext.filesDir, "cloud_rules"),
        baseHttpClient = okHttpClient,
        builtinZipProvider = { readAssetBytes(context, BUILTIN_ZIP_ASSET_PATH) },
        builtinManifestProvider = { readAssetText(context, BUILTIN_MANIFEST_ASSET_PATH) },
        localRuleParser = localRuleParser,
        defaultManifestUrl = DEFAULT_MANIFEST_URL,
        marker = Unit
    )

    internal constructor(
        storageDir: File,
        okHttpClient: OkHttpClient,
        builtinZipProvider: () -> ByteArray?,
        builtinManifestProvider: () -> String? = { null },
        localRuleParser: LocalRuleParser = LocalRuleParser(),
        defaultManifestUrl: String = DEFAULT_MANIFEST_URL
    ) : this(
        storageDir = storageDir,
        baseHttpClient = okHttpClient,
        builtinZipProvider = builtinZipProvider,
        builtinManifestProvider = builtinManifestProvider,
        localRuleParser = localRuleParser,
        defaultManifestUrl = defaultManifestUrl,
        marker = Unit
    )

    @Serializable
    private data class StoredRuleMeta(
        val source: String = JsRuleSource.BUILTIN.name,
        val version: String = "",
        val updatedAt: String = "",
        val changelog: String = "",
        val sha256: String = "",
        val lastCheckedAtMillis: Long = 0L
    )

    private sealed interface RemoteTarget {
        data class Manifest(val manifest: CloudRuleManifest, val resolvedUrl: String) : RemoteTarget
        data class DirectZip(val tempFile: File, val sha256: String) : RemoteTarget
    }

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private val httpClient: OkHttpClient = baseHttpClient.newBuilder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val repoMutex = Mutex()
    private val activeDir = File(storageDir, ACTIVE_DIR_NAME)
    private val activeZipFile = File(activeDir, ACTIVE_ZIP_NAME)
    private val activeMetaFile = File(activeDir, ACTIVE_META_NAME)

    @Volatile
    private var initialized = false

    @Volatile
    private var activeSha256: String = ""

    val ruleEngine = SandboxedJsRuleEngine(localRuleParser = localRuleParser)

    private val _status = MutableStateFlow(CloudRuleStatus())
    val status: StateFlow<CloudRuleStatus> = _status.asStateFlow()

    suspend fun initialize() {
        if (initialized) return
        withContext(Dispatchers.IO) {
            repoMutex.withLock {
                if (initialized) return@withLock
                val activeLoaded = tryLoadActiveRulesLocked()
                if (!activeLoaded) {
                    clearActiveStorageLocked()
                    loadBuiltinLocked(lastCheckedAtMillis = 0L, lastError = null)
                }
                initialized = true
            }
        }
    }

    fun currentWatchedNoticePackages(): Set<String> = _status.value.watchedNoticePackages

    fun currentWatchedOcrPackages(): Set<String> = _status.value.watchedOcrPackages

    fun isWatchedOcrPackage(packageName: String): Boolean {
        val pkg = packageName.trim()
        if (pkg.isEmpty()) return false
        val watched = _status.value.watchedOcrPackages
        return pkg in watched || watched.any { it.equals(pkg, ignoreCase = true) }
    }

    fun validateSubscriptionUrl(rawUrl: String): String? = Companion.validateSubscriptionUrl(rawUrl)

    suspend fun checkAndUpdate(
        manifestUrl: String = "",
        mirrorPrefix: String = "",
        force: Boolean = false
    ): CloudRuleUpdateResult {
        if (!initialized) {
            initialize()
        }
        return withContext(Dispatchers.IO) {
            repoMutex.withLock {
                val nowMillis = System.currentTimeMillis()
                val rawTarget = manifestUrl.trim().ifBlank { defaultManifestUrl.trim() }
                val urlError = validateSubscriptionUrl(rawTarget)
                if (urlError != null || rawTarget.isBlank()) {
                    val reason = urlError ?: "规则订阅地址不能为空"
                    _status.value = _status.value.copy(
                        lastCheckedAtMillis = nowMillis,
                        lastError = reason
                    )
                    return@withLock CloudRuleUpdateResult.Failed(reason)
                }

                val previousStatus = _status.value
                val previousVersion = previousStatus.version

                try {
                    when (val target = fetchRemoteTarget(rawTarget, mirrorPrefix)) {
                        is RemoteTarget.Manifest -> {
                            val manifest = target.manifest
                            val manifestSha = manifest.sha256.trim().lowercase(Locale.ROOT)
                            if (!SHA256_REGEX.matches(manifestSha) ||
                                manifest.sizeBytes <= 0L ||
                                manifest.sizeBytes > JsRuleBundleLoader.MAX_ZIP_BYTES
                            ) {
                                error("规则清单缺少有效的 sha256 或 sizeBytes 校验信息")
                            }

                            val resolvedRulesUrl = resolveUrlAgainstBase(target.resolvedUrl, manifest.rulesUrl)
                                ?: error("规则包下载地址无效: ${manifest.rulesUrl}")
                            validateSubscriptionUrl(resolvedRulesUrl)?.let { error("规则包下载地址不安全: $it") }

                            val sameVersion = manifest.version.isNotBlank() &&
                                manifest.version.trim() == previousStatus.version.trim()
                            val sameSha = activeSha256.isNotBlank() &&
                                manifestSha.equals(activeSha256, ignoreCase = true)

                            if (!force && sameVersion && sameSha) {
                                val updatedStatus = previousStatus.copy(
                                    lastCheckedAtMillis = nowMillis,
                                    lastError = null
                                )
                                _status.value = updatedStatus
                                persistCheckedTimestampIfActiveLocked(nowMillis)
                                return@withLock CloudRuleUpdateResult.UpToDate(updatedStatus)
                            }

                            val verified = downloadAndVerifyZip(
                                rawZipUrl = resolvedRulesUrl,
                                mirrorPrefix = mirrorPrefix,
                                expectedSha256 = manifestSha,
                                expectedSizeBytes = manifest.sizeBytes
                            )
                            val tempZip = verified.first
                            val actualSha = verified.second
                            try {
                                val bundle = JsRuleBundleLoader.loadFromZipFile(
                                    file = tempZip,
                                    source = JsRuleSource.CLOUD,
                                    version = manifest.version,
                                    updatedAt = manifest.updatedAt,
                                    changelog = manifest.changelog
                                ).getOrThrow()

                                val storedMeta = StoredRuleMeta(
                                    source = JsRuleSource.CLOUD.name,
                                    version = bundle.version,
                                    updatedAt = bundle.updatedAt,
                                    changelog = bundle.changelog,
                                    sha256 = actualSha,
                                    lastCheckedAtMillis = nowMillis
                                )
                                commitActiveRulePackageLocked(tempZip, storedMeta)
                                activeSha256 = actualSha
                                ruleEngine.updateBundle(bundle)
                                val nextStatus = bundle.toStatus(lastCheckedAtMillis = nowMillis, lastError = null)
                                _status.value = nextStatus
                                CloudRuleUpdateResult.Updated(
                                    status = nextStatus,
                                    previousVersion = previousVersion
                                )
                            } finally {
                                if (tempZip.exists()) tempZip.delete()
                            }
                        }

                        is RemoteTarget.DirectZip -> {
                            val tempZip = target.tempFile
                            val actualSha = target.sha256
                            try {
                                if (!force &&
                                    previousStatus.source == JsRuleSource.CLOUD &&
                                    activeSha256.equals(actualSha, ignoreCase = true)
                                ) {
                                    val updatedStatus = previousStatus.copy(
                                        lastCheckedAtMillis = nowMillis,
                                        lastError = null
                                    )
                                    _status.value = updatedStatus
                                    persistCheckedTimestampIfActiveLocked(nowMillis)
                                    return@withLock CloudRuleUpdateResult.UpToDate(updatedStatus)
                                }

                                val versionLabel = "直链-${actualSha.take(8)}"
                                val updatedAtIso = Instant.ofEpochMilli(nowMillis).toString()
                                val bundle = JsRuleBundleLoader.loadFromZipFile(
                                    file = tempZip,
                                    source = JsRuleSource.CLOUD,
                                    version = versionLabel,
                                    updatedAt = updatedAtIso,
                                    changelog = "通过直链同步规则包"
                                ).getOrThrow()

                                val storedMeta = StoredRuleMeta(
                                    source = JsRuleSource.CLOUD.name,
                                    version = bundle.version,
                                    updatedAt = bundle.updatedAt,
                                    changelog = bundle.changelog,
                                    sha256 = actualSha,
                                    lastCheckedAtMillis = nowMillis
                                )
                                commitActiveRulePackageLocked(tempZip, storedMeta)
                                activeSha256 = actualSha
                                ruleEngine.updateBundle(bundle)
                                val nextStatus = bundle.toStatus(lastCheckedAtMillis = nowMillis, lastError = null)
                                _status.value = nextStatus
                                CloudRuleUpdateResult.Updated(
                                    status = nextStatus,
                                    previousVersion = previousVersion
                                )
                            } finally {
                                if (tempZip.exists()) tempZip.delete()
                            }
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    val reason = e.message?.takeIf { it.isNotBlank() } ?: "检查或更新云端规则失败"
                    _status.value = _status.value.copy(
                        lastCheckedAtMillis = nowMillis,
                        lastError = reason
                    )
                    CloudRuleUpdateResult.Failed(reason)
                }
            }
        }
    }

    suspend fun importLocalZip(
        bytes: ByteArray,
        fileName: String = "imported.zip"
    ): Result<CloudRuleStatus> {
        if (!initialized) {
            initialize()
        }
        return withContext(Dispatchers.IO) {
            repoMutex.withLock {
                runCatching {
                    val nowMillis = System.currentTimeMillis()
                    val cleanName = fileName.trim().ifBlank { "imported.zip" }
                    val versionLabel = cleanName.substringBeforeLast('.').ifBlank { "本地导入" }.take(24)
                    val updatedAtIso = Instant.ofEpochMilli(nowMillis).toString()
                    val changelog = "从本地文件导入：$cleanName"

                    val bundle = JsRuleBundleLoader.loadFromZipBytes(
                        bytes = bytes,
                        source = JsRuleSource.IMPORTED,
                        version = versionLabel,
                        updatedAt = updatedAtIso,
                        changelog = changelog
                    ).getOrThrow()

                    ensureDir(storageDir)
                    val tempZip = File(storageDir, "import.${System.nanoTime()}.tmp")
                    try {
                        tempZip.writeBytes(bytes)
                        val actualSha = computeSha256Hex(bytes)
                        val storedMeta = StoredRuleMeta(
                            source = JsRuleSource.IMPORTED.name,
                            version = bundle.version,
                            updatedAt = bundle.updatedAt,
                            changelog = bundle.changelog,
                            sha256 = actualSha,
                            lastCheckedAtMillis = _status.value.lastCheckedAtMillis
                        )
                        commitActiveRulePackageLocked(tempZip, storedMeta)
                        activeSha256 = actualSha
                        ruleEngine.updateBundle(bundle)
                        val nextStatus = bundle.toStatus(
                            lastCheckedAtMillis = _status.value.lastCheckedAtMillis,
                            lastError = null
                        )
                        _status.value = nextStatus
                        nextStatus
                    } finally {
                        if (tempZip.exists()) tempZip.delete()
                    }
                }
            }
        }
    }

    suspend fun resetToBuiltin(): Result<CloudRuleStatus> {
        return withContext(Dispatchers.IO) {
            repoMutex.withLock {
                runCatching {
                    val lastChecked = _status.value.lastCheckedAtMillis
                    val loaded = loadBuiltinLocked(lastCheckedAtMillis = lastChecked, lastError = null)
                    if (!loaded) {
                        error(_status.value.lastError ?: "内置基线规则加载失败")
                    }
                    clearActiveStorageLocked()
                    initialized = true
                    _status.value
                }
            }
        }
    }

    private suspend fun tryLoadActiveRulesLocked(): Boolean {
        if (!activeZipFile.exists() || !activeZipFile.isFile) return false
        val storedMeta = readStoredMetaLocked()
        val source = storedMeta?.source?.let { raw ->
            JsRuleSource.entries.firstOrNull { it.name == raw }
        } ?: JsRuleSource.CLOUD

        val result = JsRuleBundleLoader.loadFromZipFile(
            file = activeZipFile,
            source = source,
            version = storedMeta?.version.orEmpty(),
            updatedAt = storedMeta?.updatedAt.orEmpty(),
            changelog = storedMeta?.changelog.orEmpty()
        )
        return result.fold(
            onSuccess = { bundle ->
                activeSha256 = storedMeta?.sha256?.takeIf { it.isNotBlank() }
                    ?: AppUpdateManager.computeFileSha256(activeZipFile)
                ruleEngine.updateBundle(bundle)
                _status.value = bundle.toStatus(
                    lastCheckedAtMillis = storedMeta?.lastCheckedAtMillis ?: 0L,
                    lastError = null
                )
                true
            },
            onFailure = {
                false
            }
        )
    }

    private fun loadBuiltinLocked(lastCheckedAtMillis: Long, lastError: String?): Boolean {
        val zipBytes = builtinZipProvider()
        if (zipBytes == null || zipBytes.isEmpty()) {
            ruleEngine.updateBundle(null)
            activeSha256 = ""
            _status.value = CloudRuleStatus(
                version = "未加载",
                source = JsRuleSource.BUILTIN,
                lastCheckedAtMillis = lastCheckedAtMillis,
                lastError = lastError ?: "未找到内置规则包"
            )
            return false
        }

        val manifest = builtinManifestProvider()?.takeIf { it.isNotBlank() }?.let { raw ->
            runCatching { json.decodeFromString<CloudRuleManifest>(stripBom(raw)) }.getOrNull()
        }
        val result = JsRuleBundleLoader.loadFromZipBytes(
            bytes = zipBytes,
            source = JsRuleSource.BUILTIN,
            version = manifest?.version.orEmpty(),
            updatedAt = manifest?.updatedAt.orEmpty(),
            changelog = manifest?.changelog.orEmpty()
        )

        return result.fold(
            onSuccess = { bundle ->
                activeSha256 = computeSha256Hex(zipBytes)
                ruleEngine.updateBundle(bundle)
                _status.value = bundle.toStatus(
                    lastCheckedAtMillis = lastCheckedAtMillis,
                    lastError = lastError
                )
                true
            },
            onFailure = { err ->
                ruleEngine.updateBundle(null)
                activeSha256 = ""
                _status.value = CloudRuleStatus(
                    version = "未加载",
                    source = JsRuleSource.BUILTIN,
                    lastCheckedAtMillis = lastCheckedAtMillis,
                    lastError = err.message ?: "内置规则包解析失败"
                )
                false
            }
        )
    }

    private suspend fun fetchRemoteTarget(
        rawUrl: String,
        mirrorPrefix: String
    ): RemoteTarget {
        val parsedPath = rawUrl.toHttpUrlOrNull()?.encodedPath.orEmpty()
        if (parsedPath.endsWith(".zip", ignoreCase = true)) {
            val (tempZip, sha) = downloadAndVerifyZip(
                rawZipUrl = rawUrl,
                mirrorPrefix = mirrorPrefix,
                expectedSha256 = null,
                expectedSizeBytes = null
            )
            return RemoteTarget.DirectZip(tempZip, sha)
        }

        val candidates = AppUpdateManager.buildCandidateUrls(rawUrl, mirrorPrefix, preferMirrorFirst = true)
        var lastError: Throwable? = null

        for ((index, url) in candidates.withIndex()) {
            coroutineContext.ensureActive()
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("Accept", "application/json, application/zip, */*")
                    .header("User-Agent", USER_AGENT)
                    .get()
                    .build()

                return executeCancellable(request) { response ->
                    if (!response.isSuccessful) {
                        error("HTTP ${response.code} ${response.message}")
                    }
                    val contentType = response.header("Content-Type").orEmpty().lowercase(Locale.ROOT)
                    if (contentType.contains("text/html") || contentType.contains("application/xhtml")) {
                        error("下载源返回了 HTML 网页而非规则数据")
                    }

                    val body = response.body ?: error("服务器返回内容为空")
                    val contentLength = body.contentLength()
                    if (contentLength > JsRuleBundleLoader.MAX_ZIP_BYTES) {
                        error("远程文件超过 10MB 大小限制")
                    }

                    if (contentType.contains("application/zip") || contentType.contains("application/x-zip")) {
                        ensureDir(storageDir)
                        val tempZip = File(storageDir, "direct.${System.nanoTime()}-$index.tmp")
                        try {
                            streamBodyToFile(body, tempZip, JsRuleBundleLoader.MAX_ZIP_BYTES)
                            val sha = AppUpdateManager.computeFileSha256(tempZip)
                            RemoteTarget.DirectZip(tempZip, sha)
                        } catch (e: Throwable) {
                            if (tempZip.exists()) tempZip.delete()
                            throw e
                        }
                    } else {
                        val rawBytes = readBoundedBytes(body, MAX_MANIFEST_BYTES)
                        if (looksLikeZipMagic(rawBytes)) {
                            ensureDir(storageDir)
                            val tempZip = File(storageDir, "direct.${System.nanoTime()}-$index.tmp")
                            try {
                                tempZip.writeBytes(rawBytes)
                                val sha = computeSha256Hex(rawBytes)
                                RemoteTarget.DirectZip(tempZip, sha)
                            } catch (e: Throwable) {
                                if (tempZip.exists()) tempZip.delete()
                                throw e
                            }
                        } else {
                            val text = stripBom(rawBytes.toString(Charsets.UTF_8)).trim()
                            if (text.isEmpty()) error("规则清单内容为空")
                            if (text.startsWith("<!DOCTYPE", ignoreCase = true) ||
                                text.startsWith("<html", ignoreCase = true)
                            ) {
                                error("下载源返回了 HTML 网页而非 JSON")
                            }
                            val manifest = json.decodeFromString<CloudRuleManifest>(text)
                            RemoteTarget.Manifest(manifest = manifest, resolvedUrl = rawUrl)
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                lastError = e
            }
        }
        throw lastError ?: IOException("拉取云端规则清单失败")
    }

    private suspend fun downloadAndVerifyZip(
        rawZipUrl: String,
        mirrorPrefix: String,
        expectedSha256: String?,
        expectedSizeBytes: Long?
    ): Pair<File, String> {
        ensureDir(storageDir)
        val candidates = AppUpdateManager.buildCandidateUrls(rawZipUrl, mirrorPrefix, preferMirrorFirst = true)
        var lastError: Throwable? = null

        for ((index, url) in candidates.withIndex()) {
            coroutineContext.ensureActive()
            val tempFile = File(storageDir, "rules.${System.nanoTime()}-$index.tmp")
            var keepTemp = false
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", USER_AGENT)
                    .get()
                    .build()

                executeCancellable(request) { response ->
                    if (!response.isSuccessful) {
                        error("下载失败：HTTP ${response.code}")
                    }
                    val contentType = response.header("Content-Type").orEmpty().lowercase(Locale.ROOT)
                    if (contentType.contains("text/html") || contentType.contains("application/xhtml")) {
                        error("下载源返回了 HTML 网页而非规则压缩包")
                    }
                    val body = response.body ?: error("服务器未返回规则包内容")
                    val contentLength = body.contentLength()
                    if (contentLength > JsRuleBundleLoader.MAX_ZIP_BYTES) {
                        error("规则包体积超过 10MB 安全限制")
                    }
                    streamBodyToFile(body, tempFile, JsRuleBundleLoader.MAX_ZIP_BYTES)
                }

                coroutineContext.ensureActive()
                val actualLen = tempFile.length()
                if (actualLen <= 0L || actualLen > JsRuleBundleLoader.MAX_ZIP_BYTES) {
                    error("下载的规则包体积无效（$actualLen 字节）")
                }
                if (expectedSizeBytes != null && actualLen != expectedSizeBytes) {
                    error("规则包大小校验失败（期望 $expectedSizeBytes 字节，实际 $actualLen 字节）")
                }

                val actualSha = AppUpdateManager.computeFileSha256(tempFile)
                if (!expectedSha256.isNullOrBlank() && !actualSha.equals(expectedSha256, ignoreCase = true)) {
                    error("规则包 SHA-256 校验未通过，文件可能已损坏或被篡改")
                }

                keepTemp = true
                return tempFile to actualSha
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                lastError = e
            } finally {
                if (!keepTemp && tempFile.exists()) {
                    tempFile.delete()
                }
            }
        }
        throw lastError ?: IOException("下载规则包失败")
    }

    private suspend fun <T> executeCancellable(
        request: Request,
        block: suspend (Response) -> T
    ): T = coroutineScope {
        coroutineContext.ensureActive()
        val call = httpClient.newCall(request)
        val finished = AtomicBoolean(false)
        val cancelWatcher = launch(Dispatchers.IO, start = CoroutineStart.UNDISPATCHED) {
            try {
                awaitCancellation()
            } finally {
                if (!finished.get()) {
                    runCatching { call.cancel() }
                }
            }
        }
        try {
            call.execute().use { response ->
                coroutineContext.ensureActive()
                block(response)
            }
        } catch (e: IOException) {
            coroutineContext.ensureActive()
            throw e
        } finally {
            finished.set(true)
            cancelWatcher.cancel()
        }
    }

    private suspend fun streamBodyToFile(
        body: okhttp3.ResponseBody,
        target: File,
        maxBytes: Long
    ) {
        var totalRead = 0L
        body.byteStream().use { input ->
            FileOutputStream(target).use { output ->
                val buffer = ByteArray(16 * 1024)
                while (true) {
                    coroutineContext.ensureActive()
                    val read = input.read(buffer)
                    if (read < 0) break
                    if (read == 0) continue
                    totalRead += read
                    if (totalRead > maxBytes) {
                        error("下载体积超过 ${maxBytes / (1024 * 1024)}MB 安全限制")
                    }
                    output.write(buffer, 0, read)
                }
                output.flush()
            }
        }
        val declaredLen = body.contentLength()
        if (declaredLen > 0L && totalRead < declaredLen) {
            error("文件下载不完整（已下载 $totalRead / $declaredLen 字节）")
        }
    }

    private suspend fun readBoundedBytes(body: okhttp3.ResponseBody, maxBytes: Long): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        var total = 0L
        body.byteStream().use { input ->
            val buffer = ByteArray(8 * 1024)
            while (true) {
                coroutineContext.ensureActive()
                val read = input.read(buffer)
                if (read < 0) break
                if (read == 0) continue
                total += read
                if (total > maxBytes) {
                    error("规则清单体积超过 ${maxBytes / 1024}KB 安全限制")
                }
                out.write(buffer, 0, read)
            }
        }
        return out.toByteArray()
    }

    private fun commitActiveRulePackageLocked(verifiedTempZip: File, meta: StoredRuleMeta) {
        ensureDir(activeDir)
        val tempMeta = File(activeDir, "$ACTIVE_META_NAME.${System.nanoTime()}.tmp")
        try {
            tempMeta.writeText(json.encodeToString(meta), Charsets.UTF_8)
            replaceFileAtomically(verifiedTempZip, activeZipFile)
            replaceFileAtomically(tempMeta, activeMetaFile)
        } finally {
            if (tempMeta.exists()) tempMeta.delete()
        }
    }

    private fun persistCheckedTimestampIfActiveLocked(checkedAtMillis: Long) {
        if (!activeZipFile.exists() || !activeMetaFile.exists()) return
        val existing = readStoredMetaLocked() ?: return
        val updated = existing.copy(lastCheckedAtMillis = checkedAtMillis)
        val tempMeta = File(activeDir, "$ACTIVE_META_NAME.${System.nanoTime()}.tmp")
        try {
            tempMeta.writeText(json.encodeToString(updated), Charsets.UTF_8)
            replaceFileAtomically(tempMeta, activeMetaFile)
        } catch (_: Throwable) {
            if (tempMeta.exists()) tempMeta.delete()
        }
    }

    private fun readStoredMetaLocked(): StoredRuleMeta? {
        if (!activeMetaFile.exists() || !activeMetaFile.isFile) return null
        return runCatching {
            val raw = stripBom(activeMetaFile.readText(Charsets.UTF_8))
            json.decodeFromString<StoredRuleMeta>(raw)
        }.getOrNull()
    }

    private fun clearActiveStorageLocked() {
        runCatching {
            if (activeZipFile.exists()) activeZipFile.delete()
            if (activeMetaFile.exists()) activeMetaFile.delete()
        }
    }

    private fun replaceFileAtomically(source: File, destination: File) {
        ensureDir(destination.parentFile ?: activeDir)
        if (destination.exists()) {
            destination.delete()
        }
        if (!source.renameTo(destination)) {
            source.copyTo(destination, overwrite = true)
            source.delete()
        }
    }

    private fun resolveUrlAgainstBase(baseUrl: String, targetUrl: String): String? {
        val clean = targetUrl.trim()
        if (clean.isEmpty()) return null
        clean.toHttpUrlOrNull()?.let { return it.toString() }
        return baseUrl.toHttpUrlOrNull()?.resolve(clean)?.toString()
    }

    private fun ensureDir(dir: File) {
        if (!dir.exists()) {
            dir.mkdirs()
        }
    }

    private fun looksLikeZipMagic(bytes: ByteArray): Boolean =
        bytes.size >= 4 &&
            bytes[0] == 0x50.toByte() &&
            bytes[1] == 0x4B.toByte() &&
            bytes[2] == 0x03.toByte() &&
            bytes[3] == 0x04.toByte()

    private fun stripBom(text: String): String =
        if (text.startsWith('\uFEFF')) text.substring(1) else text

    private fun computeSha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    companion object {
        const val DEFAULT_MANIFEST_URL =
            "https://raw.githubusercontent.com/${BuildConfig.GITHUB_OWNER}/${BuildConfig.GITHUB_REPO}/main/rules/manifest.json"
        private const val MAX_MANIFEST_BYTES = 1L * 1024L * 1024L
        private const val USER_AGENT = "Yubrae-Android/${BuildConfig.VERSION_NAME}"
        private const val ACTIVE_DIR_NAME = "active"
        private const val ACTIVE_ZIP_NAME = "rules.zip"
        private const val ACTIVE_META_NAME = "meta.json"
        private const val BUILTIN_ZIP_ASSET_PATH = "rules/builtin_rules.zip"
        private const val BUILTIN_MANIFEST_ASSET_PATH = "rules/builtin_manifest.json"
        private val SHA256_REGEX = Regex("^[a-fA-F0-9]{64}$")

        private fun readAssetBytes(context: Context, assetPath: String): ByteArray? =
            kotlin.runCatching {
                context.applicationContext.assets.open(assetPath).use { it.readBytes() }
            }.getOrNull()

        private fun readAssetText(context: Context, assetPath: String): String? =
            kotlin.runCatching {
                context.applicationContext.assets.open(assetPath)
                    .bufferedReader(Charsets.UTF_8)
                    .use { it.readText() }
            }.getOrNull()

        fun validateSubscriptionUrl(rawUrl: String): String? {
            val cleaned = rawUrl.trim()
            if (cleaned.isEmpty()) return null
            if (cleaned.any { it.isWhitespace() }) {
                return "订阅地址不能包含空格，请检查后重试"
            }
            val parsed = cleaned.toHttpUrlOrNull()
                ?: return "订阅地址格式无效，请填写完整 URL（如 https://.../manifest.json）"
            val scheme = parsed.scheme.lowercase(Locale.ROOT)
            val host = parsed.host.lowercase(Locale.ROOT)
            if (host.isBlank() || host.startsWith(".") || host.endsWith(".")) {
                return "订阅地址缺少有效域名"
            }
            val isLocalLoopback = host == "127.0.0.1" || host == "localhost"
            if (scheme != "https" && !(scheme == "http" && isLocalLoopback)) {
                return "订阅地址必须使用 https:// 协议（仅本地回环 127.0.0.1/localhost 允许 http://）"
            }
            return null
        }
    }
}
