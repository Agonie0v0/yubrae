package com.yubrae.app.capture.rules

import com.yubrae.app.testutil.TestHttpServer
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class CloudRuleRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun checkAndUpdate_verifiesSha256PersistsAndReturnsUpToDate() = runTest {
        val builtinZipBytes = locateProjectFile("app/src/main/assets/rules/builtin_rules.zip").readBytes()
        val builtinManifestText = locateProjectFile("app/src/main/assets/rules/builtin_manifest.json").readText(Charsets.UTF_8)

        val v2ZipBytes = buildTestRuleZipBytes(
            ruleEntries = listOf(
                TestRuleSpec(
                    ruleName = "rule_cloud_1",
                    ruleChineseName = "云端测试通知规则",
                    ruleApp = "com.cloud.test",
                    ruleType = "notice",
                    path = "notice/com.cloud.test/cloud_1.js",
                    script = "var rule_cloud_1 = { get: function(d) { return { type: 'Expend', money: 88.0, shopName: 'CloudShop' }; } };"
                ),
                TestRuleSpec(
                    ruleName = "rule_cloud_2",
                    ruleChineseName = "云端测试OCR规则",
                    ruleApp = "com.cloud.ocr",
                    ruleType = "ocr",
                    path = "ocr/com.cloud.ocr/cloud_2.js",
                    script = "var rule_cloud_2 = { get: function(d) { return { type: 'Expend', money: 12.0, shopName: 'CloudOcr' }; } };"
                )
            )
        )
        val v2Sha = sha256Hex(v2ZipBytes)

        val server = TestHttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 8)
        val baseUrl = "http://127.0.0.1:${server.address.port}"
        server.createContext("/rules/manifest.json") { ex ->
            val manifest = CloudRuleManifest(
                schemaVersion = 1,
                version = "2026.11.27.1",
                updatedAt = "2026-11-27T01:00:00Z",
                minAppVersion = "1.2.2",
                rulesUrl = "$baseUrl/rules/rules.zip",
                sha256 = v2Sha,
                sizeBytes = v2ZipBytes.size.toLong(),
                ruleCount = 2,
                smsCount = 0,
                noticeCount = 1,
                ocrCount = 1,
                changelog = "云端增量规则更新"
            )
            val body = json.encodeToString(manifest).toByteArray(Charsets.UTF_8)
            ex.responseHeaders.add("Content-Type", "application/json")
            ex.sendResponseHeaders(200, body.size.toLong())
            ex.responseBody.use { it.write(body) }
        }
        server.createContext("/rules/rules.zip") { ex ->
            ex.responseHeaders.add("Content-Type", "application/zip")
            ex.sendResponseHeaders(200, v2ZipBytes.size.toLong())
            ex.responseBody.use { it.write(v2ZipBytes) }
        }
        server.start()

        try {
            val storageDir = tempFolder.newFolder("repo-update")
            val repo = CloudRuleRepository(
                storageDir = storageDir,
                okHttpClient = OkHttpClient(),
                builtinZipProvider = { builtinZipBytes },
                builtinManifestProvider = { builtinManifestText },
                defaultManifestUrl = "$baseUrl/rules/manifest.json"
            )
            repo.initialize()
            assertEquals(JsRuleSource.BUILTIN, repo.status.value.source)
            assertEquals(128, repo.status.value.totalCount)

            // 1. 正常热更新
            val updateRes = repo.checkAndUpdate()
            assertTrue(updateRes is CloudRuleUpdateResult.Updated)
            val updated = updateRes as CloudRuleUpdateResult.Updated
            assertEquals("2026.11.26.1", updated.previousVersion)
            assertEquals(JsRuleSource.CLOUD, updated.status.source)
            assertEquals("2026.11.27.1", updated.status.version)
            assertEquals(2, updated.status.totalCount)
            assertEquals(1, updated.status.noticeCount)
            assertEquals(1, updated.status.ocrCount)
            assertNull(updated.status.lastError)

            val matched = repo.ruleEngine.matchNotice("com.cloud.test", "t", "c", 1_773_548_400_000L)
            assertNotNull(matched)
            assertEquals("CloudShop", matched!!.shopName)
            assertTrue(repo.isWatchedOcrPackage("com.cloud.ocr"))

            // 2. 再次检查同版本同哈希 -> UpToDate
            val secondRes = repo.checkAndUpdate()
            assertTrue(secondRes is CloudRuleUpdateResult.UpToDate)

            // 3. 重新实例化仓库后 initialize() 从 active/rules.zip 恢复云端版本
            val reloadedRepo = CloudRuleRepository(
                storageDir = storageDir,
                okHttpClient = OkHttpClient(),
                builtinZipProvider = { builtinZipBytes },
                builtinManifestProvider = { builtinManifestText },
                defaultManifestUrl = "$baseUrl/rules/manifest.json"
            )
            reloadedRepo.initialize()
            assertEquals(JsRuleSource.CLOUD, reloadedRepo.status.value.source)
            assertEquals("2026.11.27.1", reloadedRepo.status.value.version)
            assertEquals(2, reloadedRepo.status.value.totalCount)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun checkAndUpdate_rejectsSha256MismatchWithoutCorruptingActiveRules() = runTest {
        val builtinZipBytes = locateProjectFile("app/src/main/assets/rules/builtin_rules.zip").readBytes()
        val builtinManifestText = locateProjectFile("app/src/main/assets/rules/builtin_manifest.json").readText(Charsets.UTF_8)

        val tamperedZipBytes = buildTestRuleZipBytes(
            ruleEntries = listOf(
                TestRuleSpec(
                    ruleName = "rule_tampered",
                    ruleChineseName = "被篡改规则",
                    ruleApp = "com.tampered.app",
                    ruleType = "notice",
                    path = "notice/com.tampered.app/tampered.js",
                    script = "var rule_tampered = { get: function(d) { return { type: 'Expend', money: 999 }; } };"
                )
            )
        )

        val server = TestHttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 8)
        val baseUrl = "http://127.0.0.1:${server.address.port}"
        server.createContext("/bad-sha/manifest.json") { ex ->
            val manifest = CloudRuleManifest(
                schemaVersion = 1,
                version = "2026.11.28.1",
                updatedAt = "2026-11-28T01:00:00Z",
                minAppVersion = "1.2.2",
                rulesUrl = "$baseUrl/bad-sha/rules.zip",
                sha256 = "f".repeat(64),
                sizeBytes = tamperedZipBytes.size.toLong(),
                ruleCount = 1,
                noticeCount = 1,
                changelog = "bad sha"
            )
            val body = json.encodeToString(manifest).toByteArray(Charsets.UTF_8)
            ex.responseHeaders.add("Content-Type", "application/json")
            ex.sendResponseHeaders(200, body.size.toLong())
            ex.responseBody.use { it.write(body) }
        }
        server.createContext("/bad-sha/rules.zip") { ex ->
            ex.responseHeaders.add("Content-Type", "application/zip")
            ex.sendResponseHeaders(200, tamperedZipBytes.size.toLong())
            ex.responseBody.use { it.write(tamperedZipBytes) }
        }
        server.start()

        try {
            val storageDir = tempFolder.newFolder("repo-bad-sha")
            val repo = CloudRuleRepository(
                storageDir = storageDir,
                okHttpClient = OkHttpClient(),
                builtinZipProvider = { builtinZipBytes },
                builtinManifestProvider = { builtinManifestText }
            )
            repo.initialize()
            val beforeBundle = repo.ruleEngine.currentBundle()

            val res = repo.checkAndUpdate(manifestUrl = "$baseUrl/bad-sha/manifest.json")
            assertTrue(res is CloudRuleUpdateResult.Failed)
            val failed = res as CloudRuleUpdateResult.Failed
            assertTrue(failed.reason.contains("SHA-256"))

            // 原有激活规则未被破坏
            assertEquals(JsRuleSource.BUILTIN, repo.status.value.source)
            assertEquals("2026.11.26.1", repo.status.value.version)
            assertEquals(128, repo.status.value.totalCount)
            assertEquals(beforeBundle, repo.ruleEngine.currentBundle())
            assertNotNull(repo.status.value.lastError)
            assertNull(repo.ruleEngine.matchNotice("com.tampered.app", "t", "c", 1_773_548_400_000L))
            assertTrue(storageDir.listFiles().orEmpty().none { it.name.endsWith(".tmp") })
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun checkAndUpdate_prefersMirrorAndFallsBackToDirectWhenMirrorReturnsHtml() = runTest {
        val builtinZipBytes = locateProjectFile("app/src/main/assets/rules/builtin_rules.zip").readBytes()
        val mirrorZipBytes = buildTestRuleZipBytes(
            ruleEntries = listOf(
                TestRuleSpec(
                    ruleName = "rule_mirror_v1",
                    ruleChineseName = "镜像规则",
                    ruleApp = "com.mirror.app",
                    ruleType = "notice",
                    path = "notice/com.mirror.app/mirror.js",
                    script = "var rule_mirror_v1 = { get: function(d) { return { type: 'Expend', money: 15.0, shopName: 'MirrorOk' }; } };"
                )
            )
        )
        val mirrorZipSha = sha256Hex(mirrorZipBytes)

        val badMirrorHits = AtomicInteger(0)
        val directManifestHits = AtomicInteger(0)
        val goodMirrorHits = AtomicInteger(0)
        val deadDirectHits = AtomicInteger(0)

        val server = TestHttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 8)
        val baseUrl = "http://127.0.0.1:${server.address.port}"

        server.createContext("/bad-html-mirror/") { ex ->
            badMirrorHits.incrementAndGet()
            val html = "<html><body>Mirror Rate Limited</body></html>".toByteArray(Charsets.UTF_8)
            ex.responseHeaders.add("Content-Type", "text/html")
            ex.sendResponseHeaders(200, html.size.toLong())
            ex.responseBody.use { it.write(html) }
        }
        server.createContext("/direct/manifest.json") { ex ->
            directManifestHits.incrementAndGet()
            val manifest = CloudRuleManifest(
                schemaVersion = 1,
                version = "2026.11.27.2",
                updatedAt = "2026-11-27T02:00:00Z",
                minAppVersion = "1.2.2",
                rulesUrl = "$baseUrl/direct/rules.zip",
                sha256 = mirrorZipSha,
                sizeBytes = mirrorZipBytes.size.toLong(),
                ruleCount = 1,
                noticeCount = 1
            )
            val body = json.encodeToString(manifest).toByteArray(Charsets.UTF_8)
            ex.responseHeaders.add("Content-Type", "application/json")
            ex.sendResponseHeaders(200, body.size.toLong())
            ex.responseBody.use { it.write(body) }
        }
        server.createContext("/direct/rules.zip") { ex ->
            ex.responseHeaders.add("Content-Type", "application/zip")
            ex.sendResponseHeaders(200, mirrorZipBytes.size.toLong())
            ex.responseBody.use { it.write(mirrorZipBytes) }
        }

        server.createContext("/good-mirror/") { ex ->
            goodMirrorHits.incrementAndGet()
            if (ex.requestURI.path.endsWith("manifest.json")) {
                val manifest = CloudRuleManifest(
                    schemaVersion = 1,
                    version = "2026.11.27.3",
                    updatedAt = "2026-11-27T03:00:00Z",
                    minAppVersion = "1.2.2",
                    rulesUrl = "$baseUrl/unreachable-direct/rules.zip",
                    sha256 = mirrorZipSha,
                    sizeBytes = mirrorZipBytes.size.toLong(),
                    ruleCount = 1,
                    noticeCount = 1
                )
                val body = json.encodeToString(manifest).toByteArray(Charsets.UTF_8)
                ex.responseHeaders.add("Content-Type", "application/json")
                ex.sendResponseHeaders(200, body.size.toLong())
                ex.responseBody.use { it.write(body) }
            } else {
                ex.responseHeaders.add("Content-Type", "application/zip")
                ex.sendResponseHeaders(200, mirrorZipBytes.size.toLong())
                ex.responseBody.use { it.write(mirrorZipBytes) }
            }
        }
        server.createContext("/unreachable-direct/") { ex ->
            deadDirectHits.incrementAndGet()
            ex.sendResponseHeaders(503, -1)
        }
        server.start()

        try {
            val storageDir = tempFolder.newFolder("repo-mirror")
            val repo = CloudRuleRepository(
                storageDir = storageDir,
                okHttpClient = OkHttpClient(),
                builtinZipProvider = { builtinZipBytes }
            )
            repo.initialize()

            // 1. 坏镜像返回 HTML -> 拒绝并自动回退直连成功
            val fallbackRes = repo.checkAndUpdate(
                manifestUrl = "$baseUrl/direct/manifest.json",
                mirrorPrefix = "$baseUrl/bad-html-mirror/"
            )
            assertTrue(fallbackRes is CloudRuleUpdateResult.Updated)
            assertEquals("2026.11.27.2", repo.status.value.version)
            assertEquals(2, badMirrorHits.get()) // manifest + zip 都先尝试镜像
            assertEquals(1, directManifestHits.get())

            // 2. 好镜像优先命中，直连不可用时仍通过镜像更新成功
            val mirrorFirstRes = repo.checkAndUpdate(
                manifestUrl = "$baseUrl/unreachable-direct/manifest.json",
                mirrorPrefix = "$baseUrl/good-mirror/"
            )
            assertTrue(mirrorFirstRes is CloudRuleUpdateResult.Updated)
            assertEquals("2026.11.27.3", repo.status.value.version)
            assertEquals(2, goodMirrorHits.get())
            assertEquals(0, deadDirectHits.get())
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun importLocalZip_filtersXposedAppRulesRejectsZipSlipAndResetsToBuiltin() = runTest {
        val realZipBytes = locateProjectFile("app/src/main/assets/rules/builtin_rules.zip").readBytes()
        val realEntries = readZipEntries(realZipBytes)

        // 构造包含 app/com.tencent.mm 与 app/com.eg.android.AlipayGphone Xposed 规则的原始格式 zip
        val rawFormatZip = buildRawAutoAccountingZipWithXposed(
            commonJs = realEntries["rule/common.js"] ?: error("missing common.js"),
            categoryJs = realEntries["rule/category.js"] ?: error("missing category.js"),
            smsScript = realEntries["rule/app/com.android.phone/短信招商银行.js"] ?: error("missing sms"),
            noticeScript = realEntries["rule/notice/cmb.pb/招商银行消费通知.js"] ?: error("missing notice"),
            ocrScript = realEntries["rule/ocr/com.eg.android.AlipayGphone/支付宝支付成功.js"] ?: error("missing ocr")
        )

        val storageDir = tempFolder.newFolder("repo-import-reset")
        val repo = CloudRuleRepository(
            storageDir = storageDir,
            okHttpClient = OkHttpClient(),
            builtinZipProvider = { realZipBytes },
            builtinManifestProvider = {
                locateProjectFile("app/src/main/assets/rules/builtin_manifest.json").readText(Charsets.UTF_8)
            }
        )
        repo.initialize()
        assertEquals(128, repo.status.value.totalCount)

        // 1. 导入原始格式 zip：应自动过滤 app/com.tencent.mm 与 app/com.eg.android.AlipayGphone
        val importRes = repo.importLocalZip(rawFormatZip, "raw-autoaccounting.zip")
        assertTrue(importRes.isSuccess)
        val importedStatus = importRes.getOrThrow()
        assertEquals(JsRuleSource.IMPORTED, importedStatus.source)
        assertEquals(3, importedStatus.totalCount)
        assertEquals(1, importedStatus.smsCount)
        assertEquals(1, importedStatus.noticeCount)
        assertEquals(1, importedStatus.ocrCount)
        assertTrue(
            importedStatus.rules.none {
                it.ruleType == "app" &&
                    (it.ruleApp == "com.tencent.mm" || it.ruleApp == "com.eg.android.AlipayGphone")
            }
        )

        // 2. Zip Slip 攻击路径必须被拦截，且不破坏当前激活规则
        val zipSlipBytes = ByteArrayOutputStream().use { baos ->
            ZipOutputStream(baos).use { zos ->
                zos.putNextEntry(ZipEntry("../../evil.js"))
                zos.write("alert(1)".toByteArray())
                zos.closeEntry()
                zos.putNextEntry(ZipEntry("rule/common.js"))
                zos.write("var common = {};".toByteArray())
                zos.closeEntry()
            }
            baos.toByteArray()
        }
        val slipRes = repo.importLocalZip(zipSlipBytes, "evil-slip.zip")
        assertTrue(slipRes.isFailure)
        assertEquals(JsRuleSource.IMPORTED, repo.status.value.source)
        assertEquals(3, repo.status.value.totalCount)

        // 同时验证反斜杠越界与绝对路径均被 JsRuleBundleLoader 拒绝
        val backslashSlip = buildSingleEntryZip("..\\evil.js", "bad")
        assertTrue(JsRuleBundleLoader.loadFromZipBytes(backslashSlip, JsRuleSource.IMPORTED).isFailure)
        val absolutePathZip = buildSingleEntryZip("/etc/passwd", "bad")
        assertTrue(JsRuleBundleLoader.loadFromZipBytes(absolutePathZip, JsRuleSource.IMPORTED).isFailure)

        // 3. resetToBuiltin() 正常回退到内置 128 条规则并清理 active 存储
        val resetRes = repo.resetToBuiltin()
        assertTrue(resetRes.isSuccess)
        val resetStatus = resetRes.getOrThrow()
        assertEquals(JsRuleSource.BUILTIN, resetStatus.source)
        assertEquals("2026.11.26.1", resetStatus.version)
        assertEquals(128, resetStatus.totalCount)

        val freshRepo = CloudRuleRepository(
            storageDir = storageDir,
            okHttpClient = OkHttpClient(),
            builtinZipProvider = { realZipBytes },
            builtinManifestProvider = {
                locateProjectFile("app/src/main/assets/rules/builtin_manifest.json").readText(Charsets.UTF_8)
            }
        )
        freshRepo.initialize()
        assertEquals(JsRuleSource.BUILTIN, freshRepo.status.value.source)
        assertEquals(128, freshRepo.status.value.totalCount)
    }

    private data class TestRuleSpec(
        val ruleName: String,
        val ruleChineseName: String,
        val ruleApp: String,
        val ruleType: String,
        val path: String,
        val script: String
    )

    private fun buildTestRuleZipBytes(ruleEntries: List<TestRuleSpec>): ByteArray {
        val metas = ruleEntries.map {
            JsRuleMeta(
                ruleName = it.ruleName,
                ruleChineseName = it.ruleChineseName,
                ruleApp = it.ruleApp,
                ruleType = it.ruleType,
                path = it.path
            )
        }
        return ByteArrayOutputStream().use { baos ->
            ZipOutputStream(baos).use { zos ->
                zos.putNextEntry(ZipEntry("rule/common.js"))
                zos.write("var common = {};".toByteArray(Charsets.UTF_8))
                zos.closeEntry()

                zos.putNextEntry(ZipEntry("rule/category.js"))
                zos.write("var category = { get: function() { return '其他'; } };".toByteArray(Charsets.UTF_8))
                zos.closeEntry()

                zos.putNextEntry(ZipEntry("rule/rules.json"))
                zos.write(json.encodeToString(metas).toByteArray(Charsets.UTF_8))
                zos.closeEntry()

                for (entry in ruleEntries) {
                    zos.putNextEntry(ZipEntry("rule/${entry.path}"))
                    zos.write(entry.script.toByteArray(Charsets.UTF_8))
                    zos.closeEntry()
                }
            }
            baos.toByteArray()
        }
    }

    private fun buildRawAutoAccountingZipWithXposed(
        commonJs: ByteArray,
        categoryJs: ByteArray,
        smsScript: ByteArray,
        noticeScript: ByteArray,
        ocrScript: ByteArray
    ): ByteArray {
        val metas = listOf(
            JsRuleMeta("rule_xposed_wx", "微信Xposed记账", "com.tencent.mm", "app", "app/com.tencent.mm/微信记账.js"),
            JsRuleMeta("rule_xposed_ali", "支付宝Xposed记账", "com.eg.android.AlipayGphone", "app", "app/com.eg.android.AlipayGphone/支付宝记账.js"),
            JsRuleMeta("rule_47", "短信招商银行", "com.android.phone", "app", "app/com.android.phone/短信招商银行.js"),
            JsRuleMeta("rule_6", "招商银行消费通知", "cmb.pb", "notice", "notice/cmb.pb/招商银行消费通知.js"),
            JsRuleMeta("rule_124", "支付宝支付成功", "com.eg.android.AlipayGphone", "ocr", "ocr/com.eg.android.AlipayGphone/支付宝支付成功.js")
        )
        return ByteArrayOutputStream().use { baos ->
            ZipOutputStream(baos).use { zos ->
                zos.putNextEntry(ZipEntry("rule/common.js"))
                zos.write(commonJs)
                zos.closeEntry()

                zos.putNextEntry(ZipEntry("rule/category.js"))
                zos.write(categoryJs)
                zos.closeEntry()

                zos.putNextEntry(ZipEntry("rule/rules.json"))
                zos.write(json.encodeToString(metas).toByteArray(Charsets.UTF_8))
                zos.closeEntry()

                zos.putNextEntry(ZipEntry("rule/app/com.tencent.mm/微信记账.js"))
                zos.write("var rule_xposed_wx = { get: function() { return null; } };".toByteArray(Charsets.UTF_8))
                zos.closeEntry()

                zos.putNextEntry(ZipEntry("rule/app/com.eg.android.AlipayGphone/支付宝记账.js"))
                zos.write("var rule_xposed_ali = { get: function() { return null; } };".toByteArray(Charsets.UTF_8))
                zos.closeEntry()

                zos.putNextEntry(ZipEntry("rule/app/com.android.phone/短信招商银行.js"))
                zos.write(smsScript)
                zos.closeEntry()

                zos.putNextEntry(ZipEntry("rule/notice/cmb.pb/招商银行消费通知.js"))
                zos.write(noticeScript)
                zos.closeEntry()

                zos.putNextEntry(ZipEntry("rule/ocr/com.eg.android.AlipayGphone/支付宝支付成功.js"))
                zos.write(ocrScript)
                zos.closeEntry()
            }
            baos.toByteArray()
        }
    }

    private fun buildSingleEntryZip(entryName: String, content: String): ByteArray =
        ByteArrayOutputStream().use { baos ->
            ZipOutputStream(baos).use { zos ->
                zos.putNextEntry(ZipEntry(entryName))
                zos.write(content.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
            baos.toByteArray()
        }

    private fun readZipEntries(zipBytes: ByteArray): Map<String, ByteArray> {
        val map = LinkedHashMap<String, ByteArray>()
        ZipInputStream(zipBytes.inputStream()).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                if (!entry.isDirectory) {
                    map[entry.name] = zis.readBytes()
                }
                zis.closeEntry()
            }
        }
        return map
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun locateProjectFile(relativeFromRoot: String): File {
        val candidates = listOf(
            File(relativeFromRoot),
            File("..", relativeFromRoot),
            File("../..", relativeFromRoot)
        )
        return candidates.firstOrNull { it.exists() && it.isFile }
            ?: error("Cannot locate project test fixture file: $relativeFromRoot (user.dir=${System.getProperty("user.dir")})")
    }
}
