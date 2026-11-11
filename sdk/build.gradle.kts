plugins {
    alias(libs.plugins.aura.android.library)
    alias(libs.plugins.aura.room)
}

android {
    namespace = "com.usatov.aura.logger"
}

dependencies {
    api(project(":sdk-core"))

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.datastore.preferences)
}

kotlin {
    compilerOptions {
        // Modules of the SDK itself may use the cross-module internal API.
        optIn.add("com.usatov.aura.logger.core.InternalAuraLoggerApi")
    }
}
