package pe.leadai.rider.ui.comunes

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Un remedido chico NO cambia el alto que viaja en la URL.
 *
 * El alto es parte de la URL del mapa, y si la URL cambia el WebView RECARGA
 * la página. En el radar eso significa volver a empezar en 500 m: el cliente
 * lo veía "estático" porque cada vez que llegaba una oferta —se apilan sobre
 * el radar y le quitan alto— el contenedor se remedía y el radar se reiniciaba.
 *
 * La página ya reajusta su alto sola por JS, así que unos dp no justifican
 * perder el estado.
 */
class AltoDelMapaTest {

    /** La misma regla que aplica `MapaQueSeMide.onSizeChanged`. */
    private fun siguiente(actual: Int, medido: Int): Int =
        if (actual == 0 || abs(medido - actual) > 48) medido else actual

    @Test
    fun el_primer_alto_siempre_entra() {
        // Sin alto no se puede cargar la página: el primero manda.
        assertEquals(549, siguiente(actual = 0, medido = 549))
    }

    @Test
    fun un_remedido_chico_no_recarga_el_mapa() {
        // Lo que pasa cuando llega una oferta y el radar pierde unos dp.
        val actual = 549
        assertEquals(actual, siguiente(actual, 540), "40 dp no deben recargar")
        assertEquals(actual, siguiente(actual, 560), "11 dp tampoco")
        assertEquals(actual, siguiente(actual, 501), "48 dp es el límite")
    }

    @Test
    fun un_cambio_grande_si_recarga() {
        // Rotar el teléfono o abrir el teclado sí cambia el espacio de verdad:
        // ahí la página TIENE que redibujarse contra el tamaño nuevo.
        val actual = 549
        assertTrue(siguiente(actual, 300) == 300, "un cambio grande sí entra")
        assertTrue(siguiente(actual, 800) == 800, "crecer mucho también")
    }
}
