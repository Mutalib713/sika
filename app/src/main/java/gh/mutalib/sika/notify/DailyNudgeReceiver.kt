package gh.mutalib.sika.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import gh.mutalib.sika.TAG
import gh.mutalib.sika.ui.home.ACCRA
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Fires the end-of-day nudge, then books tomorrow's.
 *
 * Also handles `BOOT_COMPLETED`: Android throws away every alarm when the phone restarts, so
 * without this the nudge would work perfectly until the first reboot and then quietly stop —
 * the kind of failure nobody reports because nothing appears to break.
 */
class DailyNudgeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext

        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            DailyNudge.schedule(app, ACCRA)
            Log.i(TAG, "daily nudge re-scheduled after boot")
            return
        }
        if (intent.action != DailyNudge.ACTION_FIRE) return

        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                // Counting lives in DailyNudge so the Settings test button runs these exact
                // lines rather than an imitation of them.
                DailyNudge.fireNow(app, ACCRA)
            } catch (t: Throwable) {
                Log.e(TAG, "daily nudge failed", t)
            } finally {
                // ⚠ Booked in `finally`, so a failure tonight cannot end the series. An
                // alarm that is only rescheduled on success stops for good the first time
                // anything throws, and does it silently.
                DailyNudge.schedule(app, ACCRA)
                pending.finish()
            }
        }
    }
}
