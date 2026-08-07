package pe.leadai.rider.ui.cliente

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pe.leadai.rider.datos.CarreraClienteDto
import pe.leadai.rider.datos.CarrerasClienteApi
import pe.leadai.rider.datos.MotorizadosApi
import pe.leadai.rider.datos.PerfilApi
import pe.leadai.rider.datos.PerfilPersonaDto
import pe.leadai.rider.datos.Resultado
import pe.leadai.rider.push.tokenPushActual
import pe.leadai.rider.ui.carreras.UbicacionRider
import pe.leadai.rider.ui.carreras.obtenerUbicacionActual
import pe.leadai.rider.ui.comunes.AvisosGlobales

/** Los dos tipos de carrera que acepta el backend. */
const val TIPO_ENCOMIENDA = "encomienda"
const val TIPO_PASAJERO = "pasajero"

/**
 * "Delivery" SOLO existe en la pantalla: viaja al backend como
 * [TIPO_ENCOMIENDA] (ver [tipoParaElBackend]).
 *
 * Traer comida de un local ES una encomienda — mismo flujo, misma comisión.
 * Pero nadie que quiere un pollo piensa "voy a pedir una encomienda", y con
 * dos botones el caso más común de Tacna quedaba escondido detrás de una
 * palabra de manual. Separarlo de verdad en la base obligaría a una migración
 * y a una config de comisión nueva sin ganar nada.
 */
const val TIPO_DELIVERY = "delivery"

/** Lo que entiende el backend: "delivery" es una encomienda. */
fun tipoParaElBackend(tipo: String): String =
    if (tipo == TIPO_DELIVERY) TIPO_ENCOMIENDA else tipo

/** Si el rider tiene que poner plata de su bolsillo (comprar algo). */
fun requiereMontoDeCompra(tipo: String): Boolean =
    tipo == TIPO_ENCOMIENDA || tipo == TIPO_DELIVERY

/** Estado de la pantalla del cliente: o está pidiendo, o está esperando/siguiendo. */
data class ClienteUiState(
    val cargando: Boolean = true,
    val error: String? = null,
    /** La carrera activa. Si no es null, la pantalla muestra el seguimiento. */
    val miCarrera: CarreraClienteDto? = null,
    /** [TIPO_ENCOMIENDA] | [TIPO_PASAJERO]. */
    val tipo: String = TIPO_PASAJERO,
    val origen: String = "",
    val origenLat: Double? = null,
    val origenLng: Double? = null,
    val destino: String = "",
    /** El FLETE que ofrece pagar, en soles como texto (lo edita el usuario). */
    val monto: String = "",
    /**
     * SOLO encomienda: cuánto cuesta lo que el rider va a comprar y adelanta
     * de su bolsillo. NO es parte del flete y NUNCA se suma con [monto].
     */
    val montoCompra: String = "",
    val notas: String = "",
    val contacto: String = "",
    /** Lo que sugiere el sistema, en centavos — punto de partida editable. */
    val montoSugerido: Long? = null,
    val kmEstimado: Double? = null,
    val pidiendo: Boolean = false,
    /** Las carreras ya cerradas — alimenta la pestaña "Viajes". */
    val historial: List<CarreraClienteDto> = emptyList(),
    /** Mi perfil: el celular sale de acá, no de un campo por pedido. */
    val perfil: PerfilPersonaDto? = null,
    val guardandoPerfil: Boolean = false,
)

private const val MENSAJE_SIN_ORIGEN = "Falta el origen"
private const val MENSAJE_SIN_DESTINO = "Falta el destino"
private const val MENSAJE_ERROR_PEDIR = "No pudimos pedir tu moto. Intenta de nuevo."
private const val MENSAJE_ERROR_CARGAR = "No pudimos cargar tu carrera. Intenta de nuevo."

/**
 * El lado CLIENTE: pedir una moto y seguir el viaje.
 *
 * El monto es una SUGERENCIA editable, no una tarifa: LeadAI enlaza, no fija
 * precios. El pago es en efectivo entre el cliente y el rider.
 *
 * **La regla del dinero**: en una encomienda el rider puede tener que COMPRAR
 * algo y adelantar la plata. Ese `montoCompra` es un reembolso, no un precio:
 * viaja en su propio campo y jamás se suma al flete — un "total" de S/68 donde
 * el rider gana S/8 sería una mentira para los dos lados.
 */
