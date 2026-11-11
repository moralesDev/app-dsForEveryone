# Lo nativo de iOS: ds_core (con melonDS dentro, compartido con Android) más
# la salida de audio de iOS. Lo compila Gradle (tareas buildDsCore* de
# shared/build.gradle.kts) y lo enlaza en el framework Shared por cinterop.
# Ver docs/ios.md, fase iOS-4.
cmake_minimum_required(VERSION 3.22)
project(ds_ios C CXX)

add_subdirectory(${CMAKE_CURRENT_SOURCE_DIR}/../ds_core ${CMAKE_CURRENT_BINARY_DIR}/ds_core)

# La salida de audio, en C y no en Kotlin: el callback de AudioUnit corre en
# un hilo de tiempo real, donde no conviene nada que pueda pararse (ni el
# recolector de basura). Es el equivalente de audio_aaudio.cpp en Android.
add_library(ds_audio_ios STATIC ds_audio_ios.c)
target_link_libraries(ds_audio_ios PUBLIC ds_core)
