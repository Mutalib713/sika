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
    entities = [TransactionEntity::class, RuleEntity::class, CategoryEntity::class],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class SikaDatabase : RoomDatabase() {

    abstract fun transactions(): TransactionDao
    abstract fun rules(): RuleDao
    abstract fun categories(): CategoryDao

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

        private fun build(context: Context): SikaDatabase =
            Room.databaseBuilder(context, SikaDatabase::class.java, NAME)
                .addMigrations(MIGRATION_1_2)
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
