#!/bin/sh
# Mete en la app lo que viene de Gradle: el framework Shared (todo el código
# de Kotlin) y los recursos de Compose (textos, fuente, imágenes).
#
# Lo llama Xcode después de enlazar (ver CMakeLists.txt), con las variables
# que pone en el entorno de sus scripts: la carpeta exacta de la app depende
# del destino, y CMake no sabe expandirlas.
#
# Se copia siempre, entero. No se usa la fase «Embed Frameworks» de Xcode
# porque decide si recopiar mirando la fecha de la carpeta Shared.framework,
# que no cambia cuando Gradle reescribe el binario de dentro: la app se
# quedaba con el código de una compilación anterior sin avisar.
#
# $1: Shared.framework de shared/build/bin/<destino>/debugFramework.
# $2: shared/build/processedResources/<destino>/main.
set -eu
app="${TARGET_BUILD_DIR}/${CONTENTS_FOLDER_PATH}"

frameworks="${TARGET_BUILD_DIR}/${FRAMEWORKS_FOLDER_PATH}"
mkdir -p "$frameworks"
rm -rf "$frameworks/Shared.framework"
cp -R "$1" "$frameworks/"
# Sin cabeceras ni módulos dentro de la app: sólo hacen falta para compilar.
rm -rf "$frameworks/Shared.framework/Headers" "$frameworks/Shared.framework/Modules"
# Firmado antes de que Xcode firme la app, que lo sella dentro. En el
# simulador sin firma (CODE_SIGNING_ALLOWED=NO) no hay identidad y no hace falta.
if [ "${CODE_SIGNING_ALLOWED:-YES}" = "YES" ] && [ -n "${EXPANDED_CODE_SIGN_IDENTITY:-}" ]; then
    codesign --force --sign "$EXPANDED_CODE_SIGN_IDENTITY" --timestamp=none \
        --preserve-metadata=identifier,entitlements,flags "$frameworks/Shared.framework"
fi

resources="$app/compose-resources"
rm -rf "$resources"
mkdir -p "$resources"
cp -R "$2/" "$resources/"
