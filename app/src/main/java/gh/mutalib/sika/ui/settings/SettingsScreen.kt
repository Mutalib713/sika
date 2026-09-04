package gh.mutalib.sika.ui.settings

import android.content.Context
import android.content.Intent
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import gh.mutalib.sika.BuildConfig
import gh.mutalib.sika.R
import gh.mutalib.sika.notify.NotificationPrefs
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import gh.mutalib.sika.ui.Aura
import gh.mutalib.sika.ui.agree
import gh.mutalib.sika.ui.count
import gh.mutalib.sika.ui.home.ACCRA
import gh.mutalib.sika.ui.onboarding.OnboardingPrefs
import gh.mutalib.sika.ui.theme.Accent
import gh.mutalib.sika.ui.theme.Danger
import gh.mutalib.sika.ui.theme.LocalSetThemeMode
import gh.mutalib.sika.ui.theme.LocalThemeMode
import gh.mutalib.sika.ui.theme.TextMuted
import gh.mutalib.sika.ui.theme.TextPrimary
import gh.mutalib.sika.ui.theme.ThemeMode
import gh.mutalib.sika.ui.theme.Warn

/** Where Settings can send you. Kept here so MainActivity has one thing to switch on. */
enum class SettingsRoute { ROOT, CATEGORIES, RULES, REVIEW, SEMESTERS }

/**
 * Settings.
 *
 * ⚠ **The state line at the top is one line, on Mutalib's instruction** — he asked for a
 * state "but not too big" after seeing a variant that opened with a full card. It earns its
 * place because Settings is where you go when you are not sure the app is working, and that
 * question deserves an answer above the fold rather than three taps in.
 *
 * ⚠ **No entrance animation here, unlike Home.** That one plays on a cold start, on the
 * screen you land on. Replaying it on a tab you chose deliberately turns a pleasure into a
 * wait — the same reasoning that made Home's play once per process.
 */
