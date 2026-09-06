# Elección e integración del core

Este documento recoge qué core usar, por qué, y cómo enchufarlo. La decisión
se dejó deliberadamente abierta durante toda la fase 4 —la frontera
`EmulatorCore` se diseñó para admitir los dos candidatos— y se tomó midiendo
en dispositivo, no leyendo tablas, el mismo método que llevó a mGBA en
`app-emulador`. **Está cerrada** desde el entregable 4.5, a favor de melonDS:
ver la sección 10, al final de este documento, para el razonamiento completo
y los números.

## 1. Qué core

No se escribe un emulador desde cero. Se integra uno existente y mantenido.

| Core | Lenguaje | Licencia | Precisión | Archivos de sistema | Android |
|---|---|---|---|---|---|
| **melonDS** | C++ | GPL-3.0 | Muy alta, la referencia actual | **Exige volcados** de BIOS y firmware | Puerto existente (`rafaelvcaetano/melonDS-android`), GPL-3.0 |
| **DeSmuME** | C++ | GPL-2.0 | Media | **Ninguno** (BIOS por HLE) | Portado históricamente, sin puerto vivo de referencia |
| no$gba | — | Cerrada | Alta | Exige volcados | No integrable |

La licencia, que en `app-emulador` fue el criterio de más peso, **aquí no
desempata**: los dos son GPL y los dos obligan a abrir el proyecto (ver
[`licencia.md`](licencia.md)). Sí desempata en un matiz secundario: GPL-2.0
convive con Google Play con menos fricción que GPL-3.0.

Lo que sí desempata es el par **precisión / fricción de arranque**, y van en
direcciones opuestas:

**melonDS** es hoy el core de DS más preciso y el único con desarrollo activo
serio. Cubre bien la Wi-Fi local, el reloj, los juegos con protecciones raras y
el modo DSi. El coste es que **no trae BIOS por HLE**: necesita `bios7.bin`
(16 KB), `bios9.bin` (4 KB) y `firmware.bin` (128/256/512 KB) volcados de una DS
real. Para arrancar juegos directamente basta con las dos BIOS; el firmware sólo
hace falta para pasar por el menú de la consola.

**DeSmuME** arranca con la ROM y nada más, porque implementa las llamadas de
BIOS por software. Para el usuario que sólo quiere jugar, eso es la diferencia
entre abrir la app y funcionar, o abrir la app y encontrarse un tutorial de cómo
volcar la BIOS de una consola que quizá ya no tiene. El coste es precisión menor
y peor rendimiento en ARM.

### El criterio de decisión

**La primera pantalla de la app decide el producto.** Un emulador que pide tres
archivos que el usuario no tiene es un emulador que no se usa. Pero un emulador
que se cuelga en el juego que el usuario quería jugar tampoco.

Por eso la decisión no se toma ahora:

1. La frontera `EmulatorCore` se escribe agnóstica y se implementan **los dos**
   puentes, o al menos se deja el segundo trivial de añadir.
2. Se mide en el Motorola Edge 30 con ROMs libres: fotogramas por segundo,
   porcentaje de núcleo, comportamiento térmico tras varios minutos. El mismo
   protocolo que se usó para medir el ensayo de velocidad ×2 en `app-emulador`.
3. Se elige con los números delante.

Si melonDS gana en calidad —lo esperable— la fricción de la BIOS se mitiga con
producto, no con código: una pantalla de bienvenida que explique qué archivos
hacen falta, de dónde salen y que la app no los distribuye, con validación por
tamaño y hash y un mensaje claro por cada archivo que falte. Eso es trabajo de
interfaz, y de eso este proyecto va sobrado.

### Alternativa considerada: libretro

Usar un `melonds_libretro.so` precompilado evitaría compilar el core. Se
descarta por los mismos motivos que en `app-emulador`: igualmente hay que
escribir código nativo (la API de libretro es un contrato de callbacks que el
frontend implementa), añade una capa de indirección, y ata la aplicación a
binarios de terceros cuya ABI y versión no controlas. Añádase que la
presentación de dos pantallas por libretro llega ya compuesta en un único búfer
con la disposición decidida por el core, que es justo lo que aquí **no** se
quiere: la disposición es de la app.

## 2. Lo que cambia respecto a mGBA en la compilación

Tres diferencias que van a dar fricción y conviene anticipar:

- **Es C++, no C.** El puente ya no puede ser un `.c` limpio: hay que compilar
  con la STL del NDK (`c++_static`), y los símbolos JNI necesitan `extern "C"`.
