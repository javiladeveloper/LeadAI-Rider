package pe.leadai.rider.ui.carreras.componentes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pe.leadai.rider.datos.CarreraDto
import pe.leadai.rider.ui.carreras.etiquetaTipo
import pe.leadai.rider.ui.carreras.requiereCompra
import pe.leadai.rider.ui.comunes.CardJala
import pe.leadai.rider.ui.tema.ColoresJala
import pe.leadai.rider.ui.tema.centavosASoles

/**
 * Una carrera del pool, lista para aceptar.
 *
 * LA REGLA QUE NO SE NEGOCIA: cuando el rider tiene que comprar algo, el
 * flete (lo que gana) y el monto de compra (lo que adelanta) van SEPARADOS.
 * Un total combinado —"S/68" cuando son S/8 de flete y S/60 de compra— se lee
 * como una carrera muy rentable y no lo es. El adelanto va en ámbar, en su
 * propia línea, con la aclaración de que se lo devuelven.
 */
@Composable
fun CardCarrera(
    carrera: CarreraDto,
    aceptando: Boolean,
    /** Ofertar: el monto va como parámetro porque puede ser el pedido o más. */
    onOfertar: (Long) -> Unit = {},
    /** Ya propuso y espera: sin botones, para no ofertar dos veces sin querer. */
    yaOfertaste: Boolean = false,
    habilitado: Boolean,
    onAceptar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colores = ColoresJala.actuales

    CardJala(modifier = modifier.fillMaxWidth()) {
        // Fila 1: qué tipo de carrera es y cuánto GANA. Nada más.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                etiquetaTipo(carrera.tipo),
                style = MaterialTheme.typography.labelSmall,
                color = colores.tintaSecundaria,
                modifier = Modifier.weight(1f),
            )
            if (carrera.kmAlNegocio != null) {
                Text(
                    "🛵 ${carrera.kmAlNegocio} km",
                    style = MaterialTheme.typography.labelSmall,
                    color = colores.tintaSecundaria,
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        Text(
            centavosASoles(carrera.montoOfrecido),
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(Modifier.height(12.dp))

        // De dónde a dónde. Los dos puntos con el mismo peso visual: el rider
        // necesita ver el recorrido completo antes de decidir.
        PuntoDelRecorrido(
            icono = "📍",
            etiqueta = "Recoge en",
            valor = carrera.origenTexto ?: carrera.negocio,
        )
        val destino = carrera.destinoTexto ?: carrera.direccion
        if (!destino.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            PuntoDelRecorrido(icono = "🏁", etiqueta = "Entrega en", valor = destino)
        }

        if (carrera.notas.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                "📝 ${carrera.notas}",
                style = MaterialTheme.typography.bodyMedium,
                color = colores.tintaSecundaria,
            )
        }

        // El adelanto de compra: cuánta plata tiene que llevar encima. Va
        // aparte y en ámbar — NUNCA sumado a lo que gana.
        if (requiereCompra(carrera)) {
            Spacer(Modifier.height(12.dp))
            AvisoPlataParaCompra(montoCentavos = carrera.montoCompraEstimado ?: 0)
        }

        Spacer(Modifier.height(16.dp))

        if (yaOfertaste) {
            EsperandoRespuestaDelCliente()
        } else {
            // El rider ACEPTA el precio o pide más: el cliente elige entre
            // todos los que ofertaron. Antes el primero que tocaba se la
            // llevaba.
            BotonesOferta(
                montoOfrecidoCentavos = carrera.montoOfrecido,
                gananciaCentavos = carrera.gananciaCentavos,
                enviando = aceptando,
                onOfertar = onOfertar,
            )
        }
    }
}

/** Un extremo del recorrido: ícono, para qué sirve, y la dirección. */
@Composable
private fun PuntoDelRecorrido(icono: String, etiqueta: String, valor: String) {
    val colores = ColoresJala.actuales
    Row(verticalAlignment = Alignment.Top) {
        Text(icono, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.padding(horizontal = 4.dp))
        Column {
            Text(
                etiqueta,
                style = MaterialTheme.typography.labelSmall,
                color = colores.tintaSecundaria,
            )
            Text(
                valor,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/**
 * "Llevás S/60 para la compra" — el adelanto que el rider pone de su bolsillo
 * y el cliente le devuelve al entregar.
 *
 * En ámbar y con borde: tiene que verse ANTES de aceptar, porque si no lleva
 * esa plata encima no puede hacer la carrera.
 */
@Composable
fun AvisoPlataParaCompra(montoCentavos: Long, modifier: Modifier = Modifier) {
    val colores = ColoresJala.actuales
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = colores.esperaFondo,
                shape = MaterialTheme.shapes.small,
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                "💵 Llevás ${centavosASoles(montoCentavos)} para la compra",
                style = MaterialTheme.typography.labelLarge,
                color = colores.espera,
            )
            Text(
                "El cliente te lo devuelve al entregar",
                style = MaterialTheme.typography.labelSmall,
                color = colores.tintaSecundaria,
            )
        }
    }
}

/** Color del badge según el estado de verificación del rider. */
@Composable
fun colorDeEstadoRider(estado: String): Pair<String, Color> {
    val colores = ColoresJala.actuales
    return when (estado) {
        "verificado" -> "Verificado ✓" to colores.exito
        "bloqueado" -> "Bloqueado" to colores.calor
        else -> "Pendiente de verificación" to colores.espera
    }
}

/**
 * Ya ofertó: la pelota está del lado del cliente.
 *
 * Se deja la card en la lista (no se esconde) porque el rider necesita
 * recordar por cuál está esperando mientras mira las demás.
 */
@Composable
private fun EsperandoRespuestaDelCliente() {
    val colores = ColoresJala.actuales
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(colores.esperaFondo, RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "⏳ Propuesta enviada — esperando al cliente",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
