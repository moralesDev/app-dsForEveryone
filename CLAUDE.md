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

Estado: **fase de documentación.** No hay código todavía.

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

Windows / PowerShell (el shell de este entorno). Cuando exista el proyecto
Gradle serán los mismos que en `app-emulador`:

```powershell
.\gradlew.bat assembleDebug        # APK (compila el core + puente + app)
.\gradlew.bat testDebugUnitTest    # pruebas de JVM
.\gradlew.bat installDebug         # instalar en el dispositivo conectado
.\gradlew.bat lint                 # lint de AGP
```

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

1. **Documentación** (fase actual).
2. **Esqueleto + stub**: splash, home, navegación, Nocturne, biblioteca,
   partidas, y un `StubDsCore` que pinte dos rectángulos animados. Con eso se
   cierra toda la geometría sin tocar el NDK.
3. **Integración del core**: puente JNI, elección medida entre melonDS y
   DeSmuME, gestión de archivos de sistema.
4. **Acabado**: portadas desde el icono de la ROM, trucos, mandos físicos.

## Documentación

Ampliar aquí antes de escribir código nuevo en esas áreas:

- [`docs/licencia.md`](docs/licencia.md) — qué obliga la GPL, qué permite, y las trampas al compartir código con `app-emulador`.
- [`docs/arquitectura.md`](docs/arquitectura.md) — estructura de paquetes y el porqué de cada decisión.
- [`docs/integracion-core.md`](docs/integracion-core.md) — melonDS frente a DeSmuME, la correspondencia con `EmulatorCore` y el plan nativo.
- [`docs/pantallas.md`](docs/pantallas.md) — las dos pantallas, el renderizador GL y las disposiciones.
- [`docs/entrada.md`](docs/entrada.md) — lápiz con el dedo, los doce botones, el editor de disposición y el micrófono.
- [`docs/reutilizacion.md`](docs/reutilizacion.md) — qué se trae de `app-emulador`, qué se adapta y qué es nuevo.
