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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pe.leadai.rider.datos.MonederoDto
import pe.leadai.rider.datos.PerfilMotorizadoDto
import pe.leadai.rider.ui.tema.ColoresJala
import pe.leadai.rider.ui.tema.centavosASoles

/**
 * El encabezado del feed, como lo diseñó Stitch: saludo con ciudad y estado
 * de verificación, y debajo la card de saldo a sangre en amarillo.
 *
 * El saludo y los metadatos van en filas separadas —no compartiendo ancho—
 * porque "Pendiente de verificación" es texto largo y con un nombre normal
 * los dos quedaban apretados contra los bordes.
 */
@Composable
fun EncabezadoRider(
    nombreUsuario: String,
    perfil: PerfilMotorizadoDto,
    monedero: MonederoDto?,
    onRecargar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colores = ColoresJala.actuales

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = if (nombreUsuario.isNotBlank()) "Hola, $nombreUsuario 🏍️" else "Hola 🏍️",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.ExtraBold,
            ),
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(Modifier.height(6.dp))

        // Ciudad · estado, separados por un punto — como en el diseño.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "📍 ${perfil.distrito}",
                style = MaterialTheme.typography.labelSmall,
                color = colores.tintaSecundaria,
            )
            Box(
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .size(4.dp)
                    .background(colores.tintaSecundaria.copy(alpha = 0.5f), CircleShape),
            )
            val (textoEstado, colorEstado) = colorDeEstadoRider(perfil.estado)
            Text(
                textoEstado,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = colorEstado,
            )
        }

        if (monedero != null) {
            Spacer(Modifier.height(16.dp))
            CardSaldoAmarilla(monedero = monedero, onRecargar = onRecargar)
        }
    }
}

/**
 * El saldo en amarillo de marca, a sangre completa.
 *
 * Es la card más visible de la pantalla a propósito: sin saldo el rider no
 * puede aceptar nada, así que el número tiene que estar antes que las
 * carreras.
 *
 * Todo el texto va en CARBÓN sobre el amarillo — blanco sobre este fondo da
 * ~1.9:1 y al sol desaparece.
 */
@Composable
private fun CardSaldoAmarilla(
    monedero: MonederoDto,
    onRecargar: () -> Unit,
) {
    val colores = ColoresJala.actuales
    val sinSaldo = monedero.carrerasDisponibles <= 0

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                // Sin saldo la card se apaga: deja de ser una buena noticia y
                // pasa a ser lo que le impide trabajar.
                color = if (sinSaldo) colores.calor.copy(alpha = 0.12f) else colores.marcaAmarillo,
                shape = RoundedCornerShape(16.dp),
            )
            .padding(20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "TU SALDO ACTUAL",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (sinSaldo) colores.tintaSecundaria else colores.marcaCarbon.copy(alpha = 0.75f),
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    centavosASoles(monedero.saldoCentavos),
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                    ),
                    color = if (sinSaldo) colores.calor else colores.marcaCarbon,
                )
            }
            Text("💳", style = MaterialTheme.typography.headlineMedium)
        }

        Spacer(Modifier.height(14.dp))

        // Franja interna con el detalle y el botón: separa "cuánto tengo" de
        // "qué hago con eso".
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = colores.marcaCarbon.copy(alpha = if (sinSaldo) 0.06f else 0.12f),
                    shape = RoundedCornerShape(12.dp),
                )
                .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (sinSaldo) {
                    "Recargá para tomar carreras"
                } else {
                    "Te alcanza para ~${monedero.carrerasDisponibles} carreras"
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (sinSaldo) colores.calor else colores.marcaCarbon,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = onRecargar,
                modifier = Modifier
                    .background(
                        color = if (sinSaldo) colores.calor else colores.marcaCarbon,
                        shape = RoundedCornerShape(50),
                    ),
            ) {
                Text(
                    "Recargar",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (sinSaldo) colores.superficieCard else colores.marcaAmarillo,
                )
            }
        }
    }
}
