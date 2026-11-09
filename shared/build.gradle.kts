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
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    android {
        namespace = "com.moralesDev.dsForEveryone.shared"
        compileSdk = 37
        minSdk = 26

        withHostTestBuilder {}

        // Sin esto los textos de Compose Multiplatform (composeResources) no
        // entran en el APK: este tipo de módulo trae los recursos de Android
        // apagados, y es por ahí por donde se empaquetan. Las pruebas no lo
        // notan —no abren la app—; la app fallaría al primer texto.
        androidResources {
            enable = true
        }
    }

    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            // `api`: :app construye la base y el almacén de ajustes (AppModule),
            // así que necesita ver Room y DataStore.
            api(libs.androidx.room.runtime)
            api(libs.androidx.datastore.preferences.core)
            // Archivos (StorageManager, partidas, trucos). `api` porque :app
            // convierte sus rutas a java.io.File (StorageFiles.android.kt).
            api(libs.okio)
            // Los textos de la app (composeResources/values*/strings.xml) y
            // stringResource(): `api` porque las pantallas de :app los usan.
            api(libs.compose.multiplatform.runtime)
            api(libs.compose.multiplatform.resources)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
        }
        iosMain.dependencies {
            // SQLite compilado dentro de la app: en iOS no hay uno del sistema
            // que Room sepa usar. En Android se sigue usando el del sistema.
            implementation(libs.androidx.sqlite.bundled)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

// Room genera su código con KSP, una vez por plataforma.
dependencies {
    add("kspAndroid", libs.androidx.room.compiler)
    add("kspIosArm64", libs.androidx.room.compiler)
    add("kspIosSimulatorArm64", libs.androidx.room.compiler)
}

// El esquema exportado de la base de datos, versionado en git: es lo que
// permite escribir migraciones cuando cambie (ver DsEmuDatabase).
room {
    schemaDirectory("$projectDir/schemas")
}

// Textos traducidos (docs/ios.md, iOS-3 paso 3). La clase Res se genera en
// un paquete fijo y pública, porque la usan las pantallas de :app.
compose.resources {
    publicResClass = true
    packageOfResClass = "com.moralesDev.dsForEveryone.resources"
    generateResClass = always
}
