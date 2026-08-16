package pe.leadai.rider.ui.cliente

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import pe.leadai.rider.datos.Rutas
import pe.leadai.rider.ui.comunes.MapaQueSeMide
import pe.leadai.rider.ui.comunes.ChatCarrera
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.ui.draw.clip
import pe.leadai.rider.ui.tema.Formas
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
import pe.leadai.rider.ui.comunes.ManejarAtras
import pe.leadai.rider.ui.comunes.MapaEmbebido
import pe.leadai.rider.ui.comunes.PantallaCargando
import pe.leadai.rider.ui.cliente.componentes.CardMontoCompra
import pe.leadai.rider.ui.cliente.componentes.BuscadorRuta
import pe.leadai.rider.ui.cliente.componentes.OfertasRecibidas
import pe.leadai.rider.ui.cliente.componentes.PopupCalificar
import pe.leadai.rider.ui.tema.Movimiento
import pe.leadai.rider.ui.tema.recordarInteraccion
import pe.leadai.rider.ui.tema.toqueVivo
import pe.leadai.rider.ui.tema.AparecerCard
import pe.leadai.rider.ui.cliente.componentes.EstadoBusqueda
import pe.leadai.rider.ui.cliente.componentes.MapaDeLaRuta
import pe.leadai.rider.ui.cliente.componentes.DialogoMotivoCancelar
import pe.leadai.rider.ui.cliente.componentes.PopupPrecio
import pe.leadai.rider.ui.cliente.componentes.RadarMotos
import pe.leadai.rider.ui.cliente.componentes.SelectorYaPedi
import pe.leadai.rider.ui.comunes.BannerError
import pe.leadai.rider.ui.comunes.BarraInferiorCliente
import pe.leadai.rider.ui.comunes.SeccionCliente
import pe.leadai.rider.ui.comunes.BotonAcento
import pe.leadai.rider.ui.comunes.CampoJala
import pe.leadai.rider.ui.comunes.SelectorDos
import pe.leadai.rider.ui.tema.ColoresJala
import pe.leadai.rider.ui.tema.centavosASoles
import pe.leadai.rider.ui.tema.epochMsAhora
import pe.leadai.rider.ui.tema.epochMsDesdeIso

/**
 * Cada cuánto el cliente vuelve a preguntar por ofertas y por su carrera.
 *
 * Eran 10 segundos —el comentario decía "mismo ritmo que el rider", pero el
 * del rider ya estaba en 3— y se notaba: el motorizado mandaba su oferta al
 * instante y el cliente la veía hasta diez segundos después, mirando una
 * pantalla que decía "buscando" cuando ya había alguien esperando respuesta.
 *
 * 3 segundos, igual que el rider. Además el push fuerza un refresco apenas
 * entra, así que esto es solo la red de seguridad.
 */
private const val INTERVALO_POLLING_MS = 3_000L

