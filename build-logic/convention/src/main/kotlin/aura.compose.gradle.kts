import com.android.build.api.dsl.ApplicationExtension

// Jetpack Compose + Material3 for an Android application module.

plugins {
    id("org.jetbrains.kotlin.plugin.compose")
}

extensions.configure<ApplicationExtension> {
    buildFeatures {
        compose = true
    }
    // Required by the Compose Preview tasks of AGP; also lets Robolectric see app resources.
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    val composeBom = platform(catalog.library("androidx-compose-bom"))
    "implementation"(composeBom)
    "implementation"(catalog.library("androidx-compose-ui"))
    "implementation"(catalog.library("androidx-compose-ui-tooling-preview"))
    "implementation"(catalog.library("androidx-compose-material3"))
    "debugImplementation"(catalog.library("androidx-compose-ui-tooling"))
}
