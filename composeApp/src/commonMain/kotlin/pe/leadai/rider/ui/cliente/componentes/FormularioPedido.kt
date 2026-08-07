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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
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
 * "Ofrece tu tarifa" — con los botones − y + del diseño.
 *
 * Los steppers importan más de lo que parece: escribir un monto en un teclado
 * numérico con una mano, parado en la calle, es fricción. Con − y + se ajusta
 * de a S/1 sin abrir el teclado, y el campo sigue editable para quien quiera
 * poner un número exacto.
 *
 * El rango sugerido va abajo, y aclara que el precio NO lo fija la app: se
 * acuerda entre las partes.
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
            "Ofrece tu tarifa",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BotonPaso(
                simbolo = "−",
                // Nunca baja de 1: una tarifa en 0 no la toma nadie.
                onClick = { onMontoCambia(((monto.toIntOrNull() ?: 0) - 1).coerceAtLeast(1).toString()) },
            )
            // 20 y no 12: el monto es el número más grande de la pantalla y
            // con poco aire los botones lo aprietan.
            Spacer(Modifier.size(20.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "S/",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.size(4.dp))
                BasicTextField(
                    value = monto,
                    onValueChange = onMontoCambia,
                    textStyle = MaterialTheme.typography.displayLarge.copy(
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(110.dp),
                    decorationBox = { campo ->
                        Box(contentAlignment = Alignment.Center) {
                            if (monto.isEmpty()) {
                                Text(
                                    "0",
                                    style = MaterialTheme.typography.displayLarge.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                    ),
                                    color = colores.tintaSecundaria.copy(alpha = 0.4f),
                                )
                            }
                            campo()
                        }
                    },
                )
            }
            Spacer(Modifier.size(20.dp))
            BotonPaso(
                simbolo = "+",
                onClick = { onMontoCambia(((monto.toIntOrNull() ?: 0) + 1).toString()) },
            )
        }

        Spacer(Modifier.height(10.dp))

        // Un RANGO, no un número exacto: sugerir "S/7.60" finge una precisión
        // que no existe, y un rango invita a moverse dentro de él.
        val texto = if (montoSugeridoCentavos != null && montoSugeridoCentavos > 0) {
            val piso = (montoSugeridoCentavos * 0.9).toLong() / 100
            val techo = (montoSugeridoCentavos * 1.15).toLong() / 100
            "Sugerido: S/$piso - S/$techo. Podés ofrecer más o menos — lo acordás con el motorizado."
        } else {
            "Podés ofrecer lo que quieras — lo acordás con el motorizado."
        }
        Text(
            texto,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            textAlign = TextAlign.Center,
        )
    }
}

/** Botón circular de 48dp para ajustar la tarifa de a un sol. */
@Composable
private fun BotonPaso(simbolo: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .background(MaterialTheme.colorScheme.surfaceContainer, CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            simbolo,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
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
        color = colores.esperaFondo,
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
