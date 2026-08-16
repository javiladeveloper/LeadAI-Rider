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
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.material3.CircularProgressIndicator
import pe.leadai.rider.ui.tema.Formas
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.draw.clip
import pe.leadai.rider.ui.comunes.MapaElegirPunto
import pe.leadai.rider.ui.tema.ColoresJala
import pe.leadai.rider.ui.tema.Movimiento
import pe.leadai.rider.ui.tema.recordarInteraccion
import pe.leadai.rider.ui.tema.toqueVivo
import pe.leadai.rider.ui.tema.centavosASoles

/**
 * Cuánto sube o baja cada toque: medio sol.
 *
 * Es el paso de inDrive, medido en la app. Con saltos de un sol el cliente
 * pasa de largo el precio que quería; con saltos de 10 céntimos necesita diez
 * toques para mover un sol.
 */

private const val PASO_CENTAVOS = 50L

/**
 * Lo MENOS que se puede ofrecer: el 80% de lo sugerido, nunca bajo S/5.
 *
 * Medido en inDrive: una carrera sugerida en S/8.90 solo dejaba bajar hasta
 * S/7.00. Sin tope, el cliente podría ofrecer S/2 por seis kilómetros — no es
 * que esté prohibido, es que nadie la tomaría, y una lista de carreras
 * muertas hace que el rider deje de mirar la app.
 *
 * Lo calcula también el backend; acá se repite para que los botones se
 * apaguen en el momento, sin esperar una respuesta.
 */
// S/5.00 desde 2026-08-16 (antes S/4.00): es lo que se cobra de piso en
// Tacna. TIENE QUE COINCIDIR con `MINIMO_CENTAVOS` del backend — si acá fuera
// menor, los botones dejarían ofrecer un monto que el servidor rechaza.
private const val MINIMO_ABSOLUTO_CENTAVOS = 500L
private const val FRACCION_MINIMA = 0.8

internal fun montoMinimoOfertable(sugeridoCentavos: Long?): Long {
    if (sugeridoCentavos == null || sugeridoCentavos <= 0) return MINIMO_ABSOLUTO_CENTAVOS
    val porFraccion = ((sugeridoCentavos * FRACCION_MINIMA) / PASO_CENTAVOS).toLong() * PASO_CENTAVOS
    return maxOf(MINIMO_ABSOLUTO_CENTAVOS, porFraccion)
}

/**
 * Cuánto ofrece el cliente, antes de que la carrera salga al aire.
 *
 * Es el momento de decisión del modelo: acá el cliente pone SU precio, no
 * acepta una tarifa. Lo que la app calculó llega como punto de partida y se
 * muestra como referencia — si el cliente lo cambia, tiene que ver contra qué.
 *
 * Sin teclado: +/- de a medio sol. Escribir un monto abre el teclado, tapa media
 * pantalla y obliga a cerrarlo para seguir; con dos toques ya está resuelto en
 * el 90% de los casos.
 */
@Composable
fun PopupPrecio(
    montoCentavos: Long,
    /** `true` mientras se resuelve la ruta: se muestra "calculando", no un monto. */
    calculando: Boolean = false,
    /** Lo que calculó la app — para contrastar cuando el cliente lo mueve. */
    sugeridoCentavos: Long?,
    kmEstimado: Double?,
    enviando: Boolean,
    onCambiar: (Long) -> Unit,
    onConfirmar: () -> Unit,
    onCerrar: () -> Unit,
    /** El punto de recojo, para confirmarlo en el mapa. `null` = sin mapa. */
    origenLat: Double? = null,
    origenLng: Double? = null,
    /** Dirección del pin mientras el cliente lo mueve. */
    direccionDelPin: String = "",
    onMoverPin: (lat: Double, lng: Double) -> Unit = { _, _ -> },
) {
    val colores = ColoresJala.actuales

    Dialog(onDismissRequest = { if (!enviando) onCerrar() }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(24.dp))
                .padding(24.dp),
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
                if (calculando) {
                    "Calculando el recorrido…"
                } else {
                    textoDeReferencia(sugeridoCentavos, kmEstimado)
                },
                style = MaterialTheme.typography.bodySmall,
                color = colores.tintaSecundaria,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                val minimo = montoMinimoOfertable(sugeridoCentavos)
                BotonPaso("−", habilitado = !enviando && !calculando && montoCentavos > minimo) {
                    onCambiar(maxOf(minimo, montoCentavos - PASO_CENTAVOS))
                }
                // El monto ANIMA hasta su valor: al tocar +/- el número sube
                // en vez de saltar, y al terminar el cálculo cuenta desde la
                // referencia. Un número que salta no se lee como que cambió
                // por algo; uno que se mueve, sí.
                val montoAnimado by animateIntAsState(
                    targetValue = montoCentavos.toInt(),
                    animationSpec = tween(Movimiento.NORMAL_MS, easing = Movimiento.SUAVE),
                    label = "monto",
                )
                Text(
                    // Un precio inventado que después cambia es peor que
                    // esperar un segundo: el cliente decidiría sobre un número
                    // que no es.
                    if (calculando) "…" else centavosASoles(montoAnimado.toLong()),
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                    ),
                    color = if (calculando) {
                        colores.tintaSecundaria
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                )
                BotonPaso("+", habilitado = !enviando && !calculando) {
                    onCambiar(montoCentavos + PASO_CENTAVOS)
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                if (montoCentavos <= montoMinimoOfertable(sugeridoCentavos)) {
                    "Es lo mínimo para esta distancia"
                } else {
                    avisoSegunElMonto(montoCentavos, sugeridoCentavos)
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (esBajo(montoCentavos, sugeridoCentavos)) {
                    colores.espera
                } else {
                    colores.tintaSecundaria
                },
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(24.dp))

            val interaccionConfirmar = recordarInteraccion()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    // Se hunde al tocarlo: sin esto el dedo toca y no pasa
                    // nada hasta que la pantalla cambia, y el cliente duda de
                    // si registró el toque.
                    .toqueVivo(interaccionConfirmar)
                    .background(
                        // Ámbar atenuado y no gris: en gris parece
                        // deshabilitado, cuando en realidad está trabajando.
                        color = if (enviando) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        shape = Formas.card,
                    )
                    .clickable(
                        interactionSource = interaccionConfirmar,
                        indication = null,
                        enabled = !enviando && !calculando,
                    ) { onConfirmar() },
                contentAlignment = Alignment.Center,
            ) {
                // Mientras envía, un indicador REAL girando —no solo texto—.
                //
                // "ENVIANDO…" a secas se ve igual que un botón trabado: el
                // texto no se mueve y el cliente no sabe si su pedido salió.
                // Es el momento en que más importa, porque acaba de decidir
                // cuánto pagar y está esperando que algo pase.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (enviando) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp,
                        )
                    }
                    Text(
                        if (enviando) "Buscando…" else "BUSCAR MOTORIZADO",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                "Cancelar",
                style = MaterialTheme.typography.labelLarge,
                color = colores.tintaSecundaria,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !enviando) { onCerrar() }
                    .padding(vertical = 8.dp),
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
            .size(56.dp)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(24.dp),
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
