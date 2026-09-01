package gh.mutalib.sika.ui.onboarding

import android.content.Context
import androidx.core.content.edit
import java.time.LocalDate

/**
 * What Sika asked on first run, and the answers.
 *
 * Same `SharedPreferences` file as the theme and the notification switches. One small file of
 * settings beats four, and none of this belongs in the ledger — a name is not a transaction.
 *
 * ⚠ **[name] replaces a compile-time constant.** Until 2026-09-01 the greeting read
 * `OWNER = "Osman"`, welded into the binary in two places: wrong for anyone else, and right
 * for Mutalib only by luck. A blank name is a real answer here — the greeting drops the name
 * rather than inventing one — so "not set" and "deliberately empty" have to stay different,
 * which is why [name] is nullable and [nameAsked] exists separately.
 */
object OnboardingPrefs {
    private const val FILE = "sika_prefs"
    private const val DONE = "onboarding_done"
    private const val NAME = "owner_name"
    private const val NAME_ASKED = "owner_name_asked"
    private const val STUDENT = "is_student"
    private const val TERM_START = "semester_start"
    private const val TERM_END = "semester_end"

    private fun prefs(context: Context) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** True once the flow has been through to the end, or deliberately skipped. */
    fun done(context: Context): Boolean = prefs(context).getBoolean(DONE, false)

    fun setDone(context: Context, value: Boolean) =
        prefs(context).edit { putBoolean(DONE, value) }

    /** Null means no name — the greeting then says only the time of day. */
    fun name(context: Context): String? =
        prefs(context).getString(NAME, null)?.takeIf { it.isNotBlank() }

    fun setName(context: Context, value: String?) = prefs(context).edit {
        putString(NAME, value?.trim()?.takeIf { it.isNotEmpty() })
        putBoolean(NAME_ASKED, true)
    }

    fun nameAsked(context: Context): Boolean = prefs(context).getBoolean(NAME_ASKED, false)

    /**
     * ⚠ **A "no" here removes the semester view entirely**, rather than leaving a segment in
     * the report's switcher that means nothing to whoever is reading it. Mutalib's shape,
     * 2026-09-01.
     */
    fun isStudent(context: Context): Boolean = prefs(context).getBoolean(STUDENT, false)

    fun setStudent(context: Context, value: Boolean) =
        prefs(context).edit { putBoolean(STUDENT, value) }

    /**
     * The term's own dates, when they are known.
     *
     * ⚠ **Both null is a normal state, not a failure.** "I don't know the dates yet" is a real
     * answer — you might install this in the holidays — and `Period.semesterOf` falls back to
     * its old guess (the oldest transaction) rather than refusing to draw anything.
     *
     * Stored as epoch days rather than a formatted string: a date written as text has to be
     * parsed back with a locale, and a locale is a thing that changes underneath you.
     */
    fun termStart(context: Context): LocalDate? = day(context, TERM_START)

    fun termEnd(context: Context): LocalDate? = day(context, TERM_END)

    fun setTerm(context: Context, start: LocalDate?, end: LocalDate?) = prefs(context).edit {
        if (start == null) remove(TERM_START) else putLong(TERM_START, start.toEpochDay())
        if (end == null) remove(TERM_END) else putLong(TERM_END, end.toEpochDay())
    }

    /**
     * True when a term was set and its end date has passed.
     *
     * ⚠ Without this the semester view rots silently: it keeps reporting on a term that
     * finished months ago, and every figure in it is right about the wrong stretch of time.
     */
    fun termHasEnded(context: Context, today: LocalDate): Boolean {
        val end = termEnd(context) ?: return false
        return today.isAfter(end)
    }

    private fun day(context: Context, key: String): LocalDate? {
        val stored = prefs(context).getLong(key, Long.MIN_VALUE)
        return if (stored == Long.MIN_VALUE) null else LocalDate.ofEpochDay(stored)
    }
}
