# iOS y iPadOS

Plan para llevar DsForEveryone a iPhone y iPad desde este mismo repositorio.
Estado: **iOS-4 en curso.** En el iPad se juega: biblioteca, ajustes, copias
de partidas y la pantalla de juego con el core, el sonido, los mandos en
pantalla y las ranuras. Faltan los mandos físicos. El
detalle de cada fase, más abajo.

## La decisión: Kotlin Multiplatform, en este repositorio

Hay dos caminos y se elige el primero:

| | Kotlin Multiplatform + Compose Multiplatform | App nativa en Swift/SwiftUI |
|---|---|---|
| Geometría (`ControlLayout`, `ScreenLayout`, `TouchRouter`) | Se comparte tal cual, con sus pruebas | Se reescribe y se vuelve a probar |
| Partidas, ajustes, biblioteca | Se comparten | Se reescriben |
| Pantallas (juego, biblioteca, editor, ajustes) | Se comparten casi enteras | Se reescriben |
| Qué se escribe para iOS | Lo que toca el sistema: hilo, audio, vídeo, archivos, vibración, mandos | Todo |
| Dos apps que mantener | No | Sí |

Lo que más costó en Android —dos pantallas colocables, la ruta táctil, doce
botones en poco sitio y el editor— es geometría pura, **fuera de Compose a
propósito** (ver `CLAUDE.md`). Esa decisión es la que ahora hace barato
compartirla: no depende de nada de Android.

Por qué no un repositorio aparte, como se decidió para 3DS: aquel era otro
emulador con otro pipeline gráfico; esto es la **misma app** en otro sistema.
Separarla duplicaría justo lo que se quiere compartir.

## Lo que cambia en iPhone y iPad

### Sin JIT

Apple no deja a una app normal escribir y luego ejecutar código, que es lo
que hace un JIT. En Android el core usa el recompilador ARM64 de melonDS
(`ENABLE_JIT=ON`); en iOS va con el **intérprete**.

- En iPhones recientes y en iPads con chip M, la DS con intérprete es
  jugable (Delta, en la App Store, usa melonDS así).
- En modelos antiguos, y en general a 1,5x y 2x, se notará.
- Por eso la fase iOS-0 es **medir antes de construir** nada encima.

### Las piezas nativas

| Pieza | Android | iOS |
|---|---|---|
| Core | `libcore.a` con JIT | `libcore.a` sin JIT (ya compila, ver iOS-0) |
| Puente | `ds_bridge.cpp` con JNI | El mismo núcleo en C, llamado por `cinterop` |
| Audio | AAudio | Audio Unit (`AVAudioEngine` o `AURemoteIO`) |
| Vídeo | GL ES, textura 256×384 y dos quads | Compose `Canvas` con la imagen 256×384 y `FilterQuality` (ver abajo) |
| Hilo de emulación | `Thread` + `LockSupport.parkNanos` | Hilo POSIX + `mach_absolute_time`/`nanosleep` |
| ROMs y archivos de sistema | SAF (`DocumentAccess`) | `UIDocumentPickerViewController` + marcadores con ámbito de seguridad |
| Vibración | `HapticFeedback` | `UIImpactFeedbackGenerator` |
| Mandos físicos | `KeyEvent`/`MotionEvent` | Framework `GameController` |
| Micrófono | Soplido simulado | Soplido simulado (igual: sin permiso) |

**El vídeo.** En Android se pinta con GL ES porque el truco de
`setFixedSize` no servía con dos pantallas (ver `docs/pantallas.md`). En iOS,
OpenGL ES está obsoleto y Compose Multiplatform ya pinta con Metal por debajo.
El primer intento es el más sencillo: copiar el búfer 256×384 a una imagen y
dibujar sus dos mitades en los dos rectángulos de `ScreenLayout` con
`FilterQuality.None` («Nítido») o `Low` («Suavizado»). Son 393 KB por
fotograma; si eso no llega a 60 fps, el plan B es una `MTKView` dentro de un
`UIKitView` con dos quads, el mismo diseño que el renderizador GL.

**Las reglas invariantes no cambian.** Un hilo propio, el core tocado desde
un solo hilo, la entrada táctil como estado compartido, escritura atómica de
partidas, SHA-256 como identidad: todo eso vive en código común y vale igual.
Lo que cambia es sólo con qué primitiva del sistema se cumple cada una.

### Particularidades del iPad

- **Apple Pencil = lápiz de la DS.** No hace falta código propio: llega
  como un puntero más y `TouchRouter` lo trata igual que un dedo.
- **Ventanas de cualquier tamaño** (Split View, Slide Over, Stage Manager).
  La geometría ya calcula sobre el hueco disponible, pero hay que probar el
  cambio de tamaño en caliente: el perfil de mandos y la colocación se
  recalculan, y el hilo de emulación no se puede parar por ello.
