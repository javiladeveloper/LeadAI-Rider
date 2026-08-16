package pe.leadai.rider.ui.comunes

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import pe.leadai.rider.ui.tema.Elevacion
import pe.leadai.rider.ui.tema.Formas
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import pe.leadai.rider.ui.tema.ColoresJala

/**
 * La card estándar de Jala: blanca, 16dp de radio, sombra suave.
 *
 * Existe para que las ~20 cards de la app no repitan la misma configuración
 * de `CardDefaults` cada vez. Cuando el diseño cambie el radio o la sombra,
 * se toca acá y no en veinte lugares.
 *
 * `padding` interno de 16dp por defecto (lo que pide el DESIGN.md); se puede
 * poner en 0 para contenido a sangre, como un mapa.
 */
@Composable
fun CardJala(
    modifier: Modifier = Modifier,
    color: Color = ColoresJala.actuales.superficieCard,
    paddingInterno: Int = 16,
    contenido: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier,
        shape = Formas.card,
        colors = CardDefaults.cardColors(containerColor = color),
        // 2dp y no 1: con un solo dp la card no se despega del fondo y la
        // pantalla se lee como una lista plana de texto. Poca elevación pero
        // CONSISTENTE es lo que separa una app cuidada de una cargada.
        elevation = CardDefaults.cardElevation(defaultElevation = Elevacion.card),
    ) {
        Column(
            modifier = Modifier.padding(paddingInterno.dp),
            content = contenido,
        )
    }
}
