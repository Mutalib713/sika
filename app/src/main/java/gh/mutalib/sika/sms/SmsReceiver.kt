package gh.mutalib.sika.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import gh.mutalib.sika.TAG
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The live route. Android wakes this the moment a text arrives, **while the app is closed**.
 *
 * There is nothing running in the background: this class is a note in the manifest saying
 * "start me for this event". Android reads that note at install time, keeps it for free, and
 * spins the code up for a couple of seconds when the broadcast fires.
 *
 * It is one of two routes into the ledger and neither is sufficient alone. Android can skip
 * a receiver under doze or an aggressive battery saver, and the phone can be off — but the
 * message still lands in the inbox, so [Sweeper] catches it on the next launch. Belt and
 * braces, with the dedupe on `txId` making the overlap harmless.
 */
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (parts.isNullOrEmpty()) return

        val sender = parts[0].originatingAddress.orEmpty()
        // Sacred Rule 2. Everything else on this phone is none of Sika's business, and
        // returning here means the body is never even assembled.
        if (!SmsInbox.isMomoSender(sender)) return

        // ⚠ **Concatenated, not just parts[0].** MoMo alerts run past 160 characters, so
        // they arrive split across several PDUs. Reading only the first part would give a
        // message truncated mid-sentence — which would usually still match a shape while
        // losing the fee, the balance or the transaction id at the end of the text.
        val body = parts.joinToString("") { it.messageBody.orEmpty() }

        // Sacred Rule 5: Android's own timestamp, never the message text.
        val receivedAt = parts[0].timestampMillis

        // ⚠ onReceive runs on the MAIN thread and Android allows it roughly ten seconds.
        // A Room write there would block the UI and risk being killed mid-insert, so
        // goAsync() holds the broadcast open while the work happens on an IO thread.
        // finish() must be called on every path or the receiver leaks and Android
        // eventually flags the app for holding broadcasts open.
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                SmsIngest.ingest(
                    context.applicationContext,
                    body,
                    receivedAt,
                    source = "live",
                    // The live route is the only one that prompts — see SmsIngest.ingest.
                    promptForCategory = true,
                )
            } catch (t: Throwable) {
                // Never let a parse or database problem take down the receiver: a crash here
                // would lose this message and every one after it until the app was reopened.
                Log.e(TAG, "live: ingest failed", t)
            } finally {
                pending.finish()
            }
        }
    }
}
