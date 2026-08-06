package pe.leadai.rider.ui.carreras

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import pe.leadai.rider.datos.CarreraDto
import pe.leadai.rider.datos.CarreraEntregadaDto
import pe.leadai.rider.datos.HistorialRiderResponseDto
import pe.leadai.rider.datos.MonederoDto
import pe.leadai.rider.datos.PaqueteMonederoDto
import pe.leadai.rider.ui.comunes.MapaEmbebido
import pe.leadai.rider.datos.PerfilMotorizadoDto
import pe.leadai.rider.datos.SesionRepositorio
import pe.leadai.rider.ui.comunes.AvisosGlobales
import pe.leadai.rider.ui.comunes.EstadoError
import pe.leadai.rider.ui.comunes.PantallaCargando
import pe.leadai.rider.ui.permisos.PermisosPantalla
import pe.leadai.rider.ui.carreras.componentes.CardCarrera
import pe.leadai.rider.ui.carreras.componentes.ChipMontoSobreMapa
import pe.leadai.rider.ui.carreras.componentes.EncabezadoRider
import pe.leadai.rider.ui.carreras.componentes.HojaCarreraActiva
import pe.leadai.rider.ui.carreras.componentes.CardSaldo
import pe.leadai.rider.ui.carreras.componentes.colorDeEstadoRider
import pe.leadai.rider.ui.comunes.BadgeEstado
import pe.leadai.rider.ui.comunes.BarraInferiorRider
import pe.leadai.rider.ui.comunes.SeccionRider
import pe.leadai.rider.ui.billetera.BilleteraPantalla
import pe.leadai.rider.ui.ganancias.GananciasPantalla
import pe.leadai.rider.ui.perfil.PerfilPantalla
import pe.leadai.rider.ui.tema.ColoresJala
import pe.leadai.rider.ui.tema.centavosASoles

/** Mismo ritmo de polling que la Cocina: el feed del rider se refresca solo. */
private const val INTERVALO_POLLING_MS = 15_000L

/** Base del mapa embebido — el mismo host de `ApiCliente` (default de prod). */
private const val URL_BASE_TRACKING = "https://api.leadai-pe.com"

/**
 * El contacto del lead como TELÉFONO marcable, o `null` si no lo es (los
 * leads del simulador/seeds usan ids tipo "demo-cocina-104" — un botón de
 * llamar ahí no haría nada). Acepta dígitos con separadores sueltos; exige
 * 8-15 dígitos (E.164) y devuelve solo los dígitos para `wa.me/` y `tel:+`.
 */
internal fun telefonoDeContacto(contacto: String?): String? {
    if (contacto.isNullOrBlank()) return null
    if (contacto.any { it.isLetter() }) return null
    val digitos = contacto.filter { it.isDigit() }
    return digitos.takeIf { it.length in 8..15 }
}

/**
 * La pantalla donde el rider trabaja: es la principal de la app.
 *
 * Tres estados, en orden de prioridad:
 * 1. **Carrera EN CURSO**: la card grande con el negocio donde recoge, la
 *    dirección de entrega y el botón "✅ Entregado".
 * 2. **Carreras disponibles**: pedidos "listos" de los negocios de su zona
 *    (match grueso por departamento — el GPS en vivo llega encima), con
 *    "Aceptar carrera" (el primero gana; un 409 avisa que voló).
 * 3. **Sala de espera**: sin carreras a la vista, el mensaje de siempre.
 *
 * Polling de 15s. Colecciona `AvisosGlobales` por su cuenta para el snackbar.
 */
