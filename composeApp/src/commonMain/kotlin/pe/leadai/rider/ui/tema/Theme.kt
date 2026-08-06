package pe.leadai.rider.ui.tema

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Paleta de JALA, tomada del logo: las flechas amarillas sobre carbón.
 *
 * Reemplaza al teal "Brand Harmony" heredado de LeadAI — esa es la marca B2B
 * que se le vende a los negocios, y no le dice nada a un motorizado en Tacna.
 *
 * DECISIÓN CLAVE — por qué el `primary` es el CARBÓN y no el amarillo:
 * el amarillo de marca (#F0B429) es precioso pero **no sirve como fondo de
 * botón**: texto blanco encima da ~1.9:1 de contraste, muy por debajo del
 * 4.5:1 que exige accesibilidad, y a pleno sol —donde el rider usa la app—
 * se vuelve ilegible. Así que:
 *   - `primary` = carbón #2E3440 → botones, con texto blanco (13:1, AAA)
 *   - `secondary` = amarillo #F0B429 → acentos de marca, badges, resaltados,
 *     SIEMPRE con texto carbón encima (9.7:1), nunca blanco
 * El amarillo sigue siendo la cara de la marca; simplemente no carga texto
 * claro. Ver [TokensExtra.marcaAmarillo] para los usos directos.
 */
private val JalaColorScheme = lightColorScheme(
    // Carbón del logo: la acción. Sobrio, con contraste de sobra al sol.
    primary = Color(0xFF2E3440),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF3B4252),
    onPrimaryContainer = Color(0xFFE5E9F0),
    inversePrimary = Color(0xFFB8C0CF),

    // Amarillo de marca: acentos y resaltados. SIEMPRE con texto oscuro.
    secondary = Color(0xFFF0B429),
    onSecondary = Color(0xFF2E3440),
    secondaryContainer = Color(0xFFFDF0D0),
    onSecondaryContainer = Color(0xFF6B4E00),

    // Ámbar profundo: el tercer rol, para lo que necesita atención sin ser error.
    tertiary = Color(0xFF9A6700),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFE9B8),
    onTertiaryContainer = Color(0xFF5C3D00),

    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF93000A),

    // Fondo cálido, casi blanco: descansa la vista al sol y hace que las
    // cards blancas se despeguen.
    background = Color(0xFFFAFAF8),
    onBackground = Color(0xFF1C1D21),

    surface = Color(0xFFFAFAF8),
    onSurface = Color(0xFF1C1D21),
    surfaceVariant = Color(0xFFE6E6E2),
    onSurfaceVariant = Color(0xFF48494E),

    outline = Color(0xFF79797E),
    outlineVariant = Color(0xFFC9C9C5),

    inverseSurface = Color(0xFF2E3440),
    inverseOnSurface = Color(0xFFF1F1EF),

    surfaceTint = Color(0xFF2E3440),
    surfaceBright = Color(0xFFFAFAF8),
    surfaceDim = Color(0xFFDBDBD7),
    surfaceContainer = Color(0xFFEFEFEC),
    surfaceContainerHigh = Color(0xFFE9E9E6),
    surfaceContainerHighest = Color(0xFFE3E3E0),
    surfaceContainerLow = Color(0xFFF5F5F2),
    surfaceContainerLowest = Color(0xFFFFFFFF),
)

/**
 * Colores semánticos de marca sin slot en `ColorScheme` de Material 3
 * (`*_fixed`/`*_fixed_variant` de M3 tampoco tienen slot en Compose M3 y no
 * los usa ningún prototipo actual — se omiten a propósito, ver ARQUITECTURA.md).
 */
object TokensExtra {
    /**
     * El AMARILLO del logo. Es la cara de la marca: acentos, el monto que el
     * rider gana, badges destacados.
     *
     * REGLA: nunca lleva texto blanco encima (1.9:1, ilegible al sol). Usalo
     * de fondo con texto carbón, o como color de texto/ícono sobre fondo
     * oscuro.
     */
    val marcaAmarillo = Color(0xFFF0B429)

