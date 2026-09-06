package com.moralesDev.dsForEveryone.emulator

/**
 * Los doce botones de la Nintendo DS.
 *
 * A diferencia de `GbaButton` en `app-emulador`, aquí el valor de [bit] **no
 * se puede deducir del hardware**: X e Y no viven en el registro KEYINPUT
 * sino en EXTKEYIN, y cada core (melonDS, DeSmuME) empaqueta su propia
 * máscara de 12 bits a su manera. Ver docs/entrada.md, sección «El mapa de
 * bits no se copia del GBA».
 *
 * El orden de abajo es **provisional**: sirve para ejercitar `InputManager`,
 * `DirectionalPadZones` y `ControlLayout` contra el stub. Se sustituye en el
 * entregable 4.3 por el que declaren las cabeceras del core elegido, copiado
 * literalmente — nunca inventado ni deducido —, y se verifica con una prueba
 * que pulse cada botón por separado contra el core real.
 */
enum class DsButton(val bit: Int) {
    UP(0),
    DOWN(1),
    LEFT(2),
    RIGHT(3),
    A(4),
    B(5),
    X(6),
    Y(7),
    L(8),
    R(9),
    START(10),
    SELECT(11),
    ;

    val mask: Int get() = 1 shl bit
}