- **Teclado físico** además de mandos: entra por `GameController` (`GCKeyboard`).

### Lo que no se puede hacer igual

- **`filesDir` y la exportación de partidas**: en iOS son la carpeta
  `Application Support` de la app y la hoja de compartir del sistema.
- **«Sin permisos»** se mantiene: el selector de documentos no pide permiso, y
  no se declara `NSMicrophoneUsageDescription`.

## Licencia y distribución: lo delicado

- **App Store.** Apple admite emuladores desde 2024. Pero sus condiciones
  añaden restricciones de uso que la GPL prohíbe añadir; por eso VLC tuvo que
  salir de la tienda en 2011. Publicar melonDS (GPL-3.0) en la App Store
  exige **antes** confirmarlo con los titulares del copyright de melonDS. Lo
  mismo vale para TestFlight, que pasa por las mismas condiciones. Pendiente
  también en `docs/licencia.md`.
- **Sin tienda.** Instalar desde Xcode en un dispositivo propio no tiene ese
  problema. Con una cuenta de Apple gratuita la app caduca a los 7 días; con
  la cuenta de desarrollador (99 USD al año), al año.
- **Unión Europea.** Las tiendas alternativas (AltStore PAL y otras) son una
  vía distinta de la App Store, con sus propias condiciones que también hay
  que revisar.

Mientras eso no esté resuelto, el objetivo es una app que funcione en
dispositivos propios, no una publicación.

## Fases

Mismo método que [`plan-de-trabajo.md`](plan-de-trabajo.md): una rama por
fase y cada fase deja Android funcionando igual que antes. **Android no se
rompe nunca por el camino**: cada paso se comprueba con
`bash gradlew testDebugUnitTest assembleDebug` además de lo propio de iOS.

### iOS-0 · ¿Va lo bastante rápido? (medir antes de construir)

1. ✅ **El core compila para iPhone.** `third_party/melonDS` sin tocar,
   `arm64`, iOS 16, sin JIT, sin renderizador OpenGL, sin GDB stub: produce
   `libcore.a` en unos 10 s. Comando en el apéndice.
2. ✅ Una app mínima en Xcode, sin Kotlin todavía:
   [`ios/spike/`](../ios/spike/README.md). Carga una ROM con la BIOS libre de
   melonDS (`FreeBIOS`), corre el core en su propio hilo con o sin límite de
   60 Hz y muestra fotogramas por segundo, milisegundos por fotograma y
   porcentaje de velocidad de una DS. Compila y enlaza; falta correrla en un
   dispositivo.
3. Medir en un iPhone y en un iPad reales, con una demo 2D y una 3D.

   **Primera medición (2026-11-09), iPad Air 11" (M2), iPadOS 26, intérprete
   sin JIT, compilación Release:**

   | Modo | fps | ms por fotograma | Margen sobre una DS |
   |---|---|---|---|
   | Sin límite | 165 | 5,6 | 434 % |
   | 60 Hz | 60 | 6,5 | 300 % (ver nota) |

   - El core va a más de **4 veces** la velocidad de una DS: sobra para x1, x1,5
     y x2 sin JIT, al menos en un M2.
   - A 60 Hz el fotograma tarda algo más (6,5 ms frente a 5,6) porque el
     procesador baja de frecuencia entre fotograma y fotograma. Es lo
     esperable y no es un problema: sigue siendo un 39 % del presupuesto.
   - Medido con *Zelda: Spirit Tracks*, un juego **3D** (el caso exigente:
     el renderizador 3D por software corre en su propio hilo). Un 2D irá
     igual o mejor.
   - Falta probar un iPhone, sobre todo uno sin chip de la serie Pro.

   **Decisión:** seguir. Sin JIT, el iPad M2 tiene margen de sobra.

**Está hecho cuando** hay cifras de fotogramas por segundo por dispositivo y
una decisión escrita aquí: seguir, o seguir sólo para iPad / modelos recientes.

### iOS-1 · El puente, libre de Android

`ds_bridge.cpp` mezcla tres cosas: el uso de melonDS, JNI y AAudio. Se parte en:

- `ds_core.h/.cpp`: una API en C pura (`ds_load`, `ds_run_frame`,
  `ds_set_keys`, `ds_set_touch`, `ds_save_state`…), sin JNI ni Android. Aquí
  van los `Platform::*` de melonDS, el micrófono y el anillo de audio.
- `ds_jni.cpp`: sólo traduce JNI a esa API.
- `audio_aaudio.cpp` y, más adelante, `audio_ios.mm`, detrás de una interfaz
  pequeña.

**Está hecho cuando** Android funciona exactamente igual, con las mismas
pruebas, y `ds_core` compila también con el SDK de iOS.

