# Plan de trabajo

Diecinueve entregables repartidos en cinco fases, más un prompt de cierre por
fase: veinticuatro prompts en total. Cada entregable cierra una pieza
comprobable y cabe en una sesión de Claude Code.

## Cómo usar esto

**`CLAUDE.md` ya está en la raíz del repositorio.** Claude Code lo lee solo al
arrancar, así que los prompts de abajo **no repiten** las invariantes, el idioma
ni las convenciones: sólo dicen qué hay que hacer esta vez. Si un prompt te
parece corto, es porque el contexto ya está puesto.

**Una rama por fase, un commit por entregable.** Cada fase abre su propia rama
de funcionalidad, todos sus entregables se van commiteando dentro de ella, y al
terminar la fase se integra a `develop`. La convención del proyecto hermano se
mantiene: **Claude Code sólo hace commits dentro de la rama de la fase; el
*merge* a `develop` lo haces tú a mano.**

| Fase | Rama |
|---|---|
| 0 · Cimientos | `feature/fase-0-cimientos` |
| 1 · Esqueleto | `feature/fase-1-esqueleto` |
| 2 · Geometría | `feature/fase-2-geometria` |
| 3 · Biblioteca y partidas | `feature/fase-3-biblioteca-partidas` |
| 4 · Core nativo | `feature/fase-4-core-nativo` |

Cada fase termina con un prompt de **cierre**, que no escribe funcionalidad
nueva: revisa lo hecho, deja el árbol limpio y prepara la rama para que la
integres. El siguiente prompt de fase se ejecuta ya sobre `develop` actualizado.

**No pases al siguiente entregable sin que el anterior esté en verde.** Cada uno
tiene un «Está hecho cuando» explícito. Si no se cumple, no está hecho, aunque
compile. Y no abras la rama de la fase siguiente sin haber integrado la
anterior: arrastrar dos fases abiertas a la vez es como se pierden los cambios.

**Los prompts son un punto de partida, no un guion.** Si al ejecutar uno
aparece algo que no estaba previsto, ese hallazgo vale más que el plan: anótalo
en la documentación antes de seguir.

---

# Fase 0 — Cimientos del repositorio

Sin esto, la primera línea de Kotlin ya nace con una deuda legal.

## 0.1 · Repositorio, licencia y ramas

Commit dentro de `feature/fase-0-cimientos`.

```
Este repositorio está vacío salvo por la documentación en docs/ y CLAUDE.md.
Lee CLAUDE.md y docs/licencia.md antes de empezar.

Prepara los cimientos del repositorio, sin código de aplicación todavía:

1. Deja la documentación que ya existe en un primer commit sobre `develop`, y
   crea esa rama si no existe: `develop` es el tronco real del proyecto.
   `master` se reservará para "lo que está publicado en la tienda ahora mismo",
   igual que en el proyecto hermano, y de momento no se toca.
   Después crea la rama `feature/fase-0-cimientos` a partir de `develop` y
   trabaja SÓLO en ella a partir de aquí. No integres nada tú: el merge a
   `develop` lo hago yo a mano.
2. Añade el fichero LICENSE con el texto completo de la GPL-3.0. Elegimos la
   más restrictiva de las dos candidatas a propósito: si el core que acabe
   ganando es DeSmuME (GPL-2.0-or-later) podremos relajarla, pero al revés no.
   Documenta ese razonamiento en un comentario del README.
3. Escribe el README.md en español, con: qué es el proyecto, que no incluye ni
   descarga ROMs, BIOS ni firmware, la licencia, un aviso explícito de que el
   nombre y el logotipo no se licencian con el código, y una nota de que las
   contribuciones externas se aceptan bajo GPL y sin cesión de derechos.
4. Añade un .gitignore de Android/Gradle que además excluya `third_party/`,
   `local.properties` y `keystore.properties`.
5. Crea las carpetas vacías `native/` y `third_party/` con un .gitkeep y un
   README de una línea cada una explicando qué va dentro.

No crees todavía el proyecto Gradle. Eso es el siguiente entregable.
```

**Está hecho cuando** la rama tiene LICENSE, README y `.gitignore`, y `git log`
muestra commits limpios.

## 0.C · Cierre de la fase 0

```
Cierra la fase 0. No escribas funcionalidad nueva.

1. Repasa que estén LICENSE, README.md, .gitignore y las carpetas native/ y
   third_party/ con su .gitkeep y su README de una línea.
2. Comprueba que el README dice explícitamente: que la app no incluye ni
   descarga ROMs, BIOS ni firmware; que el nombre y el logotipo no se licencian
   con el código; y que las contribuciones externas se aceptan bajo GPL sin
   cesión de derechos.
3. Deja el árbol limpio (`git status` sin nada pendiente) y los commits con
   mensajes en español.
4. Resume en tu respuesta: qué commits componen la rama, qué decisiones se
   tomaron y qué quedó pendiente, si algo quedó.

NO hagas merge ni rebase sobre develop. La integración la hago yo.
```

Luego, tú a mano:

```powershell
git checkout develop
git merge --no-ff feature/fase-0-cimientos
git branch -d feature/fase-0-cimientos
```

---

# Fase 1 — Esqueleto que compila

El objetivo de esta fase es tener una app que arranque, se vea como Nocturne y
navegue. Sin emulación, sin NDK, sin ROMs.

## 1.1 · Proyecto Gradle, Hilt y el sistema visual

Primer commit de `feature/fase-1-esqueleto`.

