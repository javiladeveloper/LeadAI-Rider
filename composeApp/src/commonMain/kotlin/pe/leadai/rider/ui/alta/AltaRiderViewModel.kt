package pe.leadai.rider.ui.alta

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
import pe.leadai.rider.datos.Resultado

/** Estado inmutable de la pantalla [AltaRiderPantalla]. */
data class AltaRiderUiState(
    /** Distrito elegido del catálogo (el del backend para Lima, el local para el resto). */
    val distrito: String? = null,
    val distritosDisponibles: List<String> = emptyList(),
    /**
     * Departamento del rider (Fase E, decisión 8: ubicación país →
     * departamento → distrito; el país es Perú fijo por ahora). Con "Lima"
     * el distrito sale del catálogo del backend; con otro departamento, del
     * catálogo local ([DISTRITOS_POR_DEPARTAMENTO]) y viaja como
     * "Distrito, Departamento" (el backend acepta string libre hasta 60 —
     * el ubigeo real llega con las filas 15/16 del handoff).
     */
    val departamento: String = "Lima",
    /** Texto libre del distrito cuando el rider eligió "Otro…" en el dropdown. */
    val distritoLibre: String = "",
    /** DNI del rider (fila 16) — 8 dígitos, obligatorio. */
    val dni: String = "",
    /** `true` mientras `POST /validar-dni` está en vuelo (spinner del campo). */
    val validandoDni: Boolean = false,
    /**
     * Resultado de la validación MAXFIND: el nombre oficial si lo encontró,
     * `""` si consultó y NO lo encontró (se muestra "lo verificaremos
     * manualmente" — sin bloquear, decisión 8), `null` si aún no se validó.
     */
    val nombreOficialDni: String? = null,
    val telefono: String = "",
    /** Placa del vehículo — opcional (contrato: `POST /motorizados/mi-perfil {distrito, telefono?, placa?}`). */
    val placa: String = "",
    /** `moto` | `auto` — de qué depende cuánto se sugiere cobrar por km. */
    val tipoVehiculo: String = "moto",
    val cargando: Boolean = false,
    val error: String? = null,
)

private const val MENSAJE_SIN_DISTRITO = "Elige tu distrito"
private const val MENSAJE_SIN_DNI = "Tu DNI de 8 dígitos — lo necesitamos para verificarte"
private const val MENSAJE_PLACA_INVALIDA = "Esa placa se ve rara — revísala (ej: ABC-123 o 1234-AB)"
private const val MENSAJE_SIN_TELEFONO = "Ponle tu teléfono para que te contactemos"
private const val MENSAJE_TELEFONO_CORTO = "Ese teléfono se ve muy corto — revísalo"

/** Los 25 departamentos del Perú (+ Callao como región) — Lima primero por ser el caso mayoritario. */
val DEPARTAMENTOS_PERU = listOf(
    "Lima", "Amazonas", "Áncash", "Apurímac", "Arequipa", "Ayacucho",
    "Cajamarca", "Callao", "Cusco", "Huancavelica", "Huánuco", "Ica",
    "Junín", "La Libertad", "Lambayeque", "Loreto", "Madre de Dios",
    "Moquegua", "Pasco", "Piura", "Puno", "San Martín", "Tacna",
    "Tumbes", "Ucayali",
)

/**
 * Inversa de [distritoParaBackend] para PRE-LLENAR el modo edición del
 * rider: "Pocollay, Tacna" → (Tacna, Pocollay); sin sufijo conocido →
 * (Lima, valor tal cual).
 */
internal fun ubicacionDesdeBackend(valor: String): Pair<String, String> {
    val idx = valor.lastIndexOf(", ")
    if (idx > 0) {
        val departamento = valor.substring(idx + 2)
        if (departamento in DEPARTAMENTOS_PERU) return departamento to valor.substring(0, idx)
    }
    return "Lima" to valor
}

/**
 * El `distrito` que viaja al backend (string libre, máx 60): el elegido del
 * catálogo (local por departamento, o el del backend para Lima), con el
 * departamento como sufijo cuando no es Lima ("Pocollay, Tacna"). Con
 * "Otro…" elegido, va el texto libre del rider. `null` = falta el dato.
 * Estructurado "a mano" hasta que el ubigeo real llegue (filas 15/16).
 */
internal fun distritoParaBackend(
    departamento: String,
    distritoSeleccionado: String?,
    distritoLibre: String,
): String? {
    val distrito = if (esOtroDistrito(distritoSeleccionado)) {
        distritoLibre.trim().takeIf { it.isNotBlank() }
    } else {
        distritoSeleccionado?.takeIf { it.isNotBlank() }
    } ?: return null
    return if (departamento == "Lima") distrito else "$distrito, $departamento"
}

