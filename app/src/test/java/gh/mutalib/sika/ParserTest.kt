package gh.mutalib.sika

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * **The QA suite. It only ever grows.**
 *
 * PROFILE.md § 12: every known MoMo message shape goes in here as a golden test — the
 * exact SMS in, the exact row out. The four confirmed shapes arrive at PLAN task 3.
 *
 * The rule that matters more than any single test: when PLAN task 5 sweeps the real
 * inbox and finds a shape nobody has seen, **the new test is written here first**, and
 * only then is the parser changed to pass it. A parser fixed without a test is a parser
 * that will break the same way again.
 *
 * Task 1 has no parser yet, so this holds one placeholder to prove the suite runs and
 * `check` is wired to it. Delete this test when the first real shape lands.
 */
class ParserTest {

    @Test
    fun `qa suite is wired up`() {
        // Guarding a real fact rather than asserting true == true: this is the amount
        // arithmetic every later test depends on, in the currency the app actually uses.
        val amount = 10.00
        val fee = 0.50
        assertEquals(10.50, amount + fee, 0.001)
    }
}
