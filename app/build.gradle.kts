import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
}

/**
 * Firma de release: clave de subida para Play Console. El archivo vive fuera
 * de control de versiones (`.gitignore`) y no existe en checkouts nuevos, así
 * que su ausencia no debe romper la compilación de depuración.
 */
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use(::load)
    }
}

android {
    namespace = "com.moralesDev.dsForEveryone"
    compileSdk = 37

    // La versión exacta con la que se comprobó que el core (melonDS 1.1)
    // compila de verdad. Fijarla evita que un NDK distinto entre por una
    // actualización de Android Studio y cambie el resultado sin que nadie lo
    // pida.
    ndkVersion = "28.2.13676358"

    defaultConfig {
        applicationId = "com.moralesDev.dsForEveryone"
        // 26 es el mínimo para AAudio, la ruta de audio prevista para la fase 4.
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"

        // Sólo ARM de 64 bits: es lo que llevan todos los teléfonos en los que
        // tiene sentido emular, y cada ABI extra duplica el binario.
        ndk {
            abiFilters += listOf("arm64-v8a")
        }

        externalNativeBuild {
            cmake {
                // Sin renderizador GL todavía: necesita `PlatformOGL.h`, que
                // se aporta más adelante. El JIT se mide en la 4.5 antes de
                // arriesgar el ensamblador ARM64 del recompilador. GDB stub
                // abre un socket, y aquí no hay ni un permiso de red que
                // declarar.
                arguments(
                    "-DBUILD_QT_SDL=OFF",
                    "-DENABLE_OGLRENDERER=OFF",
                    "-DENABLE_JIT=OFF",
                    "-DENABLE_GDBSTUB=OFF",
                )
                // Desde la 4.2 el puente (`dsemu`) enlaza `core` en su propio
                // .so, así que AGP vuelve a encontrar sola qué construir: ya
                // no hace falta pedir `core` por nombre (ver docs/integracion-
                // core.md, sección 6).
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("../native/emulator_bridge/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                storeFile = file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.exifinterface)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
