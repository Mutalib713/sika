package gh.mutalib.sika.notify

import android.content.Context
import androidx.core.content.edit

/**
 * Which of Sika's notifications are wanted.
 *
 * ⚠ **Both default to on, and that is a real decision rather than laziness.** The cash-out
 * prompt is the only way a `CASH OUT AGENT` row ever gets a name — MoMo does not say what the
 * cash was spent on, so without the prompt that money is permanently unexplained. Defaulting
 * it off would make the app quietly worse for anyone who never opens Settings.
 *
 * ⚠ **These are a preference, not the permission.** Android's own notification switch still
 * outranks both: [CashOutPrompt.canPost] checks the system state as well, because a user who
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

    private fun prefs(context: Context) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** Ask what a cash-out was for, the moment the message lands. */
    fun cashOutPrompt(context: Context): Boolean = prefs(context).getBoolean(CASH_OUT, true)

    fun setCashOutPrompt(context: Context, on: Boolean) =
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
}
