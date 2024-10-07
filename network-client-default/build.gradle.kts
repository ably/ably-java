plugins {
    `java-library`
    alias(libs.plugins.lombok)
    alias(libs.plugins.maven.publish)
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

dependencies {
    api(project(":network-client-core"))
    implementation(libs.java.websocket)
}
