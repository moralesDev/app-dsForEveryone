package com.moralesDev.dsForEveryone.ui.game

import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.LocalWindowInfo

@OptIn(ExperimentalComposeUiApi::class)
@Composable
actual fun rememberIsPortrait(): Boolean {
    val size = LocalWindowInfo.current.containerSize
    return size.height >= size.width
}
