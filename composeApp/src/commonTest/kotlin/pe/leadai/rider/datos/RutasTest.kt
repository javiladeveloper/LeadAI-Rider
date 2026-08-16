package pe.leadai.rider.datos

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Las URLs de mapa SIEMPRE llevan el alto.
 *
 * El WebView reporta un viewport que no coincide con su tamaño real, así que
 * una página de mapa sin `alto` se dibuja contra un número equivocado: queda
 * cuadrada y chica, con el resto del espacio gris. Ya pasó en los cuatro mapas
 * de la app, uno por uno.
 *
 * Como el alto es un parámetro obligatorio de estas funciones, olvidarlo ahora
 * es un error de compilación. Estos tests protegen que siga siendo así.
 */
class RutasTest {

    @Test
    fun elRadarMandaElAlto() {
        val url = Rutas.Mapas.radar(lat = -17.99, lng = -70.23, altoDp = 420)
        assertTrue(url.contains("alto=420"), "el radar debe mandar su alto: $url")
    }

    @Test
    fun laRutaMandaElAlto() {
        val url = Rutas.Mapas.ruta(-17.99, -70.23, -18.01, -70.24, altoDp = 180)
        assertTrue(url.contains("alto=180"), "la ruta debe mandar su alto: $url")
    }

    @Test
    fun elPuntoMandaElAlto() {
        val url = Rutas.Mapas.punto(-17.99, -70.23, altoDp = 200)
        assertTrue(url.contains("alto=200"), "el punto debe mandar su alto: $url")
    }

    @Test
    fun elTrackingMandaElAlto() {
        // Este es el que se olvidó y dejó el mapa del rider cuadrado.
        val url = Rutas.Mapas.tracking("ped-1", altoDp = 549)
        assertTrue(url.contains("alto=549"), "el tracking debe mandar su alto: $url")
        assertTrue(url.contains("embebido=1"), "va embebido en la app: $url")
    }

    @Test
    fun todasApuntanAlMismoServidor() {
        // La dirección estaba escrita a mano en seis constantes distintas: con
        // una que quedara atrás, esa pantalla le hablaba al servidor viejo.
        val urls = listOf(
            Rutas.Mapas.radar(-17.99, -70.23, 420),
            Rutas.Mapas.ruta(-17.99, -70.23, -18.01, -70.24, 180),
            Rutas.Mapas.punto(-17.99, -70.23, 200),
            Rutas.Mapas.tracking("ped-1", 549),
            Rutas.pagoRider("tok", "paq"),
        )
        assertTrue(
            urls.all { it.startsWith(Rutas.BASE) },
            "todas deben salir de Rutas.BASE: $urls",
        )
    }

    @Test
    fun elTemaOscuroViajaSoloCuandoCorresponde() {
        assertTrue(Rutas.Mapas.radar(-17.99, -70.23, 420, oscuro = true).contains("oscuro=1"))
        assertTrue(!Rutas.Mapas.radar(-17.99, -70.23, 420).contains("oscuro"))
    }
    @Test
    fun solo_el_rider_entra_en_modo_navegacion() {
        // El modo navegación —cámara pegada a la moto, tras recoger— es para
        // el que MANEJA. Al cliente le sirve ver el viaje completo: la calle
        // por donde va la moto no le dice nada, quiere saber cuánto falta.
        //
        // Antes el backend lo deducía de `embebido`, pero los DOS entran
        // embebidos: el cliente caía en modo navegación sin ninguna razón.
        val delRider = Rutas.Mapas.tracking("ped-1", altoDp = 500, esRider = true)
        val delCliente = Rutas.Mapas.tracking("ped-1", altoDp = 500)

        assertTrue(delRider.contains("modo=rider"), "el rider sí: $delRider")
        assertTrue(!delCliente.contains("modo=rider"), "el cliente no: $delCliente")
    }

}
