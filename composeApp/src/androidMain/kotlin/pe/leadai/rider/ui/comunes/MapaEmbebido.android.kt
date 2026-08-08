package pe.leadai.rider.ui.comunes

import android.webkit.GeolocationPermissions
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import pe.leadai.rider.ui.tema.ColoresJala

/**
 * El mapa, con un indicador MIENTRAS carga.
 *
 * Cargar la página es lento de verdad: el WebView baja Leaflet, los tiles de
 * OpenStreetMap y le pide la ruta a OSRM. Antes ese tiempo era un rectángulo
 * vacío debajo de una hoja ya completa — parecía que la app se había colgado.
 *
 * El indicador tapa el hueco hasta que la página termina de cargar.
 */
@Composable
actual fun MapaEmbebido(url: String, modifier: Modifier) {
    var cargando by remember(url) { mutableStateOf(true) }
    // Qué URL se mandó a cargar.
    //
    // NO se usa `webView.url`: el WebView la normaliza (escapa caracteres,
    // reordena parámetros) y la comparación nunca coincide, así que recargaba
    // en bucle y el mapa quedaba en blanco.
    //
    // `remember(url)` y no `remember`: si la URL cambia hay que volver a
    // empezar. Con un `remember` pelado, `factory` cargaba una url y dejaba
    // este valor en null, así que `update` la recargaba de inmediato — el
    // mismo bucle, movido de lugar.
    val urlCargada = remember(url) { mutableStateOf<String?>(null) }

    // Red de seguridad: si a los 8 segundos el WebView no avisó que terminó,
    // se descubre igual. Un `onPageFinished` que no llega —por un recurso
    // externo colgado, por ejemplo— dejaba el indicador girando para siempre
    // encima de un mapa que ya estaba dibujado abajo.
    LaunchedEffect(url) {
        delay(8_000)
        cargando = false
    }
    val colores = ColoresJala.actuales

    Box(modifier = modifier) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { contexto ->
            WebView(contexto).apply {
                // JS imprescindible: la página es Leaflet puro. Sin
                // WebViewClient, los links abrirían el navegador de afuera.
                settings.javaScriptEnabled = true
                @Suppress("DEPRECATION")
                settings.setGeolocationEnabled(true)
                // El modo rider lee el GPS del propio teléfono desde la
                // página (watchPosition, ~1 fix/seg): la app ya tiene el
                // permiso de ubicación — se lo cede al WebView sin diálogo.
                webChromeClient = object : WebChromeClient() {
                    override fun onGeolocationPermissionsShowPrompt(
                        origin: String,
                        callback: GeolocationPermissions.Callback,
                    ) {
                        callback.invoke(origin, true, false)
                    }
                }
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String) {
                        cargando = false
                    }

                    // Si la carga cae en mal momento (deploy reiniciando el
                    // API, red móvil parpadeando), reintenta solo en 5s en
                    // vez de quedarse clavado en "Webpage not available".
                    override fun onReceivedError(
                        view: WebView,
                        request: WebResourceRequest,
                        error: WebResourceError,
                    ) {
                        // Sigue "cargando" a propósito: hay un reintento en
                        // camino, y mostrar el mapa vacío sería peor.
                        if (request.isForMainFrame) reintentarLuego(view)
                    }

                    override fun onReceivedHttpError(
                        view: WebView,
                        request: WebResourceRequest,
                        errorResponse: WebResourceResponse,
                    ) {
                        if (request.isForMainFrame) reintentarLuego(view)
                    }
                }
                // Se marca ANTES de cargar: si no, `update` corre con el
                // valor todavía en null y dispara una segunda carga.
                urlCargada.value = url
                loadUrl(url)
            }
        },
        update = { webView ->
            // Se compara contra la ÚLTIMA URL PEDIDA, no contra `webView.url`:
            // el WebView la normaliza (escapa caracteres, reordena
            // parámetros), así que `webView.url != url` daba true para
            // siempre. El mapa recargaba en bucle y se quedaba en blanco —
            // que es justo lo que pasaba al elegir el destino.
            if (urlCargada.value != url) {
                urlCargada.value = url
                cargando = true
                webView.loadUrl(url)
            }
        },
    )

        if (cargando) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colores.marcaCarbon),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(36.dp),
                    color = colores.marcaAmarillo,
                )
                androidx.compose.foundation.layout.Spacer(Modifier.height(14.dp))
                Text(
                    "Cargando el mapa…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colores.tintaSecundaria,
                )
            }
        }
    }
}

private fun reintentarLuego(webView: WebView) {
    webView.postDelayed({ webView.reload() }, 5_000)
}
