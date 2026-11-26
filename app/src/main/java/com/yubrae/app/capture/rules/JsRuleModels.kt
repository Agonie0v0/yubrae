package com.yubrae.app.capture.rules

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipInputStream

enum class JsRuleSource(val prefix: String, val labelZh: String) {
    BUILTIN("BUILTIN_JS", "内置基线"),
    CLOUD("CLOUD_JS", "云端同步"),
    IMPORTED("IMPORTED_JS", "本地导入")
}

@Serializable
data class JsRuleMeta(
    val ruleName: String = "",
    val ruleChineseName: String = "",
    val ruleApp: String = "",
    val ruleType: String = "",
    val path: String = ""
)

@Serializable
data class CloudRuleManifest(
    val schemaVersion: Int = 1,
    val version: String = "",
    val updatedAt: String = "",
    val minAppVersion: String = "",
    val rulesUrl: String = "",
    val sha256: String = "",
    val sizeBytes: Long = 0L,
    val ruleCount: Int = 0,
    val smsCount: Int = 0,
    val noticeCount: Int = 0,
    val ocrCount: Int = 0,
    val changelog: String = ""
)

data class CloudRuleStatus(
    val version: String = "未加载",
    val source: JsRuleSource = JsRuleSource.BUILTIN,
    val updatedAt: String = "",
    val totalCount: Int = 0,
    val smsCount: Int = 0,
    val noticeCount: Int = 0,
    val ocrCount: Int = 0,
    val watchedNoticePackages: Set<String> = emptySet(),
    val watchedOcrPackages: Set<String> = emptySet(),
    val rules: List<JsRuleMeta> = emptyList(),
    val changelog: String = "",
    val lastCheckedAtMillis: Long = 0L,
    val lastError: String? = null
)

sealed interface CloudRuleUpdateResult {
    data class Updated(val status: CloudRuleStatus, val previousVersion: String) : CloudRuleUpdateResult
    data class UpToDate(val status: CloudRuleStatus) : CloudRuleUpdateResult
    data class Failed(val reason: String) : CloudRuleUpdateResult
}

data class JsRuleRawResult(
    val type: String,
    val money: Double,
    val fee: Double = 0.0,
    val shopName: String = "",
    val shopItem: String = "",
    val accountNameFrom: String = "",
    val accountNameTo: String = "",
    val currency: String = "CNY",
    val time: Long = 0L,
    val channel: String = "",
    val total: Double = 0.0,
    val matchedRuleName: String = "",
    val matchedRuleChineseName: String = "",
    val ruleType: String = ""
)

data class JsRuleEntry(
    val meta: JsRuleMeta,
    val script: String,
    val bankName: String? = null
)

data class JsRuleBundle(
    val source: JsRuleSource,
    val version: String,
    val updatedAt: String,
    val changelog: String,
    val commonJs: String,
    val categoryJs: String,
    val smsRules: List<JsRuleEntry>,
    val smsRulesByBank: Map<String, List<JsRuleEntry>>,
    val unindexedSmsRules: List<JsRuleEntry>,
    val noticeRulesByPackage: Map<String, List<JsRuleEntry>>,
    val ocrRulesByPackage: Map<String, List<JsRuleEntry>>,
    val allRules: List<JsRuleEntry>
) {
    val watchedNoticePackages: Set<String>
        get() = noticeRulesByPackage.keys

    val watchedOcrPackages: Set<String>
        get() = ocrRulesByPackage.keys

    fun toStatus(
        lastCheckedAtMillis: Long = 0L,
        lastError: String? = null
    ): CloudRuleStatus = CloudRuleStatus(
        version = version.ifBlank { source.labelZh },
        source = source,
        updatedAt = updatedAt,
        totalCount = allRules.size,
        smsCount = smsRules.size,
        noticeCount = noticeRulesByPackage.values.sumOf { it.size },
        ocrCount = ocrRulesByPackage.values.sumOf { it.size },
        watchedNoticePackages = watchedNoticePackages,
        watchedOcrPackages = watchedOcrPackages,
        rules = allRules.map { it.meta },
        changelog = changelog,
        lastCheckedAtMillis = lastCheckedAtMillis,
        lastError = lastError
    )
}

/**
 * 规则包 ZIP 安全校验、解压、过滤与预索引加载器（纯 JVM 可测）。
 */
