package gh.mutalib.sika

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import gh.mutalib.sika.notify.CashOutPrompt
import gh.mutalib.sika.notify.DailyNudge
import gh.mutalib.sika.sms.Sweeper
import gh.mutalib.sika.ui.Aura
import gh.mutalib.sika.ui.Dock
import gh.mutalib.sika.ui.Tab
import gh.mutalib.sika.ui.animationsEnabled
import gh.mutalib.sika.ui.scrimBehindDock
import gh.mutalib.sika.ui.home.ACCRA
import gh.mutalib.sika.ui.home.AllTransactionsScreen
import gh.mutalib.sika.ui.home.HomeScreen
import gh.mutalib.sika.ui.home.HomeViewModel
import gh.mutalib.sika.ui.home.LoadingState
import gh.mutalib.sika.ui.home.TransactionSheet
import gh.mutalib.sika.ui.report.ReportScreen
import gh.mutalib.sika.ui.report.ReportViewModel
import gh.mutalib.sika.ui.theme.Accent
import gh.mutalib.sika.ui.theme.AccentContrast
import gh.mutalib.sika.ui.theme.Bg
import gh.mutalib.sika.ui.theme.LocalSikaColors
import gh.mutalib.sika.ui.theme.SikaTheme
import gh.mutalib.sika.ui.theme.ThemePreference
import gh.mutalib.sika.ui.theme.SurfaceRaised
import gh.mutalib.sika.ui.theme.TextMuted
import gh.mutalib.sika.ui.theme.TextPrimary

/** One tag for the whole app, so `adb logcat -s Sika` shows everything and nothing else. */
const val TAG = "Sika"

class MainActivity : ComponentActivity() {

    /**
     * The row a cash-out notification asked us to open, or null.
     *
     * Held on the activity rather than inside the composable because it arrives on an
     * Intent, which is an activity-level event — and it must survive the case where Sika is
     * already running, where [onNewIntent] delivers it instead of [onCreate].
     */
    private val openRow = mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "MainActivity started")
        openRow.value = rowIdFrom(intent)
        enableEdgeToEdge()
        setContent {
            // Loaded once, then held in composition. Saved on every change so the choice
            // survives a relaunch.
            var mode by rememberSaveable { mutableStateOf(ThemePreference.load(this)) }
            SikaTheme(
                mode = mode,
                onModeChange = { mode = it; ThemePreference.save(this, it) },
            ) {
                // ⚠ The status bar is drawn by Android, not by Sika, so the theme does not
                // reach it on its own. Caught on the device 2026-09-01: in light mode the
                // clock and the notification icons stayed white on a near-white page and
                // were all but invisible. `isAppearanceLightStatusBars` asks the system for
                // DARK glyphs — the flag is named for the background, not the icons, which
                // is the easiest thing in this API to get backwards.
                val darkTheme = !LocalSikaColors.current.isDark
                val view = LocalView.current
                LaunchedEffect(darkTheme) {
                    WindowCompat.getInsetsController(window, view).apply {
                        isAppearanceLightStatusBars = darkTheme
                        isAppearanceLightNavigationBars = darkTheme
                    }
                }
                SikaApp(openRow)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Without setIntent the activity keeps reporting the Intent it was created with,
        // so a second notification tap would silently reopen the first row.
        setIntent(intent)
        openRow.value = rowIdFrom(intent)
    }

    private fun rowIdFrom(intent: Intent?): Long? =
        intent?.getLongExtra(CashOutPrompt.EXTRA_ROW_ID, -1L)?.takeIf { it > 0L }
}

private sealed interface Gate {
    data object NeedsPermission : Gate
    data object Denied : Gate
    data object Sweeping : Gate
    data object Ready : Gate
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SikaApp(openRow: MutableState<Long?>) {
    val context = LocalContext.current
    val animated = remember { animationsEnabled(context) }

    var gate by remember {
        mutableStateOf<Gate>(
            if (SMS_PERMISSIONS.all {
                    ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
                }
            ) {
                Gate.Sweeping
            } else {
                Gate.NeedsPermission
            },
        )
    }

    val ask = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        // Both or neither. READ_SMS alone gives a ledger that only updates when the app is
        // opened; RECEIVE_SMS alone gives no history at all.
        gate = if (results.values.all { it }) Gate.Sweeping else Gate.Denied
    }

