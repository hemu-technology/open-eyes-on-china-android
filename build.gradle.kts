// Top-level Gradle build file
plugins {
    id("com.android.application") version "8.5.2" apply false
    // Upgrade Kotlin plugin to 2.1.0 to match dependencies (play-services-ads compiled with Kotlin 2.1.0)
    id("org.jetbrains.kotlin.android") version "2.1.0" apply false
}

// Repositories are defined in settings.gradle.kts via dependencyResolutionManagement
