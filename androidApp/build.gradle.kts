import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

android {
    namespace = "io.music_assistant.client"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "io.music_assistant.client"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 15
        versionName = "0.14.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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

        create("release") {
            val props = Properties().apply {
                val file = project.file("keystore.properties")
                if (file.exists()) load(file.inputStream())
            }
            storeFile = props["storeFile"]?.let { file(it as String) }
            storePassword = props["storePassword"] as? String
            keyAlias = props["keyAlias"] as? String
            keyPassword = props["keyPassword"] as? String
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }

        create("selfSignedRelease") {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("selfSigned")
        }
    }

    // ABI splits for the GitHub-distributed APK only. Harmless to the Play AAB:
    // `splits` is ignored when building an app bundle, so a combined
    // `bundleRelease assembleRelease` invocation still yields an all-ABI bundle.
    splits {
        abi {
            isEnable = gradle.startParameter.taskNames.any {
                it.contains("assembleRelease", ignoreCase = true) ||
                    it.contains("SelfSignedRelease", ignoreCase = true)
            }
            reset()
            include("arm64-v8a")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildToolsVersion = "36.0.0"

    lint {
        baseline = file("lint-baseline.xml")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

android {
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    implementation(projects.composeApp)
    implementation(libs.compose.components.resources)
    implementation(libs.androidx.activity.compose)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    implementation(libs.androidx.media)
    implementation(libs.androidx.car.app)
    implementation(libs.coil)
    implementation(libs.kermit)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.ktor.client.json)
    testImplementation(libs.koin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.compose.components.resources)

    // Instrumented tests: real device/emulator only, for the class of Window/focus behavior
    // Robolectric doesn't simulate (see SelectPlayerDialogDpadLeakTest in androidTest).
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.kotlin.test)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.test.uiautomator)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.compose.components.resources)
    androidTestImplementation(libs.material)
}

@CacheableTask
abstract class GenerateLocalesConfig : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val resourcesDir: DirectoryProperty

    @get:Input
    abstract val baseLanguage: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val qualifier = Regex("""^values-([a-z]{2,3})(?:-r([A-Z]{2}))?$""")
        val locales = resourcesDir.get().asFile.listFiles().orEmpty()
            .filter { it.isDirectory && it.name.startsWith("values") }
            .map { dir ->
                if (dir.name == "values") baseLanguage.get()
                else qualifier.matchEntire(dir.name)
                    ?.groupValues?.drop(1)?.filter(String::isNotEmpty)?.joinToString("-")
                    ?: error("Unsupported locale directory '${dir.name}'. Teach GenerateLocalesConfig its BCP-47 form.")
            }
            .sorted()

        outputDir.get().asFile.resolve("xml/locales_config.xml").apply { parentFile.mkdirs() }
            .writeText(
                buildString {
                    appendLine("""<?xml version="1.0" encoding="utf-8"?>""")
                    appendLine("""<locale-config xmlns:android="http://schemas.android.com/apk/res/android">""")
                    locales.forEach { appendLine("""    <locale android:name="$it" />""") }
                    append("</locale-config>")
                }
            )
    }
}

// Android needs an explicit locale list for the per-app language picker. The shipped
// languages are the Lokalise-synced `values-*` directories, so the list is derived from
// them; a hand-written copy drifts on the next translation pull without any build error.
val generateLocalesConfig = tasks.register<GenerateLocalesConfig>("generateLocalesConfig") {
    resourcesDir.set(rootProject.layout.projectDirectory.dir("composeApp/src/commonMain/composeResources"))
    baseLanguage.set("en")
    outputDir.set(layout.buildDirectory.dir("generated/res/localesConfig"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.res?.addGeneratedSourceDirectory(
            generateLocalesConfig,
            GenerateLocalesConfig::outputDir
        )
    }
}

