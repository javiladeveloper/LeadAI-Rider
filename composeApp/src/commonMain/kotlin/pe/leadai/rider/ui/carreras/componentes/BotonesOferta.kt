package pe.leadai.rider.ui.carreras.componentes

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
import pe.leadai.rider.ui.tema.ColoresJala
import pe.leadai.rider.ui.tema.centavosASoles

/**
 * Lo que el rider decide sobre una carrera: tomarla al precio pedido, o
 * pedir más.
 *
 * TRES BOTONES, no un teclado. El rider está en la calle, con casco, quizás
 * en movimiento: escribir un monto es fricción real. Con "acepto" y dos saltos
 * de a S/1 cubre casi todos los casos con un toque.
 *
 * Se muestra lo que GANA, no lo que cobra: la comisión ya viene descontada
 * desde el backend. Un rider que tiene que restar de memoria decide peor y
 * más lento.
 */
@Composable
fun BotonesOferta(
    montoOfrecidoCentavos: Long,
    /** Lo que le queda tras la comisión — `null` si el backend no lo mandó. */
    gananciaCentavos: Long?,
    enviando: Boolean,
    onOfertar: (montoCentavos: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colores = ColoresJala.actuales
    // Saltos de a S/1: en Tacna las carreras van de S/5 a S/12, así que un
    // sol es el escalón que de verdad cambia la decisión.
    val masUno = montoOfrecidoCentavos + 100
    val masDos = montoOfrecidoCentavos + 200

    Column(modifier = modifier.fillMaxWidth()) {
        if (gananciaCentavos != null) {
            Text(
                "Ganás " + centavosASoles(gananciaCentavos) + " si aceptás",
                style = MaterialTheme.typography.labelLarge,
                color = colores.exito,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
        }

        // Aceptar el precio del cliente es lo que más se usa: va grande y
        // primero.
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
                .clickable(enabled = !enviando) { onOfertar(montoOfrecidoCentavos) },
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

        Text(
            "…o pedí más:",
            style = MaterialTheme.typography.labelSmall,
            color = colores.tintaSecundaria,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            BotonContraoferta(masUno, enviando, Modifier.weight(1f)) { onOfertar(masUno) }
            BotonContraoferta(masDos, enviando, Modifier.weight(1f)) { onOfertar(masDos) }
        }
    }
}

/** Un salto de precio: contorno, para no competir con el botón de aceptar. */
@Composable
private fun BotonContraoferta(
    montoCentavos: Long,
    enviando: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colores = ColoresJala.actuales
    Box(
        modifier = modifier
            .height(48.dp)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(14.dp),
            )
            .clickable(enabled = !enviando) { onClick() }
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            centavosASoles(montoCentavos),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
