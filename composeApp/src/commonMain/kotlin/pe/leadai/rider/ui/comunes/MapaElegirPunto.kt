package pe.leadai.rider.ui.comunes

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Mini mapa para confirmar DÓNDE queda una dirección.
 *
 * El pin va fijo en el centro y lo que se mueve es el mapa — como Uber o
 * Google Maps. Con el dedo sobre la pantalla es mucho más preciso que
 * arrastrar un marcador chiquito, que además queda tapado por el propio dedo.
 *
 * [onPunto] se llama al SOLTAR, no mientras se arrastra: cada aviso dispara un
 * reverse geocode y Nominatim admite una consulta por segundo.
 *
 * `expect/actual` como [MapaEmbebido]: Android lo pinta con un WebView e
 * inyecta el puente JS; iOS queda como stub hasta que se implemente WKWebView.
 */
@Composable
expect fun MapaElegirPunto(
    url: String,
    onPunto: (lat: Double, lng: Double) -> Unit,
    modifier: Modifier,
)