@Composable
fun CarrerasPantalla(
    alCambiarDistrito: () -> Unit,
    alCerrarSesion: () -> Unit,
    /** "Cambiar a modo cliente" desde Perfil. */
    alCambiarModo: () -> Unit = {},
    viewModel: CarrerasViewModel = koinViewModel(),
) {
    val estado by viewModel.estado.collectAsState()
    val sesionRepositorio = koinInject<SesionRepositorio>()
    val avisosGlobales = koinInject<AvisosGlobales>()
    val sesion by sesionRepositorio.observar().collectAsState(initial = null)
    var confirmandoCierre by remember { mutableStateOf(false) }
    // Recarga del monedero: elige paquete → abre la página de pago (Culqi).
    var eligiendoPaquete by remember { mutableStateOf(false) }
    // Los permisos del sistema, a demanda desde "⚙️ Permisos".
    var viendoPermisos by remember { mutableStateOf(false) }
    var seccion by remember { mutableStateOf(SeccionRider.INICIO) }
    val abridorPago = LocalUriHandler.current

    LaunchedEffect(Unit) {
        viewModel.cargar()
        while (isActive) {
            delay(INTERVALO_POLLING_MS)
            viewModel.refrescarCarreras()
        }
    }

    // El pulso del GPS lo lleva un FOREGROUND SERVICE, no la pantalla: un
    // LaunchedEffect se SUSPENDE cuando el rider bloquea el teléfono o cambia
    // de app, y el cliente veía la moto congelada en el mapa mientras el
    // rider manejaba con el celular en el bolsillo. El service vive solo
    // mientras hay carrera: arranca al aceptar, para al entregar.
    val carreraEnCurso = estado.miCarrera
    LaunchedEffect(carreraEnCurso?.pedidoId) {
        if (carreraEnCurso != null) {
            iniciarServicioCarrera(carreraEnCurso.destinoTexto ?: carreraEnCurso.direccion.orEmpty())
        } else {
            detenerServicioCarrera()
        }
    }
    // Si el rider sale de la pantalla (cerrar sesión, cambiar de modo) el
    // service no debe quedar huérfano reportando para siempre.
    DisposableEffect(Unit) {
        onDispose { detenerServicioCarrera() }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        avisosGlobales.avisos.collect { mensaje -> snackbarHostState.showSnackbar(mensaje) }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) { Snackbar(it) } },
        bottomBar = {
            // Con una carrera en curso la barra DESAPARECE: el rider está
            // manejando y la pantalla tiene que ser el mapa y el botón de
            // avanzar, nada más. Volver a Ganancias a mitad de entrega no
            // tiene sentido y roba espacio al mapa.
            if (estado.miCarrera == null && estado.perfil != null) {
                BarraInferiorRider(
                    seleccionada = seccion,
                    onSeleccionar = { seccion = it },
                )
            }
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when {
                estado.cargando -> PantallaCargando()
                estado.perfil == null && estado.error != null ->
                    EstadoError(mensaje = estado.error!!, onReintentar = viewModel::cargar)
                estado.perfil != null -> {
                    // La carrera en curso MANDA sobre la pestaña elegida: si
                    // aceptó algo, lo que importa es llegar.
                    if (estado.miCarrera != null) {
                        ContenidoRider(
                            nombreUsuario = sesion?.usuarioNombre.orEmpty(),
                            perfil = estado.perfil!!,
                            carreras = estado.carreras,
                            miCarrera = estado.miCarrera,
                            historial = estado.historial,
                            monedero = estado.monedero,
                            accionEnCurso = estado.accionEnCurso,
                            onAceptar = viewModel::aceptar,
                            onEntregar = viewModel::entregar,
                            onRecogido = viewModel::marcarRecogido,
                            onRecargar = { eligiendoPaquete = true },
                            onCambiarDistrito = alCambiarDistrito,
                            onVerPermisos = { viendoPermisos = true },
                            onCerrarSesion = { confirmandoCierre = true },
                        )
                    } else {
                        // Cada pestaña tiene SU pantalla. Antes Billetera y
                        // Perfil caían en la misma vista de Inicio, así que
                        // tocarlas no hacía nada.
                        when (seccion) {
                            SeccionRider.INICIO -> ContenidoRider(
                                nombreUsuario = sesion?.usuarioNombre.orEmpty(),
                                perfil = estado.perfil!!,
                                carreras = estado.carreras,
                                miCarrera = estado.miCarrera,
                                historial = estado.historial,
                                monedero = estado.monedero,
                                accionEnCurso = estado.accionEnCurso,
                                onAceptar = viewModel::aceptar,
                                onEntregar = viewModel::entregar,
                                onRecogido = viewModel::marcarRecogido,
                                onRecargar = { eligiendoPaquete = true },
                                onCambiarDistrito = alCambiarDistrito,
                                onVerPermisos = { viendoPermisos = true },
                                onCerrarSesion = { confirmandoCierre = true },
                            )
                            SeccionRider.GANANCIAS -> GananciasPantalla(historial = estado.historial)
                            SeccionRider.BILLETERA -> BilleteraPantalla(
                                monedero = estado.monedero,
                                onRecargar = { eligiendoPaquete = true },
                                onElegirPaquete = { paqueteId ->
                                    val token = sesion?.token.orEmpty()
                                    abridorPago.openUri(
                                        "$URL_BASE_TRACKING/pago/rider?token=$token&paquete=$paqueteId",
                                    )
                                },
                            )
                            SeccionRider.PERFIL -> PerfilPantalla(
                                nombreUsuario = sesion?.usuarioNombre.orEmpty(),
                                emailUsuario = sesion?.usuarioEmail.orEmpty(),
                                perfil = estado.perfil!!,
                                onEditarPerfil = alCambiarDistrito,
                                onVerPermisos = { viendoPermisos = true },
                                onCambiarModo = alCambiarModo,
                                onCerrarSesion = { confirmandoCierre = true },
                            )
                        }
                    }
                }
                else -> EstadoError(mensaje = "No pudimos cargar tu perfil.", onReintentar = viewModel::cargar)
            }
        }
    }

    if (eligiendoPaquete) {
        DialogoRecargar(
            paquetes = estado.monedero?.paquetes.orEmpty(),
            onElegir = { paqueteId ->
                eligiendoPaquete = false
                // La página de pago vive en el backend (ahí corre el SDK de
                // Culqi que tokeniza la tarjeta). El token de sesión va en la
                // URL porque el navegador no comparte la sesión de la app.
                val token = sesion?.token.orEmpty()
                abridorPago.openUri("$URL_BASE_TRACKING/pago/rider?token=$token&paquete=$paqueteId")
            },
            onCancelar = { eligiendoPaquete = false },
        )
    }

    if (viendoPermisos) {
        // Como diálogo y no como ruta de navegación: el rider entra, toca
        // "Configurar", el sistema lo saca a Configuración y al volver sigue
        // en su pantalla de carreras sin haber perdido el hilo.
        Dialog(onDismissRequest = { viendoPermisos = false }) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
            ) {
                PermisosPantalla(alVolver = { viendoPermisos = false })
            }
        }
    }

    if (confirmandoCierre) {
        DialogoConfirmarCierreSesionRider(
            onConfirmar = {
                confirmandoCierre = false
                alCerrarSesion()
            },
            onCancelar = { confirmandoCierre = false },
        )
    }
}

