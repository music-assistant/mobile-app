import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

/**
 * Server coordinates and the long-lived token, read from the git-ignored
 * `local.properties` at the repository root. Keeping them out of the sources means the
 * token never reaches a commit, and swapping servers is a rebuild rather than an edit.
 *
 * Add to `local.properties`:
 * ```
 * ma.host=192.168.1.10
 * ma.port=8095
 * ma.tls=false
 * ma.token=<long-lived access token>
 * ```
 * A missing value falls back to the Music Assistant defaults and an empty token, which
 * the watch surfaces as "Not configured" instead of failing the build.
 */
val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
}

fun localProperty(key: String, fallback: String): String =
    (localProperties[key] as? String)?.trim()?.takeIf { it.isNotEmpty() } ?: fallback

android {
    namespace = "io.music_assistant.client.wear"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        // Shared with the phone app on purpose: Play pairs a watch APK to its phone app by
        // application id. The version code is offset so the two never collide in a release.
        applicationId = "io.music_assistant.client"
        minSdk = 33
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1019
        versionName = "0.18.0"

        buildConfigField("String", "MA_HOST", "\"${localProperty("ma.host", "homeassistant.local")}\"")
        buildConfigField("int", "MA_PORT", localProperty("ma.port", "8095"))
        buildConfigField("boolean", "MA_TLS", localProperty("ma.tls", "false"))
        buildConfigField("String", "MA_TOKEN", "\"${localProperty("ma.token", "")}\"")
    }

    buildFeatures {
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    signingConfigs {
        create("selfSigned") {
            storeFile = file(System.getProperty("user.home") + "/.android/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            // Shrinking is off while the watch app is sideloaded only: an R8 pass would have to
            // keep the kotlinx-serialization models and the Compose runtime, and a stripped
            // model surfaces as an empty screen at runtime rather than a build failure.
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("selfSigned")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildToolsVersion = "36.0.0"
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.lifecycle.runtime.compose.android)
    implementation(libs.androidx.lifecycle.viewmodel.compose.android)

    implementation(libs.androidx.wear)
    implementation(libs.androidx.wear.ongoing)
    implementation(libs.androidx.wear.compose.material3)
    implementation(libs.androidx.wear.compose.foundation)
    implementation(libs.androidx.wear.compose.navigation)

    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.websockets)
    implementation(libs.ktor.client.json)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.material.color.utilities)
    implementation(libs.kermit)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test.junit)
}
