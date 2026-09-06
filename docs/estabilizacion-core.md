# Estabilización del core: lo que se rompió y lo que se midió

Este documento recoge lo que salió mal —y se corrigió— al poner melonDS a
correr de verdad en la fase 4 (entregables 4.5 y 4.C), y las medidas de
rendimiento en el Motorola Edge 30. Es el equivalente de este proyecto al
«qué se corrigió por el camino» del proyecto hermano.

## 1. Tres defectos, encontrados con ROMs reales

Las fases 4.2, 4.3 y 4.4 se verificaron con una ROM sintética
(`DsRomFixtures`, sin código ARM9/ARM7 real) porque las pruebas en
dispositivo de este proyecto nunca usan ROMs del usuario. Esa ROM demostró
que la tubería JNI funcionaba —el core cargaba, pintaba un framebuffer
válido, guardaba y cargaba estados—, pero **no podía demostrar que un juego
de verdad arrancara**, porque no ejecuta código real.

Al conseguir una ROM homebrew legítima para la medición de rendimiento de
este entregable (Cave Story DS, `tilderain/CaveStoryNDS`, cuyo motor
—NXEngine— es GPL-3 y cuyos datos de juego originales son freeware conocido
del propio Studio Pixel; usada sólo localmente para medir y retirada al
terminar, igual que cualquier ROM libre de prueba) y, ya en el cierre de la
fase (4.C), al ver el usuario un juego comercial real con colores propios,
aparecieron tres defectos reales en el puente que la ROM sintética no podía
revelar. Los tres están corregidos y committeados por separado en
`feature/fase-4-core-nativo`.

### 1.1. Faltaba el paso de cifrado KEY2 del cartucho

**Síntoma:** con una ROM real, el juego arrancaba (la CPU saltaba a su
dirección de entrada real) pero cualquier pantalla se quedaba en blanco de
inmediato.

**Causa:** `nativeLoad` llamaba a `NDS::SetupDirectBoot()` —la sobrecarga sin
argumentos, que sólo prepara la CPU: registros, memoria, copia del logo—
pero nunca a `NDSCartSlot::SetupDirectBoot()`, el paso que pasa el cartucho
a modo de cifrado KEY2. Sin eso, cualquier lectura de ROM después de la
cabecera inicial (overlays, gráficos, sonido) vuelve con datos basura.

**Arreglo:** usar la sobrecarga `NDS::SetupDirectBoot(romname)`, que invoca
las dos cosas en el orden correcto —es lo que hace el frontend de
referencia—. El nombre en sí sólo importa para el modo SD de cartuchos
homebrew, que este proyecto no usa; cualquier literal sirve.

### 1.2. Faltaba `NDS::Start()`: una emulación que parecía sana y no lo era

Este es el más importante de los dos, y merece el detalle completo.

**Síntoma:** con el arreglo anterior puesto, el juego seguía sin pintar
nada —blanco sólido, constante, con dos ROMs reales distintas (el juego
completo y un «fun rom» del mismo motor, descartando que fuera un problema
del contenido)—. Y sin embargo, **todo lo demás parecía funcionar**: el
contador de fotogramas avanzaba establemente a 59-60 fps durante más de 30
segundos seguidos, sin caídas del proceso, sin errores en `logcat`, y el
log de melonDS incluso llegó a mostrar «Game is now booting». Una
emulación que, medida desde fuera, no daba ninguna señal de estar mal.

**Diagnóstico:** `NDS::Running` no tiene inicializador de miembro, y
`NDS::Reset()` tampoco lo toca —sólo `NDS::Start()` lo pone a `true`.
`nativeLoad` llamaba a `Reset()` pero nunca a `Start()`, así que `Running`
se quedaba en el valor indeterminado con el que se construye el objeto.

