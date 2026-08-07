package pe.leadai.rider.ui.carreras

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pe.leadai.rider.datos.MotorizadosApi
import pe.leadai.rider.datos.CarreraDto
import pe.leadai.rider.datos.HistorialRiderResponseDto
import pe.leadai.rider.datos.MonederoApi
import pe.leadai.rider.datos.MonederoDto
import pe.leadai.rider.datos.PerfilMotorizadoDto
import pe.leadai.rider.datos.Resultado
import pe.leadai.rider.push.tokenPushActual
import pe.leadai.rider.ui.comunes.AvisosGlobales

/** Estado inmutable de [CarrerasPantalla]. */
data class CarrerasUiState(
    val cargando: Boolean = true,
    val error: String? = null,
    val perfil: PerfilMotorizadoDto? = null,
    /** POOL v0: pedidos "listos" de los negocios de la zona, esperando rider. */
    val carreras: List<CarreraDto> = emptyList(),
    /** La carrera EN CURSO del rider (aceptada, aún no entregada) — manda sobre la lista. */
    val miCarrera: CarreraDto? = null,
    /** `pedidoId` con aceptar/entregar en vuelo (deshabilita su botón). */
    val accionEnCurso: String? = null,
    /** Historial del rider: resumen de HOY (carreras/km/total) + últimas entregas. */
    val historial: HistorialRiderResponseDto? = null,
    /** Monedero prepago: saldo y cuántas carreras le alcanzan (2026-07-24). */
    val monedero: MonederoDto? = null,
    /** `true` cuando intentó aceptar sin saldo — la pantalla ofrece recargar. */
    val sinSaldo: Boolean = false,
    /**
     * `pedidoId`s donde el rider YA hizo su propuesta y espera respuesta.
     *
     * Vive en memoria a propósito: si mata la app, el feed vuelve limpio y
     * puede reofertar sin quedar convencido de que ya lo hizo.
     */
    val ofertadas: Set<String> = emptySet(),
)

private const val MENSAJE_ERROR_CARGAR = "No pudimos cargar tu perfil. Intenta de nuevo."
private const val MENSAJE_ERROR_ACCION = "No se pudo. Intenta de nuevo."
private const val MENSAJE_CARRERA_TOMADA = "Otro rider tomó esa carrera 🏍️ — atento a la siguiente"

/**
 * ViewModel de [CarrerasPantalla] (Fase B.5, Task T3-doble): la vista de
 * espera para un usuario con perfil de motorizado y SIN empresas (el rol de
 * PLATAFORMA, no una membresía a un tenant — ver ARQUITECTURA.md Fase E).
 * Solo lee [MotorizadosApi.miPerfil] — la creación/edición del perfil ya vive
 * en `CrearNegocioViewModel.crearMotorizado` (reusado por "Cambiar distrito",
 * que reabre el mini-form de `CrearNegocioPantalla`). Mismo patrón de
 * dispatcher inyectado que el resto del repo.
 */
