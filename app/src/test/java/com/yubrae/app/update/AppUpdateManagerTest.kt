package com.yubrae.app.update

import com.yubrae.app.testutil.TestHttpServer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger

class AppUpdateManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun compareVersions_handlesSemverAndBuildSuffixes() {
        assertTrue(AppUpdateManager.compareVersions("v1.1.0", "1.0.1") > 0)
        assertTrue(AppUpdateManager.compareVersions("v1.10.0", "1.2.9") > 0)
        assertTrue(AppUpdateManager.compareVersions("1.1.0-debug", "1.0.1-debug") > 0)
        assertTrue(AppUpdateManager.compareVersions("v1.1.0", "1.0.1-debug") > 0)

        // 构建变体后缀 -debug 与同版本正式 Tag 视为相等，避免本地调试包循环更新
        assertEquals(0, AppUpdateManager.compareVersions("v1.1.0", "1.1.0"))
        assertEquals(0, AppUpdateManager.compareVersions("1.1.0", "1.1.0-debug"))
        assertEquals(0, AppUpdateManager.compareVersions("v1.1.0", "1.1.0-DEBUG"))
        assertEquals(0, AppUpdateManager.compareVersions("1.1.0-rc.1", "1.1.0-rc.1-debug"))

        // 保留真实预发布后缀语义：正式版 > 预发布版
        assertTrue(AppUpdateManager.compareVersions("1.1.0", "1.1.0-beta") > 0)
        assertTrue(AppUpdateManager.compareVersions("1.1.0", "1.1.0-alpha.1") > 0)
        assertTrue(AppUpdateManager.compareVersions("1.1.0-rc.1", "1.1.0-beta.1") > 0)
        assertTrue(AppUpdateManager.compareVersions("1.0.1", "1.1.0") < 0)
    }

    @Test
    fun selectBestApkAsset_matchesBuildTypeStrictlyForRelease() {
        val dualPlatformAssets = listOf(
            GitHubReleaseAssetDto("yubrae-v1.1.0-release.apk", 100L, "https://example.com/release.apk"),
            GitHubReleaseAssetDto("yubrae-v1.1.0-ios-unsigned.ipa", 120L, "https://example.com/ios.ipa"),
            GitHubReleaseAssetDto("SHA256SUMS.txt", 10L, "https://example.com/SHA256SUMS.txt")
        )
        assertEquals("yubrae-v1.1.0-release.apk", AppUpdateManager.selectBestApkAsset(dualPlatformAssets, "release")?.name)
        // 本地 debug 变体在仅有正式 Release 资产时可回退匹配 release.apk
        assertEquals("yubrae-v1.1.0-release.apk", AppUpdateManager.selectBestApkAsset(dualPlatformAssets, "debug")?.name)

        val mixedAssets = listOf(
            GitHubReleaseAssetDto("yubrae-v1.1.0-debug.apk", 100L, "https://example.com/debug.apk"),
            GitHubReleaseAssetDto("yubrae-v1.1.0-release.apk", 100L, "https://example.com/release.apk"),
            GitHubReleaseAssetDto("SHA256SUMS.txt", 10L, "https://example.com/SHA256SUMS.txt")
        )
        assertEquals("yubrae-v1.1.0-release.apk", AppUpdateManager.selectBestApkAsset(mixedAssets, "release")?.name)
        assertEquals("yubrae-v1.1.0-debug.apk", AppUpdateManager.selectBestApkAsset(mixedAssets, "debug")?.name)

        // 若没有 -release.apk，正式版可匹配不含 debug 的通用 .apk
        val genericApkOnly = listOf(
            GitHubReleaseAssetDto("yubrae-v1.1.0.apk", 100L, "https://example.com/generic.apk")
        )
        assertEquals("yubrae-v1.1.0.apk", AppUpdateManager.selectBestApkAsset(genericApkOnly, "release")?.name)

        // 若仅有 debug 包，正式版不能误下 debug 包
        val debugOnly = listOf(
            GitHubReleaseAssetDto("yubrae-v1.1.0-debug.apk", 100L, "https://example.com/debug.apk")
        )
        assertNull(AppUpdateManager.selectBestApkAsset(debugOnly, "release"))
    }

    @Test
    fun validateMirrorPrefixAndBrowserUrl_enforcesStrictUrlRules() {
        assertEquals(
            AppUpdateManager.MirrorPrefixValidation.Valid(""),
            AppUpdateManager.validateMirrorPrefix("   ")
        )
        assertEquals(
            AppUpdateManager.MirrorPrefixValidation.Valid("https://gh-proxy.com/"),
            AppUpdateManager.validateMirrorPrefix("https://gh-proxy.com")
        )
        assertEquals(
            AppUpdateManager.MirrorPrefixValidation.Valid("https://gh-proxy.com/mirror/"),
            AppUpdateManager.validateMirrorPrefix("https://gh-proxy.com/mirror/")
        )
        assertEquals(
            AppUpdateManager.MirrorPrefixValidation.Valid("http://127.0.0.1:8080/mirror/"),
            AppUpdateManager.validateMirrorPrefix("http://127.0.0.1:8080/mirror")
        )
        assertEquals(
            AppUpdateManager.MirrorPrefixValidation.Valid("http://localhost:9000/"),
            AppUpdateManager.validateMirrorPrefix("http://localhost:9000/")
        )

        assertTrue(AppUpdateManager.validateMirrorPrefix("http://example.com/") is AppUpdateManager.MirrorPrefixValidation.Invalid)
        assertTrue(AppUpdateManager.validateMirrorPrefix("https://") is AppUpdateManager.MirrorPrefixValidation.Invalid)
        assertTrue(AppUpdateManager.validateMirrorPrefix("gh-proxy.com") is AppUpdateManager.MirrorPrefixValidation.Invalid)
        assertTrue(AppUpdateManager.validateMirrorPrefix("https://gh proxy.com/") is AppUpdateManager.MirrorPrefixValidation.Invalid)
        assertTrue(AppUpdateManager.validateMirrorPrefix("ftp://invalid.local") is AppUpdateManager.MirrorPrefixValidation.Invalid)
        assertTrue(AppUpdateManager.validateMirrorPrefix("https://gh-proxy.com/?k=v") is AppUpdateManager.MirrorPrefixValidation.Invalid)

        assertTrue(AppUpdateManager.isAllowedBrowserUrl("https://github.com/org/repo/releases"))
        assertTrue(AppUpdateManager.isAllowedBrowserUrl("http://127.0.0.1:8080/releases"))
        assertFalse(AppUpdateManager.isAllowedBrowserUrl("intent://scan/#Intent;end"))
        assertFalse(AppUpdateManager.isAllowedBrowserUrl("yubrae://addbill?amount=10"))
        assertFalse(AppUpdateManager.isAllowedBrowserUrl("javascript:alert(1)"))
    }

    @Test
    fun parseSha256AndApplyMirrorPrefix_workAccurately() {
        val sums = """
            ABCDEF1234567890abcdef1234567890abcdef1234567890abcdef1234567890  yubrae-v1.1.0-release.apk
            00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff *dist/yubrae-v1.1.0-ios-unsigned.ipa
        """.trimIndent()

        assertEquals(
            "abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890",
            AppUpdateManager.parseSha256ForAsset(sums, "yubrae-v1.1.0-release.apk")
        )
        assertEquals(
            "00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff",
            AppUpdateManager.parseSha256ForAsset(sums, "yubrae-v1.1.0-ios-unsigned.ipa")
        )
        assertNull(AppUpdateManager.parseSha256ForAsset(sums, "missing.apk"))

        assertEquals(
            "https://gh-proxy.com/https://github.com/a/b.apk",
            AppUpdateManager.applyMirrorPrefix("https://github.com/a/b.apk", "https://gh-proxy.com")
        )
        assertEquals(
            "https://github.com/a/b.apk",
            AppUpdateManager.applyMirrorPrefix("https://github.com/a/b.apk", "ftp://invalid.local")
        )
        assertEquals(
            "https://github.com/a/b.apk",
            AppUpdateManager.applyMirrorPrefix("https://github.com/a/b.apk", "http://plain.example.com")
        )
    }

    @Test
    fun checkLatestAndDownloadVerify_succeedsWithMockServerAndRejectsTamperedSha() = runTest {
        val fakeApkPayload = ByteArray(128 * 1024) { (it % 251).toByte() }
        val expectedSha = sha256Hex(fakeApkPayload)
        val shaFetchCount = AtomicInteger(0)

        val server = TestHttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 8)
        val baseUrl = "http://127.0.0.1:${server.address.port}"

        server.createContext("/releases/latest") { ex ->
            val body = """
                {
                  "tag_name": "v1.2.0",
                  "name": "Yubrae v1.2.0",
                  "body": "新增跨机型通用适配与检查更新",
                  "draft": false,
                  "html_url": "$baseUrl/releases/tag/v1.2.0",
                  "assets": [
                    {
                      "name": "yubrae-v1.2.0-release.apk",
                      "size": ${fakeApkPayload.size},
                      "browser_download_url": "$baseUrl/download/yubrae-v1.2.0-release.apk"
                    },
                    {
                      "name": "SHA256SUMS.txt",
                      "size": 120,
                      "browser_download_url": "$baseUrl/download/SHA256SUMS.txt"
                    }
                  ]
                }
            """.trimIndent().toByteArray(Charsets.UTF_8)
            ex.responseHeaders.add("Content-Type", "application/json")
            ex.sendResponseHeaders(200, body.size.toLong())
            ex.responseBody.use { it.write(body) }
        }
        server.createContext("/download/SHA256SUMS.txt") { ex ->
            shaFetchCount.incrementAndGet()
            val text = "$expectedSha  yubrae-v1.2.0-release.apk\n".toByteArray(Charsets.UTF_8)
            ex.sendResponseHeaders(200, text.size.toLong())
            ex.responseBody.use { it.write(text) }
        }
        server.createContext("/download/yubrae-v1.2.0-release.apk") { ex ->
            ex.sendResponseHeaders(200, fakeApkPayload.size.toLong())
            ex.responseBody.use { it.write(fakeApkPayload) }
        }
        server.start()

        try {
            val updatesDir = tempFolder.newFolder("updates")
            val manager = AppUpdateManager(
                updatesDir = updatesDir,
                baseHttpClient = OkHttpClient(),
                latestReleaseApiUrl = "$baseUrl/releases/latest",
                releasePageUrl = "$baseUrl/releases"
            )

            // 1. 检查阶段仅保存 sha256AssetUrl，不阻塞拉取 SHA256SUMS.txt
            val checkRes = manager.checkLatestRelease(
                currentVersionName = "1.0.1",
                buildType = "release",
                mirrorPrefix = ""
            )
            assertTrue(checkRes is UpdateCheckResult.UpdateAvailable)
            val info = (checkRes as UpdateCheckResult.UpdateAvailable).info
            assertEquals("v1.2.0", info.tagName)
            assertNull(info.expectedSha256)
            assertEquals("$baseUrl/download/SHA256SUMS.txt", info.sha256AssetUrl)
            assertEquals(0, shaFetchCount.get())

            // 2. 当前版本已是最新（含 debug 后缀）时返回 UpToDate
            assertTrue(manager.checkLatestRelease("1.2.0", "release") is UpdateCheckResult.UpToDate)
            assertTrue(manager.checkLatestRelease("1.2.0-debug", "debug") is UpdateCheckResult.UpToDate)

            // 3. 镜像不可用时自动降级直连下载并校验成功，且触发 onVerifying
            val progressReports = mutableListOf<Pair<Long, Long>>()
            val verifyingCalls = AtomicInteger(0)
            val downloadRes = manager.downloadAndVerify(
                info = info,
                mirrorPrefix = "http://127.0.0.1:1/dead-mirror/",
                onVerifying = { verifyingCalls.incrementAndGet() }
            ) { d, t ->
                progressReports.add(d to t)
            }
            assertTrue(downloadRes.isSuccess)
            val verified = downloadRes.getOrThrow()
            assertEquals(expectedSha, verified.sha256)
            assertTrue(verified.sha256Verified)
            assertTrue(verified.file.exists())
            assertEquals(1, verifyingCalls.get())
            assertEquals(1, shaFetchCount.get())
            assertTrue(progressReports.isNotEmpty())
            assertEquals(fakeApkPayload.size.toLong(), progressReports.last().first)

            // 4. 哈希被篡改时拒绝并清理全部 .tmp 文件
            val tamperedInfo = info.copy(expectedSha256 = "f".repeat(64))
            val tamperedRes = manager.downloadAndVerify(tamperedInfo)
            assertTrue(tamperedRes.isFailure)
            assertTrue(updatesDir.listFiles().orEmpty().none { it.name.endsWith(".tmp") })
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun downloadAndVerify_fallsBackToDirectWhenMirrorReturnsHttp200HtmlOrCorruptPayload() = runTest {
        val fakeApkPayload = ByteArray(64 * 1024) { ((it * 7) % 251).toByte() }
        val expectedSha = sha256Hex(fakeApkPayload)
        val htmlMirrorHits = AtomicInteger(0)
        val corruptMirrorHits = AtomicInteger(0)
        val directApkHits = AtomicInteger(0)
        val directShaHits = AtomicInteger(0)
        val mirrorShaHits = AtomicInteger(0)

        val server = TestHttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 8)
        val baseUrl = "http://127.0.0.1:${server.address.port}"

        server.createContext("/bad-html-mirror/") { ex ->
            htmlMirrorHits.incrementAndGet()
            val html = "<html><body><h1>Rate Limited</h1></body></html>".toByteArray(Charsets.UTF_8)
            ex.responseHeaders.add("Content-Type", "text/html; charset=utf-8")
            ex.sendResponseHeaders(200, html.size.toLong())
            ex.responseBody.use { it.write(html) }
        }
        server.createContext("/corrupt-mirror/") { ex ->
            if (ex.requestURI.path.endsWith("SHA256SUMS.txt")) {
                mirrorShaHits.incrementAndGet()
                val forged = "0".repeat(64) + "  yubrae-v1.2.0-release.apk\n"
                val bytes = forged.toByteArray(Charsets.UTF_8)
                ex.sendResponseHeaders(200, bytes.size.toLong())
                ex.responseBody.use { it.write(bytes) }
            } else {
                corruptMirrorHits.incrementAndGet()
                val badPayload = "corrupted-binary-payload".toByteArray(Charsets.UTF_8)
                ex.responseHeaders.add("Content-Type", "application/octet-stream")
                ex.sendResponseHeaders(200, badPayload.size.toLong())
                ex.responseBody.use { it.write(badPayload) }
            }
        }
        server.createContext("/download/yubrae-v1.2.0-release.apk") { ex ->
            directApkHits.incrementAndGet()
            ex.responseHeaders.add("Content-Type", "application/vnd.android.package-archive")
            ex.sendResponseHeaders(200, fakeApkPayload.size.toLong())
            ex.responseBody.use { it.write(fakeApkPayload) }
        }
        server.createContext("/download/SHA256SUMS.txt") { ex ->
            directShaHits.incrementAndGet()
            val text = "$expectedSha  yubrae-v1.2.0-release.apk\n".toByteArray(Charsets.UTF_8)
            ex.sendResponseHeaders(200, text.size.toLong())
            ex.responseBody.use { it.write(text) }
        }
        server.start()

        try {
            val updatesDir = tempFolder.newFolder("fallback-updates")
            val manager = AppUpdateManager(
                updatesDir = updatesDir,
                baseHttpClient = OkHttpClient(),
                latestReleaseApiUrl = "$baseUrl/releases/latest",
                releasePageUrl = "$baseUrl/releases"
            )
            val info = AppUpdateInfo(
                tagName = "v1.2.0",
                versionName = "1.2.0",
                releaseTitle = "v1.2.0",
                releaseNotes = "",
                publishedAt = "",
                htmlUrl = "$baseUrl/releases/tag/v1.2.0",
                assetName = "yubrae-v1.2.0-release.apk",
                downloadUrl = "$baseUrl/download/yubrae-v1.2.0-release.apk",
                assetSizeBytes = fakeApkPayload.size.toLong(),
                expectedSha256 = null,
                sha256AssetUrl = "$baseUrl/download/SHA256SUMS.txt"
            )

            // 1. 坏镜像返回 HTTP 200 HTML -> 立即拒绝并降级直连成功
            val verifyingCalls1 = AtomicInteger(0)
            val htmlFallbackRes = manager.downloadAndVerify(
                info = info,
                mirrorPrefix = "$baseUrl/bad-html-mirror/",
                onVerifying = { verifyingCalls1.incrementAndGet() }
            )
            assertTrue(htmlFallbackRes.isSuccess)
            val verified1 = htmlFallbackRes.getOrThrow()
            assertEquals(expectedSha, verified1.sha256)
            assertTrue(verified1.sha256Verified)
            assertEquals(1, htmlMirrorHits.get())
            assertEquals(1, directApkHits.get())
            assertEquals(1, directShaHits.get())
            assertEquals(0, mirrorShaHits.get())
            assertEquals(1, verifyingCalls1.get())
            assertTrue(updatesDir.listFiles().orEmpty().none { it.name.endsWith(".tmp") })

            // 2. 坏镜像返回 HTTP 200 篡改二进制 -> SHA256 直连拉取且校验失败后自动降级直连成功
            val verifyingCalls2 = AtomicInteger(0)
            val corruptFallbackRes = manager.downloadAndVerify(
                info = info,
                mirrorPrefix = "$baseUrl/corrupt-mirror/",
                onVerifying = { verifyingCalls2.incrementAndGet() }
            )
            assertTrue(corruptFallbackRes.isSuccess)
            val verified2 = corruptFallbackRes.getOrThrow()
            assertEquals(expectedSha, verified2.sha256)
            assertTrue(verified2.sha256Verified)
            assertEquals(1, corruptMirrorHits.get())
            assertEquals(2, directApkHits.get())
            assertEquals(0, mirrorShaHits.get())
            assertEquals(2, verifyingCalls2.get())
            assertTrue(updatesDir.listFiles().orEmpty().none { it.name.endsWith(".tmp") })
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun checkLatestRelease_preservesDirectErrorInsteadOfMirrorError() = runTest {
        val server = TestHttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 4)
        val baseUrl = "http://127.0.0.1:${server.address.port}"

        server.createContext("/releases/latest") { ex ->
            val body = """{"message":"API rate limit exceeded"}""".toByteArray(Charsets.UTF_8)
            ex.sendResponseHeaders(503, body.size.toLong())
            ex.responseBody.use { it.write(body) }
        }
        server.createContext("/mirror/") { ex ->
            val html = "<html><body>403 Forbidden by mirror</body></html>".toByteArray(Charsets.UTF_8)
            ex.responseHeaders.add("Content-Type", "text/html")
            ex.sendResponseHeaders(403, html.size.toLong())
            ex.responseBody.use { it.write(html) }
        }
        server.start()

        try {
            val manager = AppUpdateManager(
                updatesDir = tempFolder.newFolder("check-err"),
                baseHttpClient = OkHttpClient(),
                latestReleaseApiUrl = "$baseUrl/releases/latest",
                releasePageUrl = "$baseUrl/releases"
            )
            val result = manager.checkLatestRelease(
                currentVersionName = "1.0.1",
                buildType = "release",
                mirrorPrefix = "$baseUrl/mirror/"
            )
            assertTrue(result is UpdateCheckResult.Failed)
            val reason = (result as UpdateCheckResult.Failed).reason
            assertTrue("Expected direct HTTP 503 error, got: $reason", reason.contains("503"))
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun downloadAndVerify_cancelsBlockingIoQuicklyAndCleansTempFiles() = runBlocking {
        val updatesDir = tempFolder.newFolder("cancel-updates")
        val manager = AppUpdateManager(
            updatesDir = updatesDir,
            baseHttpClient = OkHttpClient()
        )

        val server = TestHttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 4)
        val baseUrl = "http://127.0.0.1:${server.address.port}"
        server.createContext("/slow.apk") { ex ->
            val chunk = ByteArray(16 * 1024) { 1 }
            ex.sendResponseHeaders(200, (chunk.size * 40).toLong())
            runCatching {
                ex.responseBody.use { out ->
                    out.write(chunk)
                    out.flush()
                    // 模拟长时间阻塞 I/O，验证 call.cancel() 能立即中断而非等待 45s 超时
                    Thread.sleep(10_000)
                }
            }
        }
        server.start()

        try {
            val info = AppUpdateInfo(
                tagName = "v1.2.0",
                versionName = "1.2.0",
                releaseTitle = "v1.2.0",
                releaseNotes = "",
                publishedAt = "",
                htmlUrl = baseUrl,
                assetName = "slow.apk",
                downloadUrl = "$baseUrl/slow.apk",
                assetSizeBytes = 640 * 1024L
            )
            var cancelled = false
            val startAt = System.currentTimeMillis()
            val job = launch(Dispatchers.Default) {
                try {
                    manager.downloadAndVerify(info) { _, _ ->
                        // 收到第一段进度后进入阻塞，由外部取消
                    }
                } catch (_: CancellationException) {
                    cancelled = true
                }
            }
            delay(180L)
            job.cancel()
            job.join()
            val elapsed = System.currentTimeMillis() - startAt

            assertTrue("Expected CancellationException to propagate", cancelled)
            assertTrue("Expected fast cancellation (< 3000ms), took ${elapsed}ms", elapsed < 3_000L)
            assertTrue(updatesDir.listFiles().orEmpty().none { it.name.endsWith(".tmp") })
            assertFalse(File(updatesDir, "slow.apk").exists())
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun cleanupOldApks_removesTempAndExpiredApksButPreservesFreshApks() = runTest {
        val updatesDir = tempFolder.newFolder("cleanup-updates")
        val manager = AppUpdateManager(
            updatesDir = updatesDir,
            baseHttpClient = OkHttpClient()
        )
        val now = 1_800_000_000_000L
        val freshApk = File(updatesDir, "fresh.apk").apply {
            writeText("fresh")
            setLastModified(now - 5 * 60 * 1000L)
        }
        val oldApk = File(updatesDir, "old.apk").apply {
            writeText("old")
            setLastModified(now - AppUpdateManager.OLD_APK_MAX_AGE_MILLIS - 1_000L)
        }
        val tempFile = File(updatesDir, "partial.apk.123.tmp").apply {
            writeText("tmp")
            setLastModified(now - 1_000L)
        }

        manager.cleanupOldApks(nowMillis = now)

        assertTrue(freshApk.exists())
        assertFalse(oldApk.exists())
        assertFalse(tempFile.exists())
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }
}
