import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.androidMultiplatformLibrary)
    `maven-publish`
}

group = "io.github.music-assistant"
version = providers.gradleProperty("sendspinVersion").get()

kotlin {
    explicitApi()

    // checkKotlinAbi compares the public API with the committed dump in api/. The dump comes
    // from the iOS klibs, so it covers commonMain but not the androidMain-only package.
    @OptIn(ExperimentalAbiValidation::class)
    abiValidation()

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
            // The default already publishes a declared file; the API is incubating, so state it.
            consumerKeepRules.publish = true
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

        getByName("androidHostTest").dependencies {
            implementation(libs.junit)
            implementation(libs.robolectric)
        }

        iosMain.dependencies {
            // Noise primitives backend (CryptoKit: X25519, ChaCha20-Poly1305).
            implementation(libs.cryptography.provider.cryptokit)
        }
    }
}

// KGP creates one publication per target plus the root `kotlinMultiplatform` one.
publishing {
    publications.withType<MavenPublication>().configureEach {
        pom {
            name.set("sendspin-kmp")
            description.set("Kotlin Multiplatform Sendspin client for the player@v1 role")
            url.set("https://github.com/music-assistant/mobile-app")
            licenses {
                license {
                    name.set("Apache-2.0")
                    url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                }
            }
            scm {
                url.set("https://github.com/music-assistant/mobile-app")
                connection.set("scm:git:https://github.com/music-assistant/mobile-app.git")
            }
        }
    }
    repositories {
        // The package belongs to the repository that runs the publish workflow.
        maven {
            name = "GitHubPackages"
            url = uri(
                "https://maven.pkg.github.com/" +
                    providers.environmentVariable("GITHUB_REPOSITORY").getOrElse("music-assistant/mobile-app"),
            )
            credentials {
                username = providers.environmentVariable("GITHUB_ACTOR").orNull
                password = providers.environmentVariable("GITHUB_TOKEN").orNull
            }
        }
    }
}
