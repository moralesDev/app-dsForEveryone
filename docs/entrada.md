# Entrada: lápiz, botones y disposición

La DS tiene tres entradas que la GBA no tiene: una **pantalla táctil**, un
**micrófono** y **dos botones más**. Cada una obliga a algo.

## 1. El lápiz con el dedo

### La frontera

`EmulatorCore` gana un método:

```kotlin
fun setTouch(x: Int, y: Int, down: Boolean)   // x en 0..255, y en 0..191
```

Y una regla que no es negociable: **la DS lee la pantalla táctil una vez por
fotograma.** Mientras el dedo esté abajo hay que reportar posición **en cada
fotograma**, no sólo cuando Compose entrega un evento de movimiento. Un arrastre
—dibujar en *Pictochat*, trazar una línea en *Kirby: Power Paintbrush*, cortar
en *Elite Beat Agents*— es un flujo continuo de posiciones, y si sólo se
actualiza cuando llega un evento, el trazo sale a trompicones y el juego lo
interpreta mal.

La forma correcta es la que ya usa `InputManager` para los botones: **un estado
compartido que el hilo de emulación lee al principio de cada fotograma.** La UI
escribe la última posición conocida; el bucle la consume. Nada de encolar un
comando por evento táctil.

### `TouchRouter`: de dedo a coordenada

Geometría pura, fuera de Compose, como `ScreenLayout` y por el mismo motivo:

```
TouchRouter.map(punto: Offset, placement: ScreenPlacement) : DsPoint?
```

Devuelve `null` si el punto cae fuera del rectángulo de la pantalla inferior.
Las afirmaciones que hay que probar en JVM:

- Un punto fuera de la pantalla táctil **no produce toque**. Ni recortado al
  borde: no produce toque.
- Las cuatro esquinas dan exactamente `(0,0)` y `(255,191)`.
- El recorte ocurre **en** el borde, no un píxel después.
- Funciona igual con las pantallas intercambiadas: lo táctil es la pantalla
  *lógica* inferior, se pinte donde se pinte.

### Convivencia con los mandos

Aquí no hay que inventar nada: `pressable` (`ui/game/controls/Pressable.kt` de
`app-emulador`) ya usa `awaitEachGesture` y sigue **cada puntero por su
identificador**, con un `finally` que suelta el botón si el gesto se cancela.
Compose entrega cada puntero al nodo sobre el que bajó, así que un dedo en la
cruceta y otro en la pantalla táctil funcionan a la vez sin tratamiento
especial.

**No añadas lógica de exclusividad.** La pantalla táctil es un puntero más, con
su propio nodo y su propio gesto. El día que alguien intente «arbitrar» entre
mandos y táctil es el día que se rompen los juegos que piden las dos cosas a la
vez (*Metroid Prime Hunters*, con cruceta para moverse y lápiz para apuntar).

En vertical los mandos van **encima** de la pantalla inferior (ver la sección
4). Tampoco ahí hace falta arbitrar: los mandos se componen después que la
superficie táctil, así que un dedo que baja sobre un mando es del mando, y uno
que baja en cualquier otro punto de la pantalla inferior es del lápiz. Compose
no reparte un mismo toque entre hermanos superpuestos.

### Joystick o cruceta

La dirección se maneja con un **joystick** por defecto, o con la cruceta de
siempre (Configuración → Entrada → Control de dirección). La DS no tiene stick
analógico: el joystick es otra forma de pulsar la misma cruceta, y produce
exactamente lo mismo, uno o dos `DsButton` adyacentes. Cambia la geometría
(`JoystickGeometry`): ocho sectores iguales de 45° en vez de diagonales
estrechas, porque con un stick se apunta con un ángulo, y una zona muerta
mayor (30 % del radio), porque el pulgar descansa encima de la bola. La bola
—un círculo transparente dentro de un aro fino, nada más— sigue al dedo y
vuelve al centro en el mismo `finally` que suelta las direcciones.

### El problema real: un dedo no es un lápiz

El lápiz de la DS es un punto. Un dedo cubre unos 9 mm y **tapa lo que toca**.
Hay juegos que esto no rompe (menús, mapas) y juegos donde sí (puntería,
escritura, precisión al píxel). Hacen falta al menos estos tres modos, elegibles
por juego:

**Toque directo.** El dedo *es* el lápiz. Simple, funciona en la mayoría, es el
que va por defecto.