- **La cadena de compilación es menos dócil.** mGBA está pensado para
  empotrarse (`mCore` es literalmente una API para frontends). melonDS y
  DeSmuME están pensados como aplicaciones de escritorio, y hay que desactivar
  interfaz, SDL, red y dependencias a mano. El puerto Android de melonDS ya
  resolvió gran parte de eso y su `CMakeLists` es la referencia a consultar.
- **La trampa de `version.cmake` puede repetirse.** En `app-emulador`, mGBA
  interrogaba a git en un target que se relanza como proceso nuevo, encontraba
  el `.git` de la app y estampaba la identidad equivocada en los save states.
  **Da por hecho que el core elegido hace algo parecido**, y aplica la misma
  corrección desde el principio: la versión del core que se graba en el
  contenedor de estados es una **constante del puente**
  (`BRIDGE_CORE_VERSION`), nunca un valor generado por el entorno de
  compilación. Ese error costó una investigación entera; no hay que pagarla dos
  veces.

## 3. Correspondencia con `EmulatorCore`

La interfaz hereda la de `app-emulador` y añade lo propio de la DS. Hay que
contrastar las firmas exactas con las cabeceras de la versión del core que se
acabe usando, porque cambian entre versiones mayores.

| `EmulatorCore` (Kotlin) | Lado nativo |
|---|---|
| `load(rom, batterySave)` | Cargar la ROM y la savedata desde memoria |
| `unload()` | Liberar la instancia |
| `runFrame()` | Un fotograma + volcado del framebuffer |
| `setKeys(mask)` | Máscara de botones del core |
| **`setTouch(x, y, down)`** | **Nuevo.** Estado de la pantalla táctil |
| **`setMicNoise(active)`** | **Nuevo.** Soplido simulado |
| `serializeState()` / `deserializeState(bytes)` | Save state en memoria |
| `readBattery()` / `consumeBatteryDirty()` | Savedata y marca de escritura |
| `captureFrame()` | Copia del búfer de vídeo |
| `attachSurface(surface)` | Superficie de presentación |
| `coreName` / `coreVersion` / `stateSize()` | Identidad para el contenedor `E4FS` |

La ROM y la partida se entregan al core **en memoria**, no como rutas: el
archivo real vive tras un `Uri` del SAF y la partida la gestiona `SaveManager`,
no el core. Igual que en `app-emulador`.

### Máscara de botones

En GBA, `GbaButton.bit` calca `KEYINPUT` y la correspondencia es directa. **En
DS no lo es**: X e Y no viven en `KEYINPUT` sino en `EXTKEYIN`, y cada core
empaqueta su propia máscara de 12 bits a su manera. `DsButton.bit` **no se
deduce del hardware**: se copia de las cabeceras del core elegido y se verifica
con una prueba que pulse cada botón por separado.

### Vídeo

El core escribe un framebuffer de **256×384** (las dos pantallas apiladas y
contiguas: la superior en las filas 0–191, la inferior en las 192–383). La
presentación **no** copia ese búfer a un `ANativeWindow` como en `app-emulador`:
lo sube como textura y pinta dos quads. El motivo y el detalle están en
[`pantallas.md`](pantallas.md).

Conviene comprobar pronto si el core puede escribir directamente en un búfer
propio (equivalente a `setVideoBuffer` de mGBA) o si hay que copiar desde su
búfer interno: es la diferencia entre una copia por fotograma y ninguna.

### Audio

La DS produce audio a **32 768 Hz**, estéreo. Se escribe en un stream de
**AAudio** en modo baja latencia, con el mismo anillo sin cerrojos de
`app-emulador` (`minSdk 26` viene impuesto por AAudio).

La limitación heredada sigue en pie: **sin sincronización por audio**, el ritmo
lo marca el reloj y en sesiones largas el anillo puede desbordar o vaciarse. Si
alguna vez se corrige, es aquí donde toca, y el `EmulatorController` está
aislado para que el cambio quede contenido en el bucle.

### Partidas de batería

Los cartuchos de DS usan EEPROM y Flash de tamaños variados, y **algunos juegos
no declaran bien su tipo**: es un problema conocido de la plataforma y la razón
de que existan bases de datos de tipos de guardado. Hay que comprobar cómo lo
resuelve el core elegido antes de dar por bueno el circuito de
`consumeBatteryDirty()`. Es el punto donde más probable es perder partidas de
usuario, y merece una prueba en dispositivo con un juego que guarde de verdad.

### Save states

El contenedor `E4FS` se hereda tal cual de `app-emulador`: identidad de la ROM
(SHA-256 + CRC32), identidad del core (nombre y versión por separado), SHA-256
del payload, y las nueve comprobaciones antes de que un byte llegue a
`loadState()`. **El formato es agnóstico del core**; sólo cambian los valores.

