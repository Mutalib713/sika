package gh.mutalib.sika.ui

import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import gh.mutalib.sika.R
import gh.mutalib.sika.ui.theme.Accent
import gh.mutalib.sika.ui.theme.LabelStyle
import gh.mutalib.sika.ui.theme.TextOnGlass

/** The three destinations. Mutalib's decision — see docs/ui-guidelines.md, Navigation. */
enum class Tab(val label: String, val icon: Int) {
    Home("Home", R.drawable.ic_home),
    Report("Report", R.drawable.ic_report),
    Settings("Settings", R.drawable.ic_settings),
}

/**
 * The floating dock. Glass, because it is the navigation layer — the one place Apple's
 * material belongs, alongside the balance capsule.
 *
 * It does **not** carry the specular sweep. The capsule glints because it is the thing
 * being looked at; chrome that glints is chrome demanding attention it has not earned.
 */
@Composable
fun Dock(
    selected: Tab,
    onSelect: (Tab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .height(66.dp)
            .glass(corner = 33.dp, fill = DockFill),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Tab.entries.forEach { tab ->
            val on = tab == selected
            val interaction = remember { MutableInteractionSource() }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .clickable(
                        interactionSource = interaction,
                        indication = null,
                        onClick = { onSelect(tab) },
                    )
                    // 48dp minimum touch target, which the icon + label already exceeds
                    // vertically; this guarantees it horizontally too.
                    .padding(horizontal = 18.dp, vertical = 8.dp),
            ) {
                Icon(
                    painterResource(tab.icon),
                    contentDescription = tab.label,
                    tint = if (on) Accent else TextOnGlass.copy(alpha = 0.72f),
                    modifier = Modifier.size(21.dp),
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    tab.label,
                    style = LabelStyle.copy(letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified),
                    color = if (on) Accent else TextOnGlass.copy(alpha = 0.72f),
                )
            }
        }
    }
}

/**
 * Whether this phone wants animation at all.
 *
 * ⚠ On Android the honest check is the **system animator scale**, not a Compose flag. A
 * user who turns animations off in Developer options or a battery saver that does it for
 * them both land here, and both mean the same thing: hold still.
 *
 * docs/ui-guidelines.md makes honouring this non-optional — reduced motion kills the aura
 * drift, the specular sweep and the balance count-up together.
 */
fun animationsEnabled(context: Context): Boolean =
    Settings.Global.getFloat(
        context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f,
    ) != 0f
