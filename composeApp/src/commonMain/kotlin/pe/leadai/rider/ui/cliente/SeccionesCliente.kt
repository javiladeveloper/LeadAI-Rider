package pe.leadai.rider.ui.cliente

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import pe.leadai.rider.datos.CarreraClienteDto
import pe.leadai.rider.datos.PerfilPersonaDto
import pe.leadai.rider.ui.billetera.fechaLegible
import pe.leadai.rider.ui.comunes.BotonPrincipal
import pe.leadai.rider.ui.comunes.CampoJala
import pe.leadai.rider.ui.comunes.CardJala
import pe.leadai.rider.ui.comunes.PieDeVersion
import pe.leadai.rider.ui.tema.ColoresJala
import pe.leadai.rider.ui.tema.centavosASoles

/**
 * "Viajes": lo que el cliente ya pidió antes.
 *
 * Sirve sobre todo para una pregunta concreta —"¿cuánto pagué la vez
 * pasada?"— porque acá el precio no lo fija la app: se acuerda cada vez, y el
 * historial es la única referencia que tiene el cliente para saber qué
 * ofrecer.
 */
@Composable
fun ViajesCliente(historial: List<CarreraClienteDto>) {
    val colores = ColoresJala.actuales

    if (historial.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("🧾", style = MaterialTheme.typography.displaySmall)
            Spacer(Modifier.height(12.dp))
            Text(
                "Todavía no pediste ninguna",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Cuando pidas tu primera moto, la vas a ver acá.",
                style = MaterialTheme.typography.bodyMedium,
                color = colores.tintaSecundaria,
                textAlign = TextAlign.Center,
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Spacer(Modifier.height(16.dp))
            Text(
                "Tus viajes",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                ),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        items(historial) { carrera -> CardViaje(carrera) }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

/** Un viaje del historial: qué fue, de dónde a dónde, cuánto y cómo terminó. */
@Composable
private fun CardViaje(carrera: CarreraClienteDto) {
    val colores = ColoresJala.actuales

    CardJala(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    tituloDelViaje(carrera.tipo),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                // La fecha del cierre; si se canceló antes de entregar, la de
                // cuando se pidió.
                val cuando = fechaLegible(carrera.entregadoEn ?: carrera.creadoEn)
                if (cuando != null) {
                    Text(
                        cuando,
                        style = MaterialTheme.typography.labelSmall,
                        color = colores.tintaSecundaria,
                    )
                }
            }
            Text(
                centavosASoles(carrera.montoOfrecido),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                ),
                // Verde solo si llegó a destino: una cancelada no es plata que
                // el cliente haya gastado.
                color = if (carrera.estado == "entregada") colores.exito else colores.tintaSecundaria,
            )
        }

        Spacer(Modifier.height(8.dp))

        if (carrera.origenTexto.isNotBlank()) {
            Text(
                "Desde ${carrera.origenTexto}",
                style = MaterialTheme.typography.bodyMedium,
                color = colores.tintaSecundaria,
            )
        }
        if (carrera.destinoTexto.isNotBlank()) {
            Text(
                "Hasta ${carrera.destinoTexto}",
                style = MaterialTheme.typography.bodyMedium,
                color = colores.tintaSecundaria,
            )
        }

        // Solo se avisa lo que NO salió como esperaba: marcar "entregada" en
        // una lista donde casi todo se entregó es ruido.
        if (carrera.estado != "entregada") {
            Spacer(Modifier.height(6.dp))
            Text(
                if (carrera.estado == "cancelada") "Cancelada" else "Nadie la tomó a tiempo",
                style = MaterialTheme.typography.labelSmall,
                color = colores.calor,
            )
        }
    }
}

/** El tipo, en las palabras del cliente y no en las de la base de datos. */
private fun tituloDelViaje(tipo: String): String = when (tipo) {
    TIPO_PASAJERO -> "🚕 Viaje"
    else -> "📦 Encomienda"
}

/**
 * "Perfil" del cliente: desde acá cambia a modo conductor y cierra sesión.
 *
 * Estas dos acciones vivían al fondo del formulario, compitiendo con PEDIR
 * JALA. Acá no estorban, y el cambio de modo queda donde uno lo busca.
 */
