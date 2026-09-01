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
 * There is no emulator on this machine either (PROFILE.md § 10), so `adb emu sms send` is
 * not available.
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
                    promptOnCashOut = true,
                )
            } catch (t: Throwable) {
                Log.e(TAG, "debug-inject: ingest failed", t)
            } finally {
                pending.finish()
            }
        }
    }
}
