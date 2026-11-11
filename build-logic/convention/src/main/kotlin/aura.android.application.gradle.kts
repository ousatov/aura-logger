import java.util.Properties

// Android application: SDK levels, R8 + resource shrinking for release, release signing.
//
// Release signing is read from `keystore.properties` in the root project
// (storeFile, storePassword, keyAlias, keyPassword) or, if absent, from the environment
// (AURA_KEYSTORE_FILE, AURA_KEYSTORE_PASSWORD, AURA_KEY_ALIAS, AURA_KEY_PASSWORD).
// Without either, the release build is produced unsigned.

plugins {
    id("com.android.application")
    id("aura.unit-test")
}

val keystoreFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystoreFile.isFile) keystoreFile.inputStream().use(::load)
}

fun signingValue(propertyKey: String, envKey: String): String? =
    keystoreProperties.getProperty(propertyKey) ?: providers.environmentVariable(envKey).orNull

val releaseStoreFile = signingValue("storeFile", "AURA_KEYSTORE_FILE")
val releaseStorePassword = signingValue("storePassword", "AURA_KEYSTORE_PASSWORD")
val releaseKeyAlias = signingValue("keyAlias", "AURA_KEY_ALIAS")
val releaseKeyPassword = signingValue("keyPassword", "AURA_KEY_PASSWORD")
val hasReleaseSigning = listOf(releaseStoreFile, releaseStorePassword, releaseKeyAlias, releaseKeyPassword)
    .all { !it.isNullOrBlank() }

android {
    compileSdk = catalog.versionOf("compileSdk").toInt()

    defaultConfig {
        minSdk = catalog.versionOf("minSdk").toInt()
        targetSdk = catalog.versionOf("targetSdk").toInt()
    }

    compileOptions {
        val javaVersion = JavaVersion.toVersion(catalog.versionOf("jvmTarget"))
        sourceCompatibility = javaVersion
        targetCompatibility = javaVersion
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
}
