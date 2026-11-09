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

        // Como en :app: en las pruebas de JVM las clases de Android son stubs
        // que lanzan excepción; con esto devuelven un valor por defecto. Sin
        // ello Process.setThreadPriority mata el hilo de emulación en su
        // primera línea (EmulatorControllerThreadTest).
        withHostTestBuilder {}.configure {
            isReturnDefaultValues = true
        }

        // Sin esto los textos de Compose Multiplatform (composeResources) no
        // entran en el APK: este tipo de módulo trae los recursos de Android
        // apagados, y es por ahí por donde se empaquetan. Las pruebas no lo
        // notan —no abren la app—; la app fallaría al primer texto.
        androidResources {
            enable = true
        }
    }

    // El framework `Shared` que enlaza la app de iOS (ios/app/). Dinámico: se
    // copia dentro de la app y trae enlazadas sus propias dependencias.
    //
    // Cada destino trae además el core: ds_core + melonDS, compilados con
    // CMake por las tareas buildDsCore* (abajo) y enlazados por cinterop.
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "Shared"
            isStatic = false
        }
        val coreDir = dsCoreBuildDir(target.name)
        target.compilations.getByName("main").cinterops.create("dsCore") {
            definitionFile.set(file("src/nativeInterop/cinterop/dsCore.def"))
            includeDirs(rootProject.file("native/ds_core"))
            extraOpts(
                "-libraryPath", coreDir.absolutePath,
                "-libraryPath", coreDir.resolve("melonDS/src").absolutePath,
                "-libraryPath", coreDir.resolve("melonDS/src/teakra/src").absolutePath,
            )
        }
    }

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
            // Las pantallas comunes (docs/ios.md, iOS-3 paso 4). En Android
            // estas dependencias se resuelven a las de AndroidX.
            api(libs.compose.multiplatform.foundation)
            api(libs.compose.multiplatform.ui)
            api(libs.compose.multiplatform.animation)
            api(libs.compose.multiplatform.material3)
            api(libs.compose.multiplatform.icons)
            // ViewModels y su estado en pantalla, y koinViewModel(), en común.
            // En Android se resuelven a los de AndroidX que ya usaba la app.
            api(libs.jetbrains.lifecycle.viewmodel.compose)
            api(libs.jetbrains.lifecycle.runtime.compose)
            api(project.dependencies.platform(libs.koin.bom))
            api(libs.koin.core)
            api(libs.koin.compose.viewmodel)
            implementation(libs.kotlinx.datetime)
            // La navegación y el botón atrás, comunes.
            api(libs.jetbrains.navigation.compose)
            implementation(libs.compose.multiplatform.backhandler)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
        }
        androidMain.dependencies {
            // Los selectores de archivos de Android (DocumentPickers.android.kt).
            implementation(libs.androidx.activity.compose)
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

// ---------------------------------------------------------------------------
// El core de iOS: native/ds_core (con melonDS dentro) como bibliotecas
// estáticas, una compilación por destino. Es el mismo ds_core que Android
// compila con el NDK, pero sin JIT: Apple no deja ejecutar código generado
// en tiempo de ejecución (ver docs/ios.md). Sólo en un Mac.
// ---------------------------------------------------------------------------

fun dsCoreBuildDir(target: String): File = layout.buildDirectory.dir("ds_core/$target").get().asFile

/**
 * CMake con ruta absoluta: Xcode lanza Gradle sin el PATH de la terminal, y
 * el de Homebrew no estaría.
 */
val cmakeExecutable: String = listOf("/opt/homebrew/bin/cmake", "/usr/local/bin/cmake")
    .firstOrNull { File(it).canExecute() } ?: "cmake"

mapOf("iosArm64" to "iphoneos", "iosSimulatorArm64" to "iphonesimulator").forEach { (target, sdk) ->
    val suffix = target.replaceFirstChar(Char::uppercaseChar)
    val buildDir = dsCoreBuildDir(target)

    val configure = tasks.register<Exec>("configureDsCore$suffix") {
        description = "Configura ds_core (CMake) para $target"
        onlyIf { System.getProperty("os.name").startsWith("Mac") }
        inputs.file(rootProject.file("native/ds_core/CMakeLists.txt"))
        outputs.file(buildDir.resolve("CMakeCache.txt"))
        commandLine(
            cmakeExecutable, "-S", rootProject.file("native/ds_core").absolutePath, "-B", buildDir.absolutePath,
            "-G", "Unix Makefiles",
            "-DCMAKE_SYSTEM_NAME=iOS", "-DCMAKE_OSX_SYSROOT=$sdk", "-DCMAKE_OSX_ARCHITECTURES=arm64",
            "-DCMAKE_OSX_DEPLOYMENT_TARGET=16.0", "-DCMAKE_BUILD_TYPE=Release",
            "-DBUILD_QT_SDL=OFF", "-DENABLE_JIT=OFF", "-DENABLE_OGLRENDERER=OFF",
            "-DENABLE_GDBSTUB=OFF", "-DENABLE_LTO_RELEASE=OFF",
        )
    }

    val build = tasks.register<Exec>("buildDsCore$suffix") {
        description = "Compila ds_core y melonDS para $target"
        dependsOn(configure)
        onlyIf { System.getProperty("os.name").startsWith("Mac") }
        // CMake ya compila sólo lo que cambió; aquí no se intenta adivinarlo.
        outputs.upToDateWhen { false }
        commandLine(cmakeExecutable, "--build", buildDir.absolutePath, "--target", "ds_core", "-j", "8")
    }

    tasks.matching { it.name == "cinteropDsCore$suffix" }.configureEach { dependsOn(build) }
}
