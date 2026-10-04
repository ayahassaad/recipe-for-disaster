// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    // No separate `kotlin-android` plugin as of AGP 9.0 — Kotlin support for
    // Android modules is now built into the Android Gradle Plugin itself.
    // `:app` and `:data` (the only two Android modules) no longer apply it.
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
}

// This project lives on an iCloud-synced Desktop. iCloud was uploading and
// offloading build outputs as fast as Gradle wrote them, so builds hung
// reading back their own freshly compiled classes. iCloud skips anything
// whose name ends in ".nosync", so every module builds into `build.nosync/`
// instead of `build/`. Delete this block (and the folders) if the project
// ever moves somewhere that isn't synced.
allprojects {
    layout.buildDirectory.set(layout.projectDirectory.dir("build.nosync"))
}
