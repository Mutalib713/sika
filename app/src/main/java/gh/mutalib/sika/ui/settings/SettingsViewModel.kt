package gh.mutalib.sika.ui.settings

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import gh.mutalib.sika.data.BackupIo
import gh.mutalib.sika.data.CategoryEntity
import gh.mutalib.sika.data.Reconciled
import gh.mutalib.sika.data.RuleEntity
import gh.mutalib.sika.data.SikaDatabase
import gh.mutalib.sika.data.TermEntity
import gh.mutalib.sika.data.Terms
import gh.mutalib.sika.data.TransactionEntity
import gh.mutalib.sika.notify.NotificationPrefs
import gh.mutalib.sika.ui.agree
import gh.mutalib.sika.ui.count
import gh.mutalib.sika.ui.home.ACCRA
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * One category as the Categories screen needs to see it: the row itself, and how many
 * transactions point at it.
 *
 * [uses] is what decides whether a put-away row can be *selected* for deleting. It is read from
 * the transactions table rather than stored on the category, because a stored count is a second
 * copy of a fact and second copies drift.
 *
 * ⚠ **It no longer decides which button a row shows.** Until 2026-09-03 an unused category got
 * a bin and a used one got the minus, which meant Mutalib's phone — where only Food had ever
 * been used — showed exactly one minus and six bins. Putting a category away is now offered on
 * every row regardless of use, because it is reversible and harmless; deleting moved to a
 * long-press on the put-away side, where the thing being deleted is already out of the way.
 */
data class CategoryRow(val category: CategoryEntity, val uses: Int) {
    val canDelete: Boolean get() = uses == 0 && !category.isProtected
    val canHide: Boolean get() = !category.isProtected
}

data class SettingsState(
    val loading: Boolean = true,
    val transactions: Int = 0,
    val gaps: Int = 0,
    val unlabelled: Int = 0,
    val needsReview: Int = 0,
    val categories: List<CategoryRow> = emptyList(),
    val rulesCount: Int = 0,
) {
    val inUse: List<CategoryRow> get() = categories.filter { !it.category.isHidden }
    val putAway: List<CategoryRow> get() = categories.filter { it.category.isHidden }
    /** True when every transaction on record balances. The one claim worth leading with. */
    val allBalancing: Boolean get() = gaps == 0
}

