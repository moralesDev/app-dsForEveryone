package com.moralesDev.dsForEveryone.emulator.core

/**
 * Una clave Action Replay tal como la aplica el core y tal como se persiste.
 *
 * A diferencia de `app-emulador` (mGBA trae tres formatos ambiguos —
 * GameShark, CodeBreaker, Pro Action Replay— y hay que resolver cuál es cada
 * clave) aquí no hace falta elegir nada: melonDS sólo trae un motor de
 * trucos, Action Replay (`third_party/melonDS/src/AREngine.cpp`), así que
 * [CheatEntry] no lleva ningún campo de formato.
 *
 * [lines] son las líneas físicas ya separadas, sin saltos de línea sobrantes
 * ni líneas en blanco intercaladas: eso se limpia antes de llegar aquí. El
 * puente las une con `'\n'` sólo para cruzar JNI.
 */
data class CheatEntry(
    val id: String,
    val label: String,
    val lines: List<String>,
    val enabled: Boolean,
)

/**
 * Por qué el core rechazó la entrada [CheatEntry.id] al aplicarla.
 *
 * Sólo [InvalidLine]: a diferencia de mGBA, `AREngine::RunCheat` no expone
 * ninguna señal de «patrón multilínea a medias» (su `default` ante un opcode
 * que no reconoce simplemente registra un aviso y no hace nada, sin
 * devolverle nada a quien llamó) — así que un código con todas sus líneas en
 * formato válido pero incompleto de verdad (por ejemplo, sin su `D2000000`
 * final) no se puede detectar al añadirlo, sólo se nota en que no hace lo que
 * debería al jugar. Es una limitación real frente al proyecto hermano, no una
 * omisión.
 */
sealed interface CheatRejection {
    /** La línea [lineIndex] (0-based) no tiene la forma `XXXXXXXX XXXXXXXX` que exige un código Action Replay de DS. */
    data class InvalidLine(val lineIndex: Int) : CheatRejection
}
