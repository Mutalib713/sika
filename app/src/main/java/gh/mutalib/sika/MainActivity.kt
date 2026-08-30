package gh.mutalib.sika

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import gh.mutalib.sika.ui.theme.SikaTheme
import gh.mutalib.sika.ui.theme.TextMuted

/** One tag for the whole app, so `adb logcat -s Sika` shows everything and nothing else. */
const val TAG = "Sika"

/**
 * PLAN task 2: the app runs on the phone and shows a designed empty state.
 *
 * Nothing here asks for a permission or touches the SMS provider — that starts at task 6.
 * The Home screen replaces this at task 10.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // The verification hook for this task. It also earns its place afterwards: this is
        // the line that says the process actually started, which is the first thing worth
        // knowing when the SMS receiver stops firing at task 6.
        Log.i(TAG, "MainActivity started — no ledger yet, nothing to show")

        enableEdgeToEdge()
        setContent {
            SikaTheme {
                Scaffold { insets ->
                    NothingYet(Modifier.padding(insets))
                }
            }
        }
    }
}

/**
 * The "no transactions" empty state from docs/screens.md § 1.
 *
 * It is a real screen rather than a blank one on purpose — ui-guidelines.md: every list has
 * a designed empty state. Right now it is also simply true: there is no parser, so there
 * are no transactions.
 *
 * The copy says what will happen rather than what is missing. "Transactions appear here as
 * MoMo texts arrive" tells you the app is waiting; "No data" tells you nothing.
 */
@Composable
private fun NothingYet(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Nothing yet this month",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "Transactions appear here as MoMo texts arrive.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 280.dp),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF101422)
@Composable
private fun NothingYetPreview() {
    SikaTheme { NothingYet() }
}
