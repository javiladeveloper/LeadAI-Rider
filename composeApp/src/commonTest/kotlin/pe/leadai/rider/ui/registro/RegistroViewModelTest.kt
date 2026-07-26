package pe.leadai.rider.ui.registro

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
import pe.leadai.rider.datos.ApiCliente
import pe.leadai.rider.datos.AuthApi
import pe.leadai.rider.datos.SesionRepositorio
import pe.leadai.rider.esperarCondicion
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
    val nombre = "registro_vm_test_${Random.nextInt()}.preferences_pb"
    val ruta = (FileSystem.SYSTEM_TEMPORARY_DIRECTORY.toString() + "/" + nombre).toPath()
    return PreferenceDataStoreFactory.createWithPath(produceFile = { ruta })
}

// Contrato real: `POST /auth/registro` devuelve el MISMO shape que
// `/auth/login` — usuario nuevo, siempre sin empresas (el alta segmentada
// crea el negocio en un paso aparte, T2).
private const val JSON_REGISTRO_OK = """
{
  "token": "hilo_u_nuevo123",
  "usuario": {"id": "u9", "email": "nueva@leadai-pe.com", "nombre": "Rosa"},
  "empresas": [],
  "esSuperAdmin": false
}
"""

private const val JSON_REGISTRO_ERROR = """{"error":"Ese email ya está registrado"}"""

private fun viewModelDePrueba(
    status: HttpStatusCode,
    contenido: String,
    dispatcher: kotlinx.coroutines.CoroutineDispatcher,
): Pair<RegistroViewModel, SesionRepositorio> {
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
    return RegistroViewModel(authApi, dispatcher) to sesionRepo
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class RegistroViewModelTest {

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
    fun registrar_feliz_guarda_sesion_y_llama_alExito() = runTest {
        val (vm, sesionRepo) = viewModelDePrueba(HttpStatusCode.Created, JSON_REGISTRO_OK, testDispatcher)
        vm.cambiarNombre("Rosa")
        vm.cambiarEmail("nueva@leadai-pe.com")
        vm.cambiarPassword("clave1234")
        vm.cambiarConfirmarPassword("clave1234")

        var seLlamoAlExito = false
        vm.registrar { seLlamoAlExito = true }
        vm.estado.esperarCondicion { !it.cargando }
        advanceUntilIdle()

        assertTrue(seLlamoAlExito)
        val guardada = sesionRepo.observar().first { it != null }
        assertNotNull(guardada)
        assertEquals("hilo_u_nuevo123", guardada.token)
        assertFalse(vm.estado.value.cargando)
        assertNull(vm.estado.value.error)
    }

    @Test
    fun registrar_con_email_ya_registrado_muestra_el_mensaje_del_backend() = runTest {
        val (vm, sesionRepo) = viewModelDePrueba(HttpStatusCode.Conflict, JSON_REGISTRO_ERROR, testDispatcher)
        vm.cambiarNombre("Rosa")
        vm.cambiarEmail("repetida@leadai-pe.com")
        vm.cambiarPassword("clave1234")
        vm.cambiarConfirmarPassword("clave1234")

        var seLlamoAlExito = false
        vm.registrar { seLlamoAlExito = true }
        vm.estado.esperarCondicion { !it.cargando }
        advanceUntilIdle()

        assertFalse(seLlamoAlExito)
        assertEquals("Ese email ya está registrado", vm.estado.value.error)
        assertFalse(vm.estado.value.cargando)
        assertNull(sesionRepo.observar().first())
    }

    @Test
    fun registrar_con_nombre_vacio_muestra_error_local_sin_llamar_al_backend() = runTest {
        val (vm, sesionRepo) = viewModelDePrueba(HttpStatusCode.Created, JSON_REGISTRO_OK, testDispatcher)
        vm.cambiarEmail("nueva@leadai-pe.com")
        vm.cambiarPassword("clave1234")
        vm.cambiarConfirmarPassword("clave1234")

        var seLlamoAlExito = false
        vm.registrar { seLlamoAlExito = true }

        assertFalse(seLlamoAlExito)
        assertEquals("Completa tu nombre", vm.estado.value.error)
        assertNull(sesionRepo.observar().first())
    }

    @Test
    fun registrar_con_email_sin_arroba_muestra_error_local() = runTest {
        val (vm, _) = viewModelDePrueba(HttpStatusCode.Created, JSON_REGISTRO_OK, testDispatcher)
        vm.cambiarNombre("Rosa")
        vm.cambiarEmail("correo-sin-arroba.com")
        vm.cambiarPassword("clave1234")
        vm.cambiarConfirmarPassword("clave1234")

        var seLlamoAlExito = false
        vm.registrar { seLlamoAlExito = true }

        assertFalse(seLlamoAlExito)
        assertEquals("Ingresa un correo válido", vm.estado.value.error)
    }

    @Test
    fun registrar_con_password_corta_muestra_error_local() = runTest {
        val (vm, _) = viewModelDePrueba(HttpStatusCode.Created, JSON_REGISTRO_OK, testDispatcher)
        vm.cambiarNombre("Rosa")
        vm.cambiarEmail("nueva@leadai-pe.com")
        vm.cambiarPassword("1234567")
        vm.cambiarConfirmarPassword("1234567")

        var seLlamoAlExito = false
        vm.registrar { seLlamoAlExito = true }

        assertFalse(seLlamoAlExito)
        assertEquals("La contraseña debe tener al menos 8 caracteres", vm.estado.value.error)
    }

    @Test
    fun registrar_con_passwords_que_no_coinciden_muestra_error_local() = runTest {
        val (vm, _) = viewModelDePrueba(HttpStatusCode.Created, JSON_REGISTRO_OK, testDispatcher)
        vm.cambiarNombre("Rosa")
        vm.cambiarEmail("nueva@leadai-pe.com")
        vm.cambiarPassword("clave1234")
        vm.cambiarConfirmarPassword("otraClave1234")

        var seLlamoAlExito = false
        vm.registrar { seLlamoAlExito = true }

        assertFalse(seLlamoAlExito)
        assertEquals("Las contraseñas no coinciden", vm.estado.value.error)
    }

    @Test
    fun cambiarNombre_cambiarEmail_cambiarPassword_limpian_error_previo() = runTest {
        val (vm, _) = viewModelDePrueba(HttpStatusCode.Conflict, JSON_REGISTRO_ERROR, testDispatcher)
        vm.cambiarNombre("Rosa")
        vm.cambiarEmail("repetida@leadai-pe.com")
        vm.cambiarPassword("clave1234")
        vm.cambiarConfirmarPassword("clave1234")
        vm.registrar { }
        vm.estado.esperarCondicion { !it.cargando }
        advanceUntilIdle()
        assertNotNull(vm.estado.value.error)

        vm.cambiarNombre("Otra")

        assertEquals("Otra", vm.estado.value.nombre)
        assertNull(vm.estado.value.error)
    }
}
