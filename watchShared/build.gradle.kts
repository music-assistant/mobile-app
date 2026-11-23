plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    // Three device/simulator slices, because Xcode builds a watchOS app for `arm64_32`
    // (every Apple Watch since Series 4) and `arm64` (the S9 generation and newer), and
    // runs it on an `arm64` simulator.
    listOf(
        watchosArm64(),
        watchosDeviceArm64(),
        watchosSimulatorArm64(),
    ).forEach { watchTarget ->
        watchTarget.binaries.framework {
            baseName = "MaWatchKit"
            // Static: the watch app is the only consumer, and a static framework needs no
            // embedding step and no dyld work at launch.
            isStatic = true
            binaryOption("bundleId", "io.music_assistant.client.watchkit")
        }
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.websockets)
                // Not used as a WebSocket content converter — the session frames its own
                // JSON. It is here because it brings kotlinx-serialization-json as an api
                // dependency, which is how every other module in this build gets it.
                implementation(libs.ktor.client.json)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kermit)
            }
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }

        watchosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
    }
}
