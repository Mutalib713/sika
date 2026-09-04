package gh.mutalib.sika.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import gh.mutalib.sika.TAG
import gh.mutalib.sika.data.SikaDatabase
import gh.mutalib.sika.ledger.Period
import gh.mutalib.sika.ledger.PeriodMode
import gh.mutalib.sika.ledger.summarise
import gh.mutalib.sika.ui.home.ACCRA
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Checks every morning whether a semester is nearly over or just finished.
 *
 * Also handles `BOOT_COMPLETED`: Android discards every alarm when the phone restarts, so
 * without this the check would work until the first reboot and then stop — silently, since a
 * notification that does not arrive looks exactly like a morning with nothing to say.
 */
class TermAlertReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext

        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            TermAlert.schedule(app, ACCRA)
            Log.i(TAG, "term check re-scheduled after boot")
            return
        }
        if (intent.action != TermAlert.ACTION_FIRE) return

        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val today = LocalDate.now(ACCRA)
                val db = SikaDatabase.get(app)
                val terms = db.terms().all()
                val due = TermAlert.due(terms, today, NotificationPrefs.termsTold(app))
                if (due == null) {
                    Log.i(TAG, "term check: nothing due today")
                    return@launch
                }
                // ⚠ **Spend is read for the term's own dates, not for "this month".** A
                // semester crosses months, and quoting a month's total under a semester's name
                // would be a wrong number presented as a right one.
                val rows = db.transactions().allChronological()
                val period = Period(
                    PeriodMode.SEMESTER, due.term.start, due.term.endExclusive, due.term.name,
                )
                val spent = summarise(rows, period, ACCRA).moneyOut

                // ⚠ **Recorded only when the notification actually went out.** If it was
                // suppressed — switched off, or the permission revoked — marking it told would
                // silently consume the one chance to say it, and the alert would never arrive
                // even after the switch went back on.
                if (TermAlert.show(app, due, spent)) {
                    NotificationPrefs.rememberTermTold(app, due.key)
                }
            } catch (t: Throwable) {
                Log.e(TAG, "term check failed", t)
            } finally {
                // ⚠ Booked in `finally`, so a failure today cannot end the series. An alarm
                // rescheduled only on success stops for good the first time anything throws,
                // and does it without a word.
                TermAlert.schedule(app, ACCRA)
                pending.finish()
            }
        }
    }
}
