package gh.mutalib.sika.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.RemoteInput
import gh.mutalib.sika.TAG
import gh.mutalib.sika.logPrivate
import gh.mutalib.sika.data.LabelSource
import gh.mutalib.sika.data.SikaDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Handles the category prompt's buttons.
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
class CategoryReplyReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val rowId = intent.getLongExtra(CategoryPrompt.EXTRA_ROW_ID, -1L)
        val label = intent.getStringExtra(CategoryPrompt.EXTRA_LABEL)
        val step = intent.getStringExtra(CategoryPrompt.EXTRA_STEP) ?: CategoryPrompt.STEP_SAVE
        if (rowId <= 0L || label.isNullOrBlank()) {
            Log.w(TAG, "category reply ignored: rowId=$rowId label=$label step=$step")
            return
        }

        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val db = SikaDatabase.get(app)
                val row = db.transactions().byId(rowId)
                if (row == null) {
                    Log.w(TAG, "category reply: row $rowId is gone")
                    CategoryPrompt.cancel(app, rowId)
                    return@launch
                }

                when (step) {
                    // Typed in the shade. Saves the words AND the proposed category, since
                    // someone who bothered to describe it has clearly decided.
                    CategoryPrompt.STEP_NOTE -> {
                        val typed = RemoteInput.getResultsFromIntent(intent)
                            ?.getCharSequence(CategoryPrompt.KEY_NOTE)
                            ?.toString()
                            ?.trim()
                            ?.take(60)
                        db.transactions().setNote(rowId, typed?.takeIf { it.isNotEmpty() })
                        db.transactions().setLabel(rowId, label, LabelSource.PROMPT)
                        CategoryPrompt.cancel(app, rowId)
                        Log.i(TAG, "row $rowId noted from the shade")
                    }

                    // Proposed only. Nothing is written.
                    CategoryPrompt.STEP_PICK ->
                        CategoryPrompt.showConfirm(app, rowId, row.amount, label)

                    // Back to the category list, still without writing.
                    CategoryPrompt.STEP_CHANGE -> CategoryPrompt.show(
                        context = app,
                        rowId = rowId,
                        amount = row.amount,
                        // ⚠ The row's OWN shape, read back from the ledger. Re-showing with
                        // a hardcoded shape would word the second prompt differently from the
                        // first for the same transaction — "Change" is meant to return you to
                        // where you were, not to a slightly different question.
                        shape = row.shape,
                        counterparty = row.counterparty,
                        categories = db.categories().visible().map { it.name },
                    )

                    // The only branch that touches the ledger.
                    CategoryPrompt.STEP_SAVE -> {
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
                        //
                        // ⚠ **That reasoning covers cash-outs and only cash-outs, and since
                        // 2026-09-06 this prompt also answers ordinary payments — where a
                        // rule would be exactly right.** MELCOM is MELCOM every time. Left
                        // as-is on purpose rather than by oversight: writing a rule from the
                        // shade would teach the app something Mutalib never saw a switch for,
                        // and the transaction sheet asks him with a visible toggle. The cost
                        // is that the same shop keeps being asked about until he answers it
                        // once inside the app. Open question, his to settle — do not "fix"
                        // it by quietly generalising from a shade tap.
                        CategoryPrompt.cancel(app, rowId)
                        // The row id is enough to follow the flow; the label is what he spent
                    // the money on.
                    Log.i(TAG, "row $rowId saved from the shade")
                    logPrivate { "row $rowId saved as '$label'" }
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "category reply failed for row $rowId at step $step", t)
            } finally {
                pending.finish()
            }
        }
    }
}
