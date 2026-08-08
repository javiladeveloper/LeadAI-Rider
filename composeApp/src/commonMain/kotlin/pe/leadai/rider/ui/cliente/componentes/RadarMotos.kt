package pe.leadai.rider.ui.cliente.componentes

import androidx.compose.foundation.background
import pe.leadai.rider.ui.tema.ColoresJala
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
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

    // Fondo carbón detrás del WebView: si el mapa no carga se ve oscuro y no
    // blanco, que es la señal de que ALGO está ahí. Un hueco del color del
    // fondo de la app es indistinguible de "no se dibujó nada".
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(ColoresJala.actuales.marcaCarbon),
    ) {
        MapaEmbebido(
            url = "$URL_RADAR?lat=$lat&lng=$lng",
            modifier = Modifier.fillMaxSize(),
        )
    }
}
