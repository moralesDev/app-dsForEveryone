package com.moralesDev.dsForEveryone.data.settings

import com.moralesDev.dsForEveryone.emulator.gamepad.GamepadMapping
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
 * A qué velocidad corre la emulación. El multiplicador acorta el período de
 * fotograma en el hilo de emulación (ver
 * [com.moralesDev.dsForEveryone.emulator.DsVideo.frameDurationNanosAt]); nada
 * más cambia, y menos que nada el core, que sigue emulando un fotograma por
 * vuelta como siempre.
 *
 * Tres valores y no una escala continua: 1,5x es lo más rápido que deja seguir
 * un combate, y 2x lo más rápido que sostiene un gama alta con este core.
 * Por encima de eso el teléfono ya no llega y el resultado no es «más rápido»,
 * es irregular.
 */
enum class EmulationSpeed(val multiplier: Float) {
    NORMAL(1f),
    FAST_1_5X(1.5f),
    FAST_2X(2f),
    ;

    /** Si esta velocidad es avance rápido; es lo que decide si se silencia el sonido. */
    val isFast: Boolean get() = this != NORMAL

    companion object {
        fun fromName(name: String?): EmulationSpeed? = entries.firstOrNull { it.name == name }
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
 * Con qué se manejan las cuatro direcciones en pantalla. Los dos producen lo
 * mismo —la cruceta de la DS, ocho direcciones—; cambia sólo cómo se pulsa.
 * Ver docs/entrada.md.
 */
enum class DirectionalControl {
    /** Un stick: se apunta con el pulgar, sin acertar en un brazo. Por defecto. */
    JOYSTICK,

    /** La cruz de siempre, con flechas en relieve. */
    DPAD,
    ;

    companion object {
        fun fromName(name: String?): DirectionalControl? = entries.firstOrNull { it.name == name }
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

    /**
     * Velocidad de la emulación. Se guarda como cualquier otro ajuste, así
     * que sobrevive a cerrar el juego; se ve y se cambia en el menú de pausa,
     * que es el único sitio desde el que se toca.
     */
    val speed: EmulationSpeed = EmulationSpeed.NORMAL,

    val directionalControl: DirectionalControl = DirectionalControl.JOYSTICK,

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

    /**
     * Mapeo de botones por mando físico, indexado por `InputDevice.descriptor`
     * (ver `GamepadDevice`). Un mando sin entrada aquí todavía usa
     * [GamepadMapping.DEFAULT] — no hace falta escribir nada para poder jugar
     * con un mando corriente, sólo para personalizarlo.
     */
    val gamepadMappings: Map<String, GamepadMapping> = emptyMap(),
)
