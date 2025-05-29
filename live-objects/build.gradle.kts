import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    `java-library`
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.test.retry)
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":java"))
    implementation(libs.coroutine.core)

    testImplementation(kotlin("test"))
    testImplementation(libs.bundles.kotlin.tests)
}

tasks.withType<Test>().configureEach {
    testLogging {
        exceptionFormat = TestExceptionFormat.FULL
    }
    jvmArgs("--add-opens", "java.base/java.time=ALL-UNNAMED")
    jvmArgs("--add-opens", "java.base/java.lang=ALL-UNNAMED")
    beforeTest(closureOf<TestDescriptor> { logger.lifecycle("-> $this") })
    outputs.upToDateWhen { false }
    // Skip tests for the "release" build type so we don't run tests twice
    if (name.lowercase().contains("release")) {
        enabled = false
    }
}

tasks.register<Test>("runLiveObjectUnitTests") {
    filter {
        includeTestsMatching("io.ably.lib.objects.unit.*")
    }
}

tasks.register<Test>("runLiveObjectIntegrationTests") {
    filter {
        includeTestsMatching("io.ably.lib.objects.integration.*")
    }
    // TODO - check if we need retry mechanism for integration tests in the future
}

kotlin {
    explicitApi()
}
