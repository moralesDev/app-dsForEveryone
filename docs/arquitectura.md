# Arquitectura

## Idea de fondo

`app-emulador` está construido alrededor de **una** frontera, `EmulatorCore`, y
esa apuesta se ganó el sueldo: la aplicación entera se construyó contra un core
de pruebas, y meter mGBA consistió en escribir el puente y cambiar una línea del
módulo de inyección de dependencias.

Aquí se repite la apuesta y se añaden dos fronteras más, porque la DS introduce
dos ejes que la GBA no tiene: **dónde van las pantallas** y **dónde toca el
dedo**. Las dos son geometría, y la geometría es el sitio donde los errores no
dan excepción sino un píxel de más.

```
┌──────────────────────────── Kotlin ─────────────────────────────┐
│  ui/       Compose: biblioteca, juego, mandos, disposición      │
│  data/     ROMs · partidas · archivos de sistema · ajustes      │
│  emulator/ EmulatorController: hilo, ritmo, cola de comandos    │
└──────┬────────────────┬────────────────┬───────────────────────-┘
       │                │                │
  EmulatorCore     ScreenLayout      TouchRouter
   (JNI → C++)      (geometría)      (geometría)
       │
  ┌────┴──────────────────┐
  │                       │
DsEmulatorCore      StubEmulatorCore
(melonDS o DeSmuME)  (pruebas, sin NDK)
```

`ScreenLayout` y `TouchRouter` viven **fuera de Compose**, por el mismo motivo
que `DirectionalPadZones` en `app-emulador`: entra un tamaño, salen rectángulos,
y eso se comprueba coordenada a coordenada en JVM sin Robolectric.

## Estructura de paquetes

Paquete: **`com.moralesDev.dsForEveryone`**. Nombre de la app: **DsForEveryone**,
que mantiene la familia de `EmuladorForEveryone`.

```
com.moralesDev.dsForEveryone
├── MainActivity            Activity única
├── DsEmuApplication        Arranca Koin
│
├── emulator/
│   ├── DsButton            Los doce botones y su máscara (del core, no del hardware)
│   ├── InputManager        Máscara de botones + estado táctil, UI ↔ hilo de emulación
│   ├── DirectionalPadZones Cruceta de ocho direcciones, con diagonales
│   ├── EmulatorController  Hilo de emulación, ritmo y cola de comandos
│   ├── core/
│   │   ├── EmulatorCore      La frontera
│   │   ├── DsEmulatorCore    El core real, sobre melonDS o DeSmuME
│   │   ├── NativeBridge      Declaración de los métodos JNI
│   │   └── StubEmulatorCore  Doble de pruebas, sin código nativo
│   ├── layout/
│   │   ├── LayoutEngine      Rectángulos con restricciones. Compartido
│   │   ├── ScreenLayout      Colocación de las dos pantallas
│   │   ├── ControlLayout     Colocación de los siete grupos de mandos
│   │   └── TouchRouter       De punto de pantalla a coordenada de la DS
│   └── render/
│       └── DsGLRenderer    GL ES: una textura de 256×384, dos quads
│
├── data/
│   ├── db/                 Room: biblioteca de juegos
│   ├── rom/                Identidad (SHA-256), cabecera de DS, banner, repositorio SAF
│   ├── save/               BatterySaveManager, SaveStateManager, SaveStateContainer
│   ├── system/             BIOS y firmware aportados por el usuario: validación y rutas
│   ├── storage/            Rutas en disco y escritura atómica
│   └── settings/           DataStore
│
├── di/                     Módulo de Koin (AppModule)
└── ui/
    ├── MainViewModel       Idioma y onboarding
    ├── UiMessage           Mensajes sin traducir, resueltos en Compose
    ├── theme/              Tokens de Nocturne
    ├── components/         Botones, interruptores, segmentados, píldoras
    ├── locale/             Cambio de idioma en caliente
    ├── navigation/
    ├── welcome/            Presentación inicial + archivos de sistema si hacen falta
    ├── library/            Biblioteca de juegos
    ├── game/               Pantalla de juego, menú de pausa, hoja de ranuras
    │   ├── controls/       Cruceta, botones, superficie táctil
    │   └── layout/         Editor de disposición: pantallas y mandos
    ├── settings/
    └── about/              Incluye licencias y enlace al código fuente (obligatorio: GPL)
```

Tres paquetes son nuevos respecto a `app-emulador` y responden a las tres cosas
que la DS añade:

