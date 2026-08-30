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
        versionCode = 1
        versionName = "0.1"

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

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Debug key for now, so a release build installs over a debug one. A real
            // keystore is not needed: this app is sideloaded to one phone and never
            // published. PROFILE.md § 7, distribution row.
            signingConfig = signingConfigs.getByName("debug")
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
    implementation("androidx.compose.ui:ui:1.9.0")
    implementation("androidx.compose.foundation:foundation:1.9.0")
    implementation("androidx.compose.material3:material3:1.4.0")
    implementation("androidx.compose.ui:ui-tooling-preview:1.9.0")

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
