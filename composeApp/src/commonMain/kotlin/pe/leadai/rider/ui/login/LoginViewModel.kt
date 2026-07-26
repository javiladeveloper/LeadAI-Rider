package pe.leadai.rider.ui.login

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
import pe.leadai.rider.datos.SesionGuardada
import pe.leadai.rider.datos.SesionRepositorio
import pe.leadai.rider.push.RegistroPushRepositorio
import pe.leadai.rider.ui.login.obtenerIdTokenGoogle as obtenerIdTokenGoogleReal

/**
 * Estado inmutable de la pantalla de login. [cargando] y [cargandoGoogle] son
 * flags SEPARADOS a propósito (Task B6: "estados de carga propios") — cada
 * botón muestra su propio spinner sin robarle el estado al otro, aunque
 * `LoginPantalla` deshabilita AMBOS botones mientras cualquiera de los dos
 * esté en `true` (no tiene sentido dejar tocar "Entrar" mientras Google está
 * en vuelo, ni viceversa).
 */
data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val cargando: Boolean = false,
    val cargandoGoogle: Boolean = false,
    val error: String? = null,
)

private const val MENSAJE_CAMPOS_VACIOS = "Completa tu correo y contraseña"
private const val MENSAJE_GOOGLE_FALLO =
    "No se pudo iniciar con Google. Usa tu correo y contraseña 🙏"

/**
 * ViewModel de [pe.leadai.rider.ui.login.LoginPantalla]. Valida localmente que
 * los campos no estén vacíos (sin llamar al backend), delega el login a
 * [AuthApi] (que ya persiste la sesión en [SesionRepositorio] cuando es
 * exitoso — ver `AuthApi.kt`) y auto-elige la empresa activa cuando el
 * usuario solo tiene una, para saltarse el selector.
 *
 * [dispatcher] se inyecta (default `Dispatchers.Main.immediate`, el mismo
 * comportamiento de siempre en producción) para que los tests puedan pasar un
 * `StandardTestDispatcher` y controlar el avance con `advanceUntilIdle()` en
 * vez de recurrir a espera activa con tiempo real (directiva del review de
 * Task 4 — ver ARQUITECTURA.md, patrón de dispatcher).
 *
 * [registroPush] registra el token FCM del dispositivo tras un login exitoso
 * (Task 8) — fire-and-forget: no bloquea `alExito` ni afecta `cargando`/
 * `error`, porque push es una mejora y nunca debe demorar ni romper el login
 * si Firebase no está disponible (repo sin `google-services.json` todavía).
 *
 * [obtenerIdTokenGoogle] (Task B6) se inyecta con el mismo patrón que
 * `RegistroPushRepositorio.obtenerToken` — default al `expect/actual` real
 * (`obtenerIdTokenGoogle()` de nivel de archivo, `ui/login/ObtenerIdTokenGoogle.kt`),
 * los tests inyectan una lambda fake sin depender de Credential Manager.
 */
class LoginViewModel(
    private val authApi: AuthApi,
    private val sesion: SesionRepositorio,
    private val registroPush: RegistroPushRepositorio,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val obtenerIdTokenGoogle: suspend () -> String? = { obtenerIdTokenGoogleReal() },
) : ViewModel() {

    private val _estado = MutableStateFlow(LoginUiState())
    val estado: StateFlow<LoginUiState> = _estado.asStateFlow()

    fun cambiarEmail(valor: String) {
        _estado.update { it.copy(email = valor, error = null) }
    }

    fun cambiarPassword(valor: String) {
        _estado.update { it.copy(password = valor, error = null) }
    }

    /** Intenta iniciar sesión; [alExito] avisa que la sesión ya quedó guardada. */
    fun entrar(alExito: () -> Unit) {
        val actual = _estado.value
        if (actual.email.isBlank() || actual.password.isBlank()) {
            _estado.update { it.copy(error = MENSAJE_CAMPOS_VACIOS) }
            return
        }

        _estado.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch(dispatcher) {
            aplicarResultadoLogin(authApi.login(actual.email, actual.password), alExito)
        }
    }

    /**
     * Intenta iniciar sesión con Google (Task B6): pide el idToken vía
     * [obtenerIdTokenGoogle] y, si lo consigue, llama `AuthApi.loginGoogle` —
     * reusa [aplicarResultadoLogin], el MISMO flujo de sesión/registro
     * push/navegación que [entrar] (`/auth/google` devuelve la misma forma de
     * sesión que `/auth/login`, ver `AuthApi.kt`). Si Credential Manager no
     * devuelve token (sin Play Services, el usuario cancela, `DEVELOPER_ERROR`
     * por SHA-1 faltante), muestra un error amable SIN llamar al backend.
     */
    fun entrarConGoogle(alExito: () -> Unit) {
        _estado.update { it.copy(cargando = true, cargandoGoogle = true, error = null) }
        viewModelScope.launch(dispatcher) {
            val idToken = obtenerIdTokenGoogle()
            if (idToken == null) {
                // Con detalle técnico disponible (Android lo llena en cada
                // catch), se muestra debajo del mensaje amable — sin él, un
                // fallo en el celular de un usuario es una caja negra
                // (aprendido en vivo 2026-07-23).
                val detalle = DiagnosticoGoogle.ultimoDetalle
                val mensaje = if (detalle != null) "$MENSAJE_GOOGLE_FALLO\n($detalle)" else MENSAJE_GOOGLE_FALLO
                _estado.update {
                    it.copy(cargando = false, cargandoGoogle = false, error = mensaje)
                }
                return@launch
            }
            aplicarResultadoLogin(authApi.loginGoogle(idToken), alExito)
        }
    }

    /**
     * Común a [entrar]/[entrarConGoogle]: dispara el registro push
     * fire-and-forget y reporta [alExito]; en error, expone el mensaje del
     * backend. Apaga SIEMPRE ambos flags de carga (a [entrar] no le afecta
     * apagar `cargandoGoogle`, que ya estaba en `false`). Debe llamarse ya
     * DENTRO de `viewModelScope.launch(dispatcher)`.
     *
     * A diferencia de la app de negocios, acá no se elige empresa: el rider es
     * un rol de plataforma y trabaja sin tenant (ver `SesionGuardada`).
     */
    private suspend fun aplicarResultadoLogin(
        resultado: Resultado<SesionGuardada>,
        alExito: () -> Unit,
    ) {
        when (resultado) {
            is Resultado.Ok -> {
                _estado.update { it.copy(cargando = false, cargandoGoogle = false, error = null) }
                // Fire-and-forget en un launch aparte: si falla (sin Firebase,
                // sin conexión) no debe afectar `cargando`/`error` del login,
                // que ya se resolvió arriba.
                viewModelScope.launch(dispatcher) { registroPush.registrar() }
                alExito()
            }
            is Resultado.Error -> {
                _estado.update {
                    it.copy(cargando = false, cargandoGoogle = false, error = resultado.mensaje)
                }
            }
        }
    }
}
