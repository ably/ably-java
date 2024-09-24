plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "io.ably"
    defaultConfig {
        minSdk = 19
        compileSdk = 30
        buildConfigField("String", "LIBRARY_NAME", "\"android\"")
        buildConfigField("String", "VERSION", "\"${property("VERSION")}\"")
        testInstrumentationRunner = "android.support.test.runner.AndroidJUnitRunner"
        testInstrumentationRunnerArguments["class"] = "io.ably.lib.test.android.AndroidPushTest"
        testInstrumentationRunnerArguments["timeout_msec"] = "300000"
        consumerProguardFiles("proguard.txt")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        buildConfig = true
    }

    lint {
        abortOnError = false
    }

    sourceSets {
        getByName("main") {
            java.srcDirs("src/main/java", "../lib/src/main/java")
        }
        getByName("androidTest") {
            java.srcDirs("src/androidTest/java", "../lib/src/test/java")
            assets.srcDirs("../lib/src/test/resources")
        }
    }
}

dependencies {
    api(libs.gson)
    implementation(libs.bundles.common)
    testImplementation(libs.bundles.tests)
    implementation("com.google.firebase:firebase-messaging:22.0.0")
    androidTestImplementation("com.android.support.test:runner:0.5")
    androidTestImplementation("com.android.support.test:rules:0.5")
    androidTestImplementation("com.crittercism.dexmaker:dexmaker:1.4")
    androidTestImplementation("com.crittercism.dexmaker:dexmaker-dx:1.4")
    androidTestImplementation("com.crittercism.dexmaker:dexmaker-mockito:1.4")
    androidTestImplementation("net.sourceforge.streamsupport:android-retrostreams:1.7.4")
}

configurations {
    all {
        exclude(group = "org.hamcrest", module = "hamcrest-core")
    }
    getByName("androidTestImplementation") {
        extendsFrom(configurations.getByName("testImplementation"))
    }
}
