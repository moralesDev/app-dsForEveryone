# DsForEveryone

Emulador de **Nintendo DS** para Android, escrito en Kotlin con Jetpack
Compose y un core nativo compilado con el NDK. Es el proyecto hermano de
`app-emulador` (el emulador de GBA/GBC del mismo autor), del que hereda la
arquitectura, el sistema visual y el sistema de partidas, pero **no** es una
continuación suya: son dos repositorios separados, con licencias distintas,
por el motivo que se explica más abajo.

Paquete: `com.moralesDev.dsForEveryone`.

Nintendo 3DS queda fuera de este repositorio. Si algún día se hace, se hace
aparte.

## Qué no es esta aplicación

**Esta aplicación no incluye ni descarga ROMs, BIOS ni firmware.** Todo eso lo
aporta el usuario desde su propio hardware. La app no lleva red: no hay forma
de descargar nada desde ella, ni de enlazar a un sitio donde conseguirlo.

## Licencia

Este proyecto es software libre bajo la **GNU General Public License,
versión 3** (véase [`LICENSE`](LICENSE)).

No es una preferencia estética: es una consecuencia obligada de la elección de
core. A diferencia de `app-emulador` —que puede ser cerrado porque mGBA es
MPL-2.0, copyleft *por archivo*—, los cores integrables de Nintendo DS
(melonDS, DeSmuME) son copyleft de **programa completo**. Enlazar cualquiera
de ellos dentro del mismo APK convierte al binario entero en obra derivada, y
eso obliga a que todo el código de esta aplicación se publique bajo la misma
licencia.

Se eligió la **GPL-3.0** —la más restrictiva de las dos candidatas— a
propósito y no por defecto: si el core que acabe integrándose es DeSmuME
(GPL-2.0-or-later) esta licencia podrá relajarse a GPL-2.0, porque ir de una
versión más restrictiva a una más permisiva es un cambio legítimo; al revés no
lo es. El detalle completo de esta decisión, incluida la comparación entre
GPL-2.0 y GPL-3.0 y sus implicaciones para la distribución en Google Play,
está en [`docs/licencia.md`](docs/licencia.md).

La licencia obliga a entregar el código fuente completo a quien reciba el
binario, incluso si la aplicación se vende. La GPL nunca ha prohibido cobrar
por el software.

### El nombre y el logotipo no se licencian con el código

La GPL cubre el código fuente. **No** cubre la marca: el nombre «DsForEveryone»
y su logotipo se reservan y no se conceden junto con los derechos de copia,
modificación y redistribución del código.

### Contribuciones externas

Las contribuciones externas son bienvenidas y se aceptan bajo los términos de
la GPL-3.0. Este proyecto **no pide cesión de derechos**: quien contribuye
conserva la autoría de su aportación, que queda ligada a la GPL para siempre
en este repositorio.

## Estado

Funcional sobre **melonDS 1.1**. Las cinco fases del plan
([`docs/plan-de-trabajo.md`](docs/plan-de-trabajo.md)) están cerradas, y
después se han añadido trucos Action Replay, mandos Bluetooth/USB, gestión del
`.sav`, avance rápido y cerrar la tapa. Lo pendiente está en la sección
«Después» del plan.

## Compilar

1. Crea `local.properties` con `sdk.dir=<ruta al SDK de Android>`.
2. Descomprime melonDS 1.1 en `third_party/melonDS/` desde el tarball de la
   etiqueta (`https://github.com/melonDS-emu/melonDS/archive/refs/tags/1.1.tar.gz`).
3. `./gradlew assembleDebug` (`.\gradlew.bat` en Windows). Gradle descarga el NDK y CMake
   fijados si faltan.
