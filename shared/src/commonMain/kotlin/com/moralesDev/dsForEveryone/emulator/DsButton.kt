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
 * Orden copiado literalmente de `EmuInstance::buttonNames` en el frontend de
 * referencia de melonDS (`frontend/qt_sdl/EmuInstanceInput.cpp`), no
 * deducido: A, B, Select, Start, Right, Left, Up, Down, R, L, X, Y. Ese mismo
 * orden es el que espera `NDS::SetKeyMask` (bits 0-9 en `key_lo`, 10-11 en
 * `key_hi` → EXTKEYIN). El puente invierte la polaridad (aquí 1 = pulsado;
 * `KEYINPUT`/`EXTKEYIN` son activos a nivel bajo) — ver `ds_bridge.cpp`.
 */
enum class DsButton(val bit: Int) {
    A(0),
    B(1),
    SELECT(2),
    START(3),
    RIGHT(4),
    LEFT(5),
    UP(6),
    DOWN(7),
    R(8),
    L(9),
    X(10),
    Y(11),
    ;

    val mask: Int get() = 1 shl bit
}
