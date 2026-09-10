# Las dos pantallas

## Lo que se rompe respecto a `app-emulador`

En el emulador de GBA hay una idea elegante que sostiene toda la presentación:
**el tamaño del búfer de la superficie *es* el filtro de imagen.**

- Búfer de 240×160 (`setFixedSize`) → amplía el compositor por hardware,
  interpolando. Modo «Suavizado».
- Búfer del tamaño de la vista (`setSizeFromLayout`) → escala el puente por
  vecino más próximo. Modo «Nítido».

Esa idea **no sobrevive a dos pantallas.** El motivo es geométrico, no de
rendimiento: el compositor escala *una* superficie a *un* rectángulo. Con dos
pantallas que van en sitios distintos, con separación variable, a veces de
tamaños distintos y a veces sólo una de ellas, no hay un único rectángulo
destino que el compositor pueda producir.

Se podría abrir dos `SurfaceView`, una por pantalla, pero eso significa dos
superficies que el hilo de emulación tiene que sincronizar entre sí cada
fotograma, con dos rutas de `attachSurface` y dos oportunidades de dibujar sobre
una superficie liberada. Es más frágil, no menos.

## Lo que se hace en su lugar

**Un `GLSurfaceView` con el framebuffer del core como textura y dos quads.**

El core entrega un búfer de **256×384**: la pantalla superior en las filas
0–191, la inferior en las 192–383. Cada fotograma:

1. El hilo de emulación produce el búfer.
2. Se sube como una única textura (`glTexSubImage2D`) — una subida, no dos.
3. Se pintan dos quads, cada uno con las coordenadas de textura de su mitad y
   con el rectángulo destino que le asigne la disposición vigente.

Ventajas que no son opcionales aquí:

- **El filtro vuelve a ser un parámetro.** `GL_NEAREST` para «Nítido»,
  `GL_LINEAR` para «Suavizado». Deja de ser una decisión de geometría, y de paso
  desaparece el problema que en `app-emulador` quedó documentado como limitación
  abierta: el escalado por software costaba el doble (75–82 % de un núcleo
  frente a 37–43 %). Aquí ese coste no existe: escala la GPU en los dos modos.
- **Las dos pantallas se mueven independientemente sin coste.** Cambiar de
  disposición es cambiar dos rectángulos, no reconfigurar superficies.
- **La rotación deja de ser un campo de minas.** El bug de rotación de
  `app-emulador` —`geomLayerBounds` de SurfaceFlinger pegado al primer tamaño
  transicional, que sólo se destrabó con un ciclo `INVISIBLE`→`VISIBLE`— era una
  patología de redimensionar el búfer de una `SurfaceView`. Con una superficie
  de tamaño de vista y la geometría dentro del shader, el búfer no cambia de
  tamaño por rotar: cambia el viewport. **Esto no garantiza que no aparezca otro
  problema de rotación, pero elimina la causa concreta que costó aquella
  investigación.**

El coste es que hay que escribir un renderizador GL ES 2.0 mínimo: dos shaders
de veinte líneas, una textura, un VBO. Es trabajo acotado y conocido.

## `ScreenLayout`: geometría fuera de Compose

Igual que `DirectionalPadZones` en `app-emulador` vive fuera de Compose para
poder comprobarse coordenada a coordenada, aquí hay un objeto de **geometría
pura**:

```
ScreenLayout.resolve(
    modo: LayoutMode,
    hueco: IntSize,        // el área disponible en píxeles
    separacion: Float,     // hueco entre pantallas, 0..1 relativo
    escalaSecundaria: Float
) : ScreenPlacement       // dos rectángulos + cuál es la táctil
```

Sin `Context`, sin `Composable`, sin unidades de Compose. Entra un tamaño, salen
dos rectángulos. Eso permite probar en JVM las afirmaciones que de verdad
importan:

- Las dos pantallas **nunca se solapan** (salvo en el modo que lo pide a
  propósito).
- Ninguna se sale del hueco.
- Cada una mantiene **4:3** exacto, salvo en «Rellenar».
- La misma entrada da siempre la misma salida.
- El rectángulo marcado como táctil es **siempre** el de la pantalla inferior,
  también cuando están intercambiadas.

## Las proporciones, con números

Cada pantalla de la DS es **256×192**, es decir **4:3**. Combinadas:

| Disposición | Tamaño lógico | Proporción | Dónde encaja |
|---|---|---|---|
| Apiladas (vertical) | 256×384 | **2:3** | Vertical. Es la nativa |
| Lado a lado | 512×192 | **8:3** | Horizontal. Muy ancha, deja mucho hueco arriba y abajo |
| Sólo una | 256×192 | 4:3 | Cualquiera. Para juegos que no usan la de abajo |
| Una grande + una pequeña encima | variable | — | Horizontal, para aprovechar la altura |

