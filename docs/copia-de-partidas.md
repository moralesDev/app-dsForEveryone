# Copia de partidas: exportar e importar

## Por qué

El manifiesto declara `allowBackup="false"` y todas las partidas viven en
`filesDir`. Eso protege las partidas de otras aplicaciones, pero también
significa que desinstalar, reinstalar o cambiar de móvil las borra todas.
Extraer e importar el `.sav` de un juego ya existía; los save states —incluido
el respaldo automático— no tenían salida. Esto la da.

## Dos entradas, un formato

- **Por juego**, desde la hoja de opciones de la ROM en la biblioteca:
  «Exportar partidas» / «Importar partidas».
- **Todo**, desde Configuración → Partidas: «Exportar todas» / «Importar
  copia». Es la que sirve para cambiar de móvil.

Las dos producen y leen exactamente el mismo `.zip`; uno «por juego» es
simplemente una copia con un solo juego dentro. Importar desde la hoja de un
juego acepta también una copia completa: toma de ella sólo ese juego.

```
dsforeveryone-saves.json          ← manifiesto, siempre la primera entrada
saves/<sha256>/battery.sav
saves/<sha256>/cheats.json
saves/<sha256>/states/slot_1.state … slot_5.state, auto.state
saves/<sha256>/states/slot_1.png  … slot_5.png,   auto.png
```

Es literalmente el árbol de `filesDir/saves/`. La identidad de cada juego es el
SHA-256 de su ROM, igual que en disco: por eso una copia puede traer partidas
de juegos que todavía no están en la biblioteca, y aparecen solas en cuanto se
añade la ROM, sin ningún paso extra.

El manifiesto lleva la marca del formato, su versión, la fecha y la lista de
juegos con su título. El título es sólo para enseñarlo al confirmar —«vas a
reemplazar las partidas de *Tal juego*»—: nunca decide nada.

**No viajan** las portadas elegidas a mano (no son partidas; viven fuera de
`saves/` por ese mismo motivo) ni los archivos de sistema.

## Importar: validar todo, luego decidir, luego cambiar

La importación tiene tres pasos separados, y el usuario sólo ve el segundo:

1. **Preparar.** Se lee el `.zip` entero y cada entrada se valida y se copia a
   una carpeta de preparación (`filesDir/import-staging/`). Nada de `saves/`
   se toca todavía.
2. **Confirmar.** Con la copia ya validada, la hoja dice exactamente qué
   juegos trae y cuáles de ellos ya tenían partidas en este móvil.
3. **Reemplazar.** Juego a juego, la carpeta preparada sustituye a la actual.

Reemplazar es la semántica elegida, no fusionar: lo que queda después es
exactamente lo que se exportó. Fusionar por ranura podría dejar un `.sav` de un
sitio con estados de otro, y eso es justo la clase de partida imposible que el
contenedor E4FS existe para impedir. Los juegos que no vienen en la copia no se
tocan.

### Lo que se rechaza

La regla de siempre: lo que importa no es leer bien una copia buena, es
**negarse a leer una mala**. Basta un fallo para rechazar la copia entera; no
hay importaciones a medias.

- Sin manifiesto como primera entrada, o con otra marca: no es una copia.
- Versión de formato mayor que la conocida: copia de una versión más nueva.
- Cualquier entrada fuera de la lista de nombres permitidos —incluidas rutas
  con `..`, absolutas o con mayúsculas en el hash—. No hay *zip slip* posible
  porque ninguna ruta se construye a partir del nombre de la entrada: se
  reconoce con una expresión cerrada y se reconstruye.
- Entradas duplicadas.
- Topes por tipo de archivo, contados sobre los bytes descomprimidos reales y
  no sobre el tamaño que declara el `.zip`; y un tope total.
- Un `.state` que no supera `SaveStateContainer.decode` completo (firma,
  versión, integridad del payload), o cuyo `romSha256` no es el de su carpeta,
  o cuyo número de ranura no es el de su nombre.
- Un `cheats.json` que no se deja leer como `CheatsFileDto`.
- Una miniatura que no empieza por la firma PNG.
- Un `battery.sav` vacío.
- Un manifiesto cuya lista de juegos no coincide con las carpetas que trae.

Lo que **no** se comprueba al importar es si el estado es de la versión actual
del core. Un estado de otra versión es un archivo íntegro y es del usuario; la
hoja de ranuras ya lo enseña como «ocupado pero no restaurable», que es lo
correcto. Rechazarlo aquí lo borraría en silencio al reemplazar la carpeta.

### Exportar sólo lo que se podría importar

Una copia que la propia aplicación no puede volver a leer es peor que no tener
copia. Por eso la exportación aplica a cada archivo las mismas comprobaciones
que la importación y **deja fuera** lo que no las pase —un estado truncado por
un corte de corriente, por ejemplo— avisando de cuántos se omitieron.

### Reemplazo y cortes a mitad

Cada juego se reemplaza con dos `rename` dentro del mismo sistema de archivos:

```
saves/<id>            → saves/.replaced-<id>
import-staging/…/<id> → saves/<id>
borrar saves/.replaced-<id>
```

Si el proceso muere entre los dos, al arrancar la aplicación
(`SaveArchive.recoverInterrupted`) encuentra `.replaced-<id>` sin `<id>` y lo
devuelve a su sitio; si encuentra los dos, la importación había terminado y
borra el sobrante. La carpeta de preparación se vacía en el mismo paso: una
importación sin confirmar no sobrevive a la muerte del proceso, y no debe
dejar megas huérfanos.
