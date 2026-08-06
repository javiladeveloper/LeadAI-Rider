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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import pe.leadai.rider.ui.comunes.CampoJala
import pe.leadai.rider.ui.comunes.CardJala
import pe.leadai.rider.ui.tema.ColoresJala
import pe.leadai.rider.ui.tema.centavosASoles

/**
 * De dónde a dónde, en una sola card con la línea que une los dos puntos.
 *
 * El origen trae un botón de GPS ("estoy acá") porque en la mayoría de los
 * casos el cliente pide desde donde está parado, y escribir su propia
 * dirección es fricción que no aporta.
 */
@Composable
fun CardRecorrido(
    origen: String,
    destino: String,
    onOrigenCambia: (String) -> Unit,
    onDestinoCambia: (String) -> Unit,
    onUsarMiUbicacion: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colores = ColoresJala.actuales

    CardJala(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Punto verde: de acá salgo.
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(colores.exito, CircleShape),
            )
            Spacer(Modifier.size(12.dp))
            CampoSimple(
                valor = origen,
                onCambio = onOrigenCambia,
                placeholder = "¿Desde dónde?",
                modifier = Modifier.weight(1f),
            )
            Text(
                "◎",
                style = MaterialTheme.typography.titleMedium,
                color = colores.exito,
                modifier = Modifier
                    .clickable { onUsarMiUbicacion() }
                    .padding(8.dp),
            )
        }

        // La línea punteada que une origen y destino: deja claro que son los
        // dos extremos de un mismo viaje, no dos campos sueltos.
        Row(modifier = Modifier.padding(start = 4.dp)) {
            Text("⋮", color = colores.tintaSecundaria, style = MaterialTheme.typography.bodyMedium)
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(colores.calor, CircleShape),
            )
            Spacer(Modifier.size(12.dp))
            CampoSimple(
                valor = destino,
                onCambio = onDestinoCambia,
                placeholder = "¿A dónde vamos?",
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** Campo sin borde ni etiqueta: dentro de la card del recorrido sobran. */
@Composable
private fun CampoSimple(
    valor: String,
    onCambio: (String) -> Unit,
    placeholder: String,
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
        modifier = modifier.padding(vertical = 12.dp),
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

/**
 * "Ofrece tu tarifa": el monto en grande, editable, con el rango sugerido
 * debajo.
 *
 * Es lo más importante del formulario porque es lo que decide si un rider
 * acepta. El texto de abajo aclara que el precio NO lo fija la app: se acuerda
 * entre las partes, y por eso se puede ofrecer más o menos.
 */
@Composable
fun CardTarifa(
    monto: String,
    onMontoCambia: (String) -> Unit,
    montoSugeridoCentavos: Long?,
    modifier: Modifier = Modifier,
) {
    val colores = ColoresJala.actuales

    CardJala(modifier = modifier.fillMaxWidth()) {
        Text(
            "OFRECE TU TARIFA",
            style = MaterialTheme.typography.labelSmall,
            color = colores.tintaSecundaria,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "S/",
                style = MaterialTheme.typography.headlineMedium,
                color = colores.tintaSecundaria,
            )
            Spacer(Modifier.size(8.dp))
            androidx.compose.foundation.text.BasicTextField(
                value = monto,
                onValueChange = onMontoCambia,
                textStyle = MaterialTheme.typography.displayLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                ),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(
                    MaterialTheme.colorScheme.primary,
                ),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                ),
                decorationBox = { campo ->
                    if (monto.isEmpty()) {
                        Text(
                            "0.00",
                            style = MaterialTheme.typography.displayLarge.copy(
                                fontWeight = FontWeight.ExtraBold,
                            ),
                            color = colores.tintaSecundaria.copy(alpha = 0.4f),
                        )
                    }
                    campo()
                },
            )
        }

        Spacer(Modifier.height(8.dp))

        // El rango, no un número exacto: sugerir "S/7.60" da una precisión
        // que no existe. Un rango invita a moverse dentro de él.
        if (montoSugeridoCentavos != null && montoSugeridoCentavos > 0) {
            val piso = (montoSugeridoCentavos * 0.9).toLong()
            val techo = (montoSugeridoCentavos * 1.15).toLong()
            Text(
                "Sugerido: ${centavosASoles(piso)} – ${centavosASoles(techo)}",
                style = MaterialTheme.typography.labelLarge,
                color = colores.espera,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
        }
        Text(
            "Podés ofrecer más o menos — lo acordás con el motorizado",
            style = MaterialTheme.typography.labelSmall,
            color = colores.tintaSecundaria,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Cuánto cuesta lo que el rider va a comprar. Solo aparece en encomiendas.
 *
 * Va en ámbar y separado de la tarifa: el cliente tiene que entender que son
 * dos plata distintas — una es el servicio, la otra se la devuelve al rider.
 */
@Composable
fun CardMontoCompra(
    monto: String,
    onMontoCambia: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colores = ColoresJala.actuales

    CardJala(
        modifier = modifier.fillMaxWidth(),
        color = colores.espera.copy(alpha = 0.10f),
    ) {
        CampoJala(
            valor = monto,
            onCambio = onMontoCambia,
            etiqueta = "¿Cuánto cuesta lo que va a comprar?",
            placeholder = "60",
            tipoTeclado = androidx.compose.ui.text.input.KeyboardType.Number,
            prefijo = "S/",
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Se lo devolvés aparte de la tarifa: el motorizado pone esa plata de su bolsillo.",
            style = MaterialTheme.typography.labelSmall,
            color = colores.tintaSecundaria,
        )
    }
}
