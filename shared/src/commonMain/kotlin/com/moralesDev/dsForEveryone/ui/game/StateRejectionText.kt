package com.moralesDev.dsForEveryone.ui.game

import com.moralesDev.dsForEveryone.data.save.StateRejection
import com.moralesDev.dsForEveryone.ui.UiMessage
import com.moralesDev.dsForEveryone.resources.Res
import org.jetbrains.compose.resources.StringResource
import com.moralesDev.dsForEveryone.resources.msg_state_corrupt
import com.moralesDev.dsForEveryone.resources.msg_state_not_a_state
import com.moralesDev.dsForEveryone.resources.msg_state_other_container
import com.moralesDev.dsForEveryone.resources.msg_state_other_core
import com.moralesDev.dsForEveryone.resources.msg_state_other_game
import com.moralesDev.dsForEveryone.resources.msg_state_truncated
import com.moralesDev.dsForEveryone.resources.msg_state_wrong_size
import com.moralesDev.dsForEveryone.resources.st_problem_damaged
import com.moralesDev.dsForEveryone.resources.st_problem_other_core
import com.moralesDev.dsForEveryone.resources.st_problem_other_game
import com.moralesDev.dsForEveryone.resources.st_problem_other_version
import com.moralesDev.dsForEveryone.resources.st_problem_unknown

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
    StateRejection.NotAContainer -> UiMessage(Res.string.msg_state_not_a_state)
    is StateRejection.UnsupportedContainer -> UiMessage(Res.string.msg_state_other_container)
    StateRejection.Truncated -> UiMessage(Res.string.msg_state_truncated)
    StateRejection.Corrupt -> UiMessage(Res.string.msg_state_corrupt)
    StateRejection.OtherGame -> UiMessage(Res.string.msg_state_other_game)
    is StateRejection.OtherCore -> UiMessage(Res.string.msg_state_other_core, core)
    is StateRejection.UnexpectedSize -> UiMessage(Res.string.msg_state_wrong_size)
}

/** Etiqueta corta para la fila de la ranura, donde iría la fecha. */
val StateRejection.shortLabel: StringResource
    get() = when (this) {
        StateRejection.OtherGame -> Res.string.st_problem_other_game
        is StateRejection.OtherCore -> Res.string.st_problem_other_core
        is StateRejection.UnsupportedContainer -> Res.string.st_problem_other_version
        StateRejection.NotAContainer -> Res.string.st_problem_unknown
        StateRejection.Truncated, StateRejection.Corrupt -> Res.string.st_problem_damaged
        is StateRejection.UnexpectedSize -> Res.string.st_problem_other_core
    }