class CarrerasViewModel(
    private val motorizadosApi: MotorizadosApi,
    private val avisos: AvisosGlobales,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    /**
     * GPS del dispositivo, inyectado como lambda (mismo patrón que
     * `RegistroPushRepositorio.obtenerToken`) para simularlo en tests sin
     * tocar el `expect/actual` real de [obtenerUbicacionActual].
     */
    private val obtenerUbicacion: suspend () -> UbicacionRider? = { obtenerUbicacionActual() },
    /** Token FCM inyectable (mismo patrón que `RegistroPushRepositorio.obtenerToken`). */
    private val obtenerTokenPush: suspend () -> String? = { tokenPushActual() },
    /** Monedero prepago — opcional para no romper los tests que no lo usan. */
    private val monederoApi: MonederoApi? = null,
) : ViewModel() {

    private val _estado = MutableStateFlow(CarrerasUiState())
    val estado: StateFlow<CarrerasUiState> = _estado.asStateFlow()

    private var pushRegistrado = false

    fun cargar() {
        _estado.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch(dispatcher) {
            when (val resultado = motorizadosApi.miPerfil()) {
                is Resultado.Ok -> {
                    _estado.update {
                        it.copy(cargando = false, error = null, perfil = resultado.valor)
                    }
                    // Push del rider ("nueva carrera en tu zona"): una vez por
                    // sesión de pantalla, silencioso — sin token no pasa nada.
                    if (!pushRegistrado && resultado.valor != null) {
                        pushRegistrado = true
                        obtenerTokenPush()?.let { token -> motorizadosApi.registrarDispositivo(token) }
                    }
                }
                is Resultado.Error -> _estado.update {
                    it.copy(cargando = false, error = resultado.mensaje.ifBlank { MENSAJE_ERROR_CARGAR })
                }
            }
        }
        refrescarCarreras()
        refrescarHistorial()
        refrescarMonedero()
    }

    /** Saldo del monedero — silencioso (si falla, la pantalla sigue funcionando). */
    fun refrescarMonedero() {
        val api = monederoApi ?: return
        viewModelScope.launch(dispatcher) {
            when (val resultado = api.leer()) {
                is Resultado.Ok -> _estado.update {
                    it.copy(monedero = resultado.valor, sinSaldo = resultado.valor.carrerasDisponibles <= 0)
                }
                is Resultado.Error -> Unit
            }
        }
    }

    /** Historial silencioso (como el feed): un fallo puntual no borra lo visible. */
    fun refrescarHistorial() {
        viewModelScope.launch(dispatcher) {
            when (val resultado = motorizadosApi.historial()) {
                is Resultado.Ok -> _estado.update { it.copy(historial = resultado.valor) }
                is Resultado.Error -> Unit
            }
        }
    }

    /**
     * POOL v0: trae las carreras de la zona + la propia en curso. SILENCIOSO
     * (no toca `cargando` — lo llama el polling de la pantalla, igual que la
     * Cocina): un fallo puntual del feed no borra lo que ya se ve.
     */
    fun refrescarCarreras() {
        viewModelScope.launch(dispatcher) {
            when (val resultado = motorizadosApi.carreras()) {
                is Resultado.Ok -> {
                    _estado.update {
                        it.copy(carreras = resultado.valor.carreras, miCarrera = resultado.valor.miCarrera)
                    }
                    // Tracking nivel 2: con carrera en curso, cada vuelta del
                    // polling también reporta el GPS — así el cliente ve al
                    // rider moverse en el mapa de /track/:pedidoId.
                    if (resultado.valor.miCarrera != null) reportarPosicion()
                }
                is Resultado.Error -> Unit
            }
        }
    }

    /**
     * Reporte de GPS bajo demanda.
     *
     * El pulso rápido de 5s ya NO vive acá: lo lleva `ServicioCarreraActiva`,
     * el foreground service, porque un loop en la pantalla se suspende cuando
     * el rider bloquea el teléfono. Esto queda como disparo puntual — sin
     * llamador en producción hoy, pero es el gancho para forzar un reporte sin
     * esperar la vuelta del service.
     */
    suspend fun reportarPosicionAhora() = reportarPosicion()

    /** Silencioso como el feed: sin GPS (permiso denegado, sin fix) no pasa nada. */
    private suspend fun reportarPosicion() {
        val ubicacion = obtenerUbicacion() ?: return
        motorizadosApi.reportarPosicion(ubicacion.lat, ubicacion.lng)
    }

    /**
     * Ofrece llevar la carrera por [montoCentavos] — el cliente decide.
     *
     * La carrera NO pasa a "en curso" acá: sigue en el feed hasta que el
     * cliente elija (o hasta que elija a otro y el polling la saque). Ofertar
     * de nuevo pisa la propuesta anterior, así que el rider puede subir el
     * precio sin ensuciarle la lista al cliente.
     */
    fun ofertar(carrera: CarreraDto, montoCentavos: Long) {
        if (_estado.value.accionEnCurso != null) return
        _estado.update { it.copy(accionEnCurso = carrera.pedidoId) }
        viewModelScope.launch(dispatcher) {
            when (val resultado = motorizadosApi.ofertarCarrera(carrera.pedidoId, montoCentavos)) {
                is Resultado.Ok -> {
                    _estado.update {
                        it.copy(
                            accionEnCurso = null,
                            ofertadas = it.ofertadas + carrera.pedidoId,
                        )
                    }
                    avisos.mostrar("✅ Propuesta enviada. Te avisamos si te eligen.")
                }
                is Resultado.Error -> {
                    _estado.update { it.copy(accionEnCurso = null) }
                    avisos.mostrar(resultado.mensaje)
                }
            }
        }
    }

    /** Acepta una carrera (el PRIMERO gana): en éxito pasa a "en curso"; con 409, la carrera voló. */
    fun aceptar(carrera: CarreraDto) {
        if (_estado.value.accionEnCurso != null) return
        _estado.update { it.copy(accionEnCurso = carrera.pedidoId) }
        viewModelScope.launch(dispatcher) {
            when (val resultado = motorizadosApi.aceptarCarrera(carrera.pedidoId)) {
                is Resultado.Ok -> {
                    // La del BACKEND si vino: trae el nombre y el teléfono del
                    // cliente, que el feed no incluye. Con la del feed la hoja
                    // salía sin botones de contacto hasta el próximo polling.
                    val activa = resultado.valor.carrera ?: carrera
                    _estado.update {
                        it.copy(
                            accionEnCurso = null,
                            miCarrera = activa,
                            carreras = it.carreras.filter { c -> c.pedidoId != carrera.pedidoId },
                        )
                    }
                    avisos.mostrar("🏍️ ¡Carrera tuya! Recoge en ${carrera.negocio}")
                    // Primera posición al toque: que el mapa del cliente no
                    // espere a la siguiente vuelta del polling.
                    reportarPosicion()
                    // La carrera costó S/1: refrescar el saldo mostrado.
                    refrescarMonedero()
                }
                is Resultado.Error -> {
                    // 402 = sin saldo en el monedero (2026-07-24): la carrera
                    // NO se tomó y sigue disponible para él si recarga.
                    val sinSaldo = resultado.codigo == 402
                    _estado.update {
                        it.copy(
                            accionEnCurso = null,
                            sinSaldo = sinSaldo || it.sinSaldo,
                            // 409: otro rider ganó — se saca de la lista al instante.
                            carreras = if (resultado.codigo == 409) {
                                it.carreras.filter { c -> c.pedidoId != carrera.pedidoId }
                            } else {
                                it.carreras
                            },
                        )
                    }
                    avisos.mostrar(
                        when {
                            sinSaldo -> "💳 Sin saldo — recarga tu monedero para tomar carreras"
                            resultado.codigo == 409 -> MENSAJE_CARRERA_TOMADA
                            else -> resultado.mensaje.ifBlank { MENSAJE_ERROR_ACCION }
                        },
                    )
                }
            }
        }
    }

    /**
     * "Ya recogí el pedido": cierra el tramo al LOCAL y arranca el del
     * cliente (2026-07-24). El mapa cambia de destino solo.
     */
    fun marcarRecogido() {
        val carrera = _estado.value.miCarrera ?: return
        if (_estado.value.accionEnCurso != null) return
        _estado.update { it.copy(accionEnCurso = carrera.pedidoId) }
        viewModelScope.launch(dispatcher) {
            when (val resultado = motorizadosApi.recogiCarrera(carrera.pedidoId)) {
                is Resultado.Ok -> {
                    _estado.update {
                        it.copy(accionEnCurso = null, miCarrera = it.miCarrera?.copy(recogido = true))
                    }
                    avisos.mostrar("📦 ¡Recogido! Ahora al cliente 🛵")
                }
                is Resultado.Error -> {
                    _estado.update { it.copy(accionEnCurso = null) }
                    avisos.mostrar(resultado.mensaje.ifBlank { MENSAJE_ERROR_ACCION })
                }
            }
        }
    }

    /**
     * Suelta la carrera aceptada: vuelve al pool y se reintegra la comisión.
     *
     * Existe porque sin esto el rider quedaba atrapado — acepta por error, se
     * le rompe la moto, el cliente no aparece — y como la app muestra una
     * carrera activa a la vez, no podía tomar ninguna otra en todo el día.
     *
     * El backend solo lo permite antes de recoger; después responde 409 y el
     * mensaje se muestra tal cual.
     */
    fun cancelar() {
        val carrera = _estado.value.miCarrera ?: return
        if (_estado.value.accionEnCurso != null) return
        _estado.update { it.copy(accionEnCurso = carrera.pedidoId) }
        viewModelScope.launch(dispatcher) {
            when (val resultado = motorizadosApi.cancelarCarrera(carrera.pedidoId)) {
                is Resultado.Ok -> {
                    _estado.update { it.copy(accionEnCurso = null, miCarrera = null) }
                    avisos.mostrar("La carrera volvió al pool. Se te devolvió la comisión.")
                    refrescarCarreras()
                }
                is Resultado.Error -> {
                    _estado.update { it.copy(accionEnCurso = null) }
                    avisos.mostrar(resultado.mensaje.ifBlank { MENSAJE_ERROR_ACCION })
                }
            }
        }
    }

    /** Marca la carrera en curso como entregada y vuelve a la lista. */
    fun entregar() {
        val carrera = _estado.value.miCarrera ?: return
        if (_estado.value.accionEnCurso != null) return
        _estado.update { it.copy(accionEnCurso = carrera.pedidoId) }
        viewModelScope.launch(dispatcher) {
            when (val resultado = motorizadosApi.entregarCarrera(carrera.pedidoId)) {
                is Resultado.Ok -> {
                    _estado.update { it.copy(accionEnCurso = null, miCarrera = null) }
                    avisos.mostrar("✅ ¡Entregado! Buen viaje")
                    refrescarCarreras()
                    refrescarHistorial()
                }
                is Resultado.Error -> {
                    _estado.update { it.copy(accionEnCurso = null) }
                    avisos.mostrar(resultado.mensaje.ifBlank { MENSAJE_ERROR_ACCION })
                }
            }
        }
    }
}
