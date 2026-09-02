package gh.mutalib.sika

import gh.mutalib.sika.ui.agree
import gh.mutalib.sika.ui.count
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The plural helper, and the four sentences that were wrong before it existed.
 *
 * ⚠ **The bug these guard against was invisible to every other test in this project.** The
 * arithmetic was right, the reconciliation was right, the row count was right — and the screen
 * still said *"1 transactions do not add up"*, because nothing had ever rendered that sentence
 * with a one in it. Mutalib read it on his own phone on 2026-09-02.
 *
 * So the cases below are deliberately boring: n = 0, 1 and 2 for every counted noun the app
 * shows. One is the case that breaks; zero and two are there to prove the fix did not break
 * the cases that already worked.
 */
class PluralsTest {

    @Test
    fun `one is singular and everything else is not`() {
        assertEquals("0 transactions", count(0, "transaction"))
        assertEquals("1 transaction", count(1, "transaction"))
        assertEquals("2 transactions", count(2, "transaction"))
        assertEquals("148 transactions", count(148, "transaction"))
    }

    @Test
    fun `irregular plurals are given explicitly`() {
        // -s would produce "categorys", which is why the second argument exists.
        assertEquals("1 category", count(1, "category", "categories"))
        assertEquals("3 categories", count(3, "category", "categories"))
    }

    @Test
    fun `verbs agree with the count`() {
        assertEquals("does", agree(1, "does", "do"))
        assertEquals("do", agree(0, "does", "do"))
        assertEquals("do", agree(2, "does", "do"))
    }

    /**
     * ⚠ **Negative counts read as plural, and that is the intended behaviour.** No caller in
     * Sika can produce one — every count comes from a collection size or a row count — but if
     * one ever did, "-1 transactions" is the less alarming failure. Recorded so the choice is
     * visible rather than accidental.
     */
    @Test
    fun `only exactly one is singular`() {
        assertEquals("-1 transactions", count(-1, "transaction"))
    }

    /**
     * The four sentences that shipped wrong, rebuilt here exactly as their screens build them.
     *
     * If someone rewords a screen later, this test does not follow — it is a record of the
     * shapes, not a binding on the copy. Its job is to make the singular case impossible to
     * forget while these particular sentences exist.
     */
    @Test
    fun `the sentences that were wrong on the phone`() {
        assertEquals(
            "1 transaction does not add up",
            count(1, "transaction") + " " + agree(1, "does", "do") + " not add up",
        )
        assertEquals(
            "1 transaction and every label, as CSV",
            count(1, "transaction") + " and every label, as CSV",
        )
        assertEquals(
            "1 row could not be read and was left out.",
            count(1, "row") + " could not be read and " + agree(1, "was", "were") + " left out.",
        )
        assertEquals(
            "1 still needs a category",
            "1 still " + agree(1, "needs", "need") + " a category",
        )
    }
}
