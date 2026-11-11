plugins {
    alias(libs.plugins.aura.android.application)
    alias(libs.plugins.aura.compose)
}

android {
    namespace = "com.usatov.aura.logger.demo"

    defaultConfig {
        applicationId = "com.usatov.aura.logger.demo"
        versionCode = 1
        versionName = "1.0.0"
    }
}

dependencies {
    implementation(project(":sdk"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
}
