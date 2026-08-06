package pe.leadai.rider.ui.comunes

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import pe.leadai.rider.ui.tema.ColoresJala

/**
 * Las secciones del modo conductor.
 *
 * Antes todo vivía apilado en una sola pantalla —feed, monedero, historial,
 * perfil—, así que había que scrollear mucho para llegar a lo de abajo. Con
 * pestañas, cada cosa está a un toque.
 */
enum class SeccionRider(val etiqueta: String, val icono: String) {
    INICIO("Inicio", "🏠"),
    GANANCIAS("Ganancias", "💸"),
    BILLETERA("Billetera", "💳"),
    PERFIL("Perfil", "👤"),
}

/**
 * Barra de navegación del modo conductor.
 *
 * Íconos con emoji a propósito: se leen rápido, no necesitan la dependencia
 * de material-icons-extended, y encajan con el tono de la app.
 */
@Composable
fun BarraInferiorRider(
    seleccionada: SeccionRider,
    onSeleccionar: (SeccionRider) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colores = ColoresJala.actuales
    NavigationBar(
        modifier = modifier,
        containerColor = colores.superficieCard,
    ) {
        SeccionRider.entries.forEach { seccion ->
            NavigationBarItem(
                selected = seleccionada == seccion,
                onClick = { onSeleccionar(seccion) },
                icon = { Text(seccion.icono, style = MaterialTheme.typography.titleMedium) },
                label = { Text(seccion.etiqueta, style = MaterialTheme.typography.labelSmall) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = colores.marcaCarbon,
                    selectedTextColor = colores.marcaCarbon,
                    // El indicador en amarillo de marca: es la única pista de
                    // color en la barra y marca dónde está parado.
                    indicatorColor = colores.marcaAmarillo,
                    unselectedIconColor = colores.tintaSecundaria,
                    unselectedTextColor = colores.tintaSecundaria,
                ),
            )
        }
    }
}
