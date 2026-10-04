plugins {
    alias(libs.plugins.android.library)
    // No `kotlin.android` plugin — AGP 9.0+ has Kotlin support built in;
    // applying it explicitly is now a hard error, not just redundant.
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.recipefordisaster.data"
    compileSdk = 37

    defaultConfig {
        minSdk = 28

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    // No separate `kotlinOptions { jvmTarget = ... }` block — under AGP's
    // built-in Kotlin support, the Kotlin JVM target defaults to
    // android.compileOptions.targetCompatibility above, so it's redundant
    // (and, per Android's built-in-Kotlin migration guide, the old
    // `kotlinOptions` block no longer exists to set it on).
}

ksp {
    // Room's exported schema history (see GameDatabase's exportSchema = true)
    // lives here so migrations added in Phase 4 can be tested against real
    // prior schemas instead of guessed at.
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(project(":domain"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.kotlinx.coroutines.core)
    // Runtime only — no @Serializable classes are defined in :data itself
    // (GameState and friends live in :domain, which applies the compiler
    // plugin), so this module just needs the ability to call Json.
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.room.testing)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.room.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