**Estado (2026-11-09):** hecho y probado en un Android real (imagen,
sonido, silencio a x1,5/x2, mandos, táctil, soplido, save states y
batería) y en el iPad con `ios/spike/`.

- `native/ds_core/` (`ds_core.h`, `ds_core.cpp`, `CMakeLists.txt`): la API
  en C. El nombre de la API cambia (`ds_load`, `ds_run_frame`…), la lógica
  no: los bloques de `Platform`, el anillo de audio, la carga y los trucos se
  movieron tal cual, con sus comentarios.
- `native/emulator_bridge/ds_bridge.cpp`: sólo JNI. Las mismas 19 funciones
  nativas, exportadas igual (comprobado con `llvm-nm` sobre `libdsemu.so`).
- `native/emulator_bridge/audio_aaudio.cpp`: AAudio, que llama a
  `ds_audio_read`. El silencio al silenciar y el relleno cuando la emulación
  no llega están en `ds_core`, compartidos.
- `verifyCoreVersion` busca ahora la constante en `ds_core.cpp.o`.
- `ios/spike/` usa `ds_core` en lugar de su copia de `Platform`, que se ha
  borrado: el mismo `ds_core.cpp` compila para Android con JIT y para iOS
  sin él.
- `bash gradlew testDebugUnitTest assembleDebug` en verde.

### iOS-2 · El módulo compartido

Nuevo módulo Gradle `:shared` con Kotlin Multiplatform (`commonMain`,
`androidMain`, `iosMain`). Se mueve primero lo que no toca nada de Android:

- `emulator/layout/*`, `DsButton`, `JoystickGeometry`, `DirectionalPadZones`.
- El contenedor de save states, `RomIdentity`, `DsRomHeader`, `DsBanner`.
- `UiMessage` y lo que no dependa de `Context`.
- Sus pruebas pasan a `commonTest` con `kotlin.test` y corren en JVM y en el
  simulador de iOS.

**Está hecho cuando** `:app` usa `:shared`, todas las pruebas pasan y las de
geometría corren también en iOS.

**Estado (2026-11-09): primer paso hecho**, la geometría.

- Módulo `shared/` con Kotlin Multiplatform y el plugin
  `com.android.kotlin.multiplatform.library` de AGP 9. Destinos: Android,
  `iosArm64` (dispositivos) e `iosSimulatorArm64`.
- Movidos con `git mv` (conservan su historia y su paquete, así que `:app`
  no cambia ni un import): `emulator/layout/*` (`ControlLayout`,
  `LayoutEngine`, `ScreenLayout`, `TouchRouter`), `DsButton`,
  `JoystickGeometry` y `DirectionalPadZones`.
- El único Java que había en ese código era un `Math.toDegrees`; ahora es
  `* (180 / PI)`, la misma cuenta.
- Sus siete archivos de pruebas, de JUnit a `kotlin.test`: el mensaje pasa
  del primer argumento al último, `@Test(expected = …)` pasa a
  `assertFailsWith`, y las comas de los nombres pasan a «—» (Kotlin/Native no
  las admite). **108 pruebas, las mismas, en verde en la JVM y en el
  simulador de iOS.**
- `bash gradlew testDebugUnitTest assembleDebug` sigue en verde.

**Segundo paso (2026-11-09): hecho**, ROMs y partidas.

- A `commonMain`, con `git mv`: `DsRomHeader`, `DsBanner`, `RomInfo` y
  `SaveStateContainer`. `crc16` pasa de `internal` a pública porque las
  pruebas de `:app` la usan y `internal` no cruza módulos.
- **SHA-256 y CRC32** (`data/hash/Hashes.kt`) con `expect`/`actual`:
  `MessageDigest` y `java.util.zip.CRC32` en Android, CommonCrypto y zlib en
  iOS. No se reescriben en Kotlin común porque serían más lentos justo donde
  importa: el alta de una ROM recorre cientos de MB.
- **`RomFingerprinter`** (común): el recorrido de `RomIdentity.read` —hash,
  cabecera y banner en una pasada—, alimentado por bloques. `RomIdentity`
  se queda en `:app` sólo para leer el `InputStream` de SAF.
- **`SaveStateContainer`**: `DataInputStream`/`DataOutputStream` pasan a un
  lector y un escritor big-endian propios, con la misma semántica. El
  formato no cambia ni un byte: `GoldenSaveStateTest` sigue leyendo el
  archivo congelado.
- Pruebas: `SaveStateContainerTest` (los archivos estropeados a mano) pasa a
  `commonTest`, y hay un `HashesTest` nuevo con vectores de referencia
  (FIPS 180-2 para SHA-256, `0xCBF43926` para CRC32) que comprueba que las
  dos plataformas dan lo mismo. **137 pruebas en verde en la JVM y en el
  simulador de iOS**; `:app`, 207 en verde.
