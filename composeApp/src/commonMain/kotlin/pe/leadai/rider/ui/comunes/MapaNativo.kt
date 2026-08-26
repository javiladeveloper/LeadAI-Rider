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
    /** Dónde está la moto ahora. Null mientras no haya GPS. */
    moto: PuntoMapa? = null,
    /**
     * Quién mira.
     *
     * El RIDER arranca pegado a su moto: lo que necesita es su cuadra y hacia
     * dónde salir, no el viaje entero. El CLIENTE ve el recorrido completo,
     * porque lo que le importa es cuánto falta.
     */
    modoRider: Boolean = false,
    /** Lo que tapa la tarjeta del viaje, para que la moto no quede debajo. */
    tapadoAbajoPx: Int = 0,
    /**
     * Qué servicio es: "pasajero", "encomienda", "delivery" o "pedido".
     *
     * Cambia el ícono del punto de recojo: en un viaje de pasajero ahí hay
     * una PERSONA esperando en la vereda; en delivery o encomienda, un local
     * o una dirección. Un mismo pin para los dos casos obliga al rider a
     * leer la tarjeta para saber qué va a buscar.
     */
    tipoServicio: String = "pedido",
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
