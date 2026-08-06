package pe.leadai.rider.ui.alta.componentes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import pe.leadai.rider.ui.tema.ColoresJala

/**
 * Barra de progreso del alta: "PASO 1 DE 2" con la barra debajo.
 *
 * Un formulario largo sin indicador de avance se abandona: el usuario no sabe
 * si le faltan dos campos o veinte. Mostrar en qué paso va y cuántos quedan
 * baja mucho el abandono.
 */
@Composable
fun ProgresoAlta(
    paso: Int,
    total: Int,
    titulo: String,
    modifier: Modifier = Modifier,
) {
    val colores = ColoresJala.actuales

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            "PASO $paso DE $total: ${titulo.uppercase()}",
            style = MaterialTheme.typography.labelSmall,
            color = colores.espera,
        )
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(
                    color = colores.tintaSecundaria.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(50),
                ),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(paso.toFloat() / total)
                    .height(6.dp)
                    .background(
                        color = colores.marcaAmarillo,
                        shape = RoundedCornerShape(50),
                    ),
            )
        }
    }
}

/**
 * "✓ Validado en RENIEC — Juan Pérez García": la confirmación de que el DNI
 * existe y a quién pertenece.
 *
 * Se muestra en verde porque es una buena noticia: el rider ve que el sistema
 * lo reconoció y que no tiene que escribir su nombre a mano.
 *
 * OJO: la validación NUNCA bloquea el alta. Si el proveedor no responde o el
 * DNI no aparece, el rider sigue igual y el caso queda para revisión manual —
 * dejar afuera a alguien porque un servicio externo se cayó sería peor.
 */
@Composable
fun DniValidado(
    nombreOficial: String,
    modifier: Modifier = Modifier,
) {
    val colores = ColoresJala.actuales

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = colores.exito.copy(alpha = 0.10f),
                shape = MaterialTheme.shapes.medium,
            )
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(28.dp).background(colores.exito, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text("✓", style = MaterialTheme.typography.labelLarge, color = colores.superficieCard)
        }
        Spacer(Modifier.size(12.dp))
        Column {
            Text(
                "VALIDADO EN RENIEC",
                style = MaterialTheme.typography.labelSmall,
                color = colores.exito,
            )
            Text(
                nombreOficial,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/**
 * El caso en que el DNI no se encontró: se avisa, pero sin alarmar y sin
 * frenar. El rider puede seguir.
 */
@Composable
fun DniSinValidar(modifier: Modifier = Modifier) {
    val colores = ColoresJala.actuales

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = colores.espera.copy(alpha = 0.10f),
                shape = MaterialTheme.shapes.medium,
            )
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("⏳", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.size(12.dp))
        Text(
            "Lo verificaremos manualmente. Podés seguir.",
            style = MaterialTheme.typography.bodyMedium,
            color = colores.tintaSecundaria,
        )
    }
}
