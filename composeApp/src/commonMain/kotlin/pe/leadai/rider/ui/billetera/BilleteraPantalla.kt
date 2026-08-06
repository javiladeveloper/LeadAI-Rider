package pe.leadai.rider.ui.billetera

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import pe.leadai.rider.datos.MonederoDto
import pe.leadai.rider.datos.MovimientoMonederoDto
import pe.leadai.rider.datos.PaqueteMonederoDto
import pe.leadai.rider.ui.comunes.BotonAcento
import pe.leadai.rider.ui.comunes.CardJala
import pe.leadai.rider.ui.tema.ColoresJala
import pe.leadai.rider.ui.tema.centavosASoles

/**
 * La billetera del rider: cuánto tiene, cómo recargar, y en qué se le fue.
 *
 * El saldo va en una card amarilla a sangre, tamaño display: es el número que
 * decide si puede trabajar o no, así que es lo primero y lo más grande.
 */
@Composable
fun BilleteraPantalla(
    monedero: MonederoDto?,
    onRecargar: () -> Unit,
    onElegirPaquete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colores = ColoresJala.actuales

    if (monedero == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Cargando tu billetera…", color = colores.tintaSecundaria)
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "titulo") {
            Spacer(Modifier.height(8.dp))
            Text(
                "Mi billetera",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                ),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        item(key = "saldo") {
            CardJala(
                modifier = Modifier.fillMaxWidth(),
                color = colores.marcaAmarillo,
                paddingInterno = 24,
            ) {
                Text(
                    "Saldo actual",
                    style = MaterialTheme.typography.labelSmall,
                    // Sobre el amarillo, SIEMPRE texto carbón.
                    color = colores.marcaCarbon.copy(alpha = 0.7f),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    centavosASoles(monedero.saldoCentavos),
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                    ),
                    color = colores.marcaCarbon,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
                if (monedero.carrerasDisponibles > 0) {
                    Text(
                        "Te alcanza para ${monedero.carrerasDisponibles} carreras",
                        style = MaterialTheme.typography.labelSmall,
                        color = colores.marcaCarbon.copy(alpha = 0.7f),
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        item(key = "recargar") {
            BotonAcento(texto = "⊕ Recargar saldo", onClick = onRecargar)
        }

        // Atajos: tocar un monto lleva directo al pago, sin pasar por el
        // diálogo de elegir paquete.
        val paquetes = monedero.paquetes
        if (paquetes.isNotEmpty()) {
            item(key = "titulo-paquetes") {
                Text(
                    "Paquetes rápidos",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            item(key = "paquetes") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(paquetes, key = { it.id }) { paquete ->
                        ChipPaquete(paquete = paquete, onClick = { onElegirPaquete(paquete.id) })
                    }
                }
            }
        }

        val movimientos = monedero.movimientos
        if (movimientos.isNotEmpty()) {
            item(key = "titulo-movimientos") {
                Text(
                    "Movimientos recientes",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            items(movimientos, key = { it.creadoEn ?: it.concepto }) { movimiento ->
                FilaMovimiento(movimiento)
            }
        } else {
            item(key = "sin-movimientos") {
                Text(
                    "Todavía no hay movimientos.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colores.tintaSecundaria,
                )
            }
        }

        item(key = "fin") { Spacer(Modifier.height(16.dp)) }
    }
}

/** "S/20" tocable: atajo directo al pago de ese paquete. */
@Composable
private fun ChipPaquete(paquete: PaqueteMonederoDto, onClick: () -> Unit) {
    val colores = ColoresJala.actuales
    Box(
        modifier = Modifier
            .background(colores.superficieCard, MaterialTheme.shapes.medium)
            .clickable { onClick() }
            .padding(horizontal = 28.dp, vertical = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "S/${paquete.soles}",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * Un movimiento: recarga (+, verde) o cobro de carrera (−).
 *
 * El signo y el color hacen el trabajo: el rider entiende de un vistazo si
 * esa línea le sumó o le restó, sin leer el concepto.
 */
@Composable
private fun FilaMovimiento(movimiento: MovimientoMonederoDto) {
    val colores = ColoresJala.actuales
    val esIngreso = movimiento.montoCentavos > 0

    CardJala(modifier = Modifier.fillMaxWidth(), paddingInterno = 14) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        color = if (esIngreso) {
                            colores.exito.copy(alpha = 0.15f)
                        } else {
                            colores.tintaSecundaria.copy(alpha = 0.12f)
                        },
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(if (esIngreso) "↓" else "🛵", style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    movimiento.concepto.ifBlank {
                        if (esIngreso) "Recarga" else "Comisión de carrera"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                (if (esIngreso) "+" else "−") +
                    centavosASoles(kotlin.math.abs(movimiento.montoCentavos)),
                style = MaterialTheme.typography.labelLarge,
                color = if (esIngreso) colores.exito else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
