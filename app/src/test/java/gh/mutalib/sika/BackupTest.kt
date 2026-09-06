package gh.mutalib.sika

import gh.mutalib.sika.data.Backup
import gh.mutalib.sika.data.CategoryEntity
import gh.mutalib.sika.data.Csv
import gh.mutalib.sika.data.LabelSource
import gh.mutalib.sika.data.Reconciled
import gh.mutalib.sika.data.RuleEntity
import gh.mutalib.sika.data.TermEntity
import gh.mutalib.sika.data.TransactionEntity
import gh.mutalib.sika.parser.Direction
import gh.mutalib.sika.parser.Shape
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The backup is the one feature whose failure is invisible until the day it matters.
 *
 * These tests are the closest thing to that day that can be run without a phone: write a
 * ledger out, read it back, and require every field to be the same object it started as.
 */
class BackupTest {

    private fun tx(
        txId: String = "1234567890",
        label: String? = null,
        note: String? = null,
        tax: Long? = 0L,
        body: String = "Payment for GHS1.00 to TEST. Current Balance: GHS 99.00.",
    ) = TransactionEntity(
        txId = txId,
        occurredAt = 1_756_700_000_000L,
        direction = Direction.OUT,
        shape = Shape.PAYMENT_MADE,
        amount = 100L,
        fee = 50L,
        tax = tax,
        counterparty = "AKOSUA MENSAH",
        reference = "-",
        balanceAfter = 9_900L,
        label = label,
        labelSource = if (label == null) LabelSource.NONE else LabelSource.MANUAL,
        note = note,
        rawBody = body,
    )

    // ---------------------------------------------------------------- the CSV itself

    @Test
    fun `a comma inside a field survives the round trip`() {
        // ⚠ The failure this guards against: MoMo writes "Fee charged: GHS0.50,Tax Charged 0."
        // A naive split on "," turns one row into two and shifts every column right, producing
        // a file that still looks like a CSV and restores nonsense.
        val body = "Payment for GHS1.00. Fee charged: GHS0.50,Tax Charged 0."
        val line = Csv.row(listOf("a", body, "c"))
        assertEquals(listOf("a", body, "c"), Csv.parse(line).single())
    }

    @Test
    fun `a quote inside a field survives the round trip`() {
        val awkward = "he said \"hello\", then left"
        assertEquals(listOf(awkward), Csv.parse(Csv.row(listOf(awkward))).single())
    }

    @Test
    fun `a newline inside a quoted field does not end the row`() {
        val wrapped = "line one\nline two"
        val parsed = Csv.parse(Csv.row(listOf("a", wrapped)))
        assertEquals(1, parsed.size)
        assertEquals(listOf("a", wrapped), parsed.single())
    }

    @Test
    fun `null and empty stay different`() {
        val parsed = Csv.parse(Csv.row(listOf(null, "", "x"))).single()
        assertNull("an absent value must come back absent", parsed[0])
        assertEquals("an empty value must come back empty, not absent", "", parsed[1])
        assertEquals("x", parsed[2])
    }

    // ---------------------------------------------------------------- the backup

    @Test
    fun `a ledger written out and read back is the same ledger`() {
        val rows = listOf(
            tx(txId = "1", label = "Food", note = "waakye"),
            tx(txId = "2", tax = null, body = "Cash Out, GHS20.00. Fee charged: GHS0.50,Tax 0."),
        )
        val categories = listOf(
            CategoryEntity(id = 7, name = "Food", sortOrder = 0, isDefault = true),
            CategoryEntity(id = 8, name = "Printing", sortOrder = 6, isDefault = true, isHidden = true),
        )
        val rules = listOf(RuleEntity("AKOSUA MENSAH", "Food", 1_756_000_000_000L))

        val back = Backup.read(Backup.write(rows, categories, rules))

        assertNull(back.fatal)
        assertEquals(emptyList<String>(), back.problems)
        assertEquals(2, back.transactions.size)
        // Ids are local to one install and are deliberately not compared - everything else is.
        assertEquals(rows.map { it.copy(id = 0) }, back.transactions)
        assertEquals(categories.map { it.copy(id = 0) }, back.categories)
        assertEquals(rules, back.rules)
    }

    @Test
    fun `a null tax is not turned into zero`() {
        // The SMS writes "-" when there was no tax, which is not the same as a tax of zero.
        val back = Backup.read(Backup.write(listOf(tx(tax = null)), emptyList(), emptyList()))
        assertNull(back.transactions.single().tax)
    }

    @Test
    fun `a put-away category comes back put away`() {
        val hidden = CategoryEntity(name = "Printing", sortOrder = 6, isHidden = true)
        val back = Backup.read(Backup.write(emptyList(), listOf(hidden), emptyList()))
        assertTrue(back.categories.single().isHidden)
    }

    @Test
    fun `a row with an unreadable amount is reported, not guessed at`() {
        val good = Backup.write(listOf(tx()), emptyList(), emptyList())
        val broken = good.replace(",100,50,", ",oops,50,")
        val back = Backup.read(broken)

        assertEquals("the bad row must not be restored", 0, back.transactions.size)
        assertEquals("and it must be reported", 1, back.problems.size)
        assertNull("one bad row is not a bad file", back.fatal)
    }

    @Test
    fun `a file that is not a backup is refused whole`() {
        val back = Backup.read("name,amount\nshopping,20\n")
        assertNotNull(back.fatal)
        assertEquals(0, back.transactions.size)
    }

