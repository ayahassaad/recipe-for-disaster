// :domain is deliberately a plain Kotlin/JVM module, not an Android library.
// It must never depend on the Android SDK or AndroidX — that's what lets the
// entire simulation engine be unit-tested on the plain JVM, with no emulator
// and no Android runtime, per the project's architecture decision to keep
// business logic independent of the UI layer.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    // kotlinx.serialization is a pure Kotlin multiplatform library, not an
    // Android/AndroidX one, so depending on it here doesn't compromise
    // :domain's "no Android dependency" rule. GameState and everything it
    // contains is @Serializable so :data can persist a snapshot as JSON
    // without :domain knowing anything about Room.
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnit()
    // Lets `./gradlew :domain:test -DbalanceReport=true` print BalanceSimulationTest's survival stats.
    listOf("balanceReport", "balanceMaxDays").forEach { key -> System.getProperty(key)?.let { systemProperty(key, it) } }
    testLogging { showStandardStreams = System.getProperty("balanceReport") != null }
}
