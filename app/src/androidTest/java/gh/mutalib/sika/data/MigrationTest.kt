package gh.mutalib.sika.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The migration, tested against real SQLite.
 *
 * ⚠ **This suite cannot be run casually on Mutalib's phone.** `connectedDebugAndroidTest`
 * uninstalls the app when it finishes — standard AGP behaviour — and the uninstall takes the
 * ledger with it. His labels are the one thing that cannot be rebuilt from the SMS inbox, so
 * this runs on a fresh device, or after the database has been exported.
 *
 * The 1 → 2 migration was therefore verified on 2026-09-01 by the route that actually
 * matters: upgrading the installed app in place, over his real 148 rows. Before: 148 rows,
 * 5 labels, `user_version` 1, no `note` column. After: 148 rows, 5 labels, `user_version` 2,
 * `note` present. That is the real scenario, and it is safe precisely because the database
 * builder has no `fallbackToDestructiveMigration` — a broken migration fails to open rather
 * than quietly wiping anything.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        SikaDatabase::class.java,
    )

    /**
     * The property that matters: a row written under version 1 still exists, with every
     * field intact, after the upgrade — and it gains a null note rather than losing anything.
     */
    @Test
    fun addingTheNoteColumnKeepsEveryRowAndEveryLabel() {
        helper.createDatabase(TEST_DB, 1).use { db ->
            db.execSQL(
                """
                INSERT INTO transactions
                  (id, txId, occurredAt, direction, shape, amount, fee, tax, counterparty,
                   reference, balanceAfter, label, labelSource, rawBody, parsedOk, reconciled)
                VALUES
                  (1, 'tx-1', 1000, 'OUT', 'PAYMENT_FOR', 1500, 50, NULL, 'WAAKYE JOINT',
                   NULL, 9000, 'Food', 'MANUAL', 'raw body', 1, 'OK')
                """.trimIndent(),
            )
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 2, true, SikaDatabase.MIGRATION_1_2)

        db.query("SELECT txId, label, labelSource, amount, fee, note FROM transactions").use { c ->
            assertTrue("the row must survive the migration", c.moveToFirst())
            assertEquals("tx-1", c.getString(0))
            assertEquals("Food", c.getString(1))
            assertEquals("MANUAL", c.getString(2))
            assertEquals(1500L, c.getLong(3))
            assertEquals(50L, c.getLong(4))
            // A row written before the column existed genuinely has no note, and null is the
            // honest way to say so.
            assertTrue("an old row has no note", c.isNull(5))
            assertEquals("exactly one row, not a duplicate", false, c.moveToNext())
        }
    }

    private companion object {
        const val TEST_DB = "migration-test.db"
    }
}
