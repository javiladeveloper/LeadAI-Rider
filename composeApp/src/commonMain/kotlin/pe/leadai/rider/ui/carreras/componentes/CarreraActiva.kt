package pe.leadai.rider.ui.carreras.componentes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pe.leadai.rider.datos.CarreraDto
import pe.leadai.rider.ui.carreras.requiereCompra
import pe.leadai.rider.ui.tema.ColoresJala
import pe.leadai.rider.ui.tema.centavosASoles

/**
 * La hoja inferior de la carrera activa — sigue el diseño de Stitch
 * ("En Camino").
 *
 * El mapa ocupa casi toda la pantalla y esto flota abajo: estado del tramo,
 * quién es el cliente con sus botones de contacto, y la acción grande.
 */
@Composable
fun HojaCarreraActiva(
    carrera: CarreraDto,
    accionEnCurso: Boolean,
    onRecogido: () -> Unit,
    onEntregar: () -> Unit,
    onWhatsApp: (String) -> Unit,
    onLlamar: (String) -> Unit,
    telefonoCliente: String?,
    modifier: Modifier = Modifier,
) {
    val colores = ColoresJala.actuales

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = colores.superficieCard,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            )
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // El "agarre" de la hoja: indica que esto es un panel, no el fondo.
        Box(
            modifier = Modifier
                .size(width = 40.dp, height = 4.dp)
                .background(colores.tintaSecundaria.copy(alpha = 0.3f), CircleShape)
                .align(Alignment.CenterHorizontally),
        )

        // En qué tramo va, con un punto ámbar al lado.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(colores.espera, CircleShape),
            )
            Spacer(Modifier.size(8.dp))
            Text(
                if (carrera.recogido) "En camino al destino" else "Recogiendo",
                style = MaterialTheme.typography.labelLarge,
                color = colores.tintaSecundaria,
            )
        }

        // Quién es y a dónde, con los botones de contacto al costado.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("👤", style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.size(12.dp))
                Column {
                    Text(
                        carrera.clienteNombre?.takeIf { it.isNotBlank() } ?: "Tu cliente",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                        ),
                        color = MaterialTheme.colorScheme.primary,
                    )
                    val donde = if (carrera.recogido) {
                        carrera.destinoTexto ?: carrera.direccion
                    } else {
                        carrera.origenTexto ?: carrera.negocio
                    }
                    if (!donde.isNullOrBlank()) {
                        Text(
                            donde,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Solo si el contacto es un teléfono marcable — los leads de
            // prueba usan ids, y un botón de llamar ahí no haría nada.
            if (telefonoCliente != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BotonRedondo("💬") { onWhatsApp(telefonoCliente) }
                    BotonRedondo("📞") { onLlamar(telefonoCliente) }
                }
            }
        }

        // El adelanto de compra, si lo hay: tiene que verse mientras maneja,
        // no solo antes de aceptar.
        if (requiereCompra(carrera)) {
            AvisoPlataParaCompra(montoCentavos = carrera.montoCompraEstimado ?: 0)
        }

        // La acción: texto a la izquierda, chevrons a la derecha en amarillo.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(16.dp),
                )
                .clickable(enabled = !accionEnCurso) {
                    if (carrera.recogido) onEntregar() else onRecogido()
                }
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                textoDeLaAccion(carrera, accionEnCurso),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimary,
            )
            Text(
                "»",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                ),
                color = colores.marcaAmarillo,
            )
        }
    }
}

/**
 * Qué dice el botón según el tramo y el tipo.
 *
 * A un pasajero no se lo "recoge": sube. Y en una encomienda con compra, lo
 * que confirma el rider es que ya compró.
 */
private fun textoDeLaAccion(carrera: CarreraDto, enCurso: Boolean): String = when {
    enCurso -> "UN MOMENTO…"
    carrera.recogido -> "ENTREGADO"
    carrera.tipo == "pasajero" -> "YA SUBIÓ"
    requiereCompraSinCompose(carrera) -> "YA COMPRÉ"
    else -> "YA RECOGÍ"
}

/** Versión no-Composable de `requiereCompra`, para usar en lógica pura. */
private fun requiereCompraSinCompose(carrera: CarreraDto): Boolean =
    (carrera.montoCompraEstimado ?: 0) > 0

/** Botón circular de 40dp con borde, para WhatsApp y llamar. */
@Composable
private fun BotonRedondo(emoji: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = CircleShape,
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(emoji, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * El monto de la carrera, flotando sobre el mapa arriba a la derecha.
 *
 * Va en amarillo con texto carbón: es lo que el rider gana, y tiene que
 * leerse de un vistazo sin sacar la vista del camino.
 */
@Composable
fun ChipMontoSobreMapa(montoCentavos: Long, modifier: Modifier = Modifier) {
    val colores = ColoresJala.actuales
    Row(
        modifier = modifier
            .background(colores.marcaAmarillo, RoundedCornerShape(50))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            centavosASoles(montoCentavos),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = colores.marcaCarbon,
        )
        Spacer(Modifier.size(6.dp))
        Text("💸", style = MaterialTheme.typography.labelLarge)
    }
}
