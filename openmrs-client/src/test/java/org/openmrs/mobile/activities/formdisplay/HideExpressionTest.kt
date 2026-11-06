package org.openmrs.mobile.activities.formdisplay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HideExpressionTest {

    private val ncd = "0ca01eb2-8a13-53a6-bc57-9a65bf34d72a"
    private val other = "150e1f45-9060-53f0-8049-92a4e2f46582"
    private fun eval(expr: String, vararg values: Pair<String, String?>) =
        HideExpression.evaluate(expr) { values.toMap()[it] }

    @Test
    fun hidesTopicUntilItsCategoryIsChosen() {
        val expr = "healthPromotionCategory !== '$ncd'"
        assertEquals(true, eval(expr))
        assertEquals(true, eval(expr, "healthPromotionCategory" to "other-uuid"))
        assertEquals(false, eval(expr, "healthPromotionCategory" to ncd))
    }

    @Test
    fun otherBoxShowsWhenAnyTopicIsOther() {
        val expr = "!(ncdTopic === '$other' || commTopic === '$other' || gcsTopic === '$other')"
        assertEquals(true, eval(expr))
        assertEquals(false, eval(expr, "commTopic" to other))
        assertEquals(true, eval(expr, "commTopic" to "x"))
    }

    @Test
    fun andOrPrecedenceAndEquality() {
        assertEquals(true, eval("a == 'x' || b == 'y' && c == 'z'", "a" to "x"))
        assertEquals(false, eval("a == 'x' && (b == 'y' || c == 'z')", "a" to "x"))
        assertEquals(true, eval("!a"))
        assertEquals(true, eval("a != null", "a" to "v"))
    }

    @Test
    fun unsupportedExpressionIsNotEvaluated() {
        assertNull(eval("foo(a) === 'x'"))
        assertNull(eval("a + 1"))
        assertNull(eval("a === "))
    }
}
