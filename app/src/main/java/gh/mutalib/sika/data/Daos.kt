package gh.mutalib.sika.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    /**
     * ⚠ **`IGNORE`, and this is the single most important line in the data layer.**
     *
     * The obvious choice here is `REPLACE`, and `REPLACE` would be a disaster. Room
     * implements it as *delete the old row, insert the new one* — so every inbox sweep
     * would destroy the label Mutalib set, the cash-out answer he tapped, and the
     * reconciliation result, replacing them with a freshly parsed row that knows none of
     * it. Silently. On every launch.
     *
     * `IGNORE` keeps what is already there. A message the app has seen before is simply
     * not re-inserted, which is exactly what Sacred Rule 4 asks for: the sweep must be
     * safe to run infinitely.
     *
     * Returns -1 when the row was ignored, or the new rowId when it was inserted, so the
     * sweep can report how many were genuinely new.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(transaction: TransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(transactions: List<TransactionEntity>): List<Long>

    @Query("SELECT * FROM transactions WHERE txId = :txId LIMIT 1")
    suspend fun byTxId(txId: String): TransactionEntity?

    /** One row by its own id — what the cash-out prompt needs to re-post itself. */
    @Query("SELECT * FROM transactions WHERE id = :id LIMIT 1")
    suspend fun byId(id: Long): TransactionEntity?

    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun count(): Int

    /** Newest first, for the Home list. */
    @Query("SELECT * FROM transactions WHERE parsedOk = 1 ORDER BY occurredAt DESC")
    fun observeAll(): Flow<List<TransactionEntity>>

    /** A half-open range, so month boundaries cannot double-count a transaction. */
    @Query(
        """
        SELECT * FROM transactions
        WHERE parsedOk = 1 AND occurredAt >= :fromInclusive AND occurredAt < :toExclusive
        ORDER BY occurredAt DESC
        """,
    )
    fun observeBetween(fromInclusive: Long, toExclusive: Long): Flow<List<TransactionEntity>>

    /** Oldest first — the order the reconciliation pass has to walk. */
    @Query("SELECT * FROM transactions WHERE parsedOk = 1 ORDER BY occurredAt ASC")
    suspend fun allChronological(): List<TransactionEntity>

    /** The review queue: messages the parser refused. Sacred Rule 7. */
    @Query("SELECT * FROM transactions WHERE parsedOk = 0 ORDER BY occurredAt DESC")
    fun observeReviewQueue(): Flow<List<TransactionEntity>>

    /** The same queue as a one-shot read, for the sweep report. */
    @Query("SELECT * FROM transactions WHERE parsedOk = 0 ORDER BY occurredAt DESC")
    suspend fun reviewQueue(): List<TransactionEntity>

    /** The most recent message's arrival time, so a sweep can pick up where it left off. */
    @Query("SELECT MAX(occurredAt) FROM transactions")
    suspend fun newestTimestamp(): Long?

    /** The oldest transaction, which is where a semester starts until Mutalib says otherwise. */
    @Query("SELECT MIN(occurredAt) FROM transactions WHERE parsedOk = 1")
    suspend fun oldestTimestamp(): Long?

    /**
     * Sets a label by hand. Writes [LabelSource.MANUAL] so a rule can never overwrite it —
     * a human decision outranks a guess.
     */
    @Query("UPDATE transactions SET label = :label, labelSource = :source WHERE id = :id")
    suspend fun setLabel(id: Long, label: String?, source: LabelSource)

    /**
     * Applies a learned rule, **skipping anything a human already decided.** Without that
     * `labelSource` guard, editing a rule would silently overwrite hand-set labels.
     */
    @Query(
        """
        UPDATE transactions SET label = :label, labelSource = 'AUTO_RULE'
        WHERE counterparty = :counterparty AND labelSource IN ('NONE', 'AUTO_RULE')
        """,
    )
    suspend fun applyRule(counterparty: String, label: String): Int

    /**
     * Fills in a guessed label, and **only** where nothing has claimed the row yet.
     *
     * ⚠ The `label IS NULL` clause is the whole safety property. Without it a keyword guess
     * could overwrite a hand-set label or a prompt answer, which is precisely the failure
     * `labelSource` exists to prevent.
     */
    @Query("UPDATE transactions SET label = :label, labelSource = :source WHERE id = :id AND label IS NULL")
    suspend fun setLabelIfUnset(id: Long, label: String, source: LabelSource): Int

    /**
     * Removes one row by MTN's own id.
     *
     * Exists for the debug injector: a test message pushed through the ingest path is a
     * real row afterwards, and leaving fabricated money in a ledger whose whole point is
     * that its arithmetic checks out is not acceptable. Nothing in the shipping UI calls it.
     */
    @Query("DELETE FROM transactions WHERE txId = :txId")
    suspend fun deleteByTxId(txId: String): Int

    /**
     * Sets or clears a row's note.
     *
     * Separate from [setLabel] on purpose: a note and a category are different facts about a
     * transaction, and writing one must never disturb the other. Passing null clears it.
     */
    @Query("UPDATE transactions SET note = :note WHERE id = :id")
    suspend fun setNote(id: Long, note: String?)

    /**
     * Restores a label from a backup, **only onto a row that has none**.
     *
     * ⚠ Keyed on `txId`, never on the row id — ids are local to one install and mean nothing
     * in a file. MTN's transaction id is the same number on any phone, which is why it is the
     * dedupe key in the first place (Sacred Rule 4).
     *
     * The `label IS NULL` guard is the promise that importing can only add: a two-week-old
     * backup can never silently undo two weeks of labelling.
     */
    @Query(
        "UPDATE transactions SET label = :label, labelSource = :source " +
            "WHERE txId = :txId AND label IS NULL",
    )
    suspend fun restoreLabel(txId: String, label: String, source: LabelSource): Int

    /** Same promise for notes: fills a blank, never replaces words already there. */
    @Query("UPDATE transactions SET note = :note WHERE txId = :txId AND note IS NULL")
    suspend fun restoreNote(txId: String, note: String): Int

    /** And for what the missing money was. Same add-only guarantee. */
    @Query("UPDATE transactions SET gapNote = :note WHERE txId = :txId AND gapNote IS NULL")
    suspend fun restoreGapNote(txId: String, note: String): Int

    /**
     * ⚠ **`gapAmount` comes back with it, and that is the point.** The category says where the
     * money was counted; the amount is how much. Restoring the first without the second files
     * a gap under Food and contributes nothing to Food's total — a category that looks answered
     * and changes no figure, which is worse than one that is plainly still open.
     *
     * Fill-only, like every other restore here: a decision already on the phone is newer than
     * the file and wins.
     */
    @Query(
        "UPDATE transactions SET gapCategory = :category, gapAmount = :amount " +
            "WHERE txId = :txId AND gapCategory IS NULL",
    )
    suspend fun restoreGapCategory(txId: String, category: String, amount: Long?): Int

    /**
     * Records what the money before this row actually was.
     *
     * ⚠ Sets the note and **nothing else**. It deliberately does not touch `reconciled`: the
     * gap is a fact about messages MTN did not send, and remembering the purchase does not
     * make the message exist. A check that can be cleared by typing is not a check.
     */
    @Query("UPDATE transactions SET gapNote = :note WHERE id = :id")
    suspend fun setGapNote(id: Long, note: String?)

    @Query("UPDATE transactions SET reconciled = :state WHERE id = :id")
    suspend fun setReconciled(id: Long, state: Reconciled)

    /**
     * Stores the state and the size of the hole together.
     *
     * ⚠ **One statement, because they are one fact.** Written separately, a crash between the
     * two would leave a row flagged GAP with no amount — which reads on screen as a gap of
     * GHS 0.00, a sentence that is both wrong and impossible.
     *
     * ⚠ Clears `gapAmount` on any non-GAP state, so a row that stops being a gap after a
     * parser fix does not keep a stale figure that nothing displays but export would carry.
     */
    @Query(
        "UPDATE transactions SET reconciled = :state, " +
            "gapAmount = CASE WHEN :state = 'GAP' THEN :amount ELSE NULL END WHERE id = :id",
    )
    suspend fun setReconciled(id: Long, state: Reconciled, amount: Long?)

    /**
     * Files remembered money under a category, or takes it back out again.
     *
     * ⚠ **`AND reconciled = 'GAP'` is a guard, not a filter.** Only a gap has money that no
     * message accounts for; letting this write to an ordinary row would put an amount into a
     * total twice — once as the transaction, once as a phantom gap.
     */
    @Query("UPDATE transactions SET gapCategory = :category WHERE id = :id AND reconciled = 'GAP'")
    suspend fun setGapCategory(id: Long, category: String?)

    @Query("SELECT COUNT(*) FROM transactions WHERE parsedOk = 1 AND label IS NULL")
    fun observeUnlabelledCount(): Flow<Int>

    /**
     * The rows just inserted that still carry no category — what [gh.mutalib.sika.ledger.AutoLabel]
     * looks at, so it works on new money instead of walking the whole ledger every launch.
     *
     * `parsedOk = 1` keeps the review queue out of it. A message the parser refused has an
     * empty counterparty and no amount; auto-labelling it would file a failure under Food.
     */
    @Query("SELECT * FROM transactions WHERE id IN (:ids) AND label IS NULL AND parsedOk = 1")
    suspend fun unlabelledIn(ids: List<Long>): List<TransactionEntity>

    /**
     * Applies **every** learned rule to **every** row that still has no category, in one
     * statement — the catch-up half of the 2026-09-06 fix.
     *
     * ⚠ **Naming new arrivals was not enough, and his own ledger is the proof.** Rules were
     * applied at two moments only: when a rule was created, and (after the fix) when a row
     * was inserted. Anything that landed in the gap between those — a payment arriving after
     * its rule existed, which is precisely what the bug caused — stayed unnamed forever, and
     * no later event would ever come back for it. Three of his rows were in exactly that
     * state. Running this on every sweep turns "applied at two moments" into an invariant
     * that simply holds, which also covers a restored backup and a re-read after a parser fix.
     *
     * ⚠ **`label IS NULL`, which is STRICTER than [applyRule]'s `labelSource` guard, and
     * deliberately so.** `applyRule` runs when Mutalib has just asked for a rule, so
     * rewriting an older `AUTO_RULE` label is him changing his mind. This runs unattended on
     * every launch, where the only safe act is filling a blank.
     *
     * ⚠ `counterparty <> ''` because a rule keyed on the empty string would otherwise claim
     * every row in the review queue at once.
     */
    @Query(
        """
        UPDATE transactions SET
            label = (SELECT r.label FROM rules r WHERE r.counterparty = transactions.counterparty),
            labelSource = 'AUTO_RULE'
        WHERE label IS NULL
          AND parsedOk = 1
          AND counterparty <> ''
          AND EXISTS (SELECT 1 FROM rules r WHERE r.counterparty = transactions.counterparty)
        """,
    )
    suspend fun applyAllRules(): Int

    /**
     * How many of one day's transactions still have no category — what the end-of-day
     * nudge counts before deciding whether it has anything worth saying.
     *
     * ⚠ **`direction = 'OUT'` matters, and its absence was a real bug.** Money *arriving*
     * needs no category — the report only ever breaks down spending — so counting incoming
     * rows made the nudge announce work that does not exist, and disagree with the figure
     * Home shows for the same day. Home has always counted outgoing only.
     */
    @Query(
        "SELECT COUNT(*) FROM transactions " +
            "WHERE parsedOk = 1 AND label IS NULL AND direction = 'OUT' " +
            "AND occurredAt >= :fromInclusive AND occurredAt < :toExclusive",
    )
    suspend fun countUnlabelledBetween(fromInclusive: Long, toExclusive: Long): Int

    @Query("SELECT COUNT(*) FROM transactions WHERE reconciled = 'GAP'")
    fun observeGapCount(): Flow<Int>
}

