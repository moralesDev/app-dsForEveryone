package com.moralesDev.dsForEveryone.data.cheats

import com.moralesDev.dsForEveryone.emulator.core.CheatEntry
import kotlinx.serialization.Serializable

/**
 * Forma en disco de `cheats.json`. [CheatEntry] es del dominio del core (vive
 * en `emulator/core`, cruza a JNI) y no lleva anotaciones de serialización a
 * propósito: este DTO es la traducción de ida y vuelta, y el único lugar
 * donde el formato de archivo puede cambiar sin tocar el contrato del core.
 */
@Serializable
data class CheatsFileDto(val entries: List<CheatEntryDto> = emptyList())

@Serializable
data class CheatEntryDto(
    val id: String,
    val label: String,
    val lines: List<String>,
    val enabled: Boolean,
)

fun CheatEntry.toDto(): CheatEntryDto =
    CheatEntryDto(id = id, label = label, lines = lines, enabled = enabled)

fun CheatEntryDto.toDomain(): CheatEntry =
    CheatEntry(id = id, label = label, lines = lines, enabled = enabled)