/**
 * Normaliza la placa al escribir: MAYÚSCULAS, sin espacios, solo
 * alfanuméricos y guion, máx 10 (límite del backend). No fuerza un formato
 * — las placas peruanas varían (autos ABC-123, motos 1234-AB, series
 * viejas) y bloquear de más deja riders reales afuera.
 */
internal fun normalizarPlaca(valor: String): String =
    valor.uppercase().filter { it.isLetterOrDigit() || it == '-' }.take(10)

/**
 * ¿La placa (ya normalizada) luce como una placa peruana? Chequeo LAXO:
 * vacía es válida (opcional — hay riders en bicicleta), y con contenido
 * exige 5-7 alfanuméricos con guion opcional. La verificación REAL
 * (titular en SUNARP, SOAT) es del panel de verificación (nivel 0 de la
 * spec Fase E) — esto solo filtra typos obvios.
 */
internal fun placaLuceValida(placa: String): Boolean {
    if (placa.isBlank()) return true
    val alfanumericos = placa.count { it.isLetterOrDigit() }
    return alfanumericos in 5..7 && placa.count { it == '-' } <= 1
}

/**
 * ViewModel de [AltaRiderPantalla]: el alta del perfil de motorizado
 * (`POST /motorizados/mi-perfil`) — rol de PLATAFORMA, no una membresía a
 * un tenant (ver ARQUITECTURA.md Fase E), por eso no toca `/empresas` ni
 * nada de negocio. En esta app SIEMPRE es motorizado: no hay tarjetas ni
 * bifurcación, el formulario arranca directo.
 *
 * El mismo ViewModel sirve al alta post-registro y al "✏️ Editar mi perfil"
 * de la sala de espera ([prepararEdicion]) — mismo endpoint, que es un
 * upsert.
 *
 * [dispatcher] inyectado, mismo patrón que el resto del repo.
 */
