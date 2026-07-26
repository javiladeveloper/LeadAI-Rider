package pe.leadai.rider.ui.comunes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Estado "vacío" estándar de toda pantalla con datos (Brand Harmony, estilo
 * cálido del prototipo `07-push-empty.png`): [emoji] grande, [titulo] y
 * [texto] en español informal, y una [accion] opcional (texto del botón +
 * callback) cuando la pantalla vacía tiene algo que el usuario puede hacer.
 */
@Composable
fun EstadoVacio(
    emoji: String,
    titulo: String,
    texto: String,
    accion: Pair<String, () -> Unit>? = null,
    accionSecundaria: Pair<String, () -> Unit>? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = emoji, style = MaterialTheme.typography.displayMedium)

        Spacer(Modifier.height(16.dp))

        Text(
            text = titulo,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = texto,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        if (accion != null) {
            Spacer(Modifier.height(24.dp))
            val (textoBoton, onClick) = accion
            Button(
                onClick = onClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                shape = MaterialTheme.shapes.medium,
            ) {
                Text(textoBoton, style = MaterialTheme.typography.labelLarge)
            }
        }

        // Acción secundaria (ej. "📋 Pegar carta completa"): enlace sutil
        // bajo el botón principal, DENTRO del estado vacío centrado.
        if (accionSecundaria != null) {
            Spacer(Modifier.height(4.dp))
            val (textoSec, onClickSec) = accionSecundaria
            TextButton(onClick = onClickSec) {
                Text(textoSec, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