- Se quedan en `:app` (en la JVM) las pruebas de cabecera, banner e
  identidad: dependen de `DsRomFixtures`, que usan otras siete pruebas de
  `:app`, y las pruebas de un módulo no se ven desde otro. Siguen probando
  el código ya movido.

Queda para iOS-3: `UiMessage` depende de los recursos de Compose para
Android y se mueve con los textos.

### iOS-3 · Las dependencias, multiplataforma

| Hoy | Mañana | Nota |
|---|---|---|
| Hilt | **Koin** (hecho) | Hilt no existe fuera de Android |
| Room | Room KMP | Room ya soporta iOS; mismo esquema |
| DataStore | DataStore KMP | Mismo formato de preferencias |
| ViewModel, Navigation | Sus versiones KMP de JetBrains | |
| Compose + Material 3 | Compose Multiplatform | Iconos y tipografía incluidos |
| Recursos `res/values*/strings.xml` | Recursos de Compose Multiplatform | Siguen bilingües, español por defecto |

Con eso las pantallas pasan a `commonMain`. Detrás de `expect`/`actual`
quedan sólo: `EmulatorCore`, el hilo de emulación, `DocumentAccess`, el
renderizador, la vibración y los mandos físicos.

**Está hecho cuando** Android sigue igual y la app de iOS arranca, muestra
la biblioteca vacía y navega por los ajustes, en el simulador.

Se hace en cinco pasos; tras cada uno Android sigue igual:

1. **Inyección de dependencias** — ✅ (2026-11-09). Hilt sustituido por
   Koin 4.2.2 en `:app`: `di/AppModule.kt` es un `module { }` con las mismas
   construcciones; los ViewModels se declaran con `viewModel { }` y las
   pantallas los piden con `koinViewModel()` (el de `koin-compose-viewmodel`,
   el mismo que se usará en `commonMain`); `GameViewModel` sigue recibiendo
   el `SavedStateHandle` de su entrada de navegación. Hilt comprobaba las
   dependencias al compilar y Koin no: `AppModuleTest` verifica el módulo
   entero en la JVM (comprobado que falla si se quita una definición).
2. **Datos**: Room KMP, DataStore KMP y los repositorios a `:shared`.
   - ✅ **Base de datos y ajustes** (2026-11-09). `DsEmuDatabase`, `RomDao`
     y `RomEntity` a `commonMain` con Room KMP (`@ConstructedBy`; KSP genera
     el código para Android, `iosArm64` e `iosSimulatorArm64`). El esquema
     pasa a `shared/schemas/` sin cambiar un byte. En Android se sigue usando
     el SQLite del sistema; `sqlite-bundled` sólo entra en iOS.
     `SettingsRepository` y `EmulatorSettings` a común: recibe el DataStore
     ya abierto (en Android, el mismo archivo
     `datastore/emulator_settings.preferences_pb` de siempre) y una función
     con el idioma del sistema. `GamepadMapping` y `GamepadTarget` también,
     con los códigos de tecla de `KeyEvent` copiados como números en
     `GamepadKeyCodes` (GamepadKeyCodesTest comprueba en `:app` que
     coinciden).
   - ✅ **Archivos, partidas y trucos** (2026-11-09), con Okio 3.9.1:
     `StorageManager`, `BatterySaveManager`, `SaveStateManager`,
     `SaveManager`, `CheatsManager` (+ DTO y `CheatEntry`) y `DsVideo` a
     `commonMain`. `StorageManager` da rutas de Okio (`batteryPath`…); en
     Android, `StorageFiles.android.kt` conserva los nombres de antes
     (`batteryFile`…) devolviendo `File`, para el código que sigue siendo sólo
     de Android y para las pruebas de `:app`. Es un puente temporal.
     - **Escritura atómica:** Okio no sirve tal cual. Su `FileHandle.flush()`
       hace `fsync` en la JVM pero sólo `fflush` en iOS (comprobado en sus
       fuentes 3.9.1), y sin `fsync` el `rename` puede llegar al disco antes
       que los datos. Por eso el temporal se escribe con `writeDurably`, una
       por plataforma: en Android, el `FileOutputStream` + `fd.sync()` de
       siempre; en iOS, POSIX con `F_FULLFSYNC` (en Apple, `fsync` deja los
       datos en la caché de la unidad). Okio hace el resto: carpetas y
       `atomicMove`.
     - La miniatura de las ranuras pasa a un `ThumbnailEncoder` inyectado
       (en Android, `Bitmap` → PNG como antes). `android.util.Log` pasa a
       `AppLog` (logcat en Android, `NSLog` en iOS).
     - `StorageOnDiskTest` (común, sobre una carpeta temporal real) ejecuta
       la escritura atómica, `StorageManager` y una ida y vuelta de un save
       state **también en el simulador de iOS**. 143 pruebas en verde en la
       JVM y en iOS; `:app`, 209.
   - Pendiente: el zip de exportar/importar partidas (`SaveArchive`) y de
     las ROMs comprimidas (`RomZipReader`), que no tiene equivalente común;
     las portadas (`CoverRepository`, `Bitmap`); y
     `RomRepository`/`DocumentAccess` (SAF).
