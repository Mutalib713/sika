package gh.mutalib.sika.ledger

import gh.mutalib.sika.data.TransactionEntity
import gh.mutalib.sika.data.Reconciled
import gh.mutalib.sika.parser.Direction
import gh.mutalib.sika.parser.Shape
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The arithmetic behind the month report — PLAN task 13, screen 3 of `docs/screens.md`.
 *
 * Pure Kotlin over a plain list, exactly like [Reconciler]: no Android import, no database,
 * so every number here is checkable on the JVM against handmade rows. That matters more for
 * this file than for most, because the report's verification step is *"the numbers must
 * match arithmetic done by hand, not just look plausible."*
 */

/**
 * **What actually left the wallet for one outgoing transaction.**
 *
 * ⚠ **The fee and the tax are part of the spend, and leaving them out is a real error.**
 * [Reconciler] proves it: the balance MoMo states only agrees with
 * `previous − amount − fee − tax`. Money that leaves the wallet but not the total is money
 * the report cannot explain, and this app's whole argument is that it does not do that.
 *
 * This was inconsistent before task 13 — Home summed `amount` for the month and
 * `amount + fee` for today, so two figures on the same card were computed differently.
 * Both now come through here.
 */
fun TransactionEntity.outflow(): Long = amount + fee + (tax ?: 0)

/**
 * What arrived. The fee on an incoming transfer is charged to the sender, so the amount is
 * what lands — but [Reconciler] subtracts any stated fee on the way in too, so if MTN ever
 * does charge the receiver this is the line that needs revisiting.
 */
fun TransactionEntity.inflow(): Long = amount

/** One category's share of a month's spending, and how it moved since the month before. */
data class CategorySlice(
    val label: String,
    val amount: Long,
    /** 0f..1f of the month's total outflow. Zero when the month spent nothing. */
    val share: Float,
    /** The same category last month. **Null means absent, which is not the same as zero.** */
    val previousAmount: Long?,
    /**
     * How much of [amount] came from a gap rather than from a message. Usually zero.
     *
     * ⚠ **This is the condition Mutalib's decision came with, carried in the data rather than
     * left to the screen to remember.** He chose to let remembered money into the totals *and*
     * to have it marked. If this were computed in the UI, the first screen that forgot to ask
     * would quietly show a measured-looking figure that is partly recalled — which is the
     * exact failure the marking exists to prevent. Every consumer of a slice gets it whether
     * it wants it or not.
     */
    val fromBalance: Long = 0L,
) {
    /** True when part of this figure is remembered rather than measured. */
    val hasRemembered: Boolean get() = fromBalance > 0L
    /** Pesewas more (positive) or less (negative) than last month. Null with no comparison. */
    val change: Long? get() = previousAmount?.let { amount - it }

    /**
     * Percent change, rounded. Null when there is nothing to compare against, when last
     * month was zero — "up from nothing" is not a percentage — **or when last month's base
     * was too small for a percentage to mean anything.**
     *
     * ⚠ That last rule is the one that matters here. At roughly 45 transactions a month a
     * small category holds five or six of them, so a single purchase swings it 200–300%.
     * A screen full of dramatic arrows that mostly mean nothing trains you to ignore all of
     * them, including the one that counts. **A missing arrow is honest; a ↑300% arrow
     * caused by one bag of rice is not.**
     *
     * Found in the task-13 research: users abandon these reports over exactly this — the
     * suspicion that the numbers are noise. The amount and the share are still shown, so
     * nothing is hidden; only the misleading ratio is withheld.
     */
    val changePercent: Int? get() {
        val prev = previousAmount ?: return null
        if (prev < MIN_COMPARISON_BASE) return null
        return Math.round((amount - prev) * 100.0 / prev).toInt()
    }
}

/**
 * Below this, last month's figure is too small to carry a percentage — GHS 20.
 *
 * A judgment call, not a measurement: it is set where a single ordinary purchase stops
 * being able to dominate the ratio. Raise it if arrows still look silly on real months.
 */
const val MIN_COMPARISON_BASE = 2_000L

