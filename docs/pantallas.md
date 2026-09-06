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