Y sigue valiendo la advertencia: el formato binario de un save state cambia
entre versiones del core, y cargar un estado ajeno no da un error limpio, da un
fallo nativo. Por eso `coreVersion` se guarda con cada estado, y por eso sale de
una constante del puente y no del sistema de compilación.

## 4. Cabecera de la ROM: una buena noticia

La cabecera de un cartucho de DS lleva, en el offset `0x68`, el desplazamiento a
un **banner** que contiene un icono de 32×32 y el título del juego en varios
idiomas.

Eso significa que aquí **sí hay portadas automáticas**, algo que en GBA no
existe y obligó a corregir la ficha de la tienda. Un juego añadido a la
biblioteca puede mostrar su icono y su nombre real sin que el usuario haga nada.
Es una de las mejoras más visibles que este proyecto tiene gratis, y conviene
aprovecharla desde la primera versión: `DsRomHeader` extrae identidad, título y
banner en el mismo recorrido en el que `RomIdentity` calcula el SHA-256, igual
que hoy hace `GbaRomHeader`.

El icono es 4 bpp con paleta de 16 colores en formato de tiles; convertirlo a un
bitmap es aritmética, no una biblioteca.

## 5. Riesgos conocidos

- **Rendimiento.** La DS tiene dos CPUs (ARM9 a 67 MHz y ARM7 a 33 MHz) y una
  GPU 3D que hay que emular. Es bastante más caro que la GBA. Un gama alta
  actual va sobrado; en gama media hay que medir. El contador de fps y la
  medida de porcentaje de núcleo se traen desde el principio, no al final.
- **El renderizador 3D.** Los cores ofrecen renderizado por software y, algunos,
  por OpenGL. El de software es más compatible y más lento; el de GL es más
  rápido y da problemas propios. Empezar por software, medir, y considerar GL
  después.
- **Tamaño del APK.** Sólo `arm64-v8a`, como en `app-emulador`.
- **Archivos de sistema.** Si gana melonDS, la validación de BIOS y firmware es
  una superficie de error nueva: archivos truncados, del modelo equivocado, o
  volcados de DSi que no arrancan el menú. Cada caso necesita su mensaje.
- **Compilación cruzada.** Es la parte que más fricción suele dar, y aquí más
  que con mGBA. El `CMakeLists` del puerto Android de melonDS es la referencia.

## 6. Lo que se comprobó al compilar el core (fase 4.1)

melonDS **1.1** (etiqueta estable más reciente en el momento de escribir esto)
se añadió como tarball en `third_party/melonDS/`, descargado directamente del
archivo de GitHub para esa etiqueta —no un `git clone`, para que no quede
ningún `.git` anidado dentro del árbol de trabajo— y compiló **a la primera**,
sin fricción de compilación cruzada. El riesgo que anotaba la sección
anterior no se materializó en este entregable.

### La trampa de `version.cmake` no se repite

El aviso que traía el prompt de este entregable —por el bug real de mGBA en
`app-emulador`, donde `git describe` se escapaba al `.git` de la aplicación y
estampaba su identidad como versión del core— **no aplica a melonDS**. Su
`CMakeLists.txt` fija la versión con un literal:

```cmake
project(melonDS VERSION 1.1 ...)
```

y `src/version.h.in` sólo interpola `MELONDS_GIT_BRANCH`/`MELONDS_GIT_HASH` si
se activa a mano la opción `MELONDS_EMBED_BUILD_INFO` (por defecto `OFF`), y en
ese caso exige que los tres campos (`MELONDS_GIT_BRANCH`, `MELONDS_GIT_HASH`,
`MELONDS_BUILD_PROVIDER`) lleguen ya rellenos por variables de entorno o falla
la configuración (`FATAL_ERROR`). No hay ningún `git describe` implícito que
pueda escaparse a un repositorio ajeno. Sigue en pie la regla general del
puente (`BRIDGE_CORE_VERSION` como constante propia, nunca un valor generado),
pero aquí es precaución, no la corrección de un defecto real.

### Las cuatro banderas de compilación

`app/build.gradle.kts` (`externalNativeBuild.cmake.arguments`, dentro de
`defaultConfig`) fija:

| Bandera | Valor | Por qué |
|---|---|---|
| `BUILD_QT_SDL` | `OFF` | Apaga el frontend de escritorio entero (Qt, SDL). Es la única bandera imprescindible: con ella encendida (su valor por defecto) `add_subdirectory` intenta enlazar Qt y SDL2, que no existen para Android. |
| `ENABLE_OGLRENDERER` | `OFF` | El renderizador 3D por OpenGL de melonDS incluye `OpenGLSupport.cpp`, que espera un `PlatformOGL.h` aportado por quien empotra el core. Ese archivo lo escribe el puente (fase 4.2); sin él, con el renderizador encendido, `core` ni siquiera compila. |
| `ENABLE_JIT` | `OFF` | Activaría el recompilador ARM64 con ensamblador propio (`ARMJIT_A64/*.S`). Compila igual de bien que el resto —no hay indicio de que fuera a dar problemas—, pero no hace falta para este entregable y conviene medir su beneficio real en la 4.5 antes de arriesgarlo. Queda como candidato claro para activar entonces. |
| `ENABLE_GDBSTUB` | `OFF` | Abre un socket de depuración remota. La aplicación no declara ningún permiso de red y no hay ninguna razón para compilar ese camino. |