/** Everything screen 3 needs for one stretch of time. */
data class PeriodSummary(
    val period: Period,
    val moneyIn: Long,
    val moneyOut: Long,
    val closingBalance: Long?,
    val slices: List<CategorySlice>,
    /**
     * Of [moneyOut], how much came from gaps someone filed under a category from memory.
     *
     * Zero for anyone who has never explained a gap, which is the overwhelmingly common case
     * and the reason this is a separate figure rather than a flag: a report can say
     * "GHS 480 accounted for, GHS 20 from your balance" only if it knows both numbers.
     */
    val fromBalance: Long = 0L,
    val unlabelledCashOut: Long,
    val hasPrevious: Boolean,
    val transactionCount: Int,
    /** The bar chart: this period cut into days, weeks or months. Never empty. */
    val buckets: List<BucketSpend> = emptyList(),
) {
    /** In minus out. Negative means the month spent more than it took in. */
    val net: Long get() = moneyIn - moneyOut

    val isEmpty: Boolean get() = transactionCount == 0

    /** What share of the month's spending nobody has categorised. 0f..1f. */
    val uncategorisedShare: Float get() =
        slices.firstOrNull { it.label == UNCATEGORISED }?.share ?: 0f

    /**
     * True when so little is labelled that the breakdown cannot answer "where did it go".
     *
     * The screen says so plainly instead of drawing a chart that is one full-width bar
     * labelled Uncategorised — which is what the device showed on 2026-08-31 with nothing
     * labelled. A report that looks informative while saying nothing is worse than one that
     * admits it has nothing to say.
     */
    val tooLittleLabelledToBreakDown: Boolean get() =
        !isEmpty && uncategorisedShare >= 0.6f

    /**
     * The one line worth putting in words: the category that moved most in absolute
     * pesewas. **Absolute, not percent** — a category that went from GHS 2 to GHS 4 is up
     * 100% and means nothing, while Food up GHS 67 is the sentence worth reading.
     *
     * ⚠ **Null unless the top mover clearly beats the second one.** If two categories moved
     * by similar amounts there is no single story, and a screen that asserts one anyway is
     * the report making things up — the precise failure this app exists to avoid. In that
     * case screen 3 says nothing rather than something shaky.
     *
     * Also null on a first month, and null when nothing moved.
     */
    val biggestChange: CategorySlice? get() {
        val movers = slices
            // ⚠ **Uncategorised can never be the story.** It is not a category, it is an
            // absence of information, so "Uncategorised went down GHS 2402" says only that
            // less money moved — dressed up as an insight. Caught on the device on
            // 2026-08-31, where nothing was labelled and the callout confidently reported
            // exactly that.
            .filter { it.label != UNCATEGORISED }
            .filter { it.change != null && it.change != 0L }
            .sortedByDescending { kotlin.math.abs(it.change!!) }
        val top = movers.firstOrNull() ?: return null
        val second = movers.getOrNull(1) ?: return top
        val topSize = kotlin.math.abs(top.change!!)
        val secondSize = kotlin.math.abs(second.change!!)
        return if (topSize >= secondSize * CLEAR_WINNER_RATIO) top else null
    }
}

/**
 * How far ahead the biggest mover must be before it is called *the* story. 1.4× is a
 * judgment call: high enough that two comparable swings stay silent, low enough that a
 * genuine standout still gets its sentence.
 */
private const val CLEAR_WINNER_RATIO = 1.4

/**
 * One bar of the chart.
 *
 * [previous] is the same slot one period earlier — last Wednesday for this Wednesday, week 2
 * of last month for week 2 of this one. It is what the pale bar behind each solid one shows,
 * and it is why that pale bar is worth drawing at all rather than being a plain track.
 */
data class BucketSpend(val label: String, val amount: Long, val previous: Long = 0)

/** The label shown for spending nobody has categorised yet. */
const val UNCATEGORISED = "Uncategorised"

/**
 * Folds the whole ledger into one month's report.
 *
 * Takes every row rather than a pre-filtered month because the comparison against the
 * previous month needs both, and doing the split here keeps the caller from having to know
 * that a "month" is a zone-dependent idea.
 */
