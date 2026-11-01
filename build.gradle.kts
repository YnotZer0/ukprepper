// Top-level build file.
// AGP 9.x compiles Kotlin itself ("built-in Kotlin"), so the
// org.jetbrains.kotlin.android plugin must NOT be applied.
// The Compose compiler plugin version matches AGP 9.0's bundled Kotlin (2.2.10).
plugins {
    id("com.android.application") version "9.0.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false
}
