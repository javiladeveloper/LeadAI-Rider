package pe.leadai.rider.ui.carreras.componentes

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.material3.Switch
import androidx.compose.runtime.getValue
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
    /** Entrar o salir de turno. Sin esto no llegan avisos de carrera nueva. */
    onCambiarTurno: (Boolean) -> Unit = {},
    cambiandoTurno: Boolean = false,
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

        Spacer(Modifier.height(8.dp))

        // EL INTERRUPTOR DE TURNO. Va arriba de todo y siempre visible porque
        // determina si al rider le llegan avisos: el backend solo notifica a
        // quien está disponible, y antes no había forma de encenderlo desde
        // la app — ningún rider recibía nada.
        InterruptorDeTurno(
            enTurno = perfil.disponible,
            cambiando = cambiandoTurno,
            onCambiar = onCambiarTurno,
        )

        Spacer(Modifier.height(8.dp))

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

/**
 * "En turno" / "Fuera de turno", con un punto de color.
 *
 * Verde cuando está trabajando y gris cuando no: el rider tiene que saber de
 * un vistazo si le van a llegar carreras. Sin esta señal, uno que se olvidó
 * de entrar en turno cree que no hay trabajo.
 */
@Composable
private fun InterruptorDeTurno(
    enTurno: Boolean,
    cambiando: Boolean,
    onCambiar: (Boolean) -> Unit,
) {
    val colores = ColoresJala.actuales
    val fondo by animateColorAsState(
        targetValue = if (enTurno) colores.exito.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant,
        animationSpec = tween(200),
        label = "fondoTurno",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(fondo, RoundedCornerShape(16.dp))
            .clickable(enabled = !cambiando) { onCambiar(!enTurno) }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(
                    if (enTurno) colores.exito else colores.tintaSecundaria,
                    CircleShape,
                ),
        )
        Spacer(Modifier.size(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                if (enTurno) "En turno" else "Fuera de turno",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                if (enTurno) {
                    "Te avisamos de las carreras nuevas"
                } else {
                    "Tocá para empezar a recibir carreras"
                },
                style = MaterialTheme.typography.labelSmall,
                color = colores.tintaSecundaria,
            )
        }
        // El Switch NO maneja el toque: lo maneja la fila que lo contiene.
        //
        // Con los dos activos, tocar el switch disparaba `onCheckedChange` Y el
        // `clickable` de la fila: dos llamadas con valores opuestos. La guarda
        // `cambiandoTurno` tapaba la segunda casi siempre, pero si la primera
        // ya había respondido, la segunda apagaba el turno recién encendido.
        //
        // El rider quedaba fuera de turno sin tocar nada, y su moto
        // desaparecía del radar del cliente a los pocos segundos.
        Switch(
            checked = enTurno,
            onCheckedChange = null,
            enabled = !cambiando,
        )
    }
}
