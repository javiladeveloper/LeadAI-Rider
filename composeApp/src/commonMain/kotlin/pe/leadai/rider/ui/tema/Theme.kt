package pe.leadai.rider.ui.tema

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import pe.leadai.rider.ui.tema.generado.ColoresTokens
import pe.leadai.rider.ui.tema.generado.FormaTokens
import pe.leadai.rider.ui.tema.generado.MarcaTokens

/**
 * Tema de Jala.
 *
 * TODOS los valores vienen de `design/jala-design-tokens.json`, que es la
 * fuente de verdad. Este archivo solo los arma en las estructuras que espera
 * Material 3 — no define ni un color.
 *
 * Para cambiar cualquier cosa visual: editar el JSON y recompilar. Si un
 * `Color(0x...)` aparece en un archivo que no sea el generado, está mal.
 */

private val EsquemaClaro = lightColorScheme(
    primary = ColoresTokens.Claro.primary,
    onPrimary = ColoresTokens.Claro.onPrimary,
    primaryContainer = ColoresTokens.Claro.primaryContainer,
    onPrimaryContainer = ColoresTokens.Claro.onPrimaryContainer,
    inversePrimary = ColoresTokens.Claro.inversePrimary,
    secondary = ColoresTokens.Claro.secondary,
    onSecondary = ColoresTokens.Claro.onSecondary,
    secondaryContainer = ColoresTokens.Claro.secondaryContainer,
    onSecondaryContainer = ColoresTokens.Claro.onSecondaryContainer,
    tertiary = ColoresTokens.Claro.tertiary,
    onTertiary = ColoresTokens.Claro.onTertiary,
    tertiaryContainer = ColoresTokens.Claro.tertiaryContainer,
    onTertiaryContainer = ColoresTokens.Claro.onTertiaryContainer,
    error = ColoresTokens.Claro.error,
    onError = ColoresTokens.Claro.onError,
    errorContainer = ColoresTokens.Claro.errorContainer,
    onErrorContainer = ColoresTokens.Claro.onErrorContainer,
    background = ColoresTokens.Claro.background,
    onBackground = ColoresTokens.Claro.onBackground,
    surface = ColoresTokens.Claro.surface,
    onSurface = ColoresTokens.Claro.onSurface,
    surfaceVariant = ColoresTokens.Claro.surfaceVariant,
    onSurfaceVariant = ColoresTokens.Claro.onSurfaceVariant,
    outline = ColoresTokens.Claro.outline,
    outlineVariant = ColoresTokens.Claro.outlineVariant,
    inverseSurface = ColoresTokens.Claro.inverseSurface,
    inverseOnSurface = ColoresTokens.Claro.inverseOnSurface,
    surfaceTint = ColoresTokens.Claro.surfaceTint,
    surfaceBright = ColoresTokens.Claro.surfaceBright,
    surfaceDim = ColoresTokens.Claro.surfaceDim,
    surfaceContainer = ColoresTokens.Claro.surfaceContainer,
    surfaceContainerHigh = ColoresTokens.Claro.surfaceContainerHigh,
    surfaceContainerHighest = ColoresTokens.Claro.surfaceContainerHighest,
    surfaceContainerLow = ColoresTokens.Claro.surfaceContainerLow,
    surfaceContainerLowest = ColoresTokens.Claro.surfaceContainerLowest,
)

private val EsquemaOscuro = darkColorScheme(
    primary = ColoresTokens.Oscuro.primary,
    onPrimary = ColoresTokens.Oscuro.onPrimary,
    primaryContainer = ColoresTokens.Oscuro.primaryContainer,
    onPrimaryContainer = ColoresTokens.Oscuro.onPrimaryContainer,
    inversePrimary = ColoresTokens.Oscuro.inversePrimary,
    secondary = ColoresTokens.Oscuro.secondary,
    onSecondary = ColoresTokens.Oscuro.onSecondary,
    secondaryContainer = ColoresTokens.Oscuro.secondaryContainer,
    onSecondaryContainer = ColoresTokens.Oscuro.onSecondaryContainer,
    tertiary = ColoresTokens.Oscuro.tertiary,
    onTertiary = ColoresTokens.Oscuro.onTertiary,
    tertiaryContainer = ColoresTokens.Oscuro.tertiaryContainer,
    onTertiaryContainer = ColoresTokens.Oscuro.onTertiaryContainer,
    error = ColoresTokens.Oscuro.error,
    onError = ColoresTokens.Oscuro.onError,
    errorContainer = ColoresTokens.Oscuro.errorContainer,
    onErrorContainer = ColoresTokens.Oscuro.onErrorContainer,
    background = ColoresTokens.Oscuro.background,
    onBackground = ColoresTokens.Oscuro.onBackground,
    surface = ColoresTokens.Oscuro.surface,
    onSurface = ColoresTokens.Oscuro.onSurface,
    surfaceVariant = ColoresTokens.Oscuro.surfaceVariant,
    onSurfaceVariant = ColoresTokens.Oscuro.onSurfaceVariant,
    outline = ColoresTokens.Oscuro.outline,
    outlineVariant = ColoresTokens.Oscuro.outlineVariant,
    inverseSurface = ColoresTokens.Oscuro.inverseSurface,
    inverseOnSurface = ColoresTokens.Oscuro.inverseOnSurface,
    surfaceTint = ColoresTokens.Oscuro.surfaceTint,
    surfaceBright = ColoresTokens.Oscuro.surfaceBright,
    surfaceDim = ColoresTokens.Oscuro.surfaceDim,
    surfaceContainer = ColoresTokens.Oscuro.surfaceContainer,
    surfaceContainerHigh = ColoresTokens.Oscuro.surfaceContainerHigh,
    surfaceContainerHighest = ColoresTokens.Oscuro.surfaceContainerHighest,
    surfaceContainerLow = ColoresTokens.Oscuro.surfaceContainerLow,
    surfaceContainerLowest = ColoresTokens.Oscuro.surfaceContainerLowest,
)

