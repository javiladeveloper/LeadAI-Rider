package pe.leadai.rider.ui.login

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okio.FileSystem
import okio.Path.Companion.toPath
import pe.leadai.rider.esperarCondicion
import pe.leadai.rider.datos.ApiCliente
import pe.leadai.rider.datos.AuthApi
import pe.leadai.rider.datos.SesionRepositorio
import pe.leadai.rider.push.RegistroPushRepositorio
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun dataStoreDePrueba(): DataStore<Preferences> {
    val nombre = "login_vm_test_${Random.nextInt()}.preferences_pb"
    val ruta = (FileSystem.SYSTEM_TEMPORARY_DIRECTORY.toString() + "/" + nombre).toPath()
    return PreferenceDataStoreFactory.createWithPath(produceFile = { ruta })
}

private const val JSON_LOGIN_UNA_EMPRESA = """
{
  "token": "hilo_u_1empresa",
  "usuario": {"id": "u1", "email": "guisella@leadai-pe.com", "nombre": "Guisella"},
  "empresas": [{"tenantId": "t1", "nombre": "Pollería Doña Rosa", "rol": "dueño"}],
  "esSuperAdmin": false
}
"""

private const val JSON_LOGIN_DOS_EMPRESAS = """
{
  "token": "hilo_u_2empresas",
  "usuario": {"id": "u2", "email": "multi@leadai-pe.com", "nombre": "Multi"},
  "empresas": [
    {"tenantId": "t1", "nombre": "Pollería Doña Rosa", "rol": "dueño"},
    {"tenantId": "t2", "nombre": "Chifa El Dragón", "rol": "dueño"}
  ],
  "esSuperAdmin": false
}
"""

private const val JSON_LOGIN_ERROR = """{"error":"Email o contraseña incorrectos"}"""

/**
 * Arma un [LoginViewModel] con un [AuthApi] respaldado por un `MockEngine` de
 * la respuesta dada, usando [dispatcher] inyectado para que el test controle
 * el avance de las corrutinas con tiempo virtual (`advanceUntilIdle()`) en
 * vez de espera activa con tiempo real.
 */