**Toque con desplazamiento.** El punto de contacto se sitúa unos milímetros por
encima del dedo, con el desplazamiento configurable. Resuelve el problema de
tapar el objetivo sin cambiar el modelo de interacción. Es barato y se
agradece mucho.

**Cursor virtual.** Un puntero que se mueve con la cruceta —o con un joystick
analógico si hay mando físico— y se «pulsa» con un botón. Lento pero exacto.
Para los juegos donde el dedo simplemente no da. Es también la única forma de
usar la pantalla táctil con un mando Bluetooth, que ya está en la hoja de ruta
del proyecto hermano.

Y un detalle que parece menor y no lo es: cuando la pantalla táctil se pinta
**pequeña** —modo «principal + secundaria reducida»— la precisión efectiva se
desploma. Conviene que al tocar la pantalla reducida se pueda ampliar
temporalmente, o directamente impedir que la táctil sea la reducida salvo que el
usuario insista.

## 2. El micrófono

Bastantes juegos piden soplar o hacer ruido: *Nintendogs*, *Zelda: Phantom
Hourglass* (apagar velas), *Mario Kart DS* (globos). Sin micrófono, esos juegos
se atascan en un punto del que no se sale.

El manifiesto de `app-emulador` **no declara ni un permiso**, y esa es una
propiedad del proyecto que merece defenderse. La solución estándar y sin
permisos:

> **Un botón de «soplar»** que, mientras está pulsado, inyecta ruido blanco en
> la entrada de micrófono del core.

Funciona en prácticamente todos los juegos que lo piden, porque casi ninguno
analiza el sonido: sólo mide amplitud. Es un botón más en la disposición de
controles, activable por juego.

El micrófono real (`RECORD_AUDIO`) queda como **decisión expresa y aplazada**.
Rompe la regla de cero permisos, obliga a un diálogo de permiso en una app que
hoy no pide ninguno, y aporta poco por encima del ruido simulado.

## 2 bis. La tapa

La DS tiene una entrada más que no es un botón: **la bisagra**. Cerrar la tapa
pone un bit (`KeyInput`, bit 23) y abrirla dispara una interrupción
(`IRQ_LidOpen`). Unos cuantos juegos la usan como mecánica —cerrar la tapa
para estampar un símbolo en *Phantom Hourglass*, para que un personaje
reaccione— y **sin ella no se puede avanzar**: no es un adorno de realismo, es
una entrada que falta.

**No es un octavo grupo de mandos.** Se cierra desde el menú de pausa, y la
razón es la misma que hizo salir de la pantalla al botón de avance rápido:
encima del juego cabe un botón, el de menú, y cada uno de más tapa justo la
esquina de la pantalla superior. Además, la tapa se usa una o dos veces por
partida, no cada minuto.

**Cerrar la tapa reanuda la emulación.** Es lo que hace que la opción funcione
desde un menú de pausa: con el hilo de emulación detenido, poner el bit no lo
lee nadie —el juego reacciona mientras corre, no mientras está parado—. Así
que «Cerrar la tapa» cierra el menú, cierra la tapa y sigue jugando.

Encima aparece **un velo** que tapa las dos pantallas, como haría la carcasa, y
que absorbe todos los toques: con la tapa bajada no se puede pulsar nada de la
consola. El único gesto posible es abrirla —tocar el velo, o el botón atrás—.
Por debajo el juego **sigue corriendo**, que es exactamente el sentido de todo
esto.

Dos detalles que no son opcionales:

- **La tapa sólo se toca cuando cambia.** El hilo de emulación no reenvía su
  estado cada fotograma como hace con la máscara de botones: abrirla dispara
  una interrupción, y repetirla sesenta veces por segundo sería un chaparrón
  que ningún hardware real produce.
- **`releaseAll` no la abre.** Soltar todos los botones al pausar, al irse a
  otra aplicación o al perder el foco no levanta la tapa: una consola con la
  tapa bajada sigue con la tapa bajada. Sólo la abre quien la cerró.

## 3. Doce botones donde había diez

### El mapa

| GBA (10) | DS (12) |
|---|---|
| Cruceta, A, B, L, R, Start, Select | Cruceta, **A, B, X, Y**, L, R, Start, Select |

A/B deja de ser un par y pasa a ser un **rombo de cuatro**. Con eso, el grupo de
acción crece de aproximadamente dos diámetros de botón a tres, justo cuando las
dos pantallas dejan menos sitio.

### El mapa de bits no se copia del GBA

