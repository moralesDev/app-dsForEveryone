// Los plugins se declaran aquí con `apply false` para que las versiones vivan en
// un único sitio: gradle/libs.versions.toml.
plugins {
    // AGP 9 trae soporte Kotlin integrado: el plugin org.jetbrains.kotlin.android
    // ya no se aplica (https://kotl.in/gradle/agp-built-in-kotlin).
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room) apply false
}
