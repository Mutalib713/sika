package gh.mutalib.sika.sms

import gh.mutalib.sika.data.LabelSource
import gh.mutalib.sika.data.Reconciled
import gh.mutalib.sika.data.TransactionEntity
import gh.mutalib.sika.parser.Direction
import gh.mutalib.sika.parser.Shape
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Fabricated transactions for looking at a full screen. **Debug builds only.**
 *
 * ⚠ These never reach the database. They are handed to [gh.mutalib.sika.data.DemoMode] and
 * read in place of the ledger, so no fake money can survive the process — let alone survive
 * into a report that claims its arithmetic checks out.
 *
 * The shape of the data is deliberate: every category appears at least once, there are
 * money-in and money-out rows, one uncategorised cash-out so the honesty note has something
 * to say, and spending spread across the week so the bar chart has a real profile rather
 * than one spike.
 */
object DemoRows {

    private val ACCRA: ZoneId = ZoneId.of("Africa/Accra")

    fun build(today: java.time.LocalDate): List<TransactionEntity> {
        var id = 1L
        var balance = 120_000L
        val out = mutableListOf<TransactionEntity>()

        fun add(
            daysAgo: Long,
            hour: Int,
            minute: Int,
            who: String,
            amount: Long,
            label: String?,
            direction: Direction = Direction.OUT,
            shape: Shape = Shape.PAYMENT_FOR,
            fee: Long = 0,
        ) {
            balance = if (direction == Direction.OUT) balance - amount - fee else balance + amount
            out += TransactionEntity(
                id = id,
                txId = "demo-$id",
                occurredAt = LocalDateTime.of(today.minusDays(daysAgo), java.time.LocalTime.of(hour, minute))
                    .atZone(ACCRA).toInstant().toEpochMilli(),
                direction = direction,
                shape = shape,
                amount = amount,
                fee = fee,
                tax = null,
                counterparty = who,
                reference = null,
                balanceAfter = balance,
                label = label,
                labelSource = if (label == null) LabelSource.NONE else LabelSource.MANUAL,
                rawBody = "demo",
                parsedOk = true,
                reconciled = Reconciled.OK,
            )
            id++
        }

        // Today
        add(0, 21, 0, "MTN AIRTIME", 1_000, "Airtime", shape = Shape.BILL_AIRTIME)
        add(0, 16, 12, "Aaa", 10_000, "Sent home", direction = Direction.IN, shape = Shape.PAYMENT_RECEIVED)
        add(0, 12, 40, "AUNTIE WAAKYE JOINT", 1_500, "Food")

        // Yesterday
        // ⚠ A REAL person's name was here until 2026-09-06, copied off the phone while
        // building this list. Demo rows are the one thing that gets screenshotted and put
        // in a public README, so a real counterparty here is the shortest path from "my
        // ledger" to "the internet". Every name in this file must be invented.
        add(1, 20, 5, "AKOSUA MENSAH", 13_500, "Food")
        add(1, 13, 20, "CASH OUT AGENT", 4_000, null, shape = Shape.CASH_OUT, fee = 50)
        add(1, 11, 2, "ZZZ PROVISIONS", 800, "Provisions")

        // Two days ago
        add(2, 19, 40, "MTN DATA BUNDLE", 2_000, "Data", shape = Shape.BILL_AIRTIME)
        add(2, 15, 15, "TROTRO STATION", 600, "Transport")
        add(2, 9, 30, "CAMPUS PRINTING", 1_200, "Printing")

        // Three days ago
        add(3, 18, 0, "KWAME CHOP BAR", 2_500, "Food")
        add(3, 14, 45, "BOLT GHANA", 1_800, "Transport")

        // Four days ago
        add(4, 17, 22, "HOSTEL MANAGER", 25_000, "Rent")
        add(4, 10, 5, "PHARMACY", 900, "Other")

        // Five and six days ago
        add(5, 19, 10, "MAAME FOOD", 1_700, "Food")
        add(5, 12, 0, "YANGO", 900, "Transport")
        add(6, 16, 30, "MTN AIRTIME", 500, "Airtime", shape = Shape.BILL_AIRTIME)
        add(6, 8, 45, "SISTER ABENA", 5_000, "Sent home")

        // Earlier in the month, so the month card and the report have depth
        add(9, 13, 0, "UNIVERSITY BOOKSHOP", 3_400, "Printing")
        add(12, 18, 20, "KWAME CHOP BAR", 2_200, "Food")
        add(15, 11, 11, "MTN DATA BUNDLE", 2_000, "Data", shape = Shape.BILL_AIRTIME)
        add(18, 9, 0, "CASH OUT AGENT", 6_000, null, shape = Shape.CASH_OUT, fee = 50)
        add(21, 15, 40, "SUPERMARKET", 4_600, "Provisions")

        return out.sortedByDescending { it.occurredAt }
    }
}
