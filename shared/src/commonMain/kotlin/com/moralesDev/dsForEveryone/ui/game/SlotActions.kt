package com.moralesDev.dsForEveryone.ui.game

/** Qué se está eligiendo en la hoja de ranuras. */
enum class SlotAction { SAVE, LOAD }

/**
 * Acción sobre una ranura que destruye algo y necesita un sí explícito. Las dos
 * son irreversibles: no hay papelera de save states.
 */
sealed interface SlotConfirmation {
    val slot: Int

    data class Overwrite(override val slot: Int) : SlotConfirmation

    data class Delete(override val slot: Int) : SlotConfirmation
}
