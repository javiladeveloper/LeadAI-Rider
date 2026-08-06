package pe.leadai.rider.ui.cliente

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import pe.leadai.rider.datos.CarreraClienteDto
import pe.leadai.rider.ui.carreras.telefonoDeContacto
import pe.leadai.rider.ui.comunes.AvisosGlobales
import pe.leadai.rider.ui.comunes.MapaEmbebido
import pe.leadai.rider.ui.comunes.PantallaCargando
import pe.leadai.rider.ui.cliente.componentes.CardMontoCompra
import pe.leadai.rider.ui.cliente.componentes.CardRecorrido
import pe.leadai.rider.ui.cliente.componentes.CardTarifa
import pe.leadai.rider.ui.comunes.BannerError
import pe.leadai.rider.ui.comunes.BotonAcento
import pe.leadai.rider.ui.comunes.CampoJala
import pe.leadai.rider.ui.comunes.SelectorDos
import pe.leadai.rider.ui.tema.ColoresJala
import pe.leadai.rider.ui.tema.centavosASoles
import pe.leadai.rider.ui.tema.epochMsAhora
import pe.leadai.rider.ui.tema.epochMsDesdeIso

/** Cada cuánto se refresca el estado de la carrera. Mismo ritmo que el pool del rider. */
private const val INTERVALO_POLLING_MS = 10_000L

/** Base del mapa embebido — el mismo host de `ApiCliente` (default de prod). */
private const val URL_BASE_TRACKING = "https://api.leadai-pe.com"

/**
 * Modo CLIENTE: pedir una moto y seguir el viaje.
 *
 * Tres estados EXCLUYENTES, en orden de prioridad (igual que `CarrerasPantalla`):
 * 1. **Con carrera activa** → seguimiento (mapa + estado + datos del rider).
 * 2. **Sin carrera** → el formulario para pedir.
 * 3. **Cargando** → spinner.
 *
 * Polling de 10s: hoy el cliente se entera por acá de que un rider la tomó
 * (el push al cliente todavía no existe).
 */
@Composable
fun ClientePantalla(
    alCambiarModo: () -> Unit,
    alCerrarSesion: () -> Unit,
    viewModel: ClienteViewModel = koinViewModel(),
) {
    val estado by viewModel.estado.collectAsState()
    val avisosGlobales = koinInject<AvisosGlobales>()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.cargar()
        while (isActive) {
            delay(INTERVALO_POLLING_MS)
            viewModel.refrescar()
        }
    }
    LaunchedEffect(Unit) {
        avisosGlobales.avisos.collect { mensaje -> snackbarHostState.showSnackbar(mensaje) }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) { Snackbar(it) } },
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            val carrera = estado.miCarrera
            when {
                // 1) Carrera activa: manda sobre todo lo demás.
                carrera != null -> SeguimientoCarrera(
                    carrera = carrera,
                    onCancelar = viewModel::cancelar,
                )
                // 2) Sin carrera: el formulario para pedir.
                !estado.cargando -> FormularioPedir(
                    estado = estado,
                    onTipo = viewModel::elegirTipo,
                    onOrigen = viewModel::cambiarOrigen,
                    onUsarMiUbicacion = viewModel::usarMiUbicacion,
                    onDestino = viewModel::cambiarDestino,
                    onMonto = viewModel::cambiarMonto,
                    onMontoCompra = viewModel::cambiarMontoCompra,
                    onNotas = viewModel::cambiarNotas,
                    onContacto = viewModel::cambiarContacto,
                    onSugerir = viewModel::pedirSugerencia,
                    onPedir = viewModel::pedir,
                    onCambiarModo = alCambiarModo,
                    onCerrarSesion = alCerrarSesion,
                )
                // 3) Cargando.
                else -> PantallaCargando()
            }
        }
    }
}

// ── Formulario: pedir la moto ────────────────────────────────────────────

