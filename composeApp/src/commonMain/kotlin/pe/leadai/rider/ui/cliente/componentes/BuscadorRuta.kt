package pe.leadai.rider.ui.cliente.componentes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pe.leadai.rider.datos.SugerenciaDireccionDto
import pe.leadai.rider.ui.cliente.TIPO_DELIVERY
import pe.leadai.rider.ui.cliente.TIPO_ENCOMIENDA
import pe.leadai.rider.ui.cliente.TIPO_PASAJERO
import pe.leadai.rider.ui.comunes.CardJala
import pe.leadai.rider.ui.tema.AparecerFila
import pe.leadai.rider.ui.tema.ColoresJala

/**
 * De dónde a dónde, con sugerencias reales mientras se escribe.
 *
 * Antes el cliente escribía a ciegas y no veía si su dirección se había
 * ubicado bien: en Tacna hay tres "Bolognesi" distintas, y el rider terminaba
 * en la otra punta. Ahora elige de una lista con coordenadas.
 *
 * El campo ACTIVO manda: solo uno muestra sugerencias a la vez, porque dos
 * listas abiertas no caben en la pantalla de un teléfono.
 */
@Composable
fun BuscadorRuta(
    origen: String,
    destino: String,
    /** Cuál de los dos está escribiendo: `true` = origen. */
    editandoOrigen: Boolean,
    sugerencias: List<SugerenciaDireccionDto>,
    buscando: Boolean,
    onOrigenCambia: (String) -> Unit,
    onDestinoCambia: (String) -> Unit,
    onFoco: (esOrigen: Boolean) -> Unit,
    onElegirSugerencia: (SugerenciaDireccionDto) -> Unit,
    onUsarMiUbicacion: () -> Unit,
    modifier: Modifier = Modifier,
    /** Qué está pidiendo: cambia las etiquetas y si el GPS sirve de origen. */
    tipo: String = TIPO_PASAJERO,
) {
    val colores = ColoresJala.actuales

    // Cada servicio pregunta lo SUYO.
    //
    // Los tres decían "¿Desde dónde?" y "¿A dónde vamos?", y en un delivery
    // eso no significa nada: el cliente no va a ningún lado — el rider recoge
    // el pedido en el local y se lo trae a su casa. Con las mismas palabras
    // para todo, los tres servicios se sentían el mismo formulario.
    val etiquetaOrigen = when (tipo) {
        TIPO_DELIVERY -> "¿De qué local lo recogemos?"
        TIPO_ENCOMIENDA -> "¿Dónde recogemos el paquete?"
        else -> "¿Desde dónde?"
    }
    val etiquetaDestino = when (tipo) {
        TIPO_DELIVERY -> "¿Dónde te lo llevamos?"
        TIPO_ENCOMIENDA -> "¿Dónde lo entregamos?"
        else -> "¿A dónde vamos?"
    }
    // El GPS como origen SOLO en pasajero: en delivery y envío el origen es el
    // local o la casa de quien manda el paquete, no donde está parado el
    // cliente. Ofrecer "mi ubicación" ahí manda al rider al lugar equivocado.
    val gpsSirveDeOrigen = tipo == TIPO_PASAJERO

    Column(modifier = modifier.fillMaxWidth()) {
        CardJala(modifier = Modifier.fillMaxWidth()) {
            FilaDireccion(
                colorPunto = colores.exito,
                valor = origen,
                placeholder = etiquetaOrigen,
                activo = editandoOrigen,
                onCambio = onOrigenCambia,
                onFoco = { onFoco(true) },
                // El GPS solo en el ORIGEN: el destino casi nunca es donde
                // uno está parado.
                accion = {
                    if (gpsSirveDeOrigen) {
                        Text(
                            "◎",
                            style = MaterialTheme.typography.titleMedium,
                            color = colores.exito,
                            modifier = Modifier.clickable { onUsarMiUbicacion() }.padding(8.dp),
                        )
                    }
                },
            )

            Row(modifier = Modifier.padding(start = 4.dp)) {
                Text("⋮", color = colores.tintaSecundaria, style = MaterialTheme.typography.bodyMedium)
            }

            FilaDireccion(
                colorPunto = colores.calor,
                valor = destino,
                placeholder = etiquetaDestino,
                activo = !editandoOrigen,
                onCambio = onDestinoCambia,
                onFoco = { onFoco(false) },
            )
        }

        // Las sugerencias van DEBAJO de la card, no dentro: dentro empujarían
        // el resto del formulario cada vez que aparecen.
        if (buscando || sugerencias.isNotEmpty()) {
            Spacer(Modifier.size(8.dp))
            CardJala(modifier = Modifier.fillMaxWidth(), paddingInterno = 0) {
                if (buscando && sugerencias.isEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = colores.marcaAmarillo,
                        )
                        Spacer(Modifier.size(10.dp))
                        Text(
                            "Buscando…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colores.tintaSecundaria,
                        )
                    }
                } else {
                    // Altura acotada: con 5 resultados largos, sin tope tapa
                    // el botón de pedir.
                    LazyColumn(modifier = Modifier.heightIn(max = 260.dp)) {
                        items(sugerencias) { s ->
                            // Cada fila entra deslizando: una lista que se
                            // llena de golpe no se registra como algo nuevo.
                            AparecerFila { FilaSugerencia(s, onElegirSugerencia) }
                        }
                    }
                }
            }
        }
    }
}

