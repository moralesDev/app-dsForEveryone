#!/bin/sh
# Copia los recursos de Compose Multiplatform (textos, fuente) dentro de la
# app, donde los busca: DsForEveryone.app/compose-resources/.
#
# Lo llama Xcode después de enlazar (ver CMakeLists.txt). Usa las variables
# que Xcode pone en el entorno de sus scripts —la carpeta exacta de la app
# depende del destino, simulador o dispositivo—, que CMake no sabe expandir.
#
# $1: la carpeta processedResources/<destino>/main de :shared.
set -eu
destination="${TARGET_BUILD_DIR}/${CONTENTS_FOLDER_PATH}/compose-resources"
rm -rf "$destination"
mkdir -p "$destination"
cp -R "$1/" "$destination/"
