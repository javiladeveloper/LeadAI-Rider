package pe.leadai.rider.ui.cliente.componentes

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import pe.leadai.rider.ui.comunes.MapaRuta
import pe.leadai.rider.ui.comunes.PuntoMapa
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import pe.leadai.rider.ui.comunes.MapaEmbebido

/** Alto del mapa, en dp. Se le pasa a la página para que no tenga que medir. */
private const val ALTO_MAPA = 180

/**
 * El recorrido dibujado, apenas hay origen y destino.
 *
 * Va ANTES del precio a propósito. El cliente escribe "Barlovento", la app
 * elige un punto, y sin mapa no tiene forma de saber si es el correcto —hay
 * locales con el mismo nombre— ni cuánto camino es. Ver la línea contesta las
 * dos cosas de un vistazo, y es lo que hace que el monto propuesto después se
 * entienda en vez de aparecer de la nada.
 *
 * No se muestra hasta tener los DOS pines: un mapa con un solo punto no dice
 * nada del viaje.
 */
@Composable
fun MapaDeLaRuta(
    origenLat: Double?,
    origenLng: Double?,
    destinoLat: Double?,
    destinoLng: Double?,
    modifier: Modifier = Modifier,
) {
    if (origenLat == null || origenLng == null || destinoLat == null || destinoLng == null) return

    // Mapa NATIVO: se mide solo y no depende de un WebView que reporta un
    // tamaño distinto del suyo. 180dp deja leer el recorrido sin comerse el
    // formulario —más grande empuja el botón de pedir fuera de la pantalla—.
    MapaRuta(
        origen = PuntoMapa(origenLat, origenLng),
        destino = PuntoMapa(destinoLat, destinoLng),
        modifier = modifier.fillMaxWidth().height(ALTO_MAPA.dp),
    )
}
