package pe.leadai.rider.ui.comunes

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

// Stub: el selector de punto en iOS llega con Fase D (WKWebView + el puente
// por WKScriptMessageHandler). Mientras tanto el cliente confirma la dirección
// por texto, como antes.
@Composable
actual fun MapaElegirPunto(
    url: String,
    onPunto: (lat: Double, lng: Double) -> Unit,
    modifier: Modifier,
) {
    Box(modifier)
}
