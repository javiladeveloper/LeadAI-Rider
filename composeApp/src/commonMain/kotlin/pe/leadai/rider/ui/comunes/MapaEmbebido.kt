package pe.leadai.rider.ui.comunes

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Página web embebida para mapas (tracking de la carrera del rider): la app
 * reusa la MISMA página `/track/:pedidoId?embebido=1` que ve el cliente —
 * moto, casa y mejor ruta en vivo — sin meter un SDK de mapas. `expect/actual`
 * como [pe.leadai.rider.ui.carreras.obtenerUbicacionActual]: Android la pinta con
 * un `WebView`; iOS es stub vacío hasta Fase D (WKWebView).
 */
@Composable
expect fun MapaEmbebido(url: String, modifier: Modifier)