    /** El CARBÓN del logo. Fondos oscuros, headers, texto sobre amarillo. */
    val marcaCarbon = Color(0xFF2E3440)

    /** Rojo de urgencia: sin saldo, carrera que se vence, error que frena el trabajo. */
    val calor = Color(0xFFE5484D)

    /** Verde de éxito: entregado, pago confirmado. */
    val exito = Color(0xFF2E7D32)

    /**
     * Ámbar de espera / atención. Es el color de "llevás S/60 para la compra"
     * y de "pendiente de verificación": pide mirar sin gritar.
     */
    val espera = Color(0xFFD98B0C)

    /** Carbón profundo: bottom navigation y headers. */
    val slate = Color(0xFF1C1D21)

    /** Carbón presionado del botón primario. */
    val brasaPresionado = Color(0xFF232833)

    /** Texto principal. Coincide con onSurface pero se expone para usos fuera de Material. */
    val tintaPrimaria = Color(0xFF1C1D21)

    /** Texto secundario / placeholders. */
    val tintaSecundaria = Color(0xFF5B5C63)

    /** Blanco puro, para cards sobre el fondo cálido. */
    val blanco = Color(0xFFFFFFFF)

    /**
     * Paleta de ETIQUETAS DE NEGOCIO (bandeja global, Fase C2): pares
     * `fondo pastel → texto oscuro` para que cada negocio se distinga de un
     * vistazo (pedido de Jonathan 2026-07-22: "a cada empresa un color
     * distinto"). Tonos suaves a propósito — no compiten con los colores
     * semánticos (coral calor / ámbar espera / teal acción). La asignación
     * es estable por tenant: ver [colorEtiquetaNegocio].
     */
    val etiquetasNegocio: List<Pair<Color, Color>> = listOf(
        Color(0xFFE2E3F8) to Color(0xFF3D4279), // lavanda
        Color(0xFFD8F0E3) to Color(0xFF1F5C40), // menta
        Color(0xFFD9EBF8) to Color(0xFF1D4E73), // cielo
        Color(0xFFF8DEE7) to Color(0xFF803049), // rosa
        Color(0xFFF3EAD2) to Color(0xFF6B5620), // arena
        Color(0xFFEBDDF6) to Color(0xFF5A3A78), // lila
    )
}

/**
 * Color estable de la etiqueta de un negocio: el `tenantId` (cuid inmutable)
 * se hashea al índice de la paleta [TokensExtra.etiquetasNegocio] — el mismo
 * negocio SIEMPRE sale del mismo color, en cualquier sesión y pantalla. Dos
 * negocios pueden colisionar si hay más de 6 (aceptado: la etiqueta además
 * lleva el nombre).
 */
fun colorEtiquetaNegocio(tenantId: String): Pair<Color, Color> {
    val indice = indiceEtiquetaNegocio(tenantId, TokensExtra.etiquetasNegocio.size)
    return TokensExtra.etiquetasNegocio[indice]
}

/** Hash estable (suma de chars, sin depender de `hashCode` de la plataforma) → índice en `[0, cantidad)`. */
internal fun indiceEtiquetaNegocio(tenantId: String, cantidad: Int): Int {
    var acumulado = 0
    for (c in tenantId) acumulado = (acumulado * 31 + c.code) and 0x7FFFFFFF
    return acumulado % cantidad
}

/**
 * Shapes: 16px (rounded-lg) para cards/botones primarios (medium/large),
 * 8px (rounded-md) para inputs (small), full/pill para chips y status pills.
 */
val LeadAIShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

/** Shape completamente redondeado (pill) para chips y status pills. */
val FormaChip = RoundedCornerShape(CornerSize(50))

@Composable
fun LeadAITheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = JalaColorScheme,
        typography = LeadAITypography,
        shapes = LeadAIShapes,
        content = content,
    )
}