En `app-emulador`, `GbaButton.bit` calca `KEYINPUT` y todo encaja. **En DS no.**
X e Y no viven en `KEYINPUT` sino en `EXTKEYIN`, y cada core empaqueta su propia
máscara de 12 bits como le conviene.

`DsButton.bit` **se copia de las cabeceras del core elegido**, no se deduce del
hardware, y se verifica con una prueba que pulse cada botón por separado contra
el core real. Un botón mal mapeado no da error: da un juego que se comporta raro
y una tarde perdida.

### Diagonales en la cruceta

`DirectionalPadZones` en `app-emulador` reparte el cuadrado en cuñas que no se
solapan y **hoy no produce diagonales, a propósito**. Para la GBA es una
decisión defendible.

Para la DS no lo es tanto. *Mario Kart DS*, *New Super Mario Bros.* y buena
parte del catálogo de plataformas y carreras piden diagonal de verdad. La
cruceta de este proyecto **debe dar ocho direcciones**, con las cuñas
diagonales bien dimensionadas: demasiado estrechas y no se alcanzan, demasiado
anchas y se activan sin querer. Es un parámetro a ajustar midiendo con el dedo,
no calculando.

**Medido (cierre de la fase 2):** `DirectionalPadZones.DEFAULT_DIAGONAL_WIDTH_DEGREES`
se dejó en 40° en el entregable 2.3, marcado como provisional. Probado con el
dedo en el Motorola Edge 30 contra `StubEmulatorCore` —las ocho direcciones,
incluido deslizar de una cardinal a la diagonal contigua sin levantar el
dedo—, 40° resultó cómodo: se alcanzan las diagonales sin que las cardinales
se disparen por error. Se deja la constante como está; el comentario
«provisional» en el código puede quitarse cuando se pruebe con un juego real
que dependa de precisión diagonal (fase 4).

Se mantiene lo que sí funciona: es **un único control**, no cuatro botones, de
modo que deslizar de arriba a la diagonal y a la derecha sin levantar el dedo
funciona; y lo que se pinta viene de la máscara real del `InputManager`, no de
un estado local del composable.

## 4. El editor de disposición de controles

En `app-emulador` esto es una funcionalidad pendiente, nacida de un problema
concreto: en horizontal es imposible llegar a tiempo al botón R en *Mario Kart*.

**Aquí no es una funcionalidad: es un requisito de la primera versión.** El
motivo es aritmética de espacio:

- Doce entradas en lugar de diez, con el grupo de acción un 50 % más grande.
- En vertical, las pantallas apiladas ocupan **2:3** frente al **3:2** de la
  GBA: mucho menos alto libre para los mandos.
- En horizontal, los mandos tienen que **superponerse** a los lados del área de
  pantallas, porque lado a lado da **8:3** y no queda banda inferior utilizable.
- Y la pantalla táctil es a la vez zona de juego.

No existe una colocación fija que sirva para las dos orientaciones y para todos
los juegos. Lo que hay que dar:

- **Grupos móviles**: cruceta, A/B/X/Y, L, R, Start+Select, y el botón de
  soplar. Siete grupos.
- **Que no se solapen entre sí.** En horizontal tampoco con la pantalla
  táctil; en vertical van encima de ella a propósito (ver abajo).
- **Perfiles independientes por orientación**, vertical y horizontal.
- **Tamaño y opacidad** por grupo, no sólo posición: en una tablet los botones
  del tamaño de un teléfono son ridículos, y es exactamente el problema que hoy
  tiene el home del proyecto hermano.
- **Sólo editable con la emulación detenida.**

Y lo dicho en [`pantallas.md`](pantallas.md): esto y la colocación de las
pantallas son el **mismo problema** —rectángulos con restricciones dentro de un
área— y se resuelven con un solo `LayoutEngine`.

### En vertical, los mandos encima de la pantalla inferior

La primera versión ponía los siete grupos en una banda **debajo** de la táctil,
sin tocarla. En un teléfono 20:9 cabía; en uno 16:9 la banda quedaba tan baja
que todo se encogía, y la colocación —L, soplar y R en una fila, cruceta y
rombo en otra— no se parecía a una consola. `ControlLayout.overlayProfile`
la sustituye en vertical:

```
[ L ]                          [ R ]
(soplar)                         (>)
( DIRECCIÓN )             ( A·B·X·Y )
         [select] [start]
```

