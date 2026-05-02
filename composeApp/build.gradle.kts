import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

composeCompiler {
    stabilityConfigurationFile = project.layout.projectDirectory.file("compose-stability.conf")
}

compose.resources {
    publicResClass = true
}

kotlin {
    androidLibrary {
        namespace = "io.music_assistant.client.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
        androidResources {
            enable = true
        }
        withHostTestBuilder {
        }
    }

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
            binaryOption("bundleId", "io.music_assistant.client.composeapp")
        }

        // Test binaries need to fully resolve WebRTC at link time. The main
        // framework is static and defers WebRTC resolution to the iOS app's
        // final Xcode link, where `iosApp/Frameworks/WebRTC.xcframework` is on
        // the framework search path. Kotlin/Native test executables don't go
        // through that wiring, so we point them at the matching slice of the
        // bundled XCFramework directly. Without this, `:composeApp:*Test*`
        // tasks fail at `linkDebugTest*` with `framework 'WebRTC' not found`.
        val webrtcSlice = when (iosTarget.targetName) {
            "iosArm64" -> "ios-arm64"
            "iosSimulatorArm64", "iosX64" -> "ios-arm64_x86_64-simulator"
            else -> error("Unexpected iOS target: ${iosTarget.targetName}")
        }
        val webrtcSliceDir = rootProject.layout.projectDirectory
            .dir("iosApp/Frameworks/WebRTC.xcframework/$webrtcSlice")
            .asFile
            .absolutePath
        listOf(
            org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType.DEBUG,
            org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType.RELEASE,
        ).forEach { buildType ->
            iosTarget.binaries.findTest(buildType)?.linkerOpts(
                "-F", webrtcSliceDir,
                "-rpath", webrtcSliceDir,
            )
        }
    }

    sourceSets {
        androidMain.dependencies {
            implementation(libs.compose.ui.tooling.preview)
            implementation(libs.androidx.activity.compose)

            implementation(libs.ktor.client.okhttp)

            implementation(libs.koin.android)
            implementation(libs.koin.androidx.compose)

            implementation(libs.androidx.media)
            implementation(libs.androidx.browser)

            implementation(libs.coil)
            implementation(libs.concentus)
        }

        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.ui.tooling.preview)
            implementation(libs.material)
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.androidx.lifecycle.runtime.compose)
            implementation(libs.androidx.navigation3.ui)
            implementation(libs.androidx.navigation3.material3.adaptive)

            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.websockets)
            implementation(libs.ktor.client.json)
//            implementation(libs.ktor.client.logging)
            implementation(libs.kotlinx.coroutines.core)

            api(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.navigation.compose)

            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor3)
            implementation(libs.coil.svg)

            implementation(libs.kmpalette.core)
            implementation(libs.kmpalette.extensions.network)

            implementation(libs.material.icons.core)
            implementation(libs.material.icons.extended)
            implementation(libs.icons.fontawesome)
            implementation(libs.icons.tabler)
            implementation(libs.settings.multiplatform)
            implementation(libs.reorderable)

            implementation(libs.kermit)

            // WebRTC for remote access
            implementation(libs.webrtc.kmp)

            implementation(libs.easyqrscan)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }

        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
    }
}

dependencies {
    androidRuntimeClasspath(libs.compose.ui.tooling)
}
