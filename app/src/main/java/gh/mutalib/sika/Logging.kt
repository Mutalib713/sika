package gh.mutalib.sika

import android.util.Log

/**
 * Logging for things that should never leave the debug build.
 *
 * ⚠ **`adb logcat` is readable by anyone who can plug the phone in**, and the Sika tag carries
 * whole SMS bodies, counterparty names and amounts. Every one of those is exactly the data
 * this app refuses to send over a network — writing it to a system log instead would be
 * giving it away through a side door, and PROFILE.md's own checklist says logs must not record
 * names or message bodies.
 *
 * The debug build keeps all of it, because that is where it earns its keep: the parser was
 * built by reading real queued messages out of logcat, and shipping without that would make
 * the next parser fix much harder.
 *
 * ⚠ **`BuildConfig.DEBUG` is a compile-time constant, so R8 removes these calls entirely from
 * a release build** — the strings are not merely skipped at runtime, they are not in the APK.
 * That is the property that matters: a string that is not there cannot be dumped.
 *
 * Found while gathering evidence for PLAN task 17 on 2026-09-01, not by using the app.
 */
fun logPrivate(message: () -> String) {
    if (BuildConfig.DEBUG) Log.i(TAG, message())
}

/** The same, at warning level — for things a debug build should notice, like a queued message. */
fun warnPrivate(message: () -> String) {
    if (BuildConfig.DEBUG) Log.w(TAG, message())
}
