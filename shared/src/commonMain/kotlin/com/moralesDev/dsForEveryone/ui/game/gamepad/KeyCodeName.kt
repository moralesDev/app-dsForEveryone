package com.moralesDev.dsForEveryone.ui.game.gamepad

/**
 * El nombre de una tecla que no está en `GamepadKeyCodes`. En Android, el de
 * `KeyEvent` (un mando puede mandar cualquier tecla); en iOS no llega ninguna
 * fuera de esa lista, así que basta con el número.
 */
internal expect fun keyCodeName(keyCode: Int): String