class AltaRiderViewModel(
    private val motorizadosApi: MotorizadosApi,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) : ViewModel() {

    private val _estado = MutableStateFlow(AltaRiderUiState())
    val estado: StateFlow<AltaRiderUiState> = _estado.asStateFlow()

    /**
     * Trae el catálogo de distritos de Lima (`GET /motorizados/distritos`) —
     * el dropdown necesita esa lista antes de que el rider pueda elegir. Lo
     * llama la pantalla al montarse, ya que acá el formulario se muestra
     * siempre (no hay tarjeta que dispare la carga).
     */
    fun cargarDistritos() {
        if (_estado.value.distritosDisponibles.isNotEmpty()) return
        viewModelScope.launch(dispatcher) {
            when (val resultado = motorizadosApi.distritos()) {
                is Resultado.Ok -> _estado.update { it.copy(distritosDisponibles = resultado.valor) }
                is Resultado.Error -> Unit // catálogo es un nice-to-have; el error real llega recién al intentar guardar
            }
        }
    }

    /**
     * Modo EDICIÓN ("✏️ Editar mi perfil" desde la sala de espera):
     * PRE-LLENA el formulario con el perfil actual (`GET /mi-perfil`) — antes
     * el botón te tiraba al alta vacía y había que escribir todo de nuevo
     * (bug en vivo 2026-07-23). El DNI se pre-llena vía [cambiarDni] para que
     * la validación en vivo vuelva a mostrar el "✓ Nombre".
     */
    fun prepararEdicion() {
        cargarDistritos()
        viewModelScope.launch(dispatcher) {
            when (val resultado = motorizadosApi.miPerfil()) {
                is Resultado.Ok -> {
                    val perfil = resultado.valor ?: return@launch
                    val (departamento, distrito) = ubicacionDesdeBackend(perfil.distrito)
                    _estado.update {
                        it.copy(
                            departamento = departamento,
                            distrito = distrito,
                            telefono = perfil.telefono.orEmpty(),
                            placa = perfil.placa.orEmpty(),
                            tipoVehiculo = perfil.tipoVehiculo,
                        )
                    }
                    perfil.dni?.let { dni -> cambiarDni(dni) }
                }
                is Resultado.Error -> Unit // sin red: el form queda editable en blanco
            }
        }
    }

    /** Cambia el departamento — el distrito elegido se limpia (era de otro catálogo). */
    fun elegirDepartamento(departamento: String) {
        _estado.update {
            it.copy(departamento = departamento, distrito = null, distritoLibre = "", error = null)
        }
    }

    /** Elegir distrito del dropdown. */
    fun elegirDistrito(distrito: String) {
        _estado.update { it.copy(distrito = distrito, error = null) }
    }

    fun cambiarDistritoLibre(valor: String) {
        _estado.update { it.copy(distritoLibre = valor, error = null) }
    }

    /**
     * DNI del rider: solo dígitos, máx 8. Al COMPLETAR los 8 se dispara la
     * validación en vivo contra el backend (proxy MAXFIND) — el resultado
     * es informativo ("✓ Nombre Oficial" / "lo verificaremos manualmente"),
     * jamás bloquea el alta (decisión 8).
     */
    fun cambiarDni(valor: String) {
        val limpio = valor.filter { it.isDigit() }.take(8)
        val cambio = limpio != _estado.value.dni
        _estado.update { it.copy(dni = limpio, error = null, nombreOficialDni = if (cambio) null else it.nombreOficialDni) }
        if (cambio && limpio.length == 8) {
            _estado.update { it.copy(validandoDni = true) }
            viewModelScope.launch(dispatcher) {
                val resultado = motorizadosApi.validarDni(limpio)
                _estado.update {
                    // Si el usuario siguió editando mientras respondía, se descarta.
                    if (it.dni != limpio) return@update it.copy(validandoDni = false)
                    when (resultado) {
                        is Resultado.Ok -> it.copy(
                            validandoDni = false,
                            nombreOficialDni = if (resultado.valor.encontrado) resultado.valor.nombreCompleto.orEmpty() else "",
                        )
                        // Error de red del propio backend: mismo trato que
                        // "no encontrado" — informativo, sin bloquear.
                        is Resultado.Error -> it.copy(validandoDni = false, nombreOficialDni = "")
                    }
                }
            }
        }
    }

    fun cambiarTelefono(valor: String) {
        _estado.update { it.copy(telefono = valor, error = null) }
    }

    fun cambiarPlaca(valor: String) {
        _estado.update { it.copy(placa = normalizarPlaca(valor), error = null) }
    }

    /**
     * En qué se mueve el rider: `moto` o `auto`. El backend lo usa para
     * sugerir el monto — un auto consume ~3x más que una moto, así que su
     * tarifa por km es mayor.
     */
    fun elegirTipoVehiculo(tipo: String) {
        _estado.update { it.copy(tipoVehiculo = tipo, error = null) }
    }

    /**
     * Intenta dar de alta (o actualizar) el perfil de motorizado:
     * `POST /motorizados/mi-perfil`. [alExito] no recibe parámetros: el
     * llamador siempre navega a la sala de espera del rider.
     */
    fun guardar(alExito: () -> Unit) {
        val actual = _estado.value
        val errorLocal = validarAltaRider(actual)
        if (errorLocal != null) {
            _estado.update { it.copy(error = errorLocal) }
            return
        }

        _estado.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch(dispatcher) {
            when (
                val resultado = motorizadosApi.guardarMiPerfil(
                    distrito = distritoParaBackend(actual.departamento, actual.distrito, actual.distritoLibre)!!,
                    telefono = actual.telefono,
                    placa = actual.placa.ifBlank { null },
                    dni = actual.dni,
                    tipoVehiculo = actual.tipoVehiculo,
                )
            ) {
                is Resultado.Ok -> {
                    _estado.update { it.copy(cargando = false, error = null) }
                    alExito()
                }
                is Resultado.Error -> _estado.update {
                    it.copy(cargando = false, error = resultado.mensaje)
                }
            }
        }
    }
}

/**
 * Validaciones locales del formulario, en el orden que el rider lo completa:
 * distrito → DNI → placa → teléfono. Placa es opcional (contrato real:
 * `POST /motorizados/mi-perfil {distrito, telefono?, placa?}` — aunque el
 * backend también lo permita opcional, acá se pide teléfono siempre para
 * poder contactar al motorizado cuando haya carreras). Función PURA, un solo
 * mensaje a la vez — mismo estilo que `validarRegistro`.
 */
internal fun validarAltaRider(estado: AltaRiderUiState): String? {
    if (distritoParaBackend(estado.departamento, estado.distrito, estado.distritoLibre) == null) {
        return MENSAJE_SIN_DISTRITO
    }
    // 8 dígitos exactos (contrato: regex ^\d{8}$). Que MAXFIND lo encuentre
    // o no NO importa acá — la validación es ligera (decisión 8).
    if (estado.dni.length != 8) return MENSAJE_SIN_DNI
    if (!placaLuceValida(estado.placa)) return MENSAJE_PLACA_INVALIDA
    if (estado.telefono.isBlank()) return MENSAJE_SIN_TELEFONO
    // El contrato real (`crearSchema` en `motorizados.ts`) exige mínimo 6
    // caracteres — validar acá evita un 400 con mensaje crudo de Zod.
    if (estado.telefono.trim().length < 6) return MENSAJE_TELEFONO_CORTO
    return null
}
