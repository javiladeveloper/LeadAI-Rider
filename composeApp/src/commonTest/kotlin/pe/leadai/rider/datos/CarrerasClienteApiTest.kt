package pe.leadai.rider.datos

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path.Companion.toPath
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun dataStoreDePrueba(): DataStore<Preferences> {
    val nombre = "cliente_api_test_${Random.nextInt()}.preferences_pb"
    val ruta = (FileSystem.SYSTEM_TEMPORARY_DIRECTORY.toString() + "/" + nombre).toPath()
    return PreferenceDataStoreFactory.createWithPath(produceFile = { ruta })
}

private fun apiCon(engine: MockEngine) =
    CarrerasClienteApi(ApiCliente(sesion = SesionRepositorio(dataStoreDePrueba()), engine = engine))

class CarrerasClienteApiTest {

    @Test
    fun sugerir_devuelve_monto_y_km() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"kmEstimado":3.0,"montoSugerido":760,
                    "origen":{"texto":"Av. Grau 240","lat":-18.0,"lng":-70.24},
                    "destino":{"texto":"Jose Olaya 110","lat":-18.01,"lng":-70.25}}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        val r = apiCon(engine).sugerir(
            tipo = "pasajero",
            origenTexto = "Av. Grau 240",
            origenLat = -18.0,
            origenLng = -70.24,
            destinoTexto = "Jose Olaya 110",
        )

        val s = (r as Resultado.Ok).valor
        assertEquals(760L, s.montoSugerido)
        assertEquals(3.0, s.kmEstimado)
    }

    @Test
    fun pedir_manda_el_flete_y_la_compra_por_separado() = runTest {
        var cuerpo = ""
        val engine = MockEngine { peticion ->
            cuerpo = (peticion.body as io.ktor.http.content.TextContent).text
            respond(
                content = """{"ok":true,"id":"c1","montoSugerido":760,"montoOfrecido":800,"expiraEnMinutos":15}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        val r = apiCon(engine).pedir(
            tipo = "encomienda",
            origenTexto = "Chifa Salon Canton",
            origenLat = null,
            origenLng = null,
            destinoTexto = "Jose Olaya 110",
            destinoLat = null,
            destinoLng = null,
            montoOfrecidoCentavos = 800,
            montoCompraEstimadoCentavos = 6000,
            notas = "combinado sin verduras",
            contacto = "952123456",
        )

        assertTrue(r is Resultado.Ok)
        // Flete y compra viajan como campos DISTINTOS, nunca sumados.
        assertTrue(cuerpo.contains("\"montoOfrecidoCentavos\":800"))
        assertTrue(cuerpo.contains("\"montoCompraEstimadoCentavos\":6000"))
    }

    @Test
    fun pedir_con_carrera_activa_devuelve_409() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"error":"Ya tienes una carrera en curso","carreraId":"c-vieja"}""",
                status = HttpStatusCode.Conflict,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        val r = apiCon(engine).pedir(
            tipo = "pasajero", origenTexto = "A", origenLat = null, origenLng = null,
            destinoTexto = "B", destinoLat = null, destinoLng = null,
            montoOfrecidoCentavos = null, montoCompraEstimadoCentavos = null,
            notas = null, contacto = null,
        )

        assertEquals(409, (r as Resultado.Error).codigo)
    }

    @Test
    fun mi_carrera_sin_carrera_activa_es_null_no_error() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"carrera":null}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        val r = apiCon(engine).miCarrera()

        assertNull((r as Resultado.Ok).valor)
    }

    @Test
    fun mi_carrera_aceptada_trae_los_datos_del_rider() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"carrera":{"id":"c1","tipo":"pasajero","estado":"aceptada",
                    "origenTexto":"Av. Grau 240","destinoTexto":"Jose Olaya 110",
                    "montoOfrecido":760,"montoCompraEstimado":null,"kmEstimado":3.0,
                    "notas":"","recogido":false,"creadoEn":"2026-07-28T10:00:00.000Z",
                    "expiraEn":null,"riderNombre":"Ana","riderTelefono":"952123456",
                    "riderPlaca":"ABC-123","riderVehiculo":"moto"}}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        val c = (apiCon(engine).miCarrera() as Resultado.Ok).valor!!

        assertEquals("aceptada", c.estado)
        assertEquals("Ana", c.riderNombre)
        assertEquals("ABC-123", c.riderPlaca)
    }

    @Test
    fun cancelar_una_carrera_ya_tomada_devuelve_409() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"error":"Esa carrera ya no se puede cancelar"}""",
                status = HttpStatusCode.Conflict,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        val r = apiCon(engine).cancelar("c1")

        assertEquals(409, (r as Resultado.Error).codigo)
    }
}
