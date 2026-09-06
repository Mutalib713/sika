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
import gh.mutalib.sika.data.SikaDatabase
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * The end-of-day nudge — Mutalib's request, 2026-08-31: *"for those transactions without
 * references or the reference is not useful, alert the user at the close of the day, I mean
 * through notification, to do it"*.
 *
 * ⚠ **This used to be the ONLY thing that chased an ordinary payment, and that was the bug.**
 * The original argument was that a payment to a shop is not worth interrupting for, because
 * the ledger already knows who got the money — so everything except a cash-out was left to
 * this one nightly line. Mutalib reported the result on 2026-09-06: transactions with no
 * category, and nothing ever said so. [CategoryPrompt] now asks at the moment money leaves.
 *
 * **So what is this still for?** The catch-up. Android can skip the live receiver under doze
 * or a battery saver, the phone can be off, and the prompt can be dismissed with a swipe on
 * the way to something else. The sweep quietly picks those rows up on the next launch and
 * nothing asks about them, because asking hours later in a burst is worse than not asking.
 * One line at 9pm is what covers that gap.
 *
 * ⚠ **It stays silent when there is nothing to ask about**, which should now be most days —
 * the prompt catches things first, and the learn-once rules name the repeats. A daily
 * notification that fires whether or not it has anything to say is one people turn off in a
 * week, and then it is worth nothing.
 */
object DailyNudge {

    /**
     * ⚠ **The `_v2` is load-bearing, for the same reason it was on the category prompt.**
     * Android ignores every change to a channel that already exists — importance, sound,
     * everything — because those become the user's settings the moment it is created.
     * Raising the importance of `daily_nudge` would compile, run, log success and change
     * nothing on the phone. A new id is the only way to ship a new default, and the old one
     * is deleted so it does not linger in Settings as a switch that controls nothing.
     */
    const val CHANNEL_ID = "daily_nudge_v2"
    private const val OLD_CHANNEL_ID = "daily_nudge"
    private const val NOTIFICATION_ID = 20_260_901
    const val ACTION_FIRE = "gh.mutalib.sika.DAILY_NUDGE"

    /** 9pm: late enough to have caught the day, early enough not to be a nuisance. */
    val AT: LocalTime = LocalTime.of(21, 0)

    fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "End-of-day reminder",
            // ⚠ **HIGH since 2026-09-06, and DEFAULT was a real error — the SAME error
            // already made and fixed on the category prompt, never carried across to here.**
            //
            // The old note said this one has no buttons to hide, so arriving collapsed costs
            // nothing. That was wrong about what collapsed means in practice. A DEFAULT
            // notification arrives silent, and at 9pm it lands in a shade already holding
            // WhatsApp, Muslim Pro and Google Tasks, then gets cleared with everything else.
            //
            // Mutalib reported he had NEVER seen it. Every mechanical cause was ruled out on
            // his own phone: the alarm was registered for 21:00 with no standby restriction,
            // POST_NOTIFICATIONS was granted, the switch was on, the channel existed and was
            // unblocked, and the count was above zero on 16 of the last 21 days. It had been
            // firing the whole time. Firing a notification nobody ever sees is not a
            // reminder; it is a log entry.
            //
            // The nagging objection does not apply any more either. [CategoryPrompt] now
            // catches unnamed spending as it happens, so this only speaks about what slipped
            // through — which should be rare, and is worth a banner exactly because it is.
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "A nightly reminder of transactions that still need a category."
        }
        ContextCompat.getSystemService(context, NotificationManager::class.java)?.apply {
            createNotificationChannel(channel)
            // The v1 channel is dead. Left alone it sits in Settings controlling nothing.
            deleteNotificationChannel(OLD_CHANNEL_ID)
        }
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
     * Counts today and shows the reminder — the whole of what 9pm does, in one call.
     *
     * ⚠ **This exists so the alarm and the Settings test button cannot drift.** The counting
     * used to live inside [DailyNudgeReceiver], which meant any hand-test could only ever
     * re-implement it — and a test that re-implements the thing it is testing proves nothing.
     * Both callers now run these exact lines.
     *
     * @return how many rows it found, so a caller can say what happened even when the
     * notification stayed quiet.
     */
    suspend fun fireNow(context: Context, zone: ZoneId): Int {
        val today = LocalDate.now(zone)
        // Half-open, like every other range in this app: a transaction at exactly midnight
        // belongs to one day, not to both.
        val from = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val to = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

        val count = SikaDatabase.get(context).transactions().countUnlabelledBetween(from, to)
        show(context, count)
        return count
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
        if (!NotificationPrefs.endOfDay(context)) {
            Log.i(TAG, "daily nudge: switched off in Settings")
            return
        }
        if (!CategoryPrompt.canPost(context)) {
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
            // Matches the channel. PRIORITY_* is what pre-Android-8 phones read; set
            // alongside the channel importance, never instead of it.
            .setPriority(NotificationCompat.PRIORITY_HIGH)
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
