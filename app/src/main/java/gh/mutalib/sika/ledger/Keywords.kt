package gh.mutalib.sika.ledger

import java.util.Locale

/**
 * Turns a written reference into a category guess — Mutalib's question, 2026-08-31:
 * *"what if I put a reference like bread which falls under food, can the app recognise
 * that it's food?"*
 *
 * Yes, and this is how. A word list, matched on whole words, against the reference the
 * sender typed and then the counterparty's name.
 *
 * ## What it cannot do, stated plainly
 *
 * - **It only knows words that are in this list.** "Kelewele" works because it is written
 *   below; a dish nobody added returns nothing. There is no cleverness here and no model —
 *   the parser is regex for the same reason (PROFILE.md): a guess that cannot be tested is
 *   worse than no guess in a ledger.
 * - **It guesses from language, so it can be wrong.** "Credit" might be airtime or a loan.
 *   That is why a keyword match is [gh.mutalib.sika.data.LabelSource.AUTO_KEYWORD], the
 *   weakest source there is, and why it never touches a row a human has already labelled.
 * - **Most real MoMo messages carry no useful reference at all.** In Mutalib's own inbox the
 *   field is usually `-` or `1`; exactly one transaction carries a real word, and that word
 *   is `Bread`. So this improves the good case and does nothing for the common one — the
 *   learn-once counterparty rule remains the mechanism that actually removes work.
 *
 * ## Why whole words
 *
 * Substring matching looks fine until `gb` matches inside a counterparty called `AGBOGBA`
 * and every payment to that shop becomes Data. Matching is on word boundaries for that
 * reason, and short entries are the ones that would have broken it.
 */
object Keywords {

    /**
     * Ghanaian first. These are the words that actually appear on a student's phone in
     * Accra, not a generic English list.
     */
    private val TABLE: Map<String, List<String>> = mapOf(
        "Food" to listOf(
            "bread", "rice", "waakye", "banku", "fufu", "jollof", "kenkey", "chop",
            "food", "chicken", "egg", "eggs", "tea", "sachet", "gob3", "indomie",
            "noodles", "yam", "plantain", "kelewele", "kebab", "khebab", "canteen",
            "restaurant", "lunch", "breakfast", "supper", "dinner", "chopbar",
            "koko", "porridge", "beans", "stew", "soup", "fish", "meat",
        ),
        "Transport" to listOf(
            "trotro", "tro", "uber", "bolt", "taxi", "fare", "transport", "lorry",
            "station", "bus", "okada", "yango", "dropping", "car",
        ),
        "Data" to listOf("data", "bundle", "bundles", "internet", "wifi", "mifi"),
        "Airtime" to listOf("airtime", "recharge", "topup", "units"),
        "Rent" to listOf("rent", "hostel", "landlord", "lease", "accommodation"),
        "Provisions" to listOf(
            "soap", "detergent", "provisions", "toiletries", "toothpaste", "tissue",
            "omo", "shampoo", "pomade", "sanitizer", "brush",
        ),
        "Printing" to listOf(
            "print", "printing", "printout", "photocopy", "copies", "binding",
            "stationery", "pen", "pens", "notebook", "handout", "handouts",
        ),
        "Sent home" to listOf(
            "mum", "mom", "mummy", "dad", "daddy", "mother", "father", "home",
            "family", "brother", "sister", "upkeep", "chop money",
        ),
    )

    /** Flattened once at class load: word -> category. */
    private val INDEX: Map<String, String> =
        TABLE.flatMap { (category, words) -> words.map { it to category } }.toMap()

    /**
     * The best category for some free text, or null when nothing matches.
     *
     * ⚠ **Longest match wins.** "chop money" must beat "chop", or a reference reading
     * "chop money" would be filed as Food when it is money sent home.
     */
    fun categoryFor(vararg text: String?): String? {
        val haystack = text.filterNotNull()
            .joinToString(" ")
            .lowercase(Locale.ROOT)
        if (haystack.isBlank()) return null

        return INDEX.entries
            .filter { (word, _) -> containsWord(haystack, word) }
            .maxByOrNull { it.key.length }
            ?.value
    }

    /**
     * Whole-word containment.
     *
     * Deliberately not `String.contains`: `gb` inside `AGBOGBA` would make every payment to
     * that shop Data. A word must be bounded by something that is not a letter or digit.
     */
    private fun containsWord(haystack: String, word: String): Boolean {
        var from = 0
        while (true) {
            val at = haystack.indexOf(word, from)
            if (at < 0) return false
            val before = at - 1
            val after = at + word.length
            val okBefore = before < 0 || !haystack[before].isLetterOrDigit()
            val okAfter = after >= haystack.length || !haystack[after].isLetterOrDigit()
            if (okBefore && okAfter) return true
            from = at + 1
        }
    }
}