`NDS::RunFrame()` entero es un `while (Running) { ... }`
(`third_party/melonDS/src/NDS.cpp:933`). Con `Running` en `false` ese bucle
no ejecuta ni un solo ciclo de CPU: `GPU::FinishFrame()` no se llama nunca,
así que `GPU::FrontBuffer` no alterna y el framebuffer se queda para
siempre en el blanco con el que `GPU::Reset()` lo rellena
(`0xFFFFFFFF`, `GPU.cpp:178`) — ese blanco es el valor de reset, no un
búfer sin escribir. Y sin embargo `NumFrames++` sigue contando **fuera**
del bucle (`NDS.cpp:1064`, con un comentario del propio melonDS explicando
que el contador de fotogramas debe avanzar aunque la consola esté apagada,
pensando en TAS), y `RunFrame()` devuelve `263` (`NDS.cpp:1071`) — el
número de líneas de un fotograma completo. De ahí que nuestro contador de
fps emulados, que sólo mide cuántas veces `RunFrame()` vuelve por segundo,
marcara 59-60 fps perfectos sin que se ejecutara ni una instrucción de la
CPU.

El «Game is now booting» que parecía confirmar que el juego corría tampoco
era la señal que aparentaba: esa cadena sale de `NDS::MonitorARM9Jump()`
(`NDS.cpp:1598`), pero la llamada que la disparó no vino del intérprete
ejecutando instrucciones dentro de `RunFrame()` —que nunca llegó a
correr—, sino de `ARM9.JumpTo(header.ARM9EntryAddress)` dentro de la propia
`SetupDirectBoot()`, que ocurre una sola vez al cargar, sin relación con
que la consola esté realmente en marcha.

**Arreglo:** añadir `emu->nds->Start()` en `nativeLoad`, en el orden del
frontend de referencia: `Reset()` → `SetupDirectBoot()` → `Start()`.
Verificado con la misma ROM: el framebuffer deja de ser blanco constante,
cambia de verdad fotograma a fotograma, y se ve la pantalla de carga y el
título completo de Cave Story DS en las dos pantallas.

**Por qué queda escrito con este detalle:** ninguna de las señales
externas que este proyecto usa habitualmente para verificar una fase
—contador de fotogramas, ausencia de errores en `logcat`, el proceso vivo—
habría detectado este defecto. Sólo mirar el contenido real del
framebuffer, con una ROM que de verdad ejecuta código, lo hizo evidente.
Es la razón concreta por la que la fase 4.5 no podía cerrarse con la ROM
sintética: una emulación que no emula nada puede parecer, en todas las
métricas automáticas, indistinguible de una que sí funciona.

### 1.3. Canales de color R y B intercambiados

**Síntoma:** con los dos defectos de arranque ya corregidos y un juego real
pintando de verdad, los colores salían mal. Con Cave Story DS (paleta
sobre todo en blanco, negro y un amarillo/verde puro —simétricos ante un
intercambio de canales, así que no delataban nada) no se notó. Con un
juego de colores variados —Pokémon HeartGold, del usuario; ningún ROM
comercial se usó por parte de Claude para diagnosticar ni arreglar
esto— quedó a la vista de inmediato: Ho-Oh, que es rojo, salía azul.

**Causa:** `ToAndroidArgb`, la función que convierte cada píxel del
framebuffer de melonDS al entero `0xAARRGGBB` que espera Android dando
por hecho que cada píxel en memoria es R,G,B,A (byte 0 = rojo). Es al
revés: B,G,R,A (byte 0 = azul). El error llevaba desde la fase 4.2 sin
detectarse exactamente por lo mismo que el de `Start()`: la ROM sintética
no tiene gráficos reales con los que notarlo.

**Arreglo:** intercambiar qué byte se lee como `r` y cuál como `b` dentro
de `ToAndroidArgb` (`ds_bridge.cpp`). Verificado con Ho-Oh en Pokémon
HeartGold (por el usuario) y con la introducción de Cave Story DS, donde
el robot compañero (azul en el juego real) y la cruz del abrigo del
doctor (roja en el juego real) salen ahora del color correcto.

## 2. El `OutOfMemoryError` con ROMs sueltas grandes

**Síntoma:** abrir un cartucho real de más de 100 MB (Pokémon Platino,
128 MB, del usuario) cerraba la aplicación con `OutOfMemoryError`.

**Causa:** `RomRepository.readRomBytes` copiaba el archivo entero a un
único `ByteArray` de Kotlin antes de entregarlo al core. El propio
comentario de la fase 3 ya avisaba de esto: *"cuando entre el core nativo
(fase 4), este es el punto donde conviene pasar a `openFileDescriptor` +
`mmap`... para evitar la copia por completo"* — una ROM suelta grande
agotaba el heap de Java (limitado a un cuarto de GB o así en la mayoría
de dispositivos) antes de llegar siquiera al core.

