import com.vanniktech.maven.publish.MavenPublishBaseExtension

// Top-level build file where you can add configuration options common to all sub-projects/modules.

plugins {
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.maven.publish) apply false
    alias(libs.plugins.lombok) apply false
    alias(libs.plugins.test.retry) apply false
}

subprojects {
    repositories {
        google()
        mavenCentral()
    }

    tasks.withType<Javadoc> {
        // To prevent javadoc warnings with Java 8
        options {
            this as StandardJavadocDocletOptions
            addBooleanOption("Xdoclint:none", true)
            addBooleanOption("quiet", true)
            addStringOption("Xmaxwarns", "1")
        }
    }
}

configure(subprojects) {
    pluginManager.withPlugin("com.vanniktech.maven.publish") {
        extensions.configure<MavenPublishBaseExtension> {
            signAllPublications()
        }
    }
}
