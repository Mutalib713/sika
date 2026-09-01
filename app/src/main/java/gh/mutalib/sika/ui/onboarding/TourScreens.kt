package gh.mutalib.sika.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import gh.mutalib.sika.R
import gh.mutalib.sika.ui.theme.Accent
import gh.mutalib.sika.ui.theme.AccentContrast
import gh.mutalib.sika.ui.theme.Border
import gh.mutalib.sika.ui.theme.SurfaceRaised
import gh.mutalib.sika.ui.theme.TextMuted
import gh.mutalib.sika.ui.theme.TextPrimary
import gh.mutalib.sika.ui.theme.Warn
import gh.mutalib.sika.ui.theme.categoryColor
import gh.mutalib.sika.ui.theme.categoryIcon

/**
 * The tour — four screens, and every one of them earns its place by the same test:
 * **can you find this out by using the app?**
 *
 * ⚠ **Mutalib overruled the recommendation not to have one, and his reason was better than my
 * objection.** I was arguing against decorative feature cards — three icons and two grey lines.
 * His point was different: Sika has features that cannot be discovered. Reconciliation only
 * shows itself the day something breaks, when it is alarming rather than reassuring. The
 * semester view is one segment in a switcher you might never press.
 *
 * So the three feature screens are the three undiscoverable things, and each shows a real
 * fragment of the app rather than an illustration of the idea. **Learning your shops is
 * deliberately absent** — "always this" sits in the sheet the first time you label anything,
 * so it teaches itself.
 *
 * ⚠ **Skippable from the first screen.** Whatever a tour is worth, it is not worth trapping
 * someone who has already decided.
 */
private const val STEPS = 4

@Composable
fun TourIntro(onNext: () -> Unit, onSkip: () -> Unit) {
    OnboardingFrame(
        step = 1, total = STEPS, onSkip = onSkip,
        primary = "See what it does  ›", onPrimary = onNext,
    ) {
        AppMark()
        Spacer(Modifier.height(20.dp))
        Headline("Sika")
        Spacer(Modifier.height(9.dp))
        Text(
            "Your MoMo messages, turned into a record you can check.",
            style = MaterialTheme.typography.titleMedium,
            color = Accent,
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(14.dp))
        Sub(
            "It reads the MTN MoMo alerts already on this phone and works out where your " +
                "money went. No typing, no account, no internet — and it can prove its own " +
                "totals.",
        )
    }
}

/**
 * ⚠ **A placeholder, and the gap behind it is real.** Sika has no launcher icon: no `mipmap`
 * folder and no `android:icon` in the manifest, so on the home screen it is Android's blank
 * default. Drawing a proper mark is identity work with its own variants to choose from, and
 * doing it inside an onboarding build would be deciding it by accident. The cedi sign holds
 * the space and the layout until then.
 */
@Composable
private fun AppMark() {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(78.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Accent),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "₵",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontSize = androidx.compose.ui.unit.TextUnit(38f, androidx.compose.ui.unit.TextUnitType.Sp),
                    fontWeight = FontWeight.W600,
                ),
                color = AccentContrast,
            )
        }
    }
}