`ENABLE_JIT` es la única de las cuatro que no es obligatoria por compilación:
es una decisión de alcance, no un bloqueo técnico. Las otras tres sí lo son.

**Actualización:** medido el coste real de no tenerlo (rendimiento
«horrible» y audio a chasquidos en juegos comerciales pesados, reportado
al cerrar la fase 4), `ENABLE_JIT` pasó a `ON` en `feature/rendimiento-jit`.
Detalle completo, con cifras, en `docs/estabilizacion-core.md`, sección
4.1.

### AGP no compila una biblioteca estática sin que se le pida por nombre

Con `core` como único objetivo de CMake (sin ningún `.so` que lo enlace
todavía, porque el puente no existe hasta la fase 4.2), `.\gradlew.bat
assembleDebug` completaba la fase de *configure* de CMake pero **no invocaba
`ninja` de verdad**: al no encontrar ninguna biblioteca compartida que
empaquetar en el APK, `buildCMakeDebug[arm64-v8a]` no tenía ningún objetivo
que construir y terminaba en segundos sin compilar una sola línea. `libcore.a`
no aparecía en `app/.cxx/` por ningún lado.

La solución no es escribir un puente vacío sólo para forzar la compilación:
es decir explícitamente qué target de CMake hace falta, con
`externalNativeBuild.cmake.targets("core")` en `defaultConfig`. Con eso, la
tarea pasa a llamarse `buildCMakeDebug[arm64-v8a][core]` y sí compila los 71
pasos de `core` y `teakra` (la biblioteca estática que aporta la emulación del
DSP de la DSi) de verdad. Esta línea se retira en la fase 4.2, cuando el
puente enlace `core` en un `.so` propio y AGP vuelva a encontrar sola qué
construir.

### Lo que produce

`libcore.a`, unos 35 MB sin reducir símbolos (es normal para una biblioteca
estática sin `strip`; lo que de verdad pesa en el APK es el `.so` final del
puente, una vez enlazado). Confirmado con `llvm-nm` que expone símbolos
`arm64-v8a` reales en el espacio de nombres `melonDS::`, contra la libc++ del
NDK (`std::__ndk1::...`).

## 7. El puente: cargar y pintar (fase 4.2)

`native/emulator_bridge/ds_bridge.cpp` enlaza `core` en `libdsemu.so` y expone
las funciones nativas de `NativeBridge.kt`; `DsEmulatorCore.kt` traduce el
contrato de `EmulatorCore` a esas llamadas. Con esto, la línea
`cmake.targets("core")` de la sección 6 ya no hace falta (AGP vuelve a
encontrar solo qué construir) y se ha retirado de `app/build.gradle.kts`.

### FreeBIOS arranca sin archivos de sistema reales

La pregunta abierta de la sección 1 —si melonDS exige BIOS y firmware
volcados de una DS real— tiene matiz: **para arrancar una ROM sí hace falta
algo en el sitio de la BIOS, pero no tiene que ser un volcado real.**
`NDSArgs`, la estructura que recibe el constructor de `NDS`, ya trae por
defecto:

```cpp
std::unique_ptr<ARM9BIOSImage> ARM9BIOS = std::make_unique<ARM9BIOSImage>(bios_arm9_bin);
std::unique_ptr<ARM7BIOSImage> ARM7BIOS = std::make_unique<ARM7BIOSImage>(bios_arm7_bin);
melonDS::Firmware Firmware {0};
```

`bios_arm9_bin`/`bios_arm7_bin` son **FreeBIOS**, una reimplementación libre
incluida en el propio `core` (`FreeBIOS.h`/`.cpp`), y `Firmware{0}` genera un
firmware sintético en memoria. El puente construye `NDS` sin tocar estos
campos, y `NDS::NeedsDirectBoot()` detecta solo que ni la BIOS ni el firmware
son "nativos" y exige arranque directo (`SetupDirectBoot()`), que es
justamente lo que se necesita para saltarse el menú de la consola y entrar
directo al juego. Verificado en dispositivo (Motorola Edge 30, ROM sintética
de prueba): el log de melonDS confirma firmware generado con CRC16 válido en
todas sus secciones y el cartucho insertado correctamente, sin pedir ningún
archivo.