@Composable
private fun ContenidoRider(
    nombreUsuario: String,
    perfil: PerfilMotorizadoDto,
    carreras: List<CarreraDto>,
    miCarrera: CarreraDto?,
    historial: HistorialRiderResponseDto?,
    monedero: MonederoDto?,
    accionEnCurso: String?,
    onAceptar: (CarreraDto) -> Unit,
    onEntregar: () -> Unit,
    onRecogido: () -> Unit,
    onRecargar: () -> Unit,
    onCambiarDistrito: () -> Unit,
    onVerPermisos: () -> Unit,
    onCerrarSesion: () -> Unit,
) {
    // 1) Carrera EN CURSO: pantalla enfocada en el viaje.
    if (miCarrera != null) {
        // El MAPA manda: ocupa toda la pantalla y la información flota encima.
        // El rider está manejando — lo único que necesita es ver por dónde va
        // y tocar un botón grande al llegar.
        val telefonoCliente = telefonoDeContacto(miCarrera.clienteContacto)
        val abridor = LocalUriHandler.current

        Box(modifier = Modifier.fillMaxSize()) {
            MapaEmbebido(
                url = "$URL_BASE_TRACKING/track/${miCarrera.pedidoId}?embebido=1",
                modifier = Modifier.fillMaxSize(),
            )

            // Cuánto gana, flotando arriba a la derecha.
            ChipMontoSobreMapa(
                montoCentavos = miCarrera.montoOfrecido,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp),
            )

            HojaCarreraActiva(
                carrera = miCarrera,
                accionEnCurso = accionEnCurso != null,
                onRecogido = onRecogido,
                onEntregar = onEntregar,
                onWhatsApp = { abridor.openUri("https://wa.me/$it") },
                onLlamar = { abridor.openUri("tel:+$it") },
                telefonoCliente = telefonoCliente,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
        return
    }

    // El FEED, con la estructura del diseño: saludo + saldo arriba (en el
    // encabezado), y debajo la lista de solicitudes cercanas. El historial y
    // el perfil ya NO viven acá — tienen su propia pestaña.
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "encabezado") {
            Spacer(Modifier.height(8.dp))
            EncabezadoRider(
                nombreUsuario = nombreUsuario,
                perfil = perfil,
                monedero = monedero,
                onRecargar = onRecargar,
            )
        }

        item(key = "titulo-solicitudes") {
            Spacer(Modifier.height(4.dp))
            Text(
                "📋 Solicitudes cercanas",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        if (carreras.isEmpty()) {
            item(key = "sin-carreras") {
                SalaDeEspera()
            }
        } else {
            items(carreras, key = { it.pedidoId }) { carrera ->
                CardCarrera(
                    carrera = carrera,
                    aceptando = accionEnCurso == carrera.pedidoId,
                    habilitado = accionEnCurso == null,
                    onAceptar = { onAceptar(carrera) },
                )
            }
        }

        item(key = "fin") { Spacer(Modifier.height(16.dp)) }
    }
}

/** Sin carreras a la vista: el mensaje de siempre, con algo de personalidad. */
@Composable
private fun SalaDeEspera() {
    val colores = ColoresJala.actuales
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("🛵", style = MaterialTheme.typography.displayLarge)
        Spacer(Modifier.height(12.dp))
        Text(
            "Sin carreras por ahora",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Cuando alguien pida cerca tuyo, aparece acá solito. " +
                "Te avisamos aunque tengas la app cerrada 📲",
            style = MaterialTheme.typography.bodyMedium,
            color = colores.tintaSecundaria,
            textAlign = TextAlign.Center,
        )
    }
}

