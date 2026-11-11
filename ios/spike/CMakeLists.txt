# DsSpike: la prueba de rendimiento de la fase iOS-0 (docs/ios.md).
#
# Genera un proyecto de Xcode con la app y melonDS (sin JIT: iOS no lo
# permite). Ver ios/spike/README.md para compilar e instalar en el iPad.
cmake_minimum_required(VERSION 3.22)
project(DsSpike C CXX OBJCXX)

# Sólo Release: el intérprete es muy sensible a la optimización, y medir una
# compilación Debug no diría nada útil.
set(CMAKE_CONFIGURATION_TYPES Release CACHE STRING "" FORCE)
set(CMAKE_BUILD_TYPE Release)

set(BUILD_QT_SDL OFF CACHE BOOL "" FORCE)
set(ENABLE_JIT OFF CACHE BOOL "" FORCE)
set(ENABLE_OGLRENDERER OFF CACHE BOOL "" FORCE)
set(ENABLE_GDBSTUB OFF CACHE BOOL "" FORCE)
set(ENABLE_LTO_RELEASE OFF CACHE BOOL "" FORCE)

# ds_core: la misma frontera con melonDS que usa Android (fase iOS-1). Trae
# consigo melonDS y su biblioteca `core`.
add_subdirectory(${CMAKE_CURRENT_SOURCE_DIR}/../../native/ds_core ${CMAKE_CURRENT_BINARY_DIR}/ds_core)

set(DEVELOPMENT_TEAM "" CACHE STRING "Team ID de Apple con el que firmar (Xcode → Settings → Accounts)")

add_executable(DsSpike MACOSX_BUNDLE main.mm)
target_compile_features(DsSpike PRIVATE cxx_std_17)
target_compile_options(DsSpike PRIVATE -fobjc-arc)
target_link_libraries(DsSpike PRIVATE
    ds_core
    "-framework UIKit"
    "-framework Foundation"
    "-framework CoreGraphics"
    "-framework QuartzCore"
    "-framework UniformTypeIdentifiers"
)

set_target_properties(DsSpike PROPERTIES
    MACOSX_BUNDLE_INFO_PLIST ${CMAKE_CURRENT_SOURCE_DIR}/Info.plist.in
    MACOSX_BUNDLE_GUI_IDENTIFIER com.moralesDev.dsForEveryone.spike
    MACOSX_BUNDLE_BUNDLE_NAME DsSpike
    XCODE_ATTRIBUTE_PRODUCT_BUNDLE_IDENTIFIER com.moralesDev.dsForEveryone.spike
    XCODE_ATTRIBUTE_CODE_SIGN_STYLE Automatic
    XCODE_ATTRIBUTE_DEVELOPMENT_TEAM "${DEVELOPMENT_TEAM}"
    XCODE_ATTRIBUTE_TARGETED_DEVICE_FAMILY "1,2"
    XCODE_ATTRIBUTE_IPHONEOS_DEPLOYMENT_TARGET 16.0
    XCODE_ATTRIBUTE_CLANG_ENABLE_OBJC_ARC YES
)
