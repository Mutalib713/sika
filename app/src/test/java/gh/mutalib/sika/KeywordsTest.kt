package gh.mutalib.sika

import gh.mutalib.sika.ledger.Keywords
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The reference-to-category guess — Mutalib's question on 2026-08-31, *"what if I put a
 * reference like bread which falls under food, can the app recognise that it's food?"*
 */
class KeywordsTest {

    @Test
    fun `the question he actually asked`() {
        assertEquals("Food", Keywords.categoryFor("Bread"))
    }

    @Test
    fun `case and surrounding words do not matter`() {
        assertEquals("Food", Keywords.categoryFor("BREAD AND EGGS"))
        assertEquals("Transport", Keywords.categoryFor("trotro fare to campus"))
        assertEquals("Printing", Keywords.categoryFor("photocopy of handouts"))
    }

    /**
     * ⚠ The trap that made whole-word matching necessary. Plain `contains` would find "gb"
     * inside "AGBOGBA" and file every payment to that shop as Data — silently, forever.
     */
    @Test
    fun `a keyword inside a longer word is not a match`() {
        assertNull(Keywords.categoryFor("AGBOGBA MARKET"))
        // "car" must not fire inside "CARPENTER".
        assertNull(Keywords.categoryFor("CARPENTER"))
        // "pen" must not fire inside "PENSION".
        assertNull(Keywords.categoryFor("PENSION FUND"))
    }

    /**
     * ⚠ Longest match wins, or "chop money" — money sent home — would be filed as Food
     * because "chop" is also in the Food list.
     */
    @Test
    fun `the longer phrase beats the shorter word inside it`() {
        assertEquals("Sent home", Keywords.categoryFor("chop money"))
        assertEquals("Food", Keywords.categoryFor("chop bar"))
    }

    @Test
    fun `the reference is tried before the counterparty`() {
        // A shop whose name says nothing, and a reference that says everything.
        assertEquals("Food", Keywords.categoryFor("waakye", "KWAME ENTERPRISE"))
    }

    @Test
    fun `the counterparty is used when the reference is empty`() {
        assertEquals("Airtime", Keywords.categoryFor(null, "MTN AIRTIME"))
    }

    /** Most real MoMo references are `-` or `1`. Those must produce nothing, not a guess. */
    @Test
    fun `the usual useless references guess nothing`() {
        assertNull(Keywords.categoryFor("-"))
        assertNull(Keywords.categoryFor("1"))
        assertNull(Keywords.categoryFor(""))
        assertNull(Keywords.categoryFor(null))
        assertNull(Keywords.categoryFor(null, null))
    }

    @Test
    fun `Ghanaian words are in the list, because that is what he actually types`() {
        assertEquals("Food", Keywords.categoryFor("kelewele"))
        assertEquals("Food", Keywords.categoryFor("banku"))
        assertEquals("Food", Keywords.categoryFor("gob3"))
        assertEquals("Transport", Keywords.categoryFor("yango"))
        assertEquals("Provisions", Keywords.categoryFor("omo"))
    }
}