/** Un campo del recorrido: punto de color, texto y una acción opcional. */
@Composable
private fun FilaDireccion(
    colorPunto: androidx.compose.ui.graphics.Color,
    valor: String,
    placeholder: String,
    activo: Boolean,
    onCambio: (String) -> Unit,
    onFoco: () -> Unit,
    accion: @Composable (() -> Unit)? = null,
) {
    val colores = ColoresJala.actuales
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(10.dp).background(colorPunto, CircleShape))
        Spacer(Modifier.size(12.dp))
        CampoDireccion(
            valor = valor,
            onCambio = onCambio,
            placeholder = placeholder,
            activo = activo,
            onFoco = onFoco,
            modifier = Modifier.weight(1f),
        )
        accion?.invoke()
    }
}

/** Una dirección sugerida: nombre arriba, referencia abajo. */
@Composable
private fun FilaSugerencia(
    s: SugerenciaDireccionDto,
    onElegir: (SugerenciaDireccionDto) -> Unit,
) {
    val colores = ColoresJala.actuales
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onElegir(s) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("📍", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                s.texto,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // El renglón que hace elegible la lista: sin él, cinco lugares
            // distintos se ven exactamente iguales.
            val referencia = listOf(s.categoria, s.detalle)
                .filter { it.isNotBlank() }
                .joinToString(" · ")
            if (referencia.isNotBlank()) {
                Text(
                    referencia,
                    style = MaterialTheme.typography.labelSmall,
                    color = colores.tintaSecundaria,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * Campo de texto sin borde: dentro de la card del recorrido sobra.
 *
 * `onFoco` avisa cuál de los dos se está editando — el buscador necesita
 * saberlo para mostrar las sugerencias del correcto.
 */
@Composable
private fun CampoDireccion(
    valor: String,
    onCambio: (String) -> Unit,
    placeholder: String,
    activo: Boolean,
    onFoco: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colores = ColoresJala.actuales
    androidx.compose.foundation.text.BasicTextField(
        value = valor,
        onValueChange = onCambio,
        textStyle = MaterialTheme.typography.bodyMedium.copy(
            color = MaterialTheme.colorScheme.onSurface,
        ),
        cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
        modifier = modifier
            .padding(vertical = 12.dp)
            .onFocusChanged { if (it.isFocused) onFoco() },
        decorationBox = { campo ->
            if (valor.isEmpty()) {
                Text(
                    placeholder,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colores.tintaSecundaria,
                )
            }
            campo()
        },
    )
}
