package pe.leadai.rider.ui.cliente

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Modo CLIENTE: pedir una moto y seguir el viaje.
 *
 * // Stub temporal — el Task 4 lo completa.
 * Existe solo para que el grafo de navegación compile: la ruta
 * `Rutas.CLIENTE` ya está cableada, pero el formulario y el seguimiento
 * llegan con el ViewModel del Task 3.
 */
@Composable
fun ClientePantalla(
    alCambiarModo: () -> Unit,
    alCerrarSesion: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "🛵 Pedir un motorizado",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            "En un ratito vas a poder pedir tu moto desde acá.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        TextButton(onClick = alCambiarModo) { Text("🏍️ Quiero manejar") }
        TextButton(onClick = alCerrarSesion) { Text("Cerrar sesión") }
    }
}
