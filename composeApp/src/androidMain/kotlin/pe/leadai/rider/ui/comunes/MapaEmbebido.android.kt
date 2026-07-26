package pe.leadai.rider.ui.comunes

import android.webkit.GeolocationPermissions
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@Composable
actual fun MapaEmbebido(url: String, modifier: Modifier) {
    AndroidView(
        modifier = modifier,
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
                    // Si la carga cae en mal momento (deploy reiniciando el
                    // API, red móvil parpadeando), reintenta solo en 5s en
                    // vez de quedarse clavado en "Webpage not available".
                    override fun onReceivedError(
                        view: WebView,
                        request: WebResourceRequest,
                        error: WebResourceError,
                    ) {
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
                loadUrl(url)
            }
        },
        update = { webView -> if (webView.url != url) webView.loadUrl(url) },
    )
}

private fun reintentarLuego(webView: WebView) {
    webView.postDelayed({ webView.reload() }, 5_000)
}