object JsRuleBundleLoader {
    const val MAX_ZIP_BYTES: Long = 10L * 1024L * 1024L
    const val MAX_UNCOMPRESSED_BYTES: Long = 20L * 1024L * 1024L
    private const val SMS_APP_PACKAGE = "com.android.phone"

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private val BANK_LITERAL_REGEX = Regex("""bankName\s*:\s*["']([^"']+)["']""")
    private val BANK_IDENT_REGEX = Regex("""bankName\s*:\s*([A-Za-z_${'$'}][A-Za-z0-9_${'$'}]*)""")
    private val DRIVE_LETTER_REGEX = Regex("""^[A-Za-z]:""")

    fun loadFromZipBytes(
        bytes: ByteArray,
        source: JsRuleSource,
        version: String = "",
        updatedAt: String = "",
        changelog: String = ""
    ): Result<JsRuleBundle> = runCatching {
        require(bytes.isNotEmpty()) { "规则包文件为空" }
        require(bytes.size.toLong() <= MAX_ZIP_BYTES) {
            "规则包体积超过 10MB 安全限制"
        }

        val entries = extractEntriesSafely(bytes)
        val commonJs = entries["common.js"]?.let(::decodeUtf8)?.takeIf { it.isNotBlank() }
            ?: error("规则包缺少有效的 common.js")
        val rulesJsonRaw = entries["rules.json"]?.let(::decodeUtf8)?.takeIf { it.isNotBlank() }
            ?: error("规则包缺少有效的 rules.json")
        val categoryJs = entries["category.js"]?.let(::decodeUtf8).orEmpty()

        val rawMetaList = json.decodeFromString<List<JsRuleMeta>>(rulesJsonRaw)

        val smsRules = mutableListOf<JsRuleEntry>()
        val smsRulesByBank = LinkedHashMap<String, MutableList<JsRuleEntry>>()
        val unindexedSmsRules = mutableListOf<JsRuleEntry>()
        val noticeRulesByPackage = LinkedHashMap<String, MutableList<JsRuleEntry>>()
        val ocrRulesByPackage = LinkedHashMap<String, MutableList<JsRuleEntry>>()
        val allRules = mutableListOf<JsRuleEntry>()

        for (rawMeta in rawMetaList) {
            val cleanType = rawMeta.ruleType.trim().lowercase()
            val cleanApp = rawMeta.ruleApp.trim()
            val cleanName = rawMeta.ruleName.trim()
            if (cleanName.isEmpty()) continue

            val isSupported = (cleanType == "app" && cleanApp == SMS_APP_PACKAGE) ||
                cleanType == "notice" ||
                cleanType == "ocr"
            if (!isSupported) continue

            val cleanPath = sanitizeRelativeRulePath(rawMeta.path)
                ?: error("规则路径非法: ${rawMeta.path}")
            val scriptBytes = entries[cleanPath] ?: continue
            val scriptText = decodeUtf8(scriptBytes)
            if (scriptText.isBlank()) continue

            val normalizedMeta = rawMeta.copy(
                ruleName = cleanName,
                ruleChineseName = rawMeta.ruleChineseName.trim(),
                ruleApp = cleanApp,
                ruleType = cleanType,
                path = cleanPath
            )

            when (cleanType) {
                "app" -> {
                    val bankName = extractSmsBankName(scriptText)
                    val entry = JsRuleEntry(meta = normalizedMeta, script = scriptText, bankName = bankName)
                    smsRules.add(entry)
                    allRules.add(entry)
                    if (!bankName.isNullOrBlank()) {
                        smsRulesByBank.getOrPut(bankName) { mutableListOf() }.add(entry)
                    } else {
                        unindexedSmsRules.add(entry)
                    }
                }
                "notice" -> {
                    if (cleanApp.isEmpty()) continue
                    val entry = JsRuleEntry(meta = normalizedMeta, script = scriptText)
                    noticeRulesByPackage.getOrPut(cleanApp) { mutableListOf() }.add(entry)
                    allRules.add(entry)
                }
                "ocr" -> {
                    if (cleanApp.isEmpty()) continue
                    val entry = JsRuleEntry(meta = normalizedMeta, script = scriptText)
                    ocrRulesByPackage.getOrPut(cleanApp) { mutableListOf() }.add(entry)
                    allRules.add(entry)
                }
            }
        }

        require(allRules.isNotEmpty()) {
            "规则包中不包含有效规则（需至少 1 条短信、通知或屏幕规则）"
        }

        JsRuleBundle(
            source = source,
            version = version.trim().ifBlank { source.labelZh },
            updatedAt = updatedAt.trim(),
            changelog = changelog.trim(),
            commonJs = commonJs,
            categoryJs = categoryJs,
            smsRules = smsRules,
            smsRulesByBank = smsRulesByBank,
            unindexedSmsRules = unindexedSmsRules,
            noticeRulesByPackage = noticeRulesByPackage,
            ocrRulesByPackage = ocrRulesByPackage,
            allRules = allRules
        )
    }