    LaunchedEffect(gate) {
        if (gate is Gate.Sweeping) {
            Sweeper.sweep(context)
            gate = Gate.Ready
        }
    }

    // Re-booked on every launch rather than once ever. Alarms do not survive a reinstall,
    // a "force stop", or Android reclaiming them, and re-setting one that already exists
    // is free — the PendingIntent matches and simply replaces it.
    LaunchedEffect(Unit) { DailyNudge.schedule(context, ACCRA) }

    // Notifications are asked for SECOND, and only once SMS is granted.
    //
    // Two dialogs at once is how you get both refused: the first is the one the whole app
    // depends on, and stacking a second on top of it turns a considered yes into a reflex
    // dismissal. This one is also genuinely optional — refusing it costs the cash-out
    // prompt and the monthly report, not the ledger.
    val askNotifications = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* Either answer is fine. CashOutPrompt.canPost checks before every post. */ }

    LaunchedEffect(gate) {
        if (gate is Gate.Ready && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !CashOutPrompt.canPost(context)
        ) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    when (gate) {
        Gate.NeedsPermission -> Curtain(
            animated,
            actions = {
                PrimaryAction("Allow SMS access") { ask.launch(SMS_PERMISSIONS) }
                Spacer(Modifier.height(6.dp))
                QuietAction("Not now") { gate = Gate.Denied }
            },
        ) {
            Title("Sika reads your MoMo messages")
            Spacer(Modifier.height(6.dp))
            Muted("Only messages from MoMo. Nothing else is ever read.")
            Muted("Nothing leaves your phone. Sika has no internet access.")
            Muted("Your whole history appears straight away.")
        }

        Gate.Denied -> Curtain(
            animated,
            actions = {
                PrimaryAction("Allow SMS access") { ask.launch(SMS_PERMISSIONS) }
            },
        ) {
            Title("Sika can't see your messages")
            Spacer(Modifier.height(6.dp))
            Muted("Without SMS access there is nothing to track.")
            Muted("If the prompt no longer appears, turn it on in Settings → Apps → Sika.")
        }

        // A skeleton of the screen that is coming, not a spinner. See LoadingState.
        Gate.Sweeping -> LoadingState(animated = animated)

        Gate.Ready -> {
            val vm: HomeViewModel = viewModel()
            val state by vm.state.collectAsStateWithLifecycle()
            var tab by remember { mutableStateOf(Tab.Home) }

            // A single flag rather than a nav library: two destinations, one of which is
            // reached from the other. Reach for Navigation Compose when there are more.
            var showingAll by remember { mutableStateOf(false) }

            // ⚠ Without this, the system back button LEAVES THE APP from the transactions
            // list, because nothing was ever pushed onto the back stack — `showingAll` is a
            // flag, and Android has no idea it means "somewhere else". Found by Mutalib on
            // 2026-09-01. Enabled only while that screen is up, so back keeps its normal
            // meaning everywhere else.
            BackHandler(enabled = showingAll) { showingAll = false }

            val categories by vm.categories.collectAsStateWithLifecycle()
            val refreshing by vm.refreshing.collectAsStateWithLifecycle()
            var sheetFor by remember { mutableStateOf<Long?>(null) }

            // A cash-out prompt was tapped on its body rather than a button: open that
            // row's sheet, where every category is available. Cleared immediately so
            // dismissing the sheet does not reopen it on the next recomposition.
            LaunchedEffect(openRow.value) {
                openRow.value?.let { id ->
                    sheetFor = id
                    openRow.value = null
                }
            }
            // Re-read from state each recomposition so the sheet updates the moment a
            // category is picked, rather than showing the row as it was when tapped.
            val sheetRow = sheetFor?.let { id -> state.days.flatMap { it.rows }.firstOrNull { it.id == id } }

            Box(Modifier.fillMaxSize()) {
                when {
                    showingAll -> AllTransactionsScreen(
                        state = state,
                        animated = animated,
                        onBack = { showingAll = false },
                        onTransactionClick = { sheetFor = it.id },
                    )

                    tab == Tab.Report -> {
                        val rvm: ReportViewModel = viewModel()
                        val summary by rvm.summary.collectAsStateWithLifecycle()
                        val reportMode by rvm.mode.collectAsStateWithLifecycle()
                        val canForward by rvm.canStepForward.collectAsStateWithLifecycle()
                        ReportScreen(
                            summary = summary,
                            mode = reportMode,
                            canStepForward = canForward,
                            animated = animated,
                            onStep = rvm::step,
                            onMode = rvm::setMode,
                        )
                    }

                    tab == Tab.Settings -> Curtain(animated) {
                        Title("Settings")
                        Spacer(Modifier.height(6.dp))
                        Muted("Categories, learned rules, and CSV export and import.")
                        Muted("Not built yet — PLAN task 15.")
                    }

                    else -> HomeScreen(
                        state = state,
                        animated = animated,
                        refreshing = refreshing,
                        onRefresh = { vm.refresh(context) },
                        onSeeAll = { showingAll = true },
                        onOpenReport = { tab = Tab.Report },
                        onTransactionClick = { sheetFor = it.id },
                    )
                }
                // Fades the list out before it reaches the dock, so rows never collide with
                // the dock's own labels. Measured problem, 2026-08-31 — see Glass.DockFill.
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(150.dp)
                        .scrimBehindDock(Bg),
                )
                Dock(
                    selected = tab,
                    // ⚠ Choosing a tab leaves the transactions list AND closes any open
                    // sheet. Without the first reset the dock looked dead from that screen:
                    // the tab highlight moved while the branch below still matched
                    // `showingAll`, so nothing changed. Without the second, the sheet stayed
                    // floating over whichever screen you switched to — it belongs to a row
                    // on the list you just left, so it has nothing to say about Home.
                    onSelect = { tab = it; showingAll = false; sheetFor = null },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(horizontal = 22.dp, vertical = 22.dp),
                )
            }

            if (sheetRow != null) {
                ModalBottomSheet(
                    onDismissRequest = { sheetFor = null },
                    containerColor = SurfaceRaised,
                    dragHandle = null,
                ) {
                    TransactionSheet(
                        row = sheetRow,
                        categories = categories,
                        onPick = { category, alsoRemember ->
                            vm.setCategory(sheetRow, category, alsoRemember)
                        },
                        onNote = { vm.setNote(sheetRow, it) },
                        onAddCategory = vm::addCategory,
                        onDismiss = { sheetFor = null },
                    )
                }
            }
        }
    }
}