@Composable
fun PerfilCliente(
    onCambiarModo: () -> Unit,
    onCerrarSesion: () -> Unit,
    perfil: PerfilPersonaDto? = null,
    guardando: Boolean = false,
    onGuardar: (nombre: String?, telefono: String?, direccion: String?) -> Unit = { _, _, _ -> },
) {
    val colores = ColoresJala.actuales
    // `key(perfil)`: cuando el perfil llega de la red hay que re-sembrar los
    // campos. Sin esto quedarían vacíos aunque el dato ya esté cargado.
    var nombre by remember(perfil) { mutableStateOf(perfil?.nombre.orEmpty()) }
    var telefono by remember(perfil) { mutableStateOf(perfil?.telefono.orEmpty()) }
    var direccion by remember(perfil) { mutableStateOf(perfil?.direccionHabitual.orEmpty()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(16.dp))

        Text(
            "Mi cuenta",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.ExtraBold,
            ),
            color = MaterialTheme.colorScheme.onSurface,
        )

        // Mis datos. El celular vive acá y NO en cada pedido: es el dato con
        // el que el motorizado consulta un pedido específico.
        CardJala(modifier = Modifier.fillMaxWidth()) {
            Text(
                "Mis datos",
                style = MaterialTheme.typography.labelLarge,
                color = colores.tintaSecundaria,
            )
            Spacer(Modifier.height(12.dp))
            CampoJala(
                valor = nombre,
                onCambio = { nombre = it },
                etiqueta = "Tu nombre",
                placeholder = "Cómo te va a llamar el motorizado",
            )
            Spacer(Modifier.height(12.dp))
            CampoJala(
                valor = telefono,
                onCambio = { telefono = it },
                etiqueta = "Tu celular",
                placeholder = "987 654 321",
                tipoTeclado = KeyboardType.Phone,
                prefijo = "+51",
            )
            Spacer(Modifier.height(12.dp))
            CampoJala(
                valor = direccion,
                onCambio = { direccion = it },
                etiqueta = "Mi dirección (opcional)",
                placeholder = "Av. Bolognesi 500, Tacna",
            )
            Spacer(Modifier.height(16.dp))
            BotonPrincipal(
                texto = if (guardando) "GUARDANDO…" else "GUARDAR",
                onClick = { onGuardar(nombre, telefono, direccion) },
                habilitado = !guardando,
            )
        }

        // El cambio de modo es la acción principal de esta pestaña: quien
        // entra acá suele venir a manejar.
        CardJala(modifier = Modifier.fillMaxWidth()) {
            Text(
                "🏍️ Quiero manejar",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                ),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth().clickable(onClick = onCambiarModo),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Pasá a modo conductor y empezá a recibir carreras.",
                style = MaterialTheme.typography.bodyMedium,
                color = colores.tintaSecundaria,
            )
        }

        CardJala(modifier = Modifier.fillMaxWidth()) {
            Text(
                "Cerrar sesión",
                style = MaterialTheme.typography.titleMedium,
                color = colores.calor,
                modifier = Modifier.fillMaxWidth().clickable(onClick = onCerrarSesion),
            )
        }

        Spacer(Modifier.height(16.dp))
    }

    PieDeVersion()
}

/**
 * "Cargá tu celular en Perfil" — cuando todavía no lo guardó.
 *
 * Sin celular el rider no tiene cómo consultar un pedido específico, que es
 * justo lo que pidieron los motorizados. Se avisa acá en vez de volver a
 * poner el campo en el formulario: el dato es del perfil, no del pedido.
 */
@Composable
fun AvisoFaltaCelular() {
    val colores = ColoresJala.actuales
    CardJala(
        modifier = Modifier.fillMaxWidth(),
        color = colores.esperaFondo,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("📱", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(0.dp))
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(
                    "Falta tu celular",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "Cargalo en Perfil para que el motorizado pueda escribirte.",
                    style = MaterialTheme.typography.labelSmall,
                    color = colores.tintaSecundaria,
                )
            }
        }
    }
}
