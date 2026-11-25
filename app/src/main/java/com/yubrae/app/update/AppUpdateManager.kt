package com.yubrae.app.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.yubrae.app.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.coroutineContext

@Serializable
data class GitHubReleaseAssetDto(
    val name: String = "",
    val size: Long = 0L,
    @SerialName("browser_download_url") val browserDownloadUrl: String = ""
)

@Serializable
data class GitHubReleaseDto(
    @SerialName("tag_name") val tagName: String = "",
    val name: String = "",
    val body: String = "",
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    @SerialName("published_at") val publishedAt: String = "",
    @SerialName("html_url") val htmlUrl: String = "",
    val assets: List<GitHubReleaseAssetDto> = emptyList()
)

data class AppUpdateInfo(
    val tagName: String,
    val versionName: String,
    val releaseTitle: String,
    val releaseNotes: String,
    val publishedAt: String,
    val htmlUrl: String,
    val assetName: String,
    val downloadUrl: String,
    val assetSizeBytes: Long,
    val expectedSha256: String? = null,
    val sha256AssetUrl: String? = null
)

sealed interface UpdateCheckResult {
    data class UpdateAvailable(val info: AppUpdateInfo) : UpdateCheckResult
    data class UpToDate(val currentVersion: String, val latestTag: String) : UpdateCheckResult
    data class Failed(val reason: String, val fallbackUrl: String) : UpdateCheckResult
}

sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Checking : UpdateUiState
    data class UpToDate(val currentVersion: String, val latestTag: String) : UpdateUiState
    data class Available(val info: AppUpdateInfo) : UpdateUiState
    data class Downloading(
        val info: AppUpdateInfo,
        val downloadedBytes: Long,
        val totalBytes: Long
    ) : UpdateUiState
    data class Verifying(val info: AppUpdateInfo) : UpdateUiState
    data class ReadyToInstall(
        val info: AppUpdateInfo,
        val apkFile: File,
        val sha256Verified: Boolean = false
    ) : UpdateUiState
    data class Error(
        val message: String,
        val fallbackUrl: String,
        val info: AppUpdateInfo? = null
    ) : UpdateUiState
}

data class VerifiedApk(
    val file: File,
    val sha256: String,
    val sha256Verified: Boolean = false
)

