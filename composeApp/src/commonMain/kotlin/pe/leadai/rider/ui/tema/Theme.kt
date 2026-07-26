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
 * Theme "Brand Harmony" — transcripción literal de los 53 `namedColors` de
 * `docs/design-tokens-m3.json` (fuente de verdad, generado por Stitch/M3).
 *
 * DECISIÓN tertiary vs TokensExtra.calor (documentada también en ARQUITECTURA.md):
 * el JSON trae DOS representaciones de "calor":
 *   1) `namedColors.tertiary` (#852307) y sus derivados (`on_tertiary`,
 *      `tertiary_container`, `on_tertiary_container`) — es el rol M3 generado
 *      por el algoritmo de esquema de color, con tonos oscuros pensados para
 *      contraste AA/AAA de texto/iconos sobre contenedores.
 *   2) `overrideTertiaryColor` / `designMd` (#f0704f) — el coral de marca
 *      "Calor" tal como lo define el brand (urgencia, borde 4px izquierdo,
 *      texto de alerta), que es el que Diseño usa visualmente en los
 *      prototipos.
 * Son colores DISTINTOS a propósito: `tertiary` (colorScheme) se usa en
 * componentes M3 que necesitan el rol completo con contraste garantizado
 * (p.ej. `tertiaryContainer` de un `Card`), mientras que `TokensExtra.calor`
 * es el coral plano (#f0704f) para usos directos de marca (borde de card
 * urgente, ícono, texto "Calor"). NO se alias-an entre sí — ver regla de oro
 * en ARQUITECTURA.md: "acción = teal (primary), calor/urgencia = coral
 * (TokensExtra.calor)".
 */
private val LeadAIColorScheme = lightColorScheme(
    primary = Color(0xFF005146),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF006B5D),
    onPrimaryContainer = Color(0xFF95E8D6),
    inversePrimary = Color(0xFF83D6C4),

    secondary = Color(0xFF565E74),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDAE2FD),
    onSecondaryContainer = Color(0xFF5C647A),

    tertiary = Color(0xFF852307),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFA63A1E),
    onTertiaryContainer = Color(0xFFFFCDC1),

    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF93000A),

    background = Color(0xFFF7F9FB),
    onBackground = Color(0xFF191C1E),

    surface = Color(0xFFF7F9FB),
    onSurface = Color(0xFF191C1E),
    surfaceVariant = Color(0xFFE0E3E5),
    onSurfaceVariant = Color(0xFF3E4946),

    outline = Color(0xFF6E7976),
    outlineVariant = Color(0xFFBEC9C5),

    inverseSurface = Color(0xFF2D3133),
    inverseOnSurface = Color(0xFFEFF1F3),

    surfaceTint = Color(0xFF006B5D),
    surfaceBright = Color(0xFFF7F9FB),
    surfaceDim = Color(0xFFD8DADC),
    surfaceContainer = Color(0xFFECEEF0),
    surfaceContainerHigh = Color(0xFFE6E8EA),
    surfaceContainerHighest = Color(0xFFE0E3E5),
    surfaceContainerLow = Color(0xFFF2F4F6),
    surfaceContainerLowest = Color(0xFFFFFFFF),
)

/**
 * Colores semánticos de marca sin slot en `ColorScheme` de Material 3
 * (`*_fixed`/`*_fixed_variant` de M3 tampoco tienen slot en Compose M3 y no
 * los usa ningún prototipo actual — se omiten a propósito, ver ARQUITECTURA.md).
 */
object TokensExtra {
    /** Coral de marca "Calor": urgencia, tardanza, borde 4px de card urgente. NO es tertiary del colorScheme — ver nota arriba. */
    val calor = Color(0xFFF0704F)

    /** Verde de éxito / pedido completado / pago confirmado. */
    val exito = Color(0xFF2E7D32)

    /** Ámbar de espera / pendiente. `waiting-amber` en el JSON. */
    val espera = Color(0xFFF5A623)

    /** Navy slate: bottom navigation y headers oscuros. Secondary override de marca (#0f172a), NO el `secondary` M3 (#565e74). */
    val slate = Color(0xFF0F172A)

    /** Teal presionado/hover del botón primario. `brasa-pressed` en el JSON. */
    val brasaPresionado = Color(0xFF00584C)

    /** Texto principal de marca (`tinta-primary`). Coincide con onSurface pero se expone explícito para no-Material contexts. */
    val tintaPrimaria = Color(0xFF191C1E)

    /** Texto secundario / placeholders (`tinta-secondary`). */
    val tintaSecundaria = Color(0xFF5B6770)

    /** Blanco puro de marca (`white`), para cards sobre `background` arena. */
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
        colorScheme = LeadAIColorScheme,
        typography = LeadAITypography,
        shapes = LeadAIShapes,
        content = content,
    )
}
