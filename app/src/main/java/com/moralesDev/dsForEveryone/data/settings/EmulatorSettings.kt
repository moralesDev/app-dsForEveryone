package com.moralesDev.dsForEveryone.data.settings

import com.moralesDev.dsForEveryone.emulator.layout.ControlGroup
import com.moralesDev.dsForEveryone.emulator.layout.ControlGroupFraction
import com.moralesDev.dsForEveryone.emulator.layout.LayoutMode

/**
 * Idioma elegido dentro de la aplicación, independiente del idioma del
 * sistema. Ver [com.moralesDev.dsForEveryone.ui.locale.ProvideAppLanguage].
 */
enum class AppLanguage(val tag: String) {
    SPANISH("es"),
    ENGLISH("en"),
    ;

    companion object {
        fun fromTag(tag: String?): AppLanguage? = entries.firstOrNull { it.tag == tag }
    }
}

/**
 * El filtro es un parámetro de la GPU (fase 2.5), no una geometría distinta
 * como en `app-emulador`: cambiarlo no recrea nada.
 */
enum class ImageFilter {
    /** `GL_NEAREST`. Píxeles cuadrados, como se ve el hardware real. */
    SHARP,

    /** `GL_LINEAR`. Interpola al escalar. */
    SMOOTH,
    ;

    companion object {
        fun fromName(name: String?): ImageFilter? = entries.firstOrNull { it.name == name }
    }
}

/**
 * Cómo se traduce un dedo en la pantalla táctil a una posición del lápiz. Ver
 * docs/entrada.md, sección «El problema real: un dedo no es un lápiz».
 */
enum class TouchMode {
    /** El dedo es el lápiz. Por defecto. */
    DIRECT,

    /** El punto de contacto se sitúa por encima del dedo, ver [EmulatorSettings.touchOffsetDp]. */
    OFFSET,

    /** Un puntero que se mueve con la cruceta y se «pulsa» con un botón. */
    VIRTUAL_CURSOR,
    ;

    companion object {
        fun fromName(name: String?): TouchMode? = entries.firstOrNull { it.name == name }
    }
}

/**
 * Preferencias del emulador. Valores por defecto pensados para jugar de pie,
 * sin haber entrado todavía al editor de disposición ni a los ajustes de
 * imagen: son los que usa alguien que abre la aplicación por primera vez.
 */
data class EmulatorSettings(
    val language: AppLanguage = AppLanguage.SPANISH,
    /**
     * La pantalla de bienvenida aparece en cada arranque hasta que el usuario
     * marca «no volver a mostrar».
     */
    val skipWelcome: Boolean = false,

    /** Disposición de pantallas cuando el teléfono está en vertical. */
    val portraitLayoutMode: LayoutMode = LayoutMode.STACKED,
    /** Disposición de pantallas cuando el teléfono está en horizontal. */
    val landscapeLayoutMode: LayoutMode = LayoutMode.SIDE_BY_SIDE,
    /** Separación entre pantallas, 0..1 relativo al hueco disponible. */
    val screenGap: Float = 0.02f,
    /** Escala de la pantalla secundaria en el modo [LayoutMode.MAIN_AND_SECONDARY]. */
    val secondaryScale: Float = 0.35f,
    /**
     * La táctil sigue siendo la pantalla lógica inferior aunque esto esté
     * activo: sólo cambia dónde se pinta cada una.
     */
    val screensSwapped: Boolean = false,
    val imageFilter: ImageFilter = ImageFilter.SHARP,

    val touchMode: TouchMode = TouchMode.DIRECT,
    /** Desplazamiento en dp del punto de contacto sobre el dedo, en [TouchMode.OFFSET]. */
    val touchOffsetDp: Float = 24f,

    /**
     * Perfil de mandos hecho a mano en el editor de disposición (fase 2.7),
     * uno por orientación. `null` significa «sin perfil propio»: la pantalla
     * de juego y el editor caen entonces al mismo perfil por defecto que
     * calcula [com.moralesDev.dsForEveryone.emulator.layout.ControlLayout].
     */
    val portraitControlLayout: Map<ControlGroup, ControlGroupFraction>? = null,
    val landscapeControlLayout: Map<ControlGroup, ControlGroupFraction>? = null,
)
