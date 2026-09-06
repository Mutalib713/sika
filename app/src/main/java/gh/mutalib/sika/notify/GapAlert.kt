package gh.mutalib.sika.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import gh.mutalib.sika.MainActivity
import gh.mutalib.sika.R
import gh.mutalib.sika.TAG
import gh.mutalib.sika.logPrivate
import gh.mutalib.sika.parser.asCedis
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * "Your balance dropped by more than your messages explain."
 *
 * Mutalib asked for this on 2026-09-01: *"add an alert immediately the balance doesn't tally"*.
 * It is the notification the whole reconciliation check earns — Sacred Rule 3 says a money app
 * that cannot check its own arithmetic does not ship, and a check whose result only appears if
 * you happen to open the app is most of the way back to not checking.
 *
 * ⚠ **Only the live route ever fires this, never the inbox sweep.** The sweep re-reads every
 * message on every launch, so alerting from it would post one notification per historic gap —
 * a burst on first run about money that moved months ago. Exactly the rule the cash-out prompt
 * already follows, for exactly the same reason.
 *
 * ⚠ **The alert names a WINDOW, not a day.** The gap is discovered at the message *after* the
 * hole, so that message's date is when it was caught, not when it happened. Saying "missing
 * from 28 August" sends you looking on the wrong day; "between 26 and 28 August" is what is
 * actually known.
 */
object GapAlert {

    const val CHANNEL_ID = "balance_gap"
    const val EXTRA_ROW_ID = "gh.mutalib.sika.GAP_ROW_ID"
    const val KEY_ANSWER = "gh.mutalib.sika.GAP_ANSWER"

    private val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")

    fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Balance doesn't tally",
            // HIGH, and unlike the end-of-day nudge this one earns it. Money left the account
            // with no message to explain it, and the only person who can say what it was is
            // holding the phone — while they still remember. A collapsed notification hides
            // its text box, which is the entire point of this one.
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "When your MoMo balance moves by more than the messages explain."
        }
        ContextCompat.getSystemService(context, NotificationManager::class.java)
            ?.createNotificationChannel(channel)
    }

    /**
     * @param rowId the transaction that revealed the gap — the one *after* the hole.
     * @param difference how much the balance moved beyond what the messages account for.
     * @param sinceMillis the previous transaction's time, which opens the window.
     * @param untilMillis this transaction's time, which closes it.
     */
    fun show(
        context: Context,
        rowId: Long,
        difference: Long,
        sinceMillis: Long?,
        untilMillis: Long,
        zone: ZoneId,
    ) {
        if (!NotificationPrefs.gapAlert(context)) {
            Log.i(TAG, "gap alert: switched off in Settings")
            return
        }
        if (!CategoryPrompt.canPost(context)) {
            Log.w(TAG, "gap alert suppressed: notifications not permitted")
            return
        }
        ensureChannel(context)

        val input = RemoteInput.Builder(KEY_ANSWER)
            .setLabel("What was it?")
            .build()
        val answer = NotificationCompat.Action.Builder(
            0,
            "I know what this was",
            replyIntent(context, rowId),
        )
            .addRemoteInput(input)
            // Without this the system opens the app to collect the text, which defeats the
            // point of asking in the shade while the answer is still in someone's head.
            .setAllowGeneratedReplies(false)
            .build()

        val body = window(sinceMillis, untilMillis, zone) +
            " your balance dropped " + difference.asCedis() +
            " more than your messages explain. MTN probably never sent one."

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(difference.asCedis() + " is unaccounted for")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openRow(context, rowId))
            .addAction(answer)
            .build()

        // Same guard as the cash-out prompt, for the same reason: the permission was checked
        // a few lines above, but it can be revoked in between — the shade is one swipe away
        // while a message is arriving. An uncaught SecurityException here would propagate out
        // of the SMS receiver and lose the transaction, which is far worse than losing an
        // alert about it. The row is already saved by this point either way.
        try {
            NotificationManagerCompat.from(context).notify(notificationId(rowId), notification)
            logPrivate { "gap alert posted for row $rowId, " + difference.asCedis() }
            Log.i(TAG, "gap alert posted for row $rowId")
        } catch (e: SecurityException) {
            Log.w(TAG, "gap alert refused by the system for row $rowId", e)
        }
    }

    /**
     * "Between 26 and 28 Aug", or "Before 28 Aug" when there is no earlier transaction to
     * open the window — which happens when the very first checkable row is already a gap.
     */
    fun window(sinceMillis: Long?, untilMillis: Long, zone: ZoneId): String {
        val until = DAY.format(Instant.ofEpochMilli(untilMillis).atZone(zone))
        if (sinceMillis == null) return "Before $until"
        val since = DAY.format(Instant.ofEpochMilli(sinceMillis).atZone(zone))
        // Two messages on the same day still leave a real hole between them, and "between
        // 28 Aug and 28 Aug" reads as a bug rather than as a narrow window.
        return if (since == until) "On $until," else "Between $since and $until,"
    }

    fun cancel(context: Context, rowId: Long) {
        NotificationManagerCompat.from(context).cancel(notificationId(rowId))
    }

    /** Offset from the cash-out prompt's ids so the two can never cancel each other. */
    fun notificationId(rowId: Long): Int = (rowId + 5_000_000L).toInt()

    private fun replyIntent(context: Context, rowId: Long): PendingIntent = PendingIntent
        .getBroadcast(
            context,
            notificationId(rowId),
            Intent(context, GapReplyReceiver::class.java).putExtra(EXTRA_ROW_ID, rowId),
            // MUTABLE, and it has to be: RemoteInput fills the typed text into this very
            // Intent. An immutable one arrives with nothing in it.
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )

    private fun openRow(context: Context, rowId: Long): PendingIntent = PendingIntent.getActivity(
        context,
        notificationId(rowId),
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
