package com.moralesDev.dsForEveryone.ui.game.gamepad

import android.view.KeyEvent

internal actual fun keyCodeName(keyCode: Int): String =
    KeyEvent.keyCodeToString(keyCode).removePrefix("KEYCODE_")
