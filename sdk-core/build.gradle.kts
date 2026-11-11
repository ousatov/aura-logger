plugins {
    alias(libs.plugins.aura.jvm.library)
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
}

kotlin {
    compilerOptions {
        // Modules of the SDK itself may use the cross-module internal API.
        optIn.add("com.usatov.aura.logger.core.InternalAuraLoggerApi")
    }
}
