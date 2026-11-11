// Room 3 with KSP code generation and exported schemas under `schemas/`.

plugins {
    id("com.google.devtools.ksp")
    id("androidx.room3")
}

room3 {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    "implementation"(catalog.library("androidx-room3-runtime"))
    "implementation"(catalog.library("androidx-sqlite-framework"))
    "ksp"(catalog.library("androidx-room3-compiler"))
}
