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
 * Preferencias del emulador. Crece en 1.3 con los ajustes propios de la DS
 * (disposición de pantallas, filtro de imagen, modo de toque); de momento sólo
 * lleva lo que necesita [com.moralesDev.dsForEveryone.ui.MainViewModel] antes
 * de poder pintar nada.
 */
data class EmulatorSettings(
    val language: AppLanguage = AppLanguage.SPANISH,
    /**
     * La pantalla de bienvenida aparece en cada arranque hasta que el usuario
     * marca «no volver a mostrar».
     */
    val skipWelcome: Boolean = false,
)
