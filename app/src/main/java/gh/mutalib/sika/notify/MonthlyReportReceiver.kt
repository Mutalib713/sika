package gh.mutalib.sika.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import gh.mutalib.sika.TAG
import gh.mutalib.sika.data.SikaDatabase
import gh.mutalib.sika.ledger.Period
import gh.mutalib.sika.ledger.summarise
import gh.mutalib.sika.ui.home.ACCRA
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Fires the monthly summary, then books next month's.
 *
 * Also handles `BOOT_COMPLETED`: Android discards every alarm when the phone restarts, so
 * without this the summary would work until the first reboot and then stop — silently, since
 * a notification that does not arrive looks exactly like a month with nothing to say.
 */
class MonthlyReportReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext

        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            MonthlyReport.schedule(app, ACCRA)
            Log.i(TAG, "monthly report re-scheduled after boot")
            return
        }
        if (intent.action != MonthlyReport.ACTION_FIRE) return

        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                // ⚠ **The month that just ENDED, not the one the alarm fired in.** The alarm
                // goes off on the 1st, so "this month" is a few hours old and empty; the
                // report is about the one before it. Getting this backwards would produce a
                // monthly summary of nothing, every month, and look like a bug in the totals
                // rather than a bug in the date.
                val lastMonth = Period.monthOf(LocalDate.now(ACCRA).minusMonths(1))
                val rows = SikaDatabase.get(app).transactions().allChronological()
                MonthlyReport.show(app, summarise(rows, lastMonth, ACCRA))
            } catch (t: Throwable) {
                Log.e(TAG, "monthly report failed", t)
            } finally {
                // ⚠ Booked in `finally`, so a failure this month cannot end the series. An
                // alarm rescheduled only on success stops for good the first time anything
                // throws, and does it without a word.
                MonthlyReport.schedule(app, ACCRA)
                pending.finish()
            }
        }
    }
}