    fun loadFromZipFile(
        file: File,
        source: JsRuleSource,
        version: String = "",
        updatedAt: String = "",
        changelog: String = ""
    ): Result<JsRuleBundle> = runCatching {
        require(file.exists() && file.isFile) { "规则包文件不存在: ${file.name}" }
        require(file.length() in 1L..MAX_ZIP_BYTES) {
            "规则包体积无效或超过 10MB 安全限制"
        }
        file.readBytes()
    }.mapCatching { bytes ->
        loadFromZipBytes(
            bytes = bytes,
            source = source,
            version = version,
            updatedAt = updatedAt,
            changelog = changelog
        ).getOrThrow()
    }

    internal fun extractSmsBankName(script: String): String? {
        BANK_LITERAL_REGEX.find(script)?.let { match ->
            val literal = match.groupValues.getOrNull(1)?.trim().orEmpty()
            if (literal.isNotEmpty()) return literal
        }
        val identMatch = BANK_IDENT_REGEX.find(script) ?: return null
        val varName = identMatch.groupValues.getOrNull(1)?.trim().orEmpty()
        if (varName.isEmpty()) return null

        val prefix = script.substring(0, identMatch.range.first)
        val escapedVar = Regex.escape(varName)
        val assignRegex = Regex("""(?:^|[\s,;])(?:(?:var|let|const)\s+)?$escapedVar\s*=\s*["']([^"']+)["']""")
        val matched = assignRegex.findAll(prefix).lastOrNull()
            ?: assignRegex.findAll(script).firstOrNull()
        return matched?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun extractEntriesSafely(bytes: ByteArray): Map<String, ByteArray> {
        val entries = LinkedHashMap<String, ByteArray>()
        var totalUncompressed = 0L
        val buffer = ByteArray(8 * 1024)

        ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                try {
                    val cleanKey = sanitizeZipEntryPath(entry.name, entry.isDirectory)
                    if (!entry.isDirectory && cleanKey.isNotEmpty()) {
                        val out = ByteArrayOutputStream()
                        while (true) {
                            val read = zis.read(buffer)
                            if (read < 0) break
                            if (read == 0) continue
                            totalUncompressed += read
                            if (totalUncompressed > MAX_UNCOMPRESSED_BYTES) {
                                throw SecurityException("规则包解压累计体积超过 20MB 安全限制")
                            }
                            out.write(buffer, 0, read)
                        }
                        entries[cleanKey] = out.toByteArray()
                    }
                } finally {
                    zis.closeEntry()
                }
            }
        }
        return entries
    }

    private fun sanitizeZipEntryPath(rawName: String, isDirectory: Boolean): String {
        if (rawName.isBlank() || rawName.contains('\u0000') || rawName.contains('\\')) {
            throw SecurityException("检测到非法压缩包路径: $rawName")
        }
        if (rawName.startsWith("/") || DRIVE_LETTER_REGEX.containsMatchIn(rawName)) {
            throw SecurityException("禁止绝对路径或盘符路径: $rawName")
        }
        val trimmed = if (isDirectory) rawName.trimEnd('/') else rawName
        if (trimmed.isEmpty()) {
            throw SecurityException("检测到空路径条目")
        }
        val segments = trimmed.split('/')
        if (segments.any { it.isEmpty() || it == "." || it == ".." }) {
            throw SecurityException("检测到路径穿越或非法片段: $rawName")
        }
        val virtualBase = File("virtual_rule_root").canonicalFile
        val resolved = File(virtualBase, trimmed).canonicalFile
        val basePrefix = virtualBase.path.trimEnd(File.separatorChar) + File.separatorChar
        if (!resolved.path.startsWith(basePrefix)) {
            throw SecurityException("检测到 Zip Slip 越界路径: $rawName")
        }
        return trimmed.removePrefix("rule/")
    }

    private fun sanitizeRelativeRulePath(rawPath: String): String? {
        val trimmed = rawPath.trim()
        if (trimmed.isEmpty() || trimmed.contains('\u0000') || trimmed.contains('\\')) return null
        if (trimmed.startsWith("/") || DRIVE_LETTER_REGEX.containsMatchIn(trimmed)) return null
        val segments = trimmed.split('/')
        if (segments.any { it.isEmpty() || it == "." || it == ".." }) return null
        return trimmed.removePrefix("rule/")
    }

    private fun decodeUtf8(bytes: ByteArray): String {
        val text = bytes.toString(Charsets.UTF_8)
        return if (text.startsWith('\uFEFF')) text.substring(1) else text
    }
}
