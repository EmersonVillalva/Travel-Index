package com.travelindex.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ViewerTest {

    @Test
    fun `abre dentro PDFs e imagenes y manda fuera el resto`() {
        assertTrue(ViewerSupport.opensInternally("application/pdf"))
        assertTrue(ViewerSupport.opensInternally("image/jpeg"))
        assertTrue(ViewerSupport.opensInternally("image/png"))
        assertFalse(ViewerSupport.opensInternally("application/msword"))
        assertFalse(ViewerSupport.opensInternally("text/plain"))
        assertFalse(ViewerSupport.opensInternally(""))
    }

    @Test
    fun `ancho de dibujo una vez y media la pantalla con topes`() {
        assertEquals(1620, ViewerSupport.pageRenderWidth(1080))   // 1080 · 1,5
        assertEquals(1080, ViewerSupport.pageRenderWidth(720))    // 720 · 1,5
        assertEquals(1620, ViewerSupport.pageRenderWidth(1440))   // tope superior
        assertEquals(480, ViewerSupport.pageRenderWidth(200))     // tope inferior
    }

    @Test
    fun `alto proporcional de la pagina`() {
        // A4: 595 x 842 puntos. A 1620 px de ancho salen 2292 px de alto.
        assertEquals(2292, ViewerSupport.pageHeightFor(595, 842, 1620))
        assertEquals(810, ViewerSupport.pageHeightFor(200, 100, 1620))
        assertEquals(1620, ViewerSupport.pageHeightFor(0, 842, 1620))   // tamaño raro: cuadrado, sin dividir por cero
        assertEquals(1, ViewerSupport.pageHeightFor(10_000, 1, 100))     // nunca menos de un píxel
    }

    @Test
    fun `la escala queda entre 1x y 5x`() {
        assertEquals(1f, ViewerSupport.clampScale(0.3f))
        assertEquals(2.5f, ViewerSupport.clampScale(2.5f))
        assertEquals(5f, ViewerSupport.clampScale(9f))
    }

    @Test
    fun `el desplazamiento no saca el contenido del marco`() {
        // Sin zoom no hay desplazamiento posible.
        assertEquals(0f, ViewerSupport.maxPan(1000f, 1f))
        assertEquals(0f, ViewerSupport.clampPan(300f, 1000f, 1f))
        // Al doble de tamaño sobran 1000 px, 500 por lado.
        assertEquals(500f, ViewerSupport.maxPan(1000f, 2f))
        assertEquals(500f, ViewerSupport.clampPan(800f, 1000f, 2f))
        assertEquals(-500f, ViewerSupport.clampPan(-800f, 1000f, 2f))
        assertEquals(120f, ViewerSupport.clampPan(120f, 1000f, 2f))
    }
}
