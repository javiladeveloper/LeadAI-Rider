package pe.leadai.rider.ui.cliente.componentes

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import pe.leadai.rider.datos.OfertaDto
import pe.leadai.rider.ui.comunes.CardJala
import pe.leadai.rider.ui.tema.AparecerFila
import pe.leadai.rider.ui.tema.ColoresJala
import pe.leadai.rider.ui.tema.recordarInteraccion
import pe.leadai.rider.ui.tema.toqueVivo
import pe.leadai.rider.ui.tema.centavosASoles

/**
 * Las propuestas que le llegaron al cliente: elige a quién, no solo cuánto.
 *
 * Ordenadas por precio porque es lo primero que se compara, pero con la
 * calificación, los viajes y la moto al lado — para poder elegir a otro
 * cuando el más barato no convence.
 */
@Composable
fun OfertasRecibidas(
    ofertas: List<OfertaDto>,
    montoOfrecido: Long,
    eligiendo: String?,
    onElegir: (OfertaDto) -> Unit,
    onSubirMonto: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (ofertas.isEmpty()) {
            SinOfertasTodavia(montoOfrecido, onSubirMonto)
            return@Column
        }

        val cuantos = ofertas.size
        Text(
            if (cuantos == 1) {
                "1 motorizado quiere llevarte"
            } else {
                "$cuantos motorizados quieren llevarte"
            },
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
        )

        ofertas.forEach { oferta ->
            // Las ofertas llegan de a una mientras el cliente mira: sin
            // animación aparecen de un salto y no se nota que son nuevas.
            AparecerFila {
            CardOferta(
                oferta = oferta,
                montoOfrecido = montoOfrecido,
                eligiendo = eligiendo == oferta.id,
                habilitado = eligiendo == null,
                onElegir = { onElegir(oferta) },
            )
            }
        }
    }
}

/**
 * Una oferta: quién es, cuánto pide y cuánto tarda en llegar.
 *
 * El monto es lo más grande de la card. Si pide MÁS de lo ofrecido va en
 * ámbar — el cliente tiene que ver que paga de más antes de tocar, no después.
 */
@Composable
private fun CardOferta(
    oferta: OfertaDto,
    montoOfrecido: Long,
    eligiendo: Boolean,
    habilitado: Boolean,
    onElegir: () -> Unit,
) {
    val colores = ColoresJala.actuales
    val pideMas = oferta.montoCentavos > montoOfrecido
    val r = oferta.rider

    CardJala(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // La foto sale de la SELFIE de verificación: la cara que un
            // humano ya comparó contra su documento.
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text("👤", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.size(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    r.nombre?.takeIf { it.isNotBlank() } ?: "Motorizado",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    reputacionLegible(r.estrellas, r.viajesCompletados),
                    style = MaterialTheme.typography.labelSmall,
                    color = colores.tintaSecundaria,
                )
                val moto = listOf(r.marcaModelo, r.color, r.placa)
                    .filter { it.isNotBlank() }
                    .joinToString(" · ")
                if (moto.isNotBlank()) {
                    Text(
                        moto,
                        style = MaterialTheme.typography.labelSmall,
                        color = colores.tintaSecundaria,
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    centavosASoles(oferta.montoCentavos),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                    ),
                    color = if (pideMas) colores.espera else colores.exito,
                )
                val min = oferta.minutosLlegada
                if (min != null) {
                    Text(
                        "llega en ~$min min",
                        style = MaterialTheme.typography.labelSmall,
                        color = colores.tintaSecundaria,
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        val interaccion = recordarInteraccion()
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .toqueVivo(interaccion)
                .background(
                    color = if (habilitado) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    shape = RoundedCornerShape(16.dp),
                )
                .clickable(
                    interactionSource = interaccion,
                    indication = null,
                    enabled = habilitado,
                ) { onElegir() },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (eligiendo) "UN MOMENTO…" else "ELEGIR A ESTE",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

/**
 * Mientras nadie ofertó: NADA.
 *
 * Antes había una card blanca con "Buscando motorizado…" y el aviso del
 * monto, y tapaba el radar entero — que es justo lo que hay que mirar
 * mientras se espera. El título ya está arriba de la pantalla y el aviso del
 * precio se movió junto al flete, donde el cliente ya está leyendo cuánto
 * ofreció.
 */
@Composable
private fun SinOfertasTodavia(montoOfrecido: Long, onSubirMonto: () -> Unit) {
    // Sin card: el radar se ve completo. Lo que había acá vive ahora en la
    // card del viaje y en el botón de al lado de "Cancelar".
}

/**
 * "4.8 ★ · 32 viajes", o "Nuevo" si nadie lo calificó.
 *
 * Un rider sin calificaciones NO se muestra con 0 estrellas: cero se lee como
 * "pésimo" y en realidad es "todavía no sabemos".
 */
internal fun reputacionLegible(estrellas: Double?, viajes: Int): String {
    val viajesTexto = when (viajes) {
        0 -> "sin viajes aún"
        1 -> "1 viaje"
        else -> "$viajes viajes"
    }
    return if (estrellas == null) "Nuevo · $viajesTexto" else "$estrellas ★ · $viajesTexto"
}
