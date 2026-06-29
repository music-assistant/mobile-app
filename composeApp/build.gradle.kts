import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.TestExecutable

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

composeCompiler {
    stabilityConfigurationFiles.add(project.layout.projectDirectory.file("compose-stability.conf"))
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

    android {
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

        val webRtcSlice = if (iosTarget.name == "iosSimulatorArm64") {
            "ios-arm64_x86_64-simulator"
        } else {
            "ios-arm64"
        }
        iosTarget.binaries.withType<TestExecutable>().configureEach {
            linkerOpts("-F${project.rootDir}/iosApp/Frameworks/WebRTC.xcframework/$webRtcSlice")
        }

        val copyWebRtcForTests = tasks.register<Copy>("copyWebRtcFor${iosTarget.name.replaceFirstChar { it.uppercase() }}Tests") {
            description = "Copies the WebRTC framework slice for ${iosTarget.name} into the test build dir so the simulator can load it."
            from("${project.rootDir}/iosApp/Frameworks/WebRTC.xcframework/$webRtcSlice/WebRTC.framework")
            into(layout.buildDirectory.dir("bin/${iosTarget.name}/debugTest/Frameworks/WebRTC.framework"))
        }
        tasks.matching { it.name == "${iosTarget.name}Test" }.configureEach {
            dependsOn(copyWebRtcForTests)
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
            implementation(libs.androidx.navigation3.material3.viewmodel)

            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.websockets)
            implementation(libs.ktor.client.json)
//            implementation(libs.ktor.client.logging)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.atomicfu)

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

// --- Material Design Icons (community pack) webfont + codepoint table ---------
//
// The server sends icon identifiers from the MDI community pack (e.g. "mdi-speaker").
// We render them as font glyphs (see MdiIcon.kt) instead of hand-mapping each name to a
// look-alike from another pack. This task fetches the official webfont and projects the
// pack's meta.json down to a slim { name -> codepoint } table.
//
// It is NETWORK-BOUND and intentionally lazy: it only runs when an output is missing or
// the pinned version changes (tracked via a build-dir marker). The generated assets live
// under composeResources/ and are committed, so ordinary/offline builds never re-download.
val mdiVersion = "7.4.47"
val mdiFontOut = layout.projectDirectory.file("src/commonMain/composeResources/font/mdi_webfont.ttf")
val mdiCodepointsOut = layout.projectDirectory.file("src/commonMain/composeResources/files/mdi_codepoints.json")
val mdiVersionMarker = layout.buildDirectory.file("mdi/version.marker")

val generateMdiResources by tasks.registering {
    description = "Fetches the MDI webfont and generates a slim name->codepoint table."
    group = "build setup"
    inputs.property("mdiVersion", mdiVersion)
    outputs.file(mdiFontOut)
    outputs.file(mdiCodepointsOut)
    outputs.file(mdiVersionMarker)
    onlyIf {
        !mdiFontOut.asFile.exists() ||
            !mdiCodepointsOut.asFile.exists() ||
            mdiVersionMarker.get().asFile.takeIf { it.exists() }?.readText() != mdiVersion
    }
    doLast {
        val fontUrl = "https://cdn.jsdelivr.net/npm/@mdi/font@$mdiVersion/fonts/materialdesignicons-webfont.ttf"
        val metaUrl = "https://cdn.jsdelivr.net/npm/@mdi/svg@$mdiVersion/meta.json"

        mdiFontOut.asFile.parentFile.mkdirs()
        uri(fontUrl).toURL().openStream().use { input ->
            mdiFontOut.asFile.outputStream().use { input.copyTo(it) }
        }

        @Suppress("UNCHECKED_CAST")
        val entries = groovy.json.JsonSlurper()
            .parseText(uri(metaUrl).toURL().readText()) as List<Map<String, Any?>>
        val table = entries.joinToString(",", "{", "}") { e ->
            "\"${e["name"]}\":\"${e["codepoint"]}\""
        }
        mdiCodepointsOut.asFile.parentFile.mkdirs()
        mdiCodepointsOut.asFile.writeText(table)

        mdiVersionMarker.get().asFile.apply { parentFile.mkdirs(); writeText(mdiVersion) }
        logger.lifecycle("generateMdiResources: wrote ${entries.size} MDI codepoints + webfont (v$mdiVersion).")
    }
}

// Ensure the assets exist before Compose generates resource accessors (so Res.font.* and
// the files/ table are present for both the IDE sync and clean CI builds).
tasks.matching {
    it.name.contains("ComposeResources") || it.name.contains("ResourceAccessors")
}.configureEach { dependsOn(generateMdiResources) }
