pluginManagement {
    repositories {
        google()           // Android Gradle Plugin lives here
        mavenCentral()     // Kotlin and most libraries
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") } // Polar BLE SDK is hosted on JitPack
    }
}

rootProject.name = "PulseCoach"
include(":app")  // Our single app module
