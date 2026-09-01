package gh.mutalib.sika.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import java.time.LocalDate

/**
 * Where first run has got to.
 *
 * ⚠ **A saved enum, not a boolean pile.** The flow survives a rotation and a process death
 * part-way through: someone who granted SMS access and then took a call must not come back to
 * the beginning and be asked for the permission they already gave.
 */
enum class OnboardingStep {
    TOUR_INTRO, TOUR_READS, TOUR_CHECKS, TOUR_SEMESTER,
    PERMISSION, NAME, STUDENT, NOTIFICATIONS,
}

/**
 * First run, end to end.
 *
 * The order, and why it is this order:
 *
 *  1. **The tour** — four screens, skippable from the first. Three of them teach things that
 *     cannot be discovered by using the app; the first says what the app *is*, which Mutalib
 *     pointed out was missing when the flow opened on "you never type anything".
 *  2. **SMS access** — after the tour, so there is a reason to say yes rather than a bare
 *     system dialog arriving cold.
 *  3. **Your name** — replacing a compile-time constant.
 *  4. **Student, and the term dates** — one screen, dates revealed under a Yes.
 *  5. **Notifications** — asked in words before Android asks in a dialog.
 *
 * ⚠ **Skipping the tour skips only the tour.** The questions still get asked, because they
 * change what the app does; the tour only changes what you know about it.
 */
@Composable
fun OnboardingFlow(
    smsGranted: Boolean,
    onRequestSms: () -> Unit,
    onRequestNotifications: () -> Unit,
    onRestore: () -> Unit,
    onFinished: () -> Unit,
) {
    val context = LocalContext.current
    var step by rememberSaveable {
        // ⚠ The tour is gated on whether the TOUR has been seen, never on the permission.
        // Those are different facts, and conflating them skipped the tour entirely for anyone
        // upgrading an install that already had SMS access — which was everyone who mattered.
        mutableStateOf(
            when {
                !OnboardingPrefs.tourSeen(context) -> OnboardingStep.TOUR_INTRO
                // Tour already seen and permission already held: nothing left to ask before
                // the questions.
                smsGranted -> OnboardingStep.NAME
                else -> OnboardingStep.PERMISSION
            },
        )
    }

    /** Past the tour: skip the permission screen only if it is genuinely already granted. */
    fun afterTour() {
        OnboardingPrefs.setTourSeen(context, true)
        step = if (smsGranted) OnboardingStep.NAME else OnboardingStep.PERMISSION
    }

    var student by rememberSaveable { mutableStateOf(OnboardingPrefs.isStudent(context)) }
    val initialStart = remember { OnboardingPrefs.termStart(context) }
    val initialEnd = remember { OnboardingPrefs.termEnd(context) }

    // Back steps through the flow rather than leaving the app, except on the first screen
    // where leaving is the only honest meaning of back.
    BackHandler(enabled = step != OnboardingStep.TOUR_INTRO) {
        step = when (step) {
            OnboardingStep.TOUR_READS -> OnboardingStep.TOUR_INTRO
            OnboardingStep.TOUR_CHECKS -> OnboardingStep.TOUR_READS
            OnboardingStep.TOUR_SEMESTER -> OnboardingStep.TOUR_CHECKS
            // ⚠ Never back into the tour from PERMISSION once SMS is granted: the next
            // forward step would ask for it again, which reads as the grant not having taken.
            OnboardingStep.PERMISSION -> OnboardingStep.TOUR_SEMESTER
            OnboardingStep.NAME -> if (smsGranted) OnboardingStep.NAME else OnboardingStep.PERMISSION
            OnboardingStep.STUDENT -> OnboardingStep.NAME
            OnboardingStep.NOTIFICATIONS -> OnboardingStep.STUDENT
            OnboardingStep.TOUR_INTRO -> OnboardingStep.TOUR_INTRO
        }
    }

    val skipTour = { afterTour() }

    when (step) {
        OnboardingStep.TOUR_INTRO ->
            TourIntro(onNext = { step = OnboardingStep.TOUR_READS }, onSkip = skipTour)

        OnboardingStep.TOUR_READS ->
            TourReads(onNext = { step = OnboardingStep.TOUR_CHECKS }, onSkip = skipTour)

        OnboardingStep.TOUR_CHECKS ->
            TourChecks(onNext = { step = OnboardingStep.TOUR_SEMESTER }, onSkip = skipTour)

        OnboardingStep.TOUR_SEMESTER ->
            TourSemester(onNext = { afterTour() }, onSkip = skipTour)

        OnboardingStep.PERMISSION -> AskPermission(
            onAllow = onRequestSms,
            onRestore = onRestore,
        )

        OnboardingStep.NAME -> AskName(
            initial = OnboardingPrefs.name(context),
        ) { name ->
            OnboardingPrefs.setName(context, name)
            step = OnboardingStep.STUDENT
        }

        OnboardingStep.STUDENT -> AskStudent(
            initialStudent = student,
            initialStart = initialStart,
            initialEnd = initialEnd,
        ) { isStudent, start, end ->
            student = isStudent
            OnboardingPrefs.setStudent(context, isStudent)
            OnboardingPrefs.setTerm(context, start, end)
            step = OnboardingStep.NOTIFICATIONS
        }

        OnboardingStep.NOTIFICATIONS -> AskNotifications(
            onAllow = {
                onRequestNotifications()
                OnboardingPrefs.setDone(context, true)
                onFinished()
            },
            onSkip = {
                // A refusal is a finished answer, not an unfinished flow.
                OnboardingPrefs.setDone(context, true)
                onFinished()
            },
        )
    }

    // Granting SMS returns to this composable rather than to a callback, so the flow moves on
    // by observing the result instead of assuming the dialog was answered yes.
    if (smsGranted && step == OnboardingStep.PERMISSION) {
        step = OnboardingStep.NAME
    }
}

/** Today in Accra — used by the term-expiry check, and stated rather than defaulted. */
internal fun todayInAccra(): LocalDate = LocalDate.now(gh.mutalib.sika.ui.home.ACCRA)
