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
import gh.mutalib.sika.logPrivate
import gh.mutalib.sika.data.TermEntity
import gh.mutalib.sika.parser.asCedis
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/**
 * Tells you a semester is nearly over, and then that it is over.
 *
 * Mutalib asked for this on 2026-09-03, in the same breath as naming semesters: *"and if that
 * semester is abt to end and ends notify the user"*. Two moments, not one — a warning while
 * there is still time to act on it, and a full stop when the stretch he has been measuring
 * himself against is finished.
 *
 * ### Why a daily check rather than an alarm per semester
 *
 * ⚠ **One alarm, fired every morning, that asks the database a question.** The obvious design
 * is an alarm booked at each term's end date, and it is wrong here: a term is a row he can
 * edit, delete or re-date from Settings at any time, so every one of those alarms would need
 * cancelling and rebooking on every edit, and a missed cancel leaves an alarm for a semester
 * that no longer exists. A daily check has one alarm, holds no state about which terms exist,
 * and is correct the morning after any edit.
 *
 * ### Why it is not stateless
 *
 * ⚠ **[due] takes the set of things already said, because "fire on exactly the right day" is
 * a promise a phone cannot keep.** A phone that is off, flat, or in a drawer on the one day
 * the condition is true would miss the alert entirely and never mention it again. So the
 * windows are deliberately wide — anywhere in the last week — and what stops it repeating for
 * seven days running is a record of what has already been said, not the narrowness of the day.
 *
 * ⚠ **The "ended" window is a week, not forever.** Without an upper bound, adding Sika to a
 * phone with three old semesters already in it would announce the end of all three on the
 * first morning — news about stretches of time that finished months ago.
 */
object TermAlert {

    const val CHANNEL_ID = "term_end"
    const val ACTION_FIRE = "gh.mutalib.sika.TERM_ALERT"
    const val NOTIFICATION_ID = 20_260_904

    /** How many days before the last day the warning may start. */
    const val WARN_DAYS = 7L

    /** How long after a term ends the "it ended" notice is still worth posting. */
    private const val STALE_DAYS = 7L

    /**
     * 9am, the same hour the monthly summary uses and for the same reason: this is news to
     * read with the morning, not a question to answer at the end of a day.
     */
    val AT: LocalTime = LocalTime.of(9, 0)

    enum class Kind {
        /** The last day is a week away or nearer. */
        ENDING_SOON,

        /** The last day has passed. */
        ENDED,
    }

    data class Due(val term: TermEntity, val kind: Kind, val daysLeft: Long) {
        /** Stable across reboots and reinstalls, because it is built from the row's own id. */
        val key: String get() = "${term.id}:$kind"
    }

    /**
     * What, if anything, is worth saying this morning.
     *
     * ⚠ **`endExclusiveDay` is the day AFTER the term**, so the last day is one back and
     * `daysLeft` is 0 on the last day itself. Getting this off by one would announce every
     * semester's end a day early, forever, and look like a rounding bug in the dates rather
     * than a bug here.
     *
     * ⚠ **ENDED wins when both apply.** On the morning after the last day a term is both
     * "ending within the week" and "ended"; the definite one is the one worth saying.
     *
     * Returns null when there is nothing to say, which is almost every morning.
     */
    fun due(
        terms: List<TermEntity>,
        today: LocalDate,
        alreadyTold: Set<String> = emptySet(),
    ): Due? {
        val candidates = terms.mapNotNull { term ->
            val lastDay = term.endExclusive.minusDays(1)
            val daysLeft = ChronoUnit.DAYS.between(today, lastDay)
            when {
                // Ended, but recently enough to still be news.
                daysLeft < 0 && daysLeft >= -STALE_DAYS -> Due(term, Kind.ENDED, daysLeft)
                daysLeft in 0..WARN_DAYS -> Due(term, Kind.ENDING_SOON, daysLeft)
                else -> null
            }
        }.filter { it.key !in alreadyTold }
        // ENDED first, then whichever ends soonest — a phone that was off for a fortnight
        // should hear the full stop before the warning.
        return candidates.minWithOrNull(
            compareBy({ if (it.kind == Kind.ENDED) 0 else 1 }, { it.daysLeft }),
        )
    }

