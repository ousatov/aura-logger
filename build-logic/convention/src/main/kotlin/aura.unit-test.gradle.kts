// JUnit Platform (Jupiter) for local unit tests, plus coroutines-test and Turbine.
// Apply after the module type plugin so that the test configurations exist.

dependencies {
    "testImplementation"(platform(catalog.library("junit-bom")))
    "testImplementation"(catalog.library("junit-jupiter"))
    "testImplementation"(catalog.library("kotlinx-coroutines-test"))
    "testImplementation"(catalog.library("turbine"))
    "testRuntimeOnly"(catalog.library("junit-platform-launcher"))
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        events("failed", "skipped")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