- **`emulator/layout/`** — todo lo que decide *dónde va cada rectángulo*. Es el
  paquete con más pruebas de JVM del proyecto.
- **`data/system/`** — BIOS y firmware. Si gana DeSmuME, este paquete se queda
  casi vacío; si gana melonDS, es una superficie de error de primer orden.
- **`ui/game/layout/`** — el editor. Nace con la primera versión, no después.

## Decisiones y por qué

### Tres fronteras, no una

`EmulatorCore` aísla *qué emula*. Pero en la DS hay dos preguntas más que
cambian con cada disposición, cada rotación y cada preferencia del usuario, y
que si viven dentro de composables sólo se pueden comprobar con capturas de
pantalla:

- **`ScreenLayout`**: dado un hueco y un modo, ¿dónde va cada pantalla?
- **`TouchRouter`**: dado un punto de la vista y esa colocación, ¿qué coordenada
  de la DS es, o ninguna?

Sacarlas a objetos puros no es purismo: es la diferencia entre una prueba de
JVM que dice «la esquina inferior derecha da (255,191)» y una tarde mirando la
pantalla del teléfono.

### El ritmo cambia

La DS corre a **59,8261 Hz**: 33 513 982 Hz ÷ 560 190 ciclos por fotograma
(355 puntos × 263 líneas × 6 ciclos). El período es **16 715 113 ns**, no los
16 742 706 ns de la GBA ni los 16,67 ms de un 60 Hz redondo.

Todo lo demás del bucle se hereda intacto: `Thread` propio con
`THREAD_PRIORITY_URGENT_DISPLAY`, `LockSupport.parkNanos` sobre reloj absoluto
—para que un fotograma lento no arrastre a los siguientes—, y cola de comandos
en vez de cerrojos.

### La entrada táctil no va por la cola de comandos

Los comandos —guardar estado, cargarlo, capturar miniatura— son operaciones que
tienen que ocurrir con la máquina detenida entre fotogramas, y por eso se
encolan.

La entrada táctil **no es eso**. Es estado continuo, igual que la máscara de
botones, y se comparte igual: la UI escribe la última posición, el hilo de
emulación la lee al principio de cada fotograma. Encolar un comando por evento
táctil produciría cientos de comandos por segundo y arrastres a saltos.

El detalle está en [`entrada.md`](entrada.md).

### GL ES en lugar del truco del búfer

En `app-emulador` el tamaño del búfer de la superficie *es* el filtro de imagen.
Es elegante y con dos pantallas no funciona: el compositor escala una superficie
a un rectángulo, y aquí hacen falta dos rectángulos independientes.

La solución —una textura de 256×384 y dos quads, con `GL_NEAREST` o `GL_LINEAR`
según el filtro— es la que ya estaba anotada como *limitación abierta* en el
proyecto hermano. Allí era una optimización pendiente; aquí es un requisito. De
regalo, elimina la causa concreta del bug de rotación que costó aquella
investigación de `geomLayerBounds`.

El razonamiento completo está en [`pantallas.md`](pantallas.md).

### Un solo motor de disposición

Colocar dos pantallas que no se solapen dentro de un área, y colocar siete
grupos de mandos que no se solapen dentro de un área, son el mismo problema.
`LayoutEngine` lo resuelve una vez y lo consumen `ScreenLayout` y
`ControlLayout`.

Esto tiene una consecuencia agradable hacia fuera: el editor de posición de
controles que `app-emulador` tiene como funcionalidad prioritaria puede
escribirse **aquí primero**, con el problema completo delante, y portarse
después. Ojo con la dirección de la licencia: de aquí hacia allá sólo si el
código sigue siendo enteramente propio (ver [`licencia.md`](licencia.md)).

### Todo lo que se hereda sin discusión

Estas decisiones ya están tomadas, probadas en dispositivo y documentadas en
`app-emulador`. Se traen tal cual y no se vuelven a discutir:

- **La identidad de una ROM es el SHA-256 de su contenido**, nunca el nombre.
  `saves/<sha256>/` es la raíz del aislamiento entre juegos.
- **Las ROMs no se copian**: `Uri` del SAF con permiso persistente, y detección
  de archivo desaparecido comparando contra `persistedUriPermissions`.
- **Escritura atómica**: temporal → `fsync` → `rename`.
- **El save state es un contenedor `E4FS`**, no el volcado pelado, con identidad
  de ROM y de core y las nueve comprobaciones.
