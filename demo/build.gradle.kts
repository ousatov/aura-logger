plugins {
    alias(libs.plugins.aura.android.application)
    alias(libs.plugins.aura.compose)
    alias(libs.plugins.aura.hilt)
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
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.paging.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
}
