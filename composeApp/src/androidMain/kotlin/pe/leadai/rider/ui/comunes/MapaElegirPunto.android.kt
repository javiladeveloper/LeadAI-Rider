package pe.leadai.rider.ui.comunes

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.json.JSONObject

/**
 * El mapa del selector de punto.
 *
 * `@SuppressLint("SetJavaScriptEnabled")`: la página es Leaflet puro y sin JS
 * no hay mapa. Se carga SOLO desde nuestro backend, así que no hay contenido
 * de terceros ejecutándose acá.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
actual fun MapaElegirPunto(
    url: String,
    onPunto: (lat: Double, lng: Double) -> Unit,
    modifier: Modifier,
) {
    var cargando by remember(url) { mutableStateOf(true) }
    // `rememberUpdatedState` para que el puente JS —que se crea UNA vez con el
    // WebView— siempre llame al callback actual. Sin esto se quedaría con el
    // de la primera composición y los puntos irían a un estado viejo.
    val alElegir by rememberUpdatedState(onPunto)

    Box(modifier = modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { contexto ->
                WebView(contexto).apply {
                    settings.javaScriptEnabled = true
                    // Sin esto los links abrirían el navegador de afuera.
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String) {
                            cargando = false
                        }
                    }
                    addJavascriptInterface(PuenteDelMapa(alElegir), "Puente")
                    loadUrl(url)
                }
            },
        )
        if (cargando) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center).size(28.dp),
                strokeWidth = 2.dp,
            )
        }
    }
}

/**
 * Lo que la página llama cuando el mapa deja de moverse.
 *
 * El JSON se parsea acá y no en el WebView: un punto mal formado no puede
 * tumbar la pantalla del cliente, así que un fallo se ignora — el pin sigue
 * donde estaba y la dirección anterior se mantiene.
 */
private class PuenteDelMapa(
    private val onPunto: (Double, Double) -> Unit,
) {
    @JavascriptInterface
    fun punto(json: String) {
        runCatching {
            val o = JSONObject(json)
            onPunto(o.getDouble("lat"), o.getDouble("lng"))
        }
    }
}
