package pe.leadai.rider.ui.carreras.componentes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import pe.leadai.rider.ui.comunes.MapaEmbebido
import pe.leadai.rider.ui.tema.ColoresJala

/**
 * La recarga del monedero, DENTRO de la app.
 *
 * Antes se abría en el navegador del teléfono: el rider salía de la app, veía
 * la barra de direcciones de Chrome y una página con los colores del diseño
 * anterior. Justo cuando hay que escribir 16 dígitos de una tarjeta, que es el
 * peor momento para dudar de dónde estás parado.
 *
 * Sigue siendo la página de Culqi, no un formulario nuestro: los datos de la
 * tarjeta nunca pasan por código propio, que es lo que exige PCI-DSS. Lo que
 * cambia es el marco — se ve como parte de la app.
 *
 * Reusa [MapaEmbebido], que ya es un WebView con indicador de carga. El nombre
 * quedó del tracking, pero lo que hace es exactamente esto.
 */
@Composable
fun HojaPago(
    url: String,
    onCerrar: () -> Unit,
) {
    val colores = ColoresJala.actuales

    Dialog(
        onDismissRequest = onCerrar,
        // `usePlatformDefaultWidth = false` para que ocupe casi toda la
        // pantalla: un formulario de tarjeta en un diálogo chico obliga a
        // hacer scroll dentro de un scroll.
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.9f)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(24.dp)),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Recargar saldo",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                // Cerrar SIEMPRE visible: una pantalla de pago sin salida
                // evidente asusta, y el rider abandona la recarga.
                Box(
                    modifier = Modifier
                        .clickable { onCerrar() }
                        .padding(8.dp),
                ) {
                    Text(
                        "✕",
                        style = MaterialTheme.typography.titleMedium,
                        color = colores.tintaSecundaria,
                    )
                }
            }

            // El alto real, igual que los otros mapas: sin esto la página se
            // dibuja contra un viewport que el WebView reporta mal.
            var altoPago by remember { mutableStateOf(0) }
            val densidadPago = LocalDensity.current
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .onSizeChanged {
                        altoPago = with(densidadPago) { it.height.toDp() }.value.toInt()
                    },
            ) {
                if (altoPago > 0) {
                    MapaEmbebido(
                        url = url + (if ('?' in url) "&" else "?") + "alto=" + altoPago,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                "🔒 Pago seguro procesado por Culqi",
                style = MaterialTheme.typography.labelSmall,
                color = colores.tintaSecundaria,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}
