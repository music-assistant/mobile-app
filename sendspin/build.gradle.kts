import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.androidMultiplatformLibrary)
}

kotlin {
    android {
        namespace = "io.music_assistant.sendspin"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
        withHostTestBuilder {
        }
    }

    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.atomicfu)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.websockets)
            // Brings kotlinx-serialization-json as an API dependency.
            implementation(libs.ktor.client.json)
            implementation(libs.kermit)
            // Crypto primitives for the Noise (encrypted) protocol.
            implementation(libs.cryptography.core)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
            implementation(libs.ktor.client.mock)
        }

        androidMain.dependencies {
            // Noise primitives backend (JCA crypto via the JDK provider).
            implementation(libs.cryptography.provider.jdk)
            implementation(libs.concentus)
        }

        iosMain.dependencies {
            // Noise primitives backend (CryptoKit: X25519, ChaCha20-Poly1305).
            implementation(libs.cryptography.provider.cryptokit)
        }
    }
}
