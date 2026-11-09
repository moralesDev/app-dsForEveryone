import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
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
                //
                // CMAKE_BUILD_TYPE explícito y a propósito: AGP, si no se le
                // dice nada, compila el core en `Debug` (sin optimizar, `-O0`)
                // para la variante `debug` de Gradle y en `RelWithDebInfo`
                // (`-O2`) sólo para `release`. Para un core de CPU/JIT como
                // este la diferencia es real —varias veces la velocidad—, y
                // «cómo se siente de fluido un juego pesado» es algo que hay
                // que poder probar sin pasar por la firma y el minify de
                // release. `RelWithDebInfo` en las dos variantes: optimizado,
                // con símbolos de depuración conservados.
                arguments(
                    "-DBUILD_QT_SDL=OFF",
                    "-DENABLE_OGLRENDERER=OFF",
                    "-DENABLE_JIT=ON",
                    "-DENABLE_GDBSTUB=OFF",
                    "-DCMAKE_BUILD_TYPE=RelWithDebInfo",
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

// Verifica que el objeto compilado de nuestro puente (ds_core.cpp.o, no
// libdsemu.so entero) contenga la constante BRIDGE_CORE_VERSION ("1.1").
// Desde la fase iOS-1 vive en native/ds_core/, compartida con iOS.
//
// A diferencia de mGBA en `app-emulador`, melonDS 1.1 no tiene el bug de
// `version.cmake` escapándose a un `.git` ajeno (ver docs/integracion-
// core.md, sección 6): su versión es un literal en CMakeLists.txt, no algo
// que `git describe` pueda ensuciar. Aun así, la regla general del proyecto
// hermano se mantiene — lo que se graba en cada save state es una constante
// del puente, nunca un símbolo del core— y esta tarea es la que la hace
// valer sobre el binario real, no sobre una promesa en un comentario.
// GoldenSaveStateTest (app/src/test/) no puede detectar una regresión aquí:
// corre en JVM puro sin NDK y compara un fixture congelado contra un
// literal de Kotlin, nunca invoca NativeBridge.nativeCoreVersion() ni carga
// el .so.
//
// Segunda comprobación: un grep sobre nuestras propias fuentes (los .cpp/.h
// de native/ds_core y native/emulator_bridge y los .kt de app/src/main/java,
// comentarios descontados) que falla si alguien referencia los símbolos de
// third_party/melonDS/src/version.h.in que identifican una compilación
// concreta (MELONDS_VERSION, MELONDS_GIT_BRANCH, MELONDS_GIT_HASH,
// MELONDS_BUILD_PROVIDER) en vez de la constante propia del puente.
abstract class VerifyCoreVersionTask : DefaultTask() {

    @get:Internal
    abstract val cxxBuildDirectory: DirectoryProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val guardedSources: ConfigurableFileCollection

    init {
        outputs.upToDateWhen { false }
    }

    @TaskAction
    fun verify() {
        verifyCompiledObject()
        verifySourcesDontReferenceCoreVersionSymbols()
    }

    private fun verifyCompiledObject() {
        val root = cxxBuildDirectory.get().asFile
        val objectFile = root.walkTopDown().firstOrNull { it.name == "ds_core.cpp.o" }
            ?: error(
                "No se encontró ds_core.cpp.o bajo $root: ¿cambió el layout de " +
                    ".cxx o el nombre del objeto?",
            )

        // minLength=3: BRIDGE_CORE_VERSION ("1.1") es más corta que la de
        // mGBA en app-emulador ("0.10.5"), y con el umbral de 4 caracteres de
        // ese proyecto quedaría descartada sin llegar a compararse.
        val strings = extractPrintableStrings(objectFile.readBytes(), minLength = 3)
        check("1.1" in strings) {
            "ds_core.cpp.o no contiene \"1.1\": BRIDGE_CORE_VERSION cambió sin " +
                "actualizar esta comprobación (o dejó de ser un literal)."
        }
    }

    private fun verifySourcesDontReferenceCoreVersionSymbols() {
        val forbidden = listOf(
            "MELONDS_VERSION",
            "MELONDS_GIT_BRANCH",
            "MELONDS_GIT_HASH",
            "MELONDS_BUILD_PROVIDER",
        )
        val offenders = guardedSources.files
            .filter { it.isFile }
            .flatMap { file ->
                val code = stripComments(file.readText())
                forbidden
                    .filter { symbol -> Regex("\\b${Regex.escape(symbol)}\\b").containsMatchIn(code) }
                    .map { symbol -> "${file.name}: $symbol" }
            }
        check(offenders.isEmpty()) {
            "Código propio referencia símbolos de version.h de melonDS (fuera de " +
                "comentarios) en vez de BRIDGE_CORE_VERSION — es exactamente el bug " +
                "de coreVersion de app-emulador entrando por otra puerta: $offenders"
        }
    }

    // Quita comentarios "//" y "/*"+"*/": C++ y Kotlin comparten esa sintaxis.
    private fun stripComments(source: String): String {
        val result = StringBuilder()
        var i = 0
        while (i < source.length) {
            when {
                source.startsWith("//", i) -> {
                    val end = source.indexOf('\n', i)
                    i = if (end == -1) source.length else end
                }
                source.startsWith("/*", i) -> {
                    val end = source.indexOf("*/", i + 2)
                    i = if (end == -1) source.length else end + 2
                }
                else -> {
                    result.append(source[i])
                    i++
                }
            }
        }
        return result.toString()
    }

    private fun extractPrintableStrings(bytes: ByteArray, minLength: Int = 4): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        for (byte in bytes) {
            val c = (byte.toInt() and 0xFF).toChar()
            if (c.code in 0x20..0x7E) {
                current.append(c)
            } else {
                if (current.length >= minLength) result.add(current.toString())
                current.clear()
            }
        }
        if (current.length >= minLength) result.add(current.toString())
        return result
    }
}

val coreVersionGuardedSources = files(
    fileTree(layout.projectDirectory.dir("../native/ds_core")) {
        include("**/*.cpp", "**/*.h")
    },
    fileTree(layout.projectDirectory.dir("../native/emulator_bridge")) {
        include("**/*.cpp", "**/*.h")
    },
    fileTree(layout.projectDirectory.dir("src/main/java")) {
        include("**/*.kt")
    },
)

// Las dos variantes compilan el nativo en RelWithDebInfo (ver
// CMAKE_BUILD_TYPE en defaultConfig), así que ambas buscan el objeto en
// .cxx/RelWithDebInfo. Apuntar la debug a .cxx/Debug sólo «funcionaba»
// mientras sobreviviera un .cxx/Debug de antes de forzar el tipo: se
// verificaba un objeto rancio, no el que entra en el APK.
listOf(
    Triple("Debug", "stripDebugDebugSymbols", "RelWithDebInfo"),
    Triple("Release", "stripReleaseDebugSymbols", "RelWithDebInfo"),
).forEach { (variant, stripTask, cxxConfig) ->
    val verifyTask = tasks.register<VerifyCoreVersionTask>("verifyCoreVersion$variant") {
        dependsOn(stripTask)
        cxxBuildDirectory.set(layout.projectDirectory.dir(".cxx/$cxxConfig"))
        guardedSources.from(coreVersionGuardedSources)
    }
    tasks.matching { it.name == "assemble$variant" }.configureEach {
        dependsOn(verifyTask)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
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
