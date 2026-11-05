# CLAUDE.md

Guía para Claude Code (claude.ai/code) al trabajar en este repositorio.

## Idioma del proyecto

Todo —documentación, comentarios de código, mensajes de commit y la
conversación con el usuario— está en **español**. La UI será bilingüe (español
por defecto en `res/values/`, inglés en `res/values-en/`), igual que en
`app-emulador`. Mantén esa convención.

## Qué es

Emulador de **Nintendo DS** para Android (Kotlin + Jetpack Compose) con un core
nativo compilado desde fuentes con el NDK y conectado por JNI. Hereda la
arquitectura, el sistema visual y el sistema de partidas de
[`app-emulador`](../app-emulador) (el emulador de GBA/GBC del mismo autor), del
que es proyecto hermano y no continuación: son dos repositorios separados con
**licencias distintas** (ver más abajo).

**No incluye ni descarga ROMs, BIOS ni firmware.** Todo eso lo aporta el usuario.

**Nintendo 3DS queda fuera de este repositorio.** No es «lo mismo pero más
grande»: exige un pipeline gráfico completo (PICA200 → GLES 3.2 / Vulkan con
recompilación de shaders), JIT de ARM11, claves de descifrado que el usuario
extrae de su consola, y es el terreno con más presión legal que existe hoy en
emulación. Si algún día se hace, se hace aparte.

Paquete: `com.moralesDev.dsForEveryone`. Nombre de la app: **DsForEveryone**.

Estado: **las cinco fases del plan están cerradas** y el emulador funciona con
melonDS 1.1 (JIT activado, renderizador 3D por software en hilo aparte). Sobre
eso ya se ha añadido funcionalidad: trucos Action Replay, mandos Bluetooth/USB
con mapeo persistente, extraer/importar/borrar el `.sav`, avance rápido
(1x / 1,5x / 2x), pausa al dejar de verse la app, cerrar la tapa y respeto de
las barras del sistema. Lo pendiente está en la sección «Después» de
[`docs/plan-de-trabajo.md`](docs/plan-de-trabajo.md); lo que se rompió y se
midió al meter el core real, en
[`docs/estabilizacion-core.md`](docs/estabilizacion-core.md).

## Licencia: esto condiciona todo lo demás

Este proyecto es **software libre bajo GPL**. No es una preferencia: es una
consecuencia obligada. Todos los cores de DS integrables son copyleft de
programa completo (melonDS es GPL-3.0, DeSmuME es GPL-2.0), a diferencia de
mGBA, que es MPL-2.0 —copyleft *por archivo*— y por eso permite que
`app-emulador` sea cerrado.

Enlazar cualquiera de esos cores dentro del APK convierte al APK entero en obra
derivada. No hay separación por carpetas que valga, como sí la hay en
`app-emulador` entre `third_party/mgba/` y `native/emulator_bridge/`.

Consecuencias prácticas que hay que tener presentes al escribir código:

- Todo el Kotlin de este repositorio se publica. Escríbelo sabiéndolo.
- **Nunca portes código de este repositorio a `app-emulador`** sin comprobar
  antes quién lo escribió. Del autor hacia aquí se puede (es su código, puede
  licenciarlo dos veces); de aquí hacia allá sólo si sigue siendo suyo. Una
  contribución externa aceptada aquí queda atada a la GPL para siempre.
- La versión de la GPL depende del core que se elija y **no es indiferente**.
  Detalle completo en [`docs/licencia.md`](docs/licencia.md).

## Comandos

El entorno actual es **macOS / zsh**. `gradlew` está versionado sin bit de
ejecución (el repositorio nació en Windows), así que se invoca con `bash`:

```sh
bash gradlew assembleDebug         # APK (compila el core + puente + app)
bash gradlew testDebugUnitTest     # pruebas de JVM
bash gradlew installDebug          # instalar en el dispositivo conectado
bash gradlew lint                  # lint de AGP
```

En Windows / PowerShell son los mismos con `.\gradlew.bat`.

Requisitos para compilar en una copia nueva:

- `local.properties` con `sdk.dir=…` (no se versiona).
- melonDS **1.1** en `third_party/melonDS/`, como tarball de la etiqueta
  (`https://github.com/melonDS-emu/melonDS/archive/refs/tags/1.1.tar.gz`),
  nunca como `git clone`: no debe quedar un `.git` anidado. `third_party/*`
  está excluido de git.
- NDK `28.2.13676358` y CMake `3.22.1`, fijados en `app/build.gradle.kts`.

