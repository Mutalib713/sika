package gh.mutalib.sika.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import gh.mutalib.sika.MainActivity
import gh.mutalib.sika.R
import gh.mutalib.sika.TAG
import gh.mutalib.sika.parser.Shape
import gh.mutalib.sika.parser.asCedis

/**
 * *"What was that for?"*, asked in the notification shade the moment money leaves.
 *
 * **The problem in one line:** a MoMo alert says *who* got the money and never *what it was
 * for*, so the ledger cannot explain its own spending. Asking at the moment it happens is the
 * only time the answer is still in your head; a week later "GHS 20 at an agent on the 14th"
 * is unrecoverable.
 *
 * Answering from the shade never opens the app — that is the whole point. One tap in the
 * notification and the row is labelled.
 *
 * ## It used to ask about cash-outs only
 *
 * PLAN task 12 built this for `CASH_OUT` alone, on the argument that a payment to a shop
 * needs no question because the ledger already knows who was paid. **Mutalib reported that as
 * a hole on 2026-09-06** — *"did some transactions the app didnt notify me abt it when there
 * was no category"* — and he was right on two counts. Knowing *who* is not knowing *what
 * for*; and the mechanism meant to close the gap quietly, the learn-once rule, turned out
 * never to run on new transactions at all (see [gh.mutalib.sika.ledger.AutoLabel]).
 *
 * So it now asks about **any outgoing transaction nothing else could name**, and the order
 * matters: auto-labelling runs first, and this only speaks when that came back empty. The
 * quieter the rules get, the less this interrupts — which is the right way round.
 */
object CategoryPrompt {

    /**
     * One channel, so the prompts can be silenced without silencing the monthly report.
     *
     * ⚠ **The `_v2` is load-bearing.** Android ignores every change to a channel that
     * already exists — importance, sound, everything — because those become the user's
     * settings the moment the channel is created. Raising the importance of `cash_out`
     * would have compiled, run, logged success and changed nothing on the phone. A new id
     * is the only way to ship a new default, and the old one is deleted so it does not sit
     * in Settings as a dead entry.
     *
     * ⚠ **The id still says `cash_out` after this stopped being cash-out-only, on purpose.**
     * Broadening the prompt changed no default — the importance is HIGH either way — so a
     * `_v3` would buy nothing and cost something real: a brand-new channel arrives with
     * Android's defaults, discarding whatever Mutalib had already set for this one. The
     * *name* is what a person reads in Settings, and name and description **are** updatable
     * on an existing channel; only importance and sound are frozen. So the label moves and
     * the id does not.
     */
    const val CHANNEL_ID = "cash_out_v2"
    private const val OLD_CHANNEL_ID = "cash_out"

    /** Extras on the reply broadcast. */
    const val EXTRA_ROW_ID = "gh.mutalib.sika.ROW_ID"
    const val EXTRA_LABEL = "gh.mutalib.sika.LABEL"
    const val EXTRA_STEP = "gh.mutalib.sika.STEP"

    /** Tapping a category only *proposes* it. Nothing is written yet. */
    const val STEP_PICK = "pick"
    /** Saving is a second, deliberate tap. This is the one that writes. */
    const val STEP_SAVE = "save"
    /** Back to the category list without writing anything. */
    const val STEP_CHANGE = "change"
    /** A typed description arriving from the notification's own text box. */
    const val STEP_NOTE = "note"

    /** The key the typed text arrives under. */
    const val KEY_NOTE = "gh.mutalib.sika.NOTE_TEXT"

    /**
     * ⚠ **Android draws at most THREE action buttons on a notification.**
     *
     * `docs/screens.md` asked for four — three categories plus a `Choose…` escape. A fourth
     * `addAction` does not error; it is silently dropped, which is the worst kind of failure
     * because it looks fine in code review and is missing on the phone.
     *
     * So the escape moved off the buttons and onto the notification **body**: tapping the
     * text opens the transaction sheet for that row, where the full category list lives.
     * Three quick answers plus a tap-through, which is the spec's intent inside Android's
     * real limit. `docs/screens.md` has been corrected to match.
     */
    private const val MAX_QUICK_CATEGORIES = 3

    fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "What was it for?",
            // ⚠ **HIGH, and the first version being DEFAULT was a real error.**
            //
            // The reasoning for DEFAULT was that a cash-out is not an emergency and a
            // banner covering the screen would be a reason to switch the whole thing off.
            // That was wrong about the mechanism: a DEFAULT notification arrives silent and
            // COLLAPSED, and a collapsed notification does not show its action buttons. In
            // Mutalib's shade, behind a stack of other apps, the prompt was invisible —
            // he tapped the body instead, which opened the app, which is the one outcome
            // this feature exists to avoid.
            //
            // HIGH makes it a heads-up banner with the buttons on it, at the one moment the
            // answer is still in his head.
            //
            // ⚠ **The old note here said "cash-outs are occasional, so this does not nag",
            // and that defence died when the prompt broadened to every payment.** What keeps
            // it honest now is not rarity but silence: nothing is asked about a transaction
            // AutoLabel could name, so a repeat shop is a banner exactly once — the first
            // time — and never again. If it still nags after the rules warm up, the answer
            // is an amount threshold, not a quieter channel: a prompt that arrives collapsed
            // has no buttons, which is the failure this importance was raised to fix.
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Asks what money you just spent was for, so it can be categorised."
        }
        ContextCompat.getSystemService(context, NotificationManager::class.java)?.apply {
            createNotificationChannel(channel)
            // The v1 channel is dead. Left alone it lingers in Settings as a switch that
            // controls nothing.
            deleteNotificationChannel(OLD_CHANNEL_ID)
        }
    }

    /**
     * Shows the prompt for one outgoing row that nothing managed to name.
     *
     * @param shape what kind of transaction it was, which decides the wording. A cash-out
     * and a payment to a shop are not the same question and must not read as though they are.
     * @param categories the category names in `sortOrder`, as [gh.mutalib.sika.data.CategoryEntity]
     * documents — the first three become the buttons.
     */
    fun show(
        context: Context,
        rowId: Long,
        amount: Long,
        shape: Shape,
        counterparty: String,
        categories: List<String>,
    ) {
        // Switched off in Settings. Checked before the permission, because a deliberate "no"
        // is not a failure and should not be logged as one.
        if (!NotificationPrefs.categoryPrompt(context)) {
            Log.i(TAG, "category prompt: switched off in Settings")
            return
        }
        // POST_NOTIFICATIONS is a runtime permission from Android 13. Without this check
        // `notify` throws nothing and does nothing, so the failure would be invisible.
        if (!canPost(context)) {
            Log.w(TAG, "category prompt suppressed: POST_NOTIFICATIONS not granted")
            return
        }
        ensureChannel(context)

        val quick = categories.take(MAX_QUICK_CATEGORIES)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(headline(amount, shape, counterparty))
            .setContentText("What was it for?")
            .setSubText(subText(shape, counterparty))
            // PRIORITY_* is the pre-Android-8 equivalent of channel importance and is what
            // older phones read. Set alongside the channel, not instead of it.
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            // Dismisses itself once answered from the app instead of the shade.
            .setAutoCancel(true)
            .setContentIntent(openSheetIntent(context, rowId))

        quick.forEachIndexed { index, category ->
            builder.addAction(
                NotificationCompat.Action.Builder(
                    // No icon: on Android 7+ the action icon is not drawn for standard
                    // notifications, and passing 0 is the documented way to say so.
                    0,
                    category,
                    replyIntent(context, rowId, category, index, STEP_PICK),
                ).build(),
            )
        }

        // The permission was checked above, but it can be revoked in the gap between that
        // check and this call — the shade is one swipe away while a message is arriving.
        // Catching it is not lint appeasement: an uncaught SecurityException here would
        // propagate out of the SMS receiver and lose the transaction, which is far worse
        // than losing the prompt. The row is already saved by this point either way.
        try {
            NotificationManagerCompat.from(context).notify(notificationId(rowId), builder.build())
            Log.i(TAG, "category prompt shown for row $rowId (${quick.size} quick answers)")
        } catch (e: SecurityException) {
            Log.w(TAG, "category prompt refused by the system for row $rowId", e)
        }
    }

    /**
     * Step two: a category has been proposed, and nothing has been written yet.
     *
     * ⚠ **Mutalib asked for this on 2026-08-31** — "when I select food let me click okay or
     * something before I can leave that popup and save it". He is right, and the reason is
     * sharper than convenience: the buttons sit in the notification shade, where a thumb is
     * already swiping past. A single mis-tap silently relabelled a transaction with no undo
     * and no visible trace, on the one screen where you are least likely to be looking.
     *
     * The proposal is held in the notification itself rather than in the database, so
     * dismissing the shade without saving leaves the ledger exactly as it was.
     */
    fun showConfirm(context: Context, rowId: Long, amount: Long, category: String) {
        if (!canPost(context)) return
        ensureChannel(context)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("${amount.asCedis()} → $category")
            .setContentText("Save it?")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            // ⚠ NOT auto-cancel: the whole point is that it waits for a deliberate answer.
            .setAutoCancel(false)
            .setContentIntent(openSheetIntent(context, rowId))
            .addAction(
                NotificationCompat.Action.Builder(
                    0, "Save", replyIntent(context, rowId, category, 0, STEP_SAVE),
                ).build(),
            )
            .addAction(
                NotificationCompat.Action.Builder(
                    0, "Change", replyIntent(context, rowId, category, 1, STEP_CHANGE),
                ).build(),
            )
            .addAction(noteAction(context, rowId, category))
        try {
            NotificationManagerCompat.from(context).notify(notificationId(rowId), builder.build())
        } catch (e: SecurityException) {
            Log.w(TAG, "category confirm refused by the system for row $rowId", e)
        }
    }

    /**
     * A text box inside the notification, for saying what the money actually went on.
     *
     * ⚠ **This is the one-off case Mutalib asked for on 2026-09-01**, and it is deliberately
     * not another category. The buttons above file the cash-out under something he spends on
     * repeatedly; this is for the laptop repair that will never happen again. Inventing a
     * category for it would leave an entry in the breakdown forever holding a single row.
     *
     * `RemoteInput` is what lets the shade take typed text without opening the app, which is
     * the whole reason the prompt exists — the answer is worth having precisely because it
     * costs nothing to give.
     */
    private fun noteAction(context: Context, rowId: Long, category: String): NotificationCompat.Action {
        val input = RemoteInput.Builder(KEY_NOTE)
            .setLabel("What was it for?")
            .build()
        return NotificationCompat.Action.Builder(
            0,
            "Say what it was",
            replyIntent(context, rowId, category, 2, STEP_NOTE, mutable = true),
        )
            .addRemoteInput(input)
            // Without this the system opens the app to collect the text, which defeats the
            // point of asking in the shade.
            .setAllowGeneratedReplies(false)
            .build()
    }

    /**
     * The line at the top of the notification: how much, and where it went.
     *
     * ⚠ **A cash-out gets different words, and that is not decoration.** Every other shape
     * names a recipient worth reading — *GHS 12.00 to MELCOM* tells you something. A
     * cash-out's counterparty is the agent who handed over the notes, which tells you nothing
     * about the spending and would read as though the agent were the shop. So the cash-out
     * says what happened and leaves the agent to [subText].
     *
     * `asCedis()` already writes the "GHS " prefix — do not add a second one.
     *
     * Internal rather than private so a unit test can hold the wording still. The strings are
     * the whole feature here: this notification is the only thing many rows will ever be
     * judged from.
     */
    internal fun headline(amount: Long, shape: Shape, counterparty: String): String = when {
        shape == Shape.CASH_OUT -> "${amount.asCedis()} cashed out"
        counterparty.isNotBlank() -> "${amount.asCedis()} to $counterparty"
        // No recipient in the message at all. Rare, but "GHS 5.00 to " would look broken.
        else -> "${amount.asCedis()} spent"
    }

    /**
     * The small grey line: what kind of transaction this was, in ordinary words.
     *
     * It exists so the question is answerable without opening anything. "GHS 5.00 to MTN"
     * could be airtime, a bundle or a bill; "Airtime or bundle" underneath settles it.
     */
    internal fun subText(shape: Shape, counterparty: String): String = when (shape) {
        // The agent, which the headline deliberately left out. Named here because "who you
        // took it from" is still the one clue to which cash-out this was.
        Shape.CASH_OUT -> counterparty.ifBlank { "MoMo agent" }
        Shape.BILL_AIRTIME -> "Airtime or bundle"
        Shape.MERCHANT_PAY -> "Paid at a till"
        Shape.TRANSFER -> "Transfer"
        // PAYMENT_MADE and PAYMENT_FOR are the same act with two MTN wordings, and the
        // difference between them is not something worth putting in front of anyone.
        else -> "MoMo payment"
    }

    fun cancel(context: Context, rowId: Long) {
        NotificationManagerCompat.from(context).cancel(notificationId(rowId))
    }

    /**
     * Whether a notification posted right now would actually appear.
     *
     * Two separate things have to be true, and conflating them is a real bug:
     *
     * 1. **Notifications are enabled for the app at all.** The user can switch them off in
     *    Settings on every Android version, and that is invisible to the permission check.
     * 2. **POST_NOTIFICATIONS is granted — but only from Android 13.**
     *    ⚠ `minSdk` here is 31. On Android 12 that permission does not exist, and asking
     *    `checkSelfPermission` about it answers DENIED — which would have silenced the
     *    prompt on a phone where it needed no permission and would have worked perfectly.
     */
    fun canPost(context: Context): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    /**
     * The notification's id, so answering cancels the right one and a re-shown prompt
     * replaces rather than stacks.
     *
     * Row ids start at 1 and this app will never see billions of rows, so the narrowing to
     * Int is safe — but it is a narrowing, so it is stated rather than assumed.
     */
    fun notificationId(rowId: Long): Int = rowId.toInt()

    /**
     * @param mutable **must be true for the note action and false for everything else.**
     *
     * ⚠ **This parameter exists because of a shipped bug.** `RemoteInput` delivers what was
     * typed by writing it *into* this very Intent — which `FLAG_IMMUTABLE` forbids. The note
     * action was built with the same immutable intent as the three category buttons, so
     * `RemoteInput.getResultsFromIntent` came back null and the receiver logged the answer as
     * "ignored". Typing into the shade did nothing at all.
     *
     * It failed silently and in the one place hardest to notice: the buttons worked, the box
     * appeared, the text sent, and the label simply never changed. Found on 2026-09-01 while
     * gathering evidence for PLAN task 17, not by using the app.
     *
     * ⚠ **Mutable is granted to exactly one action, never as a blanket.** An immutable intent
     * is the right default here — nothing outside this app has any business rewriting which
     * row gets which label — so the three category buttons keep it.
     */
    private fun replyIntent(
        context: Context,
        rowId: Long,
        category: String,
        index: Int,
        step: String,
        mutable: Boolean = false,
    ): PendingIntent {
        val intent = Intent(context, CategoryReplyReceiver::class.java).apply {
            putExtra(EXTRA_ROW_ID, rowId)
            putExtra(EXTRA_LABEL, category)
            putExtra(EXTRA_STEP, step)
            // ⚠ The step is part of the *action*, not just an extra. PendingIntent matching
            // ignores extras, so two intents differing only by step would collapse into one
            // and "Save" would deliver whatever "Change" registered first.
            action = step
        }
        return PendingIntent.getBroadcast(
            context,
            // ⚠ **The request code must differ per button.** PendingIntents are matched on
            // requestCode + Intent *without* comparing extras, so three actions sharing a
            // code would all deliver whichever set of extras was registered first — every
            // button labelling the row "Food".
            requestCode(rowId, index),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                if (mutable) PendingIntent.FLAG_MUTABLE else PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** The escape hatch: open the app on this row, where every category is available. */
    private fun openSheetIntent(context: Context, rowId: Long): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra(EXTRA_ROW_ID, rowId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            requestCode(rowId, MAX_QUICK_CATEGORIES),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    /**
     * Internal rather than private so a unit test can prove the codes are distinct. The
     * bug this guards is invisible on inspection and obvious on a phone: every button
     * writing the same label.
     */
    internal fun requestCode(rowId: Long, slot: Int): Int = rowId.toInt() * 31 + slot
}
