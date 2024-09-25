import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    id("com.github.gmazzo.buildconfig") version "5.4.0"
    checkstyle
    `java-library`
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

dependencies {
    api(libs.gson)
    implementation(libs.bundles.common)
    implementation(project(":network-client-core"))
    runtimeOnly(project(":network-client-default"))
    testImplementation(libs.bundles.tests)
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
    }
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
}

tasks.register<Test>("testRestSuite") {
    filter {
        includeTestsMatching("*RestSuite")
    }
    jvmArgs("--add-opens", "java.base/java.time=ALL-UNNAMED")
    jvmArgs("--add-opens", "java.base/java.lang=ALL-UNNAMED")
    beforeTest(closureOf<TestDescriptor> { logger.lifecycle("-> $this") })
    outputs.upToDateWhen { false }
    testLogging {
       exceptionFormat = TestExceptionFormat.FULL
    }
}

tasks.register<Test>("runUnitTests") {
    filter {
        excludeTestsMatching("io.ably.lib.test.*")
    }
    jvmArgs("--add-opens", "java.base/java.time=ALL-UNNAMED")
    jvmArgs("--add-opens", "java.base/java.lang=ALL-UNNAMED")
    beforeTest(closureOf<TestDescriptor> { logger.lifecycle("-> $this") })
    outputs.upToDateWhen { false }
}
