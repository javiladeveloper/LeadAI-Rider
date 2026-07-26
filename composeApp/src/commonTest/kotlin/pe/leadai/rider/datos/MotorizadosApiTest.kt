package pe.leadai.rider.datos

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path.Companion.toPath
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

private fun dataStoreDePrueba(): DataStore<Preferences> {
    val nombre = "motorizados_api_test_${Random.nextInt()}.preferences_pb"
    val ruta = (FileSystem.SYSTEM_TEMPORARY_DIRECTORY.toString() + "/" + nombre).toPath()
    return PreferenceDataStoreFactory.createWithPath(produceFile = { ruta })
}

private const val JSON_PERFIL =
    """{"id":"m1","usuarioId":"u1","distrito":"Los Olivos","telefono":"987654321","placa":"ABC-123","estado":"pendiente","creadoEn":"2026-07-21T10:00:00.000Z"}"""

class MotorizadosApiTest {

    @Test
    fun distritos_pide_get_y_parsea_la_lista() = runTest {
        val sesionRepo = SesionRepositorio(dataStoreDePrueba())
        var pathVisto: String? = null
        val engine = MockEngine { request ->
            pathVisto = request.url.encodedPath
            respond(
                content = """{"distritos":["Los Olivos","Comas","San Martín de Porres"]}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val api = MotorizadosApi(ApiCliente(sesion = sesionRepo, engine = engine))

        val resultado = api.distritos()

        assertEquals("/motorizados/distritos", pathVisto)
        assertIs<Resultado.Ok<List<String>>>(resultado)
        assertEquals(listOf("Los Olivos", "Comas", "San Martín de Porres"), resultado.valor)
    }

    @Test
    fun miPerfil_con_perfil_null_devuelve_ok_null_sin_ser_error() = runTest {
        val sesionRepo = SesionRepositorio(dataStoreDePrueba())
        val engine = MockEngine {
            respond(
                content = """{"perfil":null}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val api = MotorizadosApi(ApiCliente(sesion = sesionRepo, engine = engine))

        val resultado = api.miPerfil()

        assertIs<Resultado.Ok<PerfilMotorizadoDto?>>(resultado)
        assertNull(resultado.valor)
    }

    @Test
    fun miPerfil_con_perfil_existente_lo_parsea_completo() = runTest {
        val sesionRepo = SesionRepositorio(dataStoreDePrueba())
        var pathVisto: String? = null
        val engine = MockEngine { request ->
            pathVisto = request.url.encodedPath
            respond(
                content = """{"perfil":$JSON_PERFIL}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val api = MotorizadosApi(ApiCliente(sesion = sesionRepo, engine = engine))

        val resultado = api.miPerfil()

        assertEquals("/motorizados/mi-perfil", pathVisto)
        assertIs<Resultado.Ok<PerfilMotorizadoDto?>>(resultado)
        assertEquals("Los Olivos", resultado.valor?.distrito)
        assertEquals("pendiente", resultado.valor?.estado)
    }

    @Test
    fun guardarMiPerfil_manda_post_con_el_body_exacto() = runTest {
        val sesionRepo = SesionRepositorio(dataStoreDePrueba())
        var metodoVisto: HttpMethod? = null
        var pathVisto: String? = null
        var bodyVisto: String? = null
        val engine = MockEngine { request ->
            metodoVisto = request.method
            pathVisto = request.url.encodedPath
            bodyVisto = request.body.toByteArray().decodeToString()
            respond(
                content = """{"perfil":$JSON_PERFIL}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val api = MotorizadosApi(ApiCliente(sesion = sesionRepo, engine = engine))

        val resultado = api.guardarMiPerfil(distrito = "Los Olivos", telefono = "987654321", placa = "ABC-123")

        assertEquals(HttpMethod.Post, metodoVisto)
        assertEquals("/motorizados/mi-perfil", pathVisto)
        val cuerpo = bodyVisto.orEmpty()
        assertEquals(true, cuerpo.contains("\"distrito\":\"Los Olivos\""))
        assertEquals(true, cuerpo.contains("\"telefono\":\"987654321\""))
        assertEquals(true, cuerpo.contains("\"placa\":\"ABC-123\""))
        assertIs<Resultado.Ok<PerfilMotorizadoDto>>(resultado)
        assertEquals("Los Olivos", resultado.valor.distrito)
    }

    @Test
    fun guardarMiPerfil_sin_placa_omite_el_campo_en_vez_de_mandar_null() = runTest {
        // El `crearSchema` del backend (`motorizados.ts`) marca `placa` como
        // `.optional()`: Zod acepta el campo AUSENTE pero rechaza un `null`
        // explícito — mandar `"placa":null` rompía el alta sin placa.
        val sesionRepo = SesionRepositorio(dataStoreDePrueba())
        var bodyVisto: String? = null
        val engine = MockEngine { request ->
            bodyVisto = request.body.toByteArray().decodeToString()
            respond(
                content = """{"perfil":$JSON_PERFIL}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val api = MotorizadosApi(ApiCliente(sesion = sesionRepo, engine = engine))

        val resultado = api.guardarMiPerfil(distrito = "Los Olivos", telefono = "987654321", placa = null)

        val cuerpo = bodyVisto.orEmpty()
        assertEquals(false, cuerpo.contains("placa"))
        assertEquals(false, cuerpo.contains("null"))
        assertEquals(true, cuerpo.contains("\"telefono\":\"987654321\""))
        assertIs<Resultado.Ok<PerfilMotorizadoDto>>(resultado)
    }

    @Test
    fun guardarMiPerfil_error_del_backend_devuelve_resultado_error() = runTest {
        val sesionRepo = SesionRepositorio(dataStoreDePrueba())
        val engine = MockEngine {
            respond(
                content = """{"error":"distrito es requerido"}""",
                status = HttpStatusCode.BadRequest,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val api = MotorizadosApi(ApiCliente(sesion = sesionRepo, engine = engine))

        val resultado = api.guardarMiPerfil(distrito = "", telefono = null, placa = null)

        assertIs<Resultado.Error>(resultado)
        assertEquals("distrito es requerido", resultado.mensaje)
    }
}
