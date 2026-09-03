package gh.mutalib.sika.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import androidx.room.TypeConverters

/**
 * The ledger. One file, in Sika's private folder, that no other app can open.
 *
 * **`exportSchema = true`, from version 1.** PROFILE.md § 7 requires a migration for every
 * schema change, and a migration needs the previous schema on disk to migrate *from*.
 * Turning this on later is too late — the version-1 shape would already be unrecorded.
 * The JSON lands in `app/schemas/` and is committed.
 */
@Database(
    entities = [
        TransactionEntity::class,
        RuleEntity::class,
        CategoryEntity::class,
        TermEntity::class,
    ],
    version = 6,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class SikaDatabase : RoomDatabase() {

    abstract fun transactions(): TransactionDao
    abstract fun rules(): RuleDao
    abstract fun categories(): CategoryDao
    abstract fun terms(): TermDao

    companion object {
        const val NAME = "sika.db"

        @Volatile private var instance: SikaDatabase? = null

        fun get(context: Context): SikaDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context.applicationContext).also { instance = it }
            }

        /**
         * Version 1 to 2: a per-transaction note.
         *
         * ⚠ **`ADD COLUMN` and nothing else.** SQLite adds the column to the existing table
         * in place, so every row, every label, every cash-out answer and every reconciliation
         * result survives untouched. The alternative Room offers - drop and recreate - would
         * destroy the one dataset that cannot be rebuilt from the SMS inbox.
         *
         * The column is nullable with no default, which is what makes it safe: existing rows
         * get NULL, meaning "no note", which is exactly true of every row written before this
         * column existed.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL("ALTER TABLE transactions ADD COLUMN note TEXT")
            }
        }

        /**
         * Version 2 to 3: a category can be put away instead of deleted.
         *
         * ⚠ **`NOT NULL DEFAULT 0` is what makes this safe on existing rows.** Every category
         * already on the phone becomes `isHidden = 0`, meaning "in use", which is exactly what
         * was true of all of them before the column existed. A nullable column would have
         * matched the note migration's shape but been wrong here: "unknown" is not a state a
         * category can be in, and every read would need to decide what null meant.
         *
         * Same `ALTER TABLE` guarantee as 1→2 — SQLite adds the column in place, so no
         * transaction, label, note or reconciliation result is touched.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    "ALTER TABLE categories ADD COLUMN isHidden INTEGER NOT NULL DEFAULT 0",
                )
            }
        }

        /**
         * Version 3 to 4: an explanation for money that never got a message.
         *
         * Nullable with no default, like the note column in 1 to 2 and for the same reason:
         * every existing row gets NULL, which means "unexplained", which is exactly true of
         * every gap recorded before there was any way to explain one.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL("ALTER TABLE transactions ADD COLUMN gapNote TEXT")
            }
        }

        /**
         * Gaps can now carry an amount and a category.
         *
         * ⚠ **Two columns, both nullable, no backfill.** `gapAmount` fills itself in on the
         * next reconciliation pass, which runs on every sweep, so existing rows repair
         * themselves within one launch. Backfilling here would mean running the reconciler
         * inside a migration — on the main thread, mid-upgrade, with no way to report a
         * failure. Leaving them null is the honest state: not yet computed.
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL("ALTER TABLE transactions ADD COLUMN gapAmount INTEGER")
                connection.execSQL("ALTER TABLE transactions ADD COLUMN gapCategory TEXT")
            }
        }

        /**
         * Version 5 to 6: semesters become a named list instead of one anonymous stretch.
         *
         * ⚠ **`CREATE TABLE`, so nothing existing is touched at all** — this migration cannot
         * lose a transaction because it never mentions that table.
         *
         * ⚠ **The two SharedPreferences keys it replaces are NOT read here, and are not
         * deleted either.** A migration runs inside the database and has no business reaching
         * into a preferences file; the bridge that turns an old single term into the first row
         * of this table lives in `Terms.ensureSeeded`, where it can run on a background thread
         * and be tested. Leaving the old keys in place also means an install that rolls back to
         * v1.0.1 still finds its semester dates where it left them.
         *
         * ⚠ **No index on `startDay` despite every read ordering by it.** A student has single
         * digits of these. An index would be larger than the table.
         */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS terms (
                        id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        name TEXT NOT NULL,
                        startDay INTEGER NOT NULL,
                        endExclusiveDay INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
            }
        }

        private fun build(context: Context): SikaDatabase =
            Room.databaseBuilder(context, SikaDatabase::class.java, NAME)
                .addMigrations(
                    MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6,
                )
                // ⚠ **No `fallbackToDestructiveMigration()`.** It is the usual shortcut and
                // it means "if the schema changed, delete everything and start over" — on a
                // ledger whose whole value is months of history, and whose labels are the
                // one thing that cannot be rebuilt from the SMS inbox. If a future version
                // bumps the schema without a migration, this must fail loudly at build time
                // rather than wipe the user's data at runtime.
                .build()

        /** The nine starters, seeded on first run. `Other` is last and protected. */
        val SEED_CATEGORIES = listOf(
            "Food", "Transport", "Data", "Airtime", "Rent", "Provisions", "Printing", "Sent home",
        ).mapIndexed { i, name ->
            CategoryEntity(name = name, sortOrder = i, isDefault = true)
        } + CategoryEntity(
            name = "Other",
            sortOrder = 99,
            isDefault = true,
            isProtected = true,
        )
    }
}
