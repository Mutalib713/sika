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
import androidx.core.content.ContextCompat
import gh.mutalib.sika.MainActivity
import gh.mutalib.sika.R
import gh.mutalib.sika.TAG
import gh.mutalib.sika.parser.asCedis

/**
 * The cash-out prompt — PLAN task 12, and the answer to Mutalib's own blind spot.
 *
 * **The problem in one line:** a MoMo cash-out says *who* you took money from and never
 * *what you spent it on*, so it is the one transaction the ledger cannot explain by itself.
 * Asking at the moment it happens is the only time the answer is still in your head; a week
 * later "GHS 20 at an agent on the 14th" is unrecoverable.
 *
 * Answering from the shade never opens the app — that is the whole point. One tap in the
 * notification and the row is labelled.
 */
object CashOutPrompt {

    /** One channel, so the prompts can be silenced without silencing the monthly report. */
    const val CHANNEL_ID = "cash_out"

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
            "Cash-out prompts",
            // DEFAULT, not HIGH: this should appear in the shade and be answerable at
            // leisure. A cash-out is not an emergency, and a heads-up banner that covers
            // what you are doing the moment you walk away from an agent would be a reason
            // to turn the whole thing off.
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Asks what a cash-out was for, so it can be categorised."
        }
        ContextCompat.getSystemService(context, NotificationManager::class.java)
            ?.createNotificationChannel(channel)
    }

    /**
     * Shows the prompt for one cash-out row.
     *
     * @param categories the category names in `sortOrder`, as [gh.mutalib.sika.data.CategoryEntity]
     * documents — the first three become the buttons.
     */
    fun show(
        context: Context,
        rowId: Long,
        amount: Long,
        counterparty: String,
        categories: List<String>,
    ) {
        // POST_NOTIFICATIONS is a runtime permission from Android 13. Without this check
        // `notify` throws nothing and does nothing, so the failure would be invisible.
        if (!canPost(context)) {
            Log.w(TAG, "cash-out prompt suppressed: POST_NOTIFICATIONS not granted")
            return
        }
        ensureChannel(context)

        val quick = categories.take(MAX_QUICK_CATEGORIES)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            // asCedis() already writes the "GHS " prefix — do not add a second one.
            .setContentTitle("${amount.asCedis()} cashed out")
            .setContentText("What was it for?")
            .setSubText(counterparty.ifBlank { "MoMo agent" })
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
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
            Log.i(TAG, "cash-out prompt shown for row $rowId (${quick.size} quick answers)")
        } catch (e: SecurityException) {
            Log.w(TAG, "cash-out prompt refused by the system for row $rowId", e)
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
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
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
        try {
            NotificationManagerCompat.from(context).notify(notificationId(rowId), builder.build())
        } catch (e: SecurityException) {
            Log.w(TAG, "cash-out confirm refused by the system for row $rowId", e)
        }
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

    private fun replyIntent(
        context: Context,
        rowId: Long,
        category: String,
        index: Int,
        step: String,
    ): PendingIntent {
        val intent = Intent(context, CashOutReplyReceiver::class.java).apply {
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
            // IMMUTABLE is mandatory from Android 12 and correct anyway: nothing outside
            // this app has any business rewriting which row gets which label.
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
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