3. **Textos** — ✅ (2026-11-09). Los 282 de `strings.xml` (es/en) a
   `shared/src/commonMain/composeResources/`, con Compose Multiplatform
   1.12.2 (sólo runtime y recursos, todavía). `Res` se genera pública en
   `com.moralesDev.dsForEveryone.resources`. 313 usos en 21 archivos de
   `R.string.x` a `Res.string.x`; `UiMessage` guarda un `StringResource` en
   vez de un `@StringRes Int`. Tres cosas que no eran obvias:
   - Compose Multiplatform sólo entiende `\n`, `\t` y `\uXXXX`: los `\'` y
     `\"` de Android (20 textos en inglés) saldrían tal cual. Se pasaron a
     `'` y `"` sin barra.
   - Su `stringResource` no lee `LocalConfiguration`, sino `Locale.current`
     (el idioma del proceso), y no recompone al cambiarlo.
     `ProvideAppLanguage` hace además `Locale.setDefault` con el idioma de la
     app y rehace la interfaz con una `key` por idioma; el `NavController`
     sube a `MainActivity` para que la pantalla actual sobreviva. En iOS
     habrá que hacer lo equivalente con `AppleLanguages`.
   - El módulo `com.android.kotlin.multiplatform.library` trae los recursos
     de Android apagados, y sin `androidResources { enable = true }` los
     textos **no entran en el APK** (compila y las pruebas pasan igual).
     Comprobado abriendo el APK: `assets/composeResources/…` con los dos
     idiomas.
4. **Pantallas** a `commonMain`; vídeo, mandos físicos, archivos y
   vibración detrás de `expect`/`actual`.
   - ✅ **Tema, componentes y pantallas simples** (2026-11-09): `ui/theme`,
     `ui/components` (AppLogo, LoadingVeil, Nocturne), splash, bienvenida,
     Acerca de, `ScreenTransitions` y `UiMessage`, con Compose
     Multiplatform: foundation/ui/animation 1.12.2, Material 3 1.9.0 (la
     que fija el plugin 1.12.2) y `material-icons-extended` 1.7.3 (congelada
     por JetBrains, pero sirve en iOS y deja `Icons.Filled.X` igual). La
     fuente Inter pasa a `composeResources/font/`; como cargarla es
     composable, `Inter` y `DsEmuTypography` son propiedades `@Composable`.
     Acerca de abre los enlaces con `LocalUriHandler` en vez de un `Intent`;
     `System.nanoTime()` pasa a `TimeSource.Monotonic`. Lo `internal` de
     esos archivos pasa a público mientras haya pantallas en `:app` que lo
     usen.
   - ✅ **Copia de partidas** (2026-11-09): `SaveArchive` y
     `SaveArchiveCoordinator` a común, con un zip propio sobre Okio
     (`data/zip/ZipWriter`, `ZipArchiveReader`) en lugar de `java.util.zip`,
     que no existe en iOS. Ver `docs/copia-de-partidas.md`. Reciben
     `Sink`/`Source` de Okio; en Android, los flujos del selector se envuelven
     con `.sink()`/`.source()`. 28 pruebas de `SaveArchiveTest` en verde con el
     zip nuevo; `ZipInteropTest` contra `java.util.zip` en las dos direcciones;
     `ZipRoundTripTest` también en el simulador de iOS.
   - ✅ **Ajustes** (2026-11-09): `SettingsScreen` y `SettingsViewModel` a
     común. Lo que tenían de Android:
     - Los selectores de archivos pasan a `ui/files/DocumentPickers`
       (`rememberCreateDocument`, `rememberOpenDocument`, que devuelven un
       `PickedDocument` del que abrir un `Sink`/`Source`). En Android, SAF
       como antes. **En iOS están pendientes** (no hacen nada y lo dicen en
       el registro): exportar allí funciona al revés —se escribe el archivo y
       luego se ofrece con `UIDocumentPickerViewController`—, y se hará con el
       arranque de la app (paso 5), cuando se pueda probar.
     - `RomRepository` (sólo para los títulos al exportar) pasa a una
       interfaz pequeña, `GameTitles`, que en Android da `RomRepository`.
     - `ViewModel`, `collectAsStateWithLifecycle` y `koinViewModel` en sus
       versiones multiplataforma (Lifecycle de JetBrains 2.11.0, la misma
       que la de AndroidX; Koin en `:shared`). `LocalDate.now()` pasa a
       `kotlinx-datetime` 0.7.1.
   - ✅ **Mandos en pantalla y hojas del juego** (2026-11-09):
     `ui/game/controls/*` (botones fantasma, cruceta, joystick, rombo,
     `pressable`), `SaveStateSheet`, `PauseMenu`, `CheatsSheet`,
     `StateRejectionText`, `PhysicalUnits` y `SlotAction`/`SlotConfirmation`
     (sacados de `GameViewModel`). Lo que tenían de Android:
     - La vibración de los mandos pasa a `LocalHapticFeedback` con
       `HapticFeedbackType.VirtualKey`, que en Android es el mismo
       `VIRTUAL_KEY` de la vista.
     - La miniatura de las ranuras se lee con Okio y se decodifica con
       `decodeToImageBitmap()` de Compose Multiplatform, en vez de
       `BitmapFactory`.
     - `rememberPxPerMm()`, una por plataforma: `xdpi`/`ydpi` en Android; en
       iOS, que no da los píxeles por pulgada, puntos por pulgada del tipo de
       aparato (163 iPhone, 132 iPad) por la escala. Aproximado.
     - `rememberSaveTimeFormatter()`, una por plataforma: `java.time` en
       Android (como antes), `NSDateFormatter` en iOS. `LocalSystemLocale`
       pasa a `:shared` (androidMain), mismo paquete.
     - `CheatsCoordinator` se queda en `:app`: habla con `EmulatorController`.
   - ✅ **Editor de disposición** (2026-11-09): `LayoutEditorScreen` y su
     ViewModel. La orientación pasa a `rememberIsPortrait()`, una por
     plataforma: en Android, la `Configuration` de siempre (también la usa ya
     `GameScreen`); en iOS, alto contra ancho de la ventana, que sirve
     también en Split View.
   - El **mapeo de mandos físicos** espera al sistema de mandos de iOS
     (GameController): depende de `KeyEvent`, `GamepadManager` y
     `GamepadInputRouter`, que son el de Android.
   - Pendiente: biblioteca, juego, mapeo de mandos, navegación y el idioma.
