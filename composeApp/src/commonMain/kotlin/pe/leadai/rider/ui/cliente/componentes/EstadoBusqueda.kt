package pe.leadai.rider.ui.cliente.componentes

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pe.leadai.rider.ui.tema.ColoresJala

/**
 * Cuánto dura la búsqueda antes de que la carrera venza. Igual que
 * `MINUTOS_HASTA_EXPIRAR` del backend.
 */
private const val SEGUNDOS_BUSQUEDA = 15 * 60

/**
 * El estado de la búsqueda: qué está pasando y cuánto falta.
 *
 * Una pantalla que solo dice "buscando…" no distingue entre "hay diez motos
 * mirando tu pedido" y "no hay nadie en la ciudad" — y esos dos casos piden
 * decisiones distintas del cliente: esperar, o subir la oferta.
 *
 * El contador es lo que convierte la espera en algo acotado: sin él, treinta
 * segundos se sienten como cinco minutos.
 */
@Composable
fun EstadoBusqueda(
    segundosRestantes: Int,
    motosCerca: Int,
    ofertas: Int,
    modifier: Modifier = Modifier,
) {
    val colores = ColoresJala.actuales
    val fraccion by animateFloatAsState(
        targetValue = (segundosRestantes / SEGUNDOS_BUSQUEDA.toFloat()).coerceIn(0f, 1f),
        animationSpec = tween(1_000),
        label = "busqueda",
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    tituloDeLaBusqueda(ofertas, motosCerca),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    detalleDeLaBusqueda(ofertas, motosCerca),
                    style = MaterialTheme.typography.labelSmall,
                    color = colores.tintaSecundaria,
                )
            }
            Text(
                relojDe(segundosRestantes),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                ),
                // Ámbar en el último minuto: es cuando conviene subir la
                // oferta en vez de seguir esperando.
                color = if (segundosRestantes <= 60) colores.espera else colores.tintaSecundaria,
            )
        }

        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraccion)
                    .height(3.dp)
                    .background(if (segundosRestantes <= 60) colores.espera else colores.exito),
            )
        }
    }
}

/**
 * Qué decir según lo que está pasando.
 *
 * Tres estados distintos, como en inDrive: buscando a secas, "hay motos
 * cerca" cuando el radar encontró alguna, y "te están ofreciendo" cuando ya
 * llegó una propuesta. Cada uno le dice al cliente algo que cambia lo que
 * puede hacer.
 */
internal fun tituloDeLaBusqueda(ofertas: Int, motosCerca: Int): String = when {
    ofertas > 0 -> if (ofertas == 1) "1 motorizado te ofreció" else "$ofertas motorizados te ofrecieron"
    motosCerca > 0 -> "Ofreciendo tu tarifa"
    else -> "🔍 Buscando motorizado…"
}

internal fun detalleDeLaBusqueda(ofertas: Int, motosCerca: Int): String = when {
    ofertas > 0 -> "Elegí con quién querés ir"
    motosCerca == 1 -> "1 motorizado está cerca"
    motosCerca > 1 -> "$motosCerca motorizados están cerca"
    // Sin motos alrededor el radar sigue creciendo: decirlo evita que el
    // cliente crea que la app se colgó.
    else -> "Ampliando el área de búsqueda"
}

/** Segundos a "2:34". */
internal fun relojDe(segundos: Int): String {
    val s = segundos.coerceAtLeast(0)
    val minutos = s / 60
    val resto = (s % 60).toString().padStart(2, '0')
    return "$minutos:$resto"
}
