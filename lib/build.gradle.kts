plugins {
  id("java-library")
}

repositories {
  mavenCentral()
}

java.sourceCompatibility = JavaVersion.VERSION_1_8
java.targetCompatibility = JavaVersion.VERSION_1_8

dependencies {
  implementation("org.msgpack:msgpack-core:0.8.11")
  implementation("org.java-websocket:Java-WebSocket:1.5.3")
  api("com.google.code.gson:gson:2.9.0")
  implementation("com.davidehrmann.vcdiff:vcdiff-core:0.1.1")
  testImplementation("org.hamcrest:hamcrest-all:1.3")
  testImplementation("junit:junit:4.12")
  testImplementation("org.nanohttpd:nanohttpd:2.3.0")
  testImplementation("org.nanohttpd:nanohttpd-nanolets:2.3.0")
  testImplementation("org.nanohttpd:nanohttpd-websocket:2.3.0")
  testImplementation("org.mockito:mockito-core:1.10.19")
  testImplementation("net.jodah:concurrentunit:0.4.2")
  testImplementation("org.slf4j:slf4j-simple:1.7.30")
}
