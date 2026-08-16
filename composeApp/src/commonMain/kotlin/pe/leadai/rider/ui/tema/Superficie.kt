package pe.leadai.rider.ui.tema
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * Las formas y la profundidad, en un solo lugar.
 *
 * El código tenía radios de 8, 12, 14, 16, 24 y 50 mezclados sin criterio: eso
 * es lo que hace que una pantalla se vea armada de a pedazos aunque cada
 * pieza esté bien. Y la elevación era de 1-2dp, o sea prácticamente plana.
 *
 * Los valores salen de `design/jala-design-tokens.json`, que es la fuente de
 * verdad: 16dp en cards y botones, 24dp en las hojas grandes.
 *
 * La regla es simple: cuanto más "arriba" está algo, más redondeado y más
 * separado del fondo. Una hoja que cubre la pantalla lleva más radio que un
 * chip, y un diálogo proyecta más sombra que una card.
 */
object Formas {
    /** Chips, etiquetas, cosas chicas que acompañan. */
    val chip = RoundedCornerShape(12.dp)

    /** Cards, botones, campos: el radio de la marca. */
    val card = RoundedCornerShape(16.dp)

    /** Hojas y diálogos: lo que cubre la pantalla va más redondeado. */
    val hoja = RoundedCornerShape(24.dp)

    /** Solo para lo circular de verdad: avatares, botones redondos. */
    val circulo = RoundedCornerShape(50)

    /** Una hoja que sube desde abajo: redondea ARRIBA y apoya en el borde. */
    val hojaInferior = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
}

/**
 * Cuánto se separa del fondo cada cosa.
 *
 * En dp. Compose los usa para la sombra, así que valores altos se ven pesados
 * y de gama baja; la diferencia entre "premium" y "cargado" está en usar poca
 * elevación pero CONSISTENTE.
 */
object Elevacion {
    /** Pegado al fondo: listas, separadores. */
    val plano = 0.dp

    /** Apenas despegada: las cards del feed. */
    val card = 2.dp

    /** Lo que flota sobre el contenido: chips sobre el mapa, FABs. */
    val flotante = 6.dp

    /** Diálogos y hojas: lo que exige atención y bloquea el resto. */
    val dialogo = 12.dp
}

/**
 * Sombra con el COLOR de la marca, no negra.
 *
 * Una sombra negra pura se ve barata: es lo que hace por defecto cualquier
 * framework, y el ojo lo registra como "sin terminar". Tiñéndola apenas con el
 * carbón de la marca, la pieza parece apoyada sobre la misma superficie en vez
 * de recortada encima.
 *
 * Es el detalle más barato que sube la percepción de calidad: no cuesta
 * rendimiento, funciona desde Android 8 y no depende de ninguna librería.
 *
 * @param elevacion cuánto se separa del fondo. Usar la escala de `Elevacion`.
 */
@Composable
fun Modifier.sombraDeMarca(
    elevacion: Dp,
    forma: Shape = Formas.card,
): Modifier {
    val colores = ColoresJala.actuales
    return this.shadow(
        elevation = elevacion,
        shape = forma,
        // Las dos tintadas con el carbón de la marca. `ambient` es la luz
        // difusa y `spot` la direccional: si solo se tiñe una, la sombra sale
        // con dos tonos y se nota.
        ambientColor = colores.marcaCarbon.copy(alpha = 0.5f),
        spotColor = colores.marcaCarbon.copy(alpha = 0.5f),
        clip = false,
    )
}
