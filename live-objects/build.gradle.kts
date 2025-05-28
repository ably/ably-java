plugins {
    `java-library`
    alias(libs.plugins.kotlin.jvm)
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":java"))
    testImplementation(kotlin("test"))
    implementation(libs.coroutine.core)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.coroutine.test)
    testImplementation(libs.nanohttpd)
    testImplementation(libs.turbine)
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    explicitApi()
}
