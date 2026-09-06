# Elección e integración del core

Este documento recoge qué core usar, por qué, y cómo enchufarlo. **La decisión
está deliberadamente abierta**: la frontera `EmulatorCore` se diseña para
admitir los dos candidatos, y la elección se toma midiendo en dispositivo, no
leyendo tablas. Es el mismo método que llevó a mGBA en `app-emulador`.

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
