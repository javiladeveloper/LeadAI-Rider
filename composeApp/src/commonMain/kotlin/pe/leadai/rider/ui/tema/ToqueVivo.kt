package pe.leadai.rider.ui.tema

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale

/**
 * El botón se hunde apenas al tocarlo.
 *
 * Es el detalle que más separa una app viva de una muerta: sin esto el dedo
 * toca y no pasa nada hasta que la pantalla cambia, y en esos 200 ms el
 * usuario duda de si registró el toque — y vuelve a tocar.
 *
 * 0.96 y no menos: un botón que se achica demasiado parece que se rompe. El
 * `spring` hace que vuelva con un rebote mínimo, como algo físico.
 */
@Composable
fun Modifier.toqueVivo(interaccion: MutableInteractionSource): Modifier {
    val presionado by interaccion.collectIsPressedAsState()
    val escala by animateFloatAsState(
        targetValue = if (presionado) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh,
        ),
        label = "escalaToque",
    )
    return this.scale(escala)
}

/** Crea la fuente de interacción, para no repetirla en cada botón. */
@Composable
fun recordarInteraccion(): MutableInteractionSource =
    remember { MutableInteractionSource() }
