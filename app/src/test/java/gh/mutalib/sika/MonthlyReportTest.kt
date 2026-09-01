package gh.mutalib.sika

import gh.mutalib.sika.ledger.Period
import gh.mutalib.sika.ledger.PeriodSummary
import gh.mutalib.sika.ledger.CategorySlice
import gh.mutalib.sika.notify.MonthlyReport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * The half of PLAN task 14 that does not need a phone.
 *
 * `AlarmManager` firing can only be proved on the device. *When* it should fire is arithmetic,
 * and arithmetic that silently drifts by a day a month is exactly the kind of bug that is
 * noticed six months late, so it is pinned down here.
 */
class MonthlyReportTest {

    private val accra: ZoneId = ZoneId.of("Africa/Accra")

    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int = 0): ZonedDateTime =
        ZonedDateTime.of(y, m, d, h, min, 0, 0, accra)

    // ------------------------------------------------------------------ when

    @Test
    fun `mid-month books the 1st of next month`() {
        assertEquals(at(2026, 10, 1, 9), MonthlyReport.nextFire(at(2026, 9, 15, 10, 30)))
    }

    @Test
    fun `early on the 1st books later the same day`() {
        assertEquals(at(2026, 9, 1, 9), MonthlyReport.nextFire(at(2026, 9, 1, 8, 59)))
    }

    @Test
    fun `at exactly 9am on the 1st books next month, not now`() {
        // ⚠ The boundary that matters: this is the instant the firing itself reschedules from.
        // Returning "today at 9" would book an alarm for the present moment and fire forever.
        assertEquals(at(2026, 10, 1, 9), MonthlyReport.nextFire(at(2026, 9, 1, 9)))
    }

    @Test
    fun `later on the 1st books next month`() {
        assertEquals(at(2026, 10, 1, 9), MonthlyReport.nextFire(at(2026, 9, 1, 9, 1)))
    }

    @Test
    fun `December rolls into the new year`() {
        assertEquals(at(2027, 1, 1, 9), MonthlyReport.nextFire(at(2026, 12, 31, 23, 58)))
    }

    @Test
    fun `a year of rescheduling never drifts off the 1st`() {
        // The failure this guards against: rebooking by "add 30 days" instead of "add a month".
        // It looks right for one cycle and is firing on the 28th by December.
        var t = MonthlyReport.nextFire(at(2026, 1, 15, 12))
        repeat(24) {
            assertEquals("must always land on the 1st", 1, t.dayOfMonth)
            assertEquals("must always land at 9am", 9, t.hour)
            assertEquals(0, t.minute)
            val next = MonthlyReport.nextFire(t)
            assertTrue("each firing must book a later one", next.isAfter(t))
            t = next
        }
        // Mid-January 2026 books 1 February 2026; twenty-four more firings is 1 February 2028.
        assertEquals(2028, t.year)
        assertEquals(2, t.monthValue)
    }

    @Test
    fun `the end of a short month still books the 1st`() {
        assertEquals(at(2026, 3, 1, 9), MonthlyReport.nextFire(at(2026, 2, 28, 20)))
    }

    // ------------------------------------------------------------------ the words

    private fun summary(
        out: Long = 64_000L,
        income: Long = 50_000L,
        slices: List<CategorySlice> = emptyList(),
        count: Int = 40,
    ) = PeriodSummary(
        period = Period.monthOf(LocalDate.of(2026, 8, 15)),
        moneyIn = income,
        moneyOut = out,
        closingBalance = 5_171L,
        slices = slices,
        unlabelledCashOut = 0L,
        hasPrevious = true,
        transactionCount = count,
    )

    private fun slice(label: String, amount: Long, previous: Long?, share: Float) =
        CategorySlice(label, amount, share, previous)

    @Test
    fun `the title names the month and both directions`() {
        assertEquals(
            "August: GHS 640.00 out, GHS 500.00 in",
            MonthlyReport.title(summary()),
        )
    }

    @Test
    fun `the detail names the biggest category and how far it moved`() {
        val slices = listOf(
            slice("Food", 24_000L, 17_300L, 0.38f),
            slice("Transport", 9_000L, 8_800L, 0.14f),
            slice("Data", 6_000L, 5_900L, 0.09f),
        )
        assertEquals(
            "Food was your biggest at GHS 240.00, up GHS 67.00 on the month before.",
            MonthlyReport.detail(summary(slices = slices)),
        )
    }

    @Test
    fun `an empty month has nothing to say`() {
        assertNull(MonthlyReport.detail(summary(out = 0, income = 0, count = 0)))
    }

    @Test
    fun `a mostly unlabelled month says so instead of breaking down the rest`() {
        val slices = listOf(
            slice(gh.mutalib.sika.ledger.UNCATEGORISED, 50_000L, 40_000L, 0.78f),
            slice("Food", 14_000L, 10_000L, 0.22f),
        )
        val detail = MonthlyReport.detail(summary(slices = slices))
        assertTrue(
            "should report the gap, not a breakdown: was <$detail>",
            detail!!.contains("no category yet"),
        )
    }

    @Test
    fun `no clear winner means no headline`() {
        // Two categories moved by nearly the same amount. There is no single story, and
        // asserting one anyway is the report making things up.
        val slices = listOf(
            slice("Food", 24_000L, 17_000L, 0.38f),
            slice("Transport", 20_000L, 13_200L, 0.31f),
        )
        assertNull(MonthlyReport.detail(summary(slices = slices)))
    }
}
