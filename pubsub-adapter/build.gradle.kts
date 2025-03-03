plugins {
    `java-library`
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.maven.publish)
}

kotlin {
    explicitApi()
}

dependencies {
    compileOnly(project(":java"))
    testImplementation(kotlin("test"))
    testImplementation(project(":java"))
    testImplementation(libs.nanohttpd)
    testImplementation(libs.coroutine.core)
    testImplementation(libs.coroutine.test)
    testImplementation(libs.turbine)
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.register<Test>("runUnitTests") {
    beforeTest(closureOf<TestDescriptor> { logger.lifecycle("-> $this") })
}
