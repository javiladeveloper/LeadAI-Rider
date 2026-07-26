package pe.leadai.rider.push

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path.Companion.toPath
import pe.leadai.rider.datos.ApiCliente
import pe.leadai.rider.datos.Resultado
import pe.leadai.rider.datos.SesionRepositorio
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

private fun dataStoreDePrueba(): DataStore<Preferences> {
    val nombre = "push_test_${Random.nextInt()}.preferences_pb"
    val ruta = (FileSystem.SYSTEM_TEMPORARY_DIRECTORY.toString() + "/" + nombre).toPath()
    return PreferenceDataStoreFactory.createWithPath(produceFile = { ruta })
}

/** Lee el body de la request como texto plano — `OutgoingContent.toByteArray()` es la extensión pública de ktor-client-mock para esto. */
private suspend fun HttpRequestData.textoBody(): String = body.toByteArray().decodeToString()

class RegistroPushRepositorioTest {

    @Test
    fun registrar_con_token_hace_post_con_body_correcto() = runTest {
        val sesionRepo = SesionRepositorio(dataStoreDePrueba())
        var metodoVisto: HttpMethod? = null
        var pathVisto: String? = null
        var bodyVisto: String? = null
        val engine = MockEngine { request ->
            metodoVisto = request.method
            pathVisto = request.url.encodedPath
            bodyVisto = request.textoBody()
            respond(
                content = """{"ok":true}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val api = ApiCliente(sesion = sesionRepo, engine = engine)
        val repo = RegistroPushRepositorio(api, obtenerToken = { "token-fake-123" })

        val resultado = repo.registrar()

        assertIs<Resultado.Ok<Unit>>(resultado)
        assertEquals(HttpMethod.Post, metodoVisto)
        assertEquals("/dispositivos-push", pathVisto)
        assertTrue(bodyVisto!!.contains("\"token\":\"token-fake-123\""))
        assertTrue(bodyVisto!!.contains("\"plataforma\":\"android\""))
    }

    @Test
    fun registrar_sin_token_no_llama_al_backend() = runTest {
        val sesionRepo = SesionRepositorio(dataStoreDePrueba())
        var llamadas = 0
        val engine = MockEngine { _ ->
            llamadas++
            respond(
                content = """{"ok":true}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val api = ApiCliente(sesion = sesionRepo, engine = engine)
        val repo = RegistroPushRepositorio(api, obtenerToken = { null })

        val resultado = repo.registrar()

        assertIs<Resultado.Ok<Unit>>(resultado)
        assertEquals(0, llamadas)
    }

    @Test
    fun registrar_con_error_del_backend_devuelve_resultado_error_sin_lanzar() = runTest {
        val sesionRepo = SesionRepositorio(dataStoreDePrueba())
        val engine = MockEngine { _ ->
            respond(
                content = """{"error":"No se pudo registrar el dispositivo"}""",
                status = HttpStatusCode.InternalServerError,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val api = ApiCliente(sesion = sesionRepo, engine = engine)
        val repo = RegistroPushRepositorio(api, obtenerToken = { "token-fake-123" })

        val resultado = repo.registrar()

        assertIs<Resultado.Error>(resultado)
        assertEquals("No se pudo registrar el dispositivo", resultado.mensaje)
    }

    @Test
    fun desregistrar_con_token_hace_delete_con_body_correcto() = runTest {
        val sesionRepo = SesionRepositorio(dataStoreDePrueba())
        var metodoVisto: HttpMethod? = null
        var pathVisto: String? = null
        var bodyVisto: String? = null
        val engine = MockEngine { request ->
            metodoVisto = request.method
            pathVisto = request.url.encodedPath
            bodyVisto = request.textoBody()
            respond(
                content = """{"ok":true}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val api = ApiCliente(sesion = sesionRepo, engine = engine)
        val repo = RegistroPushRepositorio(api, obtenerToken = { "token-fake-123" })

        val resultado = repo.desregistrar()

        assertIs<Resultado.Ok<Unit>>(resultado)
        assertEquals(HttpMethod.Delete, metodoVisto)
        assertEquals("/dispositivos-push", pathVisto)
        assertTrue(bodyVisto!!.contains("\"token\":\"token-fake-123\""))
    }

    @Test
    fun desregistrar_sin_token_no_llama_al_backend() = runTest {
        val sesionRepo = SesionRepositorio(dataStoreDePrueba())
        var llamadas = 0
        val engine = MockEngine { _ ->
            llamadas++
            respond(
                content = """{"ok":true}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val api = ApiCliente(sesion = sesionRepo, engine = engine)
        val repo = RegistroPushRepositorio(api, obtenerToken = { null })

        val resultado = repo.desregistrar()

        assertIs<Resultado.Ok<Unit>>(resultado)
        assertEquals(0, llamadas)
    }
}