@Composable
fun SettingsScreen(
    state: SettingsState,
    smsGranted: Boolean,
    busy: Boolean,
    animated: Boolean,
    onRoute: (SettingsRoute) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    // ⚠ `onRunSetupAgain` was removed on 2026-09-02 with the "Show the tour again" row. The
    // machinery it drove is intact — `OnboardingPrefs.setTourSeen(false)` plus `setDone(false)`
    // sends someone back through the flow — so restoring the row is a few lines, not a rebuild.
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val mode = LocalThemeMode.current
    val setMode = LocalSetThemeMode.current
    var choosingTheme by remember { mutableStateOf(false) }
    var themeRowCentre by remember { mutableStateOf<Offset?>(null) }

    var cashOut by remember { mutableStateOf(NotificationPrefs.cashOutPrompt(context)) }
    var endOfDay by remember { mutableStateOf(NotificationPrefs.endOfDay(context)) }
    var gapAlert by remember { mutableStateOf(NotificationPrefs.gapAlert(context)) }
    var monthly by remember { mutableStateOf(NotificationPrefs.monthly(context)) }
    var termAlert by remember { mutableStateOf(NotificationPrefs.termEnd(context)) }

    // Read into state rather than straight from prefs, so editing one updates the row under
    // your thumb instead of on the next visit to this screen.
    var ownerName by remember { mutableStateOf(OnboardingPrefs.name(context)) }
    var isStudent by remember { mutableStateOf(OnboardingPrefs.isStudent(context)) }
    var termStart by remember { mutableStateOf(OnboardingPrefs.termStart(context)) }
    var termEnd by remember { mutableStateOf(OnboardingPrefs.termEnd(context)) }
    var editing by remember { mutableStateOf<Personal?>(null) }
    val termOver = remember(termEnd) {
        termEnd != null && LocalDate.now(ACCRA).isAfter(termEnd)
    }

    Box(modifier.fillMaxSize()) {
        Aura(animated = animated)
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 50.dp, bottom = 150.dp),
        ) {
            item {
                Text("Settings", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
                Spacer(Modifier.height(12.dp))
                StateLine(state, smsGranted)
                Spacer(Modifier.height(14.dp))
            }

            item {
                SettingsCard {
                    SettingsRow(
                        icon = themeIcon(mode),
                        title = "Appearance",
                        // ⚠ The ripple starts at this ROW, not at the dialog that opens from
                        // it. A dialog is a separate window, so it is not in the snapshot the
                        // effect is drawn from — and by the time the animation runs the dialog
                        // has already closed. The row is where the eye was before and after.
                        modifier = Modifier.onGloballyPositioned {
                            themeRowCentre = it.boundsInWindow().center
                        },
                        onClick = { choosingTheme = true },
                    ) { ValueAndChevron(mode.label) }
                }
            }

            item {
                SectionLabel("YOU")
                SettingsCard {
                    SettingsRow(
                        icon = R.drawable.ic_message,
                        title = "Your name",
                        subtitle = ownerName ?: "Not set — the greeting says the time of day",
                        onClick = { editing = Personal.NAME },
                    ) { Chevron() }
                    RowDivider()
                    SettingsRow(
                        icon = R.drawable.ic_calendar,
                        title = "Semester",
                        subtitle = termSubtitle(isStudent, termStart, termEnd),
                        tint = if (termOver) Warn else TextMuted,
                        // ⚠ Opens the LIST now, not the old pair of date pickers. Naming
                        // semesters means there can be several, and a dialog that edits "the"
                        // semester has nowhere to put the second one.
                        onClick = { onRoute(SettingsRoute.SEMESTERS) },
                    ) { Chevron() }
                }
                // ⚠ **"Show the tour again" was removed here on 2026-09-02, at Mutalib's
                // call: "i don't think it's relevant".** He is right, and the reason is worth
                // keeping: it was a first-run thing given a permanent home. Nobody watches an
                // onboarding tour twice, and every row in Settings costs attention from the
                // rows that do get used. The flow itself is untouched — `OnboardingPrefs`
                // still has `setTourSeen`, so a way back can be added if one is ever wanted.
                // ⚠ A term that has finished keeps reporting on itself. Every figure in the
                // semester view stays correct about the wrong stretch of time, which is the
                // kind of wrong nobody spots.
                if (termOver) {
                    Spacer(Modifier.height(10.dp))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Warn.copy(alpha = 0.10f))
                            .padding(12.dp),
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_warning),
                            contentDescription = null,
                            tint = Warn,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(9.dp))
                        Column {
                            Text(
                                "Your semester ended on " + termEnd?.format(SHORT_DATE) + ".",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary,
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                "The report is still using those dates. Set the new term when " +
                                    "you know it.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMuted,
                            )
                        }
                    }
                }
            }

            item {
                SectionLabel("READING YOUR MESSAGES")
                SettingsCard {
                    SettingsRow(
                        icon = R.drawable.ic_wallet,
                        title = "Ask what a cash-out was for",
                        subtitle = "The moment the message lands",
                    ) {
                        SettingsSwitch(cashOut) {
                            cashOut = it
                            NotificationPrefs.setCashOutPrompt(context, it)
                        }
                    }
                    RowDivider()
                    SettingsRow(
                        icon = R.drawable.ic_clock,
                        title = "Remind me at the end of the day",
                        subtitle = "Only when something is still unlabelled",
                    ) {
                        SettingsSwitch(endOfDay) {
                            endOfDay = it
                            NotificationPrefs.setEndOfDay(context, it)
                        }
                    }
                    RowDivider()
                    SettingsRow(
                        icon = R.drawable.ic_calendar,
                        title = "Monthly summary",
                        subtitle = "On the 1st, for the month just gone",
                    ) {
                        SettingsSwitch(monthly) {
                            monthly = it
                            NotificationPrefs.setMonthly(context, it)
                        }
                    }
                    RowDivider()
                    SettingsRow(
                        icon = R.drawable.ic_calendar,
                        // ⚠ "Semester ending" told you when it fires and nothing about what
                        // you get. Mutalib, 2026-09-04: *"semester endding says nothing
                        // shouldnt it be like semester sunnary"*. Every other row here is
                        // named for its content — "Monthly summary", "End-of-day reminder" —
                        // and this one was the odd one out.
                        title = "Semester summary",
                        subtitle = "A week before one ends, and the total when it does",
                    ) {
                        SettingsSwitch(termAlert) {
                            termAlert = it
                            NotificationPrefs.setTermEnd(context, it)
                        }
                    }
                    RowDivider()
                    SettingsRow(
                        icon = R.drawable.ic_warning,
                        title = "Tell me when a balance doesn't tally",
                        subtitle = "The moment money moves with no message to explain it",
                    ) {
                        SettingsSwitch(gapAlert) {
                            gapAlert = it
                            NotificationPrefs.setGapAlert(context, it)
                        }
                    }
                    RowDivider()
                    // ⚠ **Two things fixed here on 2026-09-02, both found by Mutalib using
                    // his own app.**
                    //
                    // It was a DEAD CONTROL. `onClick` was null whenever access was granted,
                    // so once everything was working the row did nothing when tapped — while
                    // sitting in a card looking exactly like every tappable row around it. He
                    // tapped it and reported "SMS access is not working", which is the only
                    // conclusion available. It is now always tappable and always goes
                    // somewhere: Android's own permission page.
                    //
                    // And it was FIRST, above the four switches. "Put it down the settings
                    // rather than the top" — right, because it is status, not a setting. The
                    // switches are what someone opens this screen to change; this is what
                    // they check once and then trust.
                    //
                    // ⚠ **No in-app switch, and there cannot be one.** Android permissions
                    // belong to the person, not the app: nothing here can grant or revoke
                    // READ_SMS, only link to the page that can. Revoking also kills the
                    // process — the ledger survives untouched, and only new messages stop.
                    SettingsRow(
                        icon = R.drawable.ic_message,
                        title = "SMS access",
                        subtitle = if (smsGranted) {
                            "Sika reads MoMo messages and nothing else"
                        } else {
                            "Without it there is nothing to track"
                        },
                        hint = "Tap to check in Android settings",
                        tint = if (smsGranted) TextMuted else Danger,
                        onClick = { openAppSettings(context) },
                    ) {
                        StatusPill(
                            if (smsGranted) "Allowed" else "Turn on",
                            if (smsGranted) Accent else Danger,
                        )
                    }
                }
            }

            item {
                SectionLabel("YOUR DATA")
                SettingsCard {
                    SettingsRow(
                        icon = R.drawable.ic_tag,
                        title = "Categories",
                        subtitle = categoriesSubtitle(state),
                        onClick = { onRoute(SettingsRoute.CATEGORIES) },
                    ) { Chevron() }
                    RowDivider()
                    // ⚠ **"Learned rules" until 2026-09-02, and it was named for the code.**
                    // Mutalib: *"the user won't know what it means"*. A "rule" is what the
                    // table is called; what it IS, to the person reading, is a shop that
                    // fills its own label in. The subtitle now says what the screen does
                    // rather than only counting what is in it.
                    SettingsRow(
                        icon = R.drawable.ic_stamp,
                        title = "Automatic labels",
                        subtitle = rulesSubtitle(state.rulesCount),
                        onClick = { onRoute(SettingsRoute.RULES) },
                    ) { Chevron() }
                    RowDivider()
                    // ⚠ Always shown, and always tappable. It shipped for an hour with a
                    // chevron and no destination, which is the thing a comment two files away
                    // says never to do. When the queue is empty the screen behind it is the
                    // good news, so hiding the row would hide the reassurance too.
                    SettingsRow(
                        icon = R.drawable.ic_inbox,
                        title = "Messages Sika couldn't read",
                        subtitle = reviewSubtitle(state.needsReview),
                        tint = if (state.needsReview > 0) Warn else TextMuted,
                        onClick = { onRoute(SettingsRoute.REVIEW) },
                    ) { Chevron() }
                    RowDivider()
                    SettingsRow(
                        icon = R.drawable.ic_download,
                        title = "Export everything",
                        subtitle = if (busy) {
                            "Working…"
                        } else {
                            count(state.transactions, "transaction") + " and every label, as CSV"
                        },
                        onClick = if (busy) null else onExport,
                    ) { Chevron() }
                    RowDivider()
                    SettingsRow(
                        icon = R.drawable.ic_upload,
                        title = "Restore from a file",
                        subtitle = "Puts your labels back after a reinstall",
                        onClick = if (busy) null else onImport,
                    ) { Chevron() }
                }
            }

            item { About() }
        }
    }

    when (editing) {
        Personal.NAME -> NameDialog(
            initial = ownerName,
            onSave = {
                OnboardingPrefs.setName(context, it)
                ownerName = it
                editing = null
            },
            onDismiss = { editing = null },
        )

        Personal.SEMESTER -> SemesterDialog(
            initialStudent = isStudent,
            initialStart = termStart,
            initialEnd = termEnd,
            onSave = { student, start, end ->
                OnboardingPrefs.setStudent(context, student)
                OnboardingPrefs.setTerm(context, start, end)
                isStudent = student
                termStart = start
                termEnd = end
                editing = null
            },
            onDismiss = { editing = null },
        )

        null -> Unit
    }

    if (choosingTheme) {
        AppearanceDialog(
            current = mode,
            onPick = { setMode(it, themeRowCentre); choosingTheme = false },
            onDismiss = { choosingTheme = false },
        )
    }
}

