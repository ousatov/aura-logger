plugins {
    alias(libs.plugins.aura.jvm.library)
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
}
