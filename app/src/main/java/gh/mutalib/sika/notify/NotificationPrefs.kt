package gh.mutalib.sika.notify

import android.content.Context
import androidx.core.content.edit

/**
 * Which of Sika's notifications are wanted.
 *
 * ⚠ **Both default to on, and that is a real decision rather than laziness.** The prompt is
 * the only way an unnamed row ever gets a category without opening the app — MoMo does not say
 * what money was spent on, so without it that spending stays permanently unexplained.
 * Defaulting it off would make the app quietly worse for anyone who never opens Settings.
 *
 * ⚠ **These are a preference, not the permission.** Android's own notification switch still
 * outranks both: [CategoryPrompt.canPost] checks the system state as well, because a user who
 * turned Sika's notifications off in Android settings has said something stronger than
 * anything stored here.
 *
 * Same `SharedPreferences` file as the theme. One small file of settings beats three.
 */
object NotificationPrefs {
    private const val FILE = "sika_prefs"
    private const val CASH_OUT = "notify_cash_out"
    private const val END_OF_DAY = "notify_end_of_day"
    private const val GAP = "notify_gap"
    private const val MONTHLY = "notify_monthly"
    private const val TERM_END = "notify_term_end"
    private const val TERMS_TOLD = "terms_told"

    private fun prefs(context: Context) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /**
     * Ask what money just spent was for, the moment the message lands.
     *
     * ⚠ **The stored key is still `notify_cash_out` and must stay that way.** This covered
     * cash-outs only until 2026-09-06; renaming the key would read as "never set" on a phone
     * that already has it, silently resetting the switch to its default and discarding
     * whatever Mutalib chose. The key is storage, the function name is meaning — they are
     * allowed to disagree, and here they have to.
     */
    fun categoryPrompt(context: Context): Boolean = prefs(context).getBoolean(CASH_OUT, true)

    fun setCategoryPrompt(context: Context, on: Boolean) =
        prefs(context).edit { putBoolean(CASH_OUT, on) }

    /** One reminder at the end of the day, and only when something is still unlabelled. */
    fun endOfDay(context: Context): Boolean = prefs(context).getBoolean(END_OF_DAY, true)

    fun setEndOfDay(context: Context, on: Boolean) =
        prefs(context).edit { putBoolean(END_OF_DAY, on) }

    /**
     * Tell me the moment a balance does not tally. Mutalib's request, 2026-09-01.
     *
     * ⚠ **On by default, and this is the one where that matters most.** Reconciliation is
     * Sacred Rule 3 — the app's whole claim is that its arithmetic checks out. A result that
     * only surfaces if you happen to open the app is most of the way back to not checking,
     * and the person who can say what the missing money was is holding the phone right now,
     * while they still remember.
     */
    fun gapAlert(context: Context): Boolean = prefs(context).getBoolean(GAP, true)

    fun setGapAlert(context: Context, on: Boolean) =
        prefs(context).edit { putBoolean(GAP, on) }

    /**
     * A summary of the month just gone, on the 1st.
     *
     * ⚠ **This switch was deliberately absent until the notification behind it existed.** A
     * control that looks live and changes nothing is worse than no control, so it arrived with
     * PLAN task 14 rather than ahead of it.
     */
    fun monthly(context: Context): Boolean = prefs(context).getBoolean(MONTHLY, true)

    fun setMonthly(context: Context, on: Boolean) =
        prefs(context).edit { putBoolean(MONTHLY, on) }

    /** A week before a semester ends, and again once it has. */
    fun termEnd(context: Context): Boolean = prefs(context).getBoolean(TERM_END, true)

    fun setTermEnd(context: Context, on: Boolean) =
        prefs(context).edit { putBoolean(TERM_END, on) }

    /**
     * Which semester alerts have already gone out, as `"<termId>:<KIND>"`.
     *
     * ⚠ **This exists so the windows in [TermAlert.due] can be a week wide.** Firing on exactly
     * the right day is a promise a phone cannot keep — off, flat, or in a drawer, and the one
     * day passes. Wide windows plus a record of what was said gives an alert that still arrives
     * after a fortnight in a drawer, and still arrives only once.
     *
     * ⚠ **Never returns the live set.** `SharedPreferences` documents the set from
     * `getStringSet` as one you must not modify — mutating it corrupts the in-memory copy and
     * the change may not survive to disk. The defensive copy is the fix and is not optional.
     */
    fun termsTold(context: Context): Set<String> =
        prefs(context).getStringSet(TERMS_TOLD, emptySet())?.toSet() ?: emptySet()

    fun rememberTermTold(context: Context, key: String) =
        prefs(context).edit { putStringSet(TERMS_TOLD, termsTold(context) + key) }

    /**
     * Forgets that a semester was ever announced, so editing its dates re-arms both alerts.
     *
     * ⚠ Without this, moving a term's end date forward would be silent: the alert was already
     * sent for the old date, the key is unchanged because it is built from the row id, and the
     * new ending would pass with nothing said.
     */
    fun forgetTermTold(context: Context, termId: Long) = prefs(context).edit {
        putStringSet(TERMS_TOLD, termsTold(context).filterNot { it.startsWith("$termId:") }.toSet())
    }
}
