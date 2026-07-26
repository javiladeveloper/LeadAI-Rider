package pe.leadai.rider.ui.comunes

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp

private const val DURACION_SHIMMER_MS = 1100

/**
 * Card con shimmer sutil (gradiente animado sobre `surfaceContainerLow`) que
 * simula el contenido cargando. Usada por [PantallaCargando] y reusable
 * suelta donde una pantalla necesite el mismo efecto en un layout distinto.
 */
@Composable
fun SkeletonCard(modifier: Modifier = Modifier) {
    val transicion = rememberInfiniteTransition(label = "shimmer")
    val desplazo by transicion.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = DURACION_SHIMMER_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmer-offset",
    )

    val base = MaterialTheme.colorScheme.surfaceContainerLow
    val brillo = MaterialTheme.colorScheme.surfaceContainerHigh
    val brush = Brush.linearGradient(
        colors = listOf(base, brillo, base),
        start = Offset(desplazo * 300f, 0f),
        end = Offset(desplazo * 300f + 300f, 300f),
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(brush, MaterialTheme.shapes.medium)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        LineaSkeleton(anchoFraccion = 0.5f, alto = 16.dp)
        LineaSkeleton(anchoFraccion = 0.85f, alto = 12.dp)
        LineaSkeleton(anchoFraccion = 0.3f, alto = 12.dp)
    }
}

@Composable
private fun LineaSkeleton(anchoFraccion: Float, alto: androidx.compose.ui.unit.Dp) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .fillMaxWidth(anchoFraccion)
            .height(alto)
            .background(
                MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
                MaterialTheme.shapes.extraSmall,
            ),
    )
}

/**
 * Apila [filas] [SkeletonCard] con separación — el estado "cargando" estándar
 * de toda pantalla con datos (regla de Brand Harmony: nunca un spinner crudo).
 */
@Composable
fun PantallaCargando(filas: Int = 3, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        repeat(filas) {
            SkeletonCard()
        }
    }
}