class ClienteViewModel(
    private val api: CarrerasClienteApi,
    private val avisos: AvisosGlobales,
    /**
     * Solo para registrar el token push del CLIENTE. `POST
     * /motorizados/dispositivo` sirve igual acá: asocia `usuarioId` + token y
     * no exige perfil de motorizado.
     */
    private val motorizadosApi: MotorizadosApi,
    /** El perfil de la persona: el celular y la dirección habitual. */
    private val perfilApi: PerfilApi,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    /** GPS inyectable (mismo patrón que `CarrerasViewModel`) — en tests, `null`. */
    private val obtenerUbicacion: suspend () -> UbicacionRider? = { obtenerUbicacionActual() },
    /** Token FCM inyectable (mismo patrón que `CarrerasViewModel`). */
    private val obtenerTokenPush: suspend () -> String? = { tokenPushActual() },
) : ViewModel() {

    private val _estado = MutableStateFlow(ClienteUiState())
    val estado: StateFlow<ClienteUiState> = _estado.asStateFlow()

    /** El registro del token va UNA vez por sesión de pantalla, no en cada `cargar()`. */
    private var pushRegistrado = false

    fun cargar() {
        _estado.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch(dispatcher) {
            when (val r = api.miCarrera()) {
                is Resultado.Ok -> _estado.update { it.copy(cargando = false, miCarrera = r.valor) }
                is Resultado.Error -> _estado.update {
                    it.copy(cargando = false, error = r.mensaje.ifBlank { MENSAJE_ERROR_CARGAR })
                }
            }
        }
        registrarPush()
        usarMiUbicacion()
        refrescarHistorial()
        refrescarPerfil()
    }

    /**
     * Trae el perfil y PRE-LLENA el contacto del pedido.
     *
     * El celular vive en el perfil, no en cada pedido: antes había que
     * escribirlo cada vez, y si el cliente lo dejaba vacío el rider se
     * quedaba sin a quién llamar. Solo pre-llena si el campo está en blanco,
     * para no pisar lo que la persona escribió recién.
     */
    fun refrescarPerfil() {
        viewModelScope.launch(dispatcher) {
            when (val r = perfilApi.miPerfil()) {
                is Resultado.Ok -> _estado.update {
                    it.copy(
                        perfil = r.valor.perfil,
                        contacto = it.contacto.ifBlank { r.valor.perfil?.telefono.orEmpty() },
                    )
                }
                is Resultado.Error -> Unit
            }
        }
    }

    /** Guarda el celular y la dirección habitual desde la pestaña Perfil. */
    fun guardarPerfil(nombre: String?, telefono: String?, direccion: String?) {
        if (_estado.value.guardandoPerfil) return
        _estado.update { it.copy(guardandoPerfil = true) }
        viewModelScope.launch(dispatcher) {
            val r = perfilApi.guardar(
                nombre = nombre?.trim()?.takeIf { it.isNotBlank() },
                telefono = telefono?.trim()?.takeIf { it.isNotBlank() },
                direccionHabitual = direccion?.trim()?.takeIf { it.isNotBlank() },
            )
            _estado.update { it.copy(guardandoPerfil = false) }
            when (r) {
                is Resultado.Ok -> {
                    avisos.mostrar("Datos guardados")
                    refrescarPerfil()
                }
                is Resultado.Error -> avisos.mostrar(
                    r.mensaje.ifBlank { "No pudimos guardar tus datos" },
                )
            }
        }
    }

    /**
     * Las carreras cerradas, para la pestaña "Viajes".
     *
     * Silencioso: es información de consulta, no bloquea pedir una moto. Si
     * falla, la pestaña queda vacía y se reintenta al volver a cargar.
     */
    fun refrescarHistorial() {
        viewModelScope.launch(dispatcher) {
            when (val r = api.historial()) {
                is Resultado.Ok -> _estado.update { it.copy(historial = r.valor) }
                is Resultado.Error -> Unit
            }
        }
    }

    /**
     * Push del cliente ("un rider tomó tu carrera"): una vez por sesión de
     * pantalla y silencioso. Sin token no pasa nada — el endpoint es el mismo
     * del rider, que solo asocia `usuarioId` + token.
     *
     * Sin esto el backend manda el aviso al vacío: el cliente nunca registró
     * su teléfono y solo se enteraría con la app abierta, por el polling.
     */
    private fun registrarPush() {
        if (pushRegistrado) return
        pushRegistrado = true
        viewModelScope.launch(dispatcher) {
            obtenerTokenPush()?.let { token -> motorizadosApi.registrarDispositivo(token) }
        }
    }

    /** Refresco silencioso: un fallo puntual no debe borrar lo que ya se ve. */
    fun refrescar() {
        viewModelScope.launch(dispatcher) {
            when (val r = api.miCarrera()) {
                is Resultado.Ok -> _estado.update { it.copy(miCarrera = r.valor) }
                is Resultado.Error -> Unit
            }
        }
    }

    /** "Estoy acá": toma el GPS del teléfono como origen. Silencioso si no hay. */
    fun usarMiUbicacion() {
        viewModelScope.launch(dispatcher) {
            val u = obtenerUbicacion() ?: return@launch
            _estado.update {
                it.copy(
                    origenLat = u.lat,
                    origenLng = u.lng,
                    origen = if (it.origen.isBlank()) "Mi ubicación actual" else it.origen,
                )
            }
        }
    }

    fun elegirTipo(tipo: String) {
        _estado.update {
            // Un pasajero no manda al rider a comprar nada: si cambia de tipo,
            // el monto de compra deja de tener sentido y se limpia solo.
            it.copy(
                tipo = tipo,
                montoCompra = if (requiereMontoDeCompra(tipo)) it.montoCompra else "",
                error = null,
            )
        }
    }

    fun cambiarOrigen(valor: String) {
        // Si escribe el origen a mano, el GPS deja de aplicar.
        _estado.update { it.copy(origen = valor, origenLat = null, origenLng = null, error = null) }
    }

    fun cambiarDestino(valor: String) = _estado.update { it.copy(destino = valor, error = null) }

    /** El FLETE, en soles. */
    fun cambiarMonto(valor: String) =
        _estado.update { it.copy(monto = soloNumeros(valor), error = null) }

    /** Lo que el rider ADELANTA para comprar, en soles. Nunca parte del flete. */
    fun cambiarMontoCompra(valor: String) =
        _estado.update { it.copy(montoCompra = soloNumeros(valor), error = null) }

    fun cambiarNotas(valor: String) = _estado.update { it.copy(notas = valor, error = null) }
    fun cambiarContacto(valor: String) = _estado.update { it.copy(contacto = valor, error = null) }

    /** Pide la sugerencia de monto al backend, para mostrarla antes de confirmar. */
    fun pedirSugerencia() {
        val a = _estado.value
        if (a.origen.isBlank() || a.destino.isBlank()) return
        viewModelScope.launch(dispatcher) {
            when (
                val r = api.sugerir(
                    tipo = tipoParaElBackend(a.tipo),
                    origenTexto = a.origen,
                    origenLat = a.origenLat,
                    origenLng = a.origenLng,
                    destinoTexto = a.destino,
                )
            ) {
                is Resultado.Ok -> _estado.update {
                    it.copy(
                        montoSugerido = r.valor.montoSugerido,
                        kmEstimado = r.valor.kmEstimado,
                        // Solo pre-llena si el usuario no escribió su monto.
                        monto = if (it.monto.isBlank()) (r.valor.montoSugerido / 100).toString() else it.monto,
                    )
                }
                is Resultado.Error -> Unit // la sugerencia es un nice-to-have
            }
        }
    }

    fun pedir() {
        val a = _estado.value
        if (a.pidiendo) return
        if (a.origen.isBlank()) {
            _estado.update { it.copy(error = MENSAJE_SIN_ORIGEN) }
            return
        }
        if (a.destino.isBlank()) {
            _estado.update { it.copy(error = MENSAJE_SIN_DESTINO) }
            return
        }

        _estado.update { it.copy(pidiendo = true, error = null) }
        viewModelScope.launch(dispatcher) {
            when (
                val r = api.pedir(
                    tipo = tipoParaElBackend(a.tipo),
                    origenTexto = a.origen,
                    origenLat = a.origenLat,
                    origenLng = a.origenLng,
                    destinoTexto = a.destino,
                    destinoLat = null,
                    destinoLng = null,
                    montoOfrecidoCentavos = aCentavos(a.monto),
                    // El monto de compra SOLO en encomienda: es lo que el rider
                    // adelanta y el cliente le devuelve, nunca parte del flete.
                    montoCompraEstimadoCentavos = if (a.tipo == TIPO_ENCOMIENDA) aCentavos(a.montoCompra) else null,
                    notas = a.notas,
                    contacto = a.contacto,
                )
            ) {
                is Resultado.Ok -> {
                    _estado.update { it.copy(pidiendo = false) }
                    avisos.mostrar("🛵 Buscando motorizado…")
                    refrescar()
                }
                is Resultado.Error -> {
                    val mensaje = r.mensaje.ifBlank { MENSAJE_ERROR_PEDIR }
                    _estado.update { it.copy(pidiendo = false, error = mensaje) }
                    avisos.mostrar(mensaje)
                }
            }
        }
    }

    fun cancelar() {
        val id = _estado.value.miCarrera?.id ?: return
        viewModelScope.launch(dispatcher) {
            when (val r = api.cancelar(id)) {
                is Resultado.Ok -> {
                    _estado.update { it.copy(miCarrera = null) }
                    avisos.mostrar("Carrera cancelada")
                }
                // 409: un rider ya la tomó y está yendo — no se cancela.
                is Resultado.Error -> avisos.mostrar(r.mensaje.ifBlank { "No se pudo cancelar" })
            }
        }
    }
}

private fun soloNumeros(valor: String): String = valor.filter { it.isDigit() }.take(5)

/** Soles como texto → centavos. Vacío = null (que el backend sugiera). */
private fun aCentavos(soles: String): Long? =
    soles.trim().takeIf { it.isNotBlank() }?.toLongOrNull()?.let { it * 100 }
