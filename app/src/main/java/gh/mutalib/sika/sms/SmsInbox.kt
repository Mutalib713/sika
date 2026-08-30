package gh.mutalib.sika.sms

import android.content.ContentResolver
import android.provider.Telephony

/**
 * Reads the phone's SMS store — the shared table underneath your Messages app, reached
 * through a **content provider**, Android's controlled doorway onto another app's data.
 *
 * Sacred Rule 2: only MoMo messages are ever read. That filter is applied **in the query
 * itself**, so other people's messages are never loaded into memory at all, let alone
 * parsed. The one exception is [senderCounts], which reads the `address` column and
 * nothing else — no bodies — because the app has to find out what MoMo's sender ID is
 * before it can filter on it.
 */
/** MTN Ghana's transaction sender ID. Measured from the real inbox, not guessed. */
const val MOMO_SENDER = "MobileMoney"

object SmsInbox {

    /**
     * ⚠ **`MobileMoney`, exactly — measured, not guessed.**
     *
     * This started as three patterns (`%MobileMoney%`, `%MoMo%`, `%MTN%`) because nobody
     * knew what MTN Ghana's transaction sender ID actually was. The first sweep of
     * Mutalib's real inbox on 2026-08-30 settled it: **all 118 parsed transactions came
     * from `MobileMoney`**, and the loose patterns dragged in 308 OTPs, fraud warnings and
     * bundle adverts from MTN's marketing senders.
     *
     * Narrow on purpose. Sacred Rule 2 says only MoMo messages are ever read, and every
     * extra pattern here is another slice of the inbox loaded for no reason.
     */
    private val MOMO_PATTERNS = listOf(MOMO_SENDER)

    /**
     * The single gate both routes go through. [SmsReceiver] calls it on every incoming text,
     * so this is where Sacred Rule 2 is actually enforced for live messages — anything that
     * fails here has its body left unread.
     */
    fun isMomoSender(address: String): Boolean =
        MOMO_PATTERNS.any { it.equals(address, ignoreCase = true) }

    /**
     * Every sender in the inbox with a message count — **addresses only, no bodies.**
     *
     * A diagnostic for exactly one job: finding out what MoMo's sender ID really is on
     * this phone, so [readMomo] can filter on the right thing. Knowing that a sender
     * exists is not reading anyone's messages.
     */
    fun senderCounts(resolver: ContentResolver): List<Pair<String, Int>> {
        val counts = mutableMapOf<String, Int>()
        resolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            arrayOf(Telephony.Sms.ADDRESS),
            null,
            null,
            null,
        )?.use { c ->
            val col = c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            while (c.moveToNext()) {
                val address = c.getString(col) ?: continue
                counts[address] = (counts[address] ?: 0) + 1
            }
        }
        return counts.entries.sortedByDescending { it.value }.map { it.key to it.value }
    }

    /**
     * MoMo messages, newest first.
     *
     * @param sinceExclusive only messages that arrived strictly after this instant. Null
     * reads everything, which is the first-run backfill — the reason months of history
     * appear the day the app is installed.
     */
    fun readMomo(resolver: ContentResolver, sinceExclusive: Long? = null): List<RawSms> {
        val where = buildString {
            append("(")
            append(MOMO_PATTERNS.joinToString(" OR ") { "${Telephony.Sms.ADDRESS} = ?" })
            append(")")
            if (sinceExclusive != null) append(" AND ${Telephony.Sms.DATE} > ?")
        }
        val args = buildList {
            addAll(MOMO_PATTERNS)
            if (sinceExclusive != null) add(sinceExclusive.toString())
        }.toTypedArray()

        val out = mutableListOf<RawSms>()
        resolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
            where,
            args,
            "${Telephony.Sms.DATE} DESC",
        )?.use { c ->
            val addr = c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val body = c.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val date = c.getColumnIndexOrThrow(Telephony.Sms.DATE)
            while (c.moveToNext()) {
                out += RawSms(
                    address = c.getString(addr).orEmpty(),
                    body = c.getString(body).orEmpty(),
                    // ⚠ Sacred Rule 5. This is Android's own record of when the message
                    // arrived, and it is the ONLY date the app trusts. Only one of the
                    // four known MoMo shapes carries a time in its text.
                    receivedAt = c.getLong(date),
                )
            }
        }
        return out
    }
}

/** One message as it sits in the phone's store, before anything tries to understand it. */
data class RawSms(
    val address: String,
    val body: String,
    /** Epoch millis, from Android. Never from the message text. */
    val receivedAt: Long,
)
