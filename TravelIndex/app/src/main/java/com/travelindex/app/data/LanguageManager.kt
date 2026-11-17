package com.travelindex.app.data

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.LocaleList
import com.travelindex.app.domain.AppLanguage
import java.util.Locale

/**
 * Idioma elegido en Perfil. Se guarda en SharedPreferences (y no en DataStore) porque hay que leerlo de forma
 * síncrona al crear la actividad, antes de dibujar nada. Por defecto, español.
 *
 * [localize] devuelve un contexto con ese idioma y deja el mismo idioma como predeterminado del proceso, así
 * fechas, números y textos de pantallas, avisos, widget y accesos directos salen todos en el idioma elegido.
 */
object LanguageManager {
    private const val PREFS = "travelindex_language"
    private const val KEY = "language"

    private fun prefs(context: Context): SharedPreferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun get(context: Context): AppLanguage = AppLanguage.fromKey(prefs(context).getString(KEY, null))

    fun set(context: Context, language: AppLanguage) {
        prefs(context).edit().putString(KEY, language.key).apply()
    }

    /** Avisa de cualquier cambio de idioma. El oyente debe conservarse en un campo (SharedPreferences lo guarda con referencia débil). */
    fun register(context: Context, listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs(context).registerOnSharedPreferenceChangeListener(listener)
    }

    fun localize(base: Context): Context {
        val locale = Locale.forLanguageTag(get(base).tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocales(LocaleList(locale))
        return base.createConfigurationContext(config)
    }
}