5. **Arranque en iOS**: la app abre en el simulador.
   - ✅ **Arranca** (2026-11-09), en el simulador de iPad. Antes, en el
     paso 4, la navegación y el idioma pasaron a común: `AppNavHost` (con
     Navigation de JetBrains 2.9.2 y `ui-backhandler`), `MainViewModel`,
     `ProvideAppLanguage` (una por plataforma: en iOS, `AppleLanguages` y una
     `key`) y `App()`, que es lo que pintan `MainActivity` y el arranque de
     iOS. Las pantallas aún no comunes (biblioteca, juego, mapeo de mandos)
     son huecos de `PlatformScreens`: Android pone las suyas; iOS, un aviso.
   - `shared` produce el framework dinámico `Shared`. En `iosMain`:
     `MainViewController()` y su módulo de Koin (`di/IosModule.kt`): ajustes,
     Room con `BundledSQLiteDriver` y partidas en `Application Support/files/`.
   - La app de Xcode está en `ios/app/` (CMake, como `ios/spike/`; ver su
     README). Dos cosas que no eran obvias: Gradle se lanza sin el perfil de
     la terminal, así que hay que darle `JAVA_HOME`; y los recursos de Compose
     hay que copiarlos a `DsForEveryone.app/compose-resources/` —sin ellos
     falla al cargar la fuente—, con un script que use las variables de Xcode
     (`TARGET_BUILD_DIR`), porque CMake no expande `EFFECTIVE_PLATFORM_NAME`.
   - ✅ Probada en un iPad Air M2. Logo nuevo (`art/logo.png`,
     `tools/make-icons.swift`): icono en Android e iOS y `AppLogo` dentro de
     la app.
   - ✅ **Selectores de archivos de iOS** (`DocumentPickers.ios.kt`,
     `UIDocumentPickerViewController`). Abrir lee el archivo en su sitio, sin
     copiarlo, con su permiso de acceso («security-scoped»). Crear elige una
     **carpeta** y crea dentro el archivo con el nombre propuesto (« (2)»… si
     existe): así funciona como en Android, primero se elige dónde y después
     se escribe. Pendiente de probar con una copia de partidas real (importar
     en el iPad una exportada en Android).
   - ✅ **Biblioteca, en común** (2026-11-09), en tres pasos:
     1. `RomLibrary` (interfaz común: listar, importar un `PickedDocument`,
        renombrar, quitar, marcar jugado) y `RomImportResult`. En Android la
        implementa `RomRepository` sin cambiar su lógica: la entrega de la ROM
        al core (descriptores, `mmap`, lo aprendido de los `OutOfMemoryError`)
        no se toca. `PickedDocument.reference`: el `Uri` en Android, la URL en
        iOS.
     2. Portadas: `CoverImage` a común (EXIF como los números del estándar),
        `PickedImage` con un `ImageBitmap`, y la interfaz `CoverStore`, que en
        Android implementa `CoverRepository` igual que antes.
        `rememberPickImage`: en Android, el selector de fotos; en iOS, por
        ahora, el de archivos filtrado a imágenes.
     3. `RomLibraryScreen`, `RomLibraryViewModel` y `CoverSheets` a común,
        con los selectores comunes. Lo que escribía `java.time` y
        `String.format` pasa a `ui/Formats` (una por plataforma), el icono del
        cartucho a `argbImageBitmap` (Bitmap en Android, Skia en iOS) y la
        portada se lee con Okio + `decodeToImageBitmap`.
   - ✅ **Biblioteca en iOS** (2026-11-09). `IosRomLibrary`: la ROM no se
     copia; se guarda un marcador («bookmark») de la URL del selector en
     `documentUri` (`bookmark:` + base64) y se resuelve para leerla en su
     sitio; si deja de resolverse, el juego sale no disponible. `.zip` con las
     reglas de Android (una `.nds`, 1 GB como mucho), leído con el zip común
     sobre una copia temporal. `IosCoverStore`: abre con `UIImage` (entiende
     HEIC y aplica la orientación) y recorta, escala y codifica en WebP con
     Skia. Probado en un iPad Air M2: añadir ROMs, que sigan ahí al
     reiniciar, portadas.
   - Pendiente: el juego; el selector de la fototeca (PHPicker) para las
     portadas.