@Dao
interface RuleDao {
    /** Upsert: setting a label for a counterparty again just updates the existing rule. */
    @Upsert
    suspend fun put(rule: RuleEntity)

    @Query("SELECT * FROM rules WHERE counterparty = :counterparty LIMIT 1")
    suspend fun forCounterparty(counterparty: String): RuleEntity?

    @Query("SELECT * FROM rules ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<RuleEntity>>

    /** A one-shot read, for writing the backup file. */
    @Query("SELECT * FROM rules ORDER BY createdAt DESC")
    suspend fun all(): List<RuleEntity>

    @Query("DELETE FROM rules WHERE counterparty = :counterparty")
    suspend fun delete(counterparty: String)

    @Query("SELECT COUNT(*) FROM rules")
    suspend fun count(): Int
}

@Dao
interface CategoryDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(categories: List<CategoryEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(category: CategoryEntity): Long

    /** Everything, hidden included. What the Categories screen shows. */
    @Query("SELECT * FROM categories ORDER BY sortOrder ASC")
    fun observeAll(): Flow<List<CategoryEntity>>

    /**
     * Only the ones still in use.
     *
     * ⚠ **This is what the picker and the cash-out notification read**, and the difference
     * from [observeAll] is the whole point of putting a category away. Wire a chooser to
     * [observeAll] by mistake and hiding does nothing at all.
     */
    @Query("SELECT * FROM categories WHERE isHidden = 0 ORDER BY sortOrder ASC")
    fun observeVisible(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE isHidden = 0 ORDER BY sortOrder ASC")
    suspend fun visible(): List<CategoryEntity>

    @Query("SELECT * FROM categories ORDER BY sortOrder ASC")
    suspend fun all(): List<CategoryEntity>

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int

    /**
     * Puts a category away, or brings it back.
     *
     * ⚠ `isProtected = 0` guards it: `Other` must always be offerable, because it is where
     * anything that fits nothing else goes. Hide it and a transaction can end up with no
     * honest answer available at all.
     */
    @Query("UPDATE categories SET isHidden = :hidden WHERE id = :id AND isProtected = 0")
    suspend fun setHidden(id: Long, hidden: Boolean): Int

    /** How many transactions carry this label. Zero is what makes a delete safe. */
    @Query("SELECT COUNT(*) FROM transactions WHERE label = :name")
    suspend fun usageOf(name: String): Int

    /** Usage for every category name at once, so the screen reads the table once, not nine times. */
    @Query("SELECT label AS name, COUNT(*) AS uses FROM transactions WHERE label IS NOT NULL GROUP BY label")
    fun observeUsage(): Flow<List<CategoryUsage>>

    /** `Other` is protected — no screen may ever offer to delete it. */
    @Query("DELETE FROM categories WHERE id = :id AND isProtected = 0")
    suspend fun delete(id: Long): Int

    /**
     * Deletes a category **only while nothing points at it**.
     *
     * ⚠ **The one rule, with no special case for the nine starters.** A category holding
     * transactions cannot be deleted at all — it can only be put away, which keeps every row
     * exactly as it is. A category that never labelled anything can go, because there is
     * nothing left to lose.
     *
     * `@Transaction` matters: the count and the delete have to see the same database, or a
     * label written between the two would be orphaned by a delete that read a stale zero.
     *
     * @return true if it was removed.
     */
    @Transaction
    suspend fun deleteIfUnused(id: Long, name: String): Boolean {
        if (usageOf(name) > 0) return false
        return delete(id) > 0
    }

    /**
     * Moves one category's transactions to another.
     *
     * ⚠ **Nothing calls this any more, and that is deliberate.** It was written for the
     * delete-and-reassign flow that hiding replaced: losing a category would not have lost the
     * money, but it would have erased what the money was *for*, which is the only thing the
     * ledger cannot rebuild from the SMS inbox. Kept because a genuine merge — "these two
     * names mean the same thing" — is a real future feature, and this is the safe half of it.
     */
    @Query("UPDATE transactions SET label = :toLabel WHERE label = :fromLabel")
    suspend fun reassign(fromLabel: String, toLabel: String): Int
}

/** One row of [CategoryDao.observeUsage]: a label and how many transactions carry it. */
data class CategoryUsage(val name: String, val uses: Int)

/**
 * The semesters Mutalib has named.
 *
 * ⚠ **Ordered by start date, never by id.** Terms are added in whatever order he remembers
 * them, and stepping ‹ › through the report has to walk them chronologically. Sorting by
 * insertion order would send "previous semester" to whichever one he happened to type second.
 */
@Dao
interface TermDao {

    @Query("SELECT * FROM terms ORDER BY startDay")
    suspend fun all(): List<TermEntity>

    @Query("SELECT * FROM terms ORDER BY startDay")
    fun observe(): Flow<List<TermEntity>>

    @Query("SELECT COUNT(*) FROM terms")
    suspend fun count(): Int

    @Insert
    suspend fun insert(term: TermEntity): Long

    @Update
    suspend fun update(term: TermEntity)

    /**
     * ⚠ Deleting a term does not touch a single transaction. A semester is a lens on the
     * ledger, not a container for it — the rows keep their dates and simply stop being
     * grouped under that name.
     */
    @Query("DELETE FROM terms WHERE id = :id")
    suspend fun delete(id: Long)
}