```
Crea el proyecto Android desde cero en este repositorio.

Configuración, alineada con el proyecto hermano app-emulador:
- applicationId y namespace: com.moralesDev.dsForEveryone
- Nombre de la app: DsForEveryone
- Kotlin + Jetpack Compose, Gradle con catálogo de versiones en
  gradle/libs.versions.toml
- minSdk 26 (lo impone AAudio, que llegará en la fase 4), compileSdk y
  targetSdk en la última estable
- abiFilters: sólo arm64-v8a
- Hilt configurado, con DsEmuApplication como punto de entrada
- MainActivity única. En el manifiesto va con screenOrientation="portrait";
  sólo la pantalla de juego se desbloqueará en runtime más adelante
- SIN externalNativeBuild todavía: no hay código nativo en esta fase

Trae el sistema visual Nocturne desde
C:\Users\user\Documents\workspace\app-emulador\app\src\main\java\com\moralesDev\emuladorForEveryone\ui\
copiando estos paquetes y adaptando el nombre del paquete:
- ui/theme/ (Color.kt, Theme.kt, Type.kt) — sólo modo oscuro, no traigas nada
  de tema claro
- ui/components/ (Nocturne.kt, AppLogo.kt, LoadingVeil.kt)
- ui/UiMessage.kt
- ui/locale/AppLanguageProvider.kt

Sobre AppLanguageProvider hay una trampa documentada que debes respetar: usa
ContextThemeWrapper y NO createConfigurationContext, porque este último rompe
la cadena por la que Hilt localiza la Activity y hiltViewModel() falla.

Prepara res/values/ (español, por defecto) y res/values-en/ (inglés).

MainActivity puede pintar de momento un texto centrado con el logotipo.
```

**Está hecho cuando** `.\gradlew.bat assembleDebug` pasa, la app instala y
arranca en el Motorola, y se ve en modo oscuro con la tipografía de Nocturne.

## 1.2 · Navegación, splash y bienvenida

Commit dentro de `feature/fase-1-esqueleto`.

```
Trae la navegación y las dos primeras pantallas desde app-emulador, adaptando
el paquete:
- ui/navigation/ (AppNavHost.kt, ScreenTransitions.kt)
- ui/splash/SplashScreen.kt
- ui/welcome/WelcomeScreen.kt
- ui/MainViewModel.kt (idioma y onboarding: lo que hace falta antes de pintar)

Adapta los textos a Nintendo DS. La pantalla de bienvenida gana un apartado
nuevo, de momento sólo maquetado y sin lógica: un paso que explicará qué
archivos de sistema (BIOS y firmware) hacen falta, de dónde salen y que la app
no los distribuye. Ese paso debe poder ocultarse entero según una bandera,
porque si el core que acabe ganando es DeSmuME no hará falta.

Destinos del NavHost, algunos todavía vacíos: splash, bienvenida, biblioteca,
juego, ajustes, acerca de, editor de disposición.

Recuerda la invariante: sin Dialog, Popup ni DropdownMenu en ningún sitio.
```

**Está hecho cuando** se navega splash → bienvenida → biblioteca (vacía) y las
transiciones son las del proyecto hermano.

## 1.3 · Almacenamiento y ajustes

Commit dentro de `feature/fase-1-esqueleto`.

```
Trae la capa de almacenamiento desde app-emulador, adaptando el paquete:
- data/storage/StorageManager.kt — única autoridad sobre rutas. Recibe la
  carpeta ya resuelta, no el Context, para poder probarlo en JVM sobre una
  carpeta temporal. Añade una ruta nueva: system/, para BIOS y firmware.
- data/storage/AtomicWrite.kt — temporal, fsync, rename. Sin cambios.
- data/settings/ (SettingsRepository.kt, EmulatorSettings.kt) — DataStore.

EmulatorSettings crece respecto al del GBA. Añade, con valores por defecto
razonables y sin UI todavía:
- modo de disposición de pantallas, uno por orientación (vertical y horizontal)
- separación entre pantallas y escala de la pantalla secundaria
- pantallas intercambiadas, sí o no
- filtro de imagen (nítido / suavizado)
- modo de toque (directo / con desplazamiento / cursor virtual) y la magnitud
  del desplazamiento

Nada pasa por SharedPreferences en ningún momento.

Pruebas de JVM sobre carpeta temporal real para StorageManager y AtomicWrite,
como en el proyecto hermano.
```

**Está hecho cuando** las pruebas de JVM pasan y `StorageManager` resuelve
`saves/`, `covers/` y `system/`.

## 1.C · Cierre de la fase 1

```
Cierra la fase 1. No escribas funcionalidad nueva.

1. `.\gradlew.bat assembleDebug`, `testDebugUnitTest` y `lint`, los tres en
   verde. Si el lint saca avisos, corrígelos ahora: arrastrarlos hasta la fase 4
   es como se acumulan.
2. Instala en el dispositivo y comprueba a mano el recorrido completo:
   splash → bienvenida → biblioteca vacía, en español y en inglés, con el
   cambio de idioma en caliente funcionando.
3. Revisa que no se haya colado nada de tema claro, ningún Dialog / Popup /
   DropdownMenu, y ningún permiso en el manifiesto.
4. Actualiza docs/arquitectura.md si la estructura real de paquetes se desvió
   de la que está documentada. El documento manda hasta que el código
   demuestre lo contrario, y entonces se actualiza el documento.
5. Deja el árbol limpio y resume en tu respuesta qué contiene la rama.

NO hagas merge ni rebase sobre develop. La integración la hago yo.
```