- **La ranura 0 es el respaldo automático** y la garantía es la forma de los
  métodos: `writeAutoBackup` no acepta número de ranura y `writeState` rechaza
  el 0.
- **`savedAt` decide qué es lo último**, nunca la fecha del sistema de archivos.
- **Sin `Dialog`, `Popup` ni `DropdownMenu`**: rompen el idioma elegido en la
  app.
- **Los ViewModel guardan `UiMessage`**, no texto traducido.
- **Cambio de idioma en caliente** con `ContextThemeWrapper`, no con
  `createConfigurationContext`, que rompe la cadena de `ContextWrapper` hasta
  la Activity.
- **Los mandos no son `clickable`**: `pressable` con `awaitEachGesture` por id
  de puntero.
- **Sin permisos en el manifiesto.**

El inventario completo, con qué se copia, qué se adapta y qué es nuevo, está en
[`reutilizacion.md`](reutilizacion.md).

### Qué ajuste va en Configuración y cuál en el menú de pausa

Los dos sitios escriben el **mismo** `EmulatorSettings`: no hay una copia «de
ajustes» de nada, y cambiar el filtro en Configuración cambia el del juego que
esté abierto. Lo que decide dónde aparece cada uno es cuándo se toca:

- **Menú de pausa** — lo que se cambia *con la partida delante*, porque el
  efecto sólo se juzga viéndolo: disposición de pantallas, velocidad, modo de
  toque, filtro, ranuras, trucos, cerrar la tapa. Obligar a salir del juego
  para cambiar la disposición a mitad de un combate convierte esa función en
  algo que nadie usa.
- **Configuración** — lo que se deja puesto *antes* de jugar y no depende de
  ninguna partida: idioma, pantalla de bienvenida, y las tres puertas a
  pantallas propias (editor de disposición, mando físico, acerca de).
- **En los dos** — filtro y modo de toque, a propósito: son el mismo valor, y
  hay quien los deja puestos de una vez y quien los ajusta jugando.

Lo que **no** entra en ninguno de los dos sigue siendo lo de siempre: no hay
ajustes decorativos. Cada fila de Configuración cambia algo que se nota, y la
que no tendría nada que graduar no se enseña —la distancia del lápiz sólo
aparece con el modo de toque en «Desplazamiento», porque en «Directo» no
ajustaría nada—.

## Dónde vive cada dato

| Dato | Dónde | Por qué |
|---|---|---|
| Biblioteca (metadatos, URI) | Room | Consultas, orden por último jugado, observable |
| Ajustes, idioma, disposiciones | DataStore | Escalares, con flujo observable |
| Partidas de batería | `filesDir/saves/<hash>/battery.sav` | Binario, privado del proceso |
| Save states + miniaturas | `filesDir/saves/<hash>/states/` | Contenedor `E4FS`, aislado por ROM |
| Portadas | `filesDir/covers/<hash>.webp` | **Extraídas del banner de la ROM**, no del usuario |
| BIOS y firmware | `filesDir/system/` | Copiados una vez desde SAF, validados por tamaño y hash |
| ROMs | **fuera**, referenciadas por URI | No se copian |

Dos diferencias con `app-emulador` en esta tabla:

- **Las portadas ya no las pone el usuario.** La cabecera de una ROM de DS lleva
  un banner con icono de 32×32 y título en varios idiomas (offset `0x68`). La
  biblioteca puede mostrar el icono y el nombre real sin que nadie haga nada.
  Sigue teniendo sentido permitir sustituirla a mano, pero deja de ser el único
  camino.
- **`filesDir/system/`** existe si el core elegido pide BIOS y firmware. Es el
  único caso en el que la app **copia** un archivo del usuario en vez de
  referenciarlo: son unos pocos KB, se leen en cada arranque del core, y
  depender de un `Uri` que puede desaparecer para algo sin lo cual la app no
  funciona sería frágil.

## Ciclo de vida

Igual que en `app-emulador`, sin cambios:

- `EmulatorController` es único en la aplicación: la sesión sobrevive a
  rotaciones y no puede haber dos hilos de emulación vivos.
- `GameScreen` observa el ciclo de vida: en `ON_STOP` pausa **y vuelca la
  partida**.
- El volcado al salir cuelga de un `CoroutineScope` de ámbito de aplicación, no
  de `viewModelScope`.
- `stop()` no bloquea: el propio hilo libera el core al salir del bucle.
- Salir del juego **para el emulador antes de navegar**.
- El menú de pausa es modal y pausa el controlador de verdad.