**Limitación conocida, documentada en el propio `Args.h` de melonDS:**
FreeBIOS no es compatible con el modo DSi y solo permite arranque directo
(no puede arrancar por el menú de firmware). Ninguna de las dos cosas es un
problema para este proyecto: no se soporta DSi, y el arranque directo es
exactamente el comportamiento que se quiere. La import de archivos de
sistema reales (para quien prefiera BIOS/firmware propios) sigue siendo
territorio de la fase 4.5, pero deja de ser un bloqueo para tener el core
funcionando.

### El framebuffer no es zero-copy

La otra pregunta abierta de la sección 3 —si el core puede escribir
directamente en un búfer propio o hay que copiar— queda resuelta: **hay que
copiar.** `GPU::Framebuffer[2][2]` (dos búferes, uno por pantalla cada uno)
es memoria que gestiona y posee melonDS por completo; el frontend de
referencia (`Screen.cpp`, en el puerto Qt/SDL) lee el búfer activo
(`GPU::FrontBuffer`) con `memcpy` en cada presentación. `nativeCaptureFrame`
hace lo mismo: copia `Framebuffer[FrontBuffer][0]` (superior) y `[1]`
(inferior), 256×192 píxeles cada una, a las mitades del array de 256×384 que
espera `EmulatorCore.captureFrame()`. Es una copia por fotograma, no cero,
pero a 256×384 píxeles (menos de 400 KB) no es una carga significativa.

Además, el formato de píxel de melonDS no es directamente el que espera
Android: cada píxel sale como `r | (g << 8) | (b << 16)` en memoria (bytes
R, G, B, A en ese orden), mientras que Android espera un entero
`0xAARRGGBB`. El puente hace el canje byte a byte al copiar
(`ToAndroidArgb` en `ds_bridge.cpp`); sin él, el rojo y el azul saldrían
intercambiados en pantalla.

### `melonDS::Platform`: la lista exacta, no toda la cabecera

`Platform.h` declara una interfaz grande (archivos, hilos, mutex/semáforo,
multijugador, cámara, AAC, addons...) que el core necesita para enlazar. En
vez de implementar la cabecera entera a ciegas, se comprobó con `llvm-nm
--undefined-only` sobre `libcore.a` qué símbolos de `melonDS::Platform::`
quedan realmente sin resolver **con las banderas de compilación de este
proyecto** (JIT/OGL/GDB/red apagados), y sólo se implementó esa lista. Entre
lo que la cabecera declara pero `core` no llega a referenciar con estas
banderas: `GetLocalFilePath`, `FileExists`, `CheckFileWritable`,
`CheckLocalFileWritable`, `DynamicLibrary_*`.

De lo que sí hace falta:

- **Hilos, mutex y semáforos** son implementaciones reales sobre
  `std::thread`/`std::mutex`/`std::condition_variable`: el renderizador 3D
  por software de melonDS los usa aunque el JIT esté apagado, así que no
  pueden ser no-operación.
- **Archivos** (`OpenFile`/`FileRead`/`FileWrite`/...) son una envoltura real
  sobre `fopen`/`fread`/`fwrite`, aunque en este entregable ningún camino los
  ejercita de verdad (ROM en memoria, FreeBIOS, firmware generado): están
  para no romper el enlace y para cuando la fase 4.5 los necesite.
- **`Log`** enruta a `__android_log_vprint` con el tag `melonDS`; es lo que
  produjo la confirmación visible en `logcat` durante la prueba en
  dispositivo.
- **Partida de batería (`WriteNDSSave`), micrófono (`Mic_ReadInput`) y
  entrada (`nativeSetKeys`/`nativeSetTouch`)** son no-operación deliberada:
  se conectan de verdad en la fase 4.3. `DsEmulatorCore` ya expone los
  métodos —la interfaz `EmulatorCore` los exige todos—, pero de momento no
  llegan al core.
- **Multijugador, cámara, AAC (DSi) y addons** (Guitar Grip, Rumble Pak,
  Motion Pak) son no-operación permanente: no se usan en este proyecto.

### `BRIDGE_CORE_VERSION`

`"1.1"`, literal en `ds_bridge.cpp`, expuesta a Kotlin por `nativeCoreVersion()`.
Sigue el principio de la sección 2 aunque melonDS no tenga el defecto de
mGBA: lo que se graba en cada save state es esta constante, nunca un valor
generado por CMake.

### Prueba en dispositivo

