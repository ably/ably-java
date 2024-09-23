plugins {
    `java-library`
    alias(libs.plugins.lombok)
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}
