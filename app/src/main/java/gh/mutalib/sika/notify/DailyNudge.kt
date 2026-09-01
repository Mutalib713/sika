package gh.mutalib.sika.notify

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import gh.mutalib.sika.MainActivity
import gh.mutalib.sika.R
import gh.mutalib.sika.TAG
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * The end-of-day nudge — Mutalib's request, 2026-08-31: *"for those transactions without
 * references or the reference is not useful, alert the user at the close of the day, I mean
 * through notification, to do it"*.
 *
 * **Why the end of the day and not the moment it happens.** A payment to a shop is not worth
 * interrupting anyone for — unlike a cash-out, the ledger already knows who got the money,
 * so nothing is lost by waiting. What *is* lost is the memory of what the money was for, and
 * that fades over days, not hours. One quiet summary at the close of play catches everything
 * while it is still recallable and interrupts nothing.
 *
 * ⚠ **It stays silent when there is nothing to ask about**, which is most days once the
 * learn-once rules have warmed up. A daily notification that fires whether or not it has
 * anything to say is one people turn off in a week, and then it is worth nothing.
 */
object DailyNudge {

    const val CHANNEL_ID = "daily_nudge"
    private const val NOTIFICATION_ID = 20_260_901
    const val ACTION_FIRE = "gh.mutalib.sika.DAILY_NUDGE"

    /** 9pm: late enough to have caught the day, early enough not to be a nuisance. */
    val AT: LocalTime = LocalTime.of(21, 0)

    fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "End-of-day reminder",
            // DEFAULT, and unlike the cash-out prompt that is right here: this one has no
            // buttons to hide, so arriving collapsed costs nothing. It is a summary to be
            // read when convenient, not a question to be answered on the spot.
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "A nightly reminder of transactions that still need a category."
        }
        ContextCompat.getSystemService(context, NotificationManager::class.java)
            ?.createNotificationChannel(channel)
    }

    /**
     * Books the next 9pm.
     *
     * ⚠ **Inexact, deliberately, and this is the opposite of PLAN task 14's monthly report.**
     * An exact alarm needs `SCHEDULE_EXACT_ALARM`, which Android 12+ treats as a restricted
     * permission the user can revoke — a heavy price for a reminder where "some time around
     * nine" is entirely good enough. `setAndAllowWhileIdle` still fires through doze, which
     * is the part that actually matters on a phone that sits in a pocket all evening.
     */
    fun schedule(context: Context, zone: ZoneId) {
        val now = ZonedDateTime.now(zone)
        var next = now.with(AT)
        if (!next.isAfter(now)) next = next.plusDays(1)

        val alarms = ContextCompat.getSystemService(context, AlarmManager::class.java) ?: return
        alarms.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            next.toInstant().toEpochMilli(),
            pendingFire(context),
        )
        Log.i(TAG, "daily nudge scheduled for $next")
    }

    fun cancel(context: Context) {
        ContextCompat.getSystemService(context, AlarmManager::class.java)
            ?.cancel(pendingFire(context))
    }

    /**
     * Shows the nudge, or nothing at all.
     *
     * @param count how many of the day's transactions still have no category.
     */
    fun show(context: Context, count: Int) {
        if (count <= 0) {
            Log.i(TAG, "daily nudge: nothing unlabelled today, staying quiet")
            return
        }
        if (!CashOutPrompt.canPost(context)) {
            Log.w(TAG, "daily nudge suppressed: notifications not permitted")
            return
        }
        ensureChannel(context)

        val open = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(
                if (count == 1) "1 transaction today has no category"
                else "$count transactions today have no category",
            )
            // Says what it is for, in his own terms: the report cannot explain money it
            // cannot name, and tonight is while he still remembers.
            .setContentText("Tap to name them while you still remember.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(open)

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
            Log.i(TAG, "daily nudge shown for $count unlabelled")
        } catch (e: SecurityException) {
            Log.w(TAG, "daily nudge refused by the system", e)
        }
    }

    private fun pendingFire(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        NOTIFICATION_ID,
        Intent(context, DailyNudgeReceiver::class.java).setAction(ACTION_FIRE),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