Luego, tú a mano:

```powershell
git checkout develop
git merge --no-ff feature/fase-1-esqueleto
git branch -d feature/fase-1-esqueleto
```

---

# Fase 2 — La geometría, contra un stub

Ésta es **la fase que importa**. Aquí se resuelve lo genuinamente nuevo de la DS
—dos pantallas, un lápiz y doce botones en menos sitio— sin haber tocado el NDK
todavía. Si esta fase sale bien, meter el core después es escribir un puente.

## 2.1 · LayoutEngine y ScreenLayout

Primer commit de `feature/fase-2-geometria`.

```
Lee docs/pantallas.md antes de empezar.

Escribe emulator/layout/, geometría pura: sin Context, sin Composable, sin
unidades de Compose. Entra un tamaño, salen rectángulos.

1. LayoutEngine — coloca rectángulos dentro de un área con restricciones: no
   solaparse entre sí, no salirse del área, mantener una proporción, ajustar a
   una rejilla. Lo van a consumir tanto las pantallas como los mandos, así que
   escríbelo genérico desde el principio.

2. ScreenLayout — resuelve la colocación de las dos pantallas de la DS
   (256x192 cada una, 4:3) según:
   - modo: APILADAS, LADO_A_LADO, SOLO_SUPERIOR, SOLO_INFERIOR,
     PRINCIPAL_Y_SECUNDARIA
   - hueco disponible en píxeles
   - separación entre pantallas (0..1 relativo)
   - escala de la secundaria
   - intercambiadas, sí o no (es un modificador que se aplica sobre cualquier
     modo, no un modo aparte)
   Devuelve un ScreenPlacement con los dos rectángulos y CUÁL de ellos es la
   pantalla táctil.

Pruebas de JVM, y que sean estas exactamente:
- las dos pantallas nunca se solapan
- ninguna se sale del hueco
- cada una mantiene 4:3 exacto
- la misma entrada da siempre la misma salida
- el rectángulo marcado como táctil es SIEMPRE el de la pantalla lógica
  inferior, también con las pantallas intercambiadas
- casos límite: hueco muy ancho, muy alto, y cuadrado

Nada de Compose en este entregable.
```

**Está hecho cuando** las pruebas de JVM pasan y ningún archivo del paquete
`layout/` importa nada de Compose.

## 2.2 · TouchRouter

Commit dentro de `feature/fase-2-geometria`.

```
Lee docs/entrada.md antes de empezar.

Escribe emulator/layout/TouchRouter: convierte un punto de la vista a una
coordenada de la pantalla táctil de la DS, dado un ScreenPlacement.

- Devuelve null si el punto cae fuera del rectángulo de la pantalla táctil. No
  lo recortes al borde: fuera es fuera, y no produce toque.
- El rango de salida es 0..255 en x y 0..191 en y.

Pruebas de JVM, y que sean estas exactamente:
- un punto fuera de la pantalla táctil devuelve null
- las cuatro esquinas dan exactamente (0,0), (255,0), (0,191) y (255,191)
- el recorte ocurre EN el borde, no un píxel después: comprueba el píxel
  interior y el exterior de cada lado
- funciona igual con las pantallas intercambiadas
- funciona con la pantalla táctil escalada al 30% (modo secundaria reducida)

Sigue sin haber Compose en este paquete.
```

**Está hecho cuando** las pruebas pasan, incluidas las de borde exacto.

## 2.3 · Botones, cruceta y disposición de mandos

Commit dentro de `feature/fase-2-geometria`.

```
Lee docs/entrada.md antes de empezar.

1. emulator/DsButton.kt — los doce botones: cruceta (4), A, B, X, Y, L, R,
   Start, Select. Deja el valor de `bit` de cada uno como una constante con un
   comentario que diga claramente que NO se deduce del hardware y que hay que
   copiarlo de las cabeceras del core cuando se elija. De momento pon un
   orden provisional y márcalo como tal.

2. emulator/DirectionalPadZones.kt — trae la de app-emulador y AÑÁDELE
   DIAGONALES. En el GBA se omitieron a propósito; en DS hacen falta. Reparte
   el cuadrado en ocho cuñas que no se solapen y no dejen huecos. Deja el
   ancho de las cuñas diagonales como un parámetro, porque habrá que ajustarlo
   midiendo con el dedo. Sigue siendo UN control, no cuatro botones: deslizar
   de arriba a la diagonal y a la derecha sin levantar el dedo debe funcionar.

3. emulator/InputManager.kt — trae el de app-emulador y amplíalo: máscara de
   12 bits en vez de 10, más la posición del lápiz y si está pulsado. La
   posición táctil es ESTADO COMPARTIDO que el hilo de emulación lee una vez
   por fotograma, no un comando encolado.

4. emulator/layout/ControlLayout.kt — sobre LayoutEngine, coloca siete grupos:
   cruceta, A/B/X/Y, L, R, Start+Select, y el botón de soplar. Con posición,
   tamaño y opacidad por grupo, y perfiles independientes por orientación. Los
   grupos no pueden solaparse entre sí ni con el rectángulo de la pantalla
   táctil.

Pruebas de JVM: las ocho cuñas cubren el círculo sin solaparse y sin huecos;
ningún grupo se solapa con otro ni con la pantalla táctil, en las dos
orientaciones y con cualquier tamaño de grupo.
```

**Está hecho cuando** las pruebas pasan y la cruceta da las ocho direcciones.

## 2.4 · La frontera y el controlador

Commit dentro de `feature/fase-2-geometria`.

