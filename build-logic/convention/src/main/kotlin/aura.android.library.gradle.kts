// Android library with explicit API mode and consumer R8 rules.
//
// Public ABI dumps are not enabled here: with AGP 9 built-in Kotlin, the ABI validation of the
// Kotlin Gradle plugin (2.4.21) fails to configure its tasks for Android library modules, and
// the standalone binary-compatibility-validator plugin does not register tasks at all.

plugins {
    id("com.android.library")
    id("aura.unit-test")
}

android {
    compileSdk = catalog.versionOf("compileSdk").toInt()

    defaultConfig {
        minSdk = catalog.versionOf("minSdk").toInt()
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        val javaVersion = JavaVersion.toVersion(catalog.versionOf("jvmTarget"))
        sourceCompatibility = javaVersion
        targetCompatibility = javaVersion
    }
}

kotlin {
    explicitApi()
}
