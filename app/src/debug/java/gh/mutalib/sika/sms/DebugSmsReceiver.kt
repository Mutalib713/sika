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
                SmsIngest.ingest(context.applicationContext, body, receivedAt, source = "debug-inject")
            } catch (t: Throwable) {
                Log.e(TAG, "debug-inject: ingest failed", t)
            } finally {
                pending.finish()
            }
        }
    }
}
