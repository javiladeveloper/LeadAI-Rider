package pe.leadai.rider.ui.comunes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pe.leadai.rider.ui.tema.ColoresJala

/**
 * El encabezado de una pantalla de cuenta: degradado de marca y la identidad
 * encima.
 *
 * La pantalla era una pila de tarjetas blancas sobre fondo gris —correcta pero
 * sin nada de la marca—: se veía apagada y genérica, como un formulario. El
 * degradado carbón→amarillo son LOS DOS colores del logo, así que la pantalla
 * se reconoce como Light Drive antes de leer una palabra.
 *
 * Ocupa la franja de arriba y nada más: el color entra donde se mira primero,
 * y el resto queda tranquilo para que se lea.
 */
@Composable
fun EncabezadoMarca(
    titulo: String,
    inicial: String,
    modifier: Modifier = Modifier,
    subtitulo: String? = null,
    /** El estado —"Activo", "Pendiente"— como pastilla sobre el degradado. */
    insignia: (@Composable () -> Unit)? = null,
) {
    val colores = ColoresJala.actuales
    Box(
        modifier = modifier
            .fillMaxWidth()
            // Solo abajo: arriba se funde con el borde de la pantalla, así el
            // color se lee como parte del marco y no como una tarjeta suelta.
            // Solo las esquinas de ABAJO: arriba tiene que llegar al borde de
            // la pantalla. Redondeando las cuatro quedaba una franja clara
            // sobre el degradado, detrás del reloj.
            .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
            .background(
                Brush.linearGradient(
                    listOf(colores.marcaCarbon, colores.marcaAmarillo),
                ),
            )
            // El color sube hasta el borde, pero el contenido baja: sin esto
            // el nombre quedaba pisado por el reloj y la señal.
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // La inicial sobre blanco translúcido: el amarillo del degradado ya
            // ocupa esa esquina y un círculo amarillo encima se perdería.
            // Amarillo de marca con la inicial en carbón, no blanco
            // translúcido: sobre el carbón del degradado el blanco al 22% se
            // ve gris sucio, y el amarillo ata el avatar al logo.
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .background(colores.marcaAmarillo, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    inicial,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                    ),
                    color = colores.marcaCarbon,
                )
            }
            Spacer(Modifier.size(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    titulo,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                    ),
                    color = Color.White,
                )
                if (!subtitulo.isNullOrBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        subtitulo,
                        style = MaterialTheme.typography.labelMedium,
                        // Blanco al 80%: el correo es un dato de apoyo y en
                        // blanco puro competía con el nombre.
                        color = Color.White.copy(alpha = 0.8f),
                    )
                }
                if (insignia != null) {
                    Spacer(Modifier.height(10.dp))
                    insignia()
                }
            }
        }
    }
}

/**
 * El encabezado de una pantalla SIN identidad: solo el título sobre el
 * degradado de marca.
 *
 * La pantalla de pedido arrancaba directo en las pestañas, sobre el gris del
 * fondo: funcionaba, pero podía ser el formulario de cualquier app. El mismo
 * degradado que el perfil le da continuidad —las dos se ven de la misma
 * familia— sin robarle sitio al formulario, que es lo que se viene a usar.
 */
@Composable
fun EncabezadoMarcaSimple(
    titulo: String,
    modifier: Modifier = Modifier,
    subtitulo: String? = null,
) {
    val colores = ColoresJala.actuales
    Box(
        modifier = modifier
            .fillMaxWidth()
            // Solo las esquinas de ABAJO: arriba tiene que llegar al borde de
            // la pantalla. Redondeando las cuatro quedaba una franja clara
            // sobre el degradado, detrás del reloj.
            .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
            .background(
                Brush.linearGradient(
                    listOf(colores.marcaCarbon, colores.marcaAmarillo),
                ),
            )
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 22.dp),
    ) {
        Column {
            Text(
                titulo,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                ),
                color = Color.White,
            )
            if (!subtitulo.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitulo,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.85f),
                )
            }
        }
    }
}

/**
 * El título de un grupo de opciones ("MI CUENTA", "APARIENCIA").
 *
 * Sin esto la pantalla era una sola lista larga donde "Cerrar sesión" pesaba lo
 * mismo que "Editar mi perfil". Agrupar da jerarquía y deja encontrar las cosas
 * sin leerlas todas.
 */
@Composable
fun TituloDeSeccion(texto: String, modifier: Modifier = Modifier) {
    Text(
        texto.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.Bold,
            // Espaciado extra: en mayúsculas y chico, las letras juntas se
            // leen como un bloque.
            letterSpacing = androidx.compose.ui.unit.TextUnit(
                1.2f,
                androidx.compose.ui.unit.TextUnitType.Sp,
            ),
        ),
        color = ColoresJala.actuales.tintaSecundaria,
        modifier = modifier.padding(start = 4.dp, bottom = 2.dp),
    )
}
