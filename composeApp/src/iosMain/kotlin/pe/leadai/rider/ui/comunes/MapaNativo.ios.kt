package pe.leadai.rider.ui.comunes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * iOS todavía no dibuja mapas (Fase D).
 *
 * Un rectángulo neutro y no un hueco transparente: así se ve que ahí VA algo
 * en vez de parecer un error de layout.
 */
@Composable
actual fun MapaRuta(
    origen: PuntoMapa,
    destino: PuntoMapa,
    modifier: Modifier,
    recorrido: List<PuntoMapa>,
) {
    Box(modifier = modifier.fillMaxSize().background(Color(0xFF1F2429)))
}

@Composable
actual fun MapaRadar(
    centro: PuntoMapa,
    motos: List<PuntoMapa>,
    modifier: Modifier,
) {
    Box(modifier = modifier.fillMaxSize().background(Color(0xFF1F2429)))
}
