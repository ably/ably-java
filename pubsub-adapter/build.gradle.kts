plugins {
    `java-library`
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.maven.publish)
}

dependencies {
    compileOnly(project(":java"))
    testImplementation(project(":java"))
}