/**
 * The one claim in this app that can be checked rather than believed.
 *
 * ⚠ Worded as a fact and a way to verify it, not as a promise. "We respect your privacy" is
 * what every app says; "no internet permission, look it up in app info" is something the
 * operating system will confirm or contradict.
 */
@Composable
private fun About() {
    Column(Modifier.padding(top = 22.dp)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(TextMuted.copy(alpha = 0.18f)))
        Row(Modifier.padding(top = 15.dp)) {
            Icon(
                painterResource(R.drawable.ic_shield),
                contentDescription = null,
                tint = Accent,
                modifier = Modifier.size(17.dp),
            )
            Spacer(Modifier.width(11.dp))
            Column {
                Text(
                    "No internet permission.",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    "Nothing on this phone can leave it. You can check that yourself in " +
                        "Android's app info.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "Sika " + BuildConfig.VERSION_NAME,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                )
            }
        }
    }
}

/**
 * One line saying whether Sika is doing its job.
 *
 * Four states, and the worst one is the loudest: no SMS access makes every other setting on
 * this screen beside the point, so it is said in the danger colour with the words to match.
 */
@Composable
private fun StateLine(state: SettingsState, smsGranted: Boolean) {
    val bad = !smsGranted
    val dot = when {
        bad -> Danger
        state.transactions == 0 -> Warn
        state.allBalancing -> Accent
        else -> Warn
    }
    val head = when {
        bad -> "SMS access is off"
        state.transactions == 0 -> "No MoMo messages found yet"
        state.allBalancing -> "Reading your messages"
        else -> count(state.gaps, "transaction") + " " +
            agree(state.gaps, "does", "do") + " not add up"
    }
    val tail = when {
        bad -> " · Sika sees nothing"
        state.transactions == 0 -> " · nothing to show"
        state.allBalancing -> " · ${state.transactions} so far, all balancing"
        else -> " · out of ${state.transactions}"
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.width(8.dp))
        Text(
            head,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.W600),
            color = if (bad) Danger else TextPrimary,
        )
        Text(tail, style = MaterialTheme.typography.bodySmall, color = TextMuted)
    }
}