private fun viewModelDePrueba(
    status: HttpStatusCode,
    contenido: String,
    dispatcher: kotlinx.coroutines.CoroutineDispatcher,
): Pair<LoginViewModel, SesionRepositorio> {
    val sesionRepo = SesionRepositorio(dataStoreDePrueba())
    val engine = MockEngine { _ ->
        respond(
            content = contenido,
            status = status,
            headers = headersOf(HttpHeaders.ContentType, "application/json"),
        )
    }
    val api = ApiCliente(sesion = sesionRepo, engine = engine)
    val authApi = AuthApi(api, sesionRepo)
    // obtenerToken = { null }: no-op de push en estos tests (no es lo que se
    // está probando acá) — evita depender del `expect/actual` real de
    // `tokenPushActual()` y de que el MockEngine compartido tenga que
    // entender también las rutas de `/dispositivos-push`.
    val registroPush = RegistroPushRepositorio(api, obtenerToken = { null })
    return LoginViewModel(authApi, sesionRepo, registroPush, dispatcher) to sesionRepo
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun antes() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun despues() {
        Dispatchers.resetMain()
    }

    @Test
    fun entrar_guarda_la_sesion_y_avisa_al_llamador() = runTest {
        val (vm, sesionRepo) = viewModelDePrueba(HttpStatusCode.OK, JSON_LOGIN_UNA_EMPRESA, testDispatcher)
        vm.cambiarEmail("guisella@leadai-pe.com")
        vm.cambiarPassword("clave123")

        var aviso = false
        vm.entrar { aviso = true }
        // El request real corre en el engine de Ktor (MockEngine) y la
        // escritura en DataStore, ambos en su propio dispatcher — fuera del
        // scheduler virtual de `testDispatcher` (mismo motivo documentado en
        // ARQUITECTURA.md). Se espera la condición real sobre el StateFlow en
        // vez de asumir que `advanceUntilIdle()` alcanza a sincronizarlos.
        vm.estado.esperarCondicion { !it.cargando }
        advanceUntilIdle()

        assertTrue(aviso)
        val guardada = sesionRepo.observar().first { it != null }
        assertNotNull(guardada)
        assertEquals("hilo_u_1empresa", guardada.token)
        assertFalse(vm.estado.value.cargando)
        assertNull(vm.estado.value.error)
    }

    @Test
    fun entrar_con_varias_empresas_entra_igual_sin_pedir_elegir_negocio() = runTest {
        // Un rider puede además ser dueño de restaurantes, así que la sesión
        // puede traer varias empresas. Esta app ignora esa parte: no hay
        // selector de negocio, y tener 2 empresas no cambia el camino de
        // entrada (en la app de negocios sí lo hacía).
        val (vm, sesionRepo) = viewModelDePrueba(HttpStatusCode.OK, JSON_LOGIN_DOS_EMPRESAS, testDispatcher)
        vm.cambiarEmail("multi@leadai-pe.com")
        vm.cambiarPassword("clave123")

        var aviso = false
        vm.entrar { aviso = true }
        vm.estado.esperarCondicion { !it.cargando }
        advanceUntilIdle()

        assertTrue(aviso)
        val guardada = sesionRepo.observar().first { it != null }
        assertNotNull(guardada)
        assertFalse(vm.estado.value.cargando)
        assertNull(vm.estado.value.error)
    }

    @Test
    fun entrar_con_credenciales_invalidas_muestra_el_mensaje_del_backend() = runTest {
        val (vm, sesionRepo) = viewModelDePrueba(HttpStatusCode.Unauthorized, JSON_LOGIN_ERROR, testDispatcher)
        vm.cambiarEmail("guisella@leadai-pe.com")
        vm.cambiarPassword("clave-incorrecta")

        var seLlamoAlExito = false
        vm.entrar { seLlamoAlExito = true }
        vm.estado.esperarCondicion { !it.cargando }
        advanceUntilIdle()

        assertFalse(seLlamoAlExito)
        assertEquals("Email o contraseña incorrectos", vm.estado.value.error)
        assertFalse(vm.estado.value.cargando)
        assertNull(sesionRepo.observar().first())
    }

    @Test
    fun entrar_con_campos_vacios_muestra_error_local_sin_llamar_al_backend() = runTest {
        val (vm, sesionRepo) = viewModelDePrueba(HttpStatusCode.OK, JSON_LOGIN_UNA_EMPRESA, testDispatcher)
        // email y password quedan vacíos (default del estado inicial)

        var seLlamoAlExito = false
        vm.entrar { seLlamoAlExito = true }

        assertFalse(seLlamoAlExito)
        assertEquals("Completa tu correo y contraseña", vm.estado.value.error)
        assertFalse(vm.estado.value.cargando)
        assertNull(sesionRepo.observar().first())
    }

    @Test
    fun cambiarEmail_y_cambiarPassword_actualizan_el_estado_y_limpian_error_previo() = runTest {
        val (vm, _) = viewModelDePrueba(HttpStatusCode.Unauthorized, JSON_LOGIN_ERROR, testDispatcher)
        vm.cambiarEmail("a@b.com")
        vm.cambiarPassword("x")
        vm.entrar { }
        vm.estado.esperarCondicion { !it.cargando }
        advanceUntilIdle()
        assertNotNull(vm.estado.value.error)

        vm.cambiarEmail("otro@b.com")

        assertEquals("otro@b.com", vm.estado.value.email)
        assertNull(vm.estado.value.error)
        assertTrue(vm.estado.value.password.isNotEmpty())
    }

    @Test
    fun entrarConGoogle_con_idToken_guarda_sesion_y_navega() = runTest {
        val sesionRepo = SesionRepositorio(dataStoreDePrueba())
        val engine = MockEngine { request ->
            assertEquals("/auth/google", request.url.encodedPath)
            respond(
                content = JSON_LOGIN_UNA_EMPRESA,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val api = ApiCliente(sesion = sesionRepo, engine = engine)
        val authApi = AuthApi(api, sesionRepo)
        val registroPush = RegistroPushRepositorio(api, obtenerToken = { null })
        val vm = LoginViewModel(
            authApi = authApi,
            sesion = sesionRepo,
            registroPush = registroPush,
            dispatcher = testDispatcher,
            obtenerIdTokenGoogle = { "id-token-fake" },
        )

        var aviso = false
        vm.entrarConGoogle { aviso = true }
        vm.estado.esperarCondicion { !it.cargando }
        advanceUntilIdle()

        assertTrue(aviso)
        val guardada = sesionRepo.observar().first { it != null }
        assertNotNull(guardada)
        assertEquals("hilo_u_1empresa", guardada.token)
        assertFalse(vm.estado.value.cargando)
        assertNull(vm.estado.value.error)
    }

    @Test
    fun entrarConGoogle_con_idToken_null_muestra_error_amable_sin_llamar_la_api() = runTest {
        val sesionRepo = SesionRepositorio(dataStoreDePrueba())
        val engine = MockEngine { _ ->
            throw AssertionError("No debería llamar a la API cuando el idToken es null")
        }
        val api = ApiCliente(sesion = sesionRepo, engine = engine)
        val authApi = AuthApi(api, sesionRepo)
        val registroPush = RegistroPushRepositorio(api, obtenerToken = { null })
        val vm = LoginViewModel(
            authApi = authApi,
            sesion = sesionRepo,
            registroPush = registroPush,
            dispatcher = testDispatcher,
            obtenerIdTokenGoogle = { null },
        )

        var seLlamoAlExito = false
        vm.entrarConGoogle { seLlamoAlExito = true }
        vm.estado.esperarCondicion { !it.cargando }
        advanceUntilIdle()

        assertFalse(seLlamoAlExito)
        assertEquals("No se pudo iniciar con Google. Usa tu correo y contraseña 🙏", vm.estado.value.error)
        assertFalse(vm.estado.value.cargando)
        assertNull(sesionRepo.observar().first())
    }

    @Test
    fun entrarConGoogle_con_error_del_backend_muestra_el_mensaje() = runTest {
        val sesionRepo = SesionRepositorio(dataStoreDePrueba())
        val engine = MockEngine { _ ->
            respond(
                content = JSON_LOGIN_ERROR,
                status = HttpStatusCode.Unauthorized,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val api = ApiCliente(sesion = sesionRepo, engine = engine)
        val authApi = AuthApi(api, sesionRepo)
        val registroPush = RegistroPushRepositorio(api, obtenerToken = { null })
        val vm = LoginViewModel(
            authApi = authApi,
            sesion = sesionRepo,
            registroPush = registroPush,
            dispatcher = testDispatcher,
            obtenerIdTokenGoogle = { "id-token-fake" },
        )

        var seLlamoAlExito = false
        vm.entrarConGoogle { seLlamoAlExito = true }
        vm.estado.esperarCondicion { !it.cargando }
        advanceUntilIdle()

        assertFalse(seLlamoAlExito)
        assertEquals("Email o contraseña incorrectos", vm.estado.value.error)
        assertFalse(vm.estado.value.cargando)
        assertNull(sesionRepo.observar().first())
    }

    @Test
    fun entrarConGoogle_exitoso_dispara_el_registro_push_fire_and_forget() = runTest {
        val sesionRepo = SesionRepositorio(dataStoreDePrueba())
        val seLlamoDispositivosPush = kotlinx.coroutines.flow.MutableStateFlow(false)
        val engine = MockEngine { request ->
            if (request.url.encodedPath == "/dispositivos-push") {
                seLlamoDispositivosPush.value = true
                respond(
                    content = """{"ok":true}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            } else {
                respond(
                    content = JSON_LOGIN_UNA_EMPRESA,
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }
        }
        val api = ApiCliente(sesion = sesionRepo, engine = engine)
        val authApi = AuthApi(api, sesionRepo)
        val registroPush = RegistroPushRepositorio(api, obtenerToken = { "token-fake-google" })
        val vm = LoginViewModel(
            authApi = authApi,
            sesion = sesionRepo,
            registroPush = registroPush,
            dispatcher = testDispatcher,
            obtenerIdTokenGoogle = { "id-token-fake" },
        )

        vm.entrarConGoogle { }
        vm.estado.esperarCondicion { !it.cargando }
        advanceUntilIdle()
        seLlamoDispositivosPush.esperarCondicion { it }

        assertTrue(seLlamoDispositivosPush.value)
    }

    @Test
    fun entrar_exitoso_dispara_el_registro_push_fire_and_forget() = runTest {
        val sesionRepo = SesionRepositorio(dataStoreDePrueba())
        // MutableStateFlow (no un `var` simple) para poder usar
        // `esperarCondicion`: el registro push corre en un `launch` aparte
        // (fire-and-forget) fuera del scheduler virtual — mismo motivo que
        // `ARQUITECTURA.md` documenta para DataStore/MockEngine, así que
        // `advanceUntilIdle()` solo no alcanza a garantizar que ya corrió.
        val seLlamoDispositivosPush = kotlinx.coroutines.flow.MutableStateFlow(false)
        val engine = MockEngine { request ->
            if (request.url.encodedPath == "/dispositivos-push") {
                seLlamoDispositivosPush.value = true
                respond(
                    content = """{"ok":true}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            } else {
                respond(
                    content = JSON_LOGIN_UNA_EMPRESA,
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }
        }
        val api = ApiCliente(sesion = sesionRepo, engine = engine)
        val authApi = AuthApi(api, sesionRepo)
        val registroPush = RegistroPushRepositorio(api, obtenerToken = { "token-fake-login" })
        val vm = LoginViewModel(authApi, sesionRepo, registroPush, testDispatcher)

        vm.cambiarEmail("guisella@leadai-pe.com")
        vm.cambiarPassword("clave123")
        vm.entrar { }
        vm.estado.esperarCondicion { !it.cargando }
        advanceUntilIdle()
        seLlamoDispositivosPush.esperarCondicion { it }

        assertTrue(seLlamoDispositivosPush.value)
    }
}
