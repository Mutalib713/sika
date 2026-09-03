package gh.mutalib.sika.data

import android.content.Context
import gh.mutalib.sika.ui.onboarding.OnboardingPrefs
import java.time.LocalDate

/**
 * Semesters: naming them, ordering them, and carrying the old single term into the new list.
 *
 * ⚠ **Why this is not in the migration.** Version 5→6 creates the table and stops. The dates it
 * needs to import live in SharedPreferences, and a Room migration has no business reaching into
 * a preferences file — it runs on whichever thread opened the database, cannot report a
 * failure anywhere useful, and cannot be unit-tested. So the bridge lives here, runs once on a
 * background thread, and is ordinary code.
 */
object Terms {

    /**
     * Turns a term's position into the name a student would use.
     *
     * ⚠ **A suggestion, not a rule.** Mutalib asked for *"first sem year 1 or something"*, and
     * the "or something" is the important half: KNUST's numbering is his, and a trimester
     * elsewhere would make this nonsense. Every name is editable free text; this only fills the
     * box so the common case is one tap.
     *
     * Two semesters to a year, which is KNUST's shape — index 0 and 1 are Year 1, 2 and 3 are
     * Year 2, and so on.
     *
     * ⚠ **Year first — "Year 1, first semester".** Mutalib's own phrasing, 2026-09-03, and it
     * is the better order anyway: the year is what distinguishes one row from another in a
     * list of eight, so it belongs where the eye lands first.
     */
    fun suggestedName(index: Int): String {
        val year = index / 2 + 1
        val half = if (index % 2 == 0) "first" else "second"
        return "Year $year, $half semester"
    }

    /**
     * Ensures the list is not empty when there is something to fill it with.
     *
     * ⚠ **Only ever adds, and only when the table is empty.** Running twice must not produce
     * two copies of the same semester, so the count check is the whole guard. Someone who never
     * set term dates gets nothing — an empty list is the honest state, and the report already
     * knows to say the dates are not set rather than invent a range.
     */
    suspend fun ensureSeeded(context: Context, dao: TermDao) {
        if (dao.count() > 0) return
        val start = OnboardingPrefs.termStart(context) ?: return
        // ⚠ An end nobody set becomes "still running": a year from the start is a guess, so
        // instead the term simply has not finished, and `current` treats today as inside it.
        val end = OnboardingPrefs.termEnd(context) ?: LocalDate.now().plusDays(1)
        dao.insert(
            TermEntity(
                name = suggestedName(0),
                startDay = start.toEpochDay(),
                endExclusiveDay = end.toEpochDay(),
            ),
        )
    }

    /**
     * The term today falls in, or the most recent one that has already ended.
     *
     * ⚠ **Falls back to the LAST term rather than to null when today is between semesters.**
     * A student on vacation still wants "last semester" to be what the report opens on;
     * showing an empty screen because it is August would read as broken.
     */
    fun currentOrLast(terms: List<TermEntity>, today: LocalDate): TermEntity? =
        terms.firstOrNull { it.contains(today) }
            ?: terms.lastOrNull { !it.start.isAfter(today) }
            ?: terms.firstOrNull()
}
