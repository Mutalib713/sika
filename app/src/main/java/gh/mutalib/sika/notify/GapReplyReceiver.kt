package gh.mutalib.sika.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.RemoteInput
import gh.mutalib.sika.TAG
import gh.mutalib.sika.data.SikaDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Takes the answer typed into a [GapAlert] and stores it against the gap.
 *
 * ⚠ **Writes the note and nothing else.** It does not clear the `GAP` flag and does not move
 * the money into any category total. The gap is a fact about a message MTN never sent, and
 * remembering the purchase does not make the message exist — a check that can be switched off
 * by typing into it is not a check.
 *
 * ⚠ **One step, not the cash-out prompt's three.** That one confirms because its buttons sit
 * under a swiping thumb and a mis-tap silently relabelled a transaction. This one cannot be
 * triggered by a mis-tap: it requires opening a text field and typing words into it. A
 * confirmation step on top of that is friction with nothing to prevent, and it can be
 * corrected on Home afterwards anyway.
 *
 * Never exported: the only thing allowed to send this is the PendingIntent this app handed to
 * the system.
 */
class GapReplyReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val rowId = intent.getLongExtra(GapAlert.EXTRA_ROW_ID, -1L)
        val answer = RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence(GapAlert.KEY_ANSWER)
            ?.toString()
            ?.trim()

        if (rowId <= 0L || answer.isNullOrBlank()) {
            Log.w(TAG, "gap reply ignored: rowId=$rowId answer=${answer?.length ?: 0} chars")
            return
        }

        val app = context.applicationContext
        // goAsync buys the receiver time for a database write. Without it the process can be
        // killed the moment onReceive returns, with the write still queued.
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                SikaDatabase.get(app).transactions().setGapNote(rowId, answer)
                Log.i(TAG, "gap on row $rowId explained from the shade")
                GapAlert.cancel(app, rowId)
            } catch (e: Exception) {
                Log.e(TAG, "could not store the gap explanation", e)
            } finally {
                pending.finish()
            }
        }
    }
}
