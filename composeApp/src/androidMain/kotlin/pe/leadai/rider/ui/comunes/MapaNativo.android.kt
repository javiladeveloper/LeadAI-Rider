package pe.leadai.rider.ui.comunes

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.isSystemInDarkTheme
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.core.graphics.createBitmap
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberMarkerState
import kotlinx.coroutines.delay
import kotlin.time.TimeSource
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

/** El borde del radar: más oscuro, para que se lea sobre las calles claras. */
private val AMBAR_BORDE = Color(0xFFF08C00)
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
 * El alcance CRECE con el tiempo, siguiendo los mismos anillos que usa el
 * backend para avisar, y la cámara se aleja con él: empieza cerrado en la
 * manzana y termina mostrando la ciudad.
 *
 * Esto en el WebView nunca quedó fluido: cada cambio de zoom hacía que Leaflet
 * pidiera tiles nuevos y tirara los viejos, y ese ciclo se veía como un
 * parpadeo. Acá la cámara la anima Google Maps sobre lo que ya tiene dibujado
 * — no hay nada que recargar, así que no puede titilar.
 */
@Composable
actual fun MapaRadar(
    centro: PuntoMapa,
    motos: List<PuntoMapa>,
    modifier: Modifier,
) {
    // El radio crece a 60 fps, interpolado DENTRO de cada anillo: así se abre
    // parejo en vez de saltar de 500 m a 1 km de golpe.
    val arranque = remember { TimeSource.Monotonic.markNow() }
    var radio by remember { mutableStateOf(RADIO_PRIMER_ANILLO) }

    LaunchedEffect(Unit) {
        while (true) {
            val seg = arranque.elapsedNow().inWholeMilliseconds / 1000.0
            radio = radioDelRadar(seg + ADELANTO_SEGUNDOS)
            delay(16)
        }
    }

    val estadoCamara = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(
            centro.aLatLng(),
            zoomParaRadio(RADIO_PRIMER_ANILLO),
        )
    }

    // LA CÁMARA SE MUEVE SOLA, en un bucle que arranca UNA vez.
    //
    // Con `LaunchedEffect(radio)` el efecto se cancelaba y relanzaba en cada
    // cuadro —el radio cambia 60 veces por segundo—, dejando cada animación a
    // medio camino: la cámara avanzaba y RETROCEDÍA. Acá cada paso espera a
    // que su animación termine antes de pedir la siguiente.
    LaunchedEffect(Unit) {
        while (true) {
            val z = zoomParaRadio(radio)
            if (kotlin.math.abs(z - estadoCamara.position.zoom) > 0.02f) {
                estadoCamara.animate(
                    CameraUpdateFactory.newLatLngZoom(centro.aLatLng(), z),
                    durationMs = 900,
                )
            } else {
                delay(120)
            }
        }
    }

    // EL PULSO: sale del centro y se va hacia afuera, en bucle. Es lo que se
    // lee como un radar barriendo; sin esto el círculo solo crece, muy
    // despacio, y no comunica que se está buscando AHORA.
    val animacion = rememberInfiniteTransition(label = "radar")
    val avancePulso by animacion.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "pulso",
    )

    // La moto late: la distingue de un pin fijo del mapa. Dice que está EN
    // LÍNEA ahora, no que es una posición vieja guardada.
    val latidoMoto by animacion.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "latido",
    )

    Box(modifier = modifier) {
        MapaBase(estadoCamara = estadoCamara, modifier = Modifier.fillMaxSize()) {
            // El pulso primero: es el fondo. Crece hasta el borde del alcance
            // y se desvanece al llegar, como una onda saliendo del cliente.
            Circle(
                center = centro.aLatLng(),
                radius = radio * (0.08 + 0.92 * avancePulso),
                fillColor = AMBAR.copy(alpha = 0.30f * (1f - avancePulso)),
                strokeColor = AMBAR_BORDE.copy(alpha = 0.55f * (1f - avancePulso)),
                strokeWidth = 4f,
            )
            // El alcance: hasta dónde se está buscando AHORA. Con borde fuerte
            // porque el ámbar suelto sobre calles claras se pierde y el radar
            // queda como una mancha sin forma.
            Circle(
                center = centro.aLatLng(),
                radius = radio,
                fillColor = AMBAR.copy(alpha = 0.20f),
                strokeColor = AMBAR_BORDE,
                strokeWidth = 9f,
            )
            // La zona inmediata: fija en 500 m, sirve de escala para que el
            // crecimiento del alcance se note.
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
            // Cada moto disponible, con su carita.
            //
            // Antes era un círculo gris oscuro: sobre el mapa se leía como un
            // punto de suciedad, no como "hay alguien ahí".
            val temaOscuro = isSystemInDarkTheme()
            val icono = remember(latidoMoto, temaOscuro) {
                iconoDeMoto(latidoMoto, temaOscuro)
            }
            motos.forEach { moto ->
                Marker(
                    state = rememberMarkerState(position = moto.aLatLng()),
                    icon = icono,
                    anchor = Offset(0.5f, 0.5f),
                    zIndex = 2f,
                )
            }
        }
    }
}

