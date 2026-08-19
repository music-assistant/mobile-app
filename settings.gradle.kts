rootProject.name = "MusicAssistantClient"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev") {
            mavenContent {
                includeGroupAndSubgroups("org.jetbrains")
            }
        }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev") {
            mavenContent {
                includeGroupAndSubgroups("org.jetbrains")
            }
        }
    }
}

// Local composite build of the Sendspin protocol library — Gradle substitutes the
// `com.sendspin:sendspin-protocol` dependency with this on-disk project (no publishing needed
// for development). Publishing to Maven Central is a later, separate step.
includeBuild("../sendspin-kmp")

include(":composeApp")
include(":androidApp")