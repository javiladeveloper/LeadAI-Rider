package pe.leadai.rider.ui.comunes

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState

/**
 * El mapa, dibujado NATIVO.
 *
 * Reemplaza al WebView, que nos costó varios días: reportaba un viewport que no
 * coincidía con su tamaño real —así que el alto había que mandárselo por la
 * URL—, el JavaScript vivía dentro de cadenas donde los escapes se rompían sin
 * que nadie lo viera, y un mapa en blanco podía ser la página, el tamaño o los
 * tiles, sin forma de distinguirlos desde afuera.
 *
 * Acá el mapa es un componente de Compose: se mide solo, el compilador revisa
 * lo que se dibuja, y el zoom y el encuadre son llamadas tipadas.
 *
 * Requiere `MAPS_API_KEY` en `local.properties`. Sin clave el mapa se ve GRIS
 * y el log dice "Authorization failure" — vale revisar eso antes que nada.
 */

private fun PuntoMapa.aLatLng() = LatLng(lat, lng)

/** Los colores de la marca, para no repetirlos en cada mapa. */
private val VERDE_ORIGEN = Color(0xFF2E7D32)
private val ROJO_DESTINO = Color(0xFFE5484D)
private val AMBAR = Color(0xFFFDBF35)
private val TRAZO_RUTA = Color(0xFF2E3440)

/**
 * Mapa base con la configuración que comparten todos.
 *
 * `interactivo = false` para los mapas embebidos en formularios: un mapa que
 * captura el gesto de scroll deja al usuario atrapado adentro.
 */
@Composable
private fun MapaBase(
    estadoCamara: CameraPositionState,
    modifier: Modifier = Modifier,
    interactivo: Boolean = false,
    contenido: @Composable () -> Unit,
) {
    GoogleMap(
        modifier = modifier.fillMaxSize(),
        cameraPositionState = estadoCamara,
        properties = MapProperties(mapType = MapType.NORMAL),
        uiSettings = MapUiSettings(
            zoomControlsEnabled = false,
            mapToolbarEnabled = false,
            compassEnabled = false,
            scrollGesturesEnabled = interactivo,
            zoomGesturesEnabled = interactivo,
            rotationGesturesEnabled = false,
            tiltGesturesEnabled = false,
        ),
        content = { contenido() },
    )
}

/**
 * El recorrido entre dos puntos, con los pines de origen y destino.
 *
 * La línea la calcula quien llame (el backend resuelve la ruta por calle); si
 * no hay, se dibuja la recta entre los dos pines, que ya dice a dónde va.
 *
 * El encuadre incluye los dos extremos: ver solo uno no cuenta nada del viaje.
 */
@Composable
actual fun MapaRuta(
    origen: PuntoMapa,
    destino: PuntoMapa,
    modifier: Modifier,
    recorrido: List<PuntoMapa>,
) {
    val estadoCamara = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(origen.aLatLng(), 14f)
    }

    // El encuadre se ajusta cuando cambian los puntos o llega la ruta real.
    // `padding` en píxeles: deja aire para que los pines no queden pegados al
    // borde, donde se leen mal.
    LaunchedEffect(origen, destino, recorrido.size) {
        val limites = LatLngBounds.builder()
            .include(origen.aLatLng())
            .include(destino.aLatLng())
            .apply { recorrido.forEach { include(it.aLatLng()) } }
            .build()
        runCatching {
            estadoCamara.animate(CameraUpdateFactory.newLatLngBounds(limites, 80))
        }
    }

    Box(modifier = modifier) {
        MapaBase(estadoCamara = estadoCamara, modifier = Modifier.fillMaxSize()) {
            val linea = remember(recorrido, origen, destino) {
                if (recorrido.isNotEmpty()) recorrido.map { it.aLatLng() }
                else listOf(origen.aLatLng(), destino.aLatLng())
            }
            Polyline(points = linea, color = TRAZO_RUTA, width = 12f)

            // Círculos y no marcadores con ícono: se leen igual y no dependen
            // de ningún recurso gráfico que pueda faltar.
            Circle(
                center = origen.aLatLng(),
                radius = 40.0,
                fillColor = VERDE_ORIGEN,
                strokeColor = Color.White,
                strokeWidth = 6f,
            )
            Circle(
                center = destino.aLatLng(),
                radius = 40.0,
                fillColor = ROJO_DESTINO,
                strokeColor = Color.White,
                strokeWidth = 6f,
            )
        }
    }
}

/**
 * El radar mientras se busca motorizado.
 *
 * Dos círculos rellenos alrededor del cliente y las motos disponibles. Dice
 * tres cosas que un texto no dice: que hay gente cerca, que la búsqueda sigue,
 * y cuántos hay.
 */
@Composable
actual fun MapaRadar(
    centro: PuntoMapa,
    motos: List<PuntoMapa>,
    modifier: Modifier,
) {
    val estadoCamara = rememberCameraPositionState {
        // Zoom 14 muestra ~2 km a la redonda: el alcance del radar entero.
        position = CameraPosition.fromLatLngZoom(centro.aLatLng(), 14f)
    }

    Box(modifier = modifier) {
        MapaBase(estadoCamara = estadoCamara, modifier = Modifier.fillMaxSize()) {
            // El grande primero, para que el chico quede encima.
            Circle(
                center = centro.aLatLng(),
                radius = RADIO_ALCANCE_METROS,
                fillColor = AMBAR.copy(alpha = 0.10f),
                strokeWidth = 0f,
            )
            Circle(
                center = centro.aLatLng(),
                radius = RADIO_CERCA_METROS,
                fillColor = AMBAR.copy(alpha = 0.18f),
                strokeWidth = 0f,
            )
            // Dónde está el cliente: el centro del radar.
            Circle(
                center = centro.aLatLng(),
                radius = 45.0,
                fillColor = VERDE_ORIGEN,
                strokeColor = Color.White,
                strokeWidth = 6f,
            )
            // Cada moto disponible.
            motos.forEach { moto ->
                Circle(
                    center = moto.aLatLng(),
                    radius = 55.0,
                    fillColor = TRAZO_RUTA,
                    strokeColor = Color.White,
                    strokeWidth = 5f,
                )
            }
        }
    }
}

/** La zona inmediata y el alcance de la búsqueda, en metros. */
private const val RADIO_CERCA_METROS = 500.0
private const val RADIO_ALCANCE_METROS = 2000.0