/**
 * Colores de marca que Material 3 no tiene dónde guardar.
 *
 * Se acceden por [ColoresJala.actuales] dentro de un `@Composable`, nunca como
 * constante global: cada uno tiene su versión clara y oscura, y una constante
 * dejaría el modo noche con los tonos del día.
 */
data class ColoresJala(
    /** El amarillo del logo. Ganancias, acentos. SIEMPRE con texto carbón encima. */
    val marcaAmarillo: Color,
    /** El carbón del logo. Fondos oscuros, headers. */
    val marcaCarbon: Color,
    /** Rojo de urgencia: sin saldo, algo que frena el trabajo. */
    val calor: Color,
    /** Verde de éxito: entregado, pago confirmado. */
    val exito: Color,
    /** Ámbar de atención: "llevás S/60 para la compra", "pendiente de verificación". */
    val espera: Color,
    /** Texto principal, para usos fuera de Material. */
    val tintaPrimaria: Color,
    /** Texto secundario y placeholders. */
    val tintaSecundaria: Color,
    /** Fondo de las cards elevadas. */
    val superficieCard: Color,
    /** Verde de la card de ganancias (del diseño: no es un rol de M3). */
    val gananciaFondo: Color,
    val gananciaBorde: Color,
    val gananciaTexto: Color,
) {
    companion object {
        /**
         * Los colores de marca del modo actual. Uso:
         * `val c = ColoresJala.actuales` dentro de un `@Composable`.
         */
        val actuales: ColoresJala
            @Composable @ReadOnlyComposable get() = LocalColoresJala.current
    }
}

private val MarcaClara = ColoresJala(
    marcaAmarillo = MarcaTokens.Claro.marcaAmarillo,
    marcaCarbon = MarcaTokens.Claro.marcaCarbon,
    calor = MarcaTokens.Claro.calor,
    exito = MarcaTokens.Claro.exito,
    espera = MarcaTokens.Claro.espera,
    tintaPrimaria = MarcaTokens.Claro.tintaPrimaria,
    tintaSecundaria = MarcaTokens.Claro.tintaSecundaria,
    superficieCard = MarcaTokens.Claro.superficieCard,
    gananciaFondo = MarcaTokens.Claro.gananciaFondo,
    gananciaBorde = MarcaTokens.Claro.gananciaBorde,
    gananciaTexto = MarcaTokens.Claro.gananciaTexto,
)

private val MarcaOscura = ColoresJala(
    marcaAmarillo = MarcaTokens.Oscuro.marcaAmarillo,
    marcaCarbon = MarcaTokens.Oscuro.marcaCarbon,
    calor = MarcaTokens.Oscuro.calor,
    exito = MarcaTokens.Oscuro.exito,
    espera = MarcaTokens.Oscuro.espera,
    tintaPrimaria = MarcaTokens.Oscuro.tintaPrimaria,
    tintaSecundaria = MarcaTokens.Oscuro.tintaSecundaria,
    superficieCard = MarcaTokens.Oscuro.superficieCard,
    gananciaFondo = MarcaTokens.Oscuro.gananciaFondo,
    gananciaBorde = MarcaTokens.Oscuro.gananciaBorde,
    gananciaTexto = MarcaTokens.Oscuro.gananciaTexto,
)

private val LocalColoresJala = staticCompositionLocalOf { MarcaClara }

/** Radios de esquina, del JSON. */
val JalaShapes = Shapes(
    extraSmall = RoundedCornerShape(FormaTokens.extraSmall),
    small = RoundedCornerShape(FormaTokens.small),
    medium = RoundedCornerShape(FormaTokens.medium),
    large = RoundedCornerShape(FormaTokens.large),
    extraLarge = RoundedCornerShape(FormaTokens.extraLarge),
)

/** Forma pill para chips y badges de estado. */
val FormaChip = RoundedCornerShape(CornerSize(50))

/**
 * Tema raíz de la app.
 *
 * [oscuro] sigue al sistema por defecto. Se puede forzar para previews o para
 * un toggle manual dentro de la app, si algún día se agrega.
 */
@Composable
fun JalaTheme(
    oscuro: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalColoresJala provides if (oscuro) MarcaOscura else MarcaClara,
    ) {
        MaterialTheme(
            colorScheme = if (oscuro) EsquemaOscuro else EsquemaClaro,
            typography = LeadAITypography,
            shapes = JalaShapes,
            content = content,
        )
    }
}

/**
 * Alias del nombre viejo, para no romper lo que ya lo usa. La app se llama
 * Jala; `LeadAITheme` queda solo como puente y se irá al migrar las pantallas.
 */
@Deprecated("Usá JalaTheme", ReplaceWith("JalaTheme(content = content)"))
@Composable
fun LeadAITheme(content: @Composable () -> Unit) = JalaTheme(content = content)