Verificado en el Motorola Edge 30 con una ROM sintética generada con
`DsRomFixtures` (sin código ARM9/ARM7 real, sólo cabecera y banner válidos):
se importó por la biblioteca —icono y título del banner correctos—, cargó
sin error, corrió más de dos minutos sin caerse, y las dos pantallas se
vieron correctamente separadas y del tamaño correcto en la pantalla de
juego. Al no llevar código ejecutable real, ambas pantallas quedan en negro
—un framebuffer válido e inicializado, no basura—, que es el resultado
esperable para esa ROM y confirma que la tubería completa (JNI → melonDS →
`captureFrame` → textura GL) funciona de punta a punta. Comprobar el pintado
de un juego de verdad queda pendiente de una ROM homebrew legítima; no se
usó ninguna ROM comercial del usuario para esta prueba (ver
`docs/plan-de-trabajo.md`, protocolo de pruebas en dispositivo).

## 8. Entrada, audio y savedata contra el core real (fase 4.3)

### El mapa de bits, copiado del frontend de referencia

`DsButton.bit` sale de `EmuInstance::buttonNames` en
`frontend/qt_sdl/EmuInstanceInput.cpp`, el array que el propio proyecto
melonDS usa para nombrar sus doce entradas: A, B, Select, Start, Right,
Left, Up, Down, R, L, X, Y. Es el mismo orden que espera `NDS::SetKeyMask`
(bits 0-9 pasan a `KeyInput`, bits 10-11 a `EXTKEYIN`). Verificado pulsando
los doce botones por separado contra el core real en el Motorola Edge 30
—con logging temporal en `nativeSetKeys`, retirado antes de este commit—:
cada botón, y cada dirección de la cruceta, produjo exactamente el bit
esperado, sin excepción. Ese resultado más una prueba de JVM
(`DsButtonTest`) que fija los doce valores son las dos defensas contra que
alguien reordene el enum sin darse cuenta.

**Polaridad:** `KEYINPUT`/`EXTKEYIN` son activos a nivel bajo (0 = pulsado);
en Kotlin, por convención de este proyecto, 1 = pulsado. El puente invierte
la máscara (`~mask & 0xFFF`) justo antes de `SetKeyMask`; en ningún otro
punto del código se piensa en «activo a nivel bajo».

### Pantalla táctil

`nativeSetTouch` llama a `NDS::TouchScreen(x, y)` o `NDS::ReleaseScreen()`
tal cual, sin lógica propia: la geometría (recorte, coordenada 0..255/0..191)
ya la resuelve `TouchRouter` antes de llegar aquí, y el contrato de «una
llamada por fotograma mientras el dedo esté abajo» lo cumple `InputManager`
más arriba. Verificado en dispositivo: las coordenadas que llegan al puente
caen dentro de rango y se mueven de forma consistente con el dedo.

### Micrófono

`Mic_ReadInput` rellena el búfer con ruido blanco a (casi) plena escala
mientras el botón de soplar esté activo, y devuelve 0 muestras si no —sin
`RECORD_AUDIO`, como en toda la aplicación—. El nivel es deliberadamente
alto: los juegos que comprueban un umbral de soplido no siempre lo cruzan
con un ruido tibio.

### Audio: AAudio con el mismo anillo sin cerrojos que `app-emulador`

Mismo diseño que `mgba_bridge.c`: un anillo circular de enteros de 16 bits
sin cerrojos (índices atómicos de lectura/escritura, potencia de dos), con
el hilo de emulación como único productor y el callback de AAudio como
único consumidor. La diferencia con mGBA es dónde se resamplea: mGBA entrega
`blip_t` y el puente decide la conversión; el `SPU` de melonDS ya resamplea
internamente si se le da la frecuencia de salida real al construir el `NDS`
(`NDSArgs::OutputSampleRate`). Por eso el stream de AAudio se abre en
`nativeCreate` —antes de que exista ningún `NDS`— y la frecuencia que
devuelve de verdad (`AAudioStream_getSampleRate`, no la pedida) es la que se
pasa a `NDSArgs` en cada `nativeLoad`.

Un detalle de seguridad entre hilos: `SPU::ReadOutput` **no** es zero-safe
frente a un lector concurrente (sus índices de lectura/escritura son
enteros simples, no atómicos). Por eso el vaciado del SPU ocurre siempre
dentro de `nativeRunFrame`, en el hilo de emulación —igual que
`RunFrame()`—, y nunca desde el callback de AAudio; el callback sólo toca
el anillo propio del puente.

Verificado en el Motorola Edge 30: `AAudioStream_getSampleRate` devuelve
32768 Hz exactos (el dispositivo acepta la frecuencia nativa de la DS sin
remuestreo), el stream arranca sin error y no hay caídas tras varios
minutos con el botón de soplar mantenido. Sigue pendiente una comprobación
con sonido real de un juego (la ROM sintética de prueba no lleva código que
produzca audio); es la misma limitación que con el vídeo, y se resuelve
igual: con una ROM homebrew legítima cuando haya una a mano.

