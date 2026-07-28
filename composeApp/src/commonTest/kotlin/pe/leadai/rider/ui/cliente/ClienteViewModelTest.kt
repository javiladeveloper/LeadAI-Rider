package pe.leadai.rider.ui.cliente

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okio.FileSystem
import okio.Path.Companion.toPath
import pe.leadai.rider.datos.ApiCliente
import pe.leadai.rider.datos.CarrerasClienteApi
import pe.leadai.rider.datos.MotorizadosApi
import pe.leadai.rider.datos.SesionRepositorio
import pe.leadai.rider.esperarCondicion
import pe.leadai.rider.ui.comunes.AvisosGlobales
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun dataStoreDePrueba(): DataStore<Preferences> {
    val nombre = "cliente_vm_test_${Random.nextInt()}.preferences_pb"
    val ruta = (FileSystem.SYSTEM_TEMPORARY_DIRECTORY.toString() + "/" + nombre).toPath()
    return PreferenceDataStoreFactory.createWithPath(produceFile = { ruta })
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ClienteViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun antes() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun despues() {
        Dispatchers.resetMain()
    }

    /**
     * El VM de siempre: MockEngine inyectado, dispatcher de test y SIN GPS.
     * `tokenPush` simula el token FCM sin tocar el `expect/actual` real —
     * mismo patrón que `RegistroPushRepositorio.obtenerToken`.
     */
    private fun vmDePrueba(engine: MockEngine, tokenPush: String? = null): ClienteViewModel {
        val apiCliente = ApiCliente(sesion = SesionRepositorio(dataStoreDePrueba()), engine = engine)
        return ClienteViewModel(
            api = CarrerasClienteApi(apiCliente),
            avisos = AvisosGlobales(),
            motorizadosApi = MotorizadosApi(apiCliente),
            dispatcher = testDispatcher,
            obtenerUbicacion = { null },
            obtenerTokenPush = { tokenPush },
        )
    }

    @Test
    fun sin_carrera_activa_muestra_el_formulario() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"carrera":null}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val vm = vmDePrueba(engine)

        vm.cargar()
        vm.estado.esperarCondicion { !it.cargando }
        advanceUntilIdle()

        assertNull(vm.estado.value.miCarrera)
        assertNull(vm.estado.value.error)
    }

    @Test
    fun con_carrera_activa_la_expone() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"carrera":{"id":"c1","tipo":"pasajero","estado":"aceptada",
                    "origenTexto":"A","destinoTexto":"B","montoOfrecido":760,
                    "riderNombre":"Ana","riderPlaca":"ABC-123"}}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val vm = vmDePrueba(engine)

        vm.cargar()
        vm.estado.esperarCondicion { it.miCarrera != null }
        advanceUntilIdle()

        assertEquals("aceptada", vm.estado.value.miCarrera?.estado)
        assertEquals("Ana", vm.estado.value.miCarrera?.riderNombre)
    }

    @Test
    fun pedir_sin_destino_avisa_y_no_llama_al_backend() = runTest {
        var llamadas = 0
        val engine = MockEngine {
            llamadas++
            respond(
                content = """{"carrera":null}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val vm = vmDePrueba(engine)
        vm.cargar()
        vm.estado.esperarCondicion { !it.cargando }
        advanceUntilIdle()
        val antes = llamadas

        vm.cambiarOrigen("Av. Grau 240")
        vm.pedir() // sin destino

        advanceUntilIdle()
        assertEquals(antes, llamadas)
        assertEquals("Falta el destino", vm.estado.value.error)
    }

    @Test
    fun el_409_avisa_que_ya_tiene_una_carrera() = runTest {
        val engine = MockEngine { peticion ->
            if (peticion.url.encodedPath.endsWith("/carreras")) {
                respond(
                    content = """{"error":"Ya tienes una carrera en curso"}""",
                    status = HttpStatusCode.Conflict,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            } else {
                respond(
                    content = """{"carrera":null}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }
        }
        val vm = vmDePrueba(engine)
        vm.cargar()
        vm.estado.esperarCondicion { !it.cargando }
        advanceUntilIdle()

        vm.cambiarOrigen("A")
        vm.cambiarDestino("B")
        vm.pedir()
        advanceUntilIdle()
        vm.estado.esperarCondicion { it.error != null }

        assertTrue(vm.estado.value.error?.contains("carrera en curso") == true)
    }

    @Test
    fun el_monto_de_compra_solo_aplica_a_encomienda() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"carrera":null}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val vm = vmDePrueba(engine)

        vm.elegirTipo("encomienda")
        vm.cambiarMontoCompra("60")
        assertEquals("60", vm.estado.value.montoCompra)

        // Al pasar a pasajero, el monto de compra se limpia: un pasajero no
        // manda al rider a comprar nada.
        vm.elegirTipo("pasajero")
        assertEquals("", vm.estado.value.montoCompra)
    }

    @Test
    fun el_flete_y_la_compra_viajan_separados_nunca_sumados() = runTest {
        var cuerpoDelPedido = ""
        val engine = MockEngine { peticion ->
            if (peticion.url.encodedPath.endsWith("/carreras")) {
                cuerpoDelPedido = (peticion.body as io.ktor.http.content.TextContent).text
                respond(
                    content = """{"ok":true,"id":"c1","montoSugerido":760,"montoOfrecido":800,"expiraEnMinutos":15}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            } else {
                respond(
                    content = """{"carrera":null}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }
        }
        val vm = vmDePrueba(engine)
        vm.cargar()
        vm.estado.esperarCondicion { !it.cargando }
        advanceUntilIdle()

        vm.elegirTipo("encomienda")
        vm.cambiarOrigen("Chifa Salon Canton")
        vm.cambiarDestino("Jose Olaya 110")
        vm.cambiarMonto("8")
        vm.cambiarMontoCompra("60")
        vm.pedir()
        advanceUntilIdle()
        // El POST viaja con I/O real (MockEngine + DataStore): se espera con el
        // mismo perro guardián de 5s que el resto de los tests del repo.
        flow { while (true) { emit(cuerpoDelPedido); delay(10) } }
            .esperarCondicion { it.isNotBlank() }

        // El flete es S/8 (800 centavos) y la compra S/60 (6000 centavos). El
        // total NUNCA existe: si alguien los sumara, viajaría 6800.
        assertTrue(cuerpoDelPedido.contains("\"montoOfrecidoCentavos\":800"), cuerpoDelPedido)
        assertTrue(cuerpoDelPedido.contains("\"montoCompraEstimadoCentavos\":6000"), cuerpoDelPedido)
        assertTrue(!cuerpoDelPedido.contains("6800"), cuerpoDelPedido)
    }

    @Test
    fun pedir_como_pasajero_no_manda_monto_de_compra() = runTest {
        var cuerpoDelPedido = ""
        val engine = MockEngine { peticion ->
            if (peticion.url.encodedPath.endsWith("/carreras")) {
                cuerpoDelPedido = (peticion.body as io.ktor.http.content.TextContent).text
                respond(
                    content = """{"ok":true,"id":"c1","montoSugerido":760,"montoOfrecido":800,"expiraEnMinutos":15}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            } else {
                respond(
                    content = """{"carrera":null}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }
        }
        val vm = vmDePrueba(engine)
        vm.cargar()
        vm.estado.esperarCondicion { !it.cargando }
        advanceUntilIdle()

        vm.cambiarOrigen("A")
        vm.cambiarDestino("B")
        vm.cambiarMonto("8")
        vm.pedir()
        advanceUntilIdle()
        flow { while (true) { emit(cuerpoDelPedido); delay(10) } }
            .esperarCondicion { it.isNotBlank() }

        assertTrue(cuerpoDelPedido.contains("\"montoOfrecidoCentavos\":800"), cuerpoDelPedido)
        assertTrue(!cuerpoDelPedido.contains("montoCompraEstimadoCentavos"), cuerpoDelPedido)
    }

    @Test
    fun al_cargar_registra_el_token_de_push() = runTest {
        val rutasLlamadas = mutableListOf<String>()
        val engine = MockEngine { peticion ->
            rutasLlamadas.add(peticion.url.encodedPath)
            respond(
                content = """{"carrera":null}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val vm = vmDePrueba(engine, tokenPush = "token-fcm-123")

        vm.cargar()
        advanceUntilIdle()
        // El POST viaja con I/O real (MockEngine + DataStore): se espera con el
        // mismo perro guardián de 5s que el resto de los tests del repo.
        flow { while (true) { emit(rutasLlamadas.toList()); delay(10) } }
            .esperarCondicion { rutas -> rutas.any { it.contains("dispositivo") } }

        assertTrue(rutasLlamadas.any { it.contains("dispositivo") }, rutasLlamadas.toString())
    }

    @Test
    fun sin_token_de_push_no_llama_al_backend() = runTest {
        val rutasLlamadas = mutableListOf<String>()
        val engine = MockEngine { peticion ->
            rutasLlamadas.add(peticion.url.encodedPath)
            respond(
                content = """{"carrera":null}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val vm = vmDePrueba(engine, tokenPush = null)

        vm.cargar()
        vm.estado.esperarCondicion { !it.cargando }
        advanceUntilIdle()

        assertTrue(rutasLlamadas.none { it.contains("dispositivo") }, rutasLlamadas.toString())
    }

    @Test
    fun cancelar_limpia_la_carrera_activa() = runTest {
        val engine = MockEngine { peticion ->
            if (peticion.url.encodedPath.endsWith("/cancelar")) {
                respond(
                    content = """{"ok":true}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            } else {
                respond(
                    content = """{"carrera":{"id":"c1","tipo":"pasajero","estado":"disponible",
                        "origenTexto":"A","destinoTexto":"B","montoOfrecido":760}}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }
        }
        val vm = vmDePrueba(engine)
        vm.cargar()
        vm.estado.esperarCondicion { it.miCarrera != null }
        advanceUntilIdle()

        vm.cancelar()
        advanceUntilIdle()
        vm.estado.esperarCondicion { it.miCarrera == null }

        assertNull(vm.estado.value.miCarrera)
    }
}