@Composable
private fun FormularioPedir(
    estado: ClienteUiState,
    onTipo: (String) -> Unit,
    onOrigen: (String) -> Unit,
    onUsarMiUbicacion: () -> Unit,
    onDestino: (String) -> Unit,
    onMonto: (String) -> Unit,
    onMontoCompra: (String) -> Unit,
    onNotas: (String) -> Unit,
    onContacto: (String) -> Unit,
    onSugerir: () -> Unit,
    onPedir: () -> Unit,
    onCambiarModo: () -> Unit,
    onCerrarSesion: () -> Unit,
) {
    val colores = ColoresJala.actuales

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(4.dp))

        // Qué necesita: llevar a alguien, o llevar/traer algo.
        SelectorDos(
            opciones = listOf(
                TIPO_PASAJERO to "🚕 Pasajero",
                TIPO_ENCOMIENDA to "📦 Encomienda",
            ),
            seleccionada = estado.tipo,
            onSeleccionar = onTipo,
        )

        CardRecorrido(
            origen = estado.origen,
            destino = estado.destino,
            onOrigenCambia = onOrigen,
            onDestinoCambia = { onDestino(it); onSugerir() },
            onUsarMiUbicacion = onUsarMiUbicacion,
        )

        CardTarifa(
            monto = estado.monto,
            onMontoCambia = onMonto,
            montoSugeridoCentavos = estado.montoSugerido,
        )

        // Solo en encomienda: cuánta plata adelanta el rider en la compra.
        if (estado.tipo == TIPO_ENCOMIENDA) {
            CardMontoCompra(monto = estado.montoCompra, onMontoCambia = onMontoCompra)
        }

        CampoJala(
            valor = estado.contacto,
            onCambio = onContacto,
            etiqueta = "Tu celular",
            placeholder = "987 654 321",
            tipoTeclado = KeyboardType.Phone,
            prefijo = "+51",
        )

        CampoJala(
            valor = estado.notas,
            onCambio = onNotas,
            etiqueta = "Notas para el motorizado",
            placeholder = "Ej: tocar timbre, casa amarilla",
            maxLineas = 3,
        )

        val error = estado.error
        if (error != null) {
            BannerError(error)
        }

        Spacer(Modifier.height(4.dp))

        BotonAcento(
            texto = "PEDIR JALA  →",
            onClick = onPedir,
            cargando = estado.pidiendo,
        )

        Spacer(Modifier.height(8.dp))

        TextButton(
            onClick = onCambiarModo,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Text(
                "🏍️ Quiero manejar",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        TextButton(
            onClick = onCerrarSesion,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Text(
                "Cerrar sesión",
                style = MaterialTheme.typography.labelSmall,
                color = colores.tintaSecundaria,
            )
        }

        Spacer(Modifier.height(16.dp))
    }
}

