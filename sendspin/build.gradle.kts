import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.androidMultiplatformLibrary)
}

kotlin {
    explicitApi()

    // Metadata that a consumer one Kotlin minor behind can read.
    compilerOptions {
        languageVersion.set(KotlinVersion.KOTLIN_2_3)
        apiVersion.set(KotlinVersion.KOTLIN_2_3)
    }

    android {
        namespace = "io.music_assistant.sendspin"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
        optimization {
            consumerKeepRules.file("consumer-rules.pro")
        }
        withHostTestBuilder {
        }
    }

    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            // Public API types: StateFlow, Flow, CoroutineDispatcher, HttpClient.
            api(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.atomicfu)
            api(libs.ktor.client.core)
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
            // X25519, which the platform JCA registers only from API 33.
            implementation(libs.bouncycastle.provider)
            // Opus decoding for AndroidDecoderFactory.
            implementation(libs.concentus)
        }

        iosMain.dependencies {
            // Noise primitives backend (CryptoKit: X25519, ChaCha20-Poly1305).
            implementation(libs.cryptography.provider.cryptokit)
        }
    }
}
