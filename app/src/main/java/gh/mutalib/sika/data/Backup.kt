package gh.mutalib.sika.data

import gh.mutalib.sika.parser.Direction
import gh.mutalib.sika.parser.Shape

/**
 * The whole ledger as one text file, and the way back in.
 *
 * ⚠ **This exists because of risk #3 in PROFILE.md: the labels are the only irreplaceable
 * data in the app.** Every transaction can be rebuilt by re-reading the SMS inbox. Nothing
 * can rebuild the fact that `AKOSUA MENSAH` meant Food — that was months of small
 * decisions, and a reinstall would take all of them.
 *
 * Export alone is not enough, and calling it a backup would be a lie: a file you can look at
 * after a wipe is a souvenir, not a recovery. Import is the half that matters.
 *
 * ### What is in the file
 *
 * Three sections, marked by a line like `[transactions]`, so one file carries everything that
 * cannot be regenerated:
 *   * **transactions** — every row, including the raw SMS body, the label and the note;
 *   * **categories** — including the ones put away, and any custom ones added;
 *   * **rules** — the learn-once table, which is the thing that keeps labelling shrinking.
 *
 * A spreadsheet opens it and shows something sensible. That is a side benefit, not the design.
 *
 * ### What import will never do
 *
 * ⚠ **It never overwrites a label or a note that already exists**, and never replaces a row.
 * Import can only add information. On an empty database that restores everything, which is the
 * case that matters; on a live one it merges, and cannot undo work done since the export.
 * The alternative — letting an old file win — means restoring a two-week-old backup silently
 * reverts two weeks of labelling, with no warning and no way back.
 *
 * ⚠ **It never invents money.** A row whose direction, shape or amount cannot be read is
 * counted as a problem and skipped, not guessed at. Same rule as the parser: Sacred Rule 7.
 */
object Backup {

    /**
     * ⚠ **Bumped to 2 on 2026-09-01 because version 1 silently dropped `gapNote`.**
     *
     * The column was added for the gap explanation and never reached the backup format, so an
     * export looked complete, restored cleanly, and quietly lost the words Mutalib had typed
     * about money no message could explain — the one field in the whole file that cannot be
     * recovered from anywhere else. Found by actually running the export-wipe-import round
     * trip, minutes before the wipe would have proved it the hard way.
     *
     * Version 1 files are still readable: the reader requires only the original sixteen
     * columns and treats a seventeenth as optional.
     */
    const val VERSION = 2
    const val MIME = "text/csv"

    private const val S_TRANSACTIONS = "[transactions]"
    private const val S_CATEGORIES = "[categories]"
    private const val S_RULES = "[rules]"

    private val TX_HEADER = listOf(
        "txId", "occurredAt", "direction", "shape", "amount", "fee", "tax",
        "counterparty", "reference", "balanceAfter", "label", "labelSource", "note",
        "parsedOk", "reconciled", "rawBody", "gapNote",
    )

    /** What a version-1 file has. Anything beyond this is optional when reading. */
    private const val TX_REQUIRED = 16
    private val CAT_HEADER = listOf("name", "sortOrder", "isDefault", "isProtected", "isHidden")
    private val RULE_HEADER = listOf("counterparty", "label", "createdAt")

    /** A filename with the date in it, because a folder of `sika.csv` files helps nobody. */
    fun fileName(today: java.time.LocalDate): String = "sika-$today.csv"

    // ------------------------------------------------------------------ writing

    fun write(
        transactions: List<TransactionEntity>,
        categories: List<CategoryEntity>,
        rules: List<RuleEntity>,
    ): String = buildString {
        appendLine(Csv.row(listOf("SIKA BACKUP", VERSION.toString())))

        appendLine(S_TRANSACTIONS)
        appendLine(Csv.row(TX_HEADER))
        transactions.forEach { t ->
            appendLine(
                Csv.row(
                    listOf(
                        t.txId, t.occurredAt.toString(), t.direction.name, t.shape.name,
                        t.amount.toString(), t.fee.toString(), t.tax?.toString(),
                        t.counterparty, t.reference, t.balanceAfter?.toString(),
                        t.label, t.labelSource.name, t.note,
                        if (t.parsedOk) "1" else "0", t.reconciled.name, t.rawBody, t.gapNote,
                    ),
                ),
            )
        }

        appendLine(S_CATEGORIES)
        appendLine(Csv.row(CAT_HEADER))
        categories.forEach { c ->
            appendLine(
                Csv.row(
                    listOf(
                        c.name, c.sortOrder.toString(),
                        if (c.isDefault) "1" else "0",
                        if (c.isProtected) "1" else "0",
                        if (c.isHidden) "1" else "0",
                    ),
                ),
            )
        }

        appendLine(S_RULES)
        appendLine(Csv.row(RULE_HEADER))
        rules.forEach { r ->
            appendLine(Csv.row(listOf(r.counterparty, r.label, r.createdAt.toString())))
        }
    }

    // ------------------------------------------------------------------ reading

    /** What a file turned out to contain, plus everything that could not be read. */
    data class Parsed(
        val transactions: List<TransactionEntity> = emptyList(),
        val categories: List<CategoryEntity> = emptyList(),
        val rules: List<RuleEntity> = emptyList(),
        /** One line per unreadable row, in plain words. Shown, never swallowed. */
        val problems: List<String> = emptyList(),
        /** Set when the file is not a Sika backup at all. */
        val fatal: String? = null,
    )

