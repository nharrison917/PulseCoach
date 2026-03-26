// Root-level build file. Declares plugin versions used across all modules.
// "apply false" means: make these available, but don't activate them here —
// the :app module will activate the ones it needs.
plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.1.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.0" apply false  // Kotlin 2.x ships the Compose compiler built-in
    id("com.google.devtools.ksp") version "2.1.0-1.0.29" apply false       // KSP = Kotlin Symbol Processing, used by Room for code generation
}
