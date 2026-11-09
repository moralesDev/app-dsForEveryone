# DsForEveryone para iOS y iPadOS

La app de iOS. Todo su código está en el módulo común `shared/` (Kotlin
Multiplatform + Compose Multiplatform); aquí sólo hay el arranque (`main.m`)
y el proyecto, que se genera con CMake. Ver [`docs/ios.md`](../../docs/ios.md).

Estado: arranca y enseña la bienvenida, Ajustes, Acerca de y el editor de
disposición. La biblioteca, el juego y el mapeo de mandos enseñan un aviso de
«todavía no»; los selectores de archivos aún no hacen nada.

## En el simulador

```sh
cmake -S ios/app -B build-ios/app-sim -G Xcode \
  -DCMAKE_SYSTEM_NAME=iOS -DCMAKE_OSX_SYSROOT=iphonesimulator \
  -DCMAKE_OSX_ARCHITECTURES=arm64 -DCMAKE_OSX_DEPLOYMENT_TARGET=16.0 \
  -DKOTLIN_TARGET=IosSimulatorArm64
open build-ios/app-sim/DsForEveryone.xcodeproj
```

En Xcode: esquema **DsForEveryone**, un simulador de iPad o iPhone, **⌘R**.

## En un iPhone o iPad

```sh
cmake -S ios/app -B build-ios/app -G Xcode \
  -DCMAKE_SYSTEM_NAME=iOS -DCMAKE_OSX_ARCHITECTURES=arm64 \
  -DCMAKE_OSX_DEPLOYMENT_TARGET=16.0 -DKOTLIN_TARGET=IosArm64 \
  -DDEVELOPMENT_TEAM=<tu Team ID>
open build-ios/app/DsForEveryone.xcodeproj
```

Igual que con `ios/spike/`: la primera vez, en el dispositivo, **Ajustes →
General → VPN y gestión de dispositivos → Confiar**.

## Qué hace el proyecto

- Antes de compilar, Xcode llama a Gradle para el framework `Shared` del
  destino elegido (`:shared:linkDebugFramework<Destino>`) y sus recursos
  (`:shared:<destino>ProcessResources`). La primera vez tarda varios minutos.
- `copy-into-app.sh` mete en la app, después de enlazar, el framework
  (firmado) y los recursos de Compose (textos, fuente, imágenes) en
  `DsForEveryone.app/compose-resources/`. Sin los recursos la app falla al
  arrancar, en la primera letra. El framework no lo copia la fase «Embed
  Frameworks» de Xcode: decide por la fecha de la carpeta, que no cambia al
  recompilar, y la app se quedaba con código viejo sin avisar.
- Gradle necesita Java, y Xcode no carga el perfil de la terminal: el
  proyecto usa el que diga `/usr/libexec/java_home`, o el que se le pase con
  `-DJAVA_HOME=…`.
