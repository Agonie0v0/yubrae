package com.yubrae.app.importer

import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.Locale

/**
 * 编码自动探测、分隔符探测（`,` / `\t` / `;`）与 RFC 4180 完整状态机解析器。
 * 支持 UTF-8 BOM / UTF-8 / UTF-16 BOM / GB18030 (GBK)，
 * 以及引号内嵌逗号、换行符和 `""` 转义引号。
 */
internal object Rfc4180CsvParser {

    data class DecodedCsv(
        val charsetName: String,
        val rows: List<List<String>>,
        val delimiter: Char = ','
    )

    private enum class State {
        START_FIELD,
        IN_UNQUOTED,
        IN_QUOTED,
        QUOTE_IN_QUOTED
    }

    fun decodeAndParse(rawBytes: ByteArray, fileNameHint: String = ""): DecodedCsv {
        val (charsetName, text) = decodeBytes(rawBytes)
        val delimiter = detectDelimiter(text, fileNameHint)
        return DecodedCsv(
            charsetName = charsetName,
            rows = parse(text, delimiter),
            delimiter = delimiter
        )
    }

    fun detectDelimiter(text: String, fileNameHint: String = ""): Char {
        if (text.isEmpty()) {
            return if (fileNameHint.endsWith(".tsv", ignoreCase = true) ||
                fileNameHint.endsWith(".tab", ignoreCase = true)
            ) {
                '\t'
            } else {
                ','
            }
        }

        val maxSampleLines = 20
        val maxSampleChars = 16_384
        var inQuotes = false
        var lineHasContent = false
        var sampledLines = 0

        var commaCount = 0
        var tabCount = 0
        var semiCount = 0

        var linesWithComma = 0
        var linesWithTab = 0
        var linesWithSemi = 0

        var lineCommas = 0
        var lineTabs = 0
        var lineSemis = 0

        fun finishLine() {
            if (lineHasContent) {
                if (lineCommas > 0) linesWithComma++
                if (lineTabs > 0) linesWithTab++
                if (lineSemis > 0) linesWithSemi++
                commaCount += lineCommas
                tabCount += lineTabs
                semiCount += lineSemis
                sampledLines++
            }
            lineHasContent = false
            lineCommas = 0
            lineTabs = 0
            lineSemis = 0
        }

        val len = minOf(text.length, maxSampleChars)
        var i = 0
        while (i < len && sampledLines < maxSampleLines) {
            val ch = text[i]
            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < len && text[i + 1] == '"') {
                        i++
                    } else {
                        inQuotes = false
                    }
                }
            } else {
                when (ch) {
                    '"' -> {
                        inQuotes = true
                        lineHasContent = true
                    }
                    '\r' -> {
                        if (i + 1 < len && text[i + 1] == '\n') i++
                        finishLine()
                    }
                    '\n' -> finishLine()
                    ',' -> {
                        lineCommas++
                        lineHasContent = true
                    }
                    '\t' -> {
                        lineTabs++
                        lineHasContent = true
                    }
                    ';' -> {
                        lineSemis++
                        lineHasContent = true
                    }
                    else -> {
                        if (!ch.isWhitespace()) lineHasContent = true
                    }
                }
            }
            i++
        }
        finishLine()

        val lowerHint = fileNameHint.lowercase(Locale.ROOT)
        if ((lowerHint.endsWith(".tsv") || lowerHint.endsWith(".tab")) && tabCount > 0) {
            return '\t'
        }

        if (commaCount == 0 && tabCount == 0 && semiCount == 0) {
            return if (lowerHint.endsWith(".tsv") || lowerHint.endsWith(".tab")) '\t' else ','
        }

        val commaScore = linesWithComma * 1000L + commaCount
        val tabScore = linesWithTab * 1000L + tabCount
        val semiScore = linesWithSemi * 1000L + semiCount

        // Preserve comma as the tie-breaker so standard CSV behavior is 100% unchanged
        return when {
            tabScore > commaScore && tabScore >= semiScore -> '\t'
            semiScore > commaScore && semiScore > tabScore -> ';'
            else -> ','
        }
    }

    fun decodeBytes(rawBytes: ByteArray): Pair<String, String> {
        if (rawBytes.isEmpty()) return "UTF-8" to ""

        // 1. Check BOMs
        if (rawBytes.size >= 3 &&
            rawBytes[0] == 0xEF.toByte() &&
            rawBytes[1] == 0xBB.toByte() &&
            rawBytes[2] == 0xBF.toByte()
        ) {
            val text = String(rawBytes, 3, rawBytes.size - 3, Charsets.UTF_8)
            return "UTF-8-BOM" to stripLeadingBom(text)
        }
        if (rawBytes.size >= 2 &&
            rawBytes[0] == 0xFE.toByte() &&
            rawBytes[1] == 0xFF.toByte()
        ) {
            val text = String(rawBytes, 2, rawBytes.size - 2, Charsets.UTF_16BE)
            return "UTF-16BE" to stripLeadingBom(text)
        }
        if (rawBytes.size >= 2 &&
            rawBytes[0] == 0xFF.toByte() &&
            rawBytes[1] == 0xFE.toByte()
        ) {
            val text = String(rawBytes, 2, rawBytes.size - 2, Charsets.UTF_16LE)
            return "UTF-16LE" to stripLeadingBom(text)
        }

        // 2. Strict UTF-8 validation first
        val utf8Text = tryDecodeStrict(rawBytes, Charsets.UTF_8)
        if (utf8Text != null) {
            return "UTF-8" to stripLeadingBom(utf8Text)
        }

        // 3. Fallback to GB18030 (superset of GBK / GB2312, common in Alipay CSV exports)
        val gbCharset = when {
            Charset.isSupported("GB18030") -> Charset.forName("GB18030")
            Charset.isSupported("GBK") -> Charset.forName("GBK")
            else -> Charsets.UTF_8
        }
        val gbText = String(rawBytes, gbCharset)
        return gbCharset.name() to stripLeadingBom(gbText)
    }

    private fun tryDecodeStrict(rawBytes: ByteArray, charset: Charset): String? {
        return try {
            val decoder = charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            decoder.decode(ByteBuffer.wrap(rawBytes)).toString()
        } catch (_: CharacterCodingException) {
            null
        }
    }

    private fun stripLeadingBom(text: String): String =
        if (text.startsWith('\uFEFF')) text.substring(1) else text

    fun parse(text: String, delimiter: Char = ','): List<List<String>> {
        if (text.isEmpty()) return emptyList()

        val rows = mutableListOf<List<String>>()
        var currentRow = mutableListOf<String>()
        val field = StringBuilder()
        var state = State.START_FIELD
        var i = 0
        val len = text.length

        while (i < len) {
            val ch = text[i]
            when (state) {
                State.START_FIELD -> when (ch) {
                    '"' -> state = State.IN_QUOTED
                    delimiter -> currentRow.add("")
                    '\r' -> {
                        if (i + 1 < len && text[i + 1] == '\n') i++
                        if (currentRow.isNotEmpty()) {
                            currentRow.add("")
                            rows.add(currentRow)
                            currentRow = mutableListOf()
                        }
                    }
                    '\n' -> {
                        if (currentRow.isNotEmpty()) {
                            currentRow.add("")
                            rows.add(currentRow)
                            currentRow = mutableListOf()
                        }
                    }
                    else -> {
                        field.append(ch)
                        state = State.IN_UNQUOTED
                    }
                }

                State.IN_UNQUOTED -> when (ch) {
                    delimiter -> {
                        currentRow.add(field.toString())
                        field.setLength(0)
                        state = State.START_FIELD
                    }
                    '\r' -> {
                        if (i + 1 < len && text[i + 1] == '\n') i++
                        currentRow.add(field.toString())
                        field.setLength(0)
                        rows.add(currentRow)
                        currentRow = mutableListOf()
                        state = State.START_FIELD
                    }
                    '\n' -> {
                        currentRow.add(field.toString())
                        field.setLength(0)
                        rows.add(currentRow)
                        currentRow = mutableListOf()
                        state = State.START_FIELD
                    }
                    else -> field.append(ch)
                }

                State.IN_QUOTED -> when (ch) {
                    '"' -> state = State.QUOTE_IN_QUOTED
                    '\r' -> {
                        if (i + 1 < len && text[i + 1] == '\n') i++
                        field.append('\n')
                    }
                    else -> field.append(ch)
                }

                State.QUOTE_IN_QUOTED -> when (ch) {
                    '"' -> {
                        field.append('"')
                        state = State.IN_QUOTED
                    }
                    delimiter -> {
                        currentRow.add(field.toString())
                        field.setLength(0)
                        state = State.START_FIELD
                    }
                    '\r' -> {
                        if (i + 1 < len && text[i + 1] == '\n') i++
                        currentRow.add(field.toString())
                        field.setLength(0)
                        rows.add(currentRow)
                        currentRow = mutableListOf()
                        state = State.START_FIELD
                    }
                    '\n' -> {
                        currentRow.add(field.toString())
                        field.setLength(0)
                        rows.add(currentRow)
                        currentRow = mutableListOf()
                        state = State.START_FIELD
                    }
                    else -> {
                        // Tolerate stray whitespace or unescaped suffix after closing quote
                        if (!ch.isWhitespace()) {
                            field.append(ch)
                        }
                        state = State.IN_UNQUOTED
                    }
                }
            }
            i++
        }

        when (state) {
            State.START_FIELD -> {
                if (currentRow.isNotEmpty()) {
                    currentRow.add("")
                    rows.add(currentRow)
                }
            }
            State.IN_UNQUOTED, State.IN_QUOTED, State.QUOTE_IN_QUOTED -> {
                currentRow.add(field.toString())
                rows.add(currentRow)
            }
        }
        return rows
    }
}
