package gh.mutalib.sika.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import gh.mutalib.sika.TAG
import gh.mutalib.sika.data.LabelSource
import gh.mutalib.sika.data.SikaDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Receives a tap on one of the cash-out prompt's buttons and labels the row.
 *
 * Never exported: the only thing allowed to send this is the PendingIntent this app handed
 * to the system. An exported receiver here would let any app on the phone relabel the
 * ledger, silently.
 */
class CashOutReplyReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val rowId = intent.getLongExtra(CashOutPrompt.EXTRA_ROW_ID, -1L)
        val label = intent.getStringExtra(CashOutPrompt.EXTRA_LABEL)
        if (rowId <= 0L || label.isNullOrBlank()) {
            Log.w(TAG, "cash-out reply ignored: rowId=$rowId label=$label")
            return
        }

        // Cancel first, on the main thread, so the shade responds instantly rather than
        // after a database round trip.
        CashOutPrompt.cancel(context, rowId)

        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                // PROMPT, not AUTO_RULE: this is a human answering a question, so a
                // learned rule must never later overwrite it.
                SikaDatabase.get(app).transactions()
                    .setLabel(rowId, label, LabelSource.PROMPT)

                // ⚠ **Deliberately no learn-once rule here**, unlike the transaction sheet.
                //
                // A rule is keyed on the counterparty, and a cash-out's counterparty is the
                // *agent*, not the purchase. Mutalib uses the same agent for whatever he
                // happens to need cash for, so "the agent by the junction = Food" would
                // quietly mislabel every future cash-out from that agent — and worse, it
                // would look like the app had learned something.
                //
                // The one place a cash-out label generalises is nowhere. Ask every time.
                Log.i(TAG, "cash-out row $rowId labelled '$label' from the shade")
            } catch (t: Throwable) {
                Log.e(TAG, "cash-out reply failed for row $rowId", t)
            } finally {
                pending.finish()
            }
        }
    }
}