/** Un dato del resumen de hoy: número grande + etiqueta chiquita. */
@Composable
private fun DatoDeHoy(valor: String, etiqueta: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            valor,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Text(
            etiqueta,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
        )
    }
}

/** Una entrega pasada del historial: negocio, dirección, monto y sus km. */
@Composable
private fun FilaEntrega(entrega: CarreraEntregadaDto) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "🍽️ ${entrega.negocio}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (!entrega.direccion.isNullOrBlank()) {
                    Text(
                        entrega.direccion,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    centavosASoles(entrega.totalCentavos),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (entrega.km != null) {
                    Text(
                        "🛣️ ${entrega.km} km",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * Elegir cuánto saldo comprar (2026-07-24). Al elegir se abre la página de
 * pago del backend, donde el SDK de Culqi tokeniza la tarjeta.
 */
@Composable
private fun DialogoRecargar(
    paquetes: List<PaqueteMonederoDto>,
    onElegir: (String) -> Unit,
    onCancelar: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text("💳 Recargar saldo") },
        text = {
            Column {
                Text(
                    "Cada carrera que aceptes cuesta S/1. Elige cuánto quieres recargar:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                paquetes.forEach { paquete ->
                    OutlinedButton(
                        onClick = { onElegir(paquete.id) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text(
                            "S/${paquete.soles}  ·  ${paquete.soles} carreras",
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onCancelar) { Text("Cancelar") } },
    )
}

@Composable
private fun DialogoConfirmarCierreSesionRider(onConfirmar: () -> Unit, onCancelar: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text("¿Cerrar sesión?") },
        text = { Text("Vas a salir de tu cuenta en este dispositivo.") },
        confirmButton = {
            TextButton(onClick = onConfirmar) {
                Text("Cerrar sesión", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onCancelar) {
                Text("Cancelar")
            }
        },
    )
}