/** Base del mapa embebido — el mismo host de `ApiCliente` (default de prod). */

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
// `ModalBottomSheet` sigue marcado como experimental en Material 3, pero es la
// hoja estándar y su API está estable en la práctica.
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
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
            // UNA sola llamada: trae la carrera, las ofertas y las motos.
            //
            // Antes eran tres, y cada request cuesta cerca de un segundo
            // porque el servidor está lejos (230ms de conexión + 445ms de TLS
            // + ~530ms hasta el primer byte). Con tres por ciclo la app pasaba
            // casi todo el tiempo esperando red — eso era lo que se sentía
            // pesado, más que cualquier animación.
            viewModel.refrescar()
            // Solo si el chat está abierto: si no, es una llamada al vacío.
            viewModel.refrescarChat()
        }
    }
    // "Tu moto llegó" tiene que verse APENAS entra el push: el cliente está
    // mirando la pantalla esperando justo eso, y esperar al polling lo dejaba
    // viendo "va en camino" con el rider ya en la puerta.
    LaunchedEffect(Unit) {
        pe.leadai.rider.ui.comunes.AvisoPush.avisos.collect {
            // `refrescar()` ya trae carrera + ofertas + motos en una sola
            // llamada: pedir las ofertas aparte era un request de más por cada
            // push, justo cuando el teléfono está ocupado mostrando el aviso.
            viewModel.refrescar()
            // El chat TAMBIÉN: un mensaje que llega por push tiene que verse
            // al instante si la conversación está abierta. Sin esto había que
            // esperar al polling con el otro escribiendo del otro lado.
            viewModel.refrescarChat()
        }
    }
    LaunchedEffect(Unit) {
        avisosGlobales.avisos.collect { mensaje -> snackbarHostState.showSnackbar(mensaje) }
    }

    var seccion by remember { mutableStateOf(SeccionCliente.PEDIR) }
    val carrera = estado.miCarrera
    // Se pregunta el motivo ANTES de cancelar: con la carrera ya borrada no
    // hay a qué asociarlo.
    var pidiendoMotivo by remember { mutableStateOf(false) }

    // ATRÁS: desde Viajes o Perfil se vuelve a Pedir, no se sale de la app.
    // En Pedir se deja pasar el evento — ahí salir SÍ es lo esperado.
    ManejarAtras(habilitado = seccion != SeccionCliente.PEDIR) {
        seccion = SeccionCliente.PEDIR
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) { Snackbar(it) } },
        bottomBar = {
            // Con una carrera en curso NO hay barra: la pantalla es el
            // seguimiento del viaje y nada más. Irse a "Viajes" mientras el
            // rider está llegando es perder de vista lo único que importa.
            if (carrera == null) {
                BarraInferiorCliente(
                    seleccionada = seccion,
                    onSeleccionar = { seccion = it },
                )
            }
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when {
                // 1) Carrera activa: manda sobre todo lo demás.
                carrera != null -> SeguimientoCarrera(
                    carrera = carrera,
                    onCancelar = { pidiendoMotivo = true },
                    ofertas = estado.ofertas,
                    eligiendoOferta = estado.eligiendoOferta,
                    onElegirOferta = viewModel::elegirOferta,
                    // +S/2 sobre lo ofrecido: en Tacna es el salto que de
                    // verdad cambia la decisión del rider.
                    onSubirMonto = { viewModel.subirMonto(carrera.montoOfrecido + 200) },
                    motosCerca = estado.motosCerca,
                    // Lo calcula el BACKEND: el reloj del teléfono puede
                    // estar corrido, y un contador que no coincide con el
                    // vencimiento real haría esperar de más.
                    segundosRestantes = carrera.segundosRestantes,
                    mensajesSinLeer = estado.mensajesSinLeer,
                    onAbrirChat = viewModel::abrirChat,
                )
                estado.cargando -> PantallaCargando()
                // 2) Sin carrera: la pestaña que haya elegido.
                else -> when (seccion) {
                    SeccionCliente.PEDIR -> FormularioPedir(
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
                        onPedir = viewModel::revisarPrecio,
                        onEsperaEnLocal = viewModel::cambiarEsperaEnLocal,
                        onFoco = viewModel::enfocarCampo,
                        onElegirSugerencia = viewModel::elegirSugerencia,
                    )
                    SeccionCliente.VIAJES -> ViajesCliente(estado.historial)
                    SeccionCliente.PERFIL -> PerfilCliente(
                        onCambiarModo = alCambiarModo,
                        onCerrarSesion = alCerrarSesion,
                        perfil = estado.perfil,
                        guardando = estado.guardandoPerfil,
                        onGuardar = viewModel::guardarPerfil,
                    )
                }
            }
        }

        // Recién terminada: se pregunta antes de que el cliente cierre la app.
        // Va fuera del `when` porque para este momento ya no hay carrera
        // activa y la pantalla volvió al formulario.
        val porCalificar = estado.carreraPorCalificar
        if (porCalificar != null) {
            PopupCalificar(
                nombreRider = porCalificar.riderNombre,
                enviando = estado.calificando,
                onCalificar = { estrellas -> viewModel.calificar(porCalificar.id, estrellas) },
                onOmitir = viewModel::omitirCalificacion,
            )
        }

        // El precio se decide en un diálogo aparte: es LA decisión del pedido
        // y compite con todo lo demás si vive en el formulario.
        if (estado.ajustandoPrecio) {
            PopupPrecio(
                montoCentavos = montoElegidoCentavos(estado),
                calculando = estado.calculandoPrecio,
                sugeridoCentavos = estado.montoSugerido,
                kmEstimado = estado.kmEstimado,
                enviando = estado.pidiendo,
                onCambiar = viewModel::cambiarMontoCentavos,
                onConfirmar = viewModel::pedir,
                onCerrar = viewModel::cerrarAjustePrecio,
                origenLat = estado.origenLat,
                origenLng = estado.origenLng,
                direccionDelPin = estado.direccionDelPin,
                onMoverPin = viewModel::moverPin,
            )
        }

        // El chat, sobre todo lo demás: cuando se abre es lo único que
        // importa —el rider está esperando en la puerta—.
        if (estado.chatAbierto) {
            ModalBottomSheet(onDismissRequest = viewModel::cerrarChat) {
                Box(modifier = Modifier.fillMaxWidth().height(460.dp)) {
                    ChatCarrera(
                        mensajes = estado.mensajes,
                        yo = "cliente",
                        rapidos = estado.rapidosChat,
                        enviando = estado.enviandoMensaje,
                        onEnviar = viewModel::enviarMensaje,
                    )
                }
            }
        }

        // El motivo se pregunta ANTES de cancelar: después la carrera ya no
        // existe y no hay a qué asociarlo.
        if (pidiendoMotivo) {
            DialogoMotivoCancelar(
                conRiderAsignado = carrera?.estado == "aceptada" ||
                    carrera?.estado == "recogida",
                onCancelarCarrera = { motivo ->
                    pidiendoMotivo = false
                    viewModel.cancelar(motivo)
                },
                onCerrar = { pidiendoMotivo = false },
            )
        }
    }
}

