package pe.leadai.rider.ui.comunes

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/**
 * "Hay una versión nueva". Aparece sobre cualquier pantalla cuando el
 * `versionCode` publicado supera al de esta build.
 *
 * Dos modos:
 * - **Sugerido** (lo normal): se puede posponer con "Más tarde" y no vuelve a
 *   aparecer en esa sesión.
 * - **Obligatorio**: sin escape. Para cuando una versión vieja dejó de
 *   funcionar contra el backend y seguir usándola solo trae errores raros.
 *   Un rider trabado a media carrera por un diálogo es peor que un bug, así
 *   que esto se usa solo cuando de verdad no queda opción.
 */
@Composable
fun DialogoActualizacion(
    versionName: String,
    notas: String,
    obligatoria: Boolean,
    onActualizar: () -> Unit,
    onMasTarde: () -> Unit,
) {
    AlertDialog(
        // Con una obligatoria, tocar afuera no la cierra.
        onDismissRequest = { if (!obligatoria) onMasTarde() },
        title = {
            Text(if (obligatoria) "🔄 Actualización necesaria" else "🎉 Hay una versión nueva")
        },
        text = {
            Text(
                when {
                    notas.isNotBlank() -> notas
                    obligatoria -> "Esta versión ya no funciona. Actualiza para seguir trabajando."
                    versionName.isNotBlank() -> "La versión $versionName ya está lista, con mejoras y arreglos."
                    else -> "Hay una versión nueva con mejoras y arreglos."
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            Button(onClick = onActualizar) { Text("Actualizar") }
        },
        dismissButton = {
            if (!obligatoria) {
                TextButton(onClick = onMasTarde) { Text("Más tarde") }
            }
        },
    )
}
