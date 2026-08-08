package pe.leadai.rider.ui.cliente

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pe.leadai.rider.datos.CarreraClienteDto
import pe.leadai.rider.datos.OfertaDto
import pe.leadai.rider.datos.SugerenciaDireccionDto
import pe.leadai.rider.datos.CarrerasClienteApi
import pe.leadai.rider.datos.MotorizadosApi
import pe.leadai.rider.datos.PerfilApi
import pe.leadai.rider.datos.PerfilPersonaDto
import pe.leadai.rider.datos.Resultado
import pe.leadai.rider.push.pedirPermisoNotificaciones
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
    /**
     * El pin del destino, cuando salió de una sugerencia.
     *
     * Sin esto el monto sugerido no se podía calcular: el backend necesita
     * los DOS puntos para pedirle la ruta a OSRM, y con solo el texto el
     * precio caía al valor por defecto.
     */
    val destinoLat: Double? = null,
    val destinoLng: Double? = null,
    /** Lo que dice el mapa donde está el pin, mientras el cliente lo mueve. */
    val direccionDelPin: String = "",
    /**
     * Lo que ofrece el cliente, en centavos. `null` = todavía no lo tocó.
     *
     * Los pasos son de 50 céntimos, así que no entra en `monto` (soles
     * enteros) sin perder la mitad en cada toque.
     */
    val montoCentavos: Long? = null,
    /**
     * `true` mientras se resuelve la ruta y no hay un monto de verdad.
     *
     * El popup muestra "calculando" en vez de un número: un precio inventado
     * que después cambia es peor que esperar un segundo.
     */
    val calculandoPrecio: Boolean = false,
    /**
     * Cuántas motos hay cerca, para el estado de la búsqueda.
     *
     * Sale del MISMO endpoint que dibuja el radar: si el mapa muestra tres
     * motos y el texto dice otra cosa, el cliente deja de creerle a los dos.
     */
    val motosCerca: Int = 0,
    /**
     * En delivery: si el rider tiene que HACER el pedido y esperar.
     *
     * Arranca en `false` —"ya lo pedí"— porque es el caso más común y el más
     * barato: si el cliente no toca nada, no se le cobra de más.
     */
    val esperaEnLocal: Boolean = false,
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
    /**
     * `true` mientras el cliente ajusta cuánto ofrece, antes de publicar.
     *
     * La carrera NO sale al aire hasta que confirme acá: el precio es su
     * decisión, no una tarifa que la app le impone.
     */
    val ajustandoPrecio: Boolean = false,
    /** Las carreras ya cerradas — alimenta la pestaña "Viajes". */
    val historial: List<CarreraClienteDto> = emptyList(),
    /** Mi perfil: el celular sale de acá, no de un campo por pedido. */
    val perfil: PerfilPersonaDto? = null,
    val guardandoPerfil: Boolean = false,

    // ── Buscador de ruta ────────────────────────────────────────────────
    /** Cuál de los dos campos se está escribiendo. */
    val editandoOrigen: Boolean = true,
    val sugerencias: List<SugerenciaDireccionDto> = emptyList(),
    val buscandoDirecciones: Boolean = false,

    // ── Mercado de ofertas ──────────────────────────────────────────────
    /** Las propuestas que llegaron, de la más barata a la más cara. */
    val ofertas: List<OfertaDto> = emptyList(),
    /** Id de la oferta que se está eligiendo (deshabilita el resto). */
    val eligiendoOferta: String? = null,
    /**
     * La carrera recién terminada, esperando que el cliente la califique.
     *
     * Se guarda aparte de `miCarrera` porque para cuando hay que preguntar,
     * `miCarrera` ya es `null` — el backend deja de devolverla al cerrarse.
     */
    val carreraPorCalificar: CarreraClienteDto? = null,
    val calificando: Boolean = false,
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
            // El permiso primero: sin él Android descarta los avisos en
            // silencio, y el cliente no se entera de que su moto llegó.
            pedirPermisoNotificaciones()
            obtenerTokenPush()?.let { token -> motorizadosApi.registrarDispositivo(token) }
        }
    }

    /** Refresco silencioso: un fallo puntual no debe borrar lo que ya se ve. */
    fun refrescar() {
        viewModelScope.launch(dispatcher) {
            when (val r = api.miCarrera()) {
                is Resultado.Ok -> _estado.update { alLlegarLaCarrera(it, r.valor) }
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

    // ── Buscador de direcciones ─────────────────────────────────────────

    /**
     * Cancela la búsqueda anterior cuando el cliente sigue escribiendo.
     *
     * Sin esto, "bolognesi" dispara 9 llamadas (una por letra) y Nominatim
     * —que admite 1 por segundo para todo el mundo— las rechaza. Peor: las
     * respuestas llegan desordenadas y la lista parpadea con resultados de
     * consultas viejas.
     */
    private var busquedaEnCurso: kotlinx.coroutines.Job? = null

    /** Cuál de los dos campos está escribiendo: define a quién le aplica la sugerencia. */
    fun enfocarCampo(esOrigen: Boolean) {
        _estado.update { it.copy(editandoOrigen = esOrigen, sugerencias = emptyList()) }
    }

    /**
     * El punto de referencia para acotar la búsqueda: el origen si ya está,
     * si no el GPS del teléfono.
     *
     * El GPS se cachea en memoria: pedirlo en cada tecleo es lento y consume
     * batería, y la ciudad no cambia mientras se escribe una dirección.
     */
    private var ultimaUbicacionConocida: Pair<Double, Double>? = null

    private suspend fun ubicacionParaBuscar(a: ClienteUiState): Pair<Double, Double>? {
        val origen = a.origenLat to a.origenLng
        if (origen.first != null && origen.second != null) {
            @Suppress("UNCHECKED_CAST")
            return origen as Pair<Double, Double>
        }
        ultimaUbicacionConocida?.let { return it }
        val u = obtenerUbicacion() ?: return null
        val punto = u.lat to u.lng
        ultimaUbicacionConocida = punto
        return punto
    }

    /** Busca direcciones con lo que lleva escrito. */
    private fun buscarDirecciones(texto: String) {
        busquedaEnCurso?.cancel()
        if (texto.trim().length < 3) {
            _estado.update { it.copy(sugerencias = emptyList(), buscandoDirecciones = false) }
            return
        }
        busquedaEnCurso = viewModelScope.launch(dispatcher) {
            // Espera a que deje de tipear: 400 ms es el punto donde ya no se
            // siente lento y se ahorran casi todas las llamadas.
            kotlinx.coroutines.delay(400)
            _estado.update { it.copy(buscandoDirecciones = true) }
            val a = _estado.value
            // Dónde está el cliente, para acotar la búsqueda a SU ciudad.
            //
            // El origen sirve cuando ya lo eligió, pero al escribir el ORIGEN
            // ese campo está vacío: se mandaba null, el backend no sabía la
            // ciudad y caía a Lima. Buscar "jose olaya 110" desde Tacna
            // devolvía un local de San Martín de Porres.
            val punto = ubicacionParaBuscar(a)
            when (val r = api.buscarDirecciones(texto, punto?.first, punto?.second)) {
                is Resultado.Ok -> _estado.update {
                    it.copy(sugerencias = r.valor, buscandoDirecciones = false)
                }
                is Resultado.Error -> _estado.update {
                    // Sin sugerencias igual puede escribir a mano: no se
                    // muestra un error por algo que es una ayuda.
                    it.copy(sugerencias = emptyList(), buscandoDirecciones = false)
                }
            }
        }
    }

    /** El cliente toca una sugerencia: se fija el texto Y sus coordenadas. */
    fun elegirSugerencia(s: SugerenciaDireccionDto) {
        _estado.update {
            if (it.editandoOrigen) {
                it.copy(
                    origen = s.texto, origenLat = s.lat, origenLng = s.lng,
                    sugerencias = emptyList(),
                )
            } else {
                // Las coordenadas del destino también: sin ellas el backend
                // tiene que geocodificar el texto a ciegas, y si no acierta no
                // hay ruta — el precio caía al valor por defecto.
                it.copy(
                    destino = s.texto, destinoLat = s.lat, destinoLng = s.lng,
                    sugerencias = emptyList(),
                )
            }
        }
        // Con los dos puntos ya se puede calcular cuánto conviene ofrecer.
        pedirSugerencia()
    }

    // ── Mercado de ofertas ──────────────────────────────────────────────

    /**
     * Las propuestas que llegaron. Silencioso: se llama en cada vuelta del
     * polling y un fallo puntual no debe borrar las que ya se ven.
     */
    /**
     * Cuántas motos hay alrededor del punto de recojo.
     *
     * Silencioso: si falla, el estado cae a "ampliando el área", que es lo
     * honesto cuando no sabemos.
     */
    fun refrescarMotosCerca() {
        val c = _estado.value.miCarrera ?: return
        val lat = c.origenLat ?: return
        val lng = c.origenLng ?: return
        if (c.estado != "disponible") return
        viewModelScope.launch(dispatcher) {
            when (val r = api.motosCerca(lat, lng)) {
                is Resultado.Ok -> _estado.update { it.copy(motosCerca = r.valor) }
                is Resultado.Error -> Unit
            }
        }
    }

    fun refrescarOfertas() {
        val carrera = _estado.value.miCarrera ?: return
        // Solo mientras nadie la tomó: después las ofertas ya no importan.
        if (carrera.estado != "disponible") return
        viewModelScope.launch(dispatcher) {
            when (val r = api.ofertas(carrera.id)) {
                is Resultado.Ok -> _estado.update { it.copy(ofertas = r.valor.ofertas) }
                is Resultado.Error -> Unit
            }
        }
    }

    /** El cliente elige a un rider: esa oferta gana y la carrera se le asigna. */
    fun elegirOferta(oferta: OfertaDto) {
        val carrera = _estado.value.miCarrera ?: return
        if (_estado.value.eligiendoOferta != null) return
        _estado.update { it.copy(eligiendoOferta = oferta.id) }
        viewModelScope.launch(dispatcher) {
            when (val r = api.elegir(carrera.id, oferta.id)) {
                is Resultado.Ok -> {
                    _estado.update { it.copy(eligiendoOferta = null, ofertas = emptyList()) }
                    avisos.mostrar("🏍️ ¡Listo! " + (oferta.rider.nombre ?: "Tu motorizado") + " va en camino")
                    refrescar()
                }
                is Resultado.Error -> {
                    _estado.update { it.copy(eligiendoOferta = null) }
                    // 409: otro rider retiró su oferta, o ya eligió desde
                    // otro lado. Se refresca para mostrar lo que queda.
                    avisos.mostrar(r.mensaje.ifBlank { "Esa oferta ya no está disponible" })
                    refrescarOfertas()
                }
            }
        }
    }

    /**
     * Sube lo que ofrece porque nadie le ofertó.
     *
     * Sin esto solo podría cancelar y volver a pedir, perdiendo las ofertas
     * que ya tenía y su lugar en la cola.
     */
    fun subirMonto(nuevoCentavos: Long) {
        val carrera = _estado.value.miCarrera ?: return
        viewModelScope.launch(dispatcher) {
            when (api.cambiarMonto(carrera.id, nuevoCentavos)) {
                is Resultado.Ok -> {
                    avisos.mostrar("Subiste tu oferta. Avisamos a los motorizados de tu zona.")
                    refrescar()
                }
                is Resultado.Error -> avisos.mostrar("No se pudo cambiar el monto")
            }
        }
    }

    /** Califica al rider tras la entrega. */
    fun calificar(carreraId: String, estrellas: Int, comentario: String? = null) {
        if (_estado.value.calificando) return
        _estado.update { it.copy(calificando = true) }
        viewModelScope.launch(dispatcher) {
            when (api.calificar(carreraId, estrellas, comentario)) {
                is Resultado.Ok -> avisos.mostrar("¡Gracias por calificar!")
                is Resultado.Error -> avisos.mostrar("No se pudo enviar tu calificación")
            }
            // Se cierra pase lo que pase: si el envío falló, insistir con el
            // diálogo abierto solo deja al cliente atrapado tras su viaje.
            _estado.update { it.copy(calificando = false, carreraPorCalificar = null) }
        }
    }

    /** "Ahora no": se cierra sin calificar y no se vuelve a preguntar por esa carrera. */
    fun omitirCalificacion() = _estado.update { it.copy(carreraPorCalificar = null) }

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
        // Si escribe el origen a mano, el GPS deja de aplicar: lo que vale es
        // lo que elija de las sugerencias.
        _estado.update {
            it.copy(origen = valor, origenLat = null, origenLng = null, error = null,
                editandoOrigen = true)
        }
        buscarDirecciones(valor)
    }

    fun cambiarDestino(valor: String) {
        // Al escribir a mano el pin anterior deja de valer: si no, se cotiza
        // contra el destino viejo mientras el texto ya dice otra cosa.
        _estado.update { it.copy(destinoLat = null, destinoLng = null) }
        _estado.update { it.copy(destino = valor, error = null, editandoOrigen = false) }
        buscarDirecciones(valor)
    }

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
                    // Con los dos pines el backend rutea de verdad; sin ellos
                    // tiene que geocodificar a ciegas y el monto sale del
                    // valor por defecto.
                    destinoLat = a.destinoLat,
                    destinoLng = a.destinoLng,
                    // Solo en delivery: en un envío el paquete ya existe y en
                    // un viaje de pasajero no hay nada que esperar.
                    esperaEnLocal = a.tipo == TIPO_DELIVERY && a.esperaEnLocal,
                )
            ) {
                is Resultado.Ok -> _estado.update {
                    it.copy(
                        calculandoPrecio = false,
                        montoSugerido = r.valor.montoSugerido,
                        kmEstimado = r.valor.kmEstimado,
                        // Solo pre-llena si el cliente todavía no eligió el
                        // suyo. En CENTAVOS: `570 / 100` daba 5 y el popup
                        // mostraba S/5.00 cuando lo sugerido era S/5.70.
                        montoCentavos = it.montoCentavos ?: r.valor.montoSugerido,
                        monto = if (it.monto.isBlank()) {
                            (r.valor.montoSugerido / 100).toString()
                        } else {
                            it.monto
                        },
                    )
                }
                // Sin cálculo el cliente igual tiene que poder pedir: se corta
                // el "calculando" y el popup cae a la referencia por defecto.
                is Resultado.Error -> _estado.update { it.copy(calculandoPrecio = false) }
            }
        }
    }

    /**
     * Paso previo a publicar: valida la ruta y abre el ajuste de precio.
     *
     * Se valida ACÁ y no dentro del popup para que el cliente no descubra que
     * le falta el destino recién después de elegir cuánto paga.
     */
    fun revisarPrecio() {
        val a = _estado.value
        if (a.pidiendo || a.ajustandoPrecio) return
        if (a.origen.isBlank()) {
            _estado.update { it.copy(error = MENSAJE_SIN_ORIGEN) }
            return
        }
        if (a.destino.isBlank()) {
            _estado.update { it.copy(error = MENSAJE_SIN_DESTINO) }
            return
        }
        // `calculandoPrecio` mientras no hay ruta: el popup muestra "calculando"
        // en vez de un monto. Sin esto aparecía el valor por defecto durante un
        // segundo y después saltaba al real — el cliente veía un precio que no
        // era, que es justo lo que se quería evitar.
        _estado.update {
            it.copy(
                ajustandoPrecio = true,
                error = null,
                calculandoPrecio = it.montoSugerido == null,
            )
        }
        if (a.montoSugerido == null) pedirSugerencia()
    }

    fun cerrarAjustePrecio() = _estado.update { it.copy(ajustandoPrecio = false) }

    /** "Ya lo pedí" / "Pedilo vos": cambia el monto sugerido al instante. */
    fun cambiarEsperaEnLocal(espera: Boolean) {
        _estado.update { it.copy(esperaEnLocal = espera) }
        // Recotiza: el cliente tiene que ver el precio nuevo al tocar, no
        // descubrirlo después en el popup.
        pedirSugerencia()
    }

    /**
     * El cliente movió el pin del mapa: se guarda el punto y se busca qué
     * dirección hay ahí.
     *
     * El texto se actualiza solo, así que el pin y la dirección nunca dicen
     * cosas distintas. Antes ganaba el texto, que es el dato menos preciso —
     * y el rider llegaba a la cuadra equivocada.
     */
    fun moverPin(lat: Double, lng: Double) {
        _estado.update { it.copy(origenLat = lat, origenLng = lng) }
        // Se cancela la consulta anterior: arrastrar el mapa dispara varios
        // avisos seguidos y Nominatim admite uno por segundo.
        reversaEnCurso?.cancel()
        reversaEnCurso = viewModelScope.launch(dispatcher) {
            kotlinx.coroutines.delay(500)
            when (val r = api.direccionEn(lat, lng)) {
                is Resultado.Ok -> {
                    val texto = r.valor
                    if (!texto.isNullOrBlank()) {
                        _estado.update { it.copy(origen = texto, direccionDelPin = texto) }
                    }
                }
                // Sin dirección el punto sigue valiendo: son las coordenadas
                // las que guían al rider, no el texto.
                is Resultado.Error -> Unit
            }
        }
    }

    private var reversaEnCurso: Job? = null

    /** El precio que el cliente ofrece, en centavos (viene de los +/- del popup). */
    /**
     * El monto que ofrece el cliente, en CENTAVOS.
     *
     * Se guarda aparte de `monto` (que está en soles enteros) porque los
     * pasos del popup son de 50 céntimos: al pasar por soles, `450 / 100` daba
     * "4" y al volver a leerlo eran 400 otra vez. El cliente tocaba "+" y el
     * número no se movía — quedaba atrapado en el mínimo.
     */
    fun cambiarMontoCentavos(centavos: Long) =
        _estado.update {
            it.copy(
                montoCentavos = centavos,
                // `monto` se mantiene al día para lo que todavía lo lee (el
                // envío del pedido), redondeado al sol como siempre.
                monto = (centavos / 100).toString(),
                error = null,
            )
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
                    destinoLat = a.destinoLat,
                    destinoLng = a.destinoLng,
                    // El del popup si lo tocó: ya viene en centavos y conserva
                    // los 50 de cada paso. `aCentavos(monto)` los perdía.
                    montoOfrecidoCentavos = a.montoCentavos ?: aCentavos(a.monto),
                    // El monto de compra SOLO en encomienda: es lo que el rider
                    // adelanta y el cliente le devuelve, nunca parte del flete.
                    montoCompraEstimadoCentavos = if (a.tipo == TIPO_ENCOMIENDA) aCentavos(a.montoCompra) else null,
                    notas = a.notas,
                    contacto = a.contacto,
                )
            ) {
                is Resultado.Ok -> {
                    _estado.update { it.copy(pidiendo = false, ajustandoPrecio = false) }
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
                    // Sin `carreraPorCalificar`: nadie califica un viaje que
                    // canceló él mismo.
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

/** Los estados en que la carrera ya tiene un motorizado a cargo. */
private val ESTADOS_CON_RIDER = setOf("aceptada", "recogida")

/**
 * Aplica la carrera que trajo el polling y decide si toca calificar.
 *
 * La calificación se dispara por la TRANSICIÓN, no por el estado: hay que
 * haber visto la carrera con un rider asignado y que después desaparezca o
 * quede entregada. Mirando solo el estado actual, el diálogo reaparecería en
 * cada vuelta del polling.
 *
 * Solo con rider: una carrera que nadie tomó y venció no tiene a quién
 * calificar.
 */
internal fun alLlegarLaCarrera(
    actual: ClienteUiState,
    nueva: CarreraClienteDto?,
): ClienteUiState {
    val anterior = actual.miCarrera
    // `aceptada`/`recogida` son justo los estados en que hay un rider asignado.
    val teniaRider = anterior != null && anterior.estado in ESTADOS_CON_RIDER
    val termino = teniaRider && (nueva == null || nueva.estado == "entregada")
    if (!termino) return actual.copy(miCarrera = nueva)

    // Ya la calificó (o dijo "ahora no") en esta misma sesión: no se insiste.
    val yaPreguntamos = actual.carreraPorCalificar?.id == anterior!!.id
    return actual.copy(
        miCarrera = nueva,
        carreraPorCalificar = if (yaPreguntamos) actual.carreraPorCalificar else anterior,
        // Las ofertas viejas no tienen sentido en la próxima carrera.
        ofertas = emptyList(),
    )
}