class AppUpdateManager private constructor(
    private val context: Context?,
    baseHttpClient: OkHttpClient,
    private val updatesDir: File,
    private val latestReleaseApiUrl: String,
    private val releasePageUrl: String,
    private val verifyArchiveWithPackageManager: Boolean
) {
    constructor(
        context: Context,
        baseHttpClient: OkHttpClient,
        updatesDir: File = File(context.cacheDir, "updates"),
        latestReleaseApiUrl: String = DEFAULT_LATEST_API_URL,
        releasePageUrl: String = DEFAULT_RELEASE_PAGE_URL
    ) : this(
        context = context.applicationContext,
        baseHttpClient = baseHttpClient,
        updatesDir = updatesDir,
        latestReleaseApiUrl = latestReleaseApiUrl,
        releasePageUrl = releasePageUrl,
        verifyArchiveWithPackageManager = true
    )

    internal constructor(
        updatesDir: File,
        baseHttpClient: OkHttpClient,
        latestReleaseApiUrl: String = DEFAULT_LATEST_API_URL,
        releasePageUrl: String = DEFAULT_RELEASE_PAGE_URL
    ) : this(
        context = null,
        baseHttpClient = baseHttpClient,
        updatesDir = updatesDir,
        latestReleaseApiUrl = latestReleaseApiUrl,
        releasePageUrl = releasePageUrl,
        verifyArchiveWithPackageManager = false
    )

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private val checkClient: OkHttpClient = baseHttpClient.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val downloadClient: OkHttpClient = baseHttpClient.newBuilder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()

    suspend fun cleanupOldApks(
        nowMillis: Long = System.currentTimeMillis(),
        maxApkAgeMillis: Long = OLD_APK_MAX_AGE_MILLIS
    ) = withContext(Dispatchers.IO) {
        try {
            coroutineContext.ensureActive()
            if (!updatesDir.exists()) return@withContext
            updatesDir.listFiles()?.forEach { file ->
                coroutineContext.ensureActive()
                if (!file.isFile) return@forEach
                val isTemp = file.name.endsWith(".tmp", ignoreCase = true)
                val isExpiredApk = file.name.endsWith(".apk", ignoreCase = true) &&
                    (nowMillis - file.lastModified() >= maxApkAgeMillis)
                if (isTemp || isExpiredApk) {
                    file.delete()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            // 忽略缓存清理异常
        }
    }

    suspend fun checkLatestRelease(
        currentVersionName: String = BuildConfig.VERSION_NAME,
        buildType: String = BuildConfig.BUILD_TYPE,
        mirrorPrefix: String = ""
    ): UpdateCheckResult = withContext(Dispatchers.IO) {
        val candidateApiUrls = buildCandidateUrls(latestReleaseApiUrl, mirrorPrefix, preferMirrorFirst = false)
        var directError: Throwable? = null
        var fallbackError: Throwable? = null
        var releaseDto: GitHubReleaseDto? = null

        for ((index, apiUrl) in candidateApiUrls.withIndex()) {
            coroutineContext.ensureActive()
            try {
                val request = Request.Builder()
                    .url(apiUrl)
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", USER_AGENT)
                    .get()
                    .build()
                releaseDto = executeCancellable(checkClient, request) { response ->
                    if (!response.isSuccessful) {
                        error("HTTP ${response.code} ${response.message}")
                    }
                    val contentType = response.header("Content-Type").orEmpty()
                    if (contentType.contains("text/html", ignoreCase = true) ||
                        contentType.contains("application/xhtml", ignoreCase = true)
                    ) {
                        error("接口返回了 HTML 网页而非 JSON")
                    }
                    val bodyText = response.body?.string().orEmpty()
                    if (bodyText.isBlank()) error("返回内容为空")
                    json.decodeFromString<GitHubReleaseDto>(bodyText)
                }
                break
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                if (index == 0) {
                    directError = e
                } else {
                    fallbackError = e
                }
            }
        }

        if (releaseDto == null) {
            val effectiveError = directError ?: fallbackError
            val detail = effectiveError?.message?.take(120) ?: "网络连接超时"
            return@withContext UpdateCheckResult.Failed(
                reason = "检查更新失败：$detail",
                fallbackUrl = releasePageUrl
            )
        }

        if (releaseDto.draft) {
            return@withContext UpdateCheckResult.UpToDate(
                currentVersion = currentVersionName,
                latestTag = currentVersionName
            )
        }

        val safeHtmlUrl = releaseDto.htmlUrl.trim()
            .takeIf { isAllowedBrowserUrl(it) }
            ?: releasePageUrl

        val latestVersion = normalizeVersion(releaseDto.tagName)
        if (latestVersion.isBlank()) {
            return@withContext UpdateCheckResult.Failed(
                reason = "无法解析最新版本标签：${releaseDto.tagName}",
                fallbackUrl = safeHtmlUrl
            )
        }

        if (compareVersions(latestVersion, currentVersionName) <= 0) {
            return@withContext UpdateCheckResult.UpToDate(
                currentVersion = currentVersionName,
                latestTag = releaseDto.tagName
            )
        }

        val apkAsset = selectBestApkAsset(releaseDto.assets, buildType)
        if (apkAsset == null || apkAsset.browserDownloadUrl.isBlank()) {
            return@withContext UpdateCheckResult.Failed(
                reason = "发现新版本 ${releaseDto.tagName}，但未找到适配 ${buildType} 的 APK 下载资源",
                fallbackUrl = safeHtmlUrl
            )
        }

        val shaAsset = releaseDto.assets.firstOrNull {
            it.name.equals(SHA256_ASSET_NAME, ignoreCase = true) && it.browserDownloadUrl.isNotBlank()
        }

        UpdateCheckResult.UpdateAvailable(
            AppUpdateInfo(
                tagName = releaseDto.tagName,
                versionName = latestVersion,
                releaseTitle = releaseDto.name.ifBlank { releaseDto.tagName },
                releaseNotes = releaseDto.body.trim(),
                publishedAt = releaseDto.publishedAt,
                htmlUrl = safeHtmlUrl,
                assetName = apkAsset.name,
                downloadUrl = apkAsset.browserDownloadUrl,
                assetSizeBytes = apkAsset.size,
                expectedSha256 = null,
                sha256AssetUrl = shaAsset?.browserDownloadUrl
            )
        )
    }

    suspend fun downloadAndVerify(
        info: AppUpdateInfo,
        mirrorPrefix: String = "",
        onVerifying: () -> Unit = {},
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit = { _, _ -> }
    ): Result<VerifiedApk> = withContext(Dispatchers.IO) {
        try {
            coroutineContext.ensureActive()
            if (!updatesDir.exists()) {
                updatesDir.mkdirs()
            }
            val safeName = info.assetName.replace(Regex("[^A-Za-z0-9._-]"), "_")
                .ifBlank { "yubrae-update.apk" }
            val targetFile = File(updatesDir, safeName)
            val urlsToTry = buildCandidateUrls(info.downloadUrl, mirrorPrefix, preferMirrorFirst = true)

            var expectedSha = info.expectedSha256?.trim()?.takeIf { it.isNotEmpty() }?.lowercase(Locale.ROOT)
            var shaFetchAttempted = expectedSha != null
            var lastError: Throwable? = null

            for ((attemptIndex, url) in urlsToTry.withIndex()) {
                coroutineContext.ensureActive()
                val tempFile = File(
                    updatesDir,
                    "$safeName.${System.nanoTime()}-$attemptIndex.tmp"
                )
                var committed = false
                try {
                    streamDownloadToFile(
                        url = url,
                        tempFile = tempFile,
                        fallbackTotalBytes = info.assetSizeBytes,
                        onProgress = onProgress
                    )
                    coroutineContext.ensureActive()

                    if (!tempFile.exists() || tempFile.length() <= 0L) {
                        error("下载的 APK 文件为空，请重试或改用浏览器下载")
                    }

                    onVerifying()
                    coroutineContext.ensureActive()

                    if (!shaFetchAttempted || expectedSha == null) {
                        val shaUrl = info.sha256AssetUrl
                        if (!shaUrl.isNullOrBlank()) {
                            expectedSha = fetchExpectedSha256(
                                shaUrl = shaUrl,
                                assetName = info.assetName,
                                mirrorPrefix = mirrorPrefix
                            )
                        }
                        shaFetchAttempted = true
                    }
                    coroutineContext.ensureActive()

                    val actualSha = computeFileSha256(tempFile)
                    val shaVerified = !expectedSha.isNullOrBlank()
                    if (shaVerified && !actualSha.equals(expectedSha, ignoreCase = true)) {
                        error("安装包 SHA-256 校验未通过，文件可能已损坏或被篡改")
                    }

                    if (verifyArchiveWithPackageManager) {
                        validateApkPackageCompatibility(tempFile)
                    }
                    coroutineContext.ensureActive()

                    commitVerifiedApk(tempFile, targetFile)
                    committed = true
                    return@withContext Result.success(
                        VerifiedApk(
                            file = targetFile,
                            sha256 = actualSha,
                            sha256Verified = shaVerified
                        )
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    lastError = e
                } finally {
                    if (!committed && tempFile.exists()) {
                        tempFile.delete()
                    }
                }
            }

            Result.failure(lastError ?: IllegalStateException("安装包下载失败"))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    private suspend fun <T> executeCancellable(
        client: OkHttpClient,
        request: Request,
        block: suspend (Response) -> T
    ): T = withContext(Dispatchers.IO) {
        coroutineScope {
            coroutineContext.ensureActive()
            val call = client.newCall(request)
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
    }

    private suspend fun streamDownloadToFile(
        url: String,
        tempFile: File,
        fallbackTotalBytes: Long,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit
    ) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .get()
            .build()

        executeCancellable(downloadClient, request) { response ->
            if (!response.isSuccessful) {
                error("下载失败：HTTP ${response.code}")
            }
            val contentType = response.header("Content-Type").orEmpty().lowercase(Locale.ROOT)
            if (contentType.contains("text/html") || contentType.contains("application/xhtml")) {
                error("下载源返回了 HTML 网页而非安装包（$contentType）")
            }
            val body = response.body ?: error("服务器未返回文件内容")
            val declaredContentLen = body.contentLength()
            val totalLen = declaredContentLen.takeIf { it > 0L } ?: fallbackTotalBytes.coerceAtLeast(0L)
            var downloadedBytes = 0L
            var lastNotifyAt = 0L

            body.byteStream().use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        if (read == 0) continue
                        output.write(buffer, 0, read)
                        downloadedBytes += read
                        val now = System.currentTimeMillis()
                        if (now - lastNotifyAt >= 120L || (totalLen > 0L && downloadedBytes >= totalLen)) {
                            lastNotifyAt = now
                            onProgress(downloadedBytes, totalLen)
                            coroutineContext.ensureActive()
                        }
                    }
                    output.flush()
                }
            }

            coroutineContext.ensureActive()
            if (declaredContentLen > 0L && downloadedBytes < declaredContentLen) {
                error("安装包下载不完整（已下载 $downloadedBytes / $declaredContentLen 字节）")
            }
            onProgress(downloadedBytes, totalLen)
        }
    }

    private fun commitVerifiedApk(tempFile: File, targetFile: File) {
        if (targetFile.exists()) {
            targetFile.delete()
        }
        if (!tempFile.renameTo(targetFile)) {
            try {
                tempFile.copyTo(targetFile, overwrite = true)
            } catch (e: Throwable) {
                if (targetFile.exists()) targetFile.delete()
                throw e
            } finally {
                if (tempFile.exists()) tempFile.delete()
            }
        }
    }

    private fun validateApkPackageCompatibility(apkFile: File) {
        val ctx = context ?: return
        val pm = ctx.packageManager
        val archiveInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageArchiveInfo(apkFile.absolutePath, PackageManager.PackageInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageArchiveInfo(apkFile.absolutePath, 0)
        } ?: run {
            apkFile.delete()
            error("无法解析下载的安装包文件，请重新下载或通过浏览器安装")
        }

        val currentPkg = ctx.packageName
        if (archiveInfo.packageName != currentPkg) {
            apkFile.delete()
            error(
                "安装包包名（${archiveInfo.packageName}）与当前安装版本（$currentPkg）不一致，请卸载当前调试版或从 Release 下载对应变体"
            )
        }
    }

    private suspend fun fetchExpectedSha256(
        shaUrl: String,
        assetName: String,
        mirrorPrefix: String
    ): String? {
        // 直连 GitHub 优先拉取 SHA256SUMS.txt，避免镜像同时篡改校验文件与 APK
        val candidates = buildCandidateUrls(shaUrl, mirrorPrefix, preferMirrorFirst = false)
        for (url in candidates) {
            coroutineContext.ensureActive()
            val text = try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", USER_AGENT)
                    .get()
                    .build()
                executeCancellable(checkClient, request) { resp ->
                    if (!resp.isSuccessful) return@executeCancellable null
                    val contentType = resp.header("Content-Type").orEmpty()
                    if (contentType.contains("text/html", ignoreCase = true) ||
                        contentType.contains("application/xhtml", ignoreCase = true)
                    ) {
                        return@executeCancellable null
                    }
                    resp.body?.string()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                null
            }
            if (!text.isNullOrBlank()) {
                val matched = parseSha256ForAsset(text, assetName)
                if (matched != null) return matched
            }
        }
        return null
    }

    fun canRequestPackageInstalls(ctx: Context? = context): Boolean {
        val targetCtx = ctx ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching { targetCtx.packageManager.canRequestPackageInstalls() }.getOrDefault(false)
        } else {
            true
        }
    }

    fun createUnknownAppSourcesIntent(ctx: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${ctx.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            Intent(Settings.ACTION_SECURITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }

    fun launchApkInstaller(ctx: Context, apkFile: File): Result<Unit> = runCatching {
        require(apkFile.exists() && apkFile.length() > 0L) { "安装包不存在，请重新下载" }
        val authority = "${ctx.packageName}.fileprovider"
        val apkUri = FileProvider.getUriForFile(ctx, authority, apkFile)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, APK_MIME_TYPE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(intent)
    }

    fun openReleaseInBrowser(ctx: Context, url: String = releasePageUrl): Boolean {
        val target = url.trim().ifBlank { releasePageUrl }
        if (!isAllowedBrowserUrl(target)) return false
        return runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(target)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    sealed interface MirrorPrefixValidation {
        data class Valid(val normalizedPrefix: String) : MirrorPrefixValidation
        data class Invalid(val reason: String) : MirrorPrefixValidation
    }

    companion object {
        const val DEFAULT_LATEST_API_URL =
            "https://api.github.com/repos/${BuildConfig.GITHUB_OWNER}/${BuildConfig.GITHUB_REPO}/releases/latest"
        const val DEFAULT_RELEASE_PAGE_URL =
            "https://github.com/${BuildConfig.GITHUB_OWNER}/${BuildConfig.GITHUB_REPO}/releases"
        internal const val OLD_APK_MAX_AGE_MILLIS = 24L * 60L * 60L * 1000L
        private const val USER_AGENT = "Yubrae-Android/${BuildConfig.VERSION_NAME}"
        private const val SHA256_ASSET_NAME = "SHA256SUMS.txt"
        private const val APK_MIME_TYPE = "application/vnd.android.package-archive"
        private val BUILD_VARIANT_SUFFIX_REGEX = Regex("-debug$", RegexOption.IGNORE_CASE)

        internal fun normalizeVersion(raw: String): String {
            return raw.trim()
                .removePrefix("v")
                .removePrefix("V")
                .trim()
        }

        private fun stripBuildVariantSuffix(ver: String): String {
            var result = ver.trim()
            while (BUILD_VARIANT_SUFFIX_REGEX.containsMatchIn(result)) {
                result = result.replace(BUILD_VARIANT_SUFFIX_REGEX, "")
            }
            return result
        }

        /**
         * 语义化版本比对：
         * - 剥离 `-debug` 构建变体后缀（不区分大小写），避免同版本 `debug` 本地调试包循环提示更新
         * - 保留 `-alpha` / `-beta` / `-rc` 等真实预发布后缀，同主版本正式版高于预发布版（`1.0.1` > `1.0.1-beta`）
         * - `1.10.0` > `1.2.0`、`1.0.2-debug` > `1.0.1-debug`、`v1.1.0` == `1.1.0-debug`
         */
        internal fun compareVersions(candidateRaw: String, currentRaw: String): Int {
            val cand = stripBuildVariantSuffix(normalizeVersion(candidateRaw))
            val curr = stripBuildVariantSuffix(normalizeVersion(currentRaw))
            val (candCore, candSuffix) = splitCoreAndSuffix(cand)
            val (currCore, currSuffix) = splitCoreAndSuffix(curr)

            val candParts = candCore.split('.').map { it.toIntOrNull() ?: 0 }
            val currParts = currCore.split('.').map { it.toIntOrNull() ?: 0 }
            val maxLen = maxOf(candParts.size, currParts.size)
            for (i in 0 until maxLen) {
                val a = candParts.getOrElse(i) { 0 }
                val b = currParts.getOrElse(i) { 0 }
                if (a != b) return a.compareTo(b)
            }

            return when {
                candSuffix.isEmpty() && currSuffix.isNotEmpty() -> 1
                candSuffix.isNotEmpty() && currSuffix.isEmpty() -> -1
                else -> candSuffix.compareTo(currSuffix, ignoreCase = true)
            }
        }

        private fun splitCoreAndSuffix(ver: String): Pair<String, String> {
            val dashIdx = ver.indexOf('-')
            return if (dashIdx >= 0) {
                ver.substring(0, dashIdx) to ver.substring(dashIdx + 1)
            } else {
                ver to ""
            }
        }

        internal fun selectBestApkAsset(
            assets: List<GitHubReleaseAssetDto>,
            buildType: String
        ): GitHubReleaseAssetDto? {
            val apks = assets.filter {
                it.name.endsWith(".apk", ignoreCase = true) && it.browserDownloadUrl.isNotBlank()
            }
            if (apks.isEmpty()) return null

            return when (buildType.lowercase(Locale.ROOT)) {
                "release" -> {
                    apks.firstOrNull { it.name.endsWith("-release.apk", ignoreCase = true) }
                        ?: apks.firstOrNull { !it.name.contains("debug", ignoreCase = true) }
                }
                else -> {
                    apks.firstOrNull { it.name.endsWith("-debug.apk", ignoreCase = true) }
                        ?: apks.firstOrNull { it.name.endsWith("-release.apk", ignoreCase = true) }
                }
            }
        }

        internal fun parseSha256ForAsset(sumsText: String, assetName: String): String? {
            val targetName = assetName.trim()
            if (targetName.isEmpty()) return null
            val regex = Regex("^([a-fA-F0-9]{64})\\s+[* ]?(.+)$")
            for (rawLine in sumsText.lineSequence()) {
                val line = rawLine.trim()
                if (line.isEmpty()) continue
                val match = regex.matchEntire(line) ?: continue
                val hash = match.groupValues[1].lowercase(Locale.ROOT)
                val fileRef = match.groupValues[2].trim().substringAfterLast('/')
                if (fileRef.equals(targetName, ignoreCase = true)) {
                    return hash
                }
            }
            return null
        }

        fun validateMirrorPrefix(rawPrefix: String): MirrorPrefixValidation {
            val cleaned = rawPrefix.trim()
            if (cleaned.isEmpty()) return MirrorPrefixValidation.Valid("")
            if (cleaned.any { it.isWhitespace() }) {
                return MirrorPrefixValidation.Invalid("镜像地址不能包含空格，请检查后重试")
            }
            val parsed = cleaned.toHttpUrlOrNull()
                ?: return MirrorPrefixValidation.Invalid("镜像地址格式无效，请填写完整 URL（如 https://gh-proxy.com/）")
            val scheme = parsed.scheme.lowercase(Locale.ROOT)
            val host = parsed.host.lowercase(Locale.ROOT)
            if (host.isBlank() || host.startsWith(".") || host.endsWith(".")) {
                return MirrorPrefixValidation.Invalid("镜像地址缺少有效域名，请填写完整 URL（如 https://gh-proxy.com/）")
            }
            val isLocalLoopback = host == "127.0.0.1" || host == "localhost"
            if (scheme != "https" && !(scheme == "http" && isLocalLoopback)) {
                return MirrorPrefixValidation.Invalid("镜像地址必须使用 https:// 协议（如 https://gh-proxy.com/）")
            }
            if (parsed.query != null || parsed.fragment != null) {
                return MirrorPrefixValidation.Invalid("镜像前缀不能包含查询参数或锚点，请填写路径前缀（如 https://gh-proxy.com/）")
            }
            val normalized = if (cleaned.endsWith("/")) cleaned else "$cleaned/"
            return MirrorPrefixValidation.Valid(normalized)
        }

        internal fun isAllowedBrowserUrl(url: String): Boolean {
            val trimmed = url.trim()
            if (trimmed.isEmpty() || trimmed.any { it.isWhitespace() }) return false
            val parsed = trimmed.toHttpUrlOrNull() ?: return false
            val scheme = parsed.scheme.lowercase(Locale.ROOT)
            return (scheme == "https" || scheme == "http") && parsed.host.isNotBlank()
        }

        internal fun applyMirrorPrefix(rawUrl: String, mirrorPrefix: String): String {
            val cleanRaw = rawUrl.trim()
            val validation = validateMirrorPrefix(mirrorPrefix)
            val normalizedPrefix = (validation as? MirrorPrefixValidation.Valid)?.normalizedPrefix.orEmpty()
            if (normalizedPrefix.isEmpty()) return cleanRaw
            if (cleanRaw.startsWith(normalizedPrefix, ignoreCase = true)) {
                return cleanRaw
            }
            val candidate = normalizedPrefix + cleanRaw
            return if (candidate.toHttpUrlOrNull() != null) candidate else cleanRaw
        }

        internal fun buildCandidateUrls(
            rawUrl: String,
            mirrorPrefix: String,
            preferMirrorFirst: Boolean = false
        ): List<String> {
            val cleanRaw = rawUrl.trim()
            val mirrored = applyMirrorPrefix(cleanRaw, mirrorPrefix)
            if (mirrored == cleanRaw) {
                return listOf(cleanRaw)
            }
            return if (preferMirrorFirst) {
                listOf(mirrored, cleanRaw)
            } else {
                listOf(cleanRaw, mirrored)
            }
        }

        internal suspend fun computeFileSha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(16 * 1024)
                while (true) {
                    coroutineContext.ensureActive()
                    val read = input.read(buffer)
                    if (read < 0) break
                    if (read > 0) digest.update(buffer, 0, read)
                }
            }
            coroutineContext.ensureActive()
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
