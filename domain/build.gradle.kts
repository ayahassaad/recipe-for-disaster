// :domain is deliberately a plain Kotlin/JVM module, not an Android library.
// It must never depend on the Android SDK or AndroidX — that's what lets the
// entire simulation engine be unit-tested on the plain JVM, with no emulator
// and no Android runtime, per the project's architecture decision to keep
// business logic independent of the UI layer.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnit()
}
