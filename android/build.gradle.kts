// Root build file. Pins the Android Gradle Plugin and Kotlin versions used by
// the `:app` module. Versions are chosen to be a well-known-stable combo
// (AGP 8.5.x / Kotlin 1.9.24 / Gradle 8.7) so a dev with Android Studio can
// sync without hunting for compatible versions.
plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.24" apply false
}

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}
