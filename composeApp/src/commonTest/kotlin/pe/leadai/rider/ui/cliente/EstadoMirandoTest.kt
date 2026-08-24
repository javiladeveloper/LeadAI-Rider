package pe.leadai.rider.ui.cliente

import pe.leadai.rider.ui.cliente.componentes.detalleDeLaBusqueda
import pe.leadai.rider.ui.cliente.componentes.tituloDeLaBusqueda
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * "ESTÁN VIENDO TU SOLICITUD" — las dos aspitas del cliente.
 *
 * El cliente esperaba sin saber si alguien había siquiera abierto su pedido.
 * Entre "nadie lo vio" y "lo están pensando" hay una diferencia enorme para
 * quien decide si sube el monto o cancela.
 */
class EstadoMirandoTest {

    @Test
    fun con_alguien_mirando_se_dice() {
        val titulo = tituloDeLaBusqueda(ofertas = 0, motosCerca = 2, mirando = 1)

        assertTrue(titulo.contains("viendo"), "tiene que avisarlo: $titulo")
    }

    @Test
    fun una_OFERTA_gana_sobre_estar_mirando() {
        // Ya ofertaron: mostrar "están viendo" sería un paso atrás. Lo que el
        // cliente tiene que hacer ahora es ELEGIR.
        val titulo = tituloDeLaBusqueda(ofertas = 1, motosCerca = 3, mirando = 2)
        val detalle = detalleDeLaBusqueda(ofertas = 1, motosCerca = 3, mirando = 2)

        assertTrue(titulo.contains("ofreció"), titulo)
        assertEquals("Elegí con quién querés ir", detalle)
    }

    @Test
    fun estar_mirando_gana_sobre_solo_haber_motos_cerca() {
        // "Hay 3 motos cerca" es geografía; "1 la está mirando" es intención.
        // Lo segundo dice muchísimo más sobre si va a salir esta carrera.
        val detalle = detalleDeLaBusqueda(ofertas = 0, motosCerca = 5, mirando = 1)

        assertTrue(detalle.contains("está mirando"), detalle)
    }

    @Test
    fun el_plural_esta_bien_escrito() {
        // "2 motorizado la está mirando" se lee como un error de la app, y
        // una app que escribe mal da la sensación de estar improvisada.
        assertEquals(
            "1 motorizado la está mirando",
            detalleDeLaBusqueda(ofertas = 0, motosCerca = 0, mirando = 1),
        )
        assertEquals(
            "3 motorizados la están mirando",
            detalleDeLaBusqueda(ofertas = 0, motosCerca = 0, mirando = 3),
        )
    }

    @Test
    fun sin_nadie_mirando_no_cambia_nada() {
        // El comportamiento viejo tiene que seguir igual: `mirando = 0` es el
        // caso normal durante casi toda la búsqueda.
        assertEquals(
            "Ofreciendo tu tarifa",
            tituloDeLaBusqueda(ofertas = 0, motosCerca = 2, mirando = 0),
        )
        assertEquals(
            "2 motorizados están cerca",
            detalleDeLaBusqueda(ofertas = 0, motosCerca = 2, mirando = 0),
        )
    }

    @Test
    fun sin_motos_ni_miradas_sigue_diciendo_que_busca() {
        // El peor caso: nadie cerca y nadie mirando. Igual hay que decir algo
        // —el silencio se lee como "la app se colgó"—.
        assertEquals(
            "Ampliando el área de búsqueda",
            detalleDeLaBusqueda(ofertas = 0, motosCerca = 0, mirando = 0),
        )
    }

    @Test
    fun un_conteo_NEGATIVO_no_rompe_el_texto() {
        // Nunca debería pasar, pero un dato raro del servidor no puede dejar
        // la pantalla con "-1 motorizados la están mirando".
        val detalle = detalleDeLaBusqueda(ofertas = 0, motosCerca = 0, mirando = -1)

        assertTrue(!detalle.contains("-1"), "un negativo no se muestra: $detalle")
        assertEquals("Ampliando el área de búsqueda", detalle)
    }
}
