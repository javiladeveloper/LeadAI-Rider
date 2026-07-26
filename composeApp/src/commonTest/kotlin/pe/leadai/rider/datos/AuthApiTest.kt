package pe.leadai.rider.datos

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path.Companion.toPath
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun dataStoreDePrueba(): DataStore<Preferences> {
    val nombre = "auth_test_${Random.nextInt()}.preferences_pb"
    val ruta = (FileSystem.SYSTEM_TEMPORARY_DIRECTORY.toString() + "/" + nombre).toPath()
    return PreferenceDataStoreFactory.createWithPath(produceFile = { ruta })
}

private const val JSON_LOGIN_OK = """
{
  "token": "hilo_u_9f8a7b6c",
  "usuario": {"id": "u1", "email": "guisella@leadai-pe.com", "nombre": "Guisella"},
  "empresas": [{"tenantId": "t1", "nombre": "Pollería Doña Rosa", "rol": "dueño"}],
  "esSuperAdmin": false
}
"""

private const val JSON_LOGIN_ERROR = """{"error":"Email o contraseña incorrectos"}"""

// Contrato real (`leadia/src/routes/auth.ts`: `registrar()` llama a
// `sesionDe()`, la MISMA función que arma la sesión del login) — usuario
// nuevo, sin empresas todavía (el alta segmentada crea el negocio en T2).
private const val JSON_REGISTRO_OK = """
{
  "token": "hilo_u_nuevo123",
  "usuario": {"id": "u9", "email": "nueva@leadai-pe.com", "nombre": "Rosa"},
  "empresas": [],
  "esSuperAdmin": false
}
"""

private const val JSON_REGISTRO_ERROR = """{"error":"Ese email ya está registrado"}"""

class AuthApiTest {

    @Test
    fun login_feliz_guarda_sesion_y_devuelve_ok() = runTest {
        val sesionRepo = SesionRepositorio(dataStoreDePrueba())
        val engine = MockEngine { request ->
            assertEquals("/auth/login", request.url.encodedPath)
            respond(
                content = JSON_LOGIN_OK,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val api = ApiCliente(sesion = sesionRepo, engine = engine)
        val authApi = AuthApi(api, sesionRepo)

        val resultado = authApi.login("guisella@leadai-pe.com", "clave123")

        assertIs<Resultado.Ok<SesionGuardada>>(resultado)
        assertEquals("hilo_u_9f8a7b6c", resultado.valor.token)
        assertEquals("Guisella", resultado.valor.usuarioNombre)
        assertEquals("t1", resultado.valor.tenantIdActivo)

        // la sesión quedó persistida sin que el caller llame guardar() aparte
        val guardada = sesionRepo.observar().first()
        assertEquals("hilo_u_9f8a7b6c", guardada?.token)
    }

    @Test
    fun login_401_devuelve_el_mensaje_del_backend_y_no_guarda_sesion() = runTest {
        val sesionRepo = SesionRepositorio(dataStoreDePrueba())
        val engine = MockEngine { request ->
            respond(
                content = JSON_LOGIN_ERROR,
                status = HttpStatusCode.Unauthorized,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val api = ApiCliente(sesion = sesionRepo, engine = engine)
        val authApi = AuthApi(api, sesionRepo)

        val resultado = authApi.login("guisella@leadai-pe.com", "clave-incorrecta")

        assertIs<Resultado.Error>(resultado)
        assertEquals("Email o contraseña incorrectos", resultado.mensaje)
        assertNull(sesionRepo.observar().first())
    }

    @Test
    fun login_sin_conexion_devuelve_mensaje_de_red() = runTest {
        val sesionRepo = SesionRepositorio(dataStoreDePrueba())
        val engine = MockEngine { _ ->
            throw io.ktor.client.plugins.HttpRequestTimeoutException(
                io.ktor.client.request.HttpRequestBuilder(),
            )
        }
        val api = ApiCliente(sesion = sesionRepo, engine = engine)
        val authApi = AuthApi(api, sesionRepo)

        val resultado = authApi.login("guisella@leadai-pe.com", "clave123")

        assertIs<Resultado.Error>(resultado)
        assertTrue(resultado.mensaje.contains("Sin conexión"))
    }

    @Test
    fun registrar_feliz_guarda_sesion_sin_empresas_y_devuelve_ok() = runTest {
        val sesionRepo = SesionRepositorio(dataStoreDePrueba())
        var pathVisto: String? = null
        val engine = MockEngine { request ->
            pathVisto = request.url.encodedPath
            respond(
                content = JSON_REGISTRO_OK,
                status = HttpStatusCode.Created,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val api = ApiCliente(sesion = sesionRepo, engine = engine)
        val authApi = AuthApi(api, sesionRepo)

        val resultado = authApi.registrar("nueva@leadai-pe.com", "clave1234", "Rosa")

        assertEquals("/auth/registro", pathVisto)
        assertIs<Resultado.Ok<SesionGuardada>>(resultado)
        assertEquals("hilo_u_nuevo123", resultado.valor.token)
        assertEquals("Rosa", resultado.valor.usuarioNombre)
        assertEquals(emptyList(), resultado.valor.empresas)
        // sin empresas: no hay tenant que auto-elegir (a diferencia del login
        // con una sola empresa, ver AuthApi.aSesionGuardada)
        assertNull(resultado.valor.tenantIdActivo)

        val guardada = sesionRepo.observar().first()
        assertEquals("hilo_u_nuevo123", guardada?.token)
    }

    @Test
    fun registrar_email_duplicado_devuelve_el_mensaje_del_backend_y_no_guarda_sesion() = runTest {
        val sesionRepo = SesionRepositorio(dataStoreDePrueba())
        val engine = MockEngine {
            respond(
                content = JSON_REGISTRO_ERROR,
                status = HttpStatusCode.Conflict,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val api = ApiCliente(sesion = sesionRepo, engine = engine)
        val authApi = AuthApi(api, sesionRepo)

        val resultado = authApi.registrar("repetida@leadai-pe.com", "clave1234", "Rosa")

        assertIs<Resultado.Error>(resultado)
        assertEquals("Ese email ya está registrado", resultado.mensaje)
        assertNull(sesionRepo.observar().first())
    }
}
