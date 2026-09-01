package gh.mutalib.sika.data

import android.content.Context
import android.net.Uri
import android.util.Log
import gh.mutalib.sika.TAG
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reading and writing the backup file on the phone.
 *
 * ⚠ **Through the Storage Access Framework, so Sika asks for no storage permission at all.**
 * The system file picker hands back a `Uri` for exactly the one file the user chose, and that
 * grant lasts for that file and nothing else. `WRITE_EXTERNAL_STORAGE` would ask for the whole
 * device to save one CSV, on an app whose pitch is that it reads nothing but MoMo messages.
 *
 * Everything here runs on `Dispatchers.IO`. The file is small — a few hundred rows — but a
 * `Uri` can point at a cloud provider that takes seconds to answer, and the main thread must
 * never be the one waiting.
 */
object BackupIo {

    /** How the export went. [rows] is what was written, so the screen can say a real number. */
    data class Export(val rows: Int, val error: String? = null)

    /**
     * What an import actually changed.
     *
     * Four separate numbers on purpose. "Imported 148" says nothing about whether it worked;
     * "0 added, 91 labels restored" says the rows were already there and the labels were the
     * thing that came back — which is the case this feature exists for.
     */
    data class Import(
        val added: Int = 0,
        val labels: Int = 0,
        val notes: Int = 0,
        val categories: Int = 0,
        val rules: Int = 0,
        val problems: List<String> = emptyList(),
        val error: String? = null,
    ) {
        val changedNothing: Boolean
            get() = added == 0 && labels == 0 && notes == 0 && categories == 0 && rules == 0
    }

    suspend fun export(context: Context, uri: Uri): Export = withContext(Dispatchers.IO) {
        runCatching {
            val db = SikaDatabase.get(context)
            val transactions = db.transactions().allChronological()
            val text = Backup.write(transactions, db.categories().all(), db.rules().all())
            context.contentResolver.openOutputStream(uri, "wt")
                ?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                ?: return@runCatching Export(0, "Sika could not open that file to write.")
            Log.i(TAG, "exported " + transactions.size + " transactions")
            Export(transactions.size)
        }.getOrElse {
            Log.e(TAG, "export failed", it)
            Export(0, "Sika could not write that file. Nothing was changed.")
        }
    }

    suspend fun import(context: Context, uri: Uri): Import = withContext(Dispatchers.IO) {
        runCatching {
            val text = context.contentResolver.openInputStream(uri)
                ?.use { it.readBytes().toString(Charsets.UTF_8) }
                ?: return@runCatching Import(error = "Sika could not open that file.")

            val parsed = Backup.read(text)
            parsed.fatal?.let { return@runCatching Import(error = it) }
            apply(context, parsed)
        }.getOrElse {
            Log.e(TAG, "import failed", it)
            Import(error = "Sika could not read that file. Nothing was changed.")
        }
    }

    /**
     * Puts a parsed file into the database.
     *
     * Order matters. Categories go in **first**, so that a label being restored a moment later
     * has somewhere to belong — otherwise a custom category comes back as a label pointing at
     * a name the picker no longer lists.
     */
    private suspend fun apply(context: Context, parsed: Backup.Parsed): Import {
        val db = SikaDatabase.get(context)
        val transactions = db.transactions()
        val categories = db.categories()
        val rules = db.rules()

        val existingNames = categories.all().associateBy { it.name }
        var newCategories = 0
        parsed.categories.forEach { c ->
            if (!existingNames.containsKey(c.name)) {
                // Copied without its id: the id in a file belongs to the phone that wrote it.
                categories.insert(c.copy(id = 0))
                newCategories++
            } else if (c.isHidden) {
                // A category put away before the export stays put away after the restore.
                existingNames[c.name]?.let { categories.setHidden(it.id, true) }
            }
        }

        // IGNORE on conflict is what makes this a merge: a txId already on the phone keeps the
        // row it has, labels and all. See SikaDatabase - REPLACE here would be catastrophic.
        val before = transactions.count()
        transactions.insertAll(parsed.transactions.map { it.copy(id = 0) })
        val added = transactions.count() - before

        var labels = 0
        var notes = 0
        parsed.transactions.forEach { t ->
            t.label?.takeIf { it.isNotBlank() }?.let { label ->
                labels += transactions.restoreLabel(t.txId, label, t.labelSource)
            }
            t.note?.takeIf { it.isNotBlank() }?.let { note ->
                notes += transactions.restoreNote(t.txId, note)
            }
        }

        var newRules = 0
        parsed.rules.forEach { r ->
            // Never overwrite a rule that exists: the one on the phone is the newer decision.
            if (rules.forCounterparty(r.counterparty) == null) {
                rules.put(r)
                newRules++
            }
        }

        Log.i(
            TAG,
            "imported: added=" + added + " labels=" + labels + " notes=" + notes +
                " categories=" + newCategories + " rules=" + newRules,
        )
        return Import(added, labels, notes, newCategories, newRules, parsed.problems)
    }
}