**Arreglo:** `RomRepository.openRomForCore` reemplaza esa vía para ROMs
sueltas (no dentro de un `.zip`, que sigue necesitando descomprimirse en
memoria): abre un `ParcelFileDescriptor` real en vez de copiar bytes.
`EmulatorCore.loadFromDescriptor` es la nueva entrada de la frontera —con
implementación por defecto que sigue leyendo a memoria, así que
`StubEmulatorCore` no necesitó tocarse—; `DsEmulatorCore` la sobrescribe
para mapear el archivo con `mmap` en `ds_bridge.cpp` y pasárselo a
`NDSCart::ParseROM` sin que exista un `ByteArray` con la ROM completa en
ningún momento.

**Verificación:** con una ROM sintética de 150 MB
(`SyntheticRomExporter.exportarGrande`, del mismo orden de magnitud que
Pokémon Platino), `dumpsys meminfo` confirma que el heap de Dalvik se
queda en ~29 MB mientras el heap nativo absorbe la ROM completa (~256 MB
redondeados por el propio core a la potencia de dos más cercana). Antes
del arreglo, esa misma prueba repetía el `OutOfMemoryError`.

## 3. Medición de rendimiento (Motorola Edge 30)

Con los dos defectos corregidos, medido con Cave Story DS (misma ROM del
diagnóstico anterior) en pantalla de título e introducción —el estado
estable más exigente al que se pudo llegar sin jugar de verdad, dado que
la entrada de este proyecto está pensada para tocarse desde la interfaz,
no para automatizarse—.

### 3.1. Fps emulados frente a presentados, por separado

| Medida | Valor |
|---|---|
| Fps emulados (`EmulatorController`, ritmo de `RunFrame`) | 59-60, estable |
| Fps presentados (`DsGLRenderer.onDrawFrame`) | 89-90, estable |

Los presentados van muy por encima de los emulados porque `GLSurfaceView`
en modo de renderizado continuo dibuja tan rápido como se lo permite la
tasa de refresco real del dispositivo (el Motorola Edge 30 llega a 90 Hz),
no al ritmo de la DS: el mismo fotograma emulado se sube y se redibuja
varias veces antes de que exista uno nuevo. No es un error —no hay
parpadeo ni tirón perceptible, y confirma que el hilo de GL nunca espera a
la emulación—, pero es trabajo de GPU de sobra: redibujar el mismo
fotograma ~1,5 veces por cada fotograma nuevo real. Candidato razonable
para una fase futura: limitar el modo de renderizado a
`RENDERMODE_WHEN_DIRTY` y pedir un redibujado sólo cuando `captureFrame()`
entregue contenido nuevo.

### 3.2. Porcentaje de CPU por hilo

Medido con `adb shell top -H -p <pid>` sobre los dos hilos propios del
proyecto (`ds-emulation`, el hilo de emulación; `GLThread`, el de
renderizado):

| Momento | `ds-emulation` | `GLThread` |
|---|---|---|
| Nítido, recién cargado | 70,3 % | 81,4 % |
| Suavizado, recién cargado | 66,6 % | 81,4 % |
| Tras ~8 minutos seguidos | 78,5 % | 96,4 % |

Cada porcentaje es relativo a un núcleo completo (100 % = un núcleo
saturado); en total, el proceso usa entre 1,4 y 1,7 núcleos de los ocho del
SoC. Un gama alta como el Edge 30 va sobrado; queda pendiente, para cuando
haya datos de un dispositivo de gama media, comprobar si estas cifras
dejan margen suficiente.

### 3.3. Nítido frente a Suavizado

**Deberían costar lo mismo, y cuestan lo mismo.** `GLThread` midió
exactamente 81,4 % en ambos filtros, sin diferencia medible; la pequeña
diferencia en `ds-emulation` (70,3 % frente a 66,6 %) es ruido de muestreo
—ese hilo no toca el filtro de la textura en ningún punto, así que no hay
ningún motivo por el que debiera cambiar—. El renderizador pasa la
comprobación que pedía el entregable: el filtro es un parámetro de
muestreo de textura, no una geometría distinta, y así se comporta.

### 3.4. Comportamiento tras varios minutos (térmico)