/** What just happened, shown once and then dismissed. Null means nothing to say. */
data class Toast(val text: String, val bad: Boolean = false)

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val db = SikaDatabase.get(app)
    private val transactions = db.transactions()
    private val categoryDao = db.categories()
    private val ruleDao = db.rules()

    private val _toast = MutableStateFlow<Toast?>(null)
    val toast: StateFlow<Toast?> = _toast
    fun clearToast() { _toast.value = null }

    /**
     * The full result of an import, held only when there is something a one-line toast cannot
     * say — namely *which* rows could not be read.
     *
     * ⚠ A count is not enough here. "3 rows could not be read" tells you something went wrong
     * and gives you no way to look at it; the line numbers let the file be opened and the rows
     * found. Mutalib's own instinct about gaps applies to this too: knowing is the point.
     */
    private val _importReport = MutableStateFlow<BackupIo.Import?>(null)
    val importReport: StateFlow<BackupIo.Import?> = _importReport
    fun clearImportReport() { _importReport.value = null }

    private val _busy = MutableStateFlow(false)

    /** True while a file is being read or written, so the row can say so instead of looking dead. */
    val busy: StateFlow<Boolean> = _busy

    val state: StateFlow<SettingsState> = combine(
        categoryDao.observeAll(),
        categoryDao.observeUsage(),
        transactions.observeAll(),
        transactions.observeReviewQueue().map { it.size },
        ruleDao.observeAll().map { it.size },
    ) { categories, usage, all, review, ruleCount ->
        val uses = usage.associate { it.name to it.uses }
        SettingsState(
            loading = false,
            transactions = all.size,
            gaps = all.count { it.reconciled == Reconciled.GAP },
            unlabelled = all.count { it.label == null },
            needsReview = review,
            categories = categories.map { CategoryRow(it, uses[it.name] ?: 0) },
            rulesCount = ruleCount,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsState())

    val rules: StateFlow<List<RuleEntity>> = ruleDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Messages the parser refused, newest first. Sacred Rule 7 made visible. */
    val reviewQueue: StateFlow<List<TransactionEntity>> = transactions.observeReviewQueue()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ------------------------------------------------------------------ categories

    fun setHidden(row: CategoryRow, hidden: Boolean) {
        viewModelScope.launch {
            categoryDao.setHidden(row.category.id, hidden)
            _toast.value = Toast(
                if (hidden) {
                    "${row.category.name} put away. Its transactions keep the label."
                } else {
                    "${row.category.name} is back in the list."
                },
            )
        }
    }

    /**
     * Deletes a selection of put-away categories, and reports honestly on any it could not.
     *
     * ⚠ **Re-checks usage inside the transaction rather than trusting the screen.** The rows
     * on screen were read a moment ago; a cash-out notification answered in the shade since
     * then could have put a transaction into one of these very categories. The database is the
     * only thing that knows, and it is asked at the moment of deleting.
     *
     * ⚠ **A refusal is never silent, and never destructive.** Anything that turns out to hold
     * transactions is put away instead and named in the message. The alternative — deleting it
     * anyway — keeps the money and throws away the only record of what the money was for.
     *
     * ⚠ **Takes a list because deleting is a selection now, not a per-row button.** Mutalib's
     * call on 2026-09-03: every category gets the minus, and the bin moved to a long-press on
     * the put-away side. Looping a single-row version here would fire one toast per row and
     * leave whichever landed last on screen.
     */
    fun deleteMany(rows: List<CategoryRow>) {
        if (rows.isEmpty()) return
        viewModelScope.launch {
            val kept = mutableListOf<String>()
            var deleted = 0
            for (row in rows) {
                val name = row.category.name
                if (categoryDao.deleteIfUnused(row.category.id, name)) {
                    deleted++
                } else {
                    categoryDao.setHidden(row.category.id, true)
                    kept += name
                }
            }
            _toast.value = when {
                kept.isEmpty() -> Toast(count(deleted, "category", "categories") + " deleted.")
                deleted == 0 -> Toast(
                    kept.joinToString(" and ") + " " + agree(kept.size, "has", "have") +
                        " transactions now, so " + agree(kept.size, "it was", "they were") +
                        " put away instead.",
                    bad = true,
                )
                else -> Toast(
                    count(deleted, "category", "categories") + " deleted. " +
                        kept.joinToString(" and ") + " " + agree(kept.size, "has", "have") +
                        " transactions now, so " + agree(kept.size, "it was", "they were") +
                        " put away instead.",
                    bad = true,
                )
            }
        }
    }

    fun add(name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            val existing = categoryDao.all().firstOrNull { it.name.equals(clean, ignoreCase = true) }
            if (existing != null) {
                // Adding a name that is only put away should bring it back, not fail silently
                // with "already exists" on a category the list is not even showing.
                if (existing.isHidden) {
                    categoryDao.setHidden(existing.id, false)
                    _toast.value = Toast("$clean was put away. It is back in the list.")
                } else {
                    _toast.value = Toast("You already have a $clean.", bad = true)
                }
                return@launch
            }
            val order = (categoryDao.all().maxOfOrNull { it.sortOrder } ?: 0) + 1
            categoryDao.insert(CategoryEntity(name = clean, sortOrder = order))
            _toast.value = Toast("$clean added.")
        }
    }

    // ------------------------------------------------------------------ rules

    fun forget(rule: RuleEntity) {
        viewModelScope.launch {
            ruleDao.delete(rule.counterparty)
            _toast.value = Toast("Sika will stop labelling ${rule.counterparty} on its own.")
        }
    }

    // ------------------------------------------------------------------ the file

    fun export(uri: Uri) {
        viewModelScope.launch {
            _busy.value = true
            val result = BackupIo.export(getApplication(), uri)
            _busy.value = false
            _toast.value = result.error?.let { Toast(it, bad = true) }
                ?: Toast("Saved " + count(result.rows, "transaction") + ", with every label.")
        }
    }

    fun import(uri: Uri) {
        viewModelScope.launch {
            _busy.value = true
            val r = BackupIo.import(getApplication(), uri)
            _busy.value = false
            when {
                r.error != null -> _toast.value = Toast(r.error, bad = true)
                r.changedNothing -> _toast.value = Toast("Everything in that file was already here.")
                // Something was skipped: the numbers deserve a panel, not a line that
                // disappears in three seconds.
                r.problems.isNotEmpty() -> _importReport.value = r
                else -> _toast.value = Toast(summarise(r))
            }
        }
    }

    /**
     * Says what changed, in the order that matters, and leaves out the zeroes.
     *
     * Both sentences come from [headline] and [skipped], which the report dialog also uses, so
     * the toast and the dialog cannot describe the same restore differently.
     */
    private fun summarise(r: BackupIo.Import): String {
        val head = headline(r)
        return if (r.problems.isEmpty()) head else head + " " + skipped(r.problems.size)
    }
}

/**
 * The named semesters, and the three things you can do to one.
 *
 * ⚠ **Its own ViewModel rather than more fields on [SettingsViewModel].** Terms are read by
 * the report as well as by Settings, and the report has no business constructing a Settings
 * ViewModel to get at them. Small and separate beats large and shared.
 */
class TermsViewModel(app: Application) : AndroidViewModel(app) {

    private val dao = SikaDatabase.get(app).terms()

    val terms: StateFlow<List<TermEntity>> =
        dao.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        // Carries a pre-2026-09-03 single term into the list. Idempotent — see Terms.ensureSeeded.
        viewModelScope.launch { Terms.ensureSeeded(getApplication(), dao) }
    }

    /**
     * Writes one, whether it is new or an edit.
     *
     * ⚠ **id == 0 means "not saved yet"**, which is Room's own convention for an
     * autoGenerate primary key. Adding no longer inserts a row on the spot — see
     * SemestersScreen — so this is the single place a semester reaches the table, new or not.
     */
    fun save(term: TermEntity) {
        viewModelScope.launch {
            if (term.id == 0L) {
                dao.insert(term)
            } else {
                dao.update(term)
                // ⚠ **Editing a semester re-arms both of its alerts.** The "already told"
                // key is built from the row id, which does not change when the dates do — so
                // without this, moving an end date forward would pass in silence, because
                // Sika still believes it has announced that semester's ending.
                NotificationPrefs.forgetTermTold(getApplication(), term.id)
            }
        }
    }

    /** How many are recorded, so the editor can suggest a name for the next one. */
    suspend fun count(): Int = dao.count()

    /** ⚠ Removes the grouping, never a transaction. See SemestersScreen for why. */
    fun delete(term: TermEntity) {
        viewModelScope.launch {
            dao.delete(term.id)
            // Nothing points at a deleted term, so its record is dead weight — and an id
            // Room later reuses would arrive pre-silenced.
            NotificationPrefs.forgetTermTold(getApplication(), term.id)
        }
    }
}
