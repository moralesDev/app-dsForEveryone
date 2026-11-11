# DsForEveryone para iOS y iPadOS (docs/ios.md, iOS-3 paso 5).
#
# Genera el proyecto de Xcode de la app. La app en sí es el framework
# `Shared` de Kotlin Multiplatform (shared/); aquí sólo hay un main.m que lo
# arranca. Antes de compilar, Xcode le pide a Gradle el framework de la
# plataforma elegida. Instrucciones en ios/app/README.md.
cmake_minimum_required(VERSION 3.22)
project(DsForEveryone OBJC)

set(DEVELOPMENT_TEAM "" CACHE STRING "Team ID de Apple con el que firmar (Xcode → Settings → Accounts)")
# IosArm64 para un iPhone/iPad de verdad, IosSimulatorArm64 para el simulador.
set(KOTLIN_TARGET "IosArm64" CACHE STRING "Destino de Kotlin/Native: IosArm64 o IosSimulatorArm64")
# Gradle se lanza desde Xcode sin el perfil de la terminal: hay que decirle
# qué Java usar. Por defecto, el que responda /usr/libexec/java_home.
execute_process(COMMAND /usr/libexec/java_home OUTPUT_VARIABLE DEFAULT_JAVA_HOME OUTPUT_STRIP_TRAILING_WHITESPACE ERROR_QUIET)
set(JAVA_HOME "${DEFAULT_JAVA_HOME}" CACHE PATH "Java con el que correr Gradle")

set(REPO_ROOT ${CMAKE_CURRENT_SOURCE_DIR}/../..)
# Gradle nombra las carpetas y tareas con la primera letra en minúscula: iosArm64.
string(REGEX REPLACE "^Ios" "ios" KOTLIN_TARGET_DIR "${KOTLIN_TARGET}")
set(FRAMEWORK_DIR ${REPO_ROOT}/shared/build/bin/${KOTLIN_TARGET_DIR}/debugFramework)
# Los recursos de Compose (textos, fuente): Compose Multiplatform los busca en
# DsForEveryone.app/compose-resources/. Sin ellos la app falla al arrancar,
# en el primer texto o en la fuente.
set(COMPOSE_RESOURCES_DIR ${REPO_ROOT}/shared/build/processedResources/${KOTLIN_TARGET_DIR}/main)

add_custom_target(SharedFramework
    COMMAND ${CMAKE_COMMAND} -E env JAVA_HOME=${JAVA_HOME}
        bash ${REPO_ROOT}/gradlew -p ${REPO_ROOT} -q
            :shared:linkDebugFramework${KOTLIN_TARGET}
            :shared:${KOTLIN_TARGET_DIR}ProcessResources
    WORKING_DIRECTORY ${REPO_ROOT}
    COMMENT "Compilando el framework Shared y sus recursos (${KOTLIN_TARGET}) con Gradle"
    VERBATIM
)

# El icono de la app (Assets.xcassets/AppIcon, generado desde art/logo.png
# con tools/make-icons.swift). Xcode lo compila con actool.
add_executable(DsForEveryone MACOSX_BUNDLE main.m Assets.xcassets)
set_source_files_properties(Assets.xcassets PROPERTIES MACOSX_PACKAGE_LOCATION Resources)
add_dependencies(DsForEveryone SharedFramework)
add_custom_command(TARGET DsForEveryone POST_BUILD
    COMMAND /bin/sh ${CMAKE_CURRENT_SOURCE_DIR}/copy-into-app.sh ${FRAMEWORK_DIR}/Shared.framework ${COMPOSE_RESOURCES_DIR}
    COMMENT "Copiando el framework Shared y los recursos de Compose a la app"
    VERBATIM
)
target_compile_options(DsForEveryone PRIVATE -fobjc-arc -F${FRAMEWORK_DIR})
target_link_options(DsForEveryone PRIVATE -F${FRAMEWORK_DIR} -framework Shared)
target_link_libraries(DsForEveryone PRIVATE "-framework UIKit" "-framework Foundation")

set_target_properties(DsForEveryone PROPERTIES
    MACOSX_BUNDLE_INFO_PLIST ${CMAKE_CURRENT_SOURCE_DIR}/Info.plist.in
    MACOSX_BUNDLE_GUI_IDENTIFIER com.moralesDev.dsForEveryone
    XCODE_ATTRIBUTE_PRODUCT_BUNDLE_IDENTIFIER com.moralesDev.dsForEveryone
    XCODE_ATTRIBUTE_CODE_SIGN_STYLE Automatic
    XCODE_ATTRIBUTE_DEVELOPMENT_TEAM "${DEVELOPMENT_TEAM}"
    XCODE_ATTRIBUTE_TARGETED_DEVICE_FAMILY "1,2"
    XCODE_ATTRIBUTE_IPHONEOS_DEPLOYMENT_TARGET 16.0
    XCODE_ATTRIBUTE_CLANG_ENABLE_OBJC_ARC YES
    XCODE_ATTRIBUTE_ASSETCATALOG_COMPILER_APPICON_NAME AppIcon
    XCODE_ATTRIBUTE_LD_RUNPATH_SEARCH_PATHS "@executable_path/Frameworks"
    # El framework de Kotlin lo mete y lo firma copy-into-app.sh, no la fase
    # «Embed Frameworks» de Xcode (ver el script).
)