### iOS-4 · El juego en iOS

**Paso 1 — ✅ el core llega a Kotlin/Native** (2026-11-09). Las tareas
`configureDsCore*`/`buildDsCore*` de `shared/build.gradle.kts` compilan
`native/ds_core` (con melonDS, sin JIT) con CMake para `iosArm64` e
`iosSimulatorArm64`: `libds_core.a`, `libcore.a` y `libteakra.a`. El
`cinterop` `dsCore` (`shared/src/nativeInterop/cinterop/dsCore.def`) genera
los enlaces a `ds_core.h` en el paquete
`com.moralesDev.dsForEveryone.emulator.core.native`, mete las tres
bibliotecas en el framework y enlaza `libc++`. CMake con ruta absoluta,
porque Xcode lanza Gradle sin el `PATH` de Homebrew. `DsCoreInteropTest`
(iosTest) carga una ROM mínima y corre 60 fotogramas en el simulador.
Descubierto de paso: melonDS acepta como cartucho hasta 16 bytes de basura
(rellena lo que falta); en la app no importa, porque la biblioteca valida la
cabecera antes.

**Paso 2 — ✅ `EmulatorCore`, `EmulatorController` e `InputManager` en
común** (2026-11-09). Las reglas de CLAUDE.md siguen igual: hilo propio,
reloj absoluto, el core desde un solo hilo, comandos en cola, la entrada como
estado compartido. Lo que da cada plataforma:

- `EmulationThread` (hilo, `parkNanos`/`unpark` con semántica de
  `LockSupport`, `join`): en Android, `Thread` + `URGENT_DISPLAY` +
  `LockSupport`, como antes; en iOS, `NSThread` con calidad de servicio
  interactiva y un `NSCondition`.
- `perFrame { }`: cada vuelta del bucle; en iOS dentro de un
  `autoreleasepool` (la lección de iOS-0).
- `FrameBuffer`: el `ByteBuffer` directo de siempre en Android; un
  `ByteArray` en iOS.
- En común: el reloj (`TimeSource.Monotonic`), la cola de comandos (un
  `Channel` sin límite, `trySend`/`tryReceive`) y los atómicos
  (`kotlin.concurrent.atomics`). En pausa el hilo atiende los comandos y
  duerme 50 ms o hasta que llega otro; antes un `resume()` podía tardar hasta
  50 ms en notarse, ahora despierta al momento.
- Cargar desde un descriptor de archivo es sólo de Android
  (`DescriptorLoadingCore`, `openFromDescriptor` como extensión). Fuera
  `attachSurface`: ningún core la usaba.
- `EmulatorControllerTest` (29 pruebas, `:app`) prueba el controlador común
  sobre las primitivas de Android; `EmulatorControllerThreadTest` (común)
  arranca, corre, atiende un comando en pausa y para, también en el
  simulador de iOS.

**Paso 3 — ✅ el core, el sonido y la pantalla de juego en iOS**
(2026-11-09, probado en un iPad Air M2 con Pokémon SoulSilver: imagen,
sonido, táctil, mandos, ranuras con miniatura, giro y pausa al salir).

