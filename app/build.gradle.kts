plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")  // Enables Compose compiler (Kotlin 2.x)
    id("com.google.devtools.ksp")              // Room annotation processor
}

android {
    namespace = "com.pulsecoach"
    compileSdk = 35  // Build against Android 15 APIs

    defaultConfig {
        applicationId = "com.pulsecoach"
        minSdk = 26         // Android 8.0 — covers virtually all current devices
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true  // Enable Jetpack Compose
    }
}

dependencies {
    // ── Compose ──────────────────────────────────────────────────────────────
    // The BOM (Bill of Materials) pins all Compose library versions together
    // so they don't conflict. We reference the BOM once, then use each library
    // without specifying a version number.
    val composeBom = platform("androidx.compose:compose-bom:2025.02.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")  // @Preview support
    implementation("androidx.compose.material3:material3")    // Material Design 3 components
    debugImplementation("androidx.compose.ui:ui-tooling")     // Layout inspector (debug only)

    // ── Core Android ─────────────────────────────────────────────────────────
    // material provides the XML Theme.Material3.* styles referenced in themes.xml
    // (Compose material3 is separate — it provides Kotlin composables, not XML themes)
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.activity:activity-compose:1.10.0")  // setContent {} entry point

    // ── ViewModel + StateFlow ─────────────────────────────────────────────────
    // ViewModel survives screen rotations; StateFlow is our reactive state container
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    // ── Navigation ───────────────────────────────────────────────────────────
    implementation("androidx.navigation:navigation-compose:2.8.5")

    // ── Room (local database) ─────────────────────────────────────────────────
    // Room is an ORM (Object-Relational Mapper) that wraps SQLite.
    // KSP generates the implementation code at compile time from our @Dao annotations.
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")  // Adds coroutine/Flow support
    ksp("androidx.room:room-compiler:$roomVersion")        // Code generator (runs at build time)

    // ── Coroutines ────────────────────────────────────────────────────────────
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // ── Polar BLE SDK ─────────────────────────────────────────────────────────
    // Official Polar SDK — handles BLE connection and Polar-proprietary data protocol.
    // Hosted on JitPack (not Maven Central), which is why JitPack is in settings.gradle.kts.
    implementation("com.github.polarofficial:polar-ble-sdk:5.4.0")

    // ── RxJava 3 ──────────────────────────────────────────────────────────────
    // The Polar SDK uses RxJava internally. We bridge it to Kotlin coroutines
    // using callbackFlow (see PolarBleManager.kt) — but we still need the RxJava
    // runtime on the classpath.
    implementation("io.reactivex.rxjava3:rxjava:3.1.9")
    implementation("io.reactivex.rxjava3:rxandroid:3.0.2")

    // ── Vico Charts ───────────────────────────────────────────────────────────
    // Compose-native charting library for the live HR graph (Phase 1).
    implementation("com.patrykandpatrick.vico:compose-m3:2.0.0-beta.3")

    // ── Unit Tests ────────────────────────────────────────────────────────────
    // JUnit 4 for JVM unit tests (src/test/). These run on the host machine,
    // not on a device — so they're fast and suitable for pure-function testing.
    testImplementation("junit:junit:4.13.2")
}