/**
 * The pre-ledger screens: aura behind, words in the middle, **actions pinned to the bottom.**
 *
 * ⚠ The buttons used to sit in the middle with the copy. Mutalib asked for the pattern every
 * other app uses, and he is right that it is the convention — for a good reason: on a 6.7in
 * phone the bottom of the screen is the only part the thumb reaches without a regrip, and a
 * permission prompt is the one moment you must not make someone shuffle their hand.
 */
@Composable
private fun Curtain(
    animated: Boolean,
    actions: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        Aura(animated = animated)
        Column(
            Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 28.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = content,
            )
            Spacer(Modifier.weight(1f))
            if (actions != null) Column(Modifier.fillMaxWidth(), content = actions)
        }
    }
}

/** Full-width, accent-filled. Dark text on the aqua — never white (docs/ui-guidelines.md). */
@Composable
private fun PrimaryAction(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(Accent)
            .clickable(onClick = onClick)
            .padding(vertical = 17.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, color = AccentContrast)
    }
}

/** The way out. Present but quiet — a refusal should not need hunting for. */
@Composable
private fun QuietAction(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 17.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, color = TextMuted)
    }
}

@Composable
private fun Title(text: String) = Text(
    text,
    style = MaterialTheme.typography.headlineSmall,
    color = TextPrimary,
    textAlign = TextAlign.Center,
)

@Composable
private fun Muted(text: String) = Text(
    text,
    style = MaterialTheme.typography.bodyMedium,
    color = TextMuted,
    textAlign = TextAlign.Center,
    modifier = Modifier.widthIn(max = 320.dp),
)

/**
 * READ_SMS reads the history and powers the sweep; RECEIVE_SMS wakes the app when a message
 * arrives. Separate runtime permissions despite sharing a group, and Sika needs both.
 */
private val SMS_PERMISSIONS = arrayOf(
    Manifest.permission.READ_SMS,
    Manifest.permission.RECEIVE_SMS,
)
