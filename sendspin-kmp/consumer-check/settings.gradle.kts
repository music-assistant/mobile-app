// A standalone build, not part of the root build: an Android application that consumes
// io.github.music-assistant:sendspin-kmp from mavenLocal, as an external consumer does.
// Run: ./gradlew :sendspin-kmp:publishToMavenLocal, then
// ./gradlew -p sendspin-kmp/consumer-check :app:compileDebugKotlin :app:minifyReleaseWithR8 \
//     :app:checkReleasePermissions -PsendspinVersion=<version>
rootProject.name = "sendspin-consumer-check"

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        // The library comes only from mavenLocal, so a stale remote copy cannot hide a break.
        exclusiveContent {
            forRepository { mavenLocal() }
            filter { includeGroup("io.github.music-assistant") }
        }
        google()
        mavenCentral()
    }
    // The root catalog, so AGP, the SDK levels and Ktor follow the library.
    versionCatalogs {
        create("libs") {
            from(files("../../gradle/libs.versions.toml"))
        }
    }
}

include(":app")
