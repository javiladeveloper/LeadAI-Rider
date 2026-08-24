package pe.leadai.rider.ui.carreras.componentes

import androidx.compose.foundation.background
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import pe.leadai.rider.ui.tema.ColoresJala
import pe.leadai.rider.ui.tema.recordarInteraccion
import pe.leadai.rider.ui.tema.toqueVivo
import pe.leadai.rider.ui.tema.centavosASoles

/**
 * Lo que el rider decide sobre una carrera: tomarla al precio pedido, o pedir
 * otro monto.
 *
 * El botón grande acepta lo que ofrece el cliente, que es lo que más se usa.
 * Debajo, un campo libre para contraofertar: los saltos de a un sol se
 * quedaban cortos en las carreras largas, donde la diferencia que el rider
 * necesita puede ser de tres o cuatro soles.
 *
 * El campo acepta solo números y se redondea a 10 céntimos —la moneda más
 * chica que circula— para que el monto se pueda cobrar en la calle.
 */
@Composable
fun BotonesOferta(
    montoOfrecidoCentavos: Long,
    /**
     * Lo que le queda tras la comisión. Solo se muestra si DIFIERE de lo
     * ofrecido: durante la campaña sin comisión son iguales, y repetir el
     * mismo número dos veces seguidas confunde en vez de informar.
     */
    gananciaCentavos: Long?,
    enviando: Boolean,
    onOfertar: (montoCentavos: Long) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * El rider empezó a EVALUAR esta carrera: enciende las dos aspitas del
     * cliente.
     *
     * Se dispara al escribir el monto, no al ver la tarjeta en la lista. Un
     * rider con la app abierta en el bolsillo tiene diez solicitudes a la
     * vista y no está mirando ninguna; el que escribe un número sí está
     * decidiendo. Si el aviso no fuera cierto, el cliente aprendería a
     * ignorarlo y perderíamos el indicador.
     */
    onEmpezoAEvaluar: () -> Unit = {},
) {
    val colores = ColoresJala.actuales
    var texto by remember { mutableStateOf("") }
    val contraoferta = centavosDeTexto(texto)

    // Una sola vez por carrera: el servidor renueva la marca solo, y avisar
    // en cada tecla sería una request por dígito.
    var yaAviso by remember { mutableStateOf(false) }
    LaunchedEffect(texto.isNotBlank()) {
        if (texto.isNotBlank() && !yaAviso) {
            yaAviso = true
            onEmpezoAEvaluar()
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        if (gananciaCentavos != null && gananciaCentavos != montoOfrecidoCentavos) {
            Text(
                "Ganás " + centavosASoles(gananciaCentavos) + " (ya con la comisión)",
                style = MaterialTheme.typography.labelLarge,
                color = colores.exito,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
        }

        val interaccionAceptar = recordarInteraccion()
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                // El rider toca esto con guantes y a veces en movimiento: que
                // el botón responda al instante evita el doble toque.
                .toqueVivo(interaccionAceptar)
                .background(
                    color = if (enviando) {
                        MaterialTheme.colorScheme.surfaceVariant
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    shape = RoundedCornerShape(16.dp),
                )
                .clickable(
                    interactionSource = interaccionAceptar,
                    indication = null,
                    enabled = !enviando,
                ) { onOfertar(montoOfrecidoCentavos) },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (enviando) {
                    "ENVIANDO…"
                } else {
                    "ACEPTO POR " + centavosASoles(montoOfrecidoCentavos)
                },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                        shape = RoundedCornerShape(16.dp),
                    )
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "S/",
                        style = MaterialTheme.typography.titleMedium,
                        color = colores.tintaSecundaria,
                    )
                    Spacer(Modifier.width(4.dp))
                    BasicTextField(
                        value = texto,
                        // Solo dígitos y una coma decimal: en la calle, con
                        // guantes, cualquier otra tecla es un error seguro.
                        onValueChange = { texto = soloMonto(it) },
                        enabled = !enviando,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        textStyle = MaterialTheme.typography.titleMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        decorationBox = { campo ->
                            if (texto.isEmpty()) {
                                Text(
                                    "pedir otro monto",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colores.tintaSecundaria,
                                )
                            }
                            campo()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            val listo = contraoferta != null && !enviando
            Box(
                modifier = Modifier
                    .width(110.dp)
                    .height(48.dp)
                    .background(
                        color = if (listo) {
                            colores.esperaFondo
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        shape = RoundedCornerShape(16.dp),
                    )
                    .clickable(enabled = listo) { contraoferta?.let(onOfertar) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "OFERTAR",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (listo) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        colores.tintaSecundaria
                    },
                )
            }
        }

        if (contraoferta != null && contraoferta != centavosCrudos(texto)) {
            Spacer(Modifier.height(8.dp))
            Text(
                // Si el rider escribe 7.57 se le cobra 7.60: mejor que lo vea
                // ahora y no cuando le llegue distinto.
                "Se redondea a " + centavosASoles(contraoferta),
                style = MaterialTheme.typography.labelSmall,
                color = colores.tintaSecundaria,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * Deja solo lo que puede ser un monto: dígitos y UNA coma decimal, con dos
 * decimales como máximo.
 *
 * Se filtra al escribir en vez de validar al final: un campo que acepta
 * cualquier cosa y después rechaza obliga a borrar y reescribir.
 */
internal fun soloMonto(entrada: String): String {
    val normalizado = entrada.replace(',', '.')
    val filtrado = buildString {
        var yaHayPunto = false
        for (c in normalizado) {
            when {
                c.isDigit() -> append(c)
                c == '.' && !yaHayPunto && isNotEmpty() -> {
                    yaHayPunto = true
                    append(c)
                }
            }
        }
    }
    // Tope de 4 enteros: nadie cobra S/10.000 por una carrera en moto, y sin
    // límite un cero de más pasa desapercibido.
    val partes = filtrado.split('.')
    val enteros = partes[0].take(4)
    return if (partes.size > 1) enteros + "." + partes[1].take(2) else enteros
}

/** Lo escrito, en centavos, sin redondear. */
internal fun centavosCrudos(texto: String): Long? {
    val valor = texto.toDoubleOrNull() ?: return null
    if (valor <= 0) return null
    return (valor * 100).toLong()
}

/**
 * Lo escrito, en centavos y redondeado a 10 céntimos.
 *
 * Las monedas de 1 y 5 céntimos salieron de circulación: un monto de S/7.57
 * no se puede cobrar. `null` si no hay un número válido — ahí el botón de
 * ofertar queda apagado.
 */
internal fun centavosDeTexto(texto: String): Long? {
    val crudos = centavosCrudos(texto) ?: return null
    val redondeado = ((crudos + 5) / 10) * 10
    return if (redondeado > 0) redondeado else null
}
