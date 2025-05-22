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

    testImplementation(libs.coroutine.test)
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    explicitApi()
}