@Composable
fun TourReads(onNext: () -> Unit, onSkip: () -> Unit) {
    OnboardingFrame(
        step = 2, total = STEPS, onSkip = onSkip,
        primary = "Next  ›", onPrimary = onNext,
    ) {
        Headline("You never type anything")
        Spacer(Modifier.height(10.dp))
        Sub("Every MoMo text becomes a row on its own, the second it arrives — even with the app closed.")
        Spacer(Modifier.height(24.dp))
        DemoCard {
            // ⚠ The counterparty is XXX, not a real name — Mutalib's instruction, 2026-09-01.
            // A screen every new install sees is the wrong place for a person who banked with
            // him once, and a placeholder makes the shape of the message just as clear.
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(13.dp))
                    .background(SurfaceRaised)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Text(
                    "MTN MoMo",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    "Payment made for GHS 35.00 to XXX. Current Balance: GHS 71.71…",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextPrimary,
                )
            }
            Box(Modifier.fillMaxWidth().padding(vertical = 7.dp), contentAlignment = Alignment.Center) {
                Icon(
                    painterResource(R.drawable.ic_chevron_right),
                    contentDescription = null,
                    tint = TextMuted,
                    modifier = Modifier.size(16.dp).rotate(90f),
                )
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(13.dp))
                    .background(SurfaceRaised)
                    .padding(horizontal = 11.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val food = categoryColor("Food")
                Box(
                    Modifier
                        .size(27.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(food.copy(alpha = 0.13f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(categoryIcon("Food")),
                        contentDescription = null,
                        tint = food,
                        modifier = Modifier.size(14.dp),
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("XXX", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Text(
                        "Food · 8:05am",
                        style = MaterialTheme.typography.bodySmall,
                        color = food,
                    )
                }
                Text(
                    "−35.00",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                )
            }
        }
    }
}

@Composable
fun TourChecks(onNext: () -> Unit, onSkip: () -> Unit) {
    OnboardingFrame(
        step = 3, total = STEPS, onSkip = onSkip,
        primary = "Next  ›", onPrimary = onNext,
    ) {
        Headline("It checks its own maths")
        Spacer(Modifier.height(10.dp))
        Sub("Every message states your balance, so Sika can prove its totals instead of asking you to trust them.")
        Spacer(Modifier.height(24.dp))
        DemoCard {
            SumLine("Balance on 26 Aug", "GHS 71.71")
            SumLine("You paid on 28 Aug", "− GHS 10.00")
            Spacer(Modifier.height(4.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(Border))
            Spacer(Modifier.height(5.dp))
            SumLine("So it should be", "GHS 61.71")
            SumLine("MoMo says it is", "GHS 41.71")
            Spacer(Modifier.height(9.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(Border))
            Spacer(Modifier.height(9.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painterResource(R.drawable.ic_warning),
                    contentDescription = null,
                    tint = Warn,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "GHS 20.00 with no message",
                    style = MaterialTheme.typography.titleMedium,
                    color = Warn,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Fine("When MTN sends no text at all, this is what catches it.")
    }
}

@Composable
private fun SumLine(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted,
            modifier = Modifier.weight(1f),
        )
        Text(value, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
    }
}

@Composable
fun TourSemester(onNext: () -> Unit, onSkip: () -> Unit) {
    OnboardingFrame(
        step = 4, total = STEPS, onSkip = onSkip,
        primary = "Get started  ›", onPrimary = onNext,
    ) {
        Headline("A semester is not a month")
        Spacer(Modifier.height(10.dp))
        Sub(
            "Report by week, by month, or across the whole semester — the span that actually " +
                "matches how a student's money runs out.",
        )
        Spacer(Modifier.height(24.dp))
        DemoCard {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceRaised)
                    .padding(3.dp),
            ) {
                listOf("Week", "Month", "Semester", "All").forEach { label ->
                    val on = label == "Semester"
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(13.dp))
                            .background(if (on) Accent else androidx.compose.ui.graphics.Color.Transparent)
                            .padding(vertical = 7.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            label,
                            style = MaterialTheme.typography.bodySmall
                                .copy(fontWeight = if (on) FontWeight.W600 else FontWeight.W500),
                            color = if (on) AccentContrast else TextMuted,
                        )
                    }
                }
            }
            Spacer(Modifier.height(13.dp))
            Text("Semester so far", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Text(
                "12 Jan – 30 May · 18 weeks in",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
            )
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth().height(56.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                listOf(22, 38, 30, 52, 41, 26).forEach { h ->
                    Box(
                        Modifier
                            .weight(1f)
                            .padding(horizontal = 3.dp)
                            .height(h.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(Accent.copy(alpha = 0.75f)),
                    )
                }
            }
        }
    }
}
