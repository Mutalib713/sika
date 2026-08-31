package gh.mutalib.sika.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import gh.mutalib.sika.parser.Direction
import gh.mutalib.sika.parser.MomoParser
import gh.mutalib.sika.parser.ParseResult
import gh.mutalib.sika.parser.Shape
import gh.mutalib.sika.sms.SmsIngest
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs on the Pixel against real SQLite — `./gradlew connectedDebugAndroidTest`.
 *
 * Deliberately not a JVM test with a fake. The claim being made here is that the *database
 * engine* refuses a duplicate, and a claim about SQLite verified without SQLite is not a
 * claim about anything.
 */
@RunWith(AndroidJUnit4::class)
class LedgerDaoTest {

    private lateinit var db: SikaDatabase
    private lateinit var transactions: TransactionDao
    private lateinit var rules: RuleDao
    private lateinit var categories: CategoryDao

    @Before
    fun open() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        // In-memory: real SQLite, thrown away after each test, so tests cannot leak into
        // one another or touch the real ledger on the phone.
        db = Room.inMemoryDatabaseBuilder(ctx, SikaDatabase::class.java).build()
        transactions = db.transactions()
        rules = db.rules()
        categories = db.categories()
    }

    @After
    fun close() = db.close()

    // ------------------------------------------------------------------ the dedupe claim

    @Test
    fun sameTxIdInsertedThreeTimesLeavesOneRow() = runTest {
        // PLAN task 4's stated verification. The inbox sweep re-reads every message on
        // every launch, so this has to hold however many times it runs.
        repeat(3) { transactions.insert(airtimeRow()) }

        assertEquals(1, transactions.count())
    }

    @Test
    fun insertReportsWhichWereGenuinelyNew() = runTest {
        // -1 means "already had it". The sweep uses this to say "209 found, 4 new" rather
        // than claiming it imported everything again.
        val first = transactions.insert(airtimeRow())
        val second = transactions.insert(airtimeRow())

        assertTrue("first insert should return a rowId, got $first", first > 0)
        assertEquals(-1L, second)
    }

    @Test
    fun reInsertingASeenTransactionDoesNotDestroyItsLabel() = runTest {
        // ⚠ **The test that matters most in this file.**
        //
        // OnConflictStrategy.REPLACE is the obvious choice and it would delete the row and
        // insert a fresh one — wiping the label, the cash-out answer and the reconciliation
        // result on every single launch, silently. IGNORE keeps what is already there.
        val id = transactions.insert(airtimeRow())
        transactions.setLabel(id, "Airtime", LabelSource.MANUAL)

        repeat(3) { transactions.insert(airtimeRow()) }

        val row = transactions.byTxId("83077174642")
        assertNotNull(row)
        assertEquals("Airtime", row!!.label)
        assertEquals(LabelSource.MANUAL, row.labelSource)
        assertEquals(1, transactions.count())
    }

    @Test
    fun differentTransactionsBothSurvive() = runTest {
        // The guard must be about the id, not about refusing similar-looking rows.
        transactions.insert(airtimeRow())
        transactions.insert(cashOutRow())

        assertEquals(2, transactions.count())
    }

    // -------------------------------------------------------------- values survive intact

    @Test
    fun everyFieldSurvivesTheRoundTrip() = runTest {
        transactions.insert(airtimeRow())
        val row = transactions.byTxId("83077174642")!!

        assertEquals(1000L, row.amount)          // pesewas, not 10.0
        assertEquals(0L, row.fee)
        assertEquals(9057L, row.balanceAfter)
        assertEquals("MTN AIRTIME", row.counterparty)
        assertEquals(Direction.OUT, row.direction)
        assertEquals(Shape.BILL_AIRTIME, row.shape)
        assertEquals(Reconciled.UNCHECKED, row.reconciled)
        // A dash in the SMS stays null all the way to storage. Not zero.
        assertNull(row.tax)
        assertNull(row.reference)
        // Sacred Rule 6: the original message is kept so a parser fix can reprocess it.
        assertTrue(row.rawBody.startsWith("Your payment of GHS 10.00"))
    }

    @Test
    fun enumsAreStoredByNameSoReorderingCannotCorruptThem() = runTest {
        // Stored as ordinals, adding a value to the middle of Direction would silently
        // turn every IN into OUT with no error anywhere. Names cannot do that.
        transactions.insert(receivedRow())
        val raw = db.openHelper.readableDatabase
            .query("SELECT direction, shape FROM transactions LIMIT 1")
        raw.moveToFirst()
        assertEquals("IN", raw.getString(0))
        assertEquals("PAYMENT_RECEIVED", raw.getString(1))
        raw.close()
    }

    // ------------------------------------------------------------------- rules and labels

    @Test
    fun aRuleLabelsMatchingRowsButNeverOverwritesAHumanDecision() = runTest {
        val autoId = transactions.insert(airtimeRow())
        val manualId = transactions.insert(airtimeRow(txId = "999000111"))
        transactions.setLabel(manualId, "Something else", LabelSource.MANUAL)

        rules.put(RuleEntity("MTN AIRTIME", "Airtime", createdAt = 0L))
        val touched = transactions.applyRule("MTN AIRTIME", "Airtime")

        assertEquals("only the unlabelled row should change", 1, touched)
        assertEquals("Airtime", transactions.byTxId("83077174642")!!.label)
        // The hand-set label is untouched — a guess never outranks a decision.
        assertEquals("Something else", transactions.byTxId("999000111")!!.label)
        assertEquals(LabelSource.MANUAL, transactions.byTxId("999000111")!!.labelSource)
        assertTrue(autoId > 0 && manualId > 0)
    }

    // ---------------------------------------------------------------------- review queue

    @Test
    fun anUnreadableMessageIsHeldForReviewAndNeverBecomesAWrongRow() = runTest {
        // Sacred Rule 7, end to end. This message carries a real amount and a real
        // transaction id, so it is plainly money — but no known shape matches it. The only
        // safe outcome is to hold it for a human, never to guess a figure from it.
        val mangled =
            "Reversal of GHS 30.00 has been processed. Transaction Id: 99900011122. " +
                "Current Balance: GHS 60.00."
        assertTrue(MomoParser.parse(mangled) is ParseResult.Unrecognised)

        transactions.insert(airtimeRow())
        transactions.insert(
            SmsIngest.unparsedRow(mangled, receivedAt = 1_756_200_000_000L, reason = "test"),
        )

        val queue = transactions.reviewQueue()
        assertEquals(1, queue.size)
        // Sacred Rule 6: the original message is kept, which is what makes the reason
        // derivable now rather than frozen at the moment it failed.
        assertEquals(mangled, queue[0].rawBody)
        assertEquals(0L, queue[0].amount)
        // The counterparty is empty, NOT the failure reason — a rule must never be keyed
        // on a sentence of English.
        assertEquals("", queue[0].counterparty)
    }

    @Test
    fun aQueuedMessageCannotCorruptTheReconciliationChain() = runTest {
        // The property that makes holding unreadable messages safe. A queued row carries
        // zeroes for every money field; if reconciliation counted it, those zeroes would
        // manufacture a gap out of nothing and the warning would stop meaning anything.
        transactions.insert(airtimeRow())
        transactions.insert(
            SmsIngest.unparsedRow("gibberish that is not money", 1_749_589_300_000L, "test"),
        )
        transactions.insert(cashOutRow())

        val rows = transactions.allChronological()
        assertEquals("allChronological must exclude queued rows", 2, rows.size)
        assertTrue(rows.none { !it.parsedOk })
    }

    // ------------------------------------------------------------------------ categories

    @Test
    fun theNineStartersSeedAndOtherIsProtected() = runTest {
        categories.insertAll(SikaDatabase.SEED_CATEGORIES)
        assertEquals(9, categories.count())

        val other = categories.all().first { it.name == "Other" }
        assertEquals("Other must be undeletable", 0, categories.delete(other.id))
        assertEquals(9, categories.count())

        val food = categories.all().first { it.name == "Food" }
        assertEquals(1, categories.delete(food.id))
        assertEquals(8, categories.count())
    }

    @Test
    fun deletingACategoryMovesItsMoneyRatherThanLosingIt() = runTest {
        categories.insertAll(SikaDatabase.SEED_CATEGORIES)
        val id = transactions.insert(airtimeRow())
        transactions.setLabel(id, "Food", LabelSource.MANUAL)

        val food = categories.all().first { it.name == "Food" }
        categories.reassign(fromLabel = "Food", toLabel = "Other")
        categories.delete(food.id)

        // The transaction is still there and still counted, just under Other.
        assertEquals(1, transactions.count())
        assertEquals("Other", transactions.byTxId("83077174642")!!.label)
    }

    // ------------------------------------------------------------- rows from real messages

    private fun airtimeRow(txId: String? = null): TransactionEntity {
        val body =
            "Your payment of GHS 10.00 to MTN AIRTIME has been completed at 2026-06-10 21:00:02. " +
                "Your new balance: GHS 90.57. Fee was GHS 0.00 Tax was GHS -. Reference: -. " +
                "Financial Transaction Id: 83077174642. External Transaction Id: 83077174642."
        val row = parse(body).toEntity(occurredAt = 1_749_589_202_000L, rawBody = body)
        return if (txId == null) row else row.copy(txId = txId)
    }

    private fun cashOutRow(): TransactionEntity {
        val body =
            "Cash Out made for GHS20.00 to 000. Current Balance: GHS9.79 " +
                "Financial Transaction Id: 87482945712. Fee charged: GHS0.50."
        return parse(body).toEntity(occurredAt = 1_756_000_000_000L, rawBody = body)
    }

    private fun receivedRow(): TransactionEntity {
        val body =
            "Payment received for GHS 100.00 from Aaa  Current Balance: GHS 179.29 . " +
                "Available Balance: GHS 179.29. Reference: 1. Transaction ID: 88139850923. " +
                "TRANSACTION FEE: 0.00"
        return parse(body).toEntity(occurredAt = 1_756_100_000_000L, rawBody = body)
    }

    private fun parse(body: String) =
        (MomoParser.parse(body) as ParseResult.Parsed).transaction
}