## Las fronteras del proyecto

`app-emulador` tiene **una** frontera, `EmulatorCore`, y le salió bien. Aquí hay
**tres**, porque la DS añade dos ejes que la GBA no tiene:

1. **`EmulatorCore`** — Kotlin arriba, C/C++ abajo. Nadie de arriba sabe qué hay
   debajo. Se diseña para admitir melonDS **y** DeSmuME sin que el resto se
   entere; la elección se documenta en
   [`docs/integracion-core.md`](docs/integracion-core.md) y se toma midiendo.
2. **`ScreenLayout`** — dónde van las dos pantallas. Geometría pura, sin
   Compose, comprobable coordenada a coordenada.
3. **`TouchRouter`** — de dónde toca el dedo a coordenada de la pantalla
   inferior. También geometría pura.

Las dos últimas son geometría deliberadamente **fuera de Compose**, por el mismo
motivo que `DirectionalPadZones` en `app-emulador`: se prueban en JVM sin
Robolectric.

## Reglas invariantes heredadas de `app-emulador`

Estas se importan tal cual y romperlas produce los mismos fallos allí que aquí:

- **Un hilo propio, no una corrutina.** `Thread` con
  `THREAD_PRIORITY_URGENT_DISPLAY` y ritmo con `LockSupport.parkNanos` sobre
  reloj absoluto. **El período cambia**: la DS corre a 59,8261 Hz
  (33 513 982 Hz ÷ 560 190 ciclos) → **16 715 113 ns**, no los 16 742 706 ns de
  la GBA.
- **El core se toca desde un solo hilo.** Guardar/cargar estado y capturar
  miniatura se encolan como comandos que ejecuta el hilo de emulación entre
  fotogramas; el resultado vuelve por `CompletableDeferred`. Sin cerrojos
  alrededor del core.
- **`attachSurface` bloquea** hasta que el core deja de escribir en la
  superficie anterior.
- **`loadState` serializa el estado actual antes de restaurar** y lo vuelve a
  meter si el core rechaza el archivo.
- **La identidad de una ROM es el SHA-256 de su contenido**, nunca el nombre.
  `saves/<sha256>/` es la raíz del aislamiento entre juegos.
- **Toda escritura de partida es atómica**: temporal → `fsync` → `rename`.
- **El respaldo automático es la ranura 0** y su garantía es la forma de los
  métodos, no un `if`.
- **Sin `Dialog`, `Popup` ni `DropdownMenu`**: abren su propia raíz de
  composición, adonde no llega el idioma elegido en la app.
- **Los ViewModel nunca construyen texto traducido**: guardan un `UiMessage`.
- **Los mandos no son `clickable`**: `pressable` con `awaitEachGesture`
  siguiendo cada puntero por su id, con `finally` que suelta el botón.
- **No hay ajustes decorativos.** Todo interruptor cambia algo real.

## Reglas invariantes nuevas de la DS

- **El vídeo se presenta con GL ES, no con `setFixedSize`.** El truco de
  `app-emulador` —el tamaño del búfer *es* el filtro— no sobrevive a dos
  pantallas colocables por separado. Aquí el búfer del core (256×384) es una
  textura y se pintan dos quads. `GL_NEAREST` para «Nítido», `GL_LINEAR` para
  «Suavizado»: el filtro vuelve a ser un parámetro, ya no una geometría.
  Motivo completo en [`docs/pantallas.md`](docs/pantallas.md).
- **La entrada táctil es estado compartido, no un comando encolado.** La DS lee
  la pantalla táctil una vez por fotograma. La UI escribe la última posición en
  `InputManager` y el hilo de emulación la lee al principio de cada fotograma,
  igual que la máscara de botones. Encolar un comando por evento táctil
  produciría cientos de comandos por segundo y arrastres a saltos: mientras el
  dedo esté abajo hay que reportar posición **cada fotograma**, no sólo cuando
  se mueve.
- **La pantalla táctil es un puntero más, no un caso especial.** Un dedo en la
  cruceta y otro en la pantalla de abajo tienen que convivir. Eso lo da
  `awaitEachGesture` siguiendo el id del puntero; no añadas lógica de
  exclusividad.
- **El editor de disposición de controles no es opcional.** Con doce entradas
  (cruceta, A B X Y, L, R, Start, Select) y dos pantallas comiéndose el área, no
  hay una colocación fija que sirva para todos los juegos y las dos
  orientaciones. Ver [`docs/entrada.md`](docs/entrada.md).
