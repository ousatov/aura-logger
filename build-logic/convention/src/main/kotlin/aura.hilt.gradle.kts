// Hilt with KSP code generation.

plugins {
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

dependencies {
    "implementation"(catalog.library("hilt-android"))
    "ksp"(catalog.library("hilt-compiler"))
}
