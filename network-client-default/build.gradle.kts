plugins {
    id("java-library")
    id("io.freefair.lombok")
}

tasks.compileJava {
    sourceCompatibility = "1.8"
    targetCompatibility = "1.8"
}

repositories {
    mavenCentral()
}

dependencies {
    api(project(":network-client-core"))
    implementation("org.java-websocket:Java-WebSocket:1.5.3")
}
