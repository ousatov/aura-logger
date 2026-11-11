import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

// Pure Kotlin/JVM library with explicit API mode and public ABI checks
// (`updateKotlinAbi` writes the reference dump, `check` runs `checkKotlinAbi`).

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("aura.unit-test")
}

val jvmTargetVersion = catalog.versionOf("jvmTarget")

kotlin {
    explicitApi()
    @OptIn(ExperimentalAbiValidation::class)
    abiValidation()
    compilerOptions {
        jvmTarget.set(JvmTarget.fromTarget(jvmTargetVersion))
        freeCompilerArgs.add("-Xjdk-release=$jvmTargetVersion")
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(jvmTargetVersion.toInt())
}
