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
        commonMain.dependencies {
            // `api`: :app construye la base y el almacén de ajustes (AppModule),
            // así que necesita ver Room y DataStore.
            api(libs.androidx.room.runtime)
            api(libs.androidx.datastore.preferences.core)
        }
        iosMain.dependencies {
            // SQLite compilado dentro de la app: en iOS no hay uno del sistema
            // que Room sepa usar. En Android se sigue usando el del sistema.
            implementation(libs.androidx.sqlite.bundled)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
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
