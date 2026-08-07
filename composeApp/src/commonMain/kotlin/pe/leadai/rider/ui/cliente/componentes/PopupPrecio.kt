package pe.leadai.rider.ui.cliente.componentes

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import pe.leadai.rider.ui.tema.ColoresJala
import pe.leadai.rider.ui.tema.centavosASoles

/** Cuánto sube o baja cada toque. En Tacna el sol es el escalón real. */
private const val PASO_CENTAVOS = 100L

/** Nadie hace una carrera por menos: por debajo no llegan ofertas. */
private const val MINIMO_CENTAVOS = 200L

/**
 * Cuánto ofrece el cliente, antes de que la carrera salga al aire.
 *
 * Es el momento de decisión del modelo: acá el cliente pone SU precio, no
 * acepta una tarifa. Lo que la app calculó llega como punto de partida y se
 * muestra como referencia — si el cliente lo cambia, tiene que ver contra qué.
 *
 * Sin teclado: +/- de a un sol. Escribir un monto abre el teclado, tapa media
 * pantalla y obliga a cerrarlo para seguir; con dos toques ya está resuelto en
 * el 90% de los casos.
 */
@Composable
fun PopupPrecio(
    montoCentavos: Long,
    /** Lo que calculó la app — para contrastar cuando el cliente lo mueve. */
    sugeridoCentavos: Long?,
    kmEstimado: Double?,
    enviando: Boolean,
    onCambiar: (Long) -> Unit,
    onConfirmar: () -> Unit,
    onCerrar: () -> Unit,
) {
    val colores = ColoresJala.actuales

    Dialog(onDismissRequest = { if (!enviando) onCerrar() }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp))
                .padding(20.dp),
        ) {
            Text(
                "¿Cuánto ofrecés?",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                textoDeReferencia(sugeridoCentavos, kmEstimado),
                style = MaterialTheme.typography.bodySmall,
                color = colores.tintaSecundaria,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                BotonPaso("−", habilitado = !enviando && montoCentavos > MINIMO_CENTAVOS) {
                    onCambiar(maxOf(MINIMO_CENTAVOS, montoCentavos - PASO_CENTAVOS))
                }
                Text(
                    centavosASoles(montoCentavos),
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                )
                BotonPaso("+", habilitado = !enviando) {
                    onCambiar(montoCentavos + PASO_CENTAVOS)
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                avisoSegunElMonto(montoCentavos, sugeridoCentavos),
                style = MaterialTheme.typography.labelSmall,
                color = if (esBajo(montoCentavos, sugeridoCentavos)) {
                    colores.espera
                } else {
                    colores.tintaSecundaria
                },
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(20.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .background(
                        color = if (enviando) {
                            MaterialTheme.colorScheme.surfaceVariant
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        shape = RoundedCornerShape(14.dp),
                    )
                    .clickable(enabled = !enviando) { onConfirmar() },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (enviando) "ENVIANDO…" else "BUSCAR MOTORIZADO",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }

            Spacer(Modifier.height(10.dp))
            Text(
                "Cancelar",
                style = MaterialTheme.typography.labelLarge,
                color = colores.tintaSecundaria,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !enviando) { onCerrar() }
                    .padding(vertical = 6.dp),
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Un paso de precio: redondo y grande, para el pulgar. */
@Composable
private fun BotonPaso(simbolo: String, habilitado: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(52.dp)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(26.dp),
            )
            .clickable(enabled = habilitado) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            simbolo,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = if (habilitado) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.outlineVariant
            },
        )
    }
}

/** "≈ 3.2 km · sugerido S/6.00" — el contexto para juzgar el número de arriba. */
internal fun textoDeReferencia(sugeridoCentavos: Long?, kmEstimado: Double?): String {
    val partes = buildList {
        if (kmEstimado != null && kmEstimado > 0) add("≈ $kmEstimado km")
        if (sugeridoCentavos != null) add("sugerido " + centavosASoles(sugeridoCentavos))
    }
    return if (partes.isEmpty()) "Vos ponés el precio" else partes.joinToString(" · ")
}

/**
 * Ofrecer poco no se bloquea: se avisa.
 *
 * El cliente conoce su barrio mejor que el cálculo, y a veces tiene razón. Pero
 * si nadie va a tomar la carrera, mejor que lo sepa ahora y no tras diez
 * minutos mirando "buscando…".
 */
internal fun esBajo(montoCentavos: Long, sugeridoCentavos: Long?): Boolean =
    sugeridoCentavos != null && montoCentavos < sugeridoCentavos * 8 / 10

internal fun avisoSegunElMonto(montoCentavos: Long, sugeridoCentavos: Long?): String = when {
    sugeridoCentavos == null -> "Los motorizados pueden aceptar o pedirte más"
    esBajo(montoCentavos, sugeridoCentavos) -> "Puede que tarde en aparecer alguien"
    montoCentavos > sugeridoCentavos -> "Con este monto te van a responder rápido"
    else -> "Los motorizados pueden aceptar o pedirte más"
}
