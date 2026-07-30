import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    `java-library`
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.maven.publish)
}

repositories {
    mavenCentral()
}

dependencies {
    compileOnly(project(":java"))
    implementation(libs.bundles.common)
    implementation(libs.coroutine.core)

    testImplementation(project(":java"))
    testImplementation(kotlin("test"))
    testImplementation(libs.bundles.kotlin.tests)
    // Shared UTS test infra (mock transport, FakeClock, SandboxApp) from :uts's test-fixtures
    // variant. Compile-safe: the fixtures depend only on :java/:network-client-core, never on
    // this module (see the invariant note in uts/build.gradle.kts).
    testImplementation(testFixtures(project(":uts")))
}

tasks.withType<Test>().configureEach {
    testLogging {
        exceptionFormat = TestExceptionFormat.FULL
    }
    jvmArgs("--add-opens", "java.base/java.time=ALL-UNNAMED")
    jvmArgs("--add-opens", "java.base/java.lang=ALL-UNNAMED")
    beforeTest(closureOf<TestDescriptor> { logger.lifecycle("-> $this") })
    outputs.upToDateWhen { false }
}

tasks.register<Test>("runLiveObjectsUnitTests") {
    filter {
        includeTestsMatching("io.ably.lib.liveobjects.unit.*")     // the module's own unit tests
        includeTestsMatching("io.ably.lib.liveobjects.uts.unit.*") // UTS objects unit suite (skill-generated)
    }
}

tasks.register<Test>("runLiveObjectsIntegrationTests") {
    filter {
        includeTestsMatching("io.ably.lib.liveobjects.integration.*")
        // Exclude the base integration test class
        excludeTestsMatching("io.ably.lib.liveobjects.integration.setup.IntegrationTest")
    }
}

kotlin {
    explicitApi()
}