    fun read(text: String): Parsed {
        val scan = Csv.parseChecked(text)
        val rows = scan.rows
        if (rows.isEmpty()) return Parsed(fatal = "That file is empty.")
        // ⚠ Fatal, not a skipped row. Everything after an unclosed quote was swallowed into
        // one field, so the honest report is "this file is damaged" rather than a count that
        // understates the loss — see Csv.Parsed.
        if (scan.unterminatedQuote) {
            return Parsed(
                fatal = "That file is damaged: a quoted value is never closed, so everything " +
                    "after it could not be read. Nothing was imported.",
            )
        }
        val first = rows.first()
        if (first.firstOrNull() != "SIKA BACKUP") {
            return Parsed(fatal = "That is not a Sika backup file.")
        }
        val version = first.getOrNull(1)?.toIntOrNull()
        if (version == null || version > VERSION) {
            return Parsed(
                fatal = "That file was written by a newer version of Sika (format $version).",
            )
        }

        val transactions = mutableListOf<TransactionEntity>()
        val categories = mutableListOf<CategoryEntity>()
        val rules = mutableListOf<RuleEntity>()
        val problems = mutableListOf<String>()
        var section = ""

        rows.drop(1).forEachIndexed { index, row ->
            val line = index + 2 // 1-based, and the marker line was dropped
            val head = row.firstOrNull()
            when {
                head == S_TRANSACTIONS || head == S_CATEGORIES || head == S_RULES -> {
                    section = head
                    return@forEachIndexed
                }
                // The header line of whichever section just started.
                head == "txId" || head == "name" || (head == "counterparty" && section == S_RULES) ->
                    return@forEachIndexed
                head == null -> return@forEachIndexed
            }
            when (section) {
                S_TRANSACTIONS -> transaction(row)
                    ?.let(transactions::add)
                    ?: problems.add("Line $line: could not read this transaction, so it was left out.")

                S_CATEGORIES -> category(row)
                    ?.let(categories::add)
                    ?: problems.add("Line $line: could not read this category.")

                S_RULES -> rule(row)
                    ?.let(rules::add)
                    ?: problems.add("Line $line: could not read this rule.")
            }
        }
        return Parsed(transactions, categories, rules, problems)
    }

    /**
     * ⚠ Every money field goes through `toLongOrNull`, and a null fails the whole row.
     * A transaction with an unreadable amount is not a transaction with a zero amount.
     */
    private fun transaction(r: List<String?>): TransactionEntity? {
        // ⚠ The ORIGINAL sixteen, not TX_HEADER.size — otherwise adding a column here would
        // make every file written by an older Sika unreadable, which is the opposite of what
        // a backup format is for.
        if (r.size < TX_REQUIRED) return null
        val txId = r[0]?.takeIf { it.isNotBlank() } ?: return null
        val occurredAt = r[1]?.toLongOrNull() ?: return null
        val direction = enumOrNull<Direction>(r[2]) ?: return null
        val shape = enumOrNull<Shape>(r[3]) ?: return null
        val amount = r[4]?.toLongOrNull() ?: return null
        val fee = r[5]?.toLongOrNull() ?: return null
        // Tax is genuinely nullable - the SMS writes "-" when there was none, which is not
        // the same as zero. An empty field here means that, and is not an error.
        val tax = r[6]?.let { it.toLongOrNull() ?: return null }
        val balanceAfter = r[9]?.let { it.toLongOrNull() ?: return null }
        return TransactionEntity(
            txId = txId,
            occurredAt = occurredAt,
            direction = direction,
            shape = shape,
            amount = amount,
            fee = fee,
            tax = tax,
            counterparty = r[7].orEmpty(),
            reference = r[8],
            balanceAfter = balanceAfter,
            label = r[10],
            labelSource = enumOrNull<LabelSource>(r[11]) ?: LabelSource.NONE,
            note = r[12],
            rawBody = r[15].orEmpty(),
            parsedOk = r[13] != "0",
            reconciled = enumOrNull<Reconciled>(r[14]) ?: Reconciled.UNCHECKED,
            // Absent in a version-1 file, which is not an error.
            gapNote = r.getOrNull(16),
        )
    }

    private fun category(r: List<String?>): CategoryEntity? {
        val name = r.getOrNull(0)?.takeIf { it.isNotBlank() } ?: return null
        val order = r.getOrNull(1)?.toIntOrNull() ?: return null
        return CategoryEntity(
            name = name,
            sortOrder = order,
            isDefault = r.getOrNull(2) == "1",
            isProtected = r.getOrNull(3) == "1",
            isHidden = r.getOrNull(4) == "1",
        )
    }

    private fun rule(r: List<String?>): RuleEntity? {
        val party = r.getOrNull(0)?.takeIf { it.isNotBlank() } ?: return null
        val label = r.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return null
        val at = r.getOrNull(2)?.toLongOrNull() ?: return null
        return RuleEntity(party, label, at)
    }

    private inline fun <reified E : Enum<E>> enumOrNull(name: String?): E? =
        name?.let { runCatching { enumValueOf<E>(it) }.getOrNull() }
}
