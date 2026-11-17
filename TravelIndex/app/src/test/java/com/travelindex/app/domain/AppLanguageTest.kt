package com.travelindex.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class AppLanguageTest {
    @Test
    fun `el espanol es el idioma por defecto`() {
        assertEquals(AppLanguage.SPANISH, AppLanguage.DEFAULT)
        assertEquals(AppLanguage.SPANISH, AppLanguage.fromKey(null))
    }

    @Test
    fun `un valor guardado desconocido vuelve al espanol`() {
        assertEquals(AppLanguage.SPANISH, AppLanguage.fromKey(""))
        assertEquals(AppLanguage.SPANISH, AppLanguage.fromKey("fr"))
    }

    @Test
    fun `cada idioma se guarda y se recupera por su clave`() {
        AppLanguage.entries.forEach { assertEquals(it, AppLanguage.fromKey(it.key)) }
    }

    @Test
    fun `solo hay espanol e ingles`() {
        assertEquals(listOf("es", "en"), AppLanguage.entries.map { it.tag })
    }

    @Test
    fun `el valor system de la 0_7_0 vuelve al espanol`() {
        assertEquals(AppLanguage.SPANISH, AppLanguage.fromKey("system"))
    }
}
