package pe.leadai.rider.ui.carreras

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

private fun dataStoreDePrueba(): DataStore<Preferences> {
    val nombre = "rider_espera_vm_test_${Random.nextInt()}.preferences_pb"
    val ruta = (FileSystem.SYSTEM_TEMPORARY_DIRECTORY.toString() + "/" + nombre).toPath()
    return PreferenceDataStoreFactory.createWithPath(produceFile = { ruta })
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class CarrerasViewModelTest {

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
    fun cargar_ok_con_perfil_expone_distrito_y_estado() = runTest {
        val sesionRepo = SesionRepositorio(dataStoreDePrueba())
        val engine = MockEngine {
            respond(
                content = """{"perfil":{"id":"m1","usuarioId":"u1","distrito":"Los Olivos","telefono":"987654321","placa":null,"estado":"pendiente","creadoEn":"2026-07-21T10:00:00.000Z"}}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val vm = CarrerasViewModel(MotorizadosApi(ApiCliente(sesion = sesionRepo, engine = engine)), AvisosGlobales(), testDispatcher)

        vm.cargar()
        vm.estado.esperarCondicion { !it.cargando }
        advanceUntilIdle()

        assertEquals("Los Olivos", vm.estado.value.perfil?.distrito)
        assertEquals("pendiente", vm.estado.value.perfil?.estado)
        assertNull(vm.estado.value.error)
    }

    @Test
    fun cargar_error_del_backend_expone_el_mensaje() = runTest {
        val sesionRepo = SesionRepositorio(dataStoreDePrueba())
        val engine = MockEngine {
            respond(
                content = """{"error":"No autorizado"}""",
                status = HttpStatusCode.Unauthorized,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val vm = CarrerasViewModel(MotorizadosApi(ApiCliente(sesion = sesionRepo, engine = engine)), AvisosGlobales(), testDispatcher)

        vm.cargar()
        vm.estado.esperarCondicion { !it.cargando }
        advanceUntilIdle()

        assertEquals("No autorizado", vm.estado.value.error)
        assertNull(vm.estado.value.perfil)
    }

    // ── POOL v0 ──────────────────────────────────────────────────────────

    /** Fixtures del contrato real de `GET /motorizados/carreras`. */
    private fun enginePool(
        aceptarStatus: HttpStatusCode = HttpStatusCode.OK,
        conMiCarrera: Boolean = false,
        rutasLlamadas: MutableList<String> = mutableListOf(),
    ): MockEngine = MockEngine { request ->
        val path = request.url.encodedPath
        rutasLlamadas.add(path)
        when {
            path == "/motorizados/disponibilidad" -> respond(
                content = """{"ok":true}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
            path == "/motorizados/posicion" -> respond(
                content = """{"ok":true}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
            path == "/motorizados/mi-perfil" -> respond(
                content = """{"perfil":{"id":"m1","usuarioId":"u1","distrito":"Pocollay, Tacna","telefono":"999888777","placa":"ABC-123","dni":"44247191","estado":"pendiente","creadoEn":"2026-07-21T10:00:00.000Z"}}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
            path == "/motorizados/carreras" -> respond(
                content = if (conMiCarrera) {
                    """{"carreras":[],"miCarrera":{"pedidoId":"p9","negocio":"Polleria Demo","negocioDistrito":"Alto de la Alianza, Tacna","direccion":"Calle Zela 355","totalCentavos":6800,"creadoEn":"2026-07-24T01:00:00.000Z","clienteNombre":"Diego","clienteContacto":"51999777666"}}"""
                } else {
                    """{"carreras":[{"pedidoId":"p1","negocio":"Polleria Demo","negocioDistrito":"Alto de la Alianza, Tacna","direccion":"Calle Zela 355","totalCentavos":6800,"creadoEn":"2026-07-24T01:00:00.000Z"}],"miCarrera":null}"""
                },
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
            path.endsWith("/aceptar") -> respond(
                content = if (aceptarStatus == HttpStatusCode.OK) """{"ok":true}""" else """{"error":"Otro rider tomó esta carrera"}""",
                status = aceptarStatus,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
            path.endsWith("/entregar") -> respond(
                content = """{"ok":true}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
            path == "/motorizados/historial" -> respond(
                content = """{"hoy":{"carreras":2,"km":7.5,"totalCentavos":12000},"carreras":[{"pedidoId":"e1","negocio":"Polleria Demo","direccion":"Calle Zela 355","totalCentavos":6800,"km":3.2,"entregadoEn":"2026-07-24T09:00:00.000Z"}]}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
            path == "/motorizados/dispositivo" -> respond(
                content = """{"ok":true}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
            else -> respond("{}", HttpStatusCode.NotFound, headersOf(HttpHeaders.ContentType, "application/json"))
        }
    }

    @Test
    fun cargar_trae_las_carreras_de_la_zona() = runTest {
        val vm = CarrerasViewModel(
            MotorizadosApi(ApiCliente(sesion = SesionRepositorio(dataStoreDePrueba()), engine = enginePool())),
            AvisosGlobales(),
            testDispatcher,
        )
        vm.cargar()
        advanceUntilIdle()
        vm.estado.esperarCondicion { it.carreras.isNotEmpty() }

        assertEquals("Polleria Demo", vm.estado.value.carreras.single().negocio)
        assertNull(vm.estado.value.miCarrera)
    }

    @Test
    fun aceptar_mueve_la_carrera_a_en_curso() = runTest {
        val vm = CarrerasViewModel(
            MotorizadosApi(ApiCliente(sesion = SesionRepositorio(dataStoreDePrueba()), engine = enginePool())),
            AvisosGlobales(),
            testDispatcher,
        )
        vm.cargar()
        advanceUntilIdle()
        vm.estado.esperarCondicion { it.carreras.isNotEmpty() }

        vm.aceptar(vm.estado.value.carreras.single())
        advanceUntilIdle()
        vm.estado.esperarCondicion { it.accionEnCurso == null }

        assertEquals("p1", vm.estado.value.miCarrera?.pedidoId)
        assertEquals(emptyList<String>(), vm.estado.value.carreras.map { it.pedidoId })

        // Entregar la limpia y vuelve a la lista.
        vm.entregar()
        advanceUntilIdle()
        vm.estado.esperarCondicion { it.accionEnCurso == null }
        assertNull(vm.estado.value.miCarrera)
    }

    @Test
    fun cargar_trae_el_historial_y_registra_el_push_del_rider() = runTest {
        val rutas = mutableListOf<String>()
        val vm = CarrerasViewModel(
            MotorizadosApi(ApiCliente(sesion = SesionRepositorio(dataStoreDePrueba()), engine = enginePool(rutasLlamadas = rutas))),
            AvisosGlobales(),
            testDispatcher,
            obtenerUbicacion = { null },
            obtenerTokenPush = { "token-fcm-rider" },
        )
        vm.cargar()
        advanceUntilIdle()
        vm.estado.esperarCondicion { it.historial != null }

        assertEquals(2, vm.estado.value.historial?.hoy?.carreras)
        assertEquals(7.5, vm.estado.value.historial?.hoy?.km)
        assertEquals("Polleria Demo", vm.estado.value.historial?.carreras?.single()?.negocio)
        // El dispositivo del rider quedó registrado para el push de carreras.
        flow { while (true) { emit(rutas.toList()); delay(10) } }
            .esperarCondicion { "/motorizados/dispositivo" in it }
    }

    @Test
    fun con_carrera_en_curso_el_polling_reporta_el_gps() = runTest {
        val rutas = mutableListOf<String>()
        val vm = CarrerasViewModel(
            MotorizadosApi(ApiCliente(sesion = SesionRepositorio(dataStoreDePrueba()), engine = enginePool(conMiCarrera = true, rutasLlamadas = rutas))),
            AvisosGlobales(),
            testDispatcher,
            obtenerUbicacion = { UbicacionRider(-18.0066, -70.2463) },
        )
        vm.refrescarCarreras()
        advanceUntilIdle()
        vm.estado.esperarCondicion { it.miCarrera != null }
        // La llamada a posicion viaja DESPUÉS de actualizar el estado — se
        // sondea la lista de rutas como flow (mismo perro guardián de 5s).
        flow { while (true) { emit(rutas.toList()); delay(10) } }
            .esperarCondicion { "/motorizados/posicion" in it }

        assertEquals(listOf("/motorizados/carreras", "/motorizados/posicion"), rutas)
    }

    @Test
    fun sin_gps_no_se_reporta_posicion_y_nada_se_rompe() = runTest {
        val rutas = mutableListOf<String>()
        val vm = CarrerasViewModel(
            MotorizadosApi(ApiCliente(sesion = SesionRepositorio(dataStoreDePrueba()), engine = enginePool(conMiCarrera = true, rutasLlamadas = rutas))),
            AvisosGlobales(),
            testDispatcher,
            obtenerUbicacion = { null },
        )
        vm.refrescarCarreras()
        advanceUntilIdle()
        vm.estado.esperarCondicion { it.miCarrera != null }

        assertEquals(listOf("/motorizados/carreras"), rutas)
    }

    @Test
    fun reportar_posicion_ahora_manda_el_gps_directo() = runTest {
        val rutas = mutableListOf<String>()
        val vm = CarrerasViewModel(
            MotorizadosApi(ApiCliente(sesion = SesionRepositorio(dataStoreDePrueba()), engine = enginePool(rutasLlamadas = rutas))),
            AvisosGlobales(),
            testDispatcher,
            obtenerUbicacion = { UbicacionRider(-18.0066, -70.2463) },
        )
        vm.reportarPosicionAhora()

        assertEquals(listOf("/motorizados/posicion"), rutas)
    }

    @Test
    fun sin_carrera_en_curso_no_se_reporta_gps() = runTest {
        val rutas = mutableListOf<String>()
        val vm = CarrerasViewModel(
            MotorizadosApi(ApiCliente(sesion = SesionRepositorio(dataStoreDePrueba()), engine = enginePool(rutasLlamadas = rutas))),
            AvisosGlobales(),
            testDispatcher,
            obtenerUbicacion = { UbicacionRider(-18.0066, -70.2463) },
        )
        vm.refrescarCarreras()
        advanceUntilIdle()
        vm.estado.esperarCondicion { it.carreras.isNotEmpty() }

        assertEquals(listOf("/motorizados/carreras"), rutas)
    }

    @Test
    fun telefono_de_contacto_distingue_numeros_reales_de_ids_de_prueba() {
        assertEquals("51999777666", telefonoDeContacto("51999777666"))
        assertEquals("51999777666", telefonoDeContacto("+51 999 777 666"))
        assertNull(telefonoDeContacto("demo-cocina-104"))
        assertNull(telefonoDeContacto("simulador-panel"))
        assertNull(telefonoDeContacto("123"))
        assertNull(telefonoDeContacto(null))
    }

    @Test
    fun aceptar_con_409_la_saca_de_la_lista_sin_hacerla_propia() = runTest {
        val vm = CarrerasViewModel(
            MotorizadosApi(ApiCliente(sesion = SesionRepositorio(dataStoreDePrueba()), engine = enginePool(aceptarStatus = HttpStatusCode.Conflict))),
            AvisosGlobales(),
            testDispatcher,
        )
        vm.cargar()
        advanceUntilIdle()
        vm.estado.esperarCondicion { it.carreras.isNotEmpty() }

        vm.aceptar(vm.estado.value.carreras.single())
        advanceUntilIdle()
        vm.estado.esperarCondicion { it.accionEnCurso == null }

        assertNull(vm.estado.value.miCarrera)
        assertEquals(emptyList<String>(), vm.estado.value.carreras.map { it.pedidoId })
    }
    @Test
    fun cambiar_el_turno_manda_un_solo_cambio_al_backend() = runTest {
        // Cubre el ViewModel: una llamada a `cambiarTurno` = una sola llamada
        // al backend, y la guarda `cambiandoTurno` no la duplica.
        //
        // OJO: el bug real que apagaba el turno estaba en la UI —la fila era
        // `clickable` Y el Switch tenía `onCheckedChange`, así que un toque
        // disparaba dos veces con valores opuestos—. Eso NO se ve desde acá;
        // lo cubre `EncabezadoRiderTest`.
        val rutas = mutableListOf<String>()
        val vm = CarrerasViewModel(
            MotorizadosApi(
                ApiCliente(
                    sesion = SesionRepositorio(dataStoreDePrueba()),
                    engine = enginePool(rutasLlamadas = rutas),
                ),
            ),
            AvisosGlobales(),
            testDispatcher,
        )
        vm.cargar()
        advanceUntilIdle()
        vm.estado.esperarCondicion { it.perfil != null }

        vm.cambiarTurno(true)
        advanceUntilIdle()
        vm.estado.esperarCondicion { !it.cambiandoTurno }
        advanceUntilIdle()

        val cambios = rutas.count { it.contains("disponibilidad") }
        assertEquals(1, cambios, "una llamada = UN cambio en el backend. rutas=$rutas")
    }

    @Test
    fun cargar_trae_lo_ganado_hoy_en_la_misma_llamada() = runTest {
        // El monto vive en el perfil, que ya se consulta al abrir: viaja en la
        // MISMA respuesta, sin una llamada aparte.
        //
        // Se refresca además cuando la carrera en curso desaparece —o sea, al
        // entregar—, que es cuando el número cambia. No en cada vuelta del
        // polling: serían 20 llamadas por minuto para un dato quieto.
        val rutas = mutableListOf<String>()
        val vm = CarrerasViewModel(
            MotorizadosApi(
                ApiCliente(
                    sesion = SesionRepositorio(dataStoreDePrueba()),
                    engine = enginePool(rutasLlamadas = rutas),
                ),
            ),
            AvisosGlobales(),
            testDispatcher,
        )

        vm.cargar()
        advanceUntilIdle()
        vm.estado.esperarCondicion { it.perfil != null }

        assertEquals(
            1,
            rutas.count { it.contains("mi-perfil") },
            "el perfil (con lo de hoy) se pide UNA vez al cargar. rutas=$rutas",
        )
    }

}
