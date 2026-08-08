package pe.leadai.rider.ui.cliente.componentes

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import pe.leadai.rider.ui.comunes.MapaEmbebido

/** La página del recorrido vive en el backend, como la del tracking. */
private const val URL_MAPA_RUTA = "https://api.leadai-pe.com/mapa/ruta"

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

    Box(
        modifier = modifier
            .fillMaxWidth()
            // Alto suficiente para leer el recorrido sin comerse el formulario:
            // más grande empuja el botón de pedir fuera de la pantalla.
            // Alto FIJO, y además se le manda a la página por la URL: el
            // WebView reporta un viewport que no coincide con su tamaño real
            // (medido: body=0, ventana=160 dentro de un contenedor de 549),
            // así que ni 100%, ni 100dvh, ni el inset absoluto sirven. Con el
            // alto explícito la página no tiene que adivinar.
            .height(ALTO_MAPA.dp)
            .clip(RoundedCornerShape(16.dp)),
    ) {
        MapaEmbebido(
            url = "$URL_MAPA_RUTA?oLat=$origenLat&oLng=$origenLng" +
                "&dLat=$destinoLat&dLng=$destinoLng&alto=$ALTO_MAPA",
            // fillMaxSIZE, no fillMaxWidth: adentro el WebView usa
            // `fillMaxSize()`, y dentro de un modifier que solo fija el ancho
            // eso resuelve a CERO de alto. El WebView existía y cargaba la
            // página —el cache lo confirma— pero medía 0 px, así que se veía
            // un hueco vacío. Ese era el "mapa en blanco".
            modifier = Modifier.fillMaxSize(),
        )
    }
}
