package pe.leadai.rider.ui.cliente.componentes

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pe.leadai.rider.datos.OfertaDto
import pe.leadai.rider.ui.comunes.CardJala
import pe.leadai.rider.ui.tema.AparecerFila
import pe.leadai.rider.ui.tema.ColoresJala
import pe.leadai.rider.ui.tema.centavosASoles
import pe.leadai.rider.ui.tema.recordarInteraccion
import pe.leadai.rider.ui.tema.toqueVivo

/** Cuánto vale una oferta. Igual que `SEGUNDOS_VIGENCIA_OFERTA` del backend. */
private const val SEGUNDOS_VIGENCIA = 90f

/**
 * Las propuestas que le llegaron al cliente: elige a quién, no solo cuánto.
 *
 * Ordenadas por precio porque es lo primero que se compara, pero con la
 * calificación, los viajes y la moto al lado — para poder elegir a otro
 * cuando el más barato no convence.
 *
 * Cada una entra deslizando desde abajo y muestra cuánto le queda de vigencia:
 * una oferta de hace diez minutos ya no sirve, y elegir a alguien que se fue
 * es peor que no tener ofertas.
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
    if (ofertas.isEmpty()) return

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val cuantos = ofertas.size
        Text(
            if (cuantos == 1) {
                "1 motorizado quiere llevarte"
            } else {
                "$cuantos motorizados quieren llevarte"
            },
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
        )

        ofertas.forEach { oferta ->
            // Cada oferta entra deslizando: llegan de a una mientras el
            // cliente mira, y sin animación aparecen de golpe sin que se note
            // que hay algo nuevo.
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
 * Una oferta, COMPACTA: todo en una fila y el botón al costado.
 *
 * Antes cada card ocupaba dos bloques (datos arriba, botón ancho abajo) y con
 * tres ofertas ya no entraba nada más — justo cuando más ofertas hay, que es
 * cuando el cliente más necesita compararlas.
 *
 * El monto va en ámbar si pide MÁS de lo ofrecido: tiene que verse que paga
 * de más antes de tocar, no después.
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
    val interaccion = recordarInteraccion()

    CardJala(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // La foto sale de la SELFIE de verificación: la cara que un
            // humano ya comparó contra su documento.
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text("👤", style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.size(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    r.nombre?.takeIf { it.isNotBlank() } ?: "Motorizado",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                // Reputación y moto en UNA línea: dos renglones de gris
                // duplicaban el alto de la card sin agregar nada que se lea
                // de un vistazo.
                val moto = listOf(r.marcaModelo, r.color)
                    .filter { it.isNotBlank() }
                    .joinToString(" ")
                val detalle = listOf(
                    reputacionLegible(r.estrellas, r.viajesCompletados),
                    moto,
                ).filter { it.isNotBlank() }.joinToString(" · ")
                Text(
                    detalle,
                    style = MaterialTheme.typography.labelSmall,
                    color = colores.tintaSecundaria,
                    maxLines = 1,
                )
            }

            Spacer(Modifier.size(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    centavosASoles(oferta.montoCentavos),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                    ),
                    color = if (pideMas) colores.espera else colores.exito,
                )
                val min = oferta.minutosLlegada
                if (min != null) {
                    Text(
                        "~$min min",
                        style = MaterialTheme.typography.labelSmall,
                        color = colores.tintaSecundaria,
                    )
                }
            }

            Spacer(Modifier.size(8.dp))
            Box(
                modifier = Modifier
                    .size(width = 96.dp, height = 40.dp)
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
                    if (eligiendo) "…" else "ELEGIR",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        BarraDeVigencia(oferta.segundosRestantes)
    }
}

/**
 * Lo que le queda a la oferta, como una barra que se vacía.
 *
 * Le dice al cliente que esto no espera: sin la barra, una lista de
 * propuestas parece un catálogo tranquilo, y las de arriba pueden estar
 * muertas hace rato.
 *
 * Cambia a ámbar en los últimos 20 segundos — es cuando decidir tarde
 * significa perderla.
 */
@Composable
private fun BarraDeVigencia(segundosRestantes: Int) {
    val colores = ColoresJala.actuales

    // La barra baja SOLA, segundo a segundo.
    //
    // Antes se dibujaba el valor tal como venía del backend, así que solo se
    // movía cuando contestaba el polling: si una respuesta se demoraba o traía
    // el mismo número, la barra quedaba quieta. Una barra de tiempo detenida
    // dice lo contrario de lo que tiene que decir —que esto vence—.
    //
    // El backend sigue mandando la verdad: cada valor nuevo la resincroniza.
    var segundos by remember(segundosRestantes) { mutableStateOf(segundosRestantes) }
    LaunchedEffect(segundosRestantes) {
        while (segundos > 0) {
            delay(1_000)
            segundos -= 1
        }
    }

    val fraccion by animateFloatAsState(
        targetValue = (segundos / SEGUNDOS_VIGENCIA).coerceIn(0f, 1f),
        // Lineal y de un segundo: acompaña al conteo sin saltos.
        animationSpec = tween(durationMillis = 1_000),
        label = "vigencia",
    )
    val porTerminar = segundos <= 20

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(3.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraccion)
                .height(3.dp)
                .background(if (porTerminar) colores.espera else colores.exito),
        )
    }
}

/**
 * "4.8 ★ · 32 viajes", o "Nuevo" si nadie lo calificó.
 *
 * Un rider sin calificaciones NO se muestra con 0 estrellas: cero se lee como
 * "pésimo" y en realidad es "todavía no sabemos".
 */
internal fun reputacionLegible(estrellas: Double?, viajes: Int): String {
    val viajesTexto = when (viajes) {
        0 -> "sin viajes"
        1 -> "1 viaje"
        else -> "$viajes viajes"
    }
    return if (estrellas == null) "Nuevo · $viajesTexto" else "$estrellas ★ · $viajesTexto"
}