/** Un tipo de carrera: elegido en teal, el otro en contorno. */
@Composable
private fun BotonTipo(
    texto: String,
    elegido: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (elegido) {
        Button(
            onClick = onClick,
            modifier = modifier.height(52.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Text(texto, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier.height(52.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Text(texto, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
        }
    }
}

// ── Seguimiento: la carrera ya pedida ────────────────────────────────────

/**
 * Qué ve el cliente una vez que pidió:
 * - `disponible`: todavía nadie la tomó — se puede cancelar.
 * - `aceptada`/`recogida`: el rider está en camino — el mapa en vivo, sus
 *   datos y los botones para contactarlo. SIN cancelar: ya salió por él.
 */
@Composable
private fun SeguimientoCarrera(
    carrera: CarreraClienteDto,
    onCancelar: () -> Unit,
) {
    val enCamino = carrera.estado == "aceptada" || carrera.estado == "recogida"

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            if (enCamino) tituloEnCamino(carrera) else "🔍 Buscando motorizado…",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 10.dp),
        )

        if (enCamino) {
            // El MISMO mapa en vivo que usa el rider, a sangre completa.
            MapaEmbebido(
                url = "$URL_BASE_TRACKING/track/${carrera.id}?embebido=1",
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
            Spacer(Modifier.height(10.dp))
        } else {
            EsperandoRider(carrera, modifier = Modifier.weight(1f))
        }

        // Datos del viaje. El flete y la compra, en LÍNEAS SEPARADAS.
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            ),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    "📍 ${carrera.origenTexto}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "🏁 ${carrera.destinoTexto}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // El FLETE: lo que le paga por el servicio.
                Text(
                    "🛵 Flete: ${centavosASoles(carrera.montoOfrecido)}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                // LA COMPRA: plata que el rider ADELANTÓ y hay que devolverle,
                // en ámbar y en su propia línea. Jamás sumada al flete — un
                // "total" de S/68 escondería que el servicio cuesta S/8.
                val compra = carrera.montoCompraEstimado
                if (compra != null && compra > 0) {
                    Text(
                        "💵 Llevá ${centavosASoles(compra)} para pagarle la compra",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ColoresJala.actuales.espera,
                    )
                    Text(
                        "Es aparte del flete: el motorizado puso esa plata.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (carrera.notas.isNotBlank()) {
                    Text(
                        "📝 ${carrera.notas}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (enCamino) {
            DatosDelRider(carrera)
        } else {
            // Solo mientras nadie la tomó: después ya salió a buscarlo.
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = onCancelar,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(48.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text(
                    "Cancelar",
                    style = MaterialTheme.typography.labelLarge,
                    color = ColoresJala.actuales.calor,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

/** Mientras nadie la toma: cuánto lleva esperando y qué puede hacer. */
@Composable
private fun EsperandoRider(carrera: CarreraClienteDto, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(36.dp), strokeWidth = 3.dp)
        Spacer(Modifier.height(16.dp))
        Text(
            esperaDesde(carrera.creadoEn),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(8.dp))
        // El monto es una sugerencia editable: si nadie la toma, subirlo es la
        // palanca que tiene el cliente.
        Text(
            "Si nadie la toma en unos minutos, probá ofreciendo un poco más",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** El rider que la tomó: quién es, en qué anda y cómo se lo contacta. */
@Composable
private fun DatosDelRider(carrera: CarreraClienteDto) {
    val telefono = telefonoDeContacto(carrera.riderTelefono)

    Spacer(Modifier.height(8.dp))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .background(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                shape = RoundedCornerShape(16.dp),
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                "🏍️ ${carrera.riderNombre ?: "Tu motorizado"}",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            val vehiculo = listOfNotNull(
                carrera.riderVehiculo?.takeIf { it.isNotBlank() },
                carrera.riderPlaca?.takeIf { it.isNotBlank() },
            ).joinToString(" · ")
            if (vehiculo.isNotBlank()) {
                Text(
                    vehiculo,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (telefono != null) {
        val abridor = LocalUriHandler.current
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = { abridor.openUri("https://wa.me/$telefono") },
                modifier = Modifier.weight(1f).height(44.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text("💬 WhatsApp", style = MaterialTheme.typography.labelLarge)
            }
            OutlinedButton(
                onClick = { abridor.openUri("tel:+$telefono") },
                modifier = Modifier.weight(1f).height(44.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text("📞 Llamar", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** El título dice en qué tramo va, con las palabras del tipo de carrera. */
private fun tituloEnCamino(carrera: CarreraClienteDto): String = when {
    carrera.estado == "recogida" && carrera.tipo == TIPO_PASAJERO -> "🚕 En viaje"
    carrera.estado == "recogida" -> "📦 Ya lo tiene, va en camino"
    carrera.tipo == TIPO_PASAJERO -> "🛵 Va a buscarte"
    else -> "🛵 Va en camino"
}

/**
 * Cuánto lleva esperando, en palabras. Si `creadoEn` no viene (o no parsea),
 * se dice lo genérico antes que arriesgar una cuenta mentirosa.
 */
private fun esperaDesde(creadoEn: String): String {
    if (creadoEn.isBlank()) return "Esperando que alguien la tome"
    val minutos = try {
        (epochMsAhora() - epochMsDesdeIso(creadoEn)) / 60_000
    } catch (e: Exception) {
        return "Esperando que alguien la tome"
    }
    return when {
        minutos < 1 -> "Recién la pediste"
        minutos == 1L -> "Esperando hace 1 minuto"
        minutos < 60 -> "Esperando hace $minutos minutos"
        else -> "Esperando hace ${minutos / 60} h"
    }
}
