// Top-level build file where you can add configuration options common to all sub-projects/modules.

plugins {
    alias(libs.plugins.android.library) apply false
}

subprojects {
    repositories {
        google()
        mavenCentral()
    }
}
