package com.yubrae.app.importer

import jxl.Cell
import jxl.CellType
import jxl.DateCell
import jxl.NumberCell
import jxl.Workbook
import jxl.WorkbookSettings
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.StringReader
import java.math.BigDecimal
import java.nio.charset.Charset
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.zip.ZipInputStream
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.floor
import kotlin.math.roundToLong

internal data class ParsedImportSource(
    val sourceKind: String,
    val rows: List<List<String>>,
    val attachments: Map<String, ByteArray> = emptyMap()
)

/**
 * 统一多格式账单文件解析器：
 * - `.xlsx`（OOXML ZIP + XXE-safe DOM XML 解析）
 * - `.xls`（OLE2 BIFF8 via JXL）
 * - `.zip`（提取内嵌图片附件并递归解析主账单文件）
 * - `.csv` / `.tsv` / 文本表格（委托 Rfc4180CsvParser）
 */
internal object SpreadsheetImportParser {

    private const val MAX_ZIP_DEPTH = 2
    private const val MAX_ZIP_ENTRIES = 2000
    private const val MAX_ENTRY_BYTES = 32 * 1024 * 1024
    private const val MAX_TOTAL_ZIP_BYTES = 64 * 1024 * 1024
    private const val MAX_COLUMNS = 512

    private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "heic")
    private val BILL_EXTENSIONS = setOf("xlsx", "xls", "csv", "tsv")
    private val BUILTIN_DATE_NUM_FMT_IDS: Set<Int> =
        (14..22).toSet() + (27..36).toSet() + (45..47).toSet() + (50..58).toSet()

    private val DATE_ONLY_FMT: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ROOT)
    private val DATE_TIME_FMT: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT)

    private val DATE_HEADER_CANDIDATES = setOf(
        "日期", "交易时间", "记账时间", "付款时间", "交易创建时间", "创建时间", "时间"
    )
    private val AMOUNT_HEADER_CANDIDATES = setOf(
        "金额", "金额(元)", "交易金额", "金额(原币种)"
    )

    private data class StylesInfo(
        val xfNumFmtIds: List<Int>,
        val customFormats: Map<Int, String>
    ) {
        fun isDateStyle(styleIndex: Int): Boolean {
            if (styleIndex < 0 || styleIndex >= xfNumFmtIds.size) return false
            val numFmtId = xfNumFmtIds[styleIndex]
            if (numFmtId in BUILTIN_DATE_NUM_FMT_IDS) return true
            val formatCode = customFormats[numFmtId] ?: return false
            return isDateFormatCode(formatCode)
        }
    }

    fun parse(fileName: String, rawBytes: ByteArray): ParsedImportSource {
        if (rawBytes.isEmpty()) {
            throw IllegalArgumentException("导入文件内容为空")
        }
        return parseInternal(fileName, rawBytes, depth = 0)
    }

    private fun parseInternal(
        fileName: String,
        rawBytes: ByteArray,
        depth: Int
    ): ParsedImportSource {
        if (rawBytes.isEmpty()) {
            throw IllegalArgumentException("导入文件内容为空")
        }

        // 1. Magic-byte detection takes precedence over file extension
        if (isZipMagic(rawBytes)) {
            return parseZipOrXlsx(rawBytes, depth)
        }
        if (isOle2Magic(rawBytes)) {
            return parseXls(rawBytes)
        }

        // 2. Validate extension if binary magic wasn't matched
        val ext = fileExtension(fileName)
        when (ext) {
            "xlsx", "zip" -> throw IllegalArgumentException("文件不是有效的 ${ext.uppercase(Locale.ROOT)} 压缩格式，请确认文件未损坏")
            "xls" -> throw IllegalArgumentException("文件不是有效的 Excel (.xls) 二进制格式，请确认文件未损坏")
        }

        // 3. Fallback to delimited text (.csv / .tsv / .txt)
        val decoded = Rfc4180CsvParser.decodeAndParse(rawBytes, fileNameHint = fileName)
        val kind = if (decoded.delimiter == '\t' || ext == "tsv") "TSV" else "CSV"
        return ParsedImportSource(
            sourceKind = kind,
            rows = decoded.rows,
            attachments = emptyMap()
        )
    }

    private fun isZipMagic(bytes: ByteArray): Boolean {
        if (bytes.size < 4) return false
        if (bytes[0] != 0x50.toByte() || bytes[1] != 0x4B.toByte()) return false
        val b2 = bytes[2]
        val b3 = bytes[3]
        return (b2 == 0x03.toByte() && b3 == 0x04.toByte()) ||
            (b2 == 0x05.toByte() && b3 == 0x06.toByte())
    }

    private fun isOle2Magic(bytes: ByteArray): Boolean {
        if (bytes.size < 8) return false
        return bytes[0] == 0xD0.toByte() &&
            bytes[1] == 0xCF.toByte() &&
            bytes[2] == 0x11.toByte() &&
            bytes[3] == 0xE0.toByte() &&
            bytes[4] == 0xA1.toByte() &&
            bytes[5] == 0xB1.toByte() &&
            bytes[6] == 0x1A.toByte() &&
            bytes[7] == 0xE1.toByte()
    }

    // -------------------------------------------------------------------------
    // ZIP & OOXML (.xlsx)
    // -------------------------------------------------------------------------

    private fun parseZipOrXlsx(rawBytes: ByteArray, depth: Int): ParsedImportSource {
        val entries = readZipEntries(rawBytes)
        if (entries.isEmpty()) {
            throw IllegalArgumentException("压缩包内容为空")
        }

        val entriesLower = LinkedHashMap<String, ByteArray>(entries.size)
        for ((path, content) in entries) {
            entriesLower[path.lowercase(Locale.ROOT)] = content
        }

        val isXlsx = entriesLower.containsKey("xl/workbook.xml") ||
            entriesLower.keys.any { it.matches(Regex("^xl/worksheets/sheet\\d+\\.xml$")) }

        if (isXlsx) {
            return parseXlsxFromEntries(entriesLower)
        }

        if (depth >= MAX_ZIP_DEPTH) {
            throw IllegalArgumentException("压缩包嵌套层级过深，无法继续解析")
        }

        val attachments = LinkedHashMap<String, ByteArray>()
        var primaryBill: Pair<String, ByteArray>? = null

        for ((path, bytes) in entries) {
            if (isIgnorableArchiveEntry(path)) continue
            val lowerPath = path.lowercase(Locale.ROOT)
            val ext = fileExtension(lowerPath)
            if (ext in IMAGE_EXTENSIONS) {
                attachments[lowerPath] = bytes
                val pureName = lowerPath.substringAfterLast('/')
                if (pureName.isNotEmpty()) {
                    attachments.putIfAbsent(pureName, bytes)
                }
            } else if (primaryBill == null && ext in BILL_EXTENSIONS) {
                primaryBill = path to bytes
            }
        }

        val (billPath, billBytes) = primaryBill
            ?: throw IllegalArgumentException("压缩包内未找到支持的账单文件（.xlsx / .xls / .csv / .tsv）")

        val inner = parseInternal(billPath, billBytes, depth + 1)
        val mergedAttachments = LinkedHashMap<String, ByteArray>(attachments.size + inner.attachments.size)
        mergedAttachments.putAll(attachments)
        mergedAttachments.putAll(inner.attachments)

        return ParsedImportSource(
            sourceKind = "${inner.sourceKind} (压缩包)",
            rows = inner.rows,
            attachments = mergedAttachments
        )
    }

    private fun readZipEntries(rawBytes: ByteArray): LinkedHashMap<String, ByteArray> {
        return try {
            readZipEntriesWithCharset(rawBytes, Charsets.UTF_8)
        } catch (e: IllegalArgumentException) {
            // Fallback for Windows Chinese archives whose entry names are GB18030/GBK without UTF-8 flag
            val fallbackCharset = when {
                Charset.isSupported("GB18030") -> Charset.forName("GB18030")
                Charset.isSupported("GBK") -> Charset.forName("GBK")
                else -> throw e
            }
            readZipEntriesWithCharset(rawBytes, fallbackCharset)
        }
    }

    private fun readZipEntriesWithCharset(
        rawBytes: ByteArray,
        charset: Charset
    ): LinkedHashMap<String, ByteArray> {
        val result = LinkedHashMap<String, ByteArray>()
        var totalBytes = 0
        var entryCount = 0

        ZipInputStream(ByteArrayInputStream(rawBytes), charset).use { zis ->
            val buffer = ByteArray(8192)
            while (true) {
                val entry = zis.nextEntry ?: break
                entryCount++
                if (entryCount > MAX_ZIP_ENTRIES) {
                    throw IllegalArgumentException("压缩包内文件数量超过安全上限（$MAX_ZIP_ENTRIES）")
                }

                val normalized = normalizeZipPath(entry.name)
                if (entry.isDirectory || normalized.isEmpty()) {
                    zis.closeEntry()
                    continue
                }

                val baos = ByteArrayOutputStream()
                var entryBytes = 0
                while (true) {
                    val read = zis.read(buffer)
                    if (read <= 0) break
                    entryBytes += read
                    totalBytes += read
                    if (entryBytes > MAX_ENTRY_BYTES || totalBytes > MAX_TOTAL_ZIP_BYTES) {
                        throw IllegalArgumentException("压缩包解压体积超过安全限制")
                    }
                    baos.write(buffer, 0, read)
                }
                zis.closeEntry()
                result[normalized] = baos.toByteArray()
            }
        }
        return result
    }

    private fun normalizeZipPath(rawName: String): String {
        if (rawName.indexOf('\u0000') >= 0) {
            throw IllegalArgumentException("压缩包包含非法空字节路径")
        }
        val unified = rawName.replace('\\', '/').trim()
        if (unified.startsWith("/") || unified.matches(Regex("^[A-Za-z]:.*"))) {
            throw IllegalArgumentException("压缩包包含非法绝对路径: $rawName")
        }
        val segments = unified.split('/').filter { it.isNotEmpty() && it != "." }
        if (segments.any { it == ".." }) {
            throw IllegalArgumentException("压缩包包含不安全穿越路径: $rawName")
        }
        return segments.joinToString("/")
    }

    private fun isIgnorableArchiveEntry(path: String): Boolean {
        val lower = path.lowercase(Locale.ROOT)
        if (lower.startsWith("__macosx/") || lower.contains("/__macosx/")) return true
        val fileName = lower.substringAfterLast('/')
        return fileName.startsWith("._")
    }

    private fun parseXlsxFromEntries(entriesLower: Map<String, ByteArray>): ParsedImportSource {
        val builder = newSecureDocumentBuilder()

        val sharedStrings = entriesLower["xl/sharedstrings.xml"]?.let { bytes ->
            parseSharedStrings(builder, bytes)
        } ?: emptyList()

        val styles = entriesLower["xl/styles.xml"]?.let { bytes ->
            parseStyles(builder, bytes)
        } ?: StylesInfo(emptyList(), emptyMap())

        val sheetPaths = resolveOrderedSheetPaths(builder, entriesLower)
        if (sheetPaths.isEmpty()) {
            throw IllegalArgumentException("XLSX 文件中未找到任何工作表")
        }

        val parsedSheets = sheetPaths.mapNotNull { path ->
            val bytes = entriesLower[path] ?: return@mapNotNull null
            parseWorksheetRows(builder, bytes, sharedStrings, styles)
        }

        val selectedRows = selectBestSheet(parsedSheets)
            ?: throw IllegalArgumentException("XLSX 工作表内容为空")

        return ParsedImportSource(
            sourceKind = "XLSX",
            rows = selectedRows,
            attachments = emptyMap()
        )
    }

    private fun newSecureDocumentBuilder(): DocumentBuilder {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        runCatching { factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }
        runCatching { factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        runCatching { factory.setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        runCatching { factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
        runCatching { factory.isXIncludeAware = false }
        runCatching { factory.isExpandEntityReferences = false }
        val builder = factory.newDocumentBuilder()
        builder.setEntityResolver { _, _ -> InputSource(StringReader("")) }
        return builder
    }

    private fun parseXml(builder: DocumentBuilder, bytes: ByteArray): Document {
        return builder.parse(ByteArrayInputStream(bytes))
    }

    private fun parseSharedStrings(builder: DocumentBuilder, bytes: ByteArray): List<String> {
        val doc = parseXml(builder, bytes)
        val root = doc.documentElement ?: return emptyList()
        val result = ArrayList<String>()
        var node = root.firstChild
        while (node != null) {
            if (node is Element && localNameOf(node) == "si") {
                result.add(extractSiText(node))
            }
            node = node.nextSibling
        }
        return result
    }

    private fun extractSiText(container: Element): String {
        val sb = StringBuilder()
        var child = container.firstChild
        while (child != null) {
            if (child is Element) {
                when (localNameOf(child)) {
                    "t" -> sb.append(child.textContent.orEmpty())
                    "r" -> {
                        var runChild = child.firstChild
                        while (runChild != null) {
                            if (runChild is Element && localNameOf(runChild) == "t") {
                                sb.append(runChild.textContent.orEmpty())
                            }
                            runChild = runChild.nextSibling
                        }
                    }
                    // Skip <rPh> phonetic ruby text to prevent duplicate text
                }
            }
            child = child.nextSibling
        }
        return sb.toString()
    }

    private fun parseStyles(builder: DocumentBuilder, bytes: ByteArray): StylesInfo {
        val doc = parseXml(builder, bytes)
        val root = doc.documentElement ?: return StylesInfo(emptyList(), emptyMap())
        val customFormats = HashMap<Int, String>()
        val xfNumFmtIds = ArrayList<Int>()

        var section = root.firstChild
        while (section != null) {
            if (section is Element) {
                when (localNameOf(section)) {
                    "numFmts" -> {
                        var fmtNode = section.firstChild
                        while (fmtNode != null) {
                            if (fmtNode is Element && localNameOf(fmtNode) == "numFmt") {
                                val id = fmtNode.getAttribute("numFmtId").toIntOrNull()
                                val code = fmtNode.getAttribute("formatCode")
                                if (id != null && code.isNotBlank()) {
                                    customFormats[id] = code
                                }
                            }
                            fmtNode = fmtNode.nextSibling
                        }
                    }
                    "cellXfs" -> {
                        var xfNode = section.firstChild
                        while (xfNode != null) {
                            if (xfNode is Element && localNameOf(xfNode) == "xf") {
                                val numFmtId = xfNode.getAttribute("numFmtId").toIntOrNull() ?: 0
                                xfNumFmtIds.add(numFmtId)
                            }
                            xfNode = xfNode.nextSibling
                        }
                    }
                }
            }
            section = section.nextSibling
        }
        return StylesInfo(xfNumFmtIds, customFormats)
    }

    private fun resolveOrderedSheetPaths(
        builder: DocumentBuilder,
        entriesLower: Map<String, ByteArray>
    ): List<String> {
        val relTargets = HashMap<String, String>()
        entriesLower["xl/_rels/workbook.xml.rels"]?.let { relsBytes ->
            runCatching {
                val relDoc = parseXml(builder, relsBytes)
                val relRoot = relDoc.documentElement
                var node = relRoot?.firstChild
                while (node != null) {
                    if (node is Element && localNameOf(node) == "Relationship") {
                        val id = node.getAttribute("Id")
                        val target = node.getAttribute("Target")
                        if (id.isNotBlank() && target.isNotBlank()) {
                            relTargets[id] = resolveXlTargetPath(target)
                        }
                    }
                    node = node.nextSibling
                }
            }
        }

        val ordered = ArrayList<String>()
        entriesLower["xl/workbook.xml"]?.let { wbBytes ->
            runCatching {
                val wbDoc = parseXml(builder, wbBytes)
                val sheetsNodes = wbDoc.getElementsByTagNameNS("*", "sheet")
                val count = sheetsNodes.length
                for (i in 0 until count) {
                    val sheetElem = sheetsNodes.item(i) as? Element ?: continue
                    val relId = sheetElem.getAttributeNS(
                        "http://schemas.openxmlformats.org/officeDocument/2006/relationships",
                        "id"
                    ).ifBlank { sheetElem.getAttribute("r:id") }
                    val resolved = relTargets[relId]?.lowercase(Locale.ROOT)
                    if (resolved != null && resolved in entriesLower && resolved !in ordered) {
                        ordered.add(resolved)
                    }
                }
            }
        }

        if (ordered.isNotEmpty()) return ordered

        // Fallback: sort xl/worksheets/sheetN.xml numerically
        val sheetRegex = Regex("^xl/worksheets/sheet(\\d+)\\.xml$")
        return entriesLower.keys
            .mapNotNull { key ->
                val m = sheetRegex.matchEntire(key)
                if (m != null) (m.groupValues[1].toIntOrNull() ?: Int.MAX_VALUE) to key else null
            }
            .sortedBy { it.first }
            .map { it.second }
    }

    private fun resolveXlTargetPath(rawTarget: String): String {
        val unified = rawTarget.replace('\\', '/').trim()
        if (unified.startsWith("/")) {
            return unified.trimStart('/')
        }
        val baseSegments = mutableListOf("xl")
        for (seg in unified.split('/')) {
            when (seg) {
                "", "." -> Unit
                ".." -> if (baseSegments.isNotEmpty()) baseSegments.removeAt(baseSegments.lastIndex)
                else -> baseSegments.add(seg)
            }
        }
        return baseSegments.joinToString("/")
    }

    private fun parseWorksheetRows(
        builder: DocumentBuilder,
        bytes: ByteArray,
        sharedStrings: List<String>,
        styles: StylesInfo
    ): List<List<String>> {
        val doc = parseXml(builder, bytes)
        val root = doc.documentElement ?: return emptyList()

        var sheetData: Element? = null
        var node = root.firstChild
        while (node != null) {
            if (node is Element && localNameOf(node) == "sheetData") {
                sheetData = node
                break
            }
            node = node.nextSibling
        }
        if (sheetData == null) return emptyList()

        val rows = ArrayList<List<String>>()
        var rowNode = sheetData.firstChild
        while (rowNode != null) {
            if (rowNode is Element && localNameOf(rowNode) == "row") {
                val parsedRow = parseWorksheetRow(rowNode, sharedStrings, styles)
                if (parsedRow.any { it.isNotBlank() }) {
                    rows.add(parsedRow)
                }
            }
            rowNode = rowNode.nextSibling
        }
        return rows
    }

    private fun parseWorksheetRow(
        rowElem: Element,
        sharedStrings: List<String>,
        styles: StylesInfo
    ): List<String> {
        val cells = ArrayList<String>()
        var lastColIndex = -1

        var cellNode = rowElem.firstChild
        while (cellNode != null) {
            if (cellNode is Element && localNameOf(cellNode) == "c") {
                val ref = cellNode.getAttribute("r")
                val parsedCol = if (ref.isNotBlank()) parseColumnIndex(ref) else -1
                val colIndex = if (parsedCol >= 0) parsedCol else (lastColIndex + 1)
                if (colIndex in 0 until MAX_COLUMNS) {
                    lastColIndex = colIndex
                    while (cells.size <= colIndex) {
                        cells.add("")
                    }
                    cells[colIndex] = extractXlsxCellValue(cellNode, sharedStrings, styles)
                }
            }
            cellNode = cellNode.nextSibling
        }

        while (cells.isNotEmpty() && cells.last().isEmpty()) {
            cells.removeAt(cells.lastIndex)
        }
        return cells
    }

    private fun parseColumnIndex(cellRef: String): Int {
        var col = 0
        var hasLetter = false
        for (ch in cellRef) {
            val upper = ch.uppercaseChar()
            if (upper in 'A'..'Z') {
                hasLetter = true
                col = col * 26 + (upper - 'A' + 1)
            } else {
                break
            }
        }
        return if (hasLetter) col - 1 else -1
    }

    private fun extractXlsxCellValue(
        cellElem: Element,
        sharedStrings: List<String>,
        styles: StylesInfo
    ): String {
        val cellType = cellElem.getAttribute("t")
        var vText: String? = null
        var inlineElem: Element? = null

        var child = cellElem.firstChild
        while (child != null) {
            if (child is Element) {
                when (localNameOf(child)) {
                    "v" -> vText = child.textContent
                    "is" -> inlineElem = child
                }
            }
            child = child.nextSibling
        }

        return when (cellType) {
            "s" -> {
                val idx = vText?.trim()?.toIntOrNull()
                if (idx != null && idx in sharedStrings.indices) sharedStrings[idx] else ""
            }
            "inlineStr" -> inlineElem?.let { extractSiText(it) } ?: ""
            "str", "e" -> vText.orEmpty()
            "b" -> if (vText?.trim() == "1" || vText?.trim().equals("true", ignoreCase = true)) "TRUE" else "FALSE"
            else -> {
                val rawVal = vText?.trim().orEmpty()
                if (rawVal.isEmpty()) return ""
                val styleIdx = cellElem.getAttribute("s").toIntOrNull() ?: -1
                if (styles.isDateStyle(styleIdx)) {
                    val serial = rawVal.toDoubleOrNull()
                    if (serial != null && serial.isFinite() && serial >= 0.0) {
                        return formatExcel1900Serial(serial)
                    }
                }
                formatXlsxNumericString(rawVal)
            }
        }
    }

    private fun formatXlsxNumericString(rawValue: String): String {
        val trimmed = rawValue.trim()
        if (trimmed.isEmpty()) return ""
        return try {
            val bd = BigDecimal(trimmed)
            if (bd.scale() > 10) {
                val d = trimmed.toDoubleOrNull()
                if (d != null && d.isFinite()) {
                    return BigDecimal.valueOf(d).stripTrailingZeros().toPlainString()
                }
            }
            bd.stripTrailingZeros().toPlainString()
        } catch (_: Exception) {
            trimmed
        }
    }

    // -------------------------------------------------------------------------
    // OLE2 BIFF8 (.xls) via JXL
    // -------------------------------------------------------------------------

    private fun parseXls(rawBytes: ByteArray): ParsedImportSource {
        val settings = WorkbookSettings().apply {
            setSuppressWarnings(true)
        }
        val workbook = Workbook.getWorkbook(ByteArrayInputStream(rawBytes), settings)
        try {
            val sheetCount = workbook.numberOfSheets
            if (sheetCount <= 0) {
                throw IllegalArgumentException("XLS 文件中未找到任何工作表")
            }

            val parsedSheets = ArrayList<List<List<String>>>(sheetCount)
            for (s in 0 until sheetCount) {
                val sheet = workbook.getSheet(s)
                val rowCount = sheet.rows
                val colCount = sheet.columns
                val sheetRows = ArrayList<List<String>>(rowCount)

                for (r in 0 until rowCount) {
                    val rowCells = ArrayList<String>(colCount)
                    for (c in 0 until colCount) {
                        rowCells.add(extractJxlCellValue(sheet.getCell(c, r)))
                    }
                    while (rowCells.isNotEmpty() && rowCells.last().isEmpty()) {
                        rowCells.removeAt(rowCells.lastIndex)
                    }
                    if (rowCells.any { it.isNotBlank() }) {
                        sheetRows.add(rowCells)
                    }
                }
                parsedSheets.add(sheetRows)
            }

            val selectedRows = selectBestSheet(parsedSheets)
                ?: throw IllegalArgumentException("XLS 工作表内容为空")

            return ParsedImportSource(
                sourceKind = "XLS",
                rows = selectedRows,
                attachments = emptyMap()
            )
        } finally {
            runCatching { workbook.close() }
        }
    }

    private fun extractJxlCellValue(cell: Cell): String {
        if (cell.type == CellType.EMPTY) return ""
        if (cell is DateCell) {
            val date = cell.date ?: return cell.contents.trim()
            val zdt = Instant.ofEpochMilli(date.time).atZone(ZoneOffset.UTC)
            return DATE_TIME_FMT.format(zdt)
        }
        if (cell is NumberCell) {
            val fmtStr = cell.cellFormat?.format?.formatString.orEmpty()
            if (fmtStr.isNotEmpty() && isDateFormatCode(fmtStr)) {
                val v = cell.value
                if (v.isFinite() && v >= 0.0) {
                    return formatExcel1900Serial(v)
                }
            }
            val value = cell.value
            return if (value.isFinite()) {
                BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
            } else {
                cell.contents.trim()
            }
        }
        return cell.contents.trim()
    }

    // -------------------------------------------------------------------------
    // Shared helpers: Sheet Selection, Date Format Detection, Excel Serial Date
    // -------------------------------------------------------------------------

    private fun selectBestSheet(sheets: List<List<List<String>>>): List<List<String>>? {
        for (sheet in sheets) {
            if (sheet.isEmpty()) continue
            val scanLimit = minOf(sheet.size, 50)
            for (i in 0 until scanLimit) {
                val row = sheet[i]
                if (row.size < 2) continue
                val normalized = row.map { normalizeHeaderToken(it) }
                val hasDate = normalized.any { it in DATE_HEADER_CANDIDATES }
                val hasAmount = normalized.any { it in AMOUNT_HEADER_CANDIDATES }
                if (hasDate && hasAmount) {
                    return sheet
                }
            }
        }
        return sheets.firstOrNull { sheet -> sheet.any { row -> row.any { it.isNotBlank() } } }
    }

    private fun normalizeHeaderToken(raw: String): String =
        raw.trim()
            .removePrefix("\uFEFF")
            .replace("（", "(")
            .replace("）", ")")
            .replace(Regex("\\s+"), "")
            .lowercase(Locale.ROOT)

    internal fun isDateFormatCode(formatCode: String): Boolean {
        if (formatCode.isBlank()) return false
        if (Regex("\\[(h+|m+|s+)]", RegexOption.IGNORE_CASE).containsMatchIn(formatCode)) {
            return true
        }

        // Strip quoted text, escaped characters, and bracketed color/locale metadata ([Red], [$USD], [$-404])
        val cleaned = formatCode
            .replace(Regex("\"[^\"]*\""), "")
            .replace(Regex("\\\\."), "")
            .replace(Regex("\\[[^\\]]*]"), "")
            .trim()
            .lowercase(Locale.ROOT)

        if (cleaned.isEmpty() || cleaned == "general" || cleaned == "standard" || cleaned == "@") {
            return false
        }
        if ('y' in cleaned || 'd' in cleaned || 'h' in cleaned) {
            return true
        }
        if (('m' in cleaned || 's' in cleaned) &&
            (cleaned.contains('/') || cleaned.contains('-') || cleaned.contains(':') ||
                cleaned.contains('年') || cleaned.contains('月') || cleaned.contains('日') ||
                cleaned.contains('时') || cleaned.contains('分') || cleaned.contains('秒') ||
                cleaned.contains("am/pm"))
        ) {
            return true
        }
        return false
    }

    internal fun formatExcel1900Serial(serial: Double): String {
        val wholeDays = floor(serial).toLong()
        val fraction = serial - wholeDays

        // Excel 1900 date system treats 1900 as a leap year (day 60 = 1900-02-29).
        // For day >= 60, epoch base 1899-12-30 aligns with real calendar dates (61 -> 1900-03-01).
        var date = if (wholeDays >= 60L) {
            LocalDate.of(1899, 12, 30).plusDays(if (wholeDays == 60L) 59L else wholeDays)
        } else {
            LocalDate.of(1899, 12, 31).plusDays(wholeDays)
        }

        if (fraction < 1e-6) {
            return DATE_ONLY_FMT.format(date)
        }

        var totalSeconds = (fraction * 86_400.0).roundToLong()
        if (totalSeconds >= 86_400L) {
            date = date.plusDays(totalSeconds / 86_400L)
            totalSeconds %= 86_400L
        }
        if (totalSeconds == 0L) {
            return DATE_ONLY_FMT.format(date)
        }

        val hours = (totalSeconds / 3600L).toInt()
        val minutes = ((totalSeconds % 3600L) / 60L).toInt()
        val seconds = (totalSeconds % 60L).toInt()
        val ldt = date.atTime(hours, minutes, seconds)
        return DATE_TIME_FMT.format(ldt)
    }

    private fun localNameOf(node: Node): String =
        node.localName ?: node.nodeName.substringAfter(':')

    private fun fileExtension(fileName: String): String {
        val base = fileName.substringAfterLast('/').substringAfterLast('\\')
        val dot = base.lastIndexOf('.')
        return if (dot >= 0 && dot + 1 < base.length) {
            base.substring(dot + 1).lowercase(Locale.ROOT)
        } else {
            ""
        }
    }
}
