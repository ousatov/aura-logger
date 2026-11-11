plugins {
    alias(libs.plugins.aura.android.library)
    alias(libs.plugins.aura.room)
    alias(libs.plugins.aura.kover)
}

android {
    namespace = "com.usatov.aura.logger"
}

dependencies {
    api(project(":sdk-core"))

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.datastore.preferences)

    // Robolectric runs on JUnit 4, executed by the JUnit Platform vintage engine.
    testImplementation(libs.junit4)
    testRuntimeOnly(libs.junit.vintage.engine)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.work.testing)
    testImplementation(testFixtures(project(":sdk-core")))
}

kotlin {
    compilerOptions {
        // Modules of the SDK itself may use the cross-module internal API.
        optIn.add("com.usatov.aura.logger.core.InternalAuraLoggerApi")
    }
}

tasks.withType<Test>().configureEach {
    // Robolectric needs access to JDK internals on JDK 17+.
    jvmArgs(
        "--add-opens=java.base/java.io=ALL-UNNAMED",
        "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED",
    )
}
