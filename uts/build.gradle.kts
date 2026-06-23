import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    testImplementation(project(":java"))
    testImplementation(project(":network-client-core"))
    testImplementation(kotlin("test"))
    testImplementation(libs.mockk)
    testImplementation(libs.coroutine.core)
    testImplementation(libs.coroutine.test)
    testImplementation(libs.ktor.client.core)
    testImplementation(libs.ktor.client.cio)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        exceptionFormat = TestExceptionFormat.FULL
    }
    jvmArgs("--add-opens", "java.base/java.time=ALL-UNNAMED")
    jvmArgs("--add-opens", "java.base/java.lang=ALL-UNNAMED")
    beforeTest(closureOf<TestDescriptor> { logger.lifecycle("-> $this") })
    outputs.upToDateWhen { false }

    // Gradle does not forward -D system properties to the forked test JVM, so propagate the
    // local uts-proxy override explicitly. Accepts either `-Duts.proxy.localPath=...` on the
    // Gradle invocation or the `UTS_PROXY_LOCAL_PATH` environment variable. See ProxyManager.
    systemProperty(
        "uts.proxy.localPath",
        providers.systemProperty("uts.proxy.localPath")
            .orElse(providers.environmentVariable("UTS_PROXY_LOCAL_PATH"))
            .getOrElse(""),
    )
}
