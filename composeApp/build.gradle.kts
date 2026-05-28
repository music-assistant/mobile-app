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
    // expect/actual classes are still marked Beta (KT-61573) but the design is
    // stable and widely used across this codebase. Suppress the per-declaration
    // warnings rather than littering @OptIn annotations.
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

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
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
            binaryOption("bundleId", "io.music_assistant.client.composeapp")
            // Trades a touch of release-link optimization for ~28% faster
            // linkReleaseFrameworkIosArm64 and a smaller binary. Experimental
            // Kotlin/Native flag — revisit if release-build correctness regresses.
            binaryOption("smallBinary", "true")
        }

        // Note: the previous webrtc-kmp test-linker block lived here. It pointed
        // `:composeApp:*Test*` tasks at `iosApp/Frameworks/WebRTC.xcframework` so
        // Kotlin/Native test executables could resolve the WebRTC symbols at link
        // time. After the Phase A migration to `io.ktor:ktor-client-webrtc`, the
        // iOS engine pulls WebRTC via the WebRTC-SDK CocoaPod instead; that
        // XCFramework directory is gone and the linker hook would just fail.
        //
        // iOS still needs a runtime WebRTC framework to be present for the
        // ComposeApp framework to link against — see the `iosApp/iosApp.xcodeproj`
        // build phase (currently still references the deleted XCFramework). That's
        // a known follow-up; iOS hasn't been validated against the new engine yet.
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
            implementation(libs.androidx.navigation3.material3.viewmodel)

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

            // WebRTC for remote access.
            // Phase A spike: switched from `com.shepeliev:webrtc-kmp` to Ktor EAP.
            // See plans/let-s-investigate-possible-migration-sequential-pike.md.
            implementation(libs.ktor.client.webrtc)

            implementation(libs.easyqrscan)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
            implementation(libs.settings.multiplatform.test)
        }

        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
    }
}

dependencies {
    androidRuntimeClasspath(libs.compose.ui.tooling)
}
