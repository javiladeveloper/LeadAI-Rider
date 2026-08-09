package pe.leadai.rider.ui.comunes

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Un punto del mapa. Sin tipos de Google acá: `commonMain` no los conoce.
 */
data class PuntoMapa(val lat: Double, val lng: Double)

/**
 * El recorrido entre dos puntos.
 *
 * En Android lo dibuja Google Maps nativo. Reemplazó al WebView, que reportaba
 * un viewport distinto de su tamaño real y obligaba a mandarle el alto por la
 * URL —además de esconder el JavaScript dentro de cadenas donde los escapes se
 * rompían sin que nadie lo viera—.
 *
 * @param recorrido la ruta por calle; vacía dibuja la recta entre los pines.
 */
@Composable
expect fun MapaRuta(
    origen: PuntoMapa,
    destino: PuntoMapa,
    modifier: Modifier = Modifier,
    recorrido: List<PuntoMapa> = emptyList(),
)

/**
 * El radar mientras se busca motorizado: el pulso y las motos alrededor.
 */
@Composable
expect fun MapaRadar(
    centro: PuntoMapa,
    motos: List<PuntoMapa>,
    modifier: Modifier = Modifier,
)