- Las dos pantallas van **centradas** a lo alto; lo que sobra se reparte
  arriba y abajo.
- Cada grupo va del lado de su mano: L, soplar, dirección y Select a la
  izquierda; R, rombo y Start a la derecha. Select y Start, abajo y pegados
  al centro, como pastillas con el nombre debajo.
- Los dos racimos **cuelgan del techo**: L y R empiezan justo 0,5 cm por
  debajo del centro del hueco (nunca por encima de la pantalla de arriba,
  `ControlLayout.overlayCeiling`) y el resto baja desde ahí; lo que sobra
  queda libre abajo, sin llevar Start y Select al filo. Si no caben entre el
  techo y el borde, todo encoge por igual. El editor tampoco deja arrastrar un mando
  por encima de ese techo.
- La dirección y el rombo se separan **3 mm** de su borde hacia el centro.
- Select y Start no van en el bloque: van **0,5 cm por debajo de la
  pantalla inferior** (`ControlLayout.lowerScreenBottom`), centrados. Si ahí
  pisaran la dirección o el rombo, bajan hasta librarlos; si no caben antes
  del borde, se quedan en el bloque.
- Estas tres medidas son **físicas**, no dp: se convierten con
  `xdpi`/`ydpi` (`rememberPxPerMm`), porque lo que se pidió es lo que mide
  una regla sobre el cristal.
- Entre las dos pantallas, que ahora se separan un 5 % por defecto, van los
  botones de **velocidad** 1x (por defecto), 1,5x y 2x. El menú de pausa
  conserva el mismo ajuste.
- Todo es **transparente con borde y letras claras** (`GhostSurface`): el
  juego se ve a través. Los tamaños son pequeños a propósito salvo la
  dirección: el rombo un 20 % menor que ella y con sus cuatro botones sin
  tocarse; L y R finos (la mitad de alto y un 25 % menos de largo que en la
  banda horizontal); soplar, la mitad del mínimo táctil; Start y Select, la
  mitad que antes.
- La táctil sigue **debajo**: todo lo que no cae en un mando le llega al
  juego. Los botones redondos recortan su forma, así que las esquinas de su
  caja no roban toques.
- El botón **«>»**, debajo de R a la altura de soplar
  (`ControlLayout.hideToggleBounds`), aparta los mandos —la izquierda sale
  por la izquierda, la derecha por la derecha— y deja la pantalla inferior
  entera para el lápiz; **«<»** los devuelve. Del todo apartados, los mandos
  dejan de componerse, así que no queda ningún botón pegado. Sólo existe en
  vertical: en horizontal los mandos no tapan la táctil.

El perfil vertical guardado cambió de clave (`portrait_controls_overlay`): uno
hecho con la regla anterior de no tocar la táctil no se trae, se cae al perfil
nuevo.

### El área es la utilizable, no la ventana

Ese «área» es la ventana **menos las barras del sistema**, la misma que usan
las pantallas (ver [`pantallas.md`](pantallas.md)). Un mando debajo de una
barra no es un mando medio tapado: es un mando que **no se puede pulsar**,
porque ese toque se lo queda el sistema. Y no basta con la de abajo — en
horizontal, con los tres botones, la barra de navegación se coloca a un lado, y
la de estado se come la fila de arriba, que es justo donde viven L y R.

Por eso `ControlLayout` trabaja sobre un `LayoutRect` con coordenadas
absolutas y no sobre un `LayoutSize` desde (0,0): el origen del hueco importa.
Las fracciones que se persisten son relativas a ese hueco utilizable, así que
un perfil guardado sigue estando dentro aunque cambien las barras —otro
dispositivo, gestos en vez de tres botones, la otra orientación—.

## 5. Mando físico (Bluetooth y USB)

Android no distingue transporte para un mando de juego: uno emparejado por
Bluetooth y uno por USB-OTG llegan **idénticos**, como un `InputDevice`
genérico (`SOURCE_GAMEPAD` / `SOURCE_JOYSTICK`) que entrega `KeyEvent` y
`MotionEvent` normales. No hace falta declarar `BLUETOOTH` ni ningún permiso
nuevo — la regla de cero permisos del proyecto sigue intacta, y el soporte USB
que ya se dejó anotado como pendiente sale del mismo código.

### Detección

