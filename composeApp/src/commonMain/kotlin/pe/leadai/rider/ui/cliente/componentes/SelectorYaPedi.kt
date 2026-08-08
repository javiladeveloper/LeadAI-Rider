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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import pe.leadai.rider.ui.comunes.CardJala
import pe.leadai.rider.ui.tema.ColoresJala

/**
 * ¿El pedido ya está hecho, o lo tiene que hacer el rider?
 *
 * Cambia el trabajo por completo. El que pasa a retirar algo listo entra y
 * sale; el que va a la pollería, PIDE las alitas y espera 25 minutos hizo el
 * mismo kilometraje pero perdió dos carreras. Sin preguntarlo, los dos cobran
 * lo mismo y las carreras con espera no las toma nadie.
 *
 * Lo declara el cliente, que es el único que sabe si ya llamó. Y se pregunta
 * ANTES de pedir, no después: un cargo que aparece al final se lee como
 * trampa.
 *
 * Solo en delivery. En un envío el paquete ya existe —nadie lo cocina— y en un
 * viaje de pasajero no hay nada que esperar.
 */
@Composable
fun SelectorYaPedi(
    esperaEnLocal: Boolean,
    /** Cuánto suma la espera, para mostrarlo antes de que el cliente elija. */
    extraCentavos: Long,
    onCambiar: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colores = ColoresJala.actuales

    CardJala(modifier = modifier.fillMaxWidth()) {
        Text(
            "¿Ya hiciste el pedido?",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OpcionPedido(
                titulo = "Sí, solo recoger",
                detalle = "Ya está pagado o encargado",
                elegida = !esperaEnLocal,
                modifier = Modifier.weight(1f),
            ) { onCambiar(false) }

            OpcionPedido(
                titulo = "No, pedilo vos",
                detalle = "+ " + soles(extraCentavos) + " por la espera",
                elegida = esperaEnLocal,
                modifier = Modifier.weight(1f),
            ) { onCambiar(true) }
        }

        if (esperaEnLocal) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Poné en las notas qué pedir y cuánto cuesta",
                style = MaterialTheme.typography.labelSmall,
                color = colores.tintaSecundaria,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Una de las dos opciones. La elegida se marca con el color de acento. */
@Composable
private fun OpcionPedido(
    titulo: String,
    detalle: String,
    elegida: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colores = ColoresJala.actuales

    Box(
        modifier = modifier
            .height(64.dp)
            .background(
                color = if (elegida) colores.esperaFondo else MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(12.dp),
            )
            .border(
                width = if (elegida) 0.dp else 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(12.dp),
            )
            .clickable { onClick() }
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                titulo,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Text(
                detalle,
                style = MaterialTheme.typography.labelSmall,
                color = colores.tintaSecundaria,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Centavos a "S/2.00". Local para no arrastrar el formateador del tema acá. */
private fun soles(centavos: Long): String {
    val enteros = centavos / 100
    val resto = (centavos % 100).toString().padStart(2, '0')
    return "S/$enteros.$resto"
}