Compárese con la GBA: **3:2**, una sola pantalla, ancha. La diferencia práctica
es que en vertical la DS se come mucho más alto y deja **bastante menos sitio
para los mandos**. Eso no se arregla en este documento: se arregla en
[`entrada.md`](entrada.md), y es la razón de que el editor de disposición de
controles sea obligatorio desde el principio y no una funcionalidad futura.

### Las barras del sistema recortan el hueco antes de nada

`ScreenLayout.resolve` recibe unos márgenes (`LayoutInsets`) y coloca las dos
pantallas **dentro de lo que queda**, no en el contenedor entero. No es una
cuestión estética: la app va a pantalla completa (`enableEdgeToEdge`), así que
dibujar debajo de la barra de navegación se puede, pero **pulsar ahí no** — ese
toque es del sistema. En vertical daba igual, porque las pantallas no llegan
tan abajo; en horizontal ocupan todo el alto y la táctil se metía debajo de la
barra: una opción del juego dibujada en esa franja quedaba imposible de
seleccionar.

Los cuatro lados, no sólo arriba y abajo: en horizontal, con los tres botones,
la barra de navegación se coloca a un lado.

El recorte ocurre **antes** de calcular las proporciones, así que las dos
pantallas siguen siendo 4:3 exactas; lo que cambia es que son un poco más
pequeñas. `GameScreen` y el editor de disposición pasan los mismos márgenes,
que es lo que garantiza que lo que se coloca en el editor sea lo que se ve en
el juego.

## Los modos que hay que dar

El mínimo razonable, con perfiles independientes por orientación:

1. **Apiladas** — la disposición nativa. Por defecto en vertical.
2. **Lado a lado** — por defecto en horizontal.
3. **Sólo superior** / **Sólo inferior** — muchos juegos usan una pantalla como
   mapa o inventario permanente, y a pantalla completa se juega mejor.
4. **Principal + secundaria reducida** — una pantalla grande y la otra en una
   esquina, a escala configurable. En horizontal es la que mejor aprovecha el
   área.
5. **Intercambiar** — un modificador, no un modo: se aplica sobre cualquiera de
   los anteriores. La táctil sigue siendo la de abajo *lógicamente*, aunque se
   pinte arriba.

Y dos parámetros transversales:

- **Separación** entre pantallas, de cero a un hueco generoso. Cero pegadas
  parece raro en algunos juegos; un hueco fijo desperdicia sitio en otros.
- **Escala de la secundaria**, para el modo 4.

**Todos configurables sin salir del juego.** Cambiar de disposición a mitad de
partida es una operación frecuente en DS —se pasa de un mapa a un combate— y
obligar a ir a ajustes la convierte en algo que nadie usa. Va en el menú de
pausa, junto a las ranuras y las claves.

## El editor de disposición

Los modos anteriores cubren el 90 %. Para el resto hace falta arrastrar las
pantallas a mano, y ahí aparece el mismo problema que ya está planteado para los
controles en `app-emulador`: **colocar rectángulos que no pueden solaparse
dentro de un área.**

Es el mismo problema. Conviene resolverlo **una vez**:

```
LayoutEngine
├── coloca rectángulos con restricciones (no solapar, no salirse, mantener
│   proporción, ajuste a rejilla)
├── lo usa ScreenLayout            → las dos pantallas
└── lo usa ControlLayout           → cruceta, A/B/X/Y, L, R, Start+Select
```

Y una regla de producto heredada de la decisión que ya se tomó para el GBA: el
editor sólo se abre **con la emulación detenida**. Arrastrar pantallas mientras
corre un juego es un problema de sincronización que no aporta nada.

### Lo que se arrastra de verdad en las pantallas (entregable 2.7)

«Arrastrar las pantallas a mano» no se tradujo en rectángulos libres para cada
pantalla. `ScreenLayout.resolve` sólo tiene tres grados de libertad reales
—modo, separación y escala de la secundaria— y las cinco pruebas de JVM de
`ScreenLayoutTest` (4:3 exacto, sin solape salvo en Principal+secundaria, sin
salirse del hueco) dependen de que sea así. Dar rectángulos libres habría
significado un segundo sistema de geometría de pantallas, con el riesgo
concreto que el propio entregable advertía: que el editor y el juego acaben
colocando distinto.

Así que el editor arrastra los mismos tres grados de libertad que ya existían:
una barra entre las dos pantallas cambia `screenGap` (en Apiladas y Lado a
lado), y una esquina de la secundaria cambia `secondaryScale` (en Principal +
secundaria). El modo se elige con las mismas cápsulas del menú de pausa
(`LayoutModeChips`, ahora `internal` para compartirla). Quien sí es
completamente libre —posición, tamaño y opacidad, sin más restricción que no
solaparse— son los siete grupos de mandos, que es donde `docs/entrada.md`
plantea el problema real: no hay colocación fija que sirva para las dos
orientaciones y todos los juegos.