Un componente propio (`GamepadManager`, distinto del `InputManager` del
proyecto, que es el que ya existe para inyectar botones) envuelve el
`InputManager` **del sistema** Android y su `InputDeviceListener`
(`onInputDeviceAdded`/`Removed`/`Changed`), filtrando por `sources` para
descartar teclados y dispositivos virtuales. Enumera también lo que ya estaba
conectado al abrir la app, no sólo lo que se conecta después. Cada conexión
nueva dispara un `UiMessage` («Mando detectado: ‹nombre›»), con el mismo
mecanismo que ya usan los mensajes de guardado.

### El mapeo es por mando, no global

Se identifica cada mando por su `descriptor` (estable entre conexiones,
sobrevive a que el usuario tenga dos mandos distintos) y cada uno guarda su
propio mapeo, persistido con el mismo patrón de texto plano que
`ControlLayout.encode`/`decode`. Sin mapeo guardado, arranca con la
asignación estándar de Android para mandos (`KEYCODE_BUTTON_A/B/X/Y`,
`DPAD_*`, `BUTTON_L1/R1`, `BUTTON_START/SELECT`), que ya normaliza
razonablemente bien entre fabricantes — se puede jugar de inmediato con un
mando corriente, y remapear queda para quien quiera cambiar algo o tenga un
mando poco convencional.

El botón de **soplar** entra en el mapeo igual que los doce de `DsButton`: es
la única forma de que un juego que pide soplido (§2) sea jugable por completo
sin tocar la pantalla. No vive en el enum `DsButton` —nunca formó parte de la
máscara de 12 bits del core— así que el mapeo lo trata como un objetivo
aparte, no como un botón trece.

### Pantalla limpia en horizontal

El editor de disposición (§4) resuelve dónde van los mandos en pantalla; esto
resuelve cuándo **no hacen falta**. La partida está bloqueada a vertical desde
el entregable que dio forma final a los mandos en pantalla — encajar doce
mandos táctiles en horizontal sin tapar la pantalla inferior no compensaba, y
por eso se retiró la liberación de orientación (`FreeOrientation`) que había
antes. Un mando físico cambia el cálculo: ya no hay doce zonas táctiles que
encajar, así que la orientación se libera **mientras haya un mando
conectado**, y sólo entonces. Sin mando, sigue bloqueada a vertical como hoy.

Con mando conectado y en horizontal, `ControlGroups` no se pinta —nada que
tocar en pantalla, salvo lo que sigue igual: la pantalla táctil sigue activa
para lápiz/dedo (un mando no sustituye al lápiz, ver §1) y el botón de menú
—ya independiente de los siete grupos de mandos, arriba a la derecha— sigue
donde está, dando acceso al menú de pausa existente.

### Capturar «pulsa un botón»

La pantalla de mapeo necesita interceptar el próximo `KeyEvent` de un mando
concreto y **no** dejarlo llegar al juego. Se intercepta en el punto más alto
posible (`Activity.dispatchKeyEvent`) y se enruta con el mismo patrón de
estado compartido que ya usa el táctil: si hay un capturador activo, el evento
va para él; si no, entra al juego por el camino de siempre
(`InputManager.setPressed`). Nunca cola de comandos, nunca lógica de
exclusividad añadida aparte de esa.

## 6. Qué se prueba en JVM

Nada de esto necesita dispositivo ni Robolectric, y todo puede fallar en
silencio:

- `TouchRouter`: recorte, esquinas exactas, fuera de área, pantallas
  intercambiadas.
- `DirectionalPadZones` con diagonales: que las ocho cuñas cubran el círculo sin
  solaparse y sin huecos.
- `ControlLayout`: que ningún grupo se solape con otro con cualquier tamaño
  de grupo; en horizontal, tampoco con la pantalla táctil; en vertical, que
  cada grupo quede del lado de su mano y en su orden (L, soplar, dirección).
- `JoystickGeometry`: zona muerta, los ocho sectores de 45°, que nunca dé dos
  direcciones opuestas y que la bola no se salga de su recorrido.
- `DsButton`: que la máscara producida por una combinación de botones sea la que
  el core espera. Con un `FakeEmulatorCore` que registre lo que recibe.
- `GamepadMapping`: que `encode`/`decode` sean inversos, que el mapeo por
  defecto cubra los doce botones más el soplido, y que dos mandos con
  descriptores distintos no se pisen el mapeo. Lo que no se puede probar en
  JVM —`InputDevice`, `KeyEvent` reales— queda para el dispositivo, igual que
  ya pasa con `ParcelFileDescriptor` en `RomRepositoryTest`.