    @Test
    fun `an empty file is refused`() {
        assertNotNull(Backup.read("").fatal)
    }

    @Test
    fun `a file from a newer Sika is refused rather than half-read`() {
        // Keyed off Backup.VERSION rather than a hardcoded 1, so bumping the format does not
        // quietly turn this test into one that asserts nothing.
        val newer = Backup.write(listOf(tx()), emptyList(), emptyList())
            .replaceFirst("SIKA BACKUP,${Backup.VERSION}", "SIKA BACKUP,99")
        assertNotNull(Backup.read(newer).fatal)
    }

    @Test
    fun `a gap explanation survives the round trip`() {
        // ⚠ It did not, until 2026-09-01. `gapNote` was added for the gap feature and never
        // reached the backup format, so an export looked complete and quietly lost the one
        // field in the file that cannot be recovered from anywhere else. Caught by running
        // the real round trip on the phone, minutes before a wipe would have proved it.
        val row = tx(txId = "9").copy(gapNote = "friend")
        val back = Backup.read(Backup.write(listOf(row), emptyList(), emptyList()))
        assertEquals("friend", back.transactions.single().gapNote)
    }

    @Test
    fun `a version-1 file still reads, without a gap explanation`() {
        // Adding a column must not make older backups unreadable - that is the opposite of
        // what a backup format is for.
        val v1 = "SIKA BACKUP,1\n[transactions]\n" +
            "txId,occurredAt,direction,shape,amount,fee,tax,counterparty,reference," +
            "balanceAfter,label,labelSource,note,parsedOk,reconciled,rawBody\n" +
            "tx1,1000,OUT,PAYMENT_MADE,100,0,0,XXX,,900,Food,MANUAL,,1,OK,body\n"
        val back = Backup.read(v1)
        assertNull(back.fatal)
        assertEquals(1, back.transactions.size)
        assertEquals("Food", back.transactions.single().label)
        assertNull(back.transactions.single().gapNote)
    }

    @Test
    fun `the file name carries its date`() {
        assertEquals("sika-2026-09-01.csv", Backup.fileName(java.time.LocalDate.of(2026, 9, 1)))
    }

    // ---- the guard against this file rotting again ------------------------------------

    /**
     * ⚠ **Every column of [TransactionEntity] must be in the backup, and this test is the only
     * thing that will say so.**
     *
     * The same failure has now happened three times. `gapNote` was added and never reached the
     * format (found 2026-09-01, minutes before a wipe). Then `gapAmount` and `gapCategory` were
     * added and never reached it either, and neither did the whole `terms` table (found in the
     * 2026-09-04 audit). Each time, the export looked complete and restored cleanly while
     * quietly dropping something that could not be recovered from anywhere else.
     *
     * A reviewer will not catch this — the code that forgets a column is the code that looks
     * finished. So the check is mechanical: reflect over the entity and fail the build.
     *
     * If you are here because this test failed, you added a field. Add it to `TX_HEADER`,
     * write it in `Backup.write`, read it in `Backup.transaction`, restore it in
     * `BackupIo.apply`, and bump `Backup.VERSION`. Do not add it to the ignore list below
     * unless it genuinely cannot be restored.
     */
    @Test
    fun `every transaction column is in the backup`() {
        // `id` is the phone's own row number and is deliberately not carried between devices.
        val notBackedUp = setOf("id")
        val fields = TransactionEntity::class.java.declaredFields
            .map { it.name }
            .filterNot { it.contains("$") || it in notBackedUp }
            .toSet()

        val written = Backup.write(
            transactions = listOf(tx()),
            categories = emptyList(),
            rules = emptyList(),
        )
        val header = written.lineSequence().first { it.startsWith("txId") }.split(",")
            .map { it.trim('"') }.toSet()

        val missing = fields - header
        assertTrue(
            "TransactionEntity has ${missing.size} field(s) the backup does not carry: " +
                "$missing. A restore would silently lose them.",
            missing.isEmpty(),
        )
    }

    /** The same, for the semesters section — the table the audit found missing entirely. */
    @Test
    fun `semesters survive a round trip`() {
        val term = TermEntity(name = "First semester, Year 1", startDay = 20_000, endExclusiveDay = 20_120)
        val text = Backup.write(emptyList(), emptyList(), emptyList(), listOf(term))
        val back = Backup.read(text)
        assertEquals(emptyList<String>(), back.problems)
        assertEquals(1, back.terms.size)
        assertEquals("First semester, Year 1", back.terms[0].name)
        assertEquals(20_000L, back.terms[0].startDay)
        assertEquals(20_120L, back.terms[0].endExclusiveDay)
    }

    /** A semester whose dates will not parse is skipped and reported, never guessed at. */
    @Test
    fun `a damaged semester is reported rather than invented`() {
        val good = TermEntity(name = "Fine", startDay = 20_000, endExclusiveDay = 20_120)
        // A row appended to the semesters section with an unreadable start day. Appending is
        // safe because that section is written last, deliberately.
        val text = Backup.write(emptyList(), emptyList(), emptyList(), listOf(good)) +
            Csv.row(listOf("Broken", "nope", "20120")) + "\n"
        val back = Backup.read(text)
        assertEquals(1, back.terms.size)
        assertEquals("Fine", back.terms[0].name)
        assertEquals(1, back.problems.size)
    }
}
