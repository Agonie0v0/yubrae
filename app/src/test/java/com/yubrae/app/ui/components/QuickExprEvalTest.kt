package com.yubrae.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class QuickExprEvalTest {

    @Test
    fun evaluate_respectsOperatorPrecedence() {
        val addMul = QuickExprEval.evaluate("10+20×3")
        assertNotNull(addMul)
        assertEquals(0, BigDecimal("70").compareTo(addMul))
        assertEquals("70", QuickExprEval.evaluateAndFormat("10+20×3"))
        assertEquals(700_000L, QuickExprEval.evaluateToMicros("10+20×3"))

        val subDiv = QuickExprEval.evaluate("100-25÷5")
        assertNotNull(subDiv)
        assertEquals(0, BigDecimal("95").compareTo(subDiv))
        assertEquals("95", QuickExprEval.evaluateAndFormat("100-25÷5"))
        assertEquals(950_000L, QuickExprEval.evaluateToMicros("100-25÷5"))

        assertEquals(90_000L, QuickExprEval.evaluateToMicros("2+3*4-10/2"))
    }

    @Test
    fun evaluateToMicros_handlesDecimalsAndRoundingPrecision() {
        assertEquals(130_000L, QuickExprEval.evaluateToMicros("12.345+0.655"))
        assertEquals("3.33", QuickExprEval.evaluateAndFormat("10÷3", maxScale = 2))
        assertEquals("3.3333", QuickExprEval.evaluateAndFormat("10÷3", maxScale = 4))
        assertEquals(33_333L, QuickExprEval.evaluateToMicros("10÷3"))
        assertEquals(1_250L, QuickExprEval.evaluateToMicros("1÷8"))
        assertEquals(1_235L, QuickExprEval.evaluateToMicros("0.12345"))
        assertEquals(1_234L, QuickExprEval.evaluateToMicros("0.12344"))
    }

    @Test
    fun evaluate_stripsTrailingOperatorAndReplacesConsecutiveOperators() {
        assertTrue(QuickExprEval.containsOperator("12.5+"))
        val trailingResult = QuickExprEval.evaluate("12.5+")
        assertNotNull(trailingResult)
        assertEquals(0, BigDecimal("12.5").compareTo(trailingResult))
        assertEquals(125_000L, QuickExprEval.evaluateToMicros("12.5+"))
        assertEquals(125_000L, QuickExprEval.evaluateToMicros("12.5+."))

        assertEquals("", QuickExprEval.appendOperator("", '+'))
        assertEquals("10+", QuickExprEval.appendOperator("10.", '+'))
        val replaced = QuickExprEval.appendOperator(QuickExprEval.appendOperator("10", '+'), '×')
        assertEquals("10×", replaced)
        assertEquals("10÷", QuickExprEval.appendOperator(replaced, '÷'))
        assertEquals(100_000L, QuickExprEval.evaluateToMicros("10÷"))
    }

    @Test
    fun evaluateToMicros_rejectsDivisionByZeroAndNonPositiveResults() {
        assertTrue(QuickExprEval.containsOperator("10÷0"))
        assertNull(QuickExprEval.evaluate("10÷0"))
        assertNull(QuickExprEval.evaluateAndFormat("10÷0"))
        assertNull(QuickExprEval.evaluateToMicros("10÷0"))
        assertNull(QuickExprEval.evaluateToMicros("10/0.00"))

        assertTrue(QuickExprEval.containsOperator("10-20"))
        assertEquals(0, BigDecimal("-10").compareTo(QuickExprEval.evaluate("10-20")))
        assertNull(QuickExprEval.evaluateToMicros("10-20"))

        assertTrue(QuickExprEval.containsOperator("10-10"))
        assertEquals(0, BigDecimal.ZERO.compareTo(QuickExprEval.evaluate("10-10")))
        assertNull(QuickExprEval.evaluateToMicros("10-10"))

        assertFalse(QuickExprEval.containsOperator(""))
        assertFalse(QuickExprEval.containsOperator("-5"))
        assertNull(QuickExprEval.evaluateToMicros(""))
        assertNull(QuickExprEval.evaluateToMicros("0"))
        assertNull(QuickExprEval.evaluateToMicros("-5"))
    }
}