    /**
     * The headline.
     *
     * ⚠ **Counts in days, and says "tomorrow" and "today" as words.** "Ends in 1 days" is the
     * plural bug that reached the Settings screen on 2026-09-02; "ends in 0 days" is worse,
     * because it is not wrong so much as not English.
     */
    fun title(due: Due): String = when {
        due.kind == Kind.ENDED -> "${due.term.name} has ended"
        due.daysLeft == 0L -> "${due.term.name} ends today"
        due.daysLeft == 1L -> "${due.term.name} ends tomorrow"
        else -> "${due.term.name} ends in ${due.daysLeft} days"
    }

    /**
     * The second line: what the semester cost, when that is known.
     *
     * ⚠ **Null when nothing was spent in it**, rather than "GHS 0.00". A semester with no
     * transactions in it is one Sika was not installed for, or one whose dates are wrong —
     * and quoting zero at someone as if it were a finding is the invented-headline failure
     * the monthly report already refuses to make.
     */
    fun detail(due: Due, spent: Long): String? {
        if (spent <= 0L) return null
        val total = spent.asCedis()
        return when (due.kind) {
            Kind.ENDED -> "You spent $total over it. Tap to look back at the whole semester."
            Kind.ENDING_SOON -> "$total so far. Tap to see where it went."
        }
    }

    // ------------------------------------------------------------------ the plumbing

    fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Semester ending",
            // DEFAULT, not HIGH. Nothing here needs answering while you remember it — the
            // reason the cash-out prompt earns HIGH — and a semester ending is not an
            // emergency even when it is a week away.
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "A week before a semester ends, and again once it has."
        }
        ContextCompat.getSystemService(context, NotificationManager::class.java)
            ?.createNotificationChannel(channel)
    }

    /** When the next check is due: 9am, today if it has not passed, otherwise tomorrow. */
    fun nextFire(now: ZonedDateTime): ZonedDateTime {
        val todayAt = now.with(AT).withSecond(0).withNano(0)
        return if (todayAt.isAfter(now)) todayAt else todayAt.plusDays(1)
    }

    /**
     * Books tomorrow morning's check.
     *
     * Inexact for the same reason as [MonthlyReport] and [DailyNudge]: `SCHEDULE_EXACT_ALARM`
     * is restricted from Android 12 and revocable, and "some time on the morning of" is
     * entirely good enough for news about a date. `setAndAllowWhileIdle` still fires through
     * doze, which is the part that matters on a phone asleep in a pocket.
     */
    fun schedule(context: Context, zone: ZoneId) {
        val next = nextFire(ZonedDateTime.now(zone))
        val alarms = ContextCompat.getSystemService(context, AlarmManager::class.java) ?: return
        alarms.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            next.toInstant().toEpochMilli(),
            pendingFire(context),
        )
        Log.i(TAG, "term check scheduled for $next")
    }

    fun cancel(context: Context) {
        ContextCompat.getSystemService(context, AlarmManager::class.java)
            ?.cancel(pendingFire(context))
    }

    /** Posts one alert. Returns true when it actually went out, so the caller can record it. */
    fun show(context: Context, due: Due, spent: Long): Boolean {
        if (!NotificationPrefs.termEnd(context)) {
            Log.i(TAG, "term alert: switched off in Settings")
            return false
        }
        if (!CategoryPrompt.canPost(context)) {
            Log.w(TAG, "term alert suppressed: notifications not permitted")
            return false
        }
        ensureChannel(context)

        val body = detail(due, spent)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title(due))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(openApp(context))
        if (body != null) {
            builder.setContentText(body).setStyle(NotificationCompat.BigTextStyle().bigText(body))
        }

        // Same guard as every other notification here: the permission can be revoked between
        // the check above and this call, and an uncaught SecurityException inside a
        // BroadcastReceiver would take the reschedule down with it.
        return try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
            Log.i(TAG, "term alert posted")
            logPrivate { "term alert: " + title(due) }
            true
        } catch (e: SecurityException) {
            Log.w(TAG, "term alert refused by the system", e)
            false
        }
    }

    private fun pendingFire(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        NOTIFICATION_ID,
        Intent(context, TermAlertReceiver::class.java).setAction(ACTION_FIRE),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        NOTIFICATION_ID,
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
