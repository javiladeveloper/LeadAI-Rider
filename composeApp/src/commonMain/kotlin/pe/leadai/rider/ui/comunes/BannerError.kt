package pe.leadai.rider.ui.comunes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import pe.leadai.rider.ui.tema.Formas
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Banner de error inline para formularios/acciones dentro de una pantalla
 * que sigue siendo usable (a diferencia de [EstadoError], que reemplaza el
 * contenido completo con un CTA de reintento). Extraído del
 * `BannerErrorNegocio` privado de `CrearNegocioPantalla` cuando
 * `NegocioSinRestaurantePantalla` necesitó el mismo look (2026-07-22);
 * el mismo día se consolidaron acá las 6 copias privadas idénticas que
 * habían crecido por pantalla (Login, Registro, Cocina, Reservas, Carta,
 * Ajustes) — deuda del ledger de Fase B.
 */
@Composable
fun BannerError(mensaje: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = Formas.chip,
            )
            .padding(12.dp),
    ) {
        Text(
            text = mensaje,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}
