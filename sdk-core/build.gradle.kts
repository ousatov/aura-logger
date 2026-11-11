plugins {
    alias(libs.plugins.aura.jvm.library)
    `java-test-fixtures`
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    // Shared fakes and the EventStore contract, also used by the sdk module tests.
    testFixturesImplementation(libs.junit4)
    testFixturesImplementation(libs.turbine)
    testFixturesImplementation(libs.kotlinx.coroutines.core)
    testRuntimeOnly(libs.junit.vintage.engine)
}

kotlin {
    compilerOptions {
        // Modules of the SDK itself may use the cross-module internal API.
        optIn.add("com.usatov.aura.logger.core.InternalAuraLoggerApi")
    }
}
