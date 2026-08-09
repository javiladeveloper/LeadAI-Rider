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
import pe.leadai.rider.datos.PerfilApi
import pe.leadai.rider.datos.SesionRepositorio
import pe.leadai.rider.esperarCondicion
import pe.leadai.rider.ui.comunes.AvisosGlobales
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import pe.leadai.rider.ui.carreras.UbicacionRider
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
            perfilApi = PerfilApi(apiCliente),
            dispatcher = testDispatcher,
            obtenerUbicacion = { null },
            obtenerTokenPush = { tokenPush },
        )
    }

    /** Como el anterior, pero con GPS: devuelve `ubicacion` y anota cómo se pidió. */
    private fun vmConGps(
        engine: MockEngine,
        ubicacion: UbicacionRider? = UbicacionRider(-17.99, -70.23),
        comoSePidio: MutableList<Boolean> = mutableListOf(),
    ): ClienteViewModel {
        val apiCliente = ApiCliente(sesion = SesionRepositorio(dataStoreDePrueba()), engine = engine)
        return ClienteViewModel(
            api = CarrerasClienteApi(apiCliente),
            avisos = AvisosGlobales(),
            motorizadosApi = MotorizadosApi(apiCliente),
            perfilApi = PerfilApi(apiCliente),
            dispatcher = testDispatcher,
            obtenerUbicacion = { loPidio ->
                comoSePidio.add(loPidio)
                ubicacion
            },
            obtenerTokenPush = { null },
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
        // `cargar()` dispara varias peticiones en paralelo (carrera, historial,
        // perfil) y no todas terminan cuando `cargando` baja. Se espera a que
        // el contador se quede QUIETO: si no, una que aterriza tarde se cuenta
        // como si la hubiera hecho `pedir()`.
        var previo = -1
        while (previo != llamadas) {
            previo = llamadas
            advanceUntilIdle()
            delay(20)
            advanceUntilIdle()
        }
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
    @Test
    fun delivery_viaja_al_backend_como_encomienda() = runTest {
        // "Delivery" solo existe en la pantalla: el backend acepta 'encomienda'
        // y 'pasajero'. Si viajara "delivery" el POST fallaria con 400.
        var cuerpoDelPedido = ""
        val engine = MockEngine { peticion ->
            if (peticion.url.encodedPath.endsWith("/carreras")) {
                cuerpoDelPedido = (peticion.body as io.ktor.http.content.TextContent).text
                respond(
                    content = """{"ok":true,"id":"c1","montoSugerido":600,"montoOfrecido":600,"expiraEnMinutos":15}""",
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

        vm.elegirTipo(TIPO_DELIVERY)
        vm.cambiarOrigen("El Pollon")
        vm.cambiarDestino("La Bombonera")
        vm.cambiarMonto("6")
        vm.pedir()
        advanceUntilIdle()
        flow { while (true) { emit(cuerpoDelPedido); delay(10) } }
            .esperarCondicion { it.isNotBlank() }

        assertTrue(cuerpoDelPedido.contains("\"tipo\":\"encomienda\""), cuerpoDelPedido)
        assertTrue(!cuerpoDelPedido.contains("delivery"), cuerpoDelPedido)
    }

    @Test
    fun en_delivery_el_monto_de_compra_se_conserva() = runTest {
        // El pollo lo paga el rider de su bolsillo, igual que en una
        // encomienda: cambiar a delivery NO debe limpiar ese monto.
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

        vm.elegirTipo(TIPO_ENCOMIENDA)
        vm.cambiarMontoCompra("60")
        vm.elegirTipo(TIPO_DELIVERY)
        advanceUntilIdle()
        assertEquals("60", vm.estado.value.montoCompra)

        // Un pasajero NO manda a comprar nada: ahi si se limpia.
        vm.elegirTipo(TIPO_PASAJERO)
        advanceUntilIdle()
        assertEquals("", vm.estado.value.montoCompra)
    }

    @Test
    fun el_historial_se_carga_al_entrar() = runTest {
        val engine = MockEngine { peticion ->
            if (peticion.url.encodedPath.endsWith("/carreras/historial")) {
                respond(
                    content = """{"carreras":[{"id":"c1","tipo":"encomienda","estado":"entregada","origenTexto":"El Pollon","destinoTexto":"La Bombonera","montoOfrecido":800,"creadoEn":"2026-08-06T10:00:00.000Z","entregadoEn":"2026-08-06T10:30:00.000Z"}]}""",
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
        vm.estado.esperarCondicion { it.historial.isNotEmpty() }

        val viaje = vm.estado.value.historial.first()
        assertEquals("entregada", viaje.estado)
        assertEquals(800, viaje.montoOfrecido)
        assertEquals("2026-08-06T10:30:00.000Z", viaje.entregadoEn)
    }

    // ── Mi ubicación como origen ────────────────────────────────────────

    @Test
    fun en_pasajero_el_origen_se_llena_solo_con_el_gps() = runTest {
        // El cliente abre la app para pedir un viaje DESDE donde está: que
        // tenga que escribir su propia dirección es trabajo que la app puede
        // ahorrarle. Se veía "¿Desde dónde?" vacío.
        val engine = MockEngine {
            respond(
                content = """{"carrera":null}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val vm = vmConGps(engine)
        vm.cargar()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(-17.99, vm.estado.value.origenLat)
        assertEquals("Mi ubicación actual", vm.estado.value.origen)
    }

    @Test
    fun el_boton_de_ubicacion_insiste_aunque_el_arranque_haya_fallado() = runTest {
        // El bug: `permisoYaPedido` es global al proceso y no se reseteaba, así
        // que un intento fallido al abrir dejaba el botón verde MUERTO para
        // siempre —tocarlo no hacía nada y no había ninguna señal de por qué—.
        //
        // El arranque pide en automático (false) y el botón como toque
        // explícito (true), que es lo que permite volver a preguntar.
        val engine = MockEngine {
            respond(
                content = """{"carrera":null}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val comoSePidio = mutableListOf<Boolean>()
        val vm = vmConGps(engine, comoSePidio = comoSePidio)
        vm.cargar()
        testDispatcher.scheduler.advanceUntilIdle()
        comoSePidio.clear()

        vm.usarMiUbicacion()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(
            listOf(true),
            comoSePidio,
            "el toque del botón debe pedir como explícito, para volver a preguntar",
        )
    }

    @Test
    fun en_delivery_el_gps_no_se_usa_como_origen() = runTest {
        // En un delivery el origen es el LOCAL donde está el pedido, no donde
        // está parado el cliente: poner su ubicación manda al rider al lugar
        // equivocado.
        val engine = MockEngine {
            respond(
                content = """{"carrera":null}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val comoSePidio = mutableListOf<Boolean>()
        val vm = vmConGps(engine, comoSePidio = comoSePidio)
        vm.cargar()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.elegirTipo(TIPO_DELIVERY)
        comoSePidio.clear()
        vm.usarMiUbicacion()
        testDispatcher.scheduler.advanceUntilIdle()

        // Ni siquiera se consulta el GPS: no hay nada que hacer con él acá.
        assertEquals(emptyList(), comoSePidio, "en delivery no se pide la ubicación")
    }
}