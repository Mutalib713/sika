package gh.mutalib.sika

import gh.mutalib.sika.data.LabelSource
import gh.mutalib.sika.ledger.AutoLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Which category a new transaction gets when nobody is asked — the precedence rules.
 *
 * The writing half lives in `LedgerDaoTest` on the phone, because "it does not overwrite" is
 * a claim about SQLite. What is here is the *choosing*, which is ordinary logic and is where
 * a mistake would be invisible: a wrong precedence still produces a plausible-looking label,
 * on the one screen where a wrong label is indistinguishable from a right one.
 */
class AutoLabelTest {

    private val noRules = emptyMap<String, String>()

    // ------------------------------------------------------- the rule, which never ran

    /**
     * ⚠ **The bug this whole file exists for.** Learned rules were only ever applied to rows
     * already in the table, at the moment the rule was created. Nothing consulted them when
     * a message arrived — on either route — so "remember this" remembered nothing about the
     * future, which is the only direction that mattered.
     */
    @Test
    fun aLearnedRuleNamesANewTransaction() {
        val picked = AutoLabel.pick(mapOf("MELCOM" to "Provisions"), "MELCOM", reference = null)

        assertEquals("Provisions" to LabelSource.AUTO_RULE, picked)
    }

    /**
     * A rule outranks a word. Mutalib telling the app what a shop is beats the app guessing
     * from English, and the two disagree often enough for the order to matter: a shop called
     * `RICE MILL` would be Food by keyword, whatever he actually filed it under.
     */
    @Test
    fun aRuleBeatsAKeywordWhenBothHaveAnOpinion() {
        val picked = AutoLabel.pick(
            rules = mapOf("RICE MILL" to "Provisions"),
            counterparty = "RICE MILL",
            reference = "rice",
        )

        assertEquals("Provisions" to LabelSource.AUTO_RULE, picked)
    }

    /**
     * ⚠ A rule keyed on the empty string must claim nothing.
     *
     * Every row in the review queue has a blank counterparty — that is what an unparsed
     * message looks like — so a single stray `"" -> Food` rule would silently file the whole
     * queue under Food. `unlabelledIn` already keeps unparsed rows away from here, which
     * makes this the second lock on the same door rather than the first.
     */
    @Test
    fun aBlankCounterpartyNeverMatchesARule() {
        val picked = AutoLabel.pick(mapOf("" to "Food"), counterparty = "", reference = null)

        assertNull(picked)
    }

    // ------------------------------------------------------------------- the word guess

    /** Mutalib's own question, 2026-08-31: a reference reading "bread" should be Food. */
    @Test
    fun aWordInTheReferenceIsGuessedWhenNoRuleApplies() {
        val picked = AutoLabel.pick(noRules, counterparty = "27xxxxxxxx", reference = "bread")

        assertEquals("Food" to LabelSource.AUTO_KEYWORD, picked)
    }

    /** The counterparty is read too, since most real references are `-` or `1`. */
    @Test
    fun aWordInTheCounterpartyIsGuessedWhenTheReferenceIsUseless() {
        val picked = AutoLabel.pick(noRules, counterparty = "KNUST CANTEEN", reference = "-")

        assertEquals("Food" to LabelSource.AUTO_KEYWORD, picked)
    }

    /**
     * Nothing matched, and that must stay null rather than becoming a default.
     *
     * A wrong category is worse than none: an unnamed row is visibly unfinished and gets
     * asked about, while one filed under "Other" looks answered and is never revisited.
     */
    @Test
    fun anUnknownShopWithNoReferenceGetsNothing() {
        val picked = AutoLabel.pick(noRules, counterparty = "QUARTEY VENTURES", reference = "1")

        assertNull(picked)
    }
}
