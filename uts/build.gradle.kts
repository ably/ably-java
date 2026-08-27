import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    `java-library`                          // NEW — provides the `api` configuration. Previously
                                            // arrived transitively via `java-test-fixtures`;
                                            // kotlin.jvm alone applies only the plain `java` plugin.
    alias(libs.plugins.kotlin.jvm)          // `java-test-fixtures` REMOVED
}

java {
    // Declare Java-8 outgoing variants (org.gradle.jvm.version=8) so :java's 8-requesting resolvable
    // configurations can consume project(":uts"). Only possible now that Phase 3 removed the
    // :liveobjects (Java-21) test edge that previously forced this module's classpaths back to 21.
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

dependencies {
    // The shared UTS test infra (src/main/kotlin/io/ably/lib/uts/infra/**) — this module's main
    // artifact. Consumed by other modules via testImplementation(project(":uts")).
    // INVARIANT (I1): :uts main (and its test config) has no :liveobjects dependency at all — the
    // objects tiers that needed the runtime plugin moved to :liveobjects.
    // `api` for types that appear in infra signatures; `implementation` for internals.
    api(project(":java"))
    api(project(":network-client-core"))
    // ktor stays implementation — the proxy infra uses it internally; it must NOT leak to consumers.
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)

    // The UTS test toolkit — exported (api) so any module consuming this infra via
    // testImplementation(project(":uts")) transitively gets the full stack needed to
    // WRITE and RUN UTS tests: JUnit 5 (api+params+engine via the aggregator), the
    // kotlin.test Jupiter binding, and coroutines (runTest etc.). Consumers declare
    // only the project dependency.
    api(platform(libs.junit.bom))
    api(libs.junit.jupiter)
    api(libs.junit.jupiter.params)
    api(kotlin("test-junit5"))
    api(libs.coroutine.core)      // promote from implementation (suspend-heavy public API anyway)
    api(libs.coroutine.test)

    // :uts's own smoke tests inherit the whole toolkit transitively from main's `api` above
    // (a module's test classpath sees its own main api/implementation deps) — nothing to declare.
    // Verified: the three smoke tests import only kotlin.test, JUnit Jupiter, coroutines and the
    // infra itself; they use ktor only through the infra, never directly.
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

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8) }
}
