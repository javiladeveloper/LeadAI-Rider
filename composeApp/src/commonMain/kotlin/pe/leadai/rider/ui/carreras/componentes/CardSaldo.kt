package pe.leadai.rider.ui.carreras.componentes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pe.leadai.rider.datos.MonederoDto
import pe.leadai.rider.ui.comunes.BotonAcento
import pe.leadai.rider.ui.comunes.CardJala
import pe.leadai.rider.ui.tema.ColoresJala
import pe.leadai.rider.ui.tema.centavosASoles

/**
 * El saldo del monedero, arriba de todo en el feed.
 *
 * Sin saldo el rider no puede aceptar nada, así que es lo primero que tiene
 * que ver. Cuando se queda sin plata la card cambia de tono y el mensaje pasa
 * de informar a pedir acción — es lo único que le impide trabajar.
 */
@Composable
fun CardSaldo(
    monedero: MonederoDto,
    onRecargar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colores = ColoresJala.actuales
    val sinSaldo = monedero.carrerasDisponibles <= 0

    CardJala(
        modifier = modifier.fillMaxWidth(),
        color = if (sinSaldo) colores.calor.copy(alpha = 0.10f) else colores.marcaCarbon,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Tu saldo",
                    style = MaterialTheme.typography.labelSmall,
                    // Sobre el carbón el texto va claro; sobre el fondo rojo
                    // suave, oscuro.
                    color = if (sinSaldo) colores.tintaSecundaria else colores.marcaAmarillo,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    centavosASoles(monedero.saldoCentavos),
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                    ),
                    color = if (sinSaldo) colores.calor else colores.superficieCard,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (sinSaldo) {
                        "Recargá para seguir tomando carreras"
                    } else {
                        "Te alcanza para ${monedero.carrerasDisponibles} carreras"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (sinSaldo) colores.calor else colores.tintaSecundaria,
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        BotonAcento(texto = "Recargar", onClick = onRecargar)
    }
}
