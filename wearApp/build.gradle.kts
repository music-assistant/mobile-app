import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

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
    implementation(projects.composeApp)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.lifecycle.runtime.compose.android)
    implementation(libs.androidx.lifecycle.viewmodel.compose.android)

    implementation(libs.material.icons.core)
    implementation(libs.material.icons.extended)

    implementation(libs.androidx.wear)
    implementation(libs.androidx.wear.ongoing)
    // The phone app sends the server and the token through the Data Layer (`PhoneLinkStore`).
    implementation(libs.play.services.wearable)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.androidx.wear.compose.material3)
    implementation(libs.androidx.wear.compose.foundation)
    implementation(libs.androidx.wear.compose.navigation)

    implementation(libs.coil.compose)

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