### Partida de batería: se lee siempre en vivo, nunca se copia

`NDSCart::ParseROM` recibe `emu` como `userdata`, y ese mismo puntero es el
`userdata` de `NDS` (igual que hace el frontend de referencia). Con eso:

- `WriteNDSSave` sólo marca `batteryDirty`; nunca copia el contenido.
- `nativeReadBattery` lee siempre `NDS::GetNDSSave()` /
  `GetNDSSaveLength()` —el búfer que posee el propio cartucho— en el
  momento en que se llama, así que no hay dos copias que puedan
  desincronizarse.
- Al cargar, si hay partida previa, se entrega como `NDSCartArgs::SRAM` en
  memoria antes de `ParseROM`; el tamaño real de la savedata lo decide el
  propio core (su detección de tipo de cartucho, `ROMList`), no Kotlin.

**Pendiente de verificar con un juego real:** la ROM sintética de prueba no
tiene una entrada reconocida en `ROMList` (`ROM entry not found for
gamecode...`, visto en logcat), así que no se pudo comprobar en dispositivo
el circuito completo de guardar, cerrar la app, reabrir y encontrar la
partida — el "está hecho cuando" exacto de este entregable para la parte de
savedata. La plomería (tipos, JNI, lectura en vivo) está verificada por
inspección y no rompe nada en las pruebas hechas; falta el round-trip real,
que requiere un juego libre que guarde de verdad. Es el mismo tipo de
limitación que el vídeo y el audio: exige una ROM homebrew legítima, no la
sintética.

## 9. Save states contra el core real (fase 4.4)

### El puente, contra `melonDS::Savestate`

`nativeSerializeState`/`nativeStateSize` crean un `Savestate` con el
constructor que reserva su propio búfer (`Savestate::DEFAULT_SIZE`, 32 MB),
llaman a `NDS::DoSavestate`, y devuelven `Length()` bytes reales — nunca la
capacidad reservada. `nativeDeserializeState` usa el otro constructor
(`Savestate(buffer, size, false)`), que envuelve los bytes de Java **sin
copiarlos ni adueñarse de ellos** (`buffer_owned=false` en `Savestate.cpp`):
el puntero JNI sólo tiene que vivir mientras dura `DoSavestate`, y así es.

`coreName` («melonds») y `coreVersion` (`BRIDGE_CORE_VERSION`, `"1.1"`)
siguen siendo constantes del puente, nunca un símbolo que genere el core —la
regla de la sección 2, que aquí es precaución y no la corrección de un
defecto real (sección 6)—. El reintento de `loadState` (serializar el estado
actual antes de restaurar, devolverlo si el core rechaza el archivo) ya vivía
en `EmulatorController.loadState` desde la fase 2.4 y no necesitó ningún
cambio: es genérico, no conoce a melonDS.

### Un dato nuevo importante: el tamaño real de un save state

Un estado completo de melonDS pesa **~19 MB** sin comprimir, incluso para una
ROM sin código real: VRAM, RAM principal y los búferes del GPU3D se
serializan siempre, se use o no el renderizador 3D. Es casi 50 veces más que
los ~400 KB de un save state de mGBA en `app-emulador`. Con cinco ranuras de
usuario más el respaldo automático, un solo juego puede ocupar más de 100 MB
sólo en save states. Esto no bloquea nada de esta fase, pero es una cifra a
tener en cuenta en la 4.5 (medición) y, más adelante, en cualquier pantalla
que hable de espacio ocupado.

### Las dos defensas del proyecto hermano

1. **`GoldenSaveStateTest`** (`app/src/test/java/.../data/save/`), contra un
   `slot_1.state` real de ~19 MB capturado en el Motorola Edge 30 con la ROM
   sintética (`app/src/test/resources/save/golden-melonds-1.1.state`,
   extraído con `adb run-as` justo como se hizo con el fixture de mGBA).
   Corre en JVM pura: no toca `libdsemu.so` ni puede detectar una regresión
   de `coreVersion` en un build futuro, sólo que el formato del contenedor
   sigue decodificándose igual.
2. **La tarea de Gradle `verifyCoreVersionDebug`/`verifyCoreVersionRelease`**
   (`app/build.gradle.kts`), con dependencia de `assembleDebug`/
   `assembleRelease`, que sí inspecciona el objeto compilado
   (`ds_bridge.cpp.o`): comprueba que contiene el literal `"1.1"` y que
   ninguna fuente propia referencia los símbolos de `version.h` de melonDS
   (`MELONDS_VERSION`, `MELONDS_GIT_BRANCH`, `MELONDS_GIT_HASH`,
   `MELONDS_BUILD_PROVIDER`) en vez de `BRIDGE_CORE_VERSION`. Verificada en
   verde contra `assembleDebug` y `assembleRelease` (con R8 activo).

