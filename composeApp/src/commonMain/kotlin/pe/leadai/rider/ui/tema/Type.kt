package pe.leadai.rider.ui.tema

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import leadairider.composeapp.generated.resources.Res
import leadairider.composeapp.generated.resources.plus_jakarta_sans_bold
import leadairider.composeapp.generated.resources.plus_jakarta_sans_extrabold
import leadairider.composeapp.generated.resources.plus_jakarta_sans_medium
import leadairider.composeapp.generated.resources.plus_jakarta_sans_regular
import leadairider.composeapp.generated.resources.plus_jakarta_sans_semibold
import org.jetbrains.compose.resources.Font

/**
 * Fuente única de marca (Brand Harmony): Plus Jakarta Sans, licencia OFL
 * (`composeResources/font/OFL.txt`). 5 pesos (Regular/Medium/SemiBold/Bold/
 * ExtraBold) cubren toda la escala M3; dinero y títulos usan Bold/ExtraBold
 * por regla de diseño. El TTF de SemiBold (Task B7, ítem b) viene del MISMO
 * repo oficial que los otros 4 (`github.com/tokotype/PlusJakartaSans`,
 * `fonts/ttf/PlusJakartaSans-{Peso}.ttf`) — se verificó el hash SHA-256 del
 * `PlusJakartaSans-Bold.ttf` de ese repo contra el archivo que ya vivía en
 * este proyecto: idéntico byte a byte, confirmando que es la fuente real
 * (antes de esta task, `headlineMedium`/`headlineSmall` pedían
 * `FontWeight.SemiBold` pero como `FontFamily` no tenía ningún `Font`
 * registrado en ese peso, Compose caía al peso más cercano disponible —
 * Bold — en vez de dibujar un SemiBold real).
 */
@Composable
fun plusJakartaSans(): FontFamily = FontFamily(
    Font(Res.font.plus_jakarta_sans_regular, weight = FontWeight.Normal),
    Font(Res.font.plus_jakarta_sans_medium, weight = FontWeight.Medium),
    Font(Res.font.plus_jakarta_sans_semibold, weight = FontWeight.SemiBold),
    Font(Res.font.plus_jakarta_sans_bold, weight = FontWeight.Bold),
    Font(Res.font.plus_jakarta_sans_extrabold, weight = FontWeight.ExtraBold),
)

/**
 * Typography M3 completa mapeada a la escala tipográfica de
 * `docs/design-tokens-m3.json` (display-lg/md, headline-lg/md, body-lg/md,
 * label-lg-bold, label-md). Los niveles M3 sin equivalente explícito en el
 * JSON (displaySmall, headlineSmall, titleMedium/Small, bodySmall,
 * labelSmall) se interpolan manteniendo la familia, el tracking editorial en
 * headlines y Bold reservado a dinero/títulos.
 */
val LeadAITypography: Typography
    @Composable
    get() {
        val familia = plusJakartaSans()
        return Typography(
            displayLarge = TextStyle(
                fontFamily = familia,
                fontWeight = FontWeight.Bold,
                fontSize = 32.sp,
                lineHeight = 40.sp,
                letterSpacing = (-0.02).sp,
            ),
            displayMedium = TextStyle(
                fontFamily = familia,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
                lineHeight = 36.sp,
            ),
            displaySmall = TextStyle(
                fontFamily = familia,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                lineHeight = 32.sp,
            ),
            headlineLarge = TextStyle(
                fontFamily = familia,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                lineHeight = 28.sp,
            ),
            headlineMedium = TextStyle(
                fontFamily = familia,
                fontWeight = FontWeight.SemiBold,
                fontSize = 20.sp,
                lineHeight = 26.sp,
            ),
            headlineSmall = TextStyle(
                fontFamily = familia,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
                lineHeight = 24.sp,
            ),
            titleLarge = TextStyle(
                fontFamily = familia,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                lineHeight = 28.sp,
            ),
            titleMedium = TextStyle(
                fontFamily = familia,
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
                lineHeight = 24.sp,
            ),
            titleSmall = TextStyle(
                fontFamily = familia,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            ),
            bodyLarge = TextStyle(
                fontFamily = familia,
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp,
                lineHeight = 24.sp,
            ),
            bodyMedium = TextStyle(
                fontFamily = familia,
                fontWeight = FontWeight.Normal,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            ),
            bodySmall = TextStyle(
                fontFamily = familia,
                fontWeight = FontWeight.Normal,
                fontSize = 12.sp,
                lineHeight = 16.sp,
            ),
            labelLarge = TextStyle(
                fontFamily = familia,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                lineHeight = 20.sp,
            ),
            labelMedium = TextStyle(
                fontFamily = familia,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                lineHeight = 16.sp,
            ),
            labelSmall = TextStyle(
                fontFamily = familia,
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
                lineHeight = 14.sp,
            ),
        )
    }