/**
 * Lo que el cliente ofrece hoy: lo que escribió, si no la sugerencia.
 *
 * Nunca cero: un popup que arranca en S/0 obliga a subir de a un sol desde
 * abajo. Sin ninguna referencia cae en S/5, el piso típico en Tacna.
 */
/**
 * Lo que suma la espera en el local. Igual que `ESPERA_EN_LOCAL_CENTAVOS` del
 * backend, que es quien manda: acá solo se muestra antes de que el cliente
 * elija.
 */
private const val EXTRA_ESPERA_CENTAVOS = 200L

private fun montoElegidoCentavos(estado: ClienteUiState): Long {
    // Lo que tocó en el popup manda, y viene ya en centavos: pasar por soles
    // perdía los 50 céntimos de cada paso.
    estado.montoCentavos?.let { if (it > 0) return it }
    val escrito = estado.monto.toLongOrNull()
    if (escrito != null && escrito > 0) return escrito * 100
    return estado.montoSugerido ?: 500L
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
    onEsperaEnLocal: (Boolean) -> Unit,
    onFoco: (Boolean) -> Unit,
    onElegirSugerencia: (pe.leadai.rider.datos.SugerenciaDireccionDto) -> Unit,
) {

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        // 18 y no 12: cada campo trae su etiqueta ARRIBA, así que con poco
        // espacio la etiqueta de uno queda pegada al campo del anterior y todo
        // se lee como un bloque. Con aire se distinguen los pasos del pedido.
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Spacer(Modifier.height(4.dp))

        // Qué necesita. "Delivery" y "Encomienda" van al backend igual (los
        // dos son `encomienda`), pero separados el cliente encuentra lo suyo:
        // quien quiere un pollo no busca la palabra "encomienda".
        //
        // Etiquetas de una palabra: con tres opciones en fila, "Encomienda"
        // ya es lo más largo que entra en un teléfono angosto.
        SelectorDos(
            opciones = listOf(
                TIPO_PASAJERO to "🚕 Pasajero",
                TIPO_DELIVERY to "🍔 Delivery",
                TIPO_ENCOMIENDA to "📦 Envío",
            ),
            seleccionada = estado.tipo,
            onSeleccionar = onTipo,
        )

        // Con sugerencias reales: en Tacna hay tres "Bolognesi" y escribir a
        // ciegas mandaba al rider a la otra punta.
        BuscadorRuta(
            origen = estado.origen,
            destino = estado.destino,
            editandoOrigen = estado.editandoOrigen,
            sugerencias = estado.sugerencias,
            buscando = estado.buscandoDirecciones,
            onOrigenCambia = onOrigen,
            onDestinoCambia = onDestino,
            onFoco = onFoco,
            onElegirSugerencia = onElegirSugerencia,
            onUsarMiUbicacion = onUsarMiUbicacion,
            tipo = estado.tipo,
        )

        // El recorrido, apenas hay los dos pines: el cliente confirma que el
        // destino es el que quiso ANTES de que se le proponga un precio.
        // Entra animado: apenas el cliente elige el destino, el mapa "crece"
        // del formulario. Aparecer de golpe hace que el resto salte y no se
        // registre que algo nuevo pasó.
        // SIN AparecerCard: el `expandVertically` mide el contenido para
        // animar el alto, y un WebView todavía no tiene tamaño cuando eso
        // ocurre — la animación lo dejaba colapsado en 0 y se veía un hueco.
        if (estado.destinoLat != null) {
            Column {
                MapaDeLaRuta(
                    origenLat = estado.origenLat,
                    origenLng = estado.origenLng,
                    destinoLat = estado.destinoLat,
                    destinoLng = estado.destinoLng,
                )
                Spacer(Modifier.height(16.dp))
            }
        }

        // El precio NO se pide acá (2026-08-08). Aparecía apenas se abría la
        // pantalla, antes de que hubiera destino y por lo tanto antes de que
        // se pudiera calcular nada: el cliente veía "Ofrece tu tarifa S/6" y
        // ese 6 no era un cálculo, era el valor por defecto. Decidir a ciegas
        // y que después el popup muestre otro número es peor que no mostrar
        // nada. Ahora el precio se elige en el popup, con la ruta ya resuelta.

        // Solo en delivery: cambia el trabajo del rider y el precio. En un
        // envío el paquete ya existe y en un viaje no hay nada que esperar.
        AparecerCard(visible = estado.tipo == TIPO_DELIVERY) {
            Column {
                SelectorYaPedi(
                    esperaEnLocal = estado.esperaEnLocal,
                    extraCentavos = EXTRA_ESPERA_CENTAVOS,
                    onCambiar = onEsperaEnLocal,
                )
                Spacer(Modifier.height(12.dp))
            }
        }

        // Cuánta plata adelanta el rider en la compra. En delivery también:
        // el pollo lo paga él y se lo devolvés al recibirlo.
        if (requiereMontoDeCompra(estado.tipo)) {
            CardMontoCompra(monto = estado.montoCompra, onMontoCambia = onMontoCompra)
        }

        // El celular NO se pide acá: vive en el perfil y se manda solo. Antes
        // había que escribirlo en cada pedido, y si quedaba vacío el rider se
        // quedaba sin a quién llamar.
        //
        // Si todavía no lo cargó, se avisa y se manda a Perfil — pedirlo dos
        // veces sería volver al problema.
        if (estado.perfil?.telefono.isNullOrBlank() && estado.contacto.isBlank()) {
            AvisoFaltaCelular()
        }

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
            texto = "PEDIR MOTO  →",
            onClick = onPedir,
            cargando = estado.pidiendo,
        )

        // "Quiero manejar" y "Cerrar sesión" se fueron a la pestaña Perfil:
        // acá competían con PEDIR MOTO, que es la única acción de esta
        // pantalla.
        Spacer(Modifier.height(16.dp))
    }
}

