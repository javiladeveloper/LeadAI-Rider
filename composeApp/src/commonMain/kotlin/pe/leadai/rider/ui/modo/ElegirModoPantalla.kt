package pe.leadai.rider.ui.modo

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import pe.leadai.rider.ui.tema.ColoresJala

/**
 * "Elige tu camino" — sigue el diseño de Stitch.
 *
 * Dos cards grandes (mínimo 200dp de alto, radio 24) en vez de botones: cada
 * modo tiene su ícono en un cuadrado de color, su título y una línea que
 * explica qué gana el usuario si lo elige. Es la primera decisión que toma
 * alguien que abre la app, y merece más que dos botones apilados.
 */
@Composable
fun ElegirModoPantalla(
    alElegirCliente: () -> Unit,
    alElegirConductor: () -> Unit,
) {
    val colores = ColoresJala.actuales

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        // El logo: las flechas en amarillo y el nombre en carbón, como el
        // logotipo. Las chevrons SIEMPRE apuntan a la derecha (regla del
        // design system: significan avance).
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = colores.marcaAmarillo)) { append("»» ") }
                withStyle(SpanStyle(color = colores.marcaCarbon)) { append("Jala") }
            },
            style = MaterialTheme.typography.headlineLarge.copy(
                fontWeight = FontWeight.ExtraBold,
            ),
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )

        Spacer(Modifier.height(32.dp))

        CardModo(
            emoji = "🛵",
            titulo = "Pido un motorizado",
            descripcion = "Viajá rápido o enviá paquetes de forma segura por la ciudad.",
            colorFondo = MaterialTheme.colorScheme.surfaceContainerLowest,
            colorIcono = MaterialTheme.colorScheme.primary,
            colorTitulo = MaterialTheme.colorScheme.primary,
            onClick = alElegirCliente,
        )

        Spacer(Modifier.height(16.dp))

        // La card de conducir va en amarillo: es la que trae riders, y el
        // color de marca la hace la más visible de las dos.
        CardModo(
            emoji = "🏍️",
            titulo = "Manejo",
            descripcion = "Generá ingresos extra conduciendo en tu tiempo libre. 💸",
            colorFondo = MaterialTheme.colorScheme.secondaryContainer,
            colorIcono = colores.marcaCarbon,
            colorTitulo = MaterialTheme.colorScheme.onSecondaryContainer,
            onClick = alElegirConductor,
        )

        Spacer(Modifier.height(24.dp))

        // La advertencia va acá y no dentro de la card de "Manejo": ahí
        // competiría con el mensaje de "ganá plata", que es lo que tiene que
        // convencer primero.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("ℹ️", style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.size(6.dp))
            Text(
                "Manejar requiere validación de DNI y vehículo",
                style = MaterialTheme.typography.labelSmall,
                color = colores.tintaSecundaria,
            )
        }
    }
}

/**
 * Una de las dos opciones: ícono en un cuadrado de color arriba a la
 * izquierda, flecha a la derecha, y el texto abajo.
 *
 * El diseño le pone un cuarto de círculo difuminado en la esquina; se omite
 * porque en Compose exige `blur`, que no existe antes de Android 12, y no
 * aporta información.
 */
@Composable
private fun CardModo(
    emoji: String,
    titulo: String,
    descripcion: String,
    colorFondo: Color,
    colorIcono: Color,
    colorTitulo: Color,
    onClick: () -> Unit,
) {
    val colores = ColoresJala.actuales

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 190.dp)
            .background(colorFondo, RoundedCornerShape(24.dp))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                shape = RoundedCornerShape(24.dp),
            )
            .clickable { onClick() }
            .padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(colorIcono, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(emoji, style = MaterialTheme.typography.headlineMedium)
            }
            Text(
                "→",
                style = MaterialTheme.typography.headlineMedium,
                color = colores.tintaSecundaria,
            )
        }

        Spacer(Modifier.height(24.dp))

        Column {
            Text(
                titulo,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                ),
                color = colorTitulo,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                descripcion,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
