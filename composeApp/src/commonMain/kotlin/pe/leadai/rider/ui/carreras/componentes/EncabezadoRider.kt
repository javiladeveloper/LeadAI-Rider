package pe.leadai.rider.ui.carreras.componentes

import androidx.compose.animation.animateColorAsState
import androidx.compose.ui.graphics.Color
import pe.leadai.rider.ui.comunes.BadgeEstado
import pe.leadai.rider.ui.comunes.EncabezadoMarca
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
import pe.leadai.rider.ui.tema.Formas
import pe.leadai.rider.ui.tema.centavosAnimados
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.clip
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
    /** Cuánto lleva ganado hoy (limpio) y cuántas carreras entregó. */
    carrerasHoy: Int = 0,
    gananciaHoyCentavos: Long = 0,
    cambiandoTurno: Boolean = false,
) {
    val colores = ColoresJala.actuales

    Column(modifier = modifier.fillMaxWidth()) {
        // EL MISMO DEGRADADO DE MARCA QUE EL LADO CLIENTE.
        //
        // El saludo era texto negro sobre gris: correcto, pero podía ser
        // cualquier app. Con el degradado carbón→amarillo —los dos colores
        // del logo— la pantalla se reconoce como Light Drive antes de leer
        // una palabra, y las dos mitades del producto se ven de la misma
        // familia.
        //
        // Se reusa `EncabezadoMarca` en vez de copiar el degradado: con dos
        // implementaciones, la próxima vez que cambie el color quedaría una
        // desactualizada.
        val (textoEstadoRider, colorEstadoRider) = colorDeEstadoRider(perfil.estado)
        EncabezadoMarca(
            titulo = if (nombreUsuario.isNotBlank()) nombreUsuario else "Motorizado",
            inicial = nombreUsuario.take(1).uppercase().ifBlank { "M" },
            subtitulo = perfil.distrito.takeIf { it.isNotBlank() }?.let { "📍 $it" },
            insignia = { BadgeEstado(texto = textoEstadoRider, color = colorEstadoRider) },
            // EL INTERRUPTOR VA DENTRO DEL DEGRADADO.
            //
            // Suelto abajo empujaba todo: entre saludo, turno y saldo, la
            // lista de solicitudes —que es a lo que el rider entra— arrancaba
            // pasada la mitad de la pantalla. Adentro no cuesta una fila
            // propia y sigue estando arriba de todo.
            pie = {
                InterruptorDeTurno(
                    enTurno = perfil.disponible,
                    cambiando = cambiandoTurno,
                    onCambiar = onCambiarTurno,
                )
            },
        )

        Spacer(Modifier.height(10.dp))

        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            // De acá para abajo vuelve el margen lateral: solo el degradado
            // sangra hasta el borde.

            // EL INTERRUPTOR DE TURNO. Va arriba de todo y siempre visible porque
            // determina si al rider le llegan avisos: el backend solo notifica a
            // quien está disponible, y antes no había forma de encenderlo desde
            // la app — ningún rider recibía nada.

            // LO GANADO HOY, arriba y sin entrar a ningún lado.
            //
            // Es el dato que hace que un motorizado deje la app abierta: saber que
            // lleva S/47 en el día. Enterrado en la pantalla de ganancias no lo
            // mira nadie.
            //
            // Solo cuando ya trabajó: un "S/0.00" a primera hora desanima en vez
            // de motivar.
            if (carrerasHoy > 0) {
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(Formas.card)
                        // Degradado SUTIL, no un bloque de color plano.
                        //
                        // Es la plata que ganó: merece verse como algo, pero un
                        // verde saturado a todo lo ancho competiría con el
                        // interruptor de turno, que es la acción de la pantalla.
                        // Un degradado que se apaga hacia la derecha destaca el
                        // monto sin robarse la atención.
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    colores.exito.copy(alpha = 0.16f),
                                    colores.exito.copy(alpha = 0.02f),
                                ),
                            ),
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(
                        // CUENTA hasta el valor nuevo: al entregar una carrera el
                        // rider ve el número subir, que es la recompensa del
                        // viaje. Saltando de golpe se lee como un parpadeo.
                        "💰 Hoy: ${centavosASoles(centavosAnimados(gananciaHoyCentavos))}",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                        ),
                        color = colores.exito,
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        if (carrerasHoy == 1) "1 carrera" else "$carrerasHoy carreras",
                        style = MaterialTheme.typography.labelMedium,
                        color = colores.tintaSecundaria,
                    )
                }
            }


            if (monedero != null) {
                Spacer(Modifier.height(16.dp))
                CardSaldoAmarilla(monedero = monedero, onRecargar = onRecargar)
            }
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

    // UNA SOLA FILA: monto a la izquierda, "Recargar" a la derecha.
    //
    // Antes era una tarjeta de tres pisos —rótulo, monto gigante y una franja
    // interna con el botón— que se comía un cuarto de la pantalla. El saldo
    // es un dato de CONSULTA: el rider lo mira de reojo y sigue. Lo que venía
    // abajo —las solicitudes— es a lo que entra.
    //
    // Sin saldo sí cambia de color: ahí deja de ser un dato y pasa a ser lo
    // que le impide trabajar.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = if (sinSaldo) colores.calor.copy(alpha = 0.12f) else colores.marcaAmarillo,
                shape = RoundedCornerShape(16.dp),
            )
            .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                centavosASoles(monedero.saldoCentavos),
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                ),
                color = if (sinSaldo) colores.calor else colores.marcaCarbon,
            )
            Text(
                if (sinSaldo) {
                    "Recargá para tomar carreras"
                } else {
                    "Te alcanza para ~${monedero.carrerasDisponibles} carreras"
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (sinSaldo) colores.calor else colores.marcaCarbon.copy(alpha = 0.75f),
            )
        }
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
        // Va DENTRO del degradado: los colores del tema —pensados para el
        // fondo de la página— acá se lavan. Carbón sólido contrasta tanto
        // contra el amarillo como contra el carbón del propio degradado.
        targetValue = if (enTurno) {
            colores.marcaCarbon.copy(alpha = 0.55f)
        } else {
            colores.marcaCarbon.copy(alpha = 0.38f)
        },
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
                    if (enTurno) colores.exito else Color.White.copy(alpha = 0.6f),
                    CircleShape,
                ),
        )
        Spacer(Modifier.size(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                if (enTurno) "En turno" else "Fuera de turno",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
            )
            Text(
                if (enTurno) {
                    "Te avisamos de las carreras nuevas"
                } else {
                    "Tocá para empezar a recibir carreras"
                },
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.75f),
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
