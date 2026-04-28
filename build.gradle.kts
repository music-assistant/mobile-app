plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.androidMultiplatformLibrary) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.detekt)
}

// Capture catalog values at the root scope so they're closure-captured into subprojects {}
// (the `libs` accessor isn't visible inside subprojects).
val detektVersion = libs.versions.detekt.get()
val detektFormatting = libs.detekt.formatting

// CI mode: when running under GitHub Actions / generic CI (`CI=true` env var), do NOT
// auto-correct (we want the build to fail loudly on any finding, not silently
// rewrite the runner's workspace). Locally, autoCorrect stays on for convenience.
val isCi = (System.getenv("CI") ?: "").equals("true", ignoreCase = true)

subprojects {
    apply(plugin = "io.gitlab.arturbosch.detekt")

    extensions.configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
        toolVersion = detektVersion
        config.setFrom(rootProject.files("config/detekt/detekt.yml"))
        buildUponDefaultConfig = true
        autoCorrect = !isCi
        // No baseline — we commit to clean. Any finding fails the build (locally,
        // autoCorrect runs first so this only fires on non-fixable rules).
        ignoreFailures = false
        parallel = true
    }

    dependencies {
        add("detektPlugins", detektFormatting)
    }

    tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
        // Skip generated/build artifacts and SVG-as-Kotlin icon files (path coordinates, not magic numbers).
        exclude("**/build/**")
        exclude("**/generated/**")
        exclude("**/icons/**")
        jvmTarget = "17"
        reports {
            html.required.set(true)
            xml.required.set(true)
            txt.required.set(false)
            sarif.required.set(false)
            md.required.set(false)
        }
    }

    tasks.withType<io.gitlab.arturbosch.detekt.DetektCreateBaselineTask>().configureEach {
        jvmTarget = "17"
    }

    // Detekt's default source detection doesn't reliably cover KMP modules (commonMain,
    // androidMain, iosMain) or Android-only modules (src/main/kotlin). Wire sources in
    // explicitly so the `detekt` task lints everything regardless of plugin layout.
    afterEvaluate {
        val sourceDirs = mutableSetOf<java.io.File>()
        val kotlinExtension = extensions.findByName("kotlin")
        if (kotlinExtension is org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension) {
            kotlinExtension.sourceSets.forEach { sourceSet ->
                sourceSet.kotlin.srcDirs.forEach { sourceDirs.add(it) }
            }
        }
        // Android-style fallback: pick up src/{main,test,debug,release}/{kotlin,java}.
        listOf("main", "test", "androidTest", "debug", "release").forEach { variant ->
            listOf("kotlin", "java").forEach { lang ->
                sourceDirs.add(file("src/$variant/$lang"))
            }
        }
        val existing = sourceDirs.filter { it.exists() }
        if (existing.isNotEmpty()) {
            tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
                setSource(existing)
                include("**/*.kt", "**/*.kts")
            }
        }
    }
}

// Convenience aggregator: ./gradlew detektAll
tasks.register("detektAll") {
    group = "verification"
    description = "Runs detekt on every subproject."
    dependsOn(subprojects.map { it.tasks.named("detekt") })
}
