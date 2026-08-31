package gh.mutalib.sika

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import gh.mutalib.sika.sms.Sweeper
import gh.mutalib.sika.ui.Aura
import gh.mutalib.sika.ui.Dock
import gh.mutalib.sika.ui.Tab
import gh.mutalib.sika.ui.animationsEnabled
import gh.mutalib.sika.ui.scrimBehindDock
import gh.mutalib.sika.ui.home.AllTransactionsScreen
import gh.mutalib.sika.ui.home.HomeScreen
import gh.mutalib.sika.ui.home.HomeViewModel
import gh.mutalib.sika.ui.theme.Accent
import gh.mutalib.sika.ui.theme.AccentContrast
import gh.mutalib.sika.ui.theme.Bg
import gh.mutalib.sika.ui.theme.SikaTheme
import gh.mutalib.sika.ui.theme.TextMuted
import gh.mutalib.sika.ui.theme.TextPrimary

/** One tag for the whole app, so `adb logcat -s Sika` shows everything and nothing else. */
const val TAG = "Sika"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "MainActivity started")
        enableEdgeToEdge()
        setContent { SikaTheme { SikaApp() } }
    }
}

private sealed interface Gate {
    data object NeedsPermission : Gate
    data object Denied : Gate
    data object Sweeping : Gate
    data object Ready : Gate
}

@Composable
private fun SikaApp() {
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

        Gate.Sweeping -> Curtain(animated) {
            CircularProgressIndicator(color = Accent)
            Muted("Reading your MoMo messages…")
        }

        Gate.Ready -> {
            val vm: HomeViewModel = viewModel()
            val state by vm.state.collectAsStateWithLifecycle()
            var tab by remember { mutableStateOf(Tab.Home) }

            // A single flag rather than a nav library: two destinations, one of which is
            // reached from the other. Reach for Navigation Compose when there are more.
            var showingAll by remember { mutableStateOf(false) }

            Box(Modifier.fillMaxSize()) {
                if (showingAll) {
                    AllTransactionsScreen(
                        state = state,
                        animated = animated,
                        onBack = { showingAll = false },
                    )
                } else {
                    HomeScreen(
                        state = state,
                        animated = animated,
                        onSeeAll = { showingAll = true },
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
                    onSelect = { tab = it },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(horizontal = 22.dp, vertical = 22.dp),
                )
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
