package pe.leadai.rider.ui.registro

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pe.leadai.rider.datos.AuthApi
import pe.leadai.rider.datos.Resultado

/** Estado inmutable de la pantalla de registro. */
data class RegistroUiState(
    val nombre: String = "",
    val email: String = "",
    val password: String = "",
    val confirmarPassword: String = "",
    val cargando: Boolean = false,
    val error: String? = null,
)

private const val MIN_LARGO_PASSWORD = 8

private const val MENSAJE_NOMBRE_VACIO = "Completa tu nombre"
private const val MENSAJE_EMAIL_INVALIDO = "Ingresa un correo válido"
private const val MENSAJE_PASSWORD_CORTA = "La contraseña debe tener al menos $MIN_LARGO_PASSWORD caracteres"
private const val MENSAJE_PASSWORDS_NO_COINCIDEN = "Las contraseñas no coinciden"

/**
 * Validaciones locales del formulario de registro, en el orden que el
 * usuario las va a corregir (nombre → email → contraseña → confirmación).
 * Función PURA, un solo mensaje a la vez (mismo estilo simple que
 * `LoginViewModel`/`validarFormulario` de Ajustes — ver ARQUITECTURA.md).
 * Corre ANTES de tocar la red: el usuario ve el error al instante en vez de
 * esperar el 400/409 del backend.
 */
internal fun validarRegistro(estado: RegistroUiState): String? {
    if (estado.nombre.isBlank()) return MENSAJE_NOMBRE_VACIO
    // Chequeo simple de "tiene @ y algo después" — la validación fuerte del
    // formato la hace Zod en el backend (`z.string().email()`); acá solo
    // queremos frenar el caso obvio (typo, campo mal llenado) sin duplicar
    // una regex completa de RFC 5322 en el cliente.
    if (!estado.email.contains("@") || estado.email.substringAfterLast("@").isBlank()) {
        return MENSAJE_EMAIL_INVALIDO
    }
    if (estado.password.length < MIN_LARGO_PASSWORD) return MENSAJE_PASSWORD_CORTA
    if (estado.password != estado.confirmarPassword) return MENSAJE_PASSWORDS_NO_COINCIDEN
    return null
}

/**
 * ViewModel de [pe.leadai.rider.ui.registro.RegistroPantalla] (Fase B.5, Task
 * T1). Valida localmente ([validarRegistro]) antes de llamar al backend,
 * delega a [AuthApi.registrar] (que ya persiste la sesión — mismo
 * `aSesionGuardada` que el login, ver `AuthApi.kt`) y reporta [alExito] SIN
 * distinguir cantidad de empresas: un usuario recién registrado SIEMPRE
 * tiene `empresas: []`, así que la navegación post-registro es fija
 * (`crearNegocio`, ver `ui/navegacion/Navegacion.kt`) — a diferencia de
 * `LoginViewModel.entrar`, que sí necesita decidir entre selector/home.
 *
 * [dispatcher] inyectado (default `Dispatchers.Main.immediate`), mismo
 * patrón que el resto del repo — ver ARQUITECTURA.md.
 */
class RegistroViewModel(
    private val authApi: AuthApi,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) : ViewModel() {

    private val _estado = MutableStateFlow(RegistroUiState())
    val estado: StateFlow<RegistroUiState> = _estado.asStateFlow()

    fun cambiarNombre(valor: String) {
        _estado.update { it.copy(nombre = valor, error = null) }
    }

    fun cambiarEmail(valor: String) {
        _estado.update { it.copy(email = valor, error = null) }
    }

    fun cambiarPassword(valor: String) {
        _estado.update { it.copy(password = valor, error = null) }
    }

    fun cambiarConfirmarPassword(valor: String) {
        _estado.update { it.copy(confirmarPassword = valor, error = null) }
    }

    /** Intenta crear la cuenta. [alExito] se llama una vez que la sesión quedó guardada. */
    fun registrar(alExito: () -> Unit) {
        val actual = _estado.value
        val errorLocal = validarRegistro(actual)
        if (errorLocal != null) {
            _estado.update { it.copy(error = errorLocal) }
            return
        }

        _estado.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch(dispatcher) {
            when (val resultado = authApi.registrar(actual.email, actual.password, actual.nombre)) {
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
