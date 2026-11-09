# ds_core: melonDS detrás de la API en C de ds_core.h, sin nada de ninguna
# plataforma. Lo consumen el puente JNI de Android
# (native/emulator_bridge/) y la app de iOS (ios/), cada uno con
# `add_subdirectory` sobre esta carpeta. Ver docs/ios.md, fase iOS-1.
#
# melonDS 1.1 — GNU General Public License v3.0. Ver docs/integracion-core.md.
#
# Las banderas que deciden qué parte de melonDS se compila (BUILD_QT_SDL,
# ENABLE_JIT, ENABLE_OGLRENDERER, ENABLE_GDBSTUB) las pone quien consume esta
# carpeta: en Android, app/build.gradle.kts; en iOS, su propio CMakeLists. La
# diferencia que importa es ENABLE_JIT: encendido en Android, apagado en iOS
# porque Apple no deja ejecutar código generado en tiempo de ejecución.

cmake_minimum_required(VERSION 3.22.1)
project(ds_core CXX)

set(MELONDS_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../third_party/melonDS)

# melonDS vive intacto en third_party/melonDS y se consume como
# subdirectorio, sin mezclar fuentes de terceros con las nuestras. Produce
# una biblioteca estática llamada `core`.
if(NOT TARGET core)
    add_subdirectory(${MELONDS_DIR} ${CMAKE_CURRENT_BINARY_DIR}/melonDS)
endif()

add_library(ds_core STATIC ds_core.cpp)

target_compile_features(ds_core PUBLIC cxx_std_17)

# PUBLIC para ds_core.h; PRIVATE para las cabeceras de melonDS, que no cruzan
# la frontera (sólo ds_core.cpp las incluye).
target_include_directories(ds_core
    PUBLIC ${CMAKE_CURRENT_SOURCE_DIR}
    PRIVATE ${MELONDS_DIR}/src
)

target_link_libraries(ds_core PUBLIC core)

if(ANDROID)
    target_link_libraries(ds_core PUBLIC log)
endif()
