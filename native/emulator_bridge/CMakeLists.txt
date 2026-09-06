# Puente nativo entre la aplicación Android y el core de emulación.
#
# Entregable 4.1: que el core compile como biblioteca estática, nada más. El
# puente propio (extern "C", JNI) llega en el 4.2, sobre esta misma base, así
# que este archivo hoy no declara ninguna biblioteca suya.
#
# melonDS vive intacto en third_party/melonDS y se consume como subdirectorio,
# sin mezclar fuentes de terceros con las nuestras.
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
# - ENABLE_JIT=OFF por ahora: activa un recompilador ARM64 con ensamblador
#   propio (`ARMJIT_A64/*.S`) que no hace falta para que el core compile, y
#   que conviene medir antes de arriesgar (fase 4.5) en vez de darlo por
#   bueno en este entregable.
# - ENABLE_GDBSTUB=OFF, porque abre un socket de depuración remota y esta
#   aplicación no declara ningún permiso de red.
# ---------------------------------------------------------------------------

add_subdirectory(
    ${CMAKE_CURRENT_SOURCE_DIR}/../../third_party/melonDS
    ${CMAKE_CURRENT_BINARY_DIR}/melonDS
)
