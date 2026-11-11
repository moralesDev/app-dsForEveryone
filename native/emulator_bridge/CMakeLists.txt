# Puente nativo entre la aplicación Android y el core de emulación.
#
# Aquí sólo queda lo propio de Android (JNI y AAudio). Lo que toca a melonDS
# está en ../ds_core, compartido con iOS; melonDS vive intacto en
# third_party/melonDS y se consume como subdirectorio, sin mezclar fuentes
# de terceros con las nuestras.
#
# melonDS 1.1 — GNU General Public License v3.0. Ver docs/integracion-core.md.

cmake_minimum_required(VERSION 3.22.1)
project(ds_emulator_bridge CXX)

# ---------------------------------------------------------------------------
# melonDS
#
# Las cuatro banderas que deciden qué parte de melonDS se compila viven en
# `app/build.gradle.kts` (`externalNativeBuild.cmake.arguments`), no aquí:
# así quedan junto a la versión del NDK con la que se comprobaron, en el mismo
# archivo. En resumen:
#
# - BUILD_QT_SDL=OFF apaga el frontend de escritorio entero (Qt, SDL, la
#   interfaz gráfica). Sin él, `add_subdirectory(src)` es lo único que corre
#   dentro del CMakeLists.txt de melonDS, y produce una única biblioteca
#   estática llamada `core`. Con él encendido (su valor por defecto)
#   `add_subdirectory` intentaría enlazar Qt y SDL2, que no existen para
#   Android.
# - ENABLE_OGLRENDERER=OFF, porque el renderizador 3D por OpenGL de melonDS
#   incluye `OpenGLSupport.cpp`, que espera un `PlatformOGL.h` aportado por
#   quien lo empotra. Ese archivo no existe todavía —lo escribe el puente de
#   la fase 4.2—, así que con el renderizador encendido ni siquiera compila.
# - ENABLE_JIT=ON: activa el recompilador ARM64 con ensamblador propio
#   (`ARMJIT_A64/*.S`). Se dejó apagado hasta la fase 4.5 para cerrar el core
#   nativo con el intérprete puro primero; medido el coste real (rendimiento
#   horrible en juegos comerciales, ver docs/estabilizacion-core.md), se
#   activa aquí. El puente rellena `NDSArgs::JIT` con un `JITArgs` real en
#   `MakeArgs()` (ds_bridge.cpp): sin eso el JIT quedaría enlazado pero
#   desactivado en tiempo de ejecución.
# - ENABLE_GDBSTUB=OFF, porque abre un socket de depuración remota y esta
#   aplicación no declara ningún permiso de red.
# ---------------------------------------------------------------------------

# ---------------------------------------------------------------------------
# ds_core: melonDS más todo lo que no es de Android, compartido con iOS
# (ver ../ds_core/CMakeLists.txt y docs/ios.md, fase iOS-1). Trae consigo el
# subdirectorio de melonDS y su biblioteca `core`.
# ---------------------------------------------------------------------------
add_subdirectory(
    ${CMAKE_CURRENT_SOURCE_DIR}/../ds_core
    ${CMAKE_CURRENT_BINARY_DIR}/ds_core
)

# ---------------------------------------------------------------------------
# Puente propio de Android
#
# Sólo JNI (ds_bridge.cpp) y AAudio (audio_aaudio.cpp) sobre ds_core, en
# una biblioteca dinámica de verdad: AGP encuentra sola qué construir (ver
# docs/integracion-core.md, sección 6).
# ---------------------------------------------------------------------------
add_library(dsemu SHARED
    ds_bridge.cpp
    audio_aaudio.cpp
)

target_compile_features(dsemu PRIVATE cxx_std_17)

target_link_libraries(dsemu
    ds_core
    aaudio
    android
    log
)
