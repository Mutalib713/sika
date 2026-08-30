package gh.mutalib.sika

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import gh.mutalib.sika.ui.theme.SikaTheme
import gh.mutalib.sika.ui.theme.TextMuted

/**
 * PLAN task 1: this exists so the project compiles and `check` means something.
 *
 * The real empty state arrives at task 2, and the Home screen at task 10. Nothing here
 * asks for a permission or touches the SMS provider yet — that starts at task 6.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SikaTheme {
                Scaffold { insets ->
                    Placeholder(Modifier.padding(insets))
                }
            }
        }
    }
}

@Composable
private fun Placeholder(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Sika", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Nothing here yet.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted,
        )
    }
}