fun summarise(all: List<TransactionEntity>, period: Period, zone: ZoneId): PeriodSummary {
    // parsedOk = false rows are review-queue placeholders with every money field zero.
    // They must not reach a total: they exist to be looked at, not counted.
    val real = all.filter { it.parsedOk }
    val before = period.previous()
    val inMonth = real.filter { period.contains(dateOf(it, zone)) }
    val previous = real.filter { before.contains(dateOf(it, zone)) }

    // ⚠ **Remembered money, and the only figures in this file that no message proves.**
    //
    // A gap is money the balance says left with no message to explain it. Mutalib can now file
    // one under a category from memory, and his decision on 2026-09-03 was that it should
    // count — *marked*, never silently. These two maps are how it reaches a total.
    //
    // ⚠ The gap belongs to the row that CAUGHT it, so it lands in whichever period that row
    // falls in. That is a real approximation and worth stating: the hole itself happened
    // somewhere in a window before that row, and near a period boundary it can be attributed
    // to the wrong side. Pinning it to a date nobody knows would be worse — it would look
    // exact. The row that caught it is at least a fact.
    val remembered = inMonth.filter {
        it.reconciled == Reconciled.GAP && it.gapCategory != null && (it.gapAmount ?: 0L) > 0L
    }
    val rememberedByLabel = remembered
        .groupBy { it.gapCategory!! }
        .mapValues { (_, rows) -> rows.sumOf { it.gapAmount ?: 0L } }
    val fromBalance = rememberedByLabel.values.sum()

    val moneyOut = inMonth.filter { it.direction == Direction.OUT }.sumOf { it.outflow() } +
        fromBalance
    val moneyIn = inMonth.filter { it.direction == Direction.IN }.sumOf { it.inflow() }

    val previousByLabel = previous
        .filter { it.direction == Direction.OUT }
        .groupBy { it.label ?: UNCATEGORISED }
        .mapValues { (_, rows) -> rows.sumOf { it.outflow() } }

    val measuredByLabel = inMonth
        .filter { it.direction == Direction.OUT }
        .groupBy { it.label ?: UNCATEGORISED }
        .mapValues { (_, rows) -> rows.sumOf { it.outflow() } }

    // ⚠ The union of both, not just the measured labels. A category whose ONLY spending this
    // period is a remembered gap still has to appear — otherwise explaining a gap files the
    // money somewhere that is not on the screen, which is worse than leaving it out.
    val slices = (measuredByLabel.keys + rememberedByLabel.keys)
        .map { label ->
            val recalled = rememberedByLabel[label] ?: 0L
            val amount = (measuredByLabel[label] ?: 0L) + recalled
            CategorySlice(
                label = label,
                amount = amount,
                // Guarded: a month with no outgoings would divide by zero.
                share = if (moneyOut == 0L) 0f else amount.toFloat() / moneyOut,
                previousAmount = previousByLabel[label],
                fromBalance = recalled,
            )
        }
        .sortedByDescending { it.amount }

    return PeriodSummary(
        period = period,
        moneyIn = moneyIn,
        moneyOut = moneyOut,
        // The newest stated balance in the period, not a figure we computed. Showing our own
        // arithmetic where MoMo's exists would be inventing a number.
        closingBalance = inMonth
            .filter { it.balanceAfter != null }
            .maxByOrNull { it.occurredAt }
            ?.balanceAfter,
        slices = slices,
        fromBalance = fromBalance,
        // The honesty note. Cash taken at an agent and never labelled is money this report
        // genuinely cannot explain, and saying so is the point of screen 3.
        unlabelledCashOut = inMonth
            .filter { it.shape == Shape.CASH_OUT && it.label == null }
            .sumOf { it.outflow() },
        hasPrevious = previous.isNotEmpty(),
        transactionCount = inMonth.size,
        // Empty buckets are kept on purpose: a week with nothing spent on Thursday must
        // still draw a Thursday, or the chart quietly relabels which day was which.
        buckets = run {
            // The previous period is the same length, so its buckets line up one to one —
            // except at a month boundary, where a 4-week month meets a 5-week one. Zipping
            // by index and tolerating a short list is what keeps that from throwing.
            val beforeBuckets = before.buckets()
            period.buckets().mapIndexed { index, bucket ->
                val prior = beforeBuckets.getOrNull(index)
                BucketSpend(
                    label = bucket.label,
                    amount = inMonth
                        .filter { it.direction == Direction.OUT && bucket.contains(dateOf(it, zone)) }
                        .sumOf { it.outflow() },
                    previous = prior?.let { p ->
                        previous
                            .filter { it.direction == Direction.OUT && p.contains(dateOf(it, zone)) }
                            .sumOf { it.outflow() }
                    } ?: 0,
                )
            }
        },
    )
}

private fun dateOf(row: TransactionEntity, zone: ZoneId): LocalDate =
    Instant.ofEpochMilli(row.occurredAt).atZone(zone).toLocalDate()
