package pe.leadai.rider.ui.cliente.componentes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import pe.leadai.rider.ui.tema.ColoresJala

/**
 * Calificar al rider apenas termina la carrera.
 *
 * Sin esto el marketplace no cierra: el cliente elige entre ofertas mirando
 * estrellas, y las estrellas solo existen si alguien las pone. Se pregunta en
 * el momento — a las dos horas ya nadie vuelve a calificar.
 *
 * Se puede omitir. Un modal que no deja salir hasta puntuar produce cincos
 * automáticos, que es peor que no tener datos.
 */
@Composable
fun PopupCalificar(
    nombreRider: String?,
    enviando: Boolean,
    onCalificar: (estrellas: Int) -> Unit,
    onOmitir: () -> Unit,
) {
    val colores = ColoresJala.actuales
    var elegidas by remember { mutableStateOf(0) }

    Dialog(onDismissRequest = { if (!enviando) onOmitir() }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp))
                .padding(20.dp),
        ) {
            Text(
                "¿Cómo te fue?",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                nombreRider?.takeIf { it.isNotBlank() }
                    ?.let { "Calificá a $it" }
                    ?: "Calificá al motorizado",
                style = MaterialTheme.typography.bodySmall,
                color = colores.tintaSecundaria,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                (1..5).forEach { n ->
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clickable(enabled = !enviando) { elegidas = n },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (n <= elegidas) "★" else "☆",
                            style = MaterialTheme.typography.headlineMedium,
                            color = if (n <= elegidas) {
                                colores.espera
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            },
                        )
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            val listo = elegidas > 0 && !enviando
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .background(
                        color = if (listo) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        shape = RoundedCornerShape(14.dp),
                    )
                    .clickable(enabled = listo) { onCalificar(elegidas) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (enviando) "ENVIANDO…" else "ENVIAR",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (listo) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        colores.tintaSecundaria
                    },
                )
            }

            Spacer(Modifier.height(10.dp))
            Text(
                "Ahora no",
                style = MaterialTheme.typography.labelLarge,
                color = colores.tintaSecundaria,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !enviando) { onOmitir() }
                    .padding(vertical = 6.dp),
                textAlign = TextAlign.Center,
            )
        }
    }
}
