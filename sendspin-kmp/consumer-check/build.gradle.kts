plugins {
    alias(libs.plugins.androidApplication) apply false
    // KGP for AGP's built-in Kotlin: one minor behind the library, the documented consumer floor.
    id("org.jetbrains.kotlin.android") version "2.3.21" apply false
}