- `native/ios/` es el proyecto CMake que compila Gradle para iOS:
  `ds_core` más `ds_audio_ios.c`, una `AudioUnit` RemoteIO a 48 kHz, estéreo
  de 16 bits, cuyo *callback* pide las muestras a `ds_audio_read`. En C y no
  en Kotlin: el *callback* corre en el hilo de tiempo real del sistema, donde
  no se puede reservar memoria ni bloquear. `dsCore.def` enlaza además
  `AudioToolbox`.
- `IosEmulatorCore` es el `EmulatorCore` de iOS sobre la API C de
  `ds_core.h`. `IosEmulatorCoreTest` lo prueba solo (cargar, correr, copiar
  el fotograma, guardar y restaurar un estado, trucos con la línea mala) y
  dentro del controlador con su hilo.
- `GameViewModel`, `GameScreen` y `CheatsCoordinator` son comunes. Lo que
  cambia por plataforma entra por tres huecos:
  - `GameRomOpener`, entregar la ROM al core: en Android, el descriptor de
    siempre (`AndroidGameRomOpener`); en iOS, `IosRomLibrary.readForCore`
    abre el marcador y lee los bytes, descomprimiendo el `.zip` si hace
    falta. En iOS la ROM entra entera en memoria: no hay el límite del heap
    de Java que obligó al descriptor.
  - `GamepadSource`, los mandos físicos: `AndroidGamepadSource` sobre
    `GamepadManager` y `GamepadInputRouter`; en iOS, `NoGamepads` hasta el
    paso 5.
  - `GameVideo`: GL ES con `DsGLRenderer` en Android, sin cambios; en iOS,
    Skia, el mismo lienzo de Compose: un `Bitmap` propio al que se copia el
    fotograma con `memcpy` una vez por refresco y que se pinta en los dos
    rectángulos, con `FilterQuality.None` para «Nítido» y `Low` para
    «Suavizado». `FreeOrientationWhileGamepadConnected` no hace nada en iOS:
    `Info.plist` ya deja girar el iPad.
- Las miniaturas de las ranuras, con Skia (`SkiaThumbnailEncoder`).
- Lo que enseñó el iPad: en vertical, con su pantalla casi 4:3, las dos
  pantallas de la DS ocupan casi todo el alto y Select y Start no caben
  debajo. Antes se quedaban en mitad de la pantalla inferior; ahora van lo
  más abajo posible (`ControlLayout.overlayProfile`).
  En horizontal, un racimo en cada esquina y L y R encima de las pantallas
  (ver [`entrada.md`](entrada.md)).

**Paso 4 — mandos físicos** con `GameController` y la pantalla de mapeo
común. Pendiente.

**Lección de iOS-0 para el hilo de emulación:** un hilo propio no tiene
bucle de eventos que vacíe la reserva de *autorelease*. En la prueba, cada
copia del fotograma (384 KB) se quedaba viva: +22 MB por segundo e iOS
cerraba la app a los ~5 GB, a los cuatro minutos, sin dejar informe. Con un
`@autoreleasepool` por fotograma se queda plana en 174 MB (medido 5 minutos
con Spirit Tracks). En Kotlin/Native vale lo mismo: cualquier objeto de
Foundation que se cree en el hilo de emulación va dentro de
`autoreleasepool { }` en cada vuelta del bucle.

**Está hecho cuando** una ROM libre se juega en un iPhone real con sonido,
táctil, mandos en pantalla y partidas que se guardan y se cargan.

### iOS-5 · iPad y cierre

- Pencil, ventanas de tamaño variable, teclado y mandos físicos.
- Cambios de tamaño en caliente sin cortar la emulación.
- La decisión sobre distribución (ver arriba), documentada.

**Está hecho cuando** se juega en un iPad real en pantalla completa y en
Split View, y las dos disposiciones se recalculan sin perder el estado.

## Apéndice: compilar el core para iPhone

```sh
cmake -S third_party/melonDS -B build-ios -G "Unix Makefiles" \
  -DCMAKE_SYSTEM_NAME=iOS -DCMAKE_OSX_ARCHITECTURES=arm64 \
  -DCMAKE_OSX_DEPLOYMENT_TARGET=16.0 -DCMAKE_BUILD_TYPE=Release \
  -DBUILD_QT_SDL=OFF -DENABLE_JIT=OFF -DENABLE_OGLRENDERER=OFF \
  -DENABLE_GDBSTUB=OFF -DENABLE_LTO_RELEASE=OFF
make -C build-ios -j10 core
```

Comprobado con Xcode 26.6 y el SDK de iOS 26.5: `build-ios/src/libcore.a`,
arm64. Para el simulador hace falta además instalar su runtime en Xcode
(Settings → Components); en este Mac no está instalado todavía.
