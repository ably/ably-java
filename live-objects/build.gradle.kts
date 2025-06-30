import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    `java-library`
    alias(libs.plugins.kotlin.jvm)
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":java"))
    implementation(libs.bundles.common)
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
}

tasks.register<Test>("runLiveObjectUnitTests") {
    filter {
        includeTestsMatching("io.ably.lib.objects.unit.*")
    }
}

tasks.register<Test>("runLiveObjectIntegrationTests") {
    filter {
        includeTestsMatching("io.ably.lib.objects.integration.*")
        // Exclude the base integration test class
        excludeTestsMatching("io.ably.lib.objects.integration.setup.IntegrationTest")
    }
}

kotlin {
    explicitApi()
}