```
Lee docs/arquitectura.md y docs/integracion-core.md antes de empezar.

1. emulator/core/EmulatorCore.kt — la frontera, adaptada desde app-emulador.
   Métodos heredados: load, unload, runFrame, setKeys, serializeState,
   deserializeState, readBattery, consumeBatteryDirty, captureFrame,
   attachSurface, coreName, coreVersion, stateSize.
   Métodos NUEVOS de la DS:
   - setTouch(x: Int, y: Int, down: Boolean)
   - setMicNoise(active: Boolean)

2. emulator/core/StubEmulatorCore.kt — el doble de pruebas, sin código nativo.
   Debe producir un framebuffer de 256x384 (pantalla superior en las filas
   0-191, inferior en las 192-383) con DOS rectángulos animados y claramente
   distintos, de modo que se vea de un vistazo cuál es cuál y si la disposición
   está bien. Que el rectángulo de la pantalla inferior reaccione al toque:
   pintar un punto donde toca el dedo es la forma más rápida de comprobar
   TouchRouter en dispositivo.

3. emulator/EmulatorController.kt — trae el de app-emulador con su estructura
   intacta: Thread propio con THREAD_PRIORITY_URGENT_DISPLAY, ritmo con
   LockSupport.parkNanos sobre reloj absoluto, cola de comandos con
   CompletableDeferred, attachSurface bloqueante, loadState que serializa el
   estado actual antes de restaurar.
   CAMBIA EL PERÍODO: 16_715_113 ns (59,8261 Hz = 33513982 Hz / 560190 ciclos),
   no los 16_742_706 ns de la GBA.
   AÑADE el estado táctil compartido, leído una vez por fotograma.

4. di/AppModule.kt — provideEmulatorCore() devuelve StubEmulatorCore. Debe
   ser el ÚNICO sitio del proyecto donde se decide qué core se usa.

Pruebas de JVM con un FakeEmulatorCore que registre lo que recibe: que el
controlador entregue la máscara correcta, que la posición táctil llegue una vez
por fotograma mientras el dedo está abajo, y que la cola de comandos se siga
atendiendo con la emulación en pausa.
```

**Está hecho cuando** las pruebas pasan y `AppModule` es el único punto de
decisión del core.

## 2.5 · El renderizador GL

Commit dentro de `feature/fase-2-geometria`.

```
Lee docs/pantallas.md antes de empezar. Este entregable sustituye el enfoque de
GbaSurfaceView de app-emulador; no lo traigas.

Escribe emulator/render/DsGLRenderer: un GLSurfaceView con GL ES 2.0 que
presenta el framebuffer del core.

- El framebuffer del core es 256x384. Se sube como UNA sola textura con
  glTexSubImage2D, una vez por fotograma. Una subida, no dos.
- Se pintan DOS quads, cada uno con las coordenadas de textura de su mitad
  (filas 0-191 y 192-383) y con el rectángulo destino que le asigne el
  ScreenPlacement vigente.
- El filtro es un parámetro, no una geometría: GL_NEAREST para "Nítido",
  GL_LINEAR para "Suavizado". Cambiarlo no debe recrear nada.
- Cambiar de disposición o rotar debe cambiar el viewport y los dos
  rectángulos, NUNCA el tamaño del búfer. Ésa era la causa del bug de rotación
  del proyecto hermano y no queremos reproducirla.

Añade un contador que distinga fps EMULADOS de fps PRESENTADOS, por separado.
Son cosas distintas y confundirlas invalida cualquier medida posterior.

Móntalo contra StubEmulatorCore en una pantalla de prueba mínima, con un
selector de disposición, para poder verlo en el dispositivo.
```

**Está hecho cuando** en el Motorola se ven los dos rectángulos del stub, se
cambia de disposición en caliente, se rota sin que se quede pegado nada, y el
coste medido es **el mismo en Nítido que en Suavizado** (si no lo es, algo está
mal en el renderizador).

## 2.6 · La pantalla de juego

Commit dentro de `feature/fase-2-geometria`.

```
Monta ui/game/GameScreen.kt uniendo lo de los entregables anteriores:

- El área de pantallas con DsGLRenderer y el ScreenPlacement vigente.
- La superficie táctil sobre el rectángulo de la pantalla táctil, escribiendo
  en InputManager a través de TouchRouter.
- Los mandos, colocados por ControlLayout. Trae `pressable` desde
  app-emulador (ui/game/controls/Pressable.kt) tal cual: awaitEachGesture
  siguiendo cada puntero por su id, con finally que suelta el botón si el gesto
  se cancela.
- El botón de soplar, que llama a setMicNoise mientras está pulsado.
- FreeOrientation(): sólo esta pantalla se desbloquea en runtime; el resto de
  la app sigue en vertical por manifiesto.
- ui/game/PauseMenu.kt traído de app-emulador, con filas nuevas: disposición de
  pantallas y modo de toque, ambos cambiables SIN salir del juego.

La pantalla táctil es un puntero más, no un caso especial. NO añadas lógica de
exclusividad entre mandos y táctil: un dedo en la cruceta y otro en la pantalla
de abajo tienen que funcionar a la vez, y eso ya lo da awaitEachGesture.

Lo que se pinta como pulsado viene de la máscara real del InputManager, no de
un estado local del composable.
```

**Está hecho cuando** en el dispositivo se puede mantener la cruceta con un dedo
y dibujar en la pantalla táctil con otro a la vez, en las dos orientaciones.

## 2.7 · El editor de disposición

