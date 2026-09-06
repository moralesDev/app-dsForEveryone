# Licencia

> Este documento no es un dictamen jurídico. Recoge cómo funcionan estas
> licencias y qué obligan en la práctica, para poder decidir con conocimiento.
> Si en algún momento hay dinero de por medio y dudas, la consulta profesional
> es barata comparada con el problema.

## Por qué este proyecto no puede ser cerrado

`app-emulador` es una aplicación cerrada y de pago, y puede serlo por una razón
concreta: **mGBA es MPL-2.0**, copyleft *por archivo*. La obligación alcanza a
los archivos de mGBA que se modifiquen —y no se modifica ninguno— y se detiene
ahí. Por eso la frontera de la licencia allí es la frontera entre dos carpetas:
`third_party/mgba/` intacto y `native/emulator_bridge/` propio.

En Nintendo DS no existe ese margen. Todos los cores integrables son copyleft de
**programa completo**:

| Core | Licencia | Notas |
|---|---|---|
| melonDS | **GPL-3.0-or-later** | El más preciso y el mejor mantenido |
| DeSmuME | **GPL-2.0-or-later** | Más antiguo, con BIOS por HLE |
| no$gba | Cerrada, sin fuentes | No integrable de ninguna forma |

Enlazar cualquiera de ellos dentro del mismo APK produce una obra derivada del
conjunto. La consecuencia es directa: **todo el código de la aplicación —el
Kotlin, la interfaz, el sistema de partidas, el puente— se distribuye bajo la
misma GPL.**

### Lo que no funciona

Conviene descartarlo por escrito para no volver sobre ello:

- **Separar el core en otro proceso o en otro APK y hablar por IPC.** Es el
  esquema clásico de elusión y la FSF lo trata como tal. Además rompería el
  modelo de `EmulatorCore` y añadiría latencia justo donde no sobra.
- **Cargar el core como `.so` descargado aparte.** Distribuir el binario sigue
  siendo distribuir; y si no lo distribuyes tú, estás pidiendo al usuario que
  monte el emulador a mano.
- **Usar un core libretro precompilado.** El `.so` de melonDS sigue siendo GPL.
  Cambiar el mecanismo de enlace no cambia la licencia del código enlazado.

## Qué obliga la GPL y qué no

Lo que **sí** puedes hacer:

- **Cobrar por la aplicación.** La GPL nunca ha prohibido vender. Puedes
  publicarla de pago en Google Play exactamente igual que `app-emulador`.
- **Seguir siendo el titular del copyright** de todo lo que escribas. Eres el
  autor: puedes licenciar *tu* código bajo GPL aquí y bajo términos propios en
  otro sitio, a la vez.
- **Reservarte la marca.** El nombre de la app y el logotipo son marca, no
  código. La GPL no los cede. Conviene decirlo explícitamente en el `README`:
  «el código está bajo GPL; el nombre y el logotipo no se licencian con él».

Lo que **te obliga**:

- **Entregar el código fuente completo a quien reciba el binario**, en la misma
  versión que compilaste y con lo necesario para reconstruirlo (scripts de
  build incluidos). En la práctica: repositorio público y una etiqueta por cada
  versión publicada. Tu procedimiento actual de publicación en `app-emulador`
  —etiquetar `v<versionName>+<versionCode>` el commit exacto que se subió— ya
  resuelve esto; aquí deja de ser buena higiene y pasa a ser un requisito legal.
- **Aceptar la redistribución.** Cualquiera que compre la app puede regalarla,
  publicarla o subirla a otra tienda. Es el precio real de este camino, y hay
  que decidirlo con los ojos abiertos: el ingreso posible es el de quien prefiere
  pagar en Play a buscar el APK, no el de un producto exclusivo.
- **Mantener los avisos de copyright** del core y de todo lo que se enlace.

## GPL-2.0 frente a GPL-3.0: por qué importa aquí

No son intercambiables, y la diferencia recae justo sobre la elección del core.

**GPL-2.0** (DeSmuME) es la más sencilla de convivir con una tienda de
aplicaciones. Es lo que llevan publicando durante años multitud de apps en Play.

**GPL-3.0** (melonDS) añade dos cosas que rozan el modelo de distribución de una
tienda:

- La cláusula **anti-tivoización**: si distribuyes el binario en un dispositivo
  que impide al usuario instalar una versión modificada, tienes que entregar la
  información de instalación. En Android, con sideload disponible, esto no suele
  ser un problema; en el ecosistema iOS fue exactamente lo que hizo retirar VLC
  de la App Store.
- La prohibición de **restricciones adicionales** (§10). Los términos de
  distribución de la tienda no pueden añadir límites a los derechos que la
  licencia concede.

En Google Play el conflicto es **considerablemente más suave** que en la App
Store de Apple —el Acuerdo de Distribución de Google no impone la prohibición de
copia que allí generó el choque, y hay aplicaciones GPL-3.0 publicadas sin
incidencias—, pero es un riesgo distinto de cero y merece pesarse. Si la
publicación en Play es un objetivo firme, es un argumento real a favor de
DeSmuME; si la prioridad es la calidad de emulación, melonDS lo compensa de
sobra y el riesgo se asume a sabiendas.

La decisión se toma en [`integracion-core.md`](integracion-core.md), midiendo, y
no antes.

## La trampa: compartir código con `app-emulador`

Este repositorio y `app-emulador` van a compartir mucho: Nocturne, el contenedor
de save states, `StorageManager`, el sistema de idioma. Y tienen licencias
incompatibles en un sentido. La regla es asimétrica y hay que tenerla clara:

**De `app-emulador` hacia aquí: siempre se puede.** Es tu código, eres el
titular, y puedes publicarlo bajo GPL aquí sin que eso cambie nada en el otro
repositorio. Que el mismo archivo exista bajo dos licencias distintas es
perfectamente legal cuando el autor es el mismo.

**De aquí hacia `app-emulador`: sólo si el código sigue siendo enteramente
tuyo.** En el momento en que aceptes una contribución externa —un *pull
request*, un parche de un usuario, un fragmento copiado de melonDS— ese código
queda atado a la GPL y **no puede volver** al repositorio cerrado. No hay forma
de deshacerlo salvo reescribirlo.

Consecuencias prácticas:

- Marca en el `README` de este repositorio que las contribuciones externas se
  aceptan bajo GPL y que el proyecto no pide cesión de derechos. Es honesto y
  evita malentendidos.
- Si en algún momento quieres que una pieza viva en los dos repositorios a
  largo plazo, escríbela **aquí primero, tú solo**, o mejor: escríbela en un
  módulo aparte con licencia permisiva propia (MIT o Apache-2.0) que ambos
  consuman. Un módulo permisivo se puede enlazar desde un programa GPL sin
  problema; al revés no.
- Antes de portar cualquier archivo de aquí hacia `app-emulador`, mira el
  historial de git de ese archivo. Si sólo apareces tú, adelante.

## Lo que hay que dejar hecho en el repositorio

Cuando empiece a haber código:

1. **`LICENSE`** en la raíz con el texto completo de la GPL que corresponda.
2. **Cabecera de licencia** en los archivos propios, o al menos una nota
   inequívoca en el `README` de que todo el repositorio está bajo esa licencia.
3. **`third_party/<core>/`** con su `LICENSE` original copiado y **sin
   modificar nada**. Aunque aquí la GPL no obliga a la separación por carpetas
   como sí ocurría con MPL-2.0, la disciplina sigue valiendo: hace trivial
   actualizar el core y deja claro qué código es de quién.
4. **Pantalla de licencias en la app** —ya existe una en `app-emulador`
   (`ui/about/LicensesScreen.kt`)—, con el aviso del core y el enlace al código
   fuente. En una app GPL esa pantalla no es cortesía: es parte de cómo se
   cumple la obligación de informar al usuario de sus derechos.
5. **Una etiqueta de git por cada versión publicada**, apuntando al commit
   exacto del que salió el binario.

## Sobre las ROMs, la BIOS y el firmware

Nada de esto se distribuye, ni se descarga, ni se enlaza a sitios donde
conseguirlo. La aplicación:

- No incluye ROMs.
- No incluye BIOS ni firmware. Si el core elegido los necesita, los aporta el
  usuario desde su propia consola, y la app se limita a leerlos por SAF.
- No lleva red. Sin red no hay descarga posible, y eso es una garantía
  estructural, no una promesa.

Es exactamente la postura de `app-emulador` y es la que mantiene el proyecto en
terreno defendible.
