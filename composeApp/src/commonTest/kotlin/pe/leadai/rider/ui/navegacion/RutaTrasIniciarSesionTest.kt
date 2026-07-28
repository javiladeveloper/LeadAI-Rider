package pe.leadai.rider.ui.navegacion

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path.Companion.toPath
import pe.leadai.rider.datos.ApiCliente
import pe.leadai.rider.datos.ModoRepositorio
import pe.leadai.rider.datos.MotorizadosApi
import pe.leadai.rider.datos.SesionRepositorio
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

private fun dataStoreDePrueba(): DataStore<Preferences> {
    val nombre = "ruta_inicio_test_${Random.nextInt()}.preferences_pb"
    val ruta = (FileSystem.SYSTEM_TEMPORARY_DIRECTORY.toString() + "/" + nombre).toPath()
    return PreferenceDataStoreFactory.createWithPath(produceFile = { ruta })
}

private const val JSON_PERFIL =
    """{"id":"m1","usuarioId":"u1","distrito":"Los Olivos","telefono":"987654321","placa":"ABC-123","estado":"verificado","creadoEn":"2026-07-21T10:00:00.000Z"}"""

private fun apiConPerfil(json: String) = MotorizadosApi(
    ApiCliente(
        sesion = SesionRepositorio(dataStoreDePrueba()),
        engine = MockEngine {
            respond(
                content = """{"perfil":$json}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        },
    ),
)

private fun apiSinRed() = MotorizadosApi(
    ApiCliente(
        sesion = SesionRepositorio(dataStoreDePrueba()),
        engine = MockEngine { respondError(HttpStatusCode.ServiceUnavailable) },
    ),
)

/**
 * La regla que no se puede violar: un rider con perfil entra directo a
 * conducir. Si esto se rompe, los motorizados de hoy quedan atrapados en una
 * pantalla de elección que no les corresponde.
 */
class RutaTrasIniciarSesionTest {

    @Test
    fun con_perfil_de_motorizado_va_directo_a_carreras() = runTest {
        val ruta = rutaTrasIniciarSesion(apiConPerfil(JSON_PERFIL), modoGuardado = null)

        assertEquals(Rutas.CARRERAS, ruta)
    }

    @Test
    fun con_perfil_el_modo_guardado_cliente_no_lo_desvia() = runTest {
        // El perfil MANDA sobre el modo guardado: un rider que alguna vez
        // probó el modo cliente sigue entrando a trabajar.
        val ruta = rutaTrasIniciarSesion(
            apiConPerfil(JSON_PERFIL),
            modoGuardado = ModoRepositorio.CLIENTE,
        )

        assertEquals(Rutas.CARRERAS, ruta)
    }

    @Test
    fun sin_perfil_y_sin_modo_elegido_pregunta() = runTest {
        val ruta = rutaTrasIniciarSesion(apiConPerfil("null"), modoGuardado = null)

        assertEquals(Rutas.ELEGIR_MODO, ruta)
    }

    @Test
    fun sin_perfil_con_modo_cliente_va_al_cliente() = runTest {
        val ruta = rutaTrasIniciarSesion(
            apiConPerfil("null"),
            modoGuardado = ModoRepositorio.CLIENTE,
        )

        assertEquals(Rutas.CLIENTE, ruta)
    }

    @Test
    fun sin_perfil_con_modo_conductor_va_al_alta() = runTest {
        val ruta = rutaTrasIniciarSesion(
            apiConPerfil("null"),
            modoGuardado = ModoRepositorio.CONDUCTOR,
        )

        assertEquals(Rutas.ALTA, ruta)
    }

    @Test
    fun sin_conexion_y_sin_modo_guardado_pregunta_en_vez_de_mandar_al_alta() = runTest {
        // Sin red no se sabe si es rider o cliente. Mandarlo al alta a ciegas
        // dejaría al cliente trabado pidiéndole DNI y placa.
        val ruta = rutaTrasIniciarSesion(apiSinRed(), modoGuardado = null)

        assertEquals(Rutas.ELEGIR_MODO, ruta)
    }

    @Test
    fun sin_conexion_respeta_el_modo_guardado() = runTest {
        assertEquals(
            Rutas.ALTA,
            rutaTrasIniciarSesion(apiSinRed(), modoGuardado = ModoRepositorio.CONDUCTOR),
        )
        assertEquals(
            Rutas.CLIENTE,
            rutaTrasIniciarSesion(apiSinRed(), modoGuardado = ModoRepositorio.CLIENTE),
        )
    }
}
