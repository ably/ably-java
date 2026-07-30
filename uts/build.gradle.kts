import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-test-fixtures`
}

dependencies {
    // Shared UTS test infra (src/testFixtures/kotlin/io/ably/lib/uts/infra/**) — consumed by this
    // module's tests automatically and by other modules via testFixtures(project(":uts")).
    // INVARIANT: no testFixtures* configuration may ever depend on :liveobjects — that keeps
    // ":liveobjects test -> :uts testFixtures -> :java" acyclic against the testRuntimeOnly below.
    // `api` for types that appear in fixture signatures; `implementation` for internals.
    testFixturesApi(project(":java"))
    testFixturesApi(project(":network-client-core"))
    testFixturesImplementation(libs.coroutine.core)
    testFixturesImplementation(libs.ktor.client.core)
    testFixturesImplementation(libs.ktor.client.cio)

    testImplementation(project(":java"))
    testImplementation(project(":network-client-core"))
    // Runtime-only so compile-time stays decoupled from the plugin internals; the objects
    // integration/proxy tests need the LiveObjects plugin on the runtime classpath.
    testRuntimeOnly(project(":liveobjects"))
    testImplementation(kotlin("test"))
    // @ParameterizedTest / @ValueSource — version managed by the junit-bom on the test classpath.
    testImplementation("org.junit.jupiter:junit-jupiter-params")
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

tasks.register<Test>("runUtsUnitTests") {
    filter {
        includeTestsMatching("io.ably.lib.uts.unit.*")
    }
}

tasks.register<Test>("runUtsIntegrationTests") {
    filter {
        includeTestsMatching("io.ably.lib.uts.integration.*")
    }
}