Commit dentro de `feature/fase-2-geometria`.

```
Lee docs/entrada.md y docs/pantallas.md antes de empezar.

Escribe ui/game/layout/: el editor que permite arrastrar a mano las dos
pantallas y los siete grupos de mandos, sobre LayoutEngine.

- Perfiles independientes por orientación, vertical y horizontal.
- Nada puede solaparse: ni las pantallas entre sí, ni los grupos entre sí, ni
  un grupo con la pantalla táctil.
- Por grupo: posición, tamaño y opacidad. El tamaño importa tanto como la
  posición: en tablet los botones de tamaño de teléfono son ridículos.
- Un botón de "restablecer" por perfil.
- SÓLO se abre con la emulación detenida. Arrastrar mientras corre un juego es
  un problema de sincronización que no aporta nada.
- Se persiste en DataStore, en los ajustes que ya preparamos en 1.3.

La vista previa del editor usa el mismo ScreenLayout y ControlLayout que la
pantalla de juego. Si el editor y el juego colocan distinto, hay un error.
```

**Está hecho cuando** una disposición hecha a mano en el editor se ve idéntica
al entrar al juego, y sobrevive a cerrar y abrir la app.

## 2.C · Cierre de la fase 2

Es el cierre más importante del plan: lo que se valide aquí es lo que no habrá
que rehacer cuando entre el core.

```
Cierra la fase 2. No escribas funcionalidad nueva.

1. `assembleDebug`, `testDebugUnitTest` y `lint` en verde.
2. Comprueba que emulator/layout/ no importa NADA de Compose en ningún archivo.
   Si algo se coló, sácalo: ahí está el valor de haberlo separado.
3. Verifica en el dispositivo, contra el stub, y anota el resultado de cada
   punto:
   - las cinco disposiciones de pantallas, en vertical y en horizontal
   - rotar en cada una de ellas sin que la imagen se quede pegada ni deformada
   - Nítido y Suavizado: deben costar LO MISMO. Si no, hay algo mal en el
     renderizador y hay que arreglarlo antes de seguir, no después.
   - cruceta con un dedo y dibujo en la pantalla táctil con otro, a la vez
   - las ocho direcciones de la cruceta, diagonales incluidas
   - una disposición personalizada en el editor, y que al entrar al juego se
     vea idéntica
4. Anota en docs/pantallas.md las cifras medidas de fps emulados, fps
   presentados y porcentaje de núcleo. Son la línea base contra la que se
   comparará el core real en la fase 4.
5. Actualiza docs/arquitectura.md y docs/entrada.md con lo que se haya desviado
   de lo planeado, sobre todo el ancho de cuña diagonal que acabe funcionando.
6. Deja el árbol limpio y resume en tu respuesta qué contiene la rama y qué
   quedó pendiente.

NO hagas merge ni rebase sobre develop. La integración la hago yo.
```

Luego, tú a mano:

```powershell
git checkout develop
git merge --no-ff feature/fase-2-geometria
git branch -d feature/fase-2-geometria
```

---

# Fase 3 — Biblioteca y partidas

Ya hay una app jugable contra el stub. Ahora se le da contenido real.

## 3.1 · Cabecera de ROM y banner

Primer commit de `feature/fase-3-biblioteca-partidas`.

```
Lee la sección "Cabecera de la ROM" de docs/integracion-core.md.

1. data/rom/DsRomHeader.kt — lee la cabecera de un cartucho de Nintendo DS:
   título, código de juego, código de fabricante, y el offset al banner (que
   está en 0x68).

2. Del banner, extrae el icono de 32x32 (4 bpp, paleta de 16 colores, en
   formato de tiles) y los títulos en los distintos idiomas. Convertir el
   icono a bitmap es aritmética, no necesitas ninguna biblioteca.

3. data/rom/RomIdentity.kt — trae el de app-emulador. Calcula el SHA-256 por
   bloques, sin cargar la ROM entera en memoria, y en el MISMO recorrido
   identifica la plataforma y extrae la cabecera. Cambia sólo qué cabecera
   prueba.

Esto nos da portadas automáticas, que en el proyecto de GBA no existen. Es de
las mejores cosas que este proyecto tiene gratis: apróvechalo.

Pruebas de JVM con cabeceras construidas a mano, incluidas cabeceras corruptas
y truncadas: lo que importa no es leer bien una buena, es negarse a leer una
mala.
```

**Está hecho cuando** dada una ROM libre de prueba salen el título correcto y
el icono como bitmap.

## 3.2 · Biblioteca: Room, SAF y zip

Commit dentro de `feature/fase-3-biblioteca-partidas`.

```
Trae desde app-emulador, adaptando el paquete y la plataforma:
- data/db/ (Database, Dao, Entity). La entidad gana el icono extraído del
  banner.
- data/rom/RomRepository.kt — las ROMs NO se copian: se guarda el Uri del
  Storage Access Framework con permiso persistente. Detecta que un archivo dejó
  de estar accesible comparando contra persistedUriPermissions y lo marca sin
  perder las partidas.
- data/rom/RomZipReader.kt — detección por firma de bytes (no por extensión),
  tope de tamaño, dedupe correcto con el archivo suelto. Cambian las firmas:
  ahora son .nds.
- data/cover/ (CoverRepository, CoverImage) — la portada por defecto sale
  ahora del banner de la ROM. Sigue permitiendo sustituirla a mano por Photo
  Picker, pero deja de ser el único camino.

Recuerda: el manifiesto no declara ni un permiso. Si algo parece necesitar uno,
el enfoque está mal.

RomRepository no tenía cobertura de pruebas en el proyecto hermano y se anotó
como pendiente. Aquí escríbela desde el principio.
```