/**
 * Un tipo de carrera. El elegido va en ÁMBAR y los otros apenas se ven.
 *
 * Antes el elegido era un `Button` y el resto `OutlinedButton`: ambos con
 * fondo claro y texto oscuro, así que a simple vista los tres se leían igual
 * y no se sabía en qué sección estabas.
 *
 * El color anima al cambiar en vez de saltar, que es lo que hace que el
 * cambio se registre como una acción tuya y no como un parpadeo.
 */
@Composable
private fun BotonTipo(
    texto: String,
    elegido: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colores = ColoresJala.actuales
    val fondo by animateColorAsState(
        targetValue = if (elegido) colores.marcaAmarillo else MaterialTheme.colorScheme.surface,
        animationSpec = tween(Movimiento.RAPIDO_MS),
        label = "fondoTipo",
    )
    // El ámbar SIEMPRE lleva texto carbón: blanco sobre amarillo da 1.9:1,
    // ilegible al sol (regla de contraste de los tokens).
    val tinta by animateColorAsState(
        targetValue = if (elegido) colores.marcaCarbon else colores.tintaSecundaria,
        animationSpec = tween(Movimiento.RAPIDO_MS),
        label = "tintaTipo",
    )
    val interaccion = recordarInteraccion()

    Box(
        modifier = modifier
            .height(56.dp)
            .toqueVivo(interaccion)
            .background(fondo, RoundedCornerShape(16.dp))
            // Contorno GRUESO en ámbar cuando está elegido, y fino y gris
            // cuando no. La negrita sola no alcanza: alguien con la vista
            // cansada no distingue dos pesos de letra, pero sí un borde de
            // 3dp de otro color. El contorno acompaña al fondo en vez de
            // desaparecer, así el elegido se lee por DOS señales.
            .border(
                width = if (elegido) 3.dp else 1.dp,
                color = if (elegido) colores.marcaCarbon else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(16.dp),
            )
            .clickable(interactionSource = interaccion, indication = null) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            texto,
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = if (elegido) FontWeight.Bold else FontWeight.Normal,
            ),
            color = tinta,
            textAlign = TextAlign.Center,
        )
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
    ofertas: List<pe.leadai.rider.datos.OfertaDto> = emptyList(),
    eligiendoOferta: String? = null,
    onElegirOferta: (pe.leadai.rider.datos.OfertaDto) -> Unit = {},
    onSubirMonto: () -> Unit = {},
    motosCerca: Int = 0,
    segundosRestantes: Int = 0,
    /** Mensajes del rider sin leer: el globito del botón de chat. */
    mensajesSinLeer: Int = 0,
    onAbrirChat: () -> Unit = {},
) {
    val enCamino = carrera.estado == "aceptada" || carrera.estado == "recogida"

    Column(modifier = Modifier.fillMaxSize()) {
        if (enCamino) {
            Text(
                tituloEnCamino(carrera),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(
                    start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp,
                ),
            )
        } else {
            // Mientras busca: qué está pasando y cuánto falta. Un "buscando…"
            // sin contador ni contexto no distingue entre "hay diez motos
            // mirando" y "no hay nadie", que piden decisiones distintas.
            EstadoBusqueda(
                segundosRestantes = segundosRestantes,
                motosCerca = motosCerca,
                ofertas = ofertas.size,
                modifier = Modifier.padding(
                    start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp,
                ),
            )
        }

        if (enCamino) {
            // El MISMO mapa en vivo que usa el rider, a sangre completa.
            MapaQueSeMide(
                url = { alto -> Rutas.Mapas.tracking(carrera.id, alto) },
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
            Spacer(Modifier.height(10.dp))
        } else {
            // El RADAR de fondo y las ofertas ENCIMA.
            //
            // El radar ocupa todo el espacio porque es lo único que pasa
            // mientras nadie responde: ver motos moverse alrededor dice que
            // hay gente ahí, que la búsqueda sigue, y cuántos hay. Las
            // ofertas se apilan sobre él a medida que llegan, sin taparlo.
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                RadarMotos(
                    lat = carrera.origenLat,
                    lng = carrera.origenLng,
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                )
                // Las ofertas ARRIBA, sobre el radar: llegan de a una y se
                // van apilando hacia abajo, así el cliente ve la nueva sin
                // tener que bajar la vista.
                //
                // `heightIn(max=…)` para que cinco ofertas no coman toda la
                // pantalla — a partir de ahí scrollean entre ellas y el radar
                // sigue viéndose debajo.
                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                ) {
                    OfertasRecibidas(
                        ofertas = ofertas,
                        montoOfrecido = carrera.montoOfrecido,
                        eligiendo = eligiendoOferta,
                        onElegir = onElegirOferta,
                        onSubirMonto = onSubirMonto,
                    )
                }
            }
        }

        // Datos del viaje. El flete y la compra, en LÍNEAS SEPARADAS.
        // (el aviso del monto y "subir oferta" viven acá abajo, junto a
        // Cancelar: agrupados donde el cliente decide, no encima del radar)
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
                // El aviso del precio va ACÁ, pegado al flete: es donde el
                // cliente ya está mirando cuánto ofreció. Antes vivía en una
                // card aparte que tapaba el radar.
                if (!enCamino) {
                    Text(
                        "Si nadie responde, puede que sea poco para la distancia.",
                        style = MaterialTheme.typography.labelSmall,
                        color = ColoresJala.actuales.tintaSecundaria,
                    )
                }
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
            DatosDelRider(
                carrera = carrera,
                sinLeer = mensajesSinLeer,
                onAbrirChat = onAbrirChat,
            )

            // Una salida TAMBIÉN cuando el rider ya viene.
            //
            // Antes el botón de cancelar solo existía mientras nadie tomaba la
            // carrera: apenas alguien aceptaba, desaparecía y el cliente
            // quedaba atrapado —si el motorizado no aparecía nunca, no tenía
            // forma de pedir otra moto—.
            //
            // Discreto y no un botón grande: cancelar cuando alguien ya salió
            // a buscarte es la excepción, no lo que se espera que hagas. El
            // rider se entera por push.
            //
            // En "recogida" no aparece: ahí el viaje ya empezó.
            if (carrera.estado == "aceptada") {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Cancelar carrera",
                    style = MaterialTheme.typography.labelMedium,
                    color = ColoresJala.actuales.calor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onCancelar() }
                        .padding(vertical = 12.dp),
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            // Solo mientras nadie la tomó: después ya salió a buscarlo.
            Spacer(Modifier.height(8.dp))

            // SUBIR MI OFERTA arriba de Cancelar: las dos salidas que tiene
            // el cliente si nadie responde, juntas y en orden — primero la
            // que resuelve, después la que abandona.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(48.dp)
                    .background(
                        ColoresJala.actuales.esperaFondo,
                        RoundedCornerShape(16.dp),
                    )
                    .clickable { onSubirMonto() },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "SUBIR MI OFERTA",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(Modifier.height(8.dp))

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
private fun DatosDelRider(
    carrera: CarreraClienteDto,
    sinLeer: Int = 0,
    onAbrirChat: () -> Unit = {},
) {
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
        // EL CHAT primero, y a todo el ancho.
        //
        // Es la vía que no obliga a nadie a dar su número: WhatsApp y llamar
        // exponen el teléfono del cliente para siempre, incluso después del
        // viaje. Quedan abajo como salida cuando el chat no alcanza.
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(48.dp)
                .clip(Formas.card)
                .background(ColoresJala.actuales.marcaAmarillo)
                .clickable { onAbrirChat() },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (sinLeer > 0) "💬 Mensajes ($sinLeer)" else "💬 Escribirle",
                style = MaterialTheme.typography.labelLarge,
                color = ColoresJala.actuales.marcaCarbon,
            )
        }
        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = { abridor.openUri("https://wa.me/$telefono") },
                modifier = Modifier.weight(1f).height(44.dp),
                shape = Formas.card,
            ) {
                Text("💬 WhatsApp", style = MaterialTheme.typography.labelLarge)
            }
            OutlinedButton(
                onClick = { abridor.openUri("tel:+$telefono") },
                modifier = Modifier.weight(1f).height(44.dp),
                shape = Formas.card,
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
