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
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when {
                estado.cargando -> PantallaCargando()
                estado.perfil == null && estado.error != null ->
                    EstadoError(mensaje = estado.error!!, onReintentar = viewModel::cargar)
                estado.perfil != null -> ContenidoRider(
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
        // El mapa MANDA (feedback de Jonathan 2026-07-24): a SANGRE COMPLETA
        // — sin márgenes laterales ni esquinas, ocupando todo el alto libre;
        // los datos van compactos abajo — como Google Maps en navegación.
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                // El título dice en qué tramo va y con las palabras del tipo:
                // a un pasajero no se lo "recoge", se lo pasa a buscar.
                tituloTramo(miCarrera),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 10.dp),
            )

            // El MISMO mapa en vivo que ve el cliente; en modo embebido la
            // cámara encuadra la zona y luego SIGUE a la moto (modo rider).
            MapaEmbebido(
                url = "$URL_BASE_TRACKING/track/${miCarrera.pedidoId}?embebido=1",
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
            Spacer(Modifier.height(10.dp))

            val telefonoCliente = telefonoDeContacto(miCarrera.clienteContacto)
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        "🍽️ ${miCarrera.negocio} · ${centavosASoles(miCarrera.totalCentavos)}",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        "🏠 ${miCarrera.direccion ?: "entrega por confirmar con el negocio"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (!miCarrera.clienteNombre.isNullOrBlank() || telefonoCliente != null) {
                        Text(
                            listOfNotNull(
                                miCarrera.clienteNombre?.takeIf { it.isNotBlank() }?.let { "👤 $it" },
                                telefonoCliente?.let { "📱 +$it" },
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    // ENCOMIENDA CON COMPRA: la plata que ADELANTA para
                    // comprar, en su propia línea y en ámbar. Nunca sumada al
                    // flete de arriba.
                    if (requiereCompra(miCarrera)) {
                        Text(
                            "💵 Llevas ${centavosASoles(miCarrera.montoCompraEstimado ?: 0)} para la compra",
                            style = MaterialTheme.typography.bodyMedium,
                            color = ColoresJala.actuales.espera,
                        )
                    }
                }
            }

            // Contacto directo con el cliente (solo si el contacto es un
            // TELÉFONO — los leads de pruebas usan ids). Sin botón "Navegar":
            // el mapa de arriba YA dibuja la mejor ruta y la recalcula solo
            // cuando el rider se desvía (decisión de Jonathan 2026-07-24).
            if (telefonoCliente != null) {
                val abridor = LocalUriHandler.current
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = { abridor.openUri("https://wa.me/$telefonoCliente") },
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Text("💬 WhatsApp", style = MaterialTheme.typography.labelLarge)
                    }
                    OutlinedButton(
                        onClick = { abridor.openUri("tel:+$telefonoCliente") },
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Text("📞 Llamar", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            // DOS TRAMOS (2026-07-24): primero va al LOCAL a recoger; recién
            // ahí el botón pasa a "Entregado".
            Button(
                onClick = if (miCarrera.recogido) onEntregar else onRecogido,
                enabled = accionEnCurso == null,
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp).height(52.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                if (accionEnCurso != null) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text(
                        when {
                            miCarrera.recogido -> "✅ Entregado"
                            miCarrera.tipo == "pasajero" -> "🚕 Ya subió"
                            // Lo que cambia el texto no es el tipo sino si hay
                            // que comprar: "Ya compré" solo si adelantó plata.
                            requiereCompra(miCarrera) -> "🛍️ Ya compré"
                            else -> "📦 Ya recogí el pedido"
                        },
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
        return
    }

    // 2) y 3): lista de carreras disponibles, o la sala de espera de siempre.
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Spacer(Modifier.height(24.dp))
        // El saludo y el badge NO comparten fila: "Pendiente de verificación"
        // es texto largo y con un nombre normal los dos quedaban apretados
        // contra los bordes. El badge va debajo, con su ancho natural.
        Text(
            text = if (nombreUsuario.isNotBlank()) "Hola, $nombreUsuario 🛵" else "Hola 🛵",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = perfil.distrito,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        BadgeEstadoMotorizado(perfil.estado)

        Spacer(Modifier.height(16.dp))

        // Monedero prepago (2026-07-24): cada carrera cuesta S/1. Sin saldo
        // resalta en coral — es lo que le impide trabajar.
        if (monedero != null) {
            val sinSaldo = monedero.carrerasDisponibles <= 0
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (sinSaldo) {
                        ColoresJala.actuales.calor.copy(alpha = 0.12f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLowest
                    },
                ),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text(
                            "💳 Mi saldo",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            centavosASoles(monedero.saldoCentavos),
                            style = MaterialTheme.typography.titleLarge,
                            color = if (sinSaldo) ColoresJala.actuales.calor else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f).padding(start = 12.dp),
                        horizontalAlignment = Alignment.End,
                    ) {
                        Text(
                            if (sinSaldo) {
                                "Recarga para tomar carreras"
                            } else {
                                "Te alcanza para ${monedero.carrerasDisponibles} carreras"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (sinSaldo) ColoresJala.actuales.calor else MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.End,
                        )
                        Spacer(Modifier.height(6.dp))
                        Button(
                            onClick = onRecargar,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.height(36.dp),
                        ) {
                            Text("Recargar", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        // Resumen de HOY (pedido de Jonathan 2026-07-24): carreras, km reales
        // del odómetro de GPS y total entregado.
        val hoy = historial?.hoy
        if (hoy != null && hoy.carreras > 0) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    DatoDeHoy(valor = "${hoy.carreras}", etiqueta = if (hoy.carreras == 1) "carrera" else "carreras")
                    DatoDeHoy(valor = "${hoy.km} km", etiqueta = "recorridos")
                    DatoDeHoy(valor = centavosASoles(hoy.totalCentavos), etiqueta = "entregado")
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (carreras.isEmpty()) {
                item(key = "sin-carreras") {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(text = "🛵", style = MaterialTheme.typography.displayMedium)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "Sin carreras por ahora",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Cuando un negocio de tu zona tenga un pedido listo, " +
                                "aparecerá acá solito. Las carreras llegan según dónde estés 📍",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            } else {
                item(key = "titulo-disponibles") {
                    Text(
                        "🛎️ Carreras disponibles",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                items(carreras, key = { it.pedidoId }) { carrera ->
                    CardCarrera(
                        carrera = carrera,
                        aceptando = accionEnCurso == carrera.pedidoId,
                        habilitado = accionEnCurso == null,
                        onAceptar = { onAceptar(carrera) },
                    )
                }
            }

            // Historial: sus últimas entregas con los km de cada una.
            val entregas = historial?.carreras.orEmpty()
            if (entregas.isNotEmpty()) {
                item(key = "titulo-historial") {
                    Text(
                        "📦 Mis últimas entregas",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                items(entregas.take(10), key = { "entrega-${it.pedidoId}" }) { entrega ->
                    FilaEntrega(entrega)
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = onCambiarDistrito,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Text("✏️ Editar mi perfil", style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.height(8.dp))
        // El permiso de ubicación "todo el tiempo" no se puede pedir por
        // diálogo desde Android 11: hay que mandar al rider a Configuración,
        // y conviene que pueda volver ahí cuando quiera (si lo negó una vez,
        // el sistema ya no vuelve a preguntar).
        OutlinedButton(
            onClick = onVerPermisos,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Text("⚙️ Permisos", style = MaterialTheme.typography.labelLarge)
        }
        TextButton(
            onClick = onCerrarSesion,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Text("Cerrar sesión", style = MaterialTheme.typography.bodyMedium, color = ColoresJala.actuales.calor)
        }
        Spacer(Modifier.height(8.dp))
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

/** Una carrera disponible: el tipo, de dónde a dónde, cuánto gana y el botón que gana el primero. */
@Composable
private fun CardCarrera(
    carrera: CarreraDto,
    aceptando: Boolean,
    habilitado: Boolean,
    onAceptar: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            // Tipo + lo que GANA (el flete). Nunca el total con la compra.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    etiquetaTipo(carrera.tipo),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    centavosASoles(carrera.montoOfrecido),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            Spacer(Modifier.height(8.dp))
            Text(
                "📍 ${carrera.origenTexto ?: carrera.negocio}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            val destino = carrera.destinoTexto ?: carrera.direccion
            if (!destino.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    "🏁 $destino",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (carrera.notas.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "📝 ${carrera.notas}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // ENCOMIENDA CON COMPRA: cuánta plata tiene que llevar encima. En
            // ámbar y SEPARADO de lo que gana — un total combinado
            // (S/8 + S/60 = S/68) se lee como una carrera muy rentable y no lo es.
            if (requiereCompra(carrera)) {
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = ColoresJala.actuales.espera.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(10.dp),
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "💵 Llevas ${centavosASoles(carrera.montoCompraEstimado ?: 0)} para la compra",
                        style = MaterialTheme.typography.bodySmall,
                        color = ColoresJala.actuales.espera,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    "El cliente te lo devuelve al entregar",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Despacho por proximidad: el feed llega ordenado por cercanía.
            if (carrera.kmAlNegocio != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "🛵 A ${carrera.kmAlNegocio} km de ti",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onAceptar,
                enabled = habilitado,
                modifier = Modifier.fillMaxWidth().height(44.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                if (aceptando) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Aceptar carrera", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

/** "Pendiente de verificación" ámbar / "Verificado ✓" teal / "Bloqueado" coral — mismos tonos que el resto de la app (regla de oro Brand Harmony). */
@Composable
private fun BadgeEstadoMotorizado(estado: String) {
    val (texto, color) = when (estado) {
        "verificado" -> "Verificado ✓" to MaterialTheme.colorScheme.primary
        "bloqueado" -> "Bloqueado" to ColoresJala.actuales.calor
        else -> "Pendiente de verificación" to ColoresJala.actuales.espera
    }
    Box(
        modifier = Modifier
            .background(color = color.copy(alpha = 0.14f), shape = RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(text = texto, style = MaterialTheme.typography.labelMedium, color = color)
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