**Está hecho cuando** se añade una ROM por SAF, aparece con su icono y su
título reales, y sigue ahí tras reiniciar la app.

## 3.3 · La biblioteca en pantalla

Commit dentro de `feature/fase-3-biblioteca-partidas`.

```
Lee la sección "Deudas conocidas que no conviene heredar" de
docs/reutilizacion.md ANTES de escribir nada.

Escribe ui/library/ (RomLibraryScreen y RomLibraryViewModel). Puedes inspirarte
en el del proyecto hermano, pero hay un problema que NO debes heredar:

En app-emulador, el ConsoleHero tiene una altura fija de unos 795 dp. El ancho
se adapta y el alto no, así que en tablet la consola queda pequeña y centrada,
el cartucho se recorta por el borde de la tarjeta y la mitad inferior de la
pantalla queda vacía. Un intento de arreglarlo salió mal y se dejó como estaba.

Aquí empezamos de cero, así que hazlo adaptable desde el primer momento:
- nada de alturas fijas en dp para el elemento principal
- que escale con el alto disponible, no sólo con el ancho
- compruébalo en teléfono vertical, teléfono horizontal y tablet vertical

La carcasa de consola es ahora una DS, con sus dos pantallas. El carrusel de
cartuchos se mantiene, con la etiqueta del título sobre el cartucho (eso ya se
resolvió en el proyecto hermano: un cartucho sin identificar no sirve de nada).
```

**Está hecho cuando** se ve bien en las tres configuraciones, sin huecos
grandes ni recortes.

## 3.4 · El sistema de partidas

Commit dentro de `feature/fase-3-biblioteca-partidas`.

```
Lee docs/reutilizacion.md, sección "El sistema de partidas entero".

Trae desde app-emulador, adaptando el paquete. Este código es AGNÓSTICO DEL
CORE por diseño, así que se copia casi tal cual:
- data/save/SaveStateContainer.kt — el formato E4FS con la identidad de la ROM
  (SHA-256 + CRC32), la del core (nombre y versión POR SEPARADO) y el SHA-256
  del payload. Las nueve comprobaciones se mantienen todas.
- data/save/SaveStateManager.kt — ranuras, miniaturas, savedAt.
- data/save/SaveManager.kt — la garantía por FORMA DE MÉTODO, no por if:
  writeAutoBackup NO acepta número de ranura (siempre escribe auto.state) y
  writeState rechaza el 0.
- data/save/BatterySaveManager.kt.
- ui/game/SaveStateSheet.kt y ui/game/StateRejectionText.kt.

savedAt (dentro del contenedor, epoch ms UTC) es la ÚNICA fuente para ordenar y
para pintar la fecha. Nunca la fecha del sistema de archivos.

Los guardados que pueden coincidir en el tiempo pasan por un mismo Mutex y
comparten el temporal.

Pruebas de JVM sobre carpeta temporal real, y sobre todo con archivos
ESTROPEADOS A MANO: lo que importa del formato no es escribir bien, sino
negarse a leer mal. Que dos juegos no se mezclen y que borrar borre sólo lo
suyo son afirmaciones sobre archivos, y un doble las daría por buenas por
construcción.
```

**Está hecho cuando** las pruebas pasan, incluidas las de archivos corruptos, y
guardar y cargar funciona contra el stub.

## 3.C · Cierre de la fase 3

```
Cierra la fase 3. No escribas funcionalidad nueva.

1. `assembleDebug`, `testDebugUnitTest` y `lint` en verde.
2. Verifica en el dispositivo:
   - añadir una ROM libre por SAF: aparece con su icono y su título reales,
     extraídos del banner
   - añadir la misma ROM dentro de un .zip: se detecta y NO se duplica
   - reiniciar la app: la biblioteca sigue igual
   - mover o borrar el archivo original: la entrada se marca como no
     disponible y las partidas NO se pierden
   - la biblioteca en teléfono vertical, teléfono horizontal y tablet vertical,
     sin huecos grandes ni recortes. Esto es una deuda explícita del proyecto
     hermano: si aquí ya se ve mal, párate y arréglalo ahora.
   - guardar en las cinco ranuras y en la 0 de respaldo, y cargarlas
3. Comprueba que RomRepository tiene cobertura de pruebas. En el proyecto
   hermano quedó sin ella y se anotó como pendiente; aquí no se repite.
4. Actualiza docs/arquitectura.md y docs/reutilizacion.md con lo que se haya
   desviado.
5. Deja el árbol limpio y resume en tu respuesta qué contiene la rama.

NO hagas merge ni rebase sobre develop. La integración la hago yo.
```

Luego, tú a mano:

```powershell
git checkout develop
git merge --no-ff feature/fase-3-biblioteca-partidas
git branch -d feature/fase-3-biblioteca-partidas
```

---

# Fase 4 — El core nativo

Todo lo anterior funciona contra un stub. Ahora se sustituye por emulación de
verdad, y en teoría es cambiar una línea de `AppModule` más el puente.

## 4.1 · NDK y compilación del core

Primer commit de `feature/fase-4-core-nativo`.

