package com.yubrae.app.ui.components

import com.yubrae.app.core.model.MoneyAmount
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * 记账金额表达式四则运算求值器（同时支持手动记账弹窗与自动记账悬浮窗）。
 */
internal object QuickExprEval {
    private val OPERATOR_CHARS = charArrayOf('+', '-', '×', '÷', '*', '/')

    fun isOperator(ch: Char): Boolean = ch in OPERATOR_CHARS

    fun containsOperator(expr: String): Boolean {
        if (expr.isEmpty()) return false
        for (i in 1 until expr.length) {
            if (isOperator(expr[i])) return true
        }
        return false
    }

    private fun lastOperatorIndex(expr: String): Int {
        var idx = -1
        for (i in expr.indices) {
            if (isOperator(expr[i]) && i > 0) {
                idx = i
            }
        }
        return idx
    }

    fun appendDigit(expr: String, digit: String): String {
        if (expr == "0" && digit != "00") return digit
        if (expr == "0" && digit == "00") return "0"
        val lastSep = lastOperatorIndex(expr)
        val currentSeg = expr.substring(lastSep + 1)
        if (currentSeg == "0") {
            return if (digit == "00" || digit == "0") expr else expr.dropLast(1) + digit
        }
        if (currentSeg.isEmpty() && digit == "00") {
            return "${expr}0"
        }
        return expr + digit
    }

    fun appendDot(expr: String): String {
        val lastSep = lastOperatorIndex(expr)
        val currentSeg = expr.substring(lastSep + 1)
        if (currentSeg.contains('.')) return expr
        return if (currentSeg.isEmpty()) "${expr}0." else "$expr."
    }

    fun appendOperator(expr: String, op: Char): String {
        if (expr.isEmpty()) return ""
        val last = expr.last()
        return when {
            isOperator(last) -> expr.dropLast(1) + op
            last == '.' -> expr.dropLast(1) + op
            else -> expr + op
        }
    }

    fun evaluate(expr: String): BigDecimal? {
        val cleaned = expr.trim().trimEnd('+', '-', '×', '÷', '*', '/', '.')
        if (cleaned.isEmpty()) return null
        val tokens = mutableListOf<String>()
        val sb = StringBuilder()
        cleaned.forEachIndexed { index, ch ->
            if (isOperator(ch) && index > 0 && sb.isNotEmpty()) {
                tokens.add(sb.toString())
                sb.clear()
                tokens.add(ch.toString())
            } else {
                sb.append(ch)
            }
        }
        if (sb.isNotEmpty()) tokens.add(sb.toString())
        if (tokens.isEmpty()) return null

        return try {
            // 第一遍：先计算 × 和 ÷（支持高优先级四则运算）
            val reduced = mutableListOf<String>()
            var acc = BigDecimal(tokens[0])
            var i = 1
            while (i + 1 < tokens.size) {
                val op = tokens[i]
                val rhs = BigDecimal(tokens[i + 1])
                when (op) {
                    "×", "*" -> {
                        acc = acc.multiply(rhs)
                    }
                    "÷", "/" -> {
                        if (rhs.compareTo(BigDecimal.ZERO) == 0) return null
                        acc = acc.divide(rhs, 8, RoundingMode.HALF_UP)
                    }
                    else -> {
                        reduced.add(acc.toPlainString())
                        reduced.add(op)
                        acc = rhs
                    }
                }
                i += 2
            }
            reduced.add(acc.toPlainString())

            // 第二遍：再从左向右计算 + 和 -
            var result = BigDecimal(reduced[0])
            var j = 1
            while (j + 1 < reduced.size) {
                val op = reduced[j]
                val rhs = BigDecimal(reduced[j + 1])
                result = when (op) {
                    "+" -> result.add(rhs)
                    "-" -> result.subtract(rhs)
                    else -> return null
                }
                j += 2
            }
            result
        } catch (_: Exception) {
            null
        }
    }

    fun evaluateAndFormat(expr: String, maxScale: Int = 4): String? {
        val bd = evaluate(expr) ?: return null
        val scaled = bd.setScale(maxScale.coerceIn(2, 4), RoundingMode.HALF_UP)
            .stripTrailingZeros()
        return if (scaled.compareTo(BigDecimal.ZERO) == 0) "0" else scaled.toPlainString()
    }

    fun evaluateToMicros(expr: String): Long? {
        val bd = evaluate(expr) ?: return null
        if (bd <= BigDecimal.ZERO) return null
        return try {
            bd.multiply(BigDecimal(MoneyAmount.AMOUNT_SCALE))
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact()
        } catch (_: Exception) {
            null
        }
    }
}