La vista previa del editor llama a `ScreenLayout.resolve` y a
`ControlLayout.resolveProfile` con los mismos argumentos que `GameScreen`: si
algún día colocan distinto es un error de una de las dos pantallas, no una
discrepancia de diseño.

## Rendimiento: lo que hay que medir

El protocolo es el que ya se usó para el ensayo de velocidad ×2 en
`app-emulador`, en el Motorola Edge 30, con ROM libre:

- **fps emulados frente a fps presentados**, por separado. Son cosas distintas y
  confundirlas invalida la medida.
- **Porcentaje de un núcleo** del hilo de emulación, y del proceso entero.
- **Comportamiento tras varios minutos**, para ver si aparece limitación
  térmica.
- Las mismas cifras con el renderizado 3D del core por software y, si existe,
  por GL.

La hipótesis a comprobar es que con el renderizador GL de la app el coste de
presentar sea **el mismo en «Nítido» y en «Suavizado»**, a diferencia del GBA.
Si no lo es, algo está mal en el renderizador.

### Línea base contra `StubEmulatorCore` (cierre de la fase 2)

Medido en el Motorola Edge 30, disposición Apiladas en vertical, sesión
contra el stub (2.C). No hay renderizado 3D todavía —eso llega con el core
real en la fase 4—, así que esto mide únicamente el coste de presentación:
subir una textura de 256×384 y pintar dos quads.

Las cifras de fps emulados y presentados se leyeron sin añadir código de
depuración: `dumpsys SurfaceFlinger --latency` sobre la superficie del
`GLSurfaceView` da la marca de tiempo real de cada fotograma *presentado*, y
`top -H` el porcentaje de CPU por hilo. El hilo de emulación no expone
todavía su contador (`EmulatorController.framesPerSecond`) por ningún canal
externo; ver la nota al final de esta sección.

| Magnitud | Nítido (`GL_NEAREST`) | Suavizado (`GL_LINEAR`) |
|---|---|---|
| fps presentados (medidos) | 90.03 | 90.04 |
| Refresco del panel | 90 Hz | 90 Hz |
| `GLThread` (hilo de GL) | 81–89 % de un núcleo | 81–89 % de un núcleo |
| `ds-emulation` (hilo de emulación) | 0–7 % de un núcleo (≈4 % típico) | igual |
| Proceso completo | ≈90 % de un núcleo | igual |
| Temperatura CPU / GPU tras 3 min | 44–45 °C / 42–43 °C | igual |
| `Thermal Status` | 0 (nominal) en todo momento | igual |

**Se confirma la hipótesis**: Nítido y Suavizado cuestan lo mismo, con
diferencias dentro del ruido de medida (±5 puntos de `%CPU` entre muestras
consecutivas del propio Nítido). El dispositivo se probó 3 minutos seguidos
sin que `Thermal Status` se moviera de 0 ni la temperatura de CPU superase los
45 °C: sin indicio de limitación térmica a este ritmo de trabajo.

**Hallazgo que conviene arrastrar a la fase 4**: `GLThread` cuesta bastante
más de lo que dos quads deberían — el sospechoso es el bucle a nivel de
píxel de `DsGLRenderer.uploadFrame()`, que recorre los 256×384 píxeles del
framebuffer haciendo cuatro `put()` por píxel para reordenar ARGB→RGBA antes
de subirlo con `glTexSubImage2D`. Con un core real produciendo fotogramas a
59,8261 Hz de verdad —aquí el stub apenas ocupa CPU—, ese bucle pasa a competir
por el mismo núcleo que el propio core. Antes de la fase 4 conviene mirar si
el core puede entregar el framebuffer ya en el orden de bytes que espera GL
(evitando el reordenado por software) o, si no, mover el reordenado a un
formato de textura que no lo necesite.

**Lo que falta por instrumentar**: los fps *emulados* no se leyeron de un
contador real, se infieren de que `ds-emulation` está muy por debajo de
saturación (≈4 % de un núcleo): con ese margen no hay motivo para que el hilo
llegue tarde a su período de 16 715 113 ns, así que se da por buena la cifra
de diseño, 59,8261 Hz. Antes de medir el core real en la fase 4 hace falta
exponer `EmulatorController.framesPerSecond` y
`DsGLRenderer.presentedFramesPerSecond()` por algún canal legible sin
depurador —un `Log.d` periódico tras `BuildConfig.DEBUG` basta—, porque contra
un core real la suposición de «nunca llega tarde» deja de sostenerse sola.
