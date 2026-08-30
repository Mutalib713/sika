plugins {
    // ⚠ **AGP 8, not AGP 9 — and this differs from Wird and Thrum on purpose.**
    //
    // Sika needs Room, Room needs KSP, and on 2026-08-30 KSP cannot be made to work with
    // AGP 9 at all. Both routes are closed, each by the other:
    //
    //   • AGP 9's built-in Kotlin → KSP refuses outright:
    //       "KSP is not compatible with Android Gradle Plugin's built-in Kotlin.
    //        Please disable by adding android.builtInKotlin=false ... and apply
    //        kotlin("android") plugin"
    //
    //   • So disable it and apply Kotlin the classic way → AGP 9's new DSL refuses:
    //       "The 'org.jetbrains.kotlin.android' plugin is not compatible with AGP's 9.0
    //        new DSL (android.newDsl=true is enabled by default)"
    //
    // Getting out needs `android.builtInKotlin=false` AND `android.newDsl=false` together
    // — two flags AGP already marks deprecated and removes in version 10. A foundation
    // built on two escape hatches is a foundation that breaks on someone else's schedule,
    // and PROFILE.md § 3 asks this app to still be correct in three months.
    //
    // AGP 8.13.2 is the current 8.x, supports compileSdk 36, and is what the overwhelming
    // majority of Room apps run today. Boring on purpose.
    //
    // Revisit when KSP ships AGP 9 support — not before, and not by adding the flags.
    id("com.android.application") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "2.3.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.21" apply false

    // KSP generates Room's DAO implementations at compile time. From 2.3 it versions
    // independently of Kotlin rather than gluing both numbers together, so 2.3.11 is a
    // KSP version, not a Kotlin one.
    id("com.google.devtools.ksp") version "2.3.11" apply false
}
