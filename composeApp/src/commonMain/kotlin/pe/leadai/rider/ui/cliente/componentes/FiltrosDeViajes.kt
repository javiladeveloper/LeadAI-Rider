package pe.leadai.rider.ui.cliente.componentes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import pe.leadai.rider.datos.CarreraClienteDto
import pe.leadai.rider.ui.tema.ColoresJala
import pe.leadai.rider.ui.tema.Formas

/**
 * Cómo se filtra el historial de viajes.
 *
 * La lista mezclaba los viajes que se hicieron con los que nadie tomó y los
 * cancelados, todos con la misma cara. Quien entra acá casi siempre viene a
 * ver lo que SÍ pasó —para un reclamo, o para acordarse de cuánto pagó— y eso
 * quedaba enterrado entre pedidos que nunca existieron.
 */
enum class FiltroViajes(val etiqueta: String) {
    TODOS("Todos"),

    /**
     * Los que llegaron a destino.
     *
     * Es el filtro que la gente busca: "¿cuánto pagué el martes?". Un pedido
     * que nadie tomó no es un viaje.
     */
    COMPLETADOS("Completados"),

    /**
     * Los que no terminaron: cancelados y los que nadie tomó a tiempo.
     *
     * Van juntos a propósito. Para el cliente son la misma categoría —"esto
     * no pasó"— y separarlos en dos pestañas obligaría a mirar en dos lugares
     * para reclamar por algo que salió mal.
     */
    SIN_CONCRETAR("Sin concretar");

    /** Si esta carrera entra en el filtro. */
    fun incluye(carrera: CarreraClienteDto): Boolean = when (this) {
        TODOS -> true
        COMPLETADOS -> carrera.estado == "entregada"
        SIN_CONCRETAR -> carrera.estado != "entregada"
    }
}

/**
 * La fila de filtros, con el CONTEO al lado de cada uno.
 *
 * El número es lo que hace útil el filtro antes de tocarlo: "Sin concretar 4"
 * dice algo —que hubo cuatro pedidos que no salieron— que la etiqueta sola no
 * dice.
 */
@Composable
fun FiltrosDeViajes(
    seleccionado: FiltroViajes,
    conteos: Map<FiltroViajes, Int>,
    onElegir: (FiltroViajes) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(FiltroViajes.entries) { filtro ->
            ChipFiltro(
                texto = filtro.etiqueta,
                cuantos = conteos[filtro] ?: 0,
                elegido = filtro == seleccionado,
                onClick = { onElegir(filtro) },
            )
        }
    }
}

/** Un filtro. El elegido va con el ámbar de la marca. */
@Composable
private fun ChipFiltro(
    texto: String,
    cuantos: Int,
    elegido: Boolean,
    onClick: () -> Unit,
) {
    val colores = ColoresJala.actuales
    Row(
        modifier = Modifier
            .clip(Formas.chip)
            .background(
                if (elegido) colores.marcaAmarillo.copy(alpha = 0.18f)
                else MaterialTheme.colorScheme.surfaceContainerHigh,
            )
            .border(
                width = if (elegido) 2.dp else 1.dp,
                color = if (elegido) colores.marcaAmarillo
                else MaterialTheme.colorScheme.outlineVariant,
                shape = Formas.chip,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            texto,
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = if (elegido) FontWeight.Bold else FontWeight.Normal,
            ),
            color = if (elegido) MaterialTheme.colorScheme.onSurface
            else colores.tintaSecundaria,
        )
        // El conteo, más apagado: acompaña a la etiqueta, no compite con ella.
        Text(
            "$cuantos",
            style = MaterialTheme.typography.labelMedium,
            color = colores.tintaSecundaria,
        )
    }
}

/**
 * Cuando el filtro elegido no tiene nada.
 *
 * Distinto del vacío general ("todavía no pediste ninguna"): acá SÍ hay
 * viajes, solo que ninguno de este tipo. Decir lo mismo en los dos casos hace
 * dudar de si la app perdió el historial.
 */
@Composable
fun SinViajesDelFiltro(filtro: FiltroViajes, modifier: Modifier = Modifier) {
    val colores = ColoresJala.actuales
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            when (filtro) {
                FiltroViajes.COMPLETADOS -> "🛵"
                else -> "✅"
            },
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            when (filtro) {
                FiltroViajes.COMPLETADOS -> "Todavía no completaste ninguno"
                // Que no haya nada acá es una BUENA noticia, y así se dice.
                else -> "Ninguno quedó sin concretar"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = colores.tintaSecundaria,
            textAlign = TextAlign.Center,
        )
    }
}