- **Sin permisos, también aquí.** El micrófono de la DS se emula con un botón de
  soplido que inyecta ruido. Declarar `RECORD_AUDIO` es la última opción y
  requiere decisión expresa.

## Permisos

Igual que en `app-emulador`: el manifiesto **no declara ni un permiso**. ROMs y
archivos de sistema por SAF, todo lo demás en `filesDir`. Sin red. Si algo
parece necesitar un permiso, es señal de que el enfoque está mal — la única
excepción con discusión abierta es el micrófono, y la respuesta por defecto es
no.

## Pruebas

Sólo pruebas de JVM (`app/src/test/`), con JUnit 4, `kotlinx-coroutines-test` y
Turbine. Un `FakeEmulatorCore` para ejercitar controlador y mandos. Los
sistemas de archivos se prueban sobre carpetas temporales de verdad, no sobre
dobles.

Lo específico de este proyecto que **tiene que** estar cubierto en JVM:

- `ScreenLayout`: que las dos pantallas nunca se solapen, que no se salgan del
  hueco, que mantengan 4:3 cada una salvo en «Rellenar», y que cada disposición
  dé el mismo resultado para la misma entrada.
- `TouchRouter`: que un punto fuera de la pantalla inferior no produzca un
  toque, que las esquinas den exactamente 0 y 255 / 0 y 191, y que el recorte
  ocurra en el borde y no un píxel después.
- El contenedor de save states, con archivos **estropeados a mano**: lo que
  importa no es escribir bien, sino negarse a leer mal.

Las pruebas en dispositivo se hacen siempre con ROMs libres, nunca con las del
usuario, y se retiran de la biblioteca al terminar.

## Orden de trabajo

El proyecto de GBA se construyó entero contra `StubEmulatorCore` antes de que
existiera mGBA, y meter mGBA fue escribir el puente y cambiar una línea. Aquí
esa jugada rinde **más**, porque lo difícil no es el core —ese ya está escrito
por otros— sino la geometría de dos pantallas, la ruta táctil y doce botones en
menos sitio.

| Fase | Qué cierra | Rama |
|---|---|---|
| 0 | Licencia, README, estructura del repositorio | `feature/fase-0-cimientos` |
| 1 | Esqueleto Gradle, Nocturne, navegación, almacenamiento | `feature/fase-1-esqueleto` |
| 2 | **La geometría**: dos pantallas, táctil, doce botones, editor — contra el stub | `feature/fase-2-geometria` |
| 3 | Biblioteca, cabecera y banner de la ROM, sistema de partidas | `feature/fase-3-biblioteca-partidas` |
| 4 | El core nativo, el puente JNI, archivos de sistema y la medición | `feature/fase-4-core-nativo` |

Las cinco fases ya están fusionadas en `develop`. Desde entonces cada
funcionalidad va en su propia rama `moralesDev-<tema>` (p. ej.
`moralesDev-trucos`, `moralesDev-barras-sistema`), con la misma regla.

**Una rama por fase (o por funcionalidad), un commit por entregable.** Claude
Code sólo hace commits dentro de esa rama; el *merge* a `develop` lo hace el usuario a mano
al terminar cada fase. `develop` es el tronco real; `master` significa
únicamente «lo que está publicado en la tienda ahora mismo».

El desglose entregable a entregable, con el prompt de cada uno y su «está hecho
cuando», está en [`docs/plan-de-trabajo.md`](docs/plan-de-trabajo.md).

## Documentación

Ampliar aquí antes de escribir código nuevo en esas áreas:

- [`docs/licencia.md`](docs/licencia.md) — qué obliga la GPL, qué permite, y las trampas al compartir código con `app-emulador`.
- [`docs/arquitectura.md`](docs/arquitectura.md) — estructura de paquetes y el porqué de cada decisión.
- [`docs/integracion-core.md`](docs/integracion-core.md) — melonDS frente a DeSmuME, la correspondencia con `EmulatorCore` y el plan nativo.
- [`docs/pantallas.md`](docs/pantallas.md) — las dos pantallas, el renderizador GL y las disposiciones.
- [`docs/entrada.md`](docs/entrada.md) — lápiz con el dedo, los doce botones, el editor de disposición y el micrófono.
- [`docs/reutilizacion.md`](docs/reutilizacion.md) — qué se trae de `app-emulador`, qué se adapta y qué es nuevo.
- [`docs/plan-de-trabajo.md`](docs/plan-de-trabajo.md) — las cinco fases, los diecinueve entregables y el prompt de cada uno.
