package com.travelindex.app.domain

/**
 * Idioma de la app que elige el usuario en Perfil. Por defecto es el español, también en un móvil con otro idioma.
 * [SYSTEM] sigue el idioma del móvil (si no es español ni inglés, la app cae al español).
 */
enum class AppLanguage(val key: String, val tag: String?) {
    SPANISH("es", "es"),
    ENGLISH("en", "en"),
    SYSTEM("system", null);

    companion object {
        val DEFAULT = SPANISH

        /** Cualquier valor guardado desconocido o ausente vuelve al idioma por defecto. */
        fun fromKey(key: String?): AppLanguage = entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}