```
Lee docs/integracion-core.md, sobre todo la sección 2.

Objetivo de este entregable: que el core compile como biblioteca estática desde
el NDK. Nada más. Sin puente, sin JNI, sin Kotlin.

1. Añade el core elegido en third_party/, como tarball de una versión
   etiquetada, SIN modificar nada. Copia su LICENSE. Empezamos por melonDS;
   si la compilación cruzada se atasca más de lo razonable, dilo y lo hablamos
   antes de insistir.
2. Configura externalNativeBuild con CMake y NDK en app/build.gradle.kts, sólo
   arm64-v8a.
3. Escribe native/emulator_bridge/CMakeLists.txt que compile el core con la
   interfaz gráfica, SDL, red y todo lo de escritorio desactivado.
4. Es C++, no C: configura la STL del NDK (c++_static).

AVISO IMPORTANTE, aprendido a base de perder tiempo en el proyecto hermano:
mGBA regeneraba su version.c interrogando a git en un target de CMake que se
relanza como proceso nuevo y no hereda SKIP_GIT; como third_party/ no tiene su
propio .git, ese `git describe` subía un nivel y encontraba el de la app,
estampando la identidad del repositorio de la aplicación como versión del core
e invalidando los save states de todos los usuarios en cada build.
Comprueba si el core que estás integrando hace algo parecido, y si lo hace,
NO leas nunca ese valor generado: la versión del core será una constante del
puente. Documenta lo que encuentres en docs/integracion-core.md.
```

**Está hecho cuando** `.\gradlew.bat assembleDebug` compila el core y produce
la biblioteca estática. La primera vez tardará varios minutos.

## 4.2 · El puente: cargar y pintar

Commit dentro de `feature/fase-4-core-nativo`.

```
Escribe el puente JNI mínimo que carga una ROM y produce imagen.

- native/emulator_bridge/ds_bridge.cpp — todo el código propio en C++, con
  extern "C" para los símbolos JNI. NO HAY LÓGICA DE INTERFAZ EN C++.
- emulator/core/NativeBridge.kt — declaraciones JNI.
- emulator/core/DsEmulatorCore.kt — implementa EmulatorCore sobre NativeBridge.
- BRIDGE_CORE_VERSION como constante del puente, con la versión exacta del core
  integrado. Nunca un valor generado por el sistema de compilación.
- La ROM se entrega al core EN MEMORIA, no como ruta: el archivo real vive tras
  un Uri del SAF.
- Comprueba si el core puede escribir directamente en un búfer nuestro de
  256x384 (equivalente a setVideoBuffer de mGBA) o si hay que copiar desde su
  búfer interno. Es la diferencia entre una copia por fotograma y ninguna;
  averígualo y déjalo escrito.

Cambia AppModule.provideEmulatorCore() de StubEmulatorCore a DsEmulatorCore.
Debe ser LA ÚNICA línea que cambie fuera del paquete core/. Si tienes que tocar
la UI, los mandos, la biblioteca o las partidas, algo está mal en la frontera:
párate y dilo antes de seguir.

En proguard-rules.pro, conserva los métodos nativos.

Prueba con una ROM libre, nunca con las del usuario, y retírala de la
biblioteca al terminar.
```

**Está hecho cuando** una ROM libre arranca y se ve en las dos pantallas, y
`AppModule` fue el único archivo tocado fuera de `core/`.

## 4.3 · Entrada, audio y savedata

Commit dentro de `feature/fase-4-core-nativo`.

```
Completa el puente:

1. Botones: cablea setKeys. AHORA es cuando se fija DsButton.bit de verdad:
   cópialo de las cabeceras del core, no lo deduzcas del hardware. Recuerda que
   X e Y no viven en KEYINPUT sino en EXTKEYIN, y que cada core empaqueta su
   máscara a su manera. Escribe una prueba que pulse CADA botón por separado
   contra el core real: un botón mal mapeado no da error, da un juego que se
   comporta raro y una tarde perdida.

2. Pantalla táctil: cablea setTouch. Mientras el dedo esté abajo, la posición
   se aplica en CADA fotograma, no sólo cuando cambia.

3. Micrófono: cablea setMicNoise inyectando ruido blanco en la entrada de
   micrófono del core. Sin RECORD_AUDIO. El manifiesto sigue sin declarar ni
   un permiso.

4. Audio: 32768 Hz estéreo, sobre AAudio en modo baja latencia, con el anillo
   sin cerrojos del proyecto hermano. Sigue vigente la limitación heredada: sin
   sincronización por audio, el ritmo lo marca el reloj.

5. Savedata: los cartuchos de DS usan EEPROM y Flash de tamaños variados y
   ALGUNOS JUEGOS NO DECLARAN BIEN SU TIPO. Averigua cómo lo resuelve el core
   antes de dar por bueno el circuito de consumeBatteryDirty(). Éste es el
   punto donde más probable es perder partidas de usuario: pruébalo en el
   dispositivo con un juego libre que guarde de verdad, cierra la app, vuelve a
   abrirla y comprueba que la partida sigue ahí.
```

**Está hecho cuando** se juega con sonido, la pantalla táctil responde, y una
partida guardada sobrevive a cerrar y reabrir la app.

## 4.4 · Save states contra el core real

Commit dentro de `feature/fase-4-core-nativo`.

