package pe.leadai.rider.ui.billetera

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import pe.leadai.rider.ui.comunes.CardJala
import pe.leadai.rider.ui.tema.ColoresJala
import pe.leadai.rider.ui.tema.centavosASoles

/**
 * Mi Billetera — sigue el diseño de Stitch (pantalla "Mi Billetera").
 *
 * Estructura del diseño: card de saldo centrada en `primary-container` con el
 * monto en `display-lg`, dos botones apilados (recargar / retirar), chips de
 * paquetes rápidos en scroll horizontal, y la lista de movimientos con ícono
 * circular, concepto, fecha y monto con signo.
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
        modifier = modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "titulo") {
            Spacer(Modifier.height(8.dp))
            Text(
                "Mi Billetera",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                ),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        }

        item(key = "saldo") {
            CardSaldo(monedero = monedero)
        }

        item(key = "acciones") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                BotonRecargar(onClick = onRecargar)
                // "Retirar ganancias" está en el diseño pero el backend no
                // tiene ese flujo todavía: el monedero solo acredita saldo
                // para comisiones, no devuelve plata. Se muestra desactivado
                // en vez de esconderlo, para que el rider sepa que existe.
                BotonRetirar()
            }
        }

        val paquetes = monedero.paquetes
        if (paquetes.isNotEmpty()) {
            item(key = "titulo-paquetes") {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Paquetes Rápidos",
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

        item(key = "titulo-movimientos") {
            Spacer(Modifier.height(8.dp))
            Text(
                "Movimientos Recientes",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        val movimientos = monedero.movimientos
        if (movimientos.isEmpty()) {
            item(key = "sin-movimientos") {
                Text(
                    "Todavía no hay movimientos.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colores.tintaSecundaria,
                )
            }
        } else {
            items(movimientos, key = { "${it.creadoEn}-${it.concepto}" }) { movimiento ->
                FilaMovimiento(movimiento)
            }
        }

        item(key = "fin") { Spacer(Modifier.height(16.dp)) }
    }
}

/**
 * El saldo, centrado, sobre `primaryContainer` (el carbón claro del diseño).
 *
 * El diseño le pone un círculo difuminado de adorno arriba a la derecha; acá
 * se omite: en Compose exige un `blur` que en Android 11 y anteriores no está
 * disponible, y el adorno no aporta información.
 */
@Composable
private fun CardSaldo(monedero: MonederoDto) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 140.dp)
            .background(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(24.dp),
            )
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "Saldo Actual",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            centavosASoles(monedero.saldoCentavos),
            style = MaterialTheme.typography.displayLarge.copy(
                fontWeight = FontWeight.ExtraBold,
            ),
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        if (monedero.carrerasDisponibles > 0) {
            Spacer(Modifier.height(4.dp))
            Text(
                "Te alcanza para ~${monedero.carrerasDisponibles} carreras",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun BotonRecargar(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(16.dp),
            )
            .clickable { onClick() },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("⊕", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
        Spacer(Modifier.size(8.dp))
        Text(
            "Recargar Saldo",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

/**
 * "Retirar Ganancias" — en el diseño, con borde y fondo neutro.
 *
 * Desactivado a propósito: el monedero de hoy es PREPAGO (se carga saldo para
 * pagar comisiones), no una cuenta de la que se pueda sacar plata. Lo que el
 * rider gana lo cobra en efectivo del cliente. Mostrarlo apagado es más
 * honesto que esconderlo: el diseño lo prevé y algún día va a existir.
 */
@Composable
private fun BotonRetirar() {
    val colores = ColoresJala.actuales
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(16.dp),
            )
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(16.dp),
            ),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("💵", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.size(8.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "Retirar Ganancias",
                style = MaterialTheme.typography.bodyLarge,
                color = colores.tintaSecundaria,
            )
            Text(
                "Próximamente",
                style = MaterialTheme.typography.labelSmall,
                color = colores.tintaSecundaria,
            )
        }
    }
}

/** "S/20" tocable: atajo directo al pago de ese paquete. */
@Composable
private fun ChipPaquete(paquete: PaqueteMonederoDto, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                shape = RoundedCornerShape(16.dp),
            )
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(16.dp),
            )
            .clickable { onClick() }
            .padding(horizontal = 28.dp, vertical = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "S/ ${paquete.soles}",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * Un movimiento: ícono circular, concepto, cuándo fue, y el monto con signo.
 *
 * El signo y el color hacen el trabajo: el rider entiende de un vistazo si esa
 * línea le sumó o le restó, sin leer el concepto.
 */
@Composable
private fun FilaMovimiento(movimiento: MovimientoMonederoDto) {
    val colores = ColoresJala.actuales
    val esIngreso = movimiento.montoCentavos > 0

    CardJala(modifier = Modifier.fillMaxWidth(), paddingInterno = 14) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
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
                Text(
                    if (esIngreso) "↓" else iconoDelConcepto(movimiento.concepto),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (esIngreso) colores.exito else colores.tintaSecundaria,
                )
            }
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    movimiento.concepto.ifBlank {
                        if (esIngreso) "Recarga exitosa" else "Comisión de carrera"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                val cuando = movimiento.creadoEn?.let { fechaLegible(it) }
                if (cuando != null) {
                    Text(
                        cuando,
                        style = MaterialTheme.typography.labelSmall,
                        color = colores.tintaSecundaria,
                    )
                }
            }
            Text(
                (if (esIngreso) "+" else "−") +
                    centavosASoles(kotlin.math.abs(movimiento.montoCentavos)),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = if (esIngreso) colores.exito else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** El ícono según de qué fue el cobro: taxi, paquete o genérico. */
private fun iconoDelConcepto(concepto: String): String = when {
    concepto.contains("pasajero", ignoreCase = true) -> "🚕"
    concepto.contains("encomienda", ignoreCase = true) -> "📦"
    else -> "🛵"
}

/**
 * "2026-08-06T15:30:00Z" → "6 ago, 15:30".
 *
 * Se formatea a mano en vez de traer kotlinx-datetime: son dos campos de una
 * fecha ISO, y la dependencia pesa más que estas líneas.
 */
internal fun fechaLegible(iso: String): String? {
    if (iso.length < 16) return null
    val meses = listOf("ene", "feb", "mar", "abr", "may", "jun",
                       "jul", "ago", "sep", "oct", "nov", "dic")
    val mes = iso.substring(5, 7).toIntOrNull()?.minus(1) ?: return null
    if (mes !in meses.indices) return null
    val dia = iso.substring(8, 10).trimStart('0')
    val hora = iso.substring(11, 16)
    return "$dia ${meses[mes]}, $hora"
}
