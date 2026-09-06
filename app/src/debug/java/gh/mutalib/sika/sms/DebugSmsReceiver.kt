package gh.mutalib.sika.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import gh.mutalib.sika.TAG
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * **Debug builds only. This file does not exist in a release build.**
 *
 * It exists because PLAN task 6's first verification step turned out to be impossible as
 * written. `SMS_RECEIVED` is a *protected broadcast* — only the system may send it — so
 * `adb shell am broadcast -a android.provider.Telephony.SMS_RECEIVED` is refused outright:
 *
 *     SecurityException: Permission Denial: not allowed to send broadcast
 *     android.provider.Telephony.SMS_RECEIVED from pid=…, uid=2000
 *
 * ⚠ **"There is no emulator on this machine" was true when this was written and is not any
 * more** — an `android-34 google_apis` AVD exists, created for another project, and it is
 * what PLAN task 14's clock-rolling check finally ran on (2026-09-02). It still does not help
 * with SMS: an emulator can fake a message, but not doze, not battery optimisation, and not
 * MTN's real wording. Use it for clocks and alarms, not for the ledger.
 *
 * This receiver takes a message body on its own private action and pushes it through
 * [SmsIngest] — **the identical code path a real SMS takes**, minus the PDU decoding that
 * [SmsReceiver] does first. So it proves parse → dedupe → row-written works end to end,
 * and the live test with a real transaction covers the part it cannot reach.
 *
 * ```
 * adb shell am broadcast -a gh.mutalib.sika.DEBUG_INJECT_SMS \
 *   -n gh.mutalib.sika/.sms.DebugSmsReceiver --es body "Payment for GHS1.00 to …"
 * ```
 */
class DebugSmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // Undo for an injected message. Debug builds only.
        //
        //   adb shell am broadcast -a gh.mutalib.sika.DEBUG_INJECT_SMS         //     -n gh.mutalib.sika/.sms.DebugSmsReceiver --es forget 90000000777
        val forget = intent.getStringExtra("forget")
        if (!forget.isNullOrBlank()) {
            val pendingForget = goAsync()
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                try {
                    val gone = gh.mutalib.sika.data.SikaDatabase.get(context.applicationContext)
                        .transactions().deleteByTxId(forget)
                    Log.i(TAG, "debug-inject: forgot txId=$forget ($gone row(s))")
                } finally {
                    pendingForget.finish()
                }
            }
            return
        }

        // Fabricated rows for looking at a full screen. ⚠ Never written to the database.
        //
        //   adb shell am broadcast -a gh.mutalib.sika.DEBUG_INJECT_SMS         //     -n gh.mutalib.sika/.sms.DebugSmsReceiver --es demo on
        val demo = intent.getStringExtra("demo")
        if (!demo.isNullOrBlank()) {
            gh.mutalib.sika.data.DemoMode.set(
                if (demo == "off") null else DemoRows.build(java.time.LocalDate.now()),
            )
            Log.i(TAG, "debug-inject: demo mode ${if (demo == "off") "off" else "on"}")
            return
        }

        // Fires the end-of-day nudge now, rather than waiting for 9pm.
        //
        //   adb shell am broadcast -a gh.mutalib.sika.DEBUG_INJECT_SMS         //     -n gh.mutalib.sika/.sms.DebugSmsReceiver --es nudge now
        if (!intent.getStringExtra("nudge").isNullOrBlank()) {
            val pendingNudge = goAsync()
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                try {
                    val zone = gh.mutalib.sika.ui.home.ACCRA
                    val day = java.time.LocalDate.now(zone)
                    val from = day.atStartOfDay(zone).toInstant().toEpochMilli()
                    val to = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                    val n = gh.mutalib.sika.data.SikaDatabase.get(context.applicationContext)
                        .transactions().countUnlabelledBetween(from, to)
                    Log.i(TAG, "debug-inject: nudge, $n unlabelled today")
                    gh.mutalib.sika.notify.DailyNudge.show(context.applicationContext, n)
                } finally {
                    pendingNudge.finish()
                }
            }
            return
        }

        // Shows the monthly summary now, for a month you name, instead of waiting for 9am on
        // the 1st.
        //
        //   adb shell am broadcast -a gh.mutalib.sika.DEBUG_INJECT_SMS \
        //     -n gh.mutalib.sika/.sms.DebugSmsReceiver --es monthly 2026-08
        //     ( --es monthly last  for the month just gone )
        //
        // ⚠ **This does NOT test the alarm, and must not be mistaken for doing so.** It calls
        // the same code the alarm's receiver calls, so it proves the summary is built and the
        // notification renders — the parts you can look at. Whether `AlarmManager` actually
        // wakes the app at 9am on the 1st is a different claim, and the only honest ways to
        // check it are `dumpsys alarm` (that the alarm is booked) and rolling a clock forward
        // on an emulator (that it fires and rebooks).
        val monthly = intent.getStringExtra("monthly")
        if (!monthly.isNullOrBlank()) {
            val pendingMonthly = goAsync()
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                try {
                    val zone = gh.mutalib.sika.ui.home.ACCRA
                    val anyDay = if (monthly.equals("last", true)) {
                        java.time.LocalDate.now(zone).minusMonths(1)
                    } else {
                        java.time.LocalDate.parse("$monthly-01")
                    }
                    val period = gh.mutalib.sika.ledger.Period.monthOf(anyDay)
                    val rows = gh.mutalib.sika.data.SikaDatabase.get(context.applicationContext)
                        .transactions().allChronological()
                    val summary = gh.mutalib.sika.ledger.summarise(rows, period, zone)
                    Log.i(TAG, "debug-inject: monthly report for $anyDay")
                    gh.mutalib.sika.notify.MonthlyReport.show(context.applicationContext, summary)
                } catch (t: Throwable) {
                    Log.e(TAG, "debug-inject: monthly report failed", t)
                } finally {
                    pendingMonthly.finish()
                }
            }
            return
        }

        val body = intent.getStringExtra("body")
        if (body.isNullOrBlank()) {
            Log.w(TAG, "debug-inject: no --es body supplied")
            return
        }
        // Defaults to now, so an injected message lands in the current month unless a
        // specific instant is given with --el at.
        val receivedAt = intent.getLongExtra("at", System.currentTimeMillis())

        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                SmsIngest.ingest(
                    context.applicationContext,
                    body,
                    receivedAt,
                    source = "debug-inject",
                    // Matches the live receiver, so an injected cash-out raises the same
                    // prompt a real one would. Without this the injector would report
                    // success on a path the notification never runs on — which is exactly
                    // the kind of gap that makes a debug tool worse than none.
                    promptForCategory = true,
                )
            } catch (t: Throwable) {
                Log.e(TAG, "debug-inject: ingest failed", t)
            } finally {
                pending.finish()
            }
        }
    }
}
