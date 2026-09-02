package gh.mutalib.sika.ui

/**
 * English plurals for counted things, in one place.
 *
 * ⚠ **Why a helper and not a `when` at each site.** Sika builds every user-facing string in
 * Kotlin — there are no string resources anywhere in this app — so a number and its noun get
 * joined by hand in about a dozen places. Most of those remembered the singular; four did
 * not, which is how *"1 transactions do not add up"* reached the Settings screen and stayed
 * there long enough for Mutalib to read it (2026-09-02).
 *
 * The instructive one is the import summary. The same sentence about skipped rows exists
 * twice — once in `ImportReportDialog` and once in `SettingsViewModel` — and only the first
 * copy guards the singular. Duplicated text drifts; a shared function cannot.
 *
 * ⚠ **This handles English only, and only the regular cases.** It is a string helper, not a
 * localisation system. If Sika is ever translated, this file is the thing that has to go:
 * Twi marks plurality differently, and languages with dual or paucal forms cannot be served
 * by an `n == 1` test at all. Android's `<plurals>` resources exist for exactly that, and
 * moving to them would mean moving every string in the app into `strings.xml` first.
 */

/**
 * A count with its noun: `count(1, "transaction")` → `"1 transaction"`.
 *
 * `many` covers nouns that do not simply take an -s, so `count(n, "entry", "entries")`.
 */
fun count(n: Int, one: String, many: String = "${one}s"): String =
    "$n " + if (n == 1) one else many

/**
 * The word that has to agree with a count: `agree(n, "does", "do")`.
 *
 * Kept separate from [count] because the verb is usually not adjacent to the number —
 * *"1 transaction out of 148 **does** not add up"* — so the two cannot be produced together.
 */
fun agree(n: Int, one: String, many: String): String = if (n == 1) one else many