/**
 * El icono de una moto disponible: el emoji dibujado en un bitmap.
 *
 * Google Maps no acepta un composable como marcador, así que el emoji se pinta
 * en un canvas y se pasa como imagen. `escala` viene de la animación: al
 * cambiar, el icono se regenera y la moto parece latir.
 */
private fun iconoDeMoto(escala: Float, oscuro: Boolean): BitmapDescriptor {
    val lado = (96 * escala).toInt().coerceAtLeast(1)
    val bitmap = createBitmap(lado, lado)
    val canvas = AndroidCanvas(bitmap)

    // UN DISCO DETRÁS, del color que contraste con el mapa.
    //
    // El emoji suelto se pierde: sobre las calles claras del tema de día casi
    // no se ve, y sobre el mapa oscuro tampoco. El disco lo despega del fondo
    // en los dos casos —claro sobre mapa oscuro, oscuro sobre mapa claro— y
    // de paso hace de área táctil.
    val fondo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (oscuro) 0xFFF5F5F5.toInt() else 0xFF1A1C1B.toInt()
    }
    val borde = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (oscuro) 0xFF1A1C1B.toInt() else 0xFFFFFFFF.toInt()
        style = Paint.Style.STROKE
        strokeWidth = lado * 0.06f
    }
    val radio = lado / 2f - borde.strokeWidth
    canvas.drawCircle(lado / 2f, lado / 2f, radio, fondo)
    canvas.drawCircle(lado / 2f, lado / 2f, radio, borde)

    val pincel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        // Más chico que el disco: el emoji va ADENTRO, con aire alrededor.
        textSize = lado * 0.54f
        textAlign = Paint.Align.CENTER
    }
    // `descent/ascent` centra el glifo de verdad: con `lado / 2` a secas el
    // emoji queda corrido hacia abajo.
    val medio = lado / 2f
    val base = medio - (pincel.descent() + pincel.ascent()) / 2f
    canvas.drawText("🛵", medio, base, pincel)
    return BitmapDescriptorFactory.fromBitmap(bitmap)
}

/** La zona inmediata, en metros. Fija: sirve de escala. */
private const val RADIO_CERCA_METROS = 500.0

/** El primer anillo del backend: donde arranca la búsqueda. */
private const val RADIO_PRIMER_ANILLO = 500.0

/**
 * El radar arranca CRECIENDO, no esperando.
 *
 * Los anillos abren el primero a los 20 s, así que el radar se quedaba clavado
 * en 500 m un buen rato antes de moverse — y eso se siente como que no pasa
 * nada. No cambia a quién avisa el backend: es solo lo que se DIBUJA.
 */
private const val ADELANTO_SEGUNDOS = 6.0

/**
 * Los anillos del backend: metros y segundo en que se abre el siguiente.
 *
 * Los MISMOS que usa `core/radio-busqueda.ts`. Si el radar dibujara otra cosa,
 * le estaría mintiendo al cliente sobre cuánta gente vio su pedido.
 */
private val ANILLOS = listOf(
    500.0 to 20.0,
    1000.0 to 40.0,
    2000.0 to 65.0,
    3000.0 to Double.MAX_VALUE,
)

/** Hasta dónde se busca a los `seg` segundos. Continuo, no a saltos. */
internal fun radioDelRadar(seg: Double): Double {
    var desde = 0.0
    for (i in ANILLOS.indices) {
        val (metros, hasta) = ANILLOS[i]
        if (seg < hasta) {
            val siguiente = ANILLOS.getOrNull(i + 1) ?: return metros
            if (hasta == Double.MAX_VALUE) return metros
            val avance = (seg - desde) / (hasta - desde)
            return metros + (siguiente.first - metros) * avance
        }
        desde = hasta
    }
    return ANILLOS.last().first
}

/**
 * El zoom al que un círculo de `metros` llena la pantalla.
 *
 * En Web Mercator cada nivel duplica la escala: a 16.4 entran ~500 m en un
 * teléfono, y baja un nivel por cada duplicación del radio.
 */
internal fun zoomParaRadio(metros: Double): Float {
    val z = 16.4 - kotlin.math.log2(metros / 500.0)
    return z.coerceIn(11.0, 18.0).toFloat()
}
