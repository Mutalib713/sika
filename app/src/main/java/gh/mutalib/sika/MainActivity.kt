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
import androidx.compose.runtime.rememberCoroutineScope
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
import gh.mutalib.sika.data.Backup
import gh.mutalib.sika.data.BackupIo
import gh.mutalib.sika.data.CategoryEntity
import gh.mutalib.sika.ledger.today
import gh.mutalib.sika.notify.CashOutPrompt
import gh.mutalib.sika.notify.DailyNudge
import gh.mutalib.sika.notify.MonthlyReport
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
import gh.mutalib.sika.ui.onboarding.OnboardingFlow
import gh.mutalib.sika.ui.onboarding.OnboardingPrefs
import gh.mutalib.sika.ui.home.TransactionSheet
import gh.mutalib.sika.ui.report.ReportScreen
import gh.mutalib.sika.ui.report.ReportViewModel
import gh.mutalib.sika.ui.settings.CategoriesScreen
import gh.mutalib.sika.ui.settings.ImportReportDialog
import gh.mutalib.sika.ui.settings.LearnedRulesScreen
import gh.mutalib.sika.ui.settings.ReviewQueueScreen
import gh.mutalib.sika.ui.settings.SettingsRoute
import gh.mutalib.sika.ui.settings.SettingsScreen
import gh.mutalib.sika.ui.settings.SettingsToast
import gh.mutalib.sika.ui.settings.SettingsViewModel
import gh.mutalib.sika.ui.theme.Accent
import gh.mutalib.sika.ui.theme.AccentContrast
import gh.mutalib.sika.ui.theme.Bg
import gh.mutalib.sika.ui.theme.LocalSikaColors
import gh.mutalib.sika.ui.theme.SikaTheme
import gh.mutalib.sika.ui.theme.ThemePreference
import gh.mutalib.sika.ui.theme.SurfaceRaised
import gh.mutalib.sika.ui.theme.TextMuted
import gh.mutalib.sika.ui.theme.TextPrimary
import kotlinx.coroutines.launch

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
    /** First run: the tour and the questions. See ui/onboarding/OnboardingFlow.kt. */
    data object Onboarding : Gate
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

    // ⚠ Onboarding outranks the permission gate, and it has to: the flow ASKS for the
    // permission itself, at a point where there is a reason to say yes. Letting the old gate
    // win would show the bare system dialog first and make the tour pointless.
    var gate by remember {
        val granted = SMS_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        mutableStateOf<Gate>(
            when {
                !OnboardingPrefs.done(context) -> Gate.Onboarding
                granted -> Gate.Sweeping
                else -> Gate.NeedsPermission
            },
        )
    }
    var smsGranted by remember {
        mutableStateOf(
            SMS_PERMISSIONS.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            },
        )
    }

    val ask = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        // Both or neither. READ_SMS alone gives a ledger that only updates when the app is
        // opened; RECEIVE_SMS alone gives no history at all.
        val ok = results.values.all { it }
        smsGranted = ok
        // During onboarding the flow decides what comes next; it watches `smsGranted` and
        // moves on by itself. Outside it, the old two-way gate still applies.
        if (gate !is Gate.Onboarding) gate = if (ok) Gate.Sweeping else Gate.Denied
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
    LaunchedEffect(Unit) {
        DailyNudge.schedule(context, ACCRA)
        MonthlyReport.schedule(context, ACCRA)
    }

    // Notifications are asked for SECOND, and only once SMS is granted.
    //
    // Two dialogs at once is how you get both refused: the first is the one the whole app
    // depends on, and stacking a second on top of it turns a considered yes into a reflex
    // dismissal. This one is also genuinely optional — refusing it costs the cash-out
    // prompt and the monthly report, not the ledger.
    val askNotifications = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* Either answer is fine. CashOutPrompt.canPost checks before every post. */ }

    // ⚠ **No automatic notification prompt any more.** It used to fire on reaching Home,
    // which would now be the SECOND time of asking — onboarding puts the case in words first,
    // and Android only ever shows its dialog once. Asking again on every launch after a
    // refusal is how an app teaches someone to refuse harder.

    val scope = rememberCoroutineScope()
    var restoreResult by remember { mutableStateOf<String?>(null) }
    val restoreDuringSetup = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let {
            scope.launch {
                val r = BackupIo.import(context, it)
                restoreResult = r.error ?: when {
                    r.changedNothing -> "That file held nothing new."
                    else -> "Restored " + r.added + " transactions and " + r.labels + " labels."
                }
            }
        }
    }

    when (gate) {
        // ⚠ The restore result is drawn OVER the flow rather than replacing it: importing is
        // a detour, not a destination, and someone who restored still has the rest of first
        // run to finish.
        Gate.Onboarding -> Box(Modifier.fillMaxSize()) {
            OnboardingFlow(
            smsGranted = smsGranted,
            onRequestSms = { ask.launch(SMS_PERMISSIONS) },
            onRequestNotifications = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            },
            // Restoring is the whole reason someone reinstalling is here. Sending them
            // through the rest of the flow first would mean re-labelling by hand before
            // finding the file that had it all.
            onRestore = { restoreDuringSetup.launch(BACKUP_TYPES) },
            onFinished = { gate = if (smsGranted) Gate.Sweeping else Gate.Denied },
            )
            restoreResult?.let { message ->
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(horizontal = 26.dp, vertical = 100.dp),
                ) {
                    SettingsToast(gh.mutalib.sika.ui.settings.Toast(message)) {
                        restoreResult = null
                    }
                }
            }
        }

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

            // Settings has two sub-screens, reached the same flag-based way.
            var settingsRoute by remember { mutableStateOf(SettingsRoute.ROOT) }
            val insideSettings = tab == Tab.Settings && settingsRoute != SettingsRoute.ROOT

            // ⚠ Without this, the system back button LEAVES THE APP from the transactions
            // list, because nothing was ever pushed onto the back stack — `showingAll` is a
            // flag, and Android has no idea it means "somewhere else". Found by Mutalib on
            // 2026-09-01. Enabled only while one of those screens is up, so back keeps its
            // normal meaning everywhere else.
            BackHandler(enabled = showingAll || insideSettings) {
                if (showingAll) showingAll = false else settingsRoute = SettingsRoute.ROOT
            }

            val svm: SettingsViewModel = viewModel()
            val settings by svm.state.collectAsStateWithLifecycle()
            val rules by svm.rules.collectAsStateWithLifecycle()
            val reviewQueue by svm.reviewQueue.collectAsStateWithLifecycle()
            val busy by svm.busy.collectAsStateWithLifecycle()
            val toast by svm.toast.collectAsStateWithLifecycle()
            val importReport by svm.importReport.collectAsStateWithLifecycle()

            // Asked directly rather than inferred from `gate`. Inside Gate.Ready the answer is
            // effectively always yes — Android kills the process when a permission is revoked —
            // but a screen that reports permission state should read the permission, not a flag
            // that happens to imply it.
            val smsGranted = remember {
                SMS_PERMISSIONS.all {
                    ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
                }
            }

            // ⚠ **The Storage Access Framework, so Sika asks for no storage permission.** The
            // system picker returns a Uri for the one file chosen and nothing else; asking for
            // WRITE_EXTERNAL_STORAGE would mean requesting the whole device to save one CSV.
            val exportTo = rememberLauncherForActivityResult(
                ActivityResultContracts.CreateDocument(Backup.MIME),
            ) { uri -> uri?.let(svm::export) }

            val importFrom = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocument(),
            ) { uri -> uri?.let(svm::import) }

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

                    tab == Tab.Settings -> when (settingsRoute) {
                        SettingsRoute.CATEGORIES -> CategoriesScreen(
                            state = settings,
                            animated = animated,
                            onBack = { settingsRoute = SettingsRoute.ROOT },
                            onHide = svm::setHidden,
                            onDelete = svm::delete,
                            onAdd = svm::add,
                        )

                        SettingsRoute.REVIEW -> ReviewQueueScreen(
                            queue = reviewQueue,
                            animated = animated,
                            onBack = { settingsRoute = SettingsRoute.ROOT },
                        )

                        SettingsRoute.RULES -> LearnedRulesScreen(
                            rules = rules,
                            animated = animated,
                            onBack = { settingsRoute = SettingsRoute.ROOT },
                            onForget = svm::forget,
                        )

                        SettingsRoute.ROOT -> SettingsScreen(
                            state = settings,
                            smsGranted = smsGranted,
                            busy = busy,
                            animated = animated,
                            onRoute = { settingsRoute = it },
                            // ⚠ The date is in the name because a folder of files all called
                            // `sika.csv` tells you nothing about which one to restore.
                            onExport = { exportTo.launch(Backup.fileName(today(ACCRA))) },
                            onImport = { importFrom.launch(BACKUP_TYPES) },
                            // ⚠ Clears the "done" flag and drops back into the flow. It does
                            // NOT clear the answers: someone re-watching the tour should find
                            // their own name already in the field, not a blank one.
                            onRunSetupAgain = {
                                OnboardingPrefs.setDone(context, false)
                                settingsRoute = SettingsRoute.ROOT
                                gate = Gate.Onboarding
                            },
                        )
                    }

                    else -> HomeScreen(
                        state = state,
                        animated = animated,
                        refreshing = refreshing,
                        onRefresh = { vm.refresh(context) },
                        onSeeAll = { showingAll = true },
                        onOpenReport = { tab = Tab.Report },
                        onTransactionClick = { sheetFor = it.id },
                        onExplainGap = vm::explainGap,
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
                // Above the dock, never over it: a message you have to move a control to read
                // is a message that will be dismissed unread.
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 104.dp),
                ) {
                    SettingsToast(toast, svm::clearToast)
                }
                Dock(
                    selected = tab,
                    // ⚠ Choosing a tab leaves the transactions list AND closes any open
                    // sheet. Without the first reset the dock looked dead from that screen:
                    // the tab highlight moved while the branch below still matched
                    // `showingAll`, so nothing changed. Without the second, the sheet stayed
                    // floating over whichever screen you switched to — it belongs to a row
                    // on the list you just left, so it has nothing to say about Home.
                    onSelect = {
                        tab = it
                        showingAll = false
                        sheetFor = null
                        // Leaving Settings and coming back should land on Settings, not on
                        // whichever sub-screen was open when you left it.
                        settingsRoute = SettingsRoute.ROOT
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(horizontal = 22.dp, vertical = 22.dp),
                )
            }

            importReport?.let { report ->
                ImportReportDialog(report, onDismiss = svm::clearImportReport)
            }

            if (sheetRow != null) {
                ModalBottomSheet(
                    onDismissRequest = { sheetFor = null },
                    containerColor = SurfaceRaised,
                    dragHandle = null,
                ) {
                    TransactionSheet(
                        row = sheetRow,
                        // ⚠ A row already filed under a put-away category must still show it
                        // as its own. Without this the sheet opens with nothing selected, on a
                        // transaction that plainly has a category — which reads as the label
                        // having been lost.
                        categories = remember(categories, sheetRow.label) {
                            val label = sheetRow.label
                            if (label == null || categories.any { it.name == label }) categories
                            else categories + CategoryEntity(name = label, sortOrder = Int.MAX_VALUE)
                        },
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

/**
 * What the file picker will let you choose when restoring.
 *
 * ⚠ **More than `text/csv`, deliberately.** The same file comes back as `text/comma-separated-
 * values` from some providers and `application/octet-stream` from others — Drive and a few
 * file managers among them — and a picker that greys out the user's own backup is indefensible.
 * A full wildcard is not the answer either: the file is still validated when it is read, and
 * offering every file on the phone invites choosing a photo and being told no.
 */
private val BACKUP_TYPES = arrayOf(
    "text/csv",
    "text/comma-separated-values",
    "text/plain",
    "application/octet-stream",
)
