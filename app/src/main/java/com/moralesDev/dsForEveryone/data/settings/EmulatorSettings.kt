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
