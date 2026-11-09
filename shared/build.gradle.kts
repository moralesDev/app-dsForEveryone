// :shared — el código común entre Android e iOS (Kotlin Multiplatform).
//
// Fase iOS-2 de docs/ios.md. Aquí va lo que no depende de ninguna
// plataforma: primero la geometría de pantallas y mandos, que es lo que más
// costó y lo que más rinde compartir. Los paquetes no cambian
// (com.moralesDev.dsForEveryone.…), así que :app lo usa sin tocar imports.
//
// Las pruebas viven en commonTest con kotlin.test y corren en las dos
// plataformas:
//   bash gradlew :shared:testAndroidHostTest       # en la JVM, como antes
//   bash gradlew :shared:iosSimulatorArm64Test     # en el simulador de iOS (sólo Mac)
//   bash gradlew :shared:allTests                  # las dos
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
}

kotlin {
    android {
        namespace = "com.moralesDev.dsForEveryone.shared"
        compileSdk = 37
        minSdk = 26

        withHostTestBuilder {}
    }

    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
