package com.moralesDev.dsForEveryone.ui.game

import androidx.annotation.StringRes
import com.moralesDev.dsForEveryone.R
import com.moralesDev.dsForEveryone.data.save.StateRejection
import com.moralesDev.dsForEveryone.ui.UiMessage

/**
 * Traducción de los motivos de rechazo a algo que se pueda leer.
 *
 * El usuario no tiene por qué saber qué es un contenedor, un payload ni un
 * hash. Lo que necesita saber es si el problema es suyo —se equivocó de
 * juego— o del archivo, y si puede hacer algo al respecto. Las excepciones y
 * los números se quedan en el registro.
 *
 * Vive en la capa de interfaz a propósito: [StateRejection] describe un hecho
 * técnico y no debe conocer ni recursos ni idiomas.
 */
fun StateRejection.toMessage(): UiMessage = when (this) {
    StateRejection.NotAContainer -> UiMessage(R.string.msg_state_not_a_state)
    is StateRejection.UnsupportedContainer -> UiMessage(R.string.msg_state_other_container)
    StateRejection.Truncated -> UiMessage(R.string.msg_state_truncated)
    StateRejection.Corrupt -> UiMessage(R.string.msg_state_corrupt)
    StateRejection.OtherGame -> UiMessage(R.string.msg_state_other_game)
    is StateRejection.OtherCore -> UiMessage(R.string.msg_state_other_core, core)
    is StateRejection.UnexpectedSize -> UiMessage(R.string.msg_state_wrong_size)
}

/** Etiqueta corta para la fila de la ranura, donde iría la fecha. */
@get:StringRes
val StateRejection.shortLabel: Int
    get() = when (this) {
        StateRejection.OtherGame -> R.string.st_problem_other_game
        is StateRejection.OtherCore -> R.string.st_problem_other_core
        is StateRejection.UnsupportedContainer -> R.string.st_problem_other_version
        StateRejection.NotAContainer -> R.string.st_problem_unknown
        StateRejection.Truncated, StateRejection.Corrupt -> R.string.st_problem_damaged
        is StateRejection.UnexpectedSize -> R.string.st_problem_other_core
    }
