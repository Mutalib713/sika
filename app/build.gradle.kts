import java.util.Properties

plugins {
    id("com.android.application")
    // Kotlin applied explicitly, the classic way. AGP 8 has no built-in Kotlin, which is
    // exactly why this project is on AGP 8 — see the root build.gradle.kts.
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "gh.mutalib.sika"
    compileSdk = 36

    defaultConfig {
        applicationId = "gh.mutalib.sika"

        // API 31 is the floor, and it is deliberate. The Pixel 6 Pro — the only device
        // this app will ever run on — shipped on API 31. Supporting anything older buys
        // nothing and costs compatibility branches in the SMS and notification code,
        // both of which changed shape around 31. PROFILE.md § 7.
        minSdk = 31
        targetSdk = 36
        // ⚠ Keep this in step with the git tag. Settings prints `versionName` at the bottom of
        // the screen, so a v1.0.0 tag on an APK that says 0.1 is a lie the person can see.
        versionCode = 4
        versionName = "1.2.0"

        // Room's DAOs talk to real SQLite, so they are tested on the real device rather
        // than against a mock. `./gradlew connectedDebugAndroidTest` runs these.
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Room writes the schema out as JSON on every build. Set up on day one, before there
    // is anything to migrate, because PROFILE.md § 7 requires a migration from version 1:
    // without the version-1 schema on disk there is nothing for a future migration test to
    // migrate *from*, and by then the history is the whole point of the app.
    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
    }
    sourceSets.getByName("androidTest").assets.srcDir("$projectDir/schemas")

    /**
     * Release signing, read from `keystore.properties` if it is there.
     *
     * ⚠ **This replaced `signingConfig = signingConfigs.getByName("debug")` on 2026-09-04, and
     * the reason is the one thing about app signing that bites late.** Android refuses an
     * update signed by a different key than the installed copy. Handing testers debug-signed
     * builds and switching to a real key later would force every one of them to uninstall —
     * and uninstalling Sika destroys the ledger, which is the one thing in this app that
     * cannot be rebuilt. The key has to be right before the first stranger installs it, not
     * before the first release.
     *
     * ⚠ **The keystore lives OUTSIDE the repo** (`~/.android/sika-release.jks`) and both it
     * and `keystore.properties` are gitignored. Lose either and no future build can ever
     * update an installed Sika.
     *
     * ⚠ **Absent config falls back to the debug key rather than failing the build.** A clone
     * on another machine, or CI with no secrets, must still be able to run `assembleRelease`
     * to prove the code compiles and shrinks. What it produces is not distributable, and
     * `releaseSigned` below is what tells you which you got.
     */
    val keystoreProperties = Properties().apply {
        val f = rootProject.file("keystore.properties")
        if (f.exists()) f.inputStream().use { load(it) }
    }
    val releaseSigned = keystoreProperties.getProperty("storeFile")?.let { file(it).exists() } == true

    signingConfigs {
        if (releaseSigned) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = if (releaseSigned) {
                signingConfigs.getByName("release")
            } else {
                logger.warn(
                    "Sika: no keystore.properties, signing release with the DEBUG key. " +
                        "This build must not be given to anyone.",
                )
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            // Must match compileOptions above. AGP 9's built-in Kotlin kept these in step
            // automatically; the classic Kotlin plugin does not, and defaults to the JDK
            // running the build — the JBR is 21, so the two disagree and the build stops
            // with "Inconsistent JVM-target compatibility". Another cost of the AGP 8
            // route, and cheaper than the alternative.
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
        // Settings shows the version, and the manifest is the only place that knows it.
        // Off by default since AGP 8; without this `BuildConfig` is not generated at all.
        buildConfig = true
    }

    lint {
        // A lint warning nobody fixes is noise, and `check` has to mean something.
        warningsAsErrors = true
        abortOnError = true

        disable += setOf(
            // "A newer version of X is available." Fires whenever any dependency ships a
            // release. Upgrading is a deliberate task, not a build failure on someone
            // else's schedule.
            "GradleDependency",
            // "compileSdk 37 is available." Only android-36 and android-36.1 are installed
            // on this machine. Revisit before v1.0.0, not on a random Tuesday.
            "OldTargetApi",
            // ⚠ "A newer version of Gradle/AGP is available."
            //
            // This one is disabled because the upgrade it asks for is *impossible*, not
            // merely inconvenient. Gradle is pinned at 9.4.1 because AGP 8 cannot run on
            // 9.6 or later — it uses a Gradle internal API removed in 9.6.0, and Gradle
            // says so by name. AGP is pinned at 8.x because KSP, which Room needs, does
            // not work with AGP 9 at all. See the root build.gradle.kts.
            //
            // So the check is correct in the abstract and wrong here, and leaving it on
            // would make `check` fail forever on a fix nobody can apply. Delete this
            // entry the day KSP supports AGP 9.
            "AndroidGradlePluginVersion",
            // ⚠ "Consider whether this feature really is required." Considered — it is.
            //
            // These two checks contradict each other for an SMS app. Omit the
            // <uses-feature android.hardware.telephony> tag and lint raises
            // PermissionImpliesUnsupportedChromeOsHardware; declare it required="true" and
            // lint raises this one instead. The only state satisfying both is
            // required="false", which would be a false statement: Sika's entire input is
            // SMS, so on a device with no modem it has nothing to do at all.
            //
            // Both checks exist to protect app-store reach on tablets and ChromeOS. Sika is
            // sideloaded to one Pixel and never published, so that concern does not apply,
            // and the truthful manifest is worth more than the silent warning.
            "UnnecessaryRequiredFeature",
            // No app icon yet, on purpose. The icon comes out of the design pass at PLAN
            // task 10, where the palette is Mutalib's to choose — picking a placeholder
            // here is how a default quietly becomes the brand.
            "MissingApplicationIcon",
        )
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")

    // ⚠ **Not "adds a splash screen" — takes control of the one Android already shows.** On
    // API 31+ every app gets a system splash whether it asks or not, and Sika's was the
    // default: the launcher icon on the window background. This library is how an app styles
    // it and, more importantly, how it holds it on screen and hands it over without a seam.
    // res/values/themes.xml has the style; MainActivity has the handover.
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.compose.ui:ui:1.9.0")
    implementation("androidx.compose.foundation:foundation:1.9.0")
    implementation("androidx.compose.material3:material3:1.4.0")
    implementation("androidx.compose.ui:ui-tooling-preview:1.9.0")
    // State that survives rotation and reads the ledger as a Flow, so a transaction landing
    // while Home is open appears without a refresh.
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")

    // ⚠ **Pinned deliberately, and the build fails without it.**
    //
    // AGP forces the androidTest compile classpath to match the app's runtime classpath
    // ("consistent resolution"). Compose 1.9.0 pulls coroutines 1.8.1 into the app, while
    // `kotlinx-coroutines-test:1.10.2` demands 1.10.2 for the tests — an unsatisfiable
    // conflict. Naming the version here makes both sides 1.10.2.
    //
    // Worth knowing how this presents, because it cost an hour on 2026-08-31: Gradle
    // throws a bare `NullPointerException: Cannot invoke "java.util.List.get(int)" because
    // "path" is null` from `generateDebugAndroidTestLintModel`. That is Gradle crashing
    // while *formatting* the resolution error, not a lint bug and not a resource problem.
    // `./gradlew :app:dependencyInsight --configuration debugAndroidTestCompileClasspath
    // --dependency kotlinx-coroutines-core` prints the real message.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    // The ledger. Entities and DAOs arrive at PLAN task 4 — the plugin is wired now so a
    // version mismatch surfaces today rather than in the middle of writing the schema.
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")

    // Debug only — the preview renderer is a build-time tool, not something users ship.
    debugImplementation("androidx.compose.ui:ui-tooling:1.9.0")

    // The parser is pure Kotlin with no Android dependency, which is the whole point of
    // structuring it that way: junit alone tests every message shape, on the JVM, in
    // milliseconds, with no emulator and no device. PROFILE.md § 12.
    testImplementation("junit:junit:4.13.2")

    // Instrumented tests — these run on the Pixel against real SQLite. A dedupe guarantee
    // verified against anything less than the real engine is not a guarantee.
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.room:room-testing:2.8.4")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}

// ⚠ **`check` must COMPILE the instrumented tests, even though it cannot run them.**
//
// It did not, and the cost was measured on 2026-09-06: `MigrationTest` had called
// `Cursor.getText()` — a method that does not exist; the name is `getString` — since commit
// 2c44bc8. `./gradlew check` passed every time in between, because it compiles the JVM tests
// and lints the androidTest sources without ever asking Kotlin to build them. So the entire
// instrumented suite, including the dedupe guarantees that Sacred Rule 4 rests on, could not
// be run at all and nothing said so.
//
// Running those tests needs a device and `check` must work with nothing plugged in — but
// *compiling* them needs nothing, takes seconds, and turns "silently un-runnable" into a
// build failure. Which is the same argument as reconciliation itself: a verification you
// have to remember to trigger is one that stops happening.
tasks.named("check") {
    dependsOn("compileDebugAndroidTestKotlin")
}
