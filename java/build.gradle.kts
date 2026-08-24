import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.build.config)
    alias(libs.plugins.maven.publish)
    alias(libs.plugins.test.retry)
    checkstyle
    `java-library`
    alias(libs.plugins.kotlin.jvm)        // NEW — test-only usage; see stdlib guardrail (step 4)
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

tasks.withType<Jar> {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

dependencies {
    api(libs.gson)
    implementation(libs.bundles.common)
    compileOnly(libs.jetbrains)
    implementation(project(":network-client-core"))
    if (findProperty("httpURLConnection") == null) {
        runtimeOnly(project(":network-client-okhttp"))
    } else {
        runtimeOnly(project(":network-client-default"))
    }
    testImplementation(libs.bundles.tests)

    // The UTS test toolkit — the whole JUnit 5 + kotlin.test-junit5 + coroutines stack arrives
    // transitively via :uts's exported (`api`) toolkit, so consumers declare only this one edge.
    // The UTS Kotlin suites run via the runUts* tasks only (JUnit4 tasks don't discover Jupiter
    // classes and vice versa). Deliberately NO junit-vintage-engine here: unlike :liveobjects, the
    // legacy JUnit4 tests stay on the JUnit4 runner, never the platform.
    testImplementation(project(":uts"))
}

// kotlin-stdlib guardrail (invariant I5): the Kotlin plugin auto-adds kotlin-stdlib to the module's
// main dependency scope, which would leak into :java's published POM/runtime. :java is Kotlin-free at
// runtime, so strip it from the main artifact scopes. kotlin-stdlib still reaches the TEST classpath
// transitively (via :uts's kotlin-test-junit5), so the UTS Kotlin suites compile and run.
// Verified empirically on Kotlin 2.1.10: the plugin adds stdlib lazily (it does not appear in any
// declared `(n)` view but resolves top-level onto compile/runtimeClasspath), so the removeIf must
// cover the base scopes the outgoing variants (apiElements/runtimeElements) and classpaths inherit
// from. Test scopes are untouched.
listOf("api", "implementation", "runtimeOnly").forEach { cfg ->
    configurations.named(cfg) {
        withDependencies {
            removeIf { it.group == "org.jetbrains.kotlin" && it.name.startsWith("kotlin-stdlib") }
        }
    }
}

buildConfig {
    useJavaOutput()
    packageName = "io.ably.lib"
    buildConfigField("String", "LIBRARY_NAME", "\"java\"")
    buildConfigField("String", "VERSION", "\"${property("VERSION_NAME")}\"")
}

sourceSets {
    named("main") {
        java {
            srcDirs("src/main/java", "../lib/src/main/java")
        }
    }
    named("test") {
        java {
            srcDirs("src/test/java", "../lib/src/test/java")
        }
        kotlin {
            srcDirs("src/test/kotlin", "../lib/src/test/kotlin")   // NEW — UTS Kotlin suites only
        }
    }
    // main gets NO kotlin srcDir — :java main stays pure Java.
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_1_8) }   // match sourceCompatibility 1.8
}

tasks.checkstyleMain.configure {
    exclude("io/ably/lib/BuildConfig.java")
}

tasks.register<Test>("testRealtimeSuite") {
    filter {
        includeTestsMatching("*RealtimeSuite")
    }
    jvmArgs("--add-opens", "java.base/java.time=ALL-UNNAMED")
    jvmArgs("--add-opens", "java.base/java.lang=ALL-UNNAMED")
    beforeTest(closureOf<TestDescriptor> { logger.lifecycle("-> $this") })
    outputs.upToDateWhen { false }
    testLogging {
        exceptionFormat = TestExceptionFormat.FULL
    }
    retry {
        maxRetries.set(3)
        maxFailures.set(15)
        failOnPassedAfterRetry.set(false)
        failOnSkippedAfterRetry.set(false)
    }
}

tasks.register<Test>("testRestSuite") {
    filter {
        includeTestsMatching("*RestSuite")
    }
    jvmArgs("--add-opens", "java.base/java.time=ALL-UNNAMED")
    jvmArgs("--add-opens", "java.base/java.lang=ALL-UNNAMED")
    jvmArgs("--add-opens", "java.base/java.net=ALL-UNNAMED")
    jvmArgs("--add-opens", "java.base/java.lang.reflect=ALL-UNNAMED")
    beforeTest(closureOf<TestDescriptor> { logger.lifecycle("-> $this") })
    outputs.upToDateWhen { false }
    testLogging {
       exceptionFormat = TestExceptionFormat.FULL
    }
    retry {
        maxRetries.set(3)
        maxFailures.set(8)
        failOnPassedAfterRetry.set(false)
        failOnSkippedAfterRetry.set(false)
    }
}

/*
Test task to run pure unit tests, where pure means that they only run
locally and do not need to communicate with Ably servers.
This is achieved by excluding everything in the io.ably.lib.test package,
as it only contains the REST and Realtime suites.
*/
tasks.register<Test>("runUnitTests") {
    filter {
        excludeTestsMatching("io.ably.lib.test.*")
        excludeTestsMatching("io.ably.lib.uts.*")   // UTS Jupiter suites run via runUts* tasks only
    }
    jvmArgs("--add-opens", "java.base/java.time=ALL-UNNAMED")
    jvmArgs("--add-opens", "java.base/java.lang=ALL-UNNAMED")
    beforeTest(closureOf<TestDescriptor> { logger.lifecycle("-> $this") })
    outputs.upToDateWhen { false }
}

// UTS realtime suites (Kotlin, JUnit Jupiter). These are the only :java tasks on the JUnit Platform;
// the legacy JUnit4 tasks above never see the Jupiter classes (no vintage engine on the classpath),
// and these never see the JUnit4 classes. --add-opens is set per-task (not withType), so these new
// tasks must declare it explicitly.
tasks.register<Test>("runUtsUnitTests") {
    useJUnitPlatform()
    filter {
        includeTestsMatching("io.ably.lib.uts.unit.*")
    }
    jvmArgs("--add-opens", "java.base/java.time=ALL-UNNAMED")
    jvmArgs("--add-opens", "java.base/java.lang=ALL-UNNAMED")
    beforeTest(closureOf<TestDescriptor> { logger.lifecycle("-> $this") })
    outputs.upToDateWhen { false }
}

tasks.register<Test>("runUtsIntegrationTests") {
    useJUnitPlatform()
    filter {
        includeTestsMatching("io.ably.lib.uts.integration.*")
    }
    jvmArgs("--add-opens", "java.base/java.time=ALL-UNNAMED")
    jvmArgs("--add-opens", "java.base/java.lang=ALL-UNNAMED")
    beforeTest(closureOf<TestDescriptor> { logger.lifecycle("-> $this") })
    outputs.upToDateWhen { false }

    // Gradle does not forward -D system properties to the forked test JVM, so propagate the
    // local uts-proxy override explicitly (invariant I6; AuthReauthTest launches the proxy).
    // Accepts either `-Duts.proxy.localPath=...` on the Gradle invocation or the
    // `UTS_PROXY_LOCAL_PATH` environment variable. See ProxyManager.
    systemProperty(
        "uts.proxy.localPath",
        providers.systemProperty("uts.proxy.localPath")
            .orElse(providers.environmentVariable("UTS_PROXY_LOCAL_PATH"))
            .getOrElse(""),
    )
}
