package pe.leadai.rider.datos

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Lo que se escribe en el buscador viaja entero.
 *
 * El buscador de direcciones se quedaba buscando y no cargaba nada: la consulta
 * se pegaba cruda en la URL, así que un espacio hacía que la request ni saliera.
 * Con una palabra andaba; con dos —o sea, casi toda dirección real— fallaba.
 */
class ConsultaTest {

    @Test
    fun el_espacio_no_viaja_crudo() {
        // ESTE es el caso que rompía: "jose olaya" nunca llegaba al servidor.
        val url = "/carreras/direcciones?q=" + paraUrl("jose olaya")

        assertTrue(' ' !in url, "un espacio crudo hace que la request ni salga: $url")
        assertEquals("/carreras/direcciones?q=jose%20olaya", url)
    }

    @Test
    fun los_acentos_y_la_enie_tambien() {
        // En Tacna aparecen todo el tiempo: "Cañaveral", "Bolognesi Nº 200".
        assertEquals("Ca%C3%B1averal", paraUrl("Cañaveral"))
        assertEquals("Jos%C3%A9", paraUrl("José"))
    }

    @Test
    fun el_ampersand_no_parte_la_consulta() {
        // Sin escapar, todo lo que sigue al & se lee como OTRO parámetro y la
        // búsqueda se hace sobre un texto cortado.
        assertEquals("a%26b", paraUrl("a&b"))
    }

    @Test
    fun lo_que_ya_era_seguro_queda_igual() {
        // Una palabra sola siempre anduvo: no hay que romperla al arreglar.
        assertEquals("barlovento", paraUrl("barlovento"))
    }
}