```
Cablea serializeState y deserializeState del puente contra el contenedor E4FS
que ya existe desde la fase 3.

- coreName y coreVersion salen de constantes del puente. coreVersion es
  BRIDGE_CORE_VERSION, NUNCA el símbolo de versión que genere el core en
  compilación. Esto no es una precaución: en el proyecto hermano ese error
  invalidó save states de usuarios reales, incluso en actualizaciones de tienda.
- loadState debe seguir serializando el estado actual antes de intentar
  restaurar, y volver a meterlo si el core rechaza el archivo. Un save state
  defectuoso no puede llevarse por delante la partida en curso.

Añade las dos defensas del proyecto hermano:
1. Un fixture dorado en app/src/test/resources/ con un save state conocido, y
   una prueba que verifique la identidad grabada.
2. Una tarea de Gradle que verifique el objeto compilado del puente y compruebe
   que BRIDGE_CORE_VERSION es la constante esperada, con dependencia de
   assemble. La prueba de JVM sola no basta: sólo compara constantes de JVM.
```

**Está hecho cuando** se guarda y se carga un estado con un juego real, y las
dos defensas están en verde.

## 4.5 · Archivos de sistema, medición y elección de core

Commit dentro de `feature/fase-4-core-nativo`.

```
Último entregable de la fase.

1. Si el core integrado necesita BIOS y firmware, escribe data/system/:
   importación por SAF, copia a filesDir/system/, validación por tamaño y hash,
   y un mensaje distinto y claro por cada caso: archivo que falta, truncado,
   del modelo equivocado, o firmware de DSi que no arranca el menú. Activa el
   paso correspondiente de la pantalla de bienvenida que quedó maquetado en 1.2.
   Éste es el único caso en el que la app COPIA un archivo del usuario en vez de
   referenciarlo por Uri, y el motivo es que sin él la app no funciona.

2. Mide en el Motorola Edge 30 con ROMs libres, con el mismo protocolo que se
   usó para el ensayo de velocidad del proyecto hermano:
   - fps emulados y fps presentados, POR SEPARADO
   - porcentaje de un núcleo del hilo de emulación, y del proceso entero
   - comportamiento tras varios minutos, para ver si aparece limitación térmica
   - las mismas cifras con el renderizado 3D del core por software y, si
     existe, por GL
   - Nítido frente a Suavizado: deberían costar lo mismo. Si no, hay algo mal
     en el renderizador.

3. Escribe los resultados en un docs/estabilizacion-core.md nuevo y, con los
   números delante, cierra la decisión de core en docs/integracion-core.md.
   Si los números dicen que había que haber elegido el otro, dilo: para eso
   está la frontera.
```

**Está hecho cuando** hay números escritos en la documentación y la decisión de
core está cerrada con ellos.

## 4.C · Cierre de la fase 4

```
Cierra la fase 4. No escribas funcionalidad nueva.

1. `assembleDebug`, `testDebugUnitTest`, `lint` y el build de RELEASE, los
   cuatro en verde. El de release importa: es el que va a la tienda y es donde
   aparecen los problemas de ProGuard con los métodos nativos.
2. Comprueba que AppModule.provideEmulatorCore() sigue siendo el ÚNICO sitio
   del proyecto donde se decide qué core se usa, y que StubEmulatorCore sigue
   existiendo y funcionando: es el doble con el que se prueba sin NDK.
3. Verifica en el dispositivo con ROMs libres:
   - un juego arranca, se ve en las dos pantallas y suena
   - la pantalla táctil responde en los tres modos de toque
   - CADA uno de los doce botones hace lo que debe (un botón mal mapeado no da
     error, da un juego que se comporta raro)
   - guardar la partida, cerrar la app, reabrir: la partida sigue ahí
   - guardar y cargar un save state
   - el fixture dorado y la verificación de BRIDGE_CORE_VERSION en el objeto
     compilado, ambos en verde
4. Retira de la biblioteca las ROMs de prueba al terminar.
5. Comprueba que la pantalla de licencias muestra el aviso del core y el enlace
   al código fuente. En una app GPL eso no es cortesía.
6. Actualiza docs/integracion-core.md con la decisión de core cerrada y sus
   números, y docs/estabilizacion-core.md con lo que se corrigió por el camino.
7. Deja el árbol limpio y resume en tu respuesta qué contiene la rama y qué
   limitaciones quedan abiertas.

NO hagas merge ni rebase sobre develop. La integración la hago yo.
```

Luego, tú a mano:

```powershell
git checkout develop
git merge --no-ff feature/fase-4-core-nativo
git branch -d feature/fase-4-core-nativo
```

Y a partir de aquí, antes de cualquier publicación: subir
`versionCode`/`versionName` como **último** commit antes de generar el build de
release, y **etiquetar** ese commit exacto. En una app GPL la etiqueta no es
higiene: es cómo se puede entregar el código fuente del que salió cada binario.

---

# Después

Con la fase 4 cerrada hay un emulador de DS que funciona. Lo siguiente ya es
funcionalidad, y conviene hacerlo de una en una, como en el proyecto hermano:

- Trucos (Action Replay DS), reutilizando la forma de `CheatsSheet` y
  `CheatsCoordinator`
- Mandos físicos por Bluetooth y USB, con el cursor virtual como forma de usar
  la pantalla táctil
- Exportar e importar partidas
- ~~Fast-forward~~ — hecho: 1x / 1,5x / 2x, con el botón ⏩ junto al de menú
  y la misma velocidad en el menú de pausa. El audio se silencia mientras
  corre rápido. Ver `integracion-core.md`, «Avance rápido»
- Wi-Fi local entre dos instancias, que en DS es mucho más jugosa que el cable
  link de GBA

Y antes de publicar nada, el procedimiento de publicación de `app-emulador`,
con un añadido que aquí no es opcional: **una etiqueta de git por cada versión
publicada**. En una app GPL hay que poder entregar el código fuente exacto del
que salió cada binario.