Tras ~8 minutos de ejecución continua:

- Los fps —emulados y presentados— no bajaron ni un punto.
- El porcentaje de CPU por hilo subió (`ds-emulation` 70,3→78,5 %,
  `GLThread` 81,4→96,4 %).
- Las temperaturas de los sensores de CPU/GPU rondaban 58-65 °C
  (`/sys/class/thermal/thermal_zone*/temp`), y las frecuencias reales de
  los núcleos grandes estaban en torno al 80 % de su máximo
  (`scaling_cur_freq` frente a `cpuinfo_max_freq`).

**Esto no es una confirmación de limitación térmica real** —ni las
temperaturas ni las frecuencias son señal inequívoca de *throttling*
forzado en este SoC, y podrían explicarse por el gobernador normal de
DVFS reaccionando a una carga sostenida—, pero la combinación de «más CPU
para sostener exactamente los mismos fps» es la huella que dejaría un
principio de limitación térmica, y queda anotada como tal para revisarla
si en el futuro hay medidas de sesiones más largas o de un dispositivo que
caliente más.

### 3.5. Renderizador 3D por software frente a GL

**No se pudo comparar.** Esta compilación tiene `ENABLE_OGLRENDERER=OFF`
(ver `docs/integracion-core.md`, sección 6): el renderizador 3D acelerado
por OpenGL de melonDS no está compilado porque exige `PlatformOGL.h`, que
este proyecto no ha escrito. La única vía de renderizado 3D disponible es
la de software, y es la que se midió arriba sin separar su coste del resto
del `GLThread` —tampoco había manera de aislarlo sin instrumentación
adicional dentro del propio core—. Activar el renderizador GL y repetir
esta comparación queda abierto para cuando haga falta de verdad exprimir
más rendimiento; con los números de hoy, en un gama alta, no hace falta.

## 4. Limitaciones que quedan abiertas

- **Sin verificación con un juego jugado de verdad.** Todo lo de este
  documento se midió en la pantalla de título e introducción de Cave
  Story DS: es lo más lejos que se pudo llegar sin automatizar la entrada
  (los doce botones y el táctil de este proyecto están pensados para
  tocarse, no para *scriptearse*). El guardado/carga de partida con
  contenido real de guardado sigue pendiente, como ya anotaba
  `docs/integracion-core.md` sección 8.
- **Sin comparación con DeSmuME.** No se llegó a integrar como segundo
  core: melonDS compiló sin fricción desde la fase 4.1, y FreeBIOS
  eliminó la que era su mayor desventaja frente a DeSmuME (pedir BIOS y
  firmware reales). Con eso resuelto, no hay ninguna señal que sugiera
  que hiciera falta escribir un segundo puente completo sólo para
  comparar. La decisión de core se cierra con esto en
  `docs/integracion-core.md`.
- **`RENDERMODE` continuo del `GLSurfaceView`.** Ver sección 3.1: dibuja
  más veces de las necesarias. No afecta a la fluidez percibida, pero es
  trabajo de GPU de sobra.
- **Medición hecha en un único dispositivo gama alta.** Sin datos de gama
  media, que es donde el margen de rendimiento importa de verdad.
- **Rendimiento y audio con un juego comercial pesado, sin JIT.** El
  usuario probó juegos reales (Brain Age, Pokémon HeartGold) durante el
  cierre de la fase y reportó rendimiento «horrible» y audio que empieza
  bien y se degrada —exactamente la huella de un hilo de emulación que no
  sostiene el ritmo real de la DS: sin JIT (intérprete puro, la
  configuración de la fase 4.1), un juego con más carga de CPU que Cave
  Story DS puede quedarse por detrás del reloj, y el anillo de audio sin
  sincronización (ver `docs/integracion-core.md`, sección 7, «Audio») se
  vacía o se llena a destiempo cuando eso pasa. No se investigó más a
  fondo ni se activó el JIT dentro de esta fase, a propósito: el propio
  plan trata "activar el JIT" como su propia decisión, con medición
  dedicada antes de arriesgar el ensamblador ARM64 del recompilador
  (`docs/integracion-core.md`, sección 1), no como una reacción a un
  síntoma. Candidato claro para el primer entregable de la próxima fase
  que toque rendimiento.
