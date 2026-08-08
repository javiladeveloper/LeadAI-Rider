package pe.leadai.rider.ui.tema

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * El movimiento de la app, en un solo lugar.
 *
 * Antes no había ninguno: las cards y los diálogos aparecían de golpe, y una
 * interfaz sin transiciones se siente muerta aunque los colores y el espaciado
 * estén bien. El ojo necesita ver de dónde vino algo para entender qué pasó.
 *
 * Las duraciones son cortas a propósito. Esto lo usa gente que está pidiendo
 * una moto o manejando una: una animación de medio segundo es lucirse a costa
 * de hacerlos esperar. 150-250 ms se percibe como fluido; más, como lento.
 */
object Movimiento {

    /** Lo que tarda algo en aparecer o irse. */
    const val RAPIDO_MS = 150

    /** Para lo que entra en pantalla y hay que notar. */
    const val NORMAL_MS = 220

    /**
     * Arranca lento y frena suave: el movimiento natural de una hoja que sube.
     * Un `LinearEasing` se ve mecánico, como una animación de los 90.
     */
    val SUAVE = CubicBezierEasing(0.2f, 0f, 0f, 1f)
}

/**
 * Cómo entra una card que aparece dentro del formulario.
 *
 * Sube 12dp mientras se desvanece hacia adentro: el desplazamiento chico
 * insinúa que la card "creció" del contenido de arriba, en vez de materializarse
 * encima. Con `expandVertically` el resto del formulario se corre suave en vez
 * de saltar.
 */
@Composable
fun AparecerCard(
    visible: Boolean,
    modifier: Modifier = Modifier,
    contenido: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = expandVertically(
            animationSpec = tween(Movimiento.NORMAL_MS, easing = Movimiento.SUAVE),
            expandFrom = Alignment.Top,
        ) + fadeIn(tween(Movimiento.NORMAL_MS)) + slideInVertically(
            animationSpec = tween(Movimiento.NORMAL_MS, easing = Movimiento.SUAVE),
            initialOffsetY = { it / 4 },
        ),
        // Salir más rápido que entrar: lo que ya no importa no debe hacerse
        // esperar.
        exit = shrinkVertically(
            animationSpec = tween(Movimiento.RAPIDO_MS),
            shrinkTowards = Alignment.Top,
        ) + fadeOut(tween(Movimiento.RAPIDO_MS)),
    ) {
        contenido()
    }
}

/**
 * Cómo entra el contenido de un diálogo.
 *
 * Con un rebote apenas perceptible (`dampingRatio` alto): un popup que aparece
 * plano se siente pegado, y uno que rebota mucho se siente de juguete. El
 * `scaleIn` arranca en 0.92 y no en 0 — un diálogo que crece desde la nada
 * marea.
 */
@Composable
fun AparecerDialogo(
    visible: Boolean,
    modifier: Modifier = Modifier,
    contenido: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = scaleIn(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessMediumLow,
            ),
            initialScale = 0.92f,
        ) + fadeIn(tween(Movimiento.RAPIDO_MS)),
        exit = fadeOut(tween(Movimiento.RAPIDO_MS)),
    ) {
        contenido()
    }
}

/**
 * Una lista que se va llenando — las ofertas que llegan, las sugerencias de
 * direcciones.
 *
 * Cada fila entra deslizando desde abajo. Sin esto, tres ofertas que llegan
 * juntas aparecen de un salto y el cliente no registra que son nuevas.
 */
@Composable
fun AparecerFila(
    visible: Boolean = true,
    modifier: Modifier = Modifier,
    contenido: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(tween(Movimiento.NORMAL_MS)) + slideInVertically(
            animationSpec = tween(Movimiento.NORMAL_MS, easing = Movimiento.SUAVE),
            initialOffsetY = { it / 3 },
        ),
        exit = fadeOut(tween(Movimiento.RAPIDO_MS)),
    ) {
        contenido()
    }
}
