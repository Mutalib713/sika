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
 * Handles the cash-out prompt's buttons.
 *
 * **Three steps, because picking is not the same as deciding.** Tapping a category only
 * proposes it; the notification then asks *"Save it?"* and only **Save** writes anything.
 * Mutalib asked for this on 2026-08-31, and the reason is stronger than convenience: these
 * buttons live in the notification shade, where a thumb is already swiping past, and a
 * mis-tap used to relabel a transaction silently with no undo.
 *
 * The proposed category rides on the PendingIntent rather than being stored, so dismissing
 * the shade without saving leaves the ledger untouched.
 *
 * Never exported: the only thing allowed to send this is the PendingIntent this app handed
 * to the system. An exported receiver here would let any app on the phone relabel the
 * ledger, silently.
 */
class CashOutReplyReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val rowId = intent.getLongExtra(CashOutPrompt.EXTRA_ROW_ID, -1L)
        val label = intent.getStringExtra(CashOutPrompt.EXTRA_LABEL)
        val step = intent.getStringExtra(CashOutPrompt.EXTRA_STEP) ?: CashOutPrompt.STEP_SAVE
        if (rowId <= 0L || label.isNullOrBlank()) {
            Log.w(TAG, "cash-out reply ignored: rowId=$rowId label=$label step=$step")
            return
        }

        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val db = SikaDatabase.get(app)
                val row = db.transactions().byId(rowId)
                if (row == null) {
                    Log.w(TAG, "cash-out reply: row $rowId is gone")
                    CashOutPrompt.cancel(app, rowId)
                    return@launch
                }

                when (step) {
                    // Proposed only. Nothing is written.
                    CashOutPrompt.STEP_PICK ->
                        CashOutPrompt.showConfirm(app, rowId, row.amount, label)

                    // Back to the category list, still without writing.
                    CashOutPrompt.STEP_CHANGE -> CashOutPrompt.show(
                        context = app,
                        rowId = rowId,
                        amount = row.amount,
                        counterparty = row.counterparty,
                        categories = db.categories().all().map { it.name },
                    )

                    // The only branch that touches the ledger.
                    CashOutPrompt.STEP_SAVE -> {
                        // PROMPT, not AUTO_RULE: this is a human answering a question, so a
                        // learned rule must never later overwrite it.
                        db.transactions().setLabel(rowId, label, LabelSource.PROMPT)

                        // ⚠ **Deliberately no learn-once rule here**, unlike the transaction
                        // sheet. A rule is keyed on the counterparty, and a cash-out's
                        // counterparty is the *agent*, not the purchase. Mutalib uses the
                        // same agent for whatever he happens to need cash for, so "the agent
                        // by the junction = Food" would quietly mislabel every future
                        // cash-out from that agent — and worse, it would look like the app
                        // had learned something.
                        CashOutPrompt.cancel(app, rowId)
                        Log.i(TAG, "cash-out row $rowId saved as '$label' from the shade")
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "cash-out reply failed for row $rowId at step $step", t)
            } finally {
                pending.finish()
            }
        }
    }
}
