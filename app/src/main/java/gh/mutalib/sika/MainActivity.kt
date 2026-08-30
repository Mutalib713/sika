package gh.mutalib.sika

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import gh.mutalib.sika.sms.SweepReport
import gh.mutalib.sika.sms.Sweeper
import gh.mutalib.sika.ui.theme.Accent
import gh.mutalib.sika.ui.theme.SikaTheme
import gh.mutalib.sika.ui.theme.TextMuted
import gh.mutalib.sika.ui.theme.Warn
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** One tag for the whole app, so `adb logcat -s Sika` shows everything and nothing else. */
const val TAG = "Sika"

/**
 * PLAN task 5: ask for SMS access, sweep the inbox, and report what was found.
 *
 * This screen is a **diagnostic**, not the product. Its job is to answer the open question
 * in PROFILE.md § 11 — are there MoMo message shapes beyond the four confirmed ones? — by
 * showing real counts from a real inbox. The Home screen replaces it at task 10.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "MainActivity started")
        enableEdgeToEdge()
        setContent {
            SikaTheme {
                Scaffold { insets ->
                    SweepScreen(Modifier.padding(insets))
                }
            }
        }
    }
}

private sealed interface State {
    data object NeedsPermission : State
    data object Denied : State
    data object Sweeping : State
    data class Done(val report: SweepReport) : State
}

@Composable
private fun SweepScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val granted = remember {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) ==
            PackageManager.PERMISSION_GRANTED
    }
    var state by remember {
        mutableStateOf<State>(if (granted) State.Sweeping else State.NeedsPermission)
    }

    val ask = rememberLauncher { allowed ->
        state = if (allowed) State.Sweeping else State.Denied
    }

    LaunchedEffect(state) {
        if (state is State.Sweeping) {
            state = State.Done(Sweeper.sweep(context))
        }
    }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (val s = state) {
            State.NeedsPermission -> Ask(onAsk = { ask(Manifest.permission.READ_SMS) })
            State.Denied -> Blocked()
            State.Sweeping -> {
                CircularProgressIndicator(color = Accent)
                Muted("Reading your MoMo messages…")
            }
            is State.Done -> Report(s.report)
        }
    }
}

@Composable
private fun Ask(onAsk: () -> Unit) {
    Title("Sika reads your MoMo messages")
    Muted("Only messages from MoMo. Nothing else is ever read.")
    Muted("Nothing leaves your phone. Sika has no internet access.")
    Muted("Your whole history appears straight away.")
    Button(onClick = onAsk) { Text("Allow SMS access") }
}

@Composable
private fun Blocked() {
    Title("Sika can't see your messages")
    Muted("Without SMS access there is nothing to track. Grant it in Settings → Apps → Sika.")
}

@Composable
private fun Report(r: SweepReport) {
    Title("Sweep complete")

    // PLAN task 5's stated verification, on screen as well as in logcat.
    Stat("Messages matched", r.found.toString())
    Stat("Transactions parsed", r.parsed.toString())
    Stat("Not transactions (OTPs, adverts)", r.notTransactions.toString())
    Stat("Unrecognised — need review", r.unrecognised.toString(), if (r.unrecognised > 0) Warn else null)
    Stat("New this sweep", r.newlyAdded.toString())
    Stat("Rows in ledger", r.totalInLedger.toString())
    Stat("Oldest message", r.oldest.asDate())
    Stat("Newest message", r.newest.asDate())

    if (r.senders.isNotEmpty()) {
        Muted("Senders seen: " + r.senders.joinToString { "${it.first} (${it.second})" })
    }
    r.unrecognisedSamples.forEachIndexed { i, body ->
        Muted("unrecognised ${i + 1}: ${body.take(160)}")
    }
}

// ------------------------------------------------------------------ small shared pieces

@Composable
private fun rememberLauncher(onResult: (Boolean) -> Unit): (String) -> Unit {
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
        onResult,
    )
    return { permission -> launcher.launch(permission) }
}

@Composable
private fun Title(text: String) = Text(
    text = text,
    style = MaterialTheme.typography.headlineSmall,
    textAlign = TextAlign.Center,
)

@Composable
private fun Muted(text: String) = Text(
    text = text,
    style = MaterialTheme.typography.bodySmall,
    color = TextMuted,
    textAlign = TextAlign.Center,
    modifier = Modifier.widthIn(max = 320.dp),
)

@Composable
private fun Stat(label: String, value: String, emphasis: androidx.compose.ui.graphics.Color? = null) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextMuted)
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            color = emphasis ?: MaterialTheme.colorScheme.onBackground,
        )
    }
}

private val DATE = DateTimeFormatter.ofPattern("d MMM yyyy")

/** Africa/Accra is UTC+0 with no DST, which is why this needs no seasonal thought. */
private fun Long?.asDate(): String = this?.let {
    Instant.ofEpochMilli(it).atZone(ZoneId.of("Africa/Accra")).format(DATE)
} ?: "—"
