# Qué se trae de `app-emulador`, qué se adapta y qué es nuevo

Este documento se queda corto a propósito hasta la fase 2: no había nada
todavía que clasificar. Se escribe al cerrar la fase 3, con `data/rom/`,
`data/cover/`, `data/save/`, `ui/library/` y el sistema de partidas ya
construidos, porque es entonces cuando el patrón de qué se copia tal cual y
qué no se vuelve claro. Se actualiza en cada cierre de fase siguiente.

## Tal cual, cambiando sólo el paquete

Código de `app-emulador` que no sabe nada de GBA ni de DS: es geometría,
formato de archivo o interfaz genérica, y adaptarlo fue renombrar
`com.moralesDev.emuladorForEveryone` por `com.moralesDev.dsForEveryone`.

- **`data/save/SaveStateContainer.kt`** — el formato `E4FS` completo: firma,
  identidad de la ROM (SHA-256 + CRC32), identidad del core por separado,
  SHA-256 del payload, las comprobaciones de truncado/corrupción/versión. Una
  sola diferencia deliberada: no se trae la tolerancia a `coreVersion` con
  forma de `git describe` (`"0.10-develop-19-a5223ed"`). Esa tolerancia es un
  parche para un bug concreto de mGBA en `app-emulador` (`nativeCoreVersion()`
  leyendo el `.git` equivocado); no hay ningún core real integrado todavía
  aquí que pueda arrastrar ese mismo defecto, así que traerla habría sido
  copiar una cicatriz sin la herida.
- **`data/save/BatterySaveManager.kt`** y **`SaveManager.kt`** — tal cual.
- **`data/save/SaveStateManager.kt`** — tal cual salvo la resolución de la
  miniatura: `240×160`/`160×144` (GBA/GBC) se sustituye por `256×384` (las dos
  pantallas de la DS apiladas, `DsVideo.WIDTH × DsVideo.HEIGHT`), porque aquí
  sólo existe una plataforma. Se retira también `legacyStateMetadataFile`: es
  migración de un formato anterior a `E4FS` que sólo existió en `app-emulador`.
- **`data/cover/CoverImage.kt`** y **`CoverRepository.kt`** — tal cual. Recorte
  cuadrado, EXIF, reducción progresiva y WebP no saben nada de qué consola es
  la ROM. Único cambio: `android.media.ExifInterface` →
  `androidx.exifinterface.media.ExifInterface`, que ya debería haberse hecho
  en el proyecto hermano (lint lo marca allí también) y aquí se corrigió de
  paso.
- **`ui/game/CoverSheets.kt`**, **`ui/game/SaveStateSheet.kt`**,
  **`ui/game/StateRejectionText.kt`**, **`ui/SaveTimeFormat.kt`** — tal cual.
  Presentación de conceptos ya genéricos (`PickedImage`, `SaveStateSlot`,
  `StateRejection`).

## Se porta el patrón, no el archivo

- **`data/rom/RomIdentity.kt`** — misma idea (SHA-256 por bloques sin cargar
  el archivo entero, identificando la cabecera en el mismo recorrido) pero
  reescrito para una sola plataforma y para capturar además el banner en un
  desplazamiento variable que sólo se conoce tras leer la cabecera. En
  `app-emulador` el banner no existe: es la mejora nueva que trae la DS,
  documentada en `integracion-core.md`.
- **`data/rom/RomRepository.kt`** — mismo contrato (alta por SAF sin copiar el
  archivo, detección de zip por firma de bytes, deduplicado por hash) pero con
  una frontera nueva: depende de la interfaz `DocumentAccess`, no de
  `ContentResolver` directamente. `app-emulador` nunca tuvo pruebas de
  `RomRepository` —quedó anotado como pendiente porque `ContentResolver` no se
  puede instanciar en una prueba de JVM—, y aquí eso era justo lo que el plan
  pedía resolver desde el principio. `ContentResolverDocumentAccess` es la
  única implementación real; `FakeDocumentAccess` la sustituye en pruebas.
  Es una desviación deliberada del original, no un descuido.
- **`ui/library/RomLibraryScreen.kt`** y **`RomLibraryViewModel.kt`** — aquí
  hay una historia. El plan de trabajo (`plan-de-trabajo.md`, entregable 3.3)
  describía la biblioteca como «la carcasa de consola con sus dos pantallas y
  el carrusel de cartuchos», heredada de una versión antigua de
  `app-emulador`. Al llegar a este entregable, ese diseño ya no existe en el
  proyecto hermano: se sustituyó por la lista con búsqueda, paginación de 5 en
  5 y hoja de opciones que hay hoy. Se decidió con el usuario portar el diseño
  **actual** en vez de reconstruir uno sin referencia viva (ver el historial
  de la conversación de la fase 3.3). Lo que se adaptó, más allá del paquete:
  - Sin `RomPlatform` ni `PlatformTag`: esta app sólo tiene una plataforma.
  - La portada por defecto es el icono del banner (`RomInfo.icon`), no un
    marcador genérico: `Cover()` decodifica el `IntArray` de 32×32 con
    `Bitmap.createBitmap` y lo pinta con `FilterQuality.None` para conservar
    el pixel art, en vez de mostrar el icono neutro que usa `app-emulador`.
  - Sin «Continuar partida» ni el bloque de «última partida» bajo el título:
    dependen de `SaveManager`, que en `app-emulador` ya existía cuando se
    escribió esa pantalla y aquí llegó **después**, en el entregable 3.4. Cada
    fila tiene un único camino, «Abrir», hasta que se retome este punto.
  - Sin extraer/importar/borrar `.sav` desde la hoja de opciones de un juego:
    es la misma razón, esa hoja en `app-emulador` ya tenía `SaveManager`
    delante y aquí no lo tenía todavía al escribirse.

## Nuevo de la DS, sin equivalente en `app-emulador`

- **`data/rom/DsRomHeader.kt`**, **`DsBanner.kt`** — la cabecera de cartucho y
  el banner (icono 32×32 en tiles de 4 bpp, títulos en varios idiomas) no
  existen en GBA. Mapa de campos y CRC-16 verificados contra GBATEK, no contra
  ninguna fuente de `app-emulador`.
- **`emulator/layout/`** completo (`LayoutEngine`, `ScreenLayout`,
  `ControlLayout`, `TouchRouter`) y **`ui/game/layout/`** (el editor) — el
  problema de dos pantallas y doce mandos en poco sitio no tiene equivalente
  en una GBA de una sola pantalla.
- **`emulator/render/DsGLRenderer.kt`** — GL ES con dos quads en vez del truco
  del tamaño de superficie; ver `pantallas.md`.
- **`data/db/RomEntity.icon`** (`ByteArray?`) — el banner se guarda ya
  decodificado como blob de píxeles ARGB, para no reprocesar tiles y paleta en
  cada arranque de la biblioteca.

## Deudas conocidas que no conviene heredar

- **La altura fija del `ConsoleHero`** en `app-emulador` (documentada en el
  propio plan de trabajo como fuente del problema de recorte en tablet) dejó
  de aplicar aquí simplemente porque ese componente ya no existe: se evitó por
  partida doble, al portar la lista actual en vez del diseño antiguo.
- **`RomRepository` sin pruebas** en `app-emulador`: resuelto aquí desde el
  principio (fase 3.2) con la frontera `DocumentAccess`. Si esa frontera
  demuestra ser útil, vale la pena backportarla a `app-emulador` —en esa
  dirección sí está permitida, ver `licencia.md`— en vez de dejar que la
  brecha de cobertura se mantenga allí.
