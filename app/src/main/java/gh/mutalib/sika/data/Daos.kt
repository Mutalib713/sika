package gh.mutalib.sika.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
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

    @Query("UPDATE transactions SET reconciled = :state WHERE id = :id")
    suspend fun setReconciled(id: Long, state: Reconciled)

    @Query("SELECT COUNT(*) FROM transactions WHERE parsedOk = 1 AND label IS NULL")
    fun observeUnlabelledCount(): Flow<Int>

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

    @Query("SELECT * FROM categories ORDER BY sortOrder ASC")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY sortOrder ASC")
    suspend fun all(): List<CategoryEntity>

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int

    /** `Other` is protected — the delete screen must never offer it. */
    @Query("DELETE FROM categories WHERE id = :id AND isProtected = 0")
    suspend fun delete(id: Long): Int

    /**
     * Moves a deleted category's transactions to `Other` rather than orphaning them.
     * **Losing a category must never lose money.**
     */
    @Query("UPDATE transactions SET label = :toLabel WHERE label = :fromLabel")
    suspend fun reassign(fromLabel: String, toLabel: String): Int
}
