import com.android.build.api.artifact.SingleArtifact

plugins {
    alias(libs.plugins.androidApplication)
}

android {
    namespace = "io.music_assistant.sendspin.consumercheck"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "io.music_assistant.sendspin.consumercheck"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
    }
    buildTypes {
        release {
            // R8 fails on a missing class in the library code that it traces from the application.
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }
}

dependencies {
    implementation("io.github.music-assistant:sendspin-kmp:${providers.gradleProperty("sendspinVersion").get()}")
    implementation(libs.ktor.client.okhttp)
}

// The library manifest must bring its permissions into the application manifest.
androidComponents {
    onVariants(selector().withBuildType("release")) { variant ->
        val manifest = variant.artifacts.get(SingleArtifact.MERGED_MANIFEST)
        tasks.register("checkReleasePermissions") {
            inputs.file(manifest)
            doLast {
                val text = manifest.get().asFile.readText()
                val missing = listOf("INTERNET", "ACCESS_NETWORK_STATE")
                    .filterNot { "\"android.permission.$it\"" in text }
                check(missing.isEmpty()) { "The merged release manifest has no permission $missing" }
            }
        }
    }
}
