package pe.leadai.rider.ui.cliente.componentes

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/**
 * "Nadie tomó tu carrera" — cuando se acabó el tiempo de búsqueda.
 *
 * El backend deja de devolver la carrera al vencer, así que la pantalla volvía
 * sola al formulario sin decir NADA: el cronómetro llegaba a cero, el radar
 * seguía girando, y de golpe estaba de nuevo en el inicio. Se siente como si
 * el pedido se hubiera perdido.
 *
 * Dice qué pasó y qué hacer. Casi siempre es lo mismo —el flete quedó corto
 * para la distancia— y por eso el botón principal es pedir de nuevo: el
 * formulario conserva las direcciones, así que solo hay que subir el monto.
 */
@Composable
fun DialogoCarreraVencida(
    onPedirDeNuevo: () -> Unit,
    onCerrar: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCerrar,
        title = { Text("⏱️ Nadie tomó tu carrera") },
        text = {
            Text(
                "Se acabó el tiempo de búsqueda y ningún motorizado respondió. " +
                    "Probá de nuevo ofreciendo un poco más: en Tacna, S/2 más " +
                    "suele ser la diferencia.",
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            Button(onClick = onPedirDeNuevo) { Text("Pedir de nuevo") }
        },
        dismissButton = {
            TextButton(onClick = onCerrar) { Text("Ahora no") }
        },
    )
}
