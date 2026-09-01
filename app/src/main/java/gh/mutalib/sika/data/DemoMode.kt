package gh.mutalib.sika.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * A holder for fabricated rows, so a screen can be seen full without inventing money.
 *
 * ⚠ **Nothing here is ever written to the database, and that is the entire point.** Earlier
 * sessions injected test transactions through the real ingest path to see a screen populated,
 * and every one became a genuine row that had to be hunted down and deleted — one even
 * tripped reconciliation, correctly, because its balances were made up. Fabricated money in a
 * ledger whose whole argument is that its arithmetic checks out is not acceptable, even
 * briefly.
 *
 * So demo rows live here, in memory, and are read *instead of* the ledger while they are set.
 * They vanish when the process dies. The database is never opened for them.
 *
 * ⚠ **A StateFlow, not a plain var.** The first version was a `@Volatile var` read inside the
 * screens' `map { }`, which meant setting it changed nothing on screen: that block only re-runs
 * when the database emits, and switching a flag is not a database change. The screen kept
 * showing the real ledger and the flag looked broken. Being a flow is what makes it a source
 * the UI can actually observe.
 *
 * Only debug builds ever populate this — see `src/debug/.../DebugSmsReceiver.kt`. In a release
 * build it holds null for the lifetime of the app.
 */
object DemoMode {
    private val _rows = MutableStateFlow<List<TransactionEntity>?>(null)
    val rows: StateFlow<List<TransactionEntity>?> = _rows

    fun set(value: List<TransactionEntity>?) {
        _rows.value = value
    }
}