### Prueba en dispositivo

Verificado en el Motorola Edge 30 con la misma ROM sintética de las fases
4.2/4.3: se guardó la ranura 1 desde el menú de pausa («Guardado en la
ranura 1»), se extrajo el contenedor real con `adb run-as`, y se comprobó
que decodifica, que `coreName`/`coreVersion` son exactamente `"melonds"`/
`"1.1"`, y que pasa sus propias comprobaciones de identidad. Sigue pendiente
—igual que el vídeo, el audio y la partida de batería— el "está hecho
cuando" con un juego real: guardar, cargar y comprobar que la partida vuelve
tal cual, con un juego que de verdad se juegue. La plomería está verificada;
falta el contenido real, y exige una ROM homebrew legítima.

## 10. La decisión, cerrada: melonDS

La sección 1 planteaba la elección como una balanza entre precisión y
fricción de arranque, con DeSmuME por delante en lo segundo (arranca sin
BIOS, por HLE) y melonDS por delante en lo primero. El plan de trabajo
preveía medir los dos con números delante antes de decidir. Eso no hizo
falta, y conviene decir exactamente por qué en vez de saltarse el paso.

**La fricción de arranque de melonDS, que era su desventaja frente a
DeSmuME, no existe en la práctica.** La sección 7 ya lo estableció:
`NDSArgs` trae FreeBIOS y firmware generado por defecto, y basta con
arrancar en modo directo para que un juego —real, no sólo la ROM
sintética— corra sin pedir un solo archivo del usuario. Verificado con
Cave Story DS y con un segundo homebrew del mismo motor, y confirmado
también con la observación —hecha por el usuario, fuera de las pruebas
formales de este documento— de que una ROM comercial real llegaba a
insertarse y a descifrar su área segura sin fricción de BIOS. La ventaja
que hacía a DeSmuME atractivo pese a su menor precisión desaparece: no
hay ningún motivo para aceptar menos exactitud a cambio de una facilidad
de arranque que el propio melonDS ya ofrece.

**Lo que sí costó fue integración, no elección de core.** Los dos defectos
de la sección 1 de `estabilizacion-core.md` (`SetupDirectBoot` incompleto,
`NDS::Start()` nunca llamado) eran errores del puente contra la API de
melonDS, no limitaciones del core en sí — y una vez corregidos, un juego
real arranca, se ve y se oye en las dos pantallas sin más peros que los ya
conocidos (sin JIT, sin renderizador 3D por GL, ver más abajo). Nada de lo
encontrado apunta a que DeSmuME fuera a evitar ese trabajo: cualquier
puente nuevo contra cualquier core exige este mismo tipo de verificación
contra contenido real, no sintético.

**Por eso no se construyó DeSmuME para comparar.** Habría sido repetir la
integración entera —CMake, Platform, puente JNI— de un segundo core sólo
para una comparación cuyo resultado ya se puede argumentar sin
ambigüedad: melonDS compiló sin fricción desde la fase 4.1 (sección 6),
integró sin exigir ningún archivo de sistema (sección 7), y sus dos únicos
defectos de arranque eran del puente, ya corregidos y verificados con un
juego real (`estabilizacion-core.md`, sección 1). El coste de escribir un
segundo puente completo sólo para confirmar lo que ya está confirmado no
se justifica.

**Con los números que sí hay** (`estabilizacion-core.md`, sección 2): 59-60
fps emulados estables, 89-90 fps presentados, 1,4-1,7 núcleos de uso
combinado en un gama alta, sin caída de fps tras varios minutos seguidos y
con Nítido/Suavizado costando exactamente lo mismo —la comprobación que
pedía el entregable, superada—. Ninguna cifra sugiere que haga falta
reconsiderar.

**Lo que queda abierto**, y no es una limitación de la elección sino del
alcance de esta fase: sin JIT (deferido a un ensayo explícito, igual que
el ensayo ×2 de `app-emulador`), sin renderizador 3D por GL (exige
`PlatformOGL.h`, no escrito), sin medir en un dispositivo de gama media, y
sin el "está hecho cuando" completo de guardado/estado con un juego que
de verdad se juegue —todo pendiente de una ROM homebrew con contenido
real, no de la elección de core—.

**Decisión: melonDS, versión 1.1, fijada como constante del puente
(`BRIDGE_CORE_VERSION`).** `StubEmulatorCore` sigue en el proyecto como
doble de pruebas sin NDK; `DeSmuME` no se integra.