private fun categoriesSubtitle(state: SettingsState): String {
    val away = state.putAway.size
    val inUse = state.inUse.size
    return if (away == 0) "$inUse in use" else "$inUse in use, $away put away"
}

/**
 * ⚠ **Says what the screen DOES, not only how many rows are in it.** Mutalib's instruction
 * with the rename, 2026-09-02: describe it underneath. The old subtitle counted a thing whose
 * name did not explain itself, which left both lines relying on the reader already knowing.
 *
 * "Places" rather than "shops", because several of his are people.
 */
private fun rulesSubtitle(count: Int): String = when (count) {
    0 -> "Tick \"always label this way\" on a transaction and it lands here"
    1 -> "1 place Sika now labels on its own"
    else -> "$count places Sika now labels on its own"
}

/** Which of the two first-run answers is being edited. */
private enum class Personal { NAME, SEMESTER }

private val SHORT_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM")

private fun termSubtitle(student: Boolean, start: LocalDate?, end: LocalDate?): String = when {
    !student -> "Not a student — the semester view is hidden"
    start == null || end == null -> "Dates not set, so Sika is still guessing"
    else -> start.format(SHORT_DATE) + " – " + end.format(SHORT_DATE) + " " + end.year
}

/**
 * ⚠ **Rewritten because the title took its words.** The row is now called "Messages Sika
 * couldn't read", so a subtitle reading "3 messages Sika could not read" said the same thing
 * twice and told you nothing new. It now says what happens to them — which is the reassuring
 * part, and the one Sacred Rule 7 is about: unparsed is kept, never guessed at.
 */
private fun reviewSubtitle(count: Int): String = when (count) {
    0 -> "None — every message was readable"
    1 -> "1, kept in case Sika learns to read it"
    else -> "$count, kept in case Sika learns to read them"
}

private fun themeIcon(mode: ThemeMode): Int = when (mode) {
    ThemeMode.SYSTEM -> R.drawable.ic_theme_auto
    ThemeMode.LIGHT -> R.drawable.ic_theme_light
    ThemeMode.DARK -> R.drawable.ic_theme_dark
}

/**
 * ⚠ **Opens Sika's own app info, not the top of Android's settings.** Dropping someone at the
 * root of Settings and telling them to find "Apps → Sika → Permissions" is an instruction, not
 * a fix. `ACTION_APPLICATION_DETAILS_SETTINGS` with the package attached lands on the right
 * screen, which is what makes it a button rather than a suggestion.
 */
private fun openAppSettings(context: Context) {
    context.startActivity(
        Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData("package:${context.packageName}".toUri())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}
