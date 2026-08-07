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
import androidx.compose.ui.unit.dp
import pe.leadai.rider.datos.SugerenciaDireccionDto
import pe.leadai.rider.ui.comunes.CardJala
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
) {
    val colores = ColoresJala.actuales

    Column(modifier = modifier.fillMaxWidth()) {
        CardJala(modifier = Modifier.fillMaxWidth()) {
            FilaDireccion(
                colorPunto = colores.exito,
                valor = origen,
                placeholder = "¿Desde dónde?",
                activo = editandoOrigen,
                onCambio = onOrigenCambia,
                onFoco = { onFoco(true) },
                // El GPS solo en el ORIGEN: el destino casi nunca es donde
                // uno está parado.
                accion = {
                    Text(
                        "◎",
                        style = MaterialTheme.typography.titleMedium,
                        color = colores.exito,
                        modifier = Modifier.clickable { onUsarMiUbicacion() }.padding(8.dp),
                    )
                },
            )

            Row(modifier = Modifier.padding(start = 4.dp)) {
                Text("⋮", color = colores.tintaSecundaria, style = MaterialTheme.typography.bodyMedium)
            }

            FilaDireccion(
                colorPunto = colores.calor,
                valor = destino,
                placeholder = "¿A dónde vamos?",
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
                        items(sugerencias) { s -> FilaSugerencia(s, onElegirSugerencia) }
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
        Text(
            s.texto,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface,
        )
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
