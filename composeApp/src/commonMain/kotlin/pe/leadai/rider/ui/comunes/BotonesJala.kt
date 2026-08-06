package pe.leadai.rider.ui.comunes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import pe.leadai.rider.ui.tema.ColoresJala

/**
 * Botones de Jala.
 *
 * El DESIGN.md pide 56dp de alto en las acciones principales: el rider las
 * toca con guantes, en la calle, muchas veces con la moto en marcha. Ese alto
 * NO es negociable por estética.
 *
 * Tres variantes, cada una con su significado:
 * - [BotonPrincipal] — carbón. La acción central de la pantalla.
 * - [BotonAcento] — amarillo de marca, texto carbón. Plata y recompensa.
 * - [BotonCritico] — rojo. Lo que frena el trabajo (sin saldo, cancelar).
 */

/** Alto de las acciones que se tocan manejando. */
private val ALTO_ACCION = 56.dp

@Composable
fun BotonPrincipal(
    texto: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    habilitado: Boolean = true,
    cargando: Boolean = false,
) {
    BotonBase(
        texto = texto,
        onClick = onClick,
        modifier = modifier,
        habilitado = habilitado,
        cargando = cargando,
        fondo = MaterialTheme.colorScheme.primary,
        contenido = MaterialTheme.colorScheme.onPrimary,
    )
}

/**
 * El botón amarillo: ganancias, recargar, aceptar una carrera.
 *
 * El texto va en CARBÓN, nunca blanco — sobre el amarillo de marca el blanco
 * da ~1.9:1 de contraste y a pleno sol desaparece.
 */
@Composable
fun BotonAcento(
    texto: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    habilitado: Boolean = true,
    cargando: Boolean = false,
) {
    val colores = ColoresJala.actuales
    BotonBase(
        texto = texto,
        onClick = onClick,
        modifier = modifier,
        habilitado = habilitado,
        cargando = cargando,
        fondo = colores.marcaAmarillo,
        contenido = colores.marcaCarbon,
    )
}

@Composable
fun BotonCritico(
    texto: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    habilitado: Boolean = true,
    cargando: Boolean = false,
) {
    BotonBase(
        texto = texto,
        onClick = onClick,
        modifier = modifier,
        habilitado = habilitado,
        cargando = cargando,
        fondo = ColoresJala.actuales.calor,
        contenido = Color.White,
    )
}

@Composable
private fun BotonBase(
    texto: String,
    onClick: () -> Unit,
    modifier: Modifier,
    habilitado: Boolean,
    cargando: Boolean,
    fondo: Color,
    contenido: Color,
) {
    Button(
        onClick = onClick,
        // Mientras carga NO se puede volver a tocar: dos toques rápidos en
        // "Aceptar" mandarían dos peticiones y la segunda daría 409.
        enabled = habilitado && !cargando,
        modifier = modifier.fillMaxWidth().height(ALTO_ACCION),
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(
            containerColor = fondo,
            contentColor = contenido,
        ),
    ) {
        // El spinner reemplaza al texto pero el botón NO cambia de tamaño:
        // que la pantalla salte mientras se procesa se siente roto.
        Box(contentAlignment = Alignment.Center) {
            if (cargando) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = contenido,
                    strokeWidth = 2.dp,
                )
            } else {
                Text(texto, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/**
 * Pill de estado: "Verificado", "Completado", "Pendiente".
 *
 * El fondo va al 14% de opacidad y el texto en el color pleno — así se lee
 * bien sin gritar, que es lo que hace un badge junto a información más
 * importante.
 */
@Composable
fun BadgeEstado(
    texto: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .background(
                color = color.copy(alpha = 0.14f),
                shape = MaterialTheme.shapes.small,
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(texto, style = MaterialTheme.typography.labelSmall, color = color)
    }
}
