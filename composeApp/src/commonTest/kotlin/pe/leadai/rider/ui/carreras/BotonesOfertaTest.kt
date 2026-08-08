package pe.leadai.rider.ui.carreras

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import pe.leadai.rider.ui.carreras.componentes.centavosDeTexto
import pe.leadai.rider.ui.carreras.componentes.soloMonto

class BotonesOfertaTest {

    @Test
    fun `deja escribir un monto normal`() {
        assertEquals("12.50", soloMonto("12.50"))
        assertEquals("8", soloMonto("8"))
    }

    @Test
    fun `filtra las letras al escribir`() {
        // El rider está en la calle, a veces con guantes: un campo que acepta
        // cualquier cosa y después rechaza obliga a borrar y reescribir.
        assertEquals("12", soloMonto("12abc"))
        assertEquals("", soloMonto("hola"))
    }

    @Test
    fun `acepta la coma como decimal`() {
        // En el teclado peruano la coma cae más a mano que el punto.
        assertEquals("7.50", soloMonto("7,50"))
    }

    @Test
    fun `un solo separador decimal`() {
        assertEquals("7.55", soloMonto("7.5.5"))
    }

    @Test
    fun `no deja empezar con el punto`() {
        // ".50" no es un monto: sin entero delante el valor es ambiguo.
        assertEquals("50", soloMonto(".50"))
    }

    @Test
    fun `corta en dos decimales`() {
        // No existen fracciones de céntimo.
        assertEquals("7.55", soloMonto("7.5555"))
    }

    @Test
    fun `pone un techo a los enteros`() {
        // Nadie cobra S/10.000 por una carrera en moto; sin tope un cero de
        // más pasa desapercibido.
        assertEquals("9999", soloMonto("999999"))
    }

    @Test
    fun `convierte a centavos`() {
        assertEquals(750L, centavosDeTexto("7.50"))
        assertEquals(1200L, centavosDeTexto("12"))
    }

    @Test
    fun `redondea a 10 centimos, la moneda mas chica que circula`() {
        // Las de 1 y 5 céntimos salieron de circulación: S/7.57 es incobrable.
        assertEquals(760L, centavosDeTexto("7.57"))
        assertEquals(750L, centavosDeTexto("7.54"))
    }

    @Test
    fun `sin numero valido no hay oferta`() {
        // El botón de ofertar queda apagado: mandar null sería ofertar S/0.
        assertNull(centavosDeTexto(""))
        assertNull(centavosDeTexto("0"))
        assertNull(centavosDeTexto("abc"))
    }
}
