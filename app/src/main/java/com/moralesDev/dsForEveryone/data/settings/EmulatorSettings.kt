package com.moralesDev.dsForEveryone.data.settings

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
 * Cómo se colocan las dos pantallas de la DS en el hueco disponible. La
 * geometría exacta de cada modo la resuelve `ScreenLayout` (fase 2); aquí sólo
 * se guarda cuál está elegido. Ver docs/pantallas.md.
 */
enum class ScreenLayoutMode {
    /** La disposición nativa: una encima de la otra. Por defecto en vertical. */
    STACKED,

    /** Una junto a la otra. Por defecto en horizontal. */
    SIDE_BY_SIDE,

    /** Sólo la pantalla superior, a toda el área disponible. */
    TOP_ONLY,

    /** Sólo la pantalla inferior (la táctil), a toda el área disponible. */
    BOTTOM_ONLY,

    /** Una grande y la otra reducida en una esquina, a [EmulatorSettings.secondaryScale]. */
    MAIN_AND_SECONDARY,
    ;

    companion object {
        fun fromName(name: String?): ScreenLayoutMode? = entries.firstOrNull { it.name == name }
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
    val portraitLayoutMode: ScreenLayoutMode = ScreenLayoutMode.STACKED,
    /** Disposición de pantallas cuando el teléfono está en horizontal. */
    val landscapeLayoutMode: ScreenLayoutMode = ScreenLayoutMode.SIDE_BY_SIDE,
    /** Separación entre pantallas, 0..1 relativo al hueco disponible. */
    val screenGap: Float = 0.02f,
    /** Escala de la pantalla secundaria en el modo [ScreenLayoutMode.MAIN_AND_SECONDARY]. */
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
)
