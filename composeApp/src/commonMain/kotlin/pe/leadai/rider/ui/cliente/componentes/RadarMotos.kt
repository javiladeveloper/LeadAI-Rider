package pe.leadai.rider.ui.cliente.componentes

import androidx.compose.foundation.background
import pe.leadai.rider.ui.tema.ColoresJala
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import pe.leadai.rider.ui.comunes.MapaEmbebido

/** El radar vive en el backend, como el resto de los mapas. */
private const val URL_RADAR = "https://api.leadai-pe.com/mapa/radar"

/**
 * El radar mientras se busca motorizado: un pulso que crece y va revelando
 * las motos que hay cerca.
 *
 * Dice tres cosas que un texto no dice: que hay gente alrededor, que la
 * búsqueda está EN CURSO, y cuántos hay. No es lo mismo esperar sabiendo que
 * hay diez motos a la vuelta que esperar sin saber si hay alguien en la
 * ciudad — y esos dos casos piden decisiones distintas: aguantar, o subir la
 * oferta.
 *
 * Ocupa todo el espacio disponible a propósito: es lo único que pasa en esa
 * pantalla mientras nadie responde.
 */
@Composable
fun RadarMotos(
    lat: Double?,
    lng: Double?,
    modifier: Modifier = Modifier,
) {
    if (lat == null || lng == null) return

    // El alto se MIDE, no se asume.
    //
    // Antes venía un 420 fijo por defecto y nadie se lo pasaba: la página
    // dibujaba 420dp y si el espacio real era más grande quedaba una franja
    // gris debajo. Peor todavía, los círculos del radar se dimensionan contra
    // ese alto — con la proporción equivocada el pulso quedaba fuera de la
    // parte visible y el mapa se veía plano, sin radar.
    //
    // El WebView no puede deducirlo solo: reporta un viewport que no coincide
    // con su tamaño (medido: body=0, ventana=160 en un contenedor de 549).
    var altoDp by remember { mutableStateOf(0) }
    val densidad = LocalDensity.current

    // Fondo carbón detrás del WebView: si el mapa no carga se ve oscuro y no
    // blanco, que es la señal de que ALGO está ahí. Un hueco del color del
    // fondo de la app es indistinguible de "no se dibujó nada".
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(ColoresJala.actuales.marcaCarbon)
            .onSizeChanged { altoDp = with(densidad) { it.height.toDp() }.value.toInt() },
    ) {
        // Sin el alto todavía no se carga: la página se dibujaría contra cero y
        // habría que recargarla igual apenas se conozca el tamaño.
        if (altoDp > 0) {
            MapaEmbebido(
                url = "$URL_RADAR?lat=$lat&lng=$lng&alto=$altoDp",
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
