package pe.leadai.rider.ui.carreras.componentes

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pe.leadai.rider.datos.CarreraDto
import pe.leadai.rider.ui.comunes.MapaRuta
import pe.leadai.rider.ui.comunes.PuntoMapa
import pe.leadai.rider.ui.tema.ColoresJala
import pe.leadai.rider.ui.tema.Formas
import pe.leadai.rider.ui.tema.centavosASoles

/**
 * LA SOLICITUD A PANTALLA COMPLETA, con el mapa a la vista.
 *
 * En el feed el rider decide leyendo dos direcciones y un monto, y
 * "Mercado Central" no dice si queda de camino o al otro lado de la ciudad.
 * Acá ve el recorrido dibujado y decide igual que en la calle —mirando— en
 * vez de imaginándose el mapa.
 *
 * Se abre al TOCAR una solicitud, no sola al llegar: nuestro rider tiene un
 * feed con varias compitiendo, y una pantalla que se abriera encima taparía
 * justo la que estaba mirando.
 */
@Composable
fun SolicitudDetalle(
    carrera: CarreraDto,
    /** Dónde está la moto: sin esto no se puede ver cuánto falta. */
    miUbicacion: PuntoMapa?,
    /** El camino por calle hasta el recojo. Vacío dibuja la recta. */
    recorrido: List<PuntoMapa>,
    aceptando: Boolean,
    habilitado: Boolean,
    yaOfertaste: Boolean,
    onAceptar: () -> Unit,
    onOfertar: (Long) -> Unit,
    onCerrar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colores = ColoresJala.actuales
    val recojo = carrera.origenLat?.let { la ->
        carrera.origenLng?.let { lo -> PuntoMapa(la, lo) }
    }
    val entrega = carrera.destinoLat?.let { la ->
        carrera.destinoLng?.let { lo -> PuntoMapa(la, lo) }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            if (recojo != null) {
                MapaRuta(
                    origen = recojo,
                    destino = entrega ?: recojo,
                    moto = miUbicacion,
                    recorrido = recorrido,
                    tipoServicio = carrera.tipo,
                    // NO es modo rider: acá todavía no maneja, está
                    // decidiendo. Lo que necesita ver es el viaje ENTERO
                    // —de dónde a dónde— y no su propia cuadra de cerca.
                    modoRider = false,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            // "Ignorar" arriba y sobre el mapa: la salida siempre a la vista.
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
                    .background(colores.marcaCarbon.copy(alpha = 0.88f), RoundedCornerShape(50))
                    .clickable { onCerrar() }
                    .padding(horizontal = 18.dp, vertical = 10.dp),
            ) {
                Text(
                    "✕  Ignorar",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    colores.superficieCard,
                    RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                )
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        ) {
            // Las dos puntas del viaje, con el mismo código de color que los
            // pines del mapa.
            PuntoDelViaje(
                color = colores.marcaAmarillo,
                texto = carrera.origenTexto ?: carrera.negocio,
                destacado = true,
            )
            Spacer(Modifier.height(8.dp))
            PuntoDelViaje(
                color = colores.exito,
                texto = carrera.destinoTexto ?: carrera.direccion ?: "Sin destino",
                destacado = false,
            )

            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    centavosASoles(carrera.montoOfrecido),
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                    ),
                    color = colores.marcaAmarillo,
                )
                carrera.kmEstimado?.let { km ->
                    Spacer(Modifier.size(10.dp))
                    Text(
                        "· ${formatearKm(km)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = colores.tintaSecundaria,
                    )
                }
            }

            if (carrera.notas.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    carrera.notas,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            Spacer(Modifier.height(18.dp))
            if (yaOfertaste) {
                // Ya propuso: sin botones, para no ofertar dos veces sin
                // querer mientras espera la respuesta.
                Text(
                    "Ya ofertaste — esperá la respuesta",
                    style = MaterialTheme.typography.labelLarge,
                    color = colores.tintaSecundaria,
                )
            } else {
                Button(
                    onClick = onAceptar,
                    enabled = habilitado && !aceptando,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = Formas.card,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colores.marcaAmarillo,
                        contentColor = colores.marcaCarbon,
                    ),
                ) {
                    Text(
                        if (aceptando) {
                            "Aceptando…"
                        } else {
                            "Aceptar por ${centavosASoles(carrera.montoOfrecido)}"
                        },
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                }

                Spacer(Modifier.height(14.dp))
                Text(
                    "Ofrecé tu tarifa",
                    style = MaterialTheme.typography.labelMedium,
                    color = colores.tintaSecundaria,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                // Montos de UN TOQUE en vez de teclado.
                //
                // Escribir el monto es la fricción que hace que el rider
                // simplemente acepte o ignore: en la calle, con casco y
                // apurado, nadie tipea.
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    montosSugeridos(carrera.montoOfrecido).forEach { monto ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(
                                    colores.marcaAmarillo.copy(alpha = 0.16f),
                                    Formas.chip,
                                )
                                .clickable(enabled = habilitado) { onOfertar(monto) }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                centavosASoles(monto),
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                ),
                                color = colores.marcaAmarillo,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Tres montos por encima de lo ofrecido.
 *
 * Porcentajes y no pasos fijos: +S/1 sobre S/5 es mucho y sobre S/25 no es
 * nada. Se redondea a los 10 céntimos de arriba porque un "S/ 6.13" en un
 * botón se lee como un error de la app.
 */
internal fun montosSugeridos(ofrecido: Long): List<Long> {
    // Sin monto ofrecido igual hay que mostrar algo usable.
    val base = if (ofrecido > 0) ofrecido else 500L
    return listOf(1.10, 1.20, 1.30).map { factor ->
        val crudo = (base * factor).toLong()
        ((crudo + 9) / 10) * 10
    }
}

/** Un kilometraje que se lee: "1,4 km" o "800 m". */
internal fun formatearKm(km: Double): String =
    if (km < 1.0) {
        "${(km * 1000).toInt()} m"
    } else {
        "${(km * 10).toInt() / 10.0} km".replace(".", ",")
    }

@Composable
private fun PuntoDelViaje(color: Color, texto: String, destacado: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(10.dp).background(color, CircleShape))
        Spacer(Modifier.size(12.dp))
        Text(
            texto,
            style = if (destacado) {
                MaterialTheme.typography.titleMedium
            } else {
                MaterialTheme.typography.bodyLarge
            },
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
